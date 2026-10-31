package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.core.PassiveEffects;
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
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;

import java.util.List;
import java.util.Set;
import java.util.UUID;

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

    /**
     * 不死图腾冷却键（UUID 派生）。
     *
     * <p>固定键会被外部模组直接 {@code remove} 掉，从而把 300 秒冷却清零 ——
     * 派生键至少要读到玩家的 UUID 才能算出名字。</p>
     */
    private static final String UNDYING_COOLDOWN_PREFIX = "eh_uc_";
    private static final String UNDYING_COOLDOWN_LEGACY_KEY = "eternal_heart_undying_cd";

    private static String undyingKey(Player player) {
        UUID id = player.getUUID();
        return UNDYING_COOLDOWN_PREFIX
                + Long.toHexString(id.getMostSignificantBits() ^ id.getLeastSignificantBits() ^ 0x0D0D0DL);
    }

    /** 读取冷却就绪时间（兼容旧版本固定键）。 */
    private static long undyingReadyAt(Player player) {
        CompoundTag data = player.getPersistentData();
        String key = undyingKey(player);
        return data.contains(key) ? data.getLong(key) : data.getLong(UNDYING_COOLDOWN_LEGACY_KEY);
    }

    private static void setUndyingReadyAt(Player player, long readyAt) {
        player.getPersistentData().putLong(undyingKey(player), readyAt);
        player.getPersistentData().remove(UNDYING_COOLDOWN_LEGACY_KEY);
    }

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
        applyAirSupplyLock(player);
        applyHungerLock(player);
        if (Ticker.due(player.getUUID(), tick, 20)) applyAutoPotion(player);
        applyAutoExtinguish(player);
        applyDurabilityRefill(player, tick);
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
        // 无限时长（原版 -1 语义）：效果永不到期，也不再需要周期性刷新
        final int duration = ConfigValues.get(EternalHeartConfig.PASSIVE_EFFECT_INFINITE) ? -1 : 1200;
        final int refreshBelow = 600;

        // 附加被动效果（可来自任意模组）：时长 -1 = 永久；有限时长在剩余不足 1/4 时续期，
        // 避免图标闪烁又不至于每 tick 重发效果包
        final List<PassiveEffects.Entry> extras = PassiveEffects.all();

        maintainBuiltin(player, extras, MobEffects.NIGHT_VISION,
                ConfigValues.get(EternalHeartConfig.NIGHT_VISION), duration,
                ConfigValues.get(EternalHeartConfig.NIGHT_VISION_LEVEL), refreshBelow);
        maintainBuiltin(player, extras, MobEffects.WATER_BREATHING,
                ConfigValues.get(EternalHeartConfig.WATER_BREATHING), duration,
                ConfigValues.get(EternalHeartConfig.WATER_BREATHING_LEVEL), refreshBelow);

        int haste = ConfigValues.get(EternalHeartConfig.HASTE_LEVEL);
        maintainBuiltin(player, extras, MobEffects.DIG_SPEED, haste > 0, duration,
                Math.max(0, haste - 1), refreshBelow);

        // 抗火有两种来源：常驻配置，或熔岩行走时 MobilitySystem 授予的短时保护
        boolean passiveFire = ConfigValues.get(EternalHeartConfig.FIRE_RESISTANCE_POTION);
        boolean lavaFire = ConfigValues.get(EternalHeartConfig.LAVA_WALKING)
                && player.getPersistentData().getLong(MobilitySystem.LAVA_RESIST_UNTIL) > tick;
        maintainBuiltin(player, extras, MobEffects.FIRE_RESISTANCE, passiveFire || lavaFire,
                passiveFire ? duration : 20, 0, passiveFire ? refreshBelow : 5);

        maintainBuiltin(player, extras, MobEffects.SATURATION,
                ConfigValues.get(EternalHeartConfig.SATURATION), duration,
                ConfigValues.get(EternalHeartConfig.SATURATION_LEVEL), refreshBelow);
        maintainBuiltin(player, extras, MobEffects.LUCK,
                ConfigValues.get(EternalHeartConfig.PERMANENT_LUCK), duration,
                ConfigValues.get(EternalHeartConfig.PERMANENT_LUCK_LEVEL), refreshBelow);

        for (PassiveEffects.Entry extra : extras) {
            int extraDuration = extra.duration();
            OwnedEffects.maintain(player, extra.effect(), true, extraDuration, extra.amplifier(),
                    extraDuration < 0 ? 0 : Math.max(1, extraDuration / 4));
        }
    }

    /**
     * 维持一个内置被动效果；若同一效果也出现在「附加被动效果」列表里，直接跳过。
     *
     * <p>两个来源同时维持同一效果会互相拆台：{@code OwnedEffects} 的账本按 ≤1 条记录/效果，
     * 等级不一致时每次 {@code maintain} 都要先释放再重记 —— 表现为效果每 tick 被移除又加回、
     * 图标狂闪、效果包刷屏。这里约定「显式配置优先」：附加列表里写了的，就由它独占地管。</p>
     */
    private static void maintainBuiltin(Player player, List<PassiveEffects.Entry> extras, MobEffect effect,
                                        boolean enabled, int duration, int amplifier, int refreshBelow) {
        for (PassiveEffects.Entry extra : extras) {
            if (extra.effect() == effect) return;
        }
        OwnedEffects.maintain(player, effect, enabled, duration, amplifier, refreshBelow);
    }

    /** 氧气锁定：直接把氧气值钉在满值（不依赖水下呼吸效果，被清效果也不影响）。 */
    private static void applyAirSupplyLock(Player player) {
        if (!ConfigValues.get(EternalHeartConfig.AIR_SUPPLY_LOCK)) return;
        int max = player.getMaxAirSupply();
        if (player.getAirSupply() < max) player.setAirSupply(max);
    }

    /** 饥饿锁定：饥饿值与饱食度都不再下降（饥饿值满即不触发自然回血扣饱食）。 */
    private static void applyHungerLock(Player player) {
        if (!ConfigValues.get(EternalHeartConfig.HUNGER_LOCK)) return;
        var food = player.getFoodData();
        if (food.getFoodLevel() < 20) food.setFoodLevel(20);
        if (food.getSaturationLevel() < 5f) food.setSaturation(5f);
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
            if (stack.isEmpty()) continue;

            // 判据从「物品类型是普通药水」放宽为「物品带有药水效果」：
            // 喷溅 / 滞留 / 模组药水（以及把效果写在 NBT 上的自定义药水）都能被自动使用
            List<MobEffectInstance> effects = PotionUtils.getMobEffects(stack);
            if (effects.isEmpty() && !(stack.getItem() instanceof PotionItem)) continue;

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
        boolean potionBottle = stack.getItem() instanceof PotionItem;
        stack.shrink(1);
        // 只有普通瓶装药水会留下玻璃瓶；喷溅 / 滞留 / 模组药水不凭空造瓶子
        if (!potionBottle) return;

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
    //  耐久补满
    // ============================================================

    /**
     * 耐久补满：按固定周期把背包里已损耗的耐久直接补回满值。
     *
     * <p>与「工具不掉耐久」互补：那个开关只阻止<b>新</b>损耗，佩戴之前已经磨掉的耐久
     * 不会自己回来；这里负责把历史损耗补满，两者可独立开关。</p>
     *
     * <p>遍历 {@code Inventory#getContainerSize()}（主背包 + 盔甲 + 副手，共 41 格），
     * 因此手上工具、盔甲、副手盾牌都在覆盖范围内。只处理「有耐久且已损耗」的物品，
     * 未损耗 / 无耐久物品零开销跳过；Curios 饰品栏不在其中。</p>
     */
    private static void applyDurabilityRefill(Player player, long tick) {
        if (!ConfigValues.get(EternalHeartConfig.DURABILITY_REFILL)) return;

        // 秒 → tick：用 long 换算，配置上限放开后 int 乘法会溢出（表现为周期变负 / 变成每 tick）
        int seconds = ConfigValues.get(EternalHeartConfig.DURABILITY_REFILL_INTERVAL);
        int period = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, (long) seconds * 20L));
        if (!Ticker.due(player.getUUID(), tick, period)) return;

        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.isDamageableItem()) continue;
            if (stack.getDamageValue() == 0) continue;
            stack.setDamageValue(0);
        }
    }

    // ============================================================
    //  秒吃食物
    // ============================================================

    /**
     * 秒吃食物：把进食的使用时长压到 1 tick（约 0.05 秒）。
     *
     * <p><b>为什么挂在这里</b>：原版「吃多久」只由 {@code Item#getUseDuration} 决定，
     * 且在 {@code LivingEntity#startUsingItem} 里读取一次；Forge 恰好在该入口提供
     * {@link LivingEntityUseItemEvent.Start} 用于改写时长，因此不必再用 Mixin 去写原版字段。</p>
     *
     * <p><b>两端都要生效</b>：客户端据此播放（几乎不可见的）进食动作并预测完成，
     * 服务端据此真正完成进食、扣除食物并结算饱食度 —— 只改一侧会出现「动画结束了但没吃上」。</p>
     *
     * <p>只处理食物：药水、弓、盾牌等其它使用类物品的行为保持原样。</p>
     */
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (!ConfigValues.get(EternalHeartConfig.INSTANT_EAT)) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!EquipTracker.isEquipped(player)) return;
        if (!event.getItem().isEdible()) return;
        event.setDuration(1);
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
            long readyAt = undyingReadyAt(player);
            if (now >= readyAt) {
                event.setCanceled(true);
                player.setHealth(player.getMaxHealth() * 0.5f);
                player.clearFire();
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 2));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 2));
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 4));
                setUndyingReadyAt(player,
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
