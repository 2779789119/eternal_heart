package com.eternal_heart;

import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.features.AegisSystem;
import com.eternal_heart.features.CombatSystem;
import com.eternal_heart.features.CurioGuard;
import com.eternal_heart.features.CurioSlotExpansion;
import com.eternal_heart.features.DefenseSystem;
import com.eternal_heart.features.EquipTracker;
import com.eternal_heart.features.FeatureManager;
import com.eternal_heart.features.FurySystem;
import com.eternal_heart.features.MobilitySystem;
import com.eternal_heart.features.OwnedEffects;
import com.eternal_heart.features.OwnedShield;
import com.eternal_heart.features.SuppressionSystem;
import com.eternal_heart.features.SurvivalSystem;
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
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
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

    /**
     * 每 tick → 全模块分发。
     *
     * <p>用 {@code LOW} 而不是默认优先级：Curios 也在 {@code LivingTickEvent} 里处理槽位变更
     * （重算属性 + 发 {@code CurioChangeEvent}），排在它之后才能在同一 tick 内看到撤掉加成后的
     * 新生命上限，并立刻把血量修回合法区间 —— 这是「摘下饰品暴毙」的关键时序。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingTick(LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        boolean clientSide = player.level().isClientSide();

        // 兜底归还（仅服务端，常态只是两次哈希查询）：
        // 死亡最终被其它模组取消、玩家已经复活，可备份还留着 —— 立即归还物品与饰品。
        // 必须放在佩戴判定之前：死亡瞬间饰品已被取下，此刻「是否佩戴」正是 false，
        // 若受它门控就永远等不到归还。
        if (!clientSide) {
            // 卸下守护窗口（离槽瞬间开启 40 tick）：必须跑在佩戴判定之前，
            // 否则会先被判成「已卸下」而整段特性 tick 被跳过
            AegisSystem.tickUnequipGuard(player);
            // 濒死无敌窗口同样必须在佩戴判定之前推进：死亡兜底救人的瞬间饰品已被取下，
            // 受佩戴门控的话计数永远不递减（= 永久无敌）
            AegisSystem.tickLethalImmunity(player);
            AegisSystem.restoreIfPending(player);
            CurioGuard.restoreIfPending(player);
        }

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

    /** 重生后重同步飞行能力（新实体重建，能力包需要补发），并兜底归还死亡保留的备份。 */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MobilitySystem.resyncFlight(player);
            // 兜底：Clone 阶段未触发的极端情况下，重生后再归还一次（正常流程此处为空操作）
            AegisSystem.restoreIfPending(player);
            CurioGuard.restoreIfPending(player);
        }
        // 重生会重建 Curios 背包：让槽位扩容重新按配置套一遍
        CurioSlotExpansion.schedule();
    }

    /** 登录后重同步飞行能力（客户端首次构建，确保授权状态与服务端一致）。 */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MobilitySystem.resyncFlight(player);
            // 崩溃兜底：死亡与重生之间服务器重启时，从持久化备份里把物品还回去
            AegisSystem.restoreIfPending(player);
            CurioGuard.restoreIfPending(player);
        }
        // 玩家上线后其佩戴的饰品纳入自动检测名单，尽快重扫
        RevelationFixCompat.scheduleScan();
        // 玩家上线后按配置给其 Curios 槽位套一次扩容
        CurioSlotExpansion.schedule();
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
     *
     * <p>同时在这里登记一次「卸下血量修复」：生命上限的修饰符就是在离槽这一刻被撤掉的，
     * 而卸下判定还有 10 tick 防抖 —— 不在这里排队的话，「上限已降、血量仍是旧值」的脏值
     * 会在整合包里被其它模组结算成 0（摘下饰品当场暴毙）。</p>
     */
    @SubscribeEvent
    public static void onCurioChange(CurioChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        // 佩戴/卸下都会改变自动检测名单的内容，尽快重扫（防收逻辑本身只关心「被移出」）
        RevelationFixCompat.scheduleScan();
        if (!event.getTo().isEmpty()) return; // 只关心「被移出」
        if (player.level().isClientSide()) return;
        CurioGuard.handleSlotLoss(player, event.getIdentifier(), event.getSlotIndex(), event.getFrom());
        if (!event.getFrom().is(EternalHeartMod.ETERNAL_HEART.get())) return;
        // 被外部夺取的饰品下一 tick 就会放回槽位：那时上限会一起恢复，
        // 提前压血只会白掉一截，等真正「确定卸下」的路径处理
        if (CurioGuard.isSeizePending(player)) return;
        AegisSystem.rememberUnequip(player);
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
        // 槽位扩容（内部节流 40 tick，配置/玩家数没变化时什么都不做）
        CurioSlotExpansion.tick(event.getServer());
    }

    /**
     * 秒吃食物：佩戴者进食的使用时长压到 1 tick。
     *
     * <p>不限定端：客户端靠它同步「瞬间吃完」的预测动作，服务端靠它真正结算进食与饱食度。</p>
     */
    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        SurvivalSystem.onUseItemStart(event);
    }

    /** 数据包重载后配方可能变化，清空熔炼结果缓存；槽位注册表也可能变化，重排一次扩容。 */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        UtilitySystem.invalidateSmeltingCache();
        CurioSlotExpansion.schedule();
    }

    // ============================================================
    //  受伤 / 攻击 / 击杀 → 战斗与防御模块
    // ============================================================

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 濒死无敌窗口：致死保护 / 死亡兜底刚救回玩家，这一段里完全免伤。
        // 不经过佩戴判定 —— 死亡兜底救人的瞬间饰品可能已被死亡流程取下。
        if (AegisSystem.inLethalImmunity(player)) {
            event.setCanceled(true);
            return;
        }
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

        if (!EquipTracker.isEquipped(player)) {
            // 卸下守护窗口：饰品刚离槽、生命上限已被撤掉，此刻佩戴判定必然是 false。
            // 若其它模组正好在这一段里结算出致死伤害（按上限重算血量、夹取上限等实现），
            // 没有这道钳制就是「摘下饰品当场被秒」。只跑致死保护，其余模块一律不介入。
            if (AegisSystem.withinUnequipGuard(player)) AegisSystem.guardLethal(player, event);
            return;
        }

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

        // 玩家攻击 → 战斗模块（被完全拦下的攻击也照样记账，由结算后核对补足）
        if (event.getSource().getEntity() instanceof Player player && EquipTracker.isEquipped(player)) {
            FeatureManager.dispatchPlayerAttack(player, event);
        }
    }

    /**
     * 玩家近战：在 {@code Player.attack} 的第一行记账，早于目标的任何 hurt() 重写。
     *
     * <p>目标在自己的 hurt() 里直接 return false（阶段免伤 / 无敌帧）时，后面所有伤害事件都不会
     * 触发；只有这里能看到「这一刀本该打多少」，结算后核对才有账可对。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!EquipTracker.isEquipped(player)) return;
        CombatSystem.observeMeleeAttack(player, event.getTarget());
    }

    /** 远程：弹射物命中实体时记账，同样早于 hurt()（只取我方弹射物，见 CombatSystem）。 */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide()) return;
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof LivingEntity target)) return;
        CombatSystem.observeProjectileHit(projectile, target);
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

    /**
     * 死亡结算收尾（LOWEST 优先级）：执行到这里，所有「复活类」机制的取消都已表态。
     *
     * <p>{@link #onPlayerDying} 必须跑在 HIGHEST —— 先把背包清空，死亡掉落才是空的、
     * 不会被灵魂石 / 墓碑 / 收集类机制截走；但复活类模组会在<b>更晚的优先级</b>再取消死亡事件
     *（例：MineFargo 的灵魂石、各种不死图腾）。缺少这道收尾就会出现
     *「人复活了，可物品栏是空的、地上也没有掉落物」。</p>
     *
     * <p>因此这里在死亡最终没有发生时，把物品 / 经验 / 饰品原样还回去。
     * 注意不能做「是否佩戴」判断：饰品在死亡瞬间已被取下，此刻的佩戴状态必然是 false。</p>
     *
     * <p>此外，真实死亡在归还之前先过一道<b>死亡兜底</b>：致死保护没拦住的致死路径
     * （直接 {@code setHealth(0)} + {@code die()}、绕过伤害事件的实现）会在这里被
     * {@link AegisSystem#lastStand} 强行取消，随后照常走「死亡被取消」的归还流程。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDyingFinalize(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        // 死亡兜底：已经走到收尾阶段还没被任何机制取消 → 最后一次拦截
        if (!event.isCanceled() && !AegisSystem.lastStand(player, event)) {
            return; // 仍然死亡：留到重生阶段归还
        }

        AegisSystem.undoDeath(player);
        CurioGuard.undoDeath(player);
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

    /**
     * 瞬挖：佩戴后任意方块 1 tick 挖穿。
     *
     * <p><b>两端都必须生效</b>：客户端用 {@code getDigSpeed} 决定何时发破坏包，
     * 服务端 {@code ServerPlayerGameMode.handleBlockBreakAction} 用同一个事件判定
     * 是否「insta mine」；只改一侧会出现「客户端挖掉了、服务端不认」的拉扯。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!ConfigValues.get(EternalHeartConfig.INSTANT_MINE)) return;
        if (!EquipTracker.isEquipped(event.getEntity())) return;
        UtilitySystem.applyInstantMine(event);
    }

    /**
     * 瞬挖的工具判定：佩戴后一律按「正确工具」处理。
     *
     * <p>这个事件同时决定两件事：</p>
     * <ol>
     *   <li>挖掘进度公式里的除数（{@code 正确工具 ? 30 : 100}）——它通过
     *       {@code player.hasCorrectToolForDrops} 读取本事件的结果；</li>
     *   <li>服务端 {@code ServerPlayerGameMode#destroyBlock} 的 {@code canHarvestBlock}
     *       检查——决定破坏后要不要执行 {@code playerDestroy}（也就是掉不掉东西）。</li>
     * </ol>
     *
     * <p>因此这里直接置为 true：空手 / 工具不对也能像正确工具一样瞬间挖穿并正常掉落。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        if (!ConfigValues.get(EternalHeartConfig.INSTANT_MINE)) return;
        if (!EquipTracker.isEquipped(event.getEntity())) return;
        event.setCanHarvest(true);
    }

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
        MobEffect effect = event.getEffectInstance().getEffect();

        // 被压制（禁疗）的目标：从施加阶段就拒绝治疗类效果（再生 / 吸收 / 生命提升 / 瞬间治疗）。
        // 必须放在「玩家 + 佩戴」门控之前 —— 被压制的基本都是怪物，吃了那个门控就永远不生效。
        if (SuppressionSystem.blocksHealingEffect(event.getEntity(), effect)) {
            event.setResult(Event.Result.DENY);
            return;
        }

        if (!(event.getEntity() instanceof Player player)) return;
        if (!EquipTracker.isEquipped(player)) return;
        if (!ConfigValues.get(EternalHeartConfig.DEBUFF_GUARD)) return; // 功能已关闭

        // 与清扫层共用同一判定（预编译规则集），保证黑名单 / 白名单在两层行为完全一致
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
