package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.core.PlayerScoped;
import com.eternal_heart.core.Ticker;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;

import java.util.List;
import java.util.Set;

/**
 * 生存系统 —— 生命回复、被动效果、虚空救援、自动喝药、无限箭矢、自动灭火、死亡保护。
 *
 * <p>重写要点：</p>
 * <ul>
 *   <li><b>幽灵箭零扫描</b>：历史实现无论手持什么物品，每 tick 都要扫一遍背包回收幽灵箭
 *       （36 格 × 20 次/秒）。现在用「是否生成过幽灵箭」的内存标记短路：
 *       没生成过就完全不扫描，常态开销归零。</li>
 *   <li><b>自动喝药单遍扫描</b>：历史实现为了「优先效果」要完整遍历背包两遍；
 *       现在一遍扫描同时记录首个任意药水作为兜底，逻辑等价、开销减半。</li>
 *   <li><b>跨维度灵魂绑定</b>：重生点位于其它维度时，历史实现用当前维度坐标传送（错位）；
 *       现在按重生维度选择正确的目标世界。</li>
 *   <li>周期任务统一走 {@link Ticker}（相位错开），被动效果维持保持「自愈式」刷新语义。</li>
 * </ul>
 */
public class SurvivalSystem implements IFeature {

    private static final String UNDYING_COOLDOWN_KEY = "eternal_heart_undying_cd";

    /** 自动喝药的优先效果：治疗、再生、生命提升、抗性提升。 */
    private static final Set<MobEffect> PRIORITY_EFFECTS = Set.of(
            MobEffects.HEAL, MobEffects.REGENERATION,
            MobEffects.HEALTH_BOOST, MobEffects.DAMAGE_RESISTANCE);

    /** 背包里是否存在我们生成的幽灵箭（存在才需要扫描回收）。 */

    @Override
    public String getName() {
        return "SurvivalSystem";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    // ============================================================
    //  Tick
    // ============================================================

    @Override
    public void onPlayerTick(Player player, LivingTickEvent event) {
        long tick = player.level().getGameTime();

        applyTickRegen(player);
        applyPassiveEffects(player, tick);
        applyVoidRescue(player);
        if (Ticker.due(player.getUUID(), tick, 20)) applyAutoPotion(player);
        applyAutoExtinguish(player);
    }

    @Override
    public void onUnequip(Player player) {
        OwnedEffects.releaseAll(player);
    }

    /** 每 tick 少量回血（工具提示按「HP/秒」展示，配置值单位为 HP/tick；负数 = 持续掉血）。 */
    private static void applyTickRegen(Player player) {
        float regen = ConfigValues.get(EternalHeartConfig.TICK_REGEN).floatValue();
        if (regen == 0) return;
        if (regen > 0) {
            if (player.getHealth() > 0 && player.getHealth() < player.getMaxHealth()) {
                Health.apply(player, regen);
            }
            return;
        }
        if (player.isAlive()) Health.apply(player, regen);
    }

    /**
     * 被动效果「自愈式」刷新：效果缺失或剩余时长低于阈值即补回，
     * 既能在被其它模组清掉后 1 tick 内恢复，也避免夜视等效果的淡出闪烁。
     */
    private static void applyPassiveEffects(Player player, long tick) {
        final int duration = 1200;
        final int refreshBelow = 600;

        OwnedEffects.maintain(player, MobEffects.NIGHT_VISION,
                ConfigValues.get(EternalHeartConfig.NIGHT_VISION), duration, 0, refreshBelow);
        OwnedEffects.maintain(player, MobEffects.WATER_BREATHING,
                ConfigValues.get(EternalHeartConfig.WATER_BREATHING), duration, 0, refreshBelow);

        int haste = ConfigValues.get(EternalHeartConfig.HASTE_LEVEL);
        OwnedEffects.maintain(player, MobEffects.DIG_SPEED, haste > 0, duration, Math.max(0, haste - 1), refreshBelow);

        // 抗火有两种来源：常驻配置，或熔岩行走时 MobilitySystem 授予的短时保护
        boolean passiveFire = ConfigValues.get(EternalHeartConfig.FIRE_RESISTANCE_POTION);
        boolean lavaFire = ConfigValues.get(EternalHeartConfig.LAVA_WALKING)
                && player.getPersistentData().getLong(MobilitySystem.LAVA_RESIST_UNTIL) > tick;
        OwnedEffects.maintain(player, MobEffects.FIRE_RESISTANCE, passiveFire || lavaFire,
                passiveFire ? duration : 20, 0, passiveFire ? refreshBelow : 5);

        OwnedEffects.maintain(player, MobEffects.SATURATION,
                ConfigValues.get(EternalHeartConfig.SATURATION), duration, 0, refreshBelow);
        OwnedEffects.maintain(player, MobEffects.LUCK,
                ConfigValues.get(EternalHeartConfig.PERMANENT_LUCK), duration, 0, refreshBelow);
    }

    /** 虚空救援：跌出世界底部时送回建筑上限高度（阈值基于世界实际最低高度）。 */
    private static void applyVoidRescue(Player player) {
        if (!ConfigValues.get(EternalHeartConfig.VOID_RESCUE)) return;

        Level level = player.level();
        int minY = level.getMinBuildHeight();
        if (player.position().y > minY - 4) return;

        int topY = level.getMaxBuildHeight();
        player.teleportTo(player.getX(), topY, player.getZ());
        player.fallDistance = 0;
    }

    // ============================================================
    //  自动喝药
    // ============================================================

    /** 低血自动喝药：单遍扫描背包，优先选择带治疗类效果的药水，否则使用任意药水。 */
    private static void applyAutoPotion(Player player) {
        if (!ConfigValues.get(EternalHeartConfig.AUTO_POTION)) return;
        double threshold = ConfigValues.get(EternalHeartConfig.AUTO_POTION_THRESHOLD);
        if (player.getHealth() / player.getMaxHealth() > threshold) return;

        Inventory inventory = player.getInventory();
        int fallbackSlot = -1;
        ItemStack fallbackStack = null;
        List<MobEffectInstance> fallbackEffects = null;

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!(stack.getItem() instanceof PotionItem)) continue;

            List<MobEffectInstance> effects = PotionUtils.getMobEffects(stack);
            if (hasPriorityEffect(effects)) {
                drinkPotion(player, slot, stack, effects);
                return;
            }
            if (fallbackSlot < 0) {
                fallbackSlot = slot;
                fallbackStack = stack;
                fallbackEffects = effects;
            }
        }

        if (fallbackSlot >= 0) drinkPotion(player, fallbackSlot, fallbackStack, fallbackEffects);
    }

    private static boolean hasPriorityEffect(List<MobEffectInstance> effects) {
        for (MobEffectInstance effect : effects) {
            if (PRIORITY_EFFECTS.contains(effect.getEffect())) return true;
        }
        return false;
    }

    private static void drinkPotion(Player player, int slot, ItemStack stack, List<MobEffectInstance> effects) {
        for (MobEffectInstance effect : effects) {
            player.addEffect(new MobEffectInstance(effect));
        }
        stack.shrink(1);

        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
        if (stack.isEmpty()) {
            player.getInventory().setItem(slot, bottle);
        } else if (!player.getInventory().add(bottle)) {
            player.level().addFreshEntity(new ItemEntity(player.level(),
                    player.getX(), player.getY() + 1, player.getZ(), bottle));
        }
    }

    /** 自动灭火：着火时立即熄灭（抗火效果由 FIRE_RESISTANCE_POTION 配置统一提供）。 */
    private static void applyAutoExtinguish(Player player) {
        if (!ConfigValues.get(EternalHeartConfig.AUTO_EXTINGUISH)) return;
        if (player.isOnFire()) player.clearFire();
    }

    // ============================================================
    //  无限箭矢
    // ============================================================

    // 实现已移到「弹药判定层」：{@code PlayerAmmoMixin} 在 Player#getProjectile 的返回处
    // 提供虚拟弹药 —— 弓 / 弩 / 模组远程武器共用同一入口，且完全不触碰玩家背包
    //（历史实现往背包塞一支"幽灵箭"，占格子、会掉出来，弓与弩还得各维护一套）。
    // 这里不再需要任何每 tick 逻辑。

    // ============================================================
    //  死亡保护
    // ============================================================

    @Override
    public void onPlayerDeath(Player player, LivingDeathEvent event) {
        long now = player.level().getGameTime();

        // 1) 不死图腾：冷却就绪时以半血复活并附带一系列保护效果
        if (ConfigValues.get(EternalHeartConfig.UNDYING_TOTEM)) {
            long readyAt = player.getPersistentData().getLong(UNDYING_COOLDOWN_KEY);
            if (now >= readyAt) {
                event.setCanceled(true);
                player.setHealth(player.getMaxHealth() * 0.5f);
                player.clearFire();
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 2));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 2));
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 4));
                player.getPersistentData().putLong(UNDYING_COOLDOWN_KEY,
                        now + ConfigValues.get(EternalHeartConfig.UNDYING_COOLDOWN) * 20L);
                return;
            }
        }

        // 2) 灵魂绑定：以 1 点生命存活并返回重生点（虚弱 + 缓慢的代价）
        if (ConfigValues.get(EternalHeartConfig.SOUL_BIND)) {
            event.setCanceled(true);
            player.setHealth(1f);
            player.clearFire();
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 3));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 2));

            if (player instanceof ServerPlayer serverPlayer) {
                recallToRespawn(serverPlayer);
            }
        }
    }

    /** 传送回重生点；重生点位于其它维度时切换到对应的世界（历史实现会保留当前维度坐标）。 */
    private static void recallToRespawn(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        BlockPos respawn = player.getRespawnPosition();
        ResourceKey<Level> dimension = player.getRespawnDimension();
        ServerLevel target = dimension == null ? null : server.getLevel(dimension);

        if (respawn != null && target != null) {
            moveTo(player, target, respawn.getX() + 0.5, respawn.getY(), respawn.getZ() + 0.5);
            return;
        }
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        moveTo(player, overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
    }

    private static void moveTo(ServerPlayer player, ServerLevel target, double x, double y, double z) {
        if (target == player.serverLevel()) {
            player.teleportTo(x, y, z);
        } else {
            player.teleportTo(target, x, y, z, player.getYRot(), player.getXRot());
        }
    }
}
