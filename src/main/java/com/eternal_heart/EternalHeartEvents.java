package com.eternal_heart;

import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.features.AegisSystem;
import com.eternal_heart.features.CombatSystem;
import com.eternal_heart.features.CurioGuard;
import com.eternal_heart.features.DefenseSystem;
import com.eternal_heart.features.EquipTracker;
import com.eternal_heart.features.FeatureManager;
import com.eternal_heart.features.FurySystem;
import com.eternal_heart.features.MobilitySystem;
import com.eternal_heart.features.OwnedEffects;
import com.eternal_heart.features.OwnedShield;
import com.eternal_heart.features.TimeStop;
import com.eternal_heart.features.UtilitySystem;
import com.eternal_heart.integration.RevelationFixCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import top.theillusivec4.curios.api.event.CurioChangeEvent;

import java.util.List;

/**
 * 永恒之心主事件处理器 —— 事件路由层。
 *
 * <p>职责：</p>
 * <ol>
 *   <li>接收 Forge 事件，检查玩家是否佩戴永恒之心（全部经 {@link EquipTracker} 缓存判定）；</li>
 *   <li>分发给 {@link FeatureManager} 调度到各功能模块；</li>
 *   <li>处理不属于任何模块的特殊事件（和平光环、便携末影箱、经验倍率、抢夺、无限箭矢）；</li>
 *   <li>生命周期清理：玩家退出、服务器停止、数据包重载。</li>
 * </ol>
 *
 * <p>业务逻辑全部位于 {@code features/} 包，本类只做路由，保持「一个事件一个处理器」的可读结构。</p>
 */
public class EternalHeartEvents {

    /** 单次掉落经验的安全上限（配置倍率放开后防止原版升级循环耗时过长）。 */
    private static final int MAX_XP_DROP = 100_000_000;

    // ============================================================
    //  每 Tick → 全模块分发
    // ============================================================

    @SubscribeEvent
    public static void onLivingTick(LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        boolean clientSide = player.level().isClientSide();

        // 装备状态判定（两端都带卸下防抖）：
        // - 服务端：连续多 tick 查不到饰品才判定卸下，并在边沿分发 dispatchEquip / dispatchUnequip；
        // - 客户端：同样的防抖语义（表现层如内置滑翔不能被同步间隙的瞬时误判中断）。
        // 防抖 + 「Curios 数据不可用 = 保持现状」共同保证：切换物品 / 维度切换 / 重生 / 同步间隙
        // 都不会误触发卸载清理（历史问题：飞行中途被误清理导致「飞一小段就掉下来」）。
        boolean equipped = clientSide ? EquipTracker.clientTick(player) : EquipTracker.serverTick(player);
        if (!equipped) {
            if (!clientSide) OwnedEffects.releaseAll(player);
            return;
        }

        FeatureManager.dispatchPlayerTick(player, event, clientSide);
    }

    // ============================================================
    //  生命周期清理
    // ============================================================

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        FeatureManager.dispatchUnequip(player);
        EquipTracker.forget(player.getUUID());
        FurySystem.forget(player);
        OwnedEffects.forget(player);
        OwnedShield.forget(player);
        AegisSystem.forget(player);
        CurioGuard.forget(player);
        // 玩家离线后其佩戴的饰品不再计入自动检测名单，尽快重扫
        RevelationFixCompat.scheduleScan();
    }

    /**
     * 跨维度后重同步飞行能力。
     *
     * <p>客户端在切换维度时会重建玩家实例，能力完全依赖服务端的 abilities 包；
     * 而「已授权 → 不再发包」会让客户端停留在默认的 mayfly=false，
     * 表现为「切换维度后无法飞行」。这里强制重新授权并补发能力包。</p>
     */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MobilitySystem.resyncFlight(player);
        }
    }

    /** 重生后重同步飞行能力（新实体重建，能力包需要补发）。 */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MobilitySystem.resyncFlight(player);
        }
    }

    /** 登录后重同步飞行能力（客户端首次构建，确保授权状态与服务端一致）。 */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MobilitySystem.resyncFlight(player);
        }
        // 玩家上线后其佩戴的饰品纳入自动检测名单，尽快重扫
        RevelationFixCompat.scheduleScan();
    }

    /** 死亡重生时归还防护机制备份的物品与经验（非死亡复制不处理）。 */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        AegisSystem.restoreOnRespawn(event.getOriginal(), event.getEntity(), event.isWasDeath());
    }

    /**
     * 饰品归还（死亡重生）：必须在 Curios 复制饰品数据<b>之后</b>执行，
     * 否则我们刚放回的饰品会被 Curios 的克隆结果覆盖（LOW 晚于 Curios 的默认优先级）。
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerCloneCurio(PlayerEvent.Clone event) {
        CurioGuard.restoreOnRespawn(event.getOriginal(), event.getEntity(), event.isWasDeath());
    }

    /**
     * 死亡掉落阶段（Curios 之后执行）：饰品已在死亡瞬间收回，
     * 这里兜底删除任何被其它路径掉出的永恒之心。
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Player player) {
            CurioGuard.filterDrops(player, event.getDrops());
        }
    }

    /**
     * 饰品防夺：饰品被外部机制强行取走（如亚波伦「末日终结」）时立即收回。
     *
     * <p>只处理「移出且玩家侧一无所获」的变更——玩家主动卸下（物品进背包 / 光标 / 掉落物）
     * 不会触发收回。</p>
     */
    @SubscribeEvent
    public static void onCurioChange(CurioChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        // 佩戴/卸下都会改变自动检测名单的内容，尽快重扫（防收逻辑本身只关心「被移出」）
        RevelationFixCompat.scheduleScan();
        if (!event.getTo().isEmpty()) return; // 只关心「被移出」
        CurioGuard.handleSlotLoss(player, event.getIdentifier(), event.getSlotIndex(), event.getFrom());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        EquipTracker.clear();
        TimeStop.clear();
    }

    /**
     * 服务器每 tick：推进时停计数并在到期时清理状态。
     *
     * <p>用「剩余 tick 计数」而非时间戳，可绕开各维度 {@code getGameTime()} 基准不一致的问题。</p>
     */
    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        TimeStop.tick(event.getServer());
        // 例行扫描在线玩家佩戴的饰品（内部节流 5 秒），维持天启豁免名单自动检测
        RevelationFixCompat.scanAndSync(event.getServer());
    }

    /** 数据包重载后配方可能变化，清空熔炼结果缓存。 */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        UtilitySystem.invalidateSmeltingCache();
    }

    // ============================================================
    //  受伤 / 攻击 / 击杀 → 战斗与防御模块
    // ============================================================

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!EquipTracker.isEquipped(player)) return;
        FeatureManager.dispatchPlayerHurt(player, event);
    }

    /**
     * 最终伤害结算（护甲 / 抗性 / 吸收之后，扣血之前）：
     * 分发到防护机制（血量审计 + 致死保护），并对账护盾归属、清空永怒。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!EquipTracker.isEquipped(player)) return;
        FeatureManager.dispatchFinalDamage(player, event);
        OwnedShield.reconcile(player);
        FurySystem.onDamageResolved(player, event);
    }

    /**
     * 我们对目标造成的伤害进入最终结算阶段：突破其它模组的单次伤害上限 / 免伤。
     * 用 LOWEST 优先级，尽量排在其它模组的限伤处理之后。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTargetFinalDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (!EquipTracker.isEquipped(player)) return;
        CombatSystem.pierceDamageCap(event);
    }

    /** 掉落物归属保护：保护期内只有抛出者本人能拾取。 */
    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (AegisSystem.isPickupBlocked(event.getItem(), event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingAttack(LivingAttackEvent event) {
        // 弹射物反射：玩家被弹射物击中时按概率反弹
        if (event.getEntity() instanceof Player player && EquipTracker.isEquipped(player)) {
            double reflectChance = ConfigValues.get(EternalHeartConfig.REFLECT_PROJECTILE_CHANCE);
            if (reflectChance > 0 && event.getSource().getDirectEntity() instanceof Projectile projectile
                    && player.getRandom().nextFloat() < reflectChance) {
                event.setCanceled(true);
                Vec3 reversed = projectile.getDeltaMovement().reverse();
                projectile.setOwner(player);
                projectile.setDeltaMovement(reversed.scale(1.5));
                return;
            }
        }

        // 玩家攻击 → 战斗模块
        if (event.getSource().getEntity() instanceof Player player && EquipTracker.isEquipped(player)) {
            FeatureManager.dispatchPlayerAttack(player, event);
            // 攻击在事件阶段就被完全拦下（BOSS 无敌 / 闪避机制）：可选地改用真实伤害
            if (event.isCanceled()) CombatSystem.pierceBlockedAttack(player, event);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onTargetHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (!EquipTracker.isEquipped(player)) return;
        if (event.getEntity() == player) return;
        FeatureManager.dispatchTargetHurt(player, event);
    }

    @SubscribeEvent
    public static void onCriticalHit(CriticalHitEvent event) {
        Player player = event.getEntity();
        if (!EquipTracker.isEquipped(player)) return;
        // 不限制为原版暴击：永怒系统会自行判定是否强制 50% 暴击
        FeatureManager.dispatchCriticalHit(player, event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDying(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!EquipTracker.isEquipped(player)) return;
        FeatureManager.dispatchPlayerDeath(player, event);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (!EquipTracker.isEquipped(player)) return;
        FeatureManager.dispatchPlayerKill(player, event);
    }

    // ============================================================
    //  方块破坏 → 便利模块
    // ============================================================

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player.isCreative()) return;
        if (!EquipTracker.isEquipped(player)) return;
        FeatureManager.dispatchBlockBreak(player, event);
    }

    // ============================================================
    //  负面效果预防 → 在效果施加前直接拒绝
    // ============================================================

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!EquipTracker.isEquipped(player)) return;
        if (!ConfigValues.get(EternalHeartConfig.DEBUFF_GUARD)) return; // 功能已关闭

        // 与清扫层共用同一判定（预编译规则集），保证黑名单 / 白名单在两层行为完全一致
        MobEffect effect = event.getEffectInstance().getEffect();
        if (DefenseSystem.isDebuffBlocked(effect)) {
            event.setResult(Event.Result.DENY);
        }
    }

    // ============================================================
    //  和平光环 → 阻止敌对生物加入世界（生成 / 载入）
    // ============================================================

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Monster)) return;
        if (!ConfigValues.get(EternalHeartConfig.PEACE_AURA_ENABLED)) return;

        double range = ConfigValues.get(EternalHeartConfig.PEACE_AURA_RANGE);
        if (range <= 0) return;

        AABB area = event.getEntity().getBoundingBox().inflate(range);
        List<Player> guardians = event.getLevel().getEntitiesOfClass(Player.class, area,
                EquipTracker::isEquipped);
        if (!guardians.isEmpty()) {
            event.setCanceled(true);
        }
    }

    /** 玩家丢出物品：打上归属标记（保护期内只有本人能拾取，防止被收走）。 */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        AegisSystem.markThrown(event.getEntity(), event.getPlayer());
    }

    // ============================================================
    //  便携末影箱 → 潜行 + 空手右键
    // ============================================================

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        openRemoteEnderChest(event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // 有 GUI 的方块（箱子 / 熔炉等）交还原版处理
        if (event.getLevel().getBlockState(event.getPos()).getMenuProvider(event.getLevel(), event.getPos()) != null) {
            return;
        }
        openRemoteEnderChest(event.getEntity(), event);
    }

    private static void openRemoteEnderChest(Player player, PlayerInteractEvent event) {
        if (player.level().isClientSide()) return;
        if (!ConfigValues.get(EternalHeartConfig.ENDER_CHEST_REMOTE)) return;
        if (!EquipTracker.isEquipped(player)) return;
        if (!player.isCrouching()) return;
        if (!player.getMainHandItem().isEmpty()) return;

        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> ChestMenu.threeRows(id, inventory, p.getEnderChestInventory()),
                Component.translatable("container.enderchest")));
        event.setCanceled(true);
    }

    // ============================================================
    //  经验倍率 / 抢夺加成
    // ============================================================

    @SubscribeEvent
    public static void onXpDrop(LivingExperienceDropEvent event) {
        Player player = event.getAttackingPlayer();
        if (player == null) return;
        if (!EquipTracker.isEquipped(player)) return;

        // 经验倍率上限已放开：极端倍率下 double→int 会饱和到 MAX_VALUE，
        // 导致原版升级循环耗时极长，因此钳制到安全上限
        double multiplied = event.getDroppedExperience() * ConfigValues.get(EternalHeartConfig.XP_MULTIPLIER);
        event.setDroppedExperience((int) Math.min(Math.max(0.0, multiplied), MAX_XP_DROP));
    }

    @SubscribeEvent
    public static void onLootingLevel(LootingLevelEvent event) {
        if (event.getDamageSource() == null) return;
        if (!(event.getDamageSource().getEntity() instanceof Player player)) return;
        if (!EquipTracker.isEquipped(player)) return;
        // 抢夺加成允许负数（配置下限已放开），但负等级会让原版战利品表的
        // nextInt(looting + 1) 抛异常，因此钳制到 0
        int level = event.getLootingLevel() + ConfigValues.get(EternalHeartConfig.LOOTING_BONUS);
        event.setLootingLevel(Math.max(0, level));
    }

    private EternalHeartEvents() {
    }
}
