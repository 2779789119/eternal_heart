package com.eternal_heart;

import com.eternal_heart.features.FeatureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * 永恒之心主事件处理器
 * 
 * 职责：
 * 1. 接收 Forge 事件
 * 2. 检查玩家是否装备永恒之心
 * 3. 分发给 FeatureManager 调度到各个功能模块
 * 4. 处理无法放入 IFeature 接口的特殊事件（和平光环、末影箱、经验等）
 * 
 * 注意：具体业务逻辑全部在 features/ 包下的模块中实现，
 *       本类只做事件路由和特殊事件处理。
 */
public class EternalHeartEvents {

    // ============================================================
    //  每 Tick 事件 → 分发到所有功能模块
    // ============================================================

    @SubscribeEvent
    public static void onLivingTick(LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        boolean hasHeart = FeatureManager.hasEternalHeart(player);

        // 没有永恒之心时：清理可能泄漏的状态
        if (!hasHeart) {
            if (!player.level().isClientSide() && player.getPersistentData().getBoolean("eternal_heart_was_equipped")) {
                FeatureManager.dispatchUnequip(player);
                player.getPersistentData().putBoolean("eternal_heart_was_equipped", false);
            }
            return;
        }

        // 标记曾装备过（用于 unequip 检测）
        if (!player.level().isClientSide() && !player.getPersistentData().getBoolean("eternal_heart_was_equipped")) {
            player.getPersistentData().putBoolean("eternal_heart_was_equipped", true);
            FeatureManager.dispatchEquip(player);
        }

        // 分发到所有功能模块
        FeatureManager.dispatchPlayerTick(player, event, player.level().isClientSide());
    }

    // ============================================================
    //  受伤事件（高优先级）→ 防御系统
    // ============================================================

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!FeatureManager.hasEternalHeart(player)) return;
        FeatureManager.dispatchPlayerHurt(player, event);
    }

    // ============================================================
    //  攻击事件 → 战斗系统（弹射反射 + 永怒叠层）
    // ============================================================

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingAttack(LivingAttackEvent event) {
        // 弹射物反射：玩家被弹射物击中时
        if (event.getEntity() instanceof Player player && FeatureManager.hasEternalHeart(player)) {
            double reflectChance = EternalHeartConfig.REFLECT_PROJECTILE_CHANCE.get();
            if (reflectChance > 0 && event.getSource().getDirectEntity() instanceof Projectile proj) {
                if (player.getRandom().nextFloat() < reflectChance) {
                    event.setCanceled(true);
                    Vec3 motion = proj.getDeltaMovement().reverse();
                    proj.setOwner(player);
                    proj.setDeltaMovement(motion.scale(1.5));
                    return;
                }
            }
        }

        // 玩家攻击 → 分发到战斗系统
        if (event.getSource().getEntity() instanceof Player player) {
            if (!FeatureManager.hasEternalHeart(player)) return;
            FeatureManager.dispatchPlayerAttack(player, event);
        }
    }

    // ============================================================
    //  目标受伤（低优先级）→ 战斗增伤系统
    // ============================================================

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onTargetHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (!FeatureManager.hasEternalHeart(player)) return;
        LivingEntity target = event.getEntity();
        if (target == player) return;
        FeatureManager.dispatchTargetHurt(player, event);
    }

    // ============================================================
    //  暴击事件 → 永怒系统
    // ============================================================

    @SubscribeEvent
    public static void onCriticalHit(CriticalHitEvent event) {
        Player player = event.getEntity();
        if (!FeatureManager.hasEternalHeart(player)) return;
        if (!event.isVanillaCritical()) return;
        FeatureManager.dispatchCriticalHit(player, event);
    }

    // ============================================================
    //  死亡事件（最高优先级）→ 生存系统（不死图腾 + 灵魂绑定）
    // ============================================================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDying(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!FeatureManager.hasEternalHeart(player)) return;
        FeatureManager.dispatchPlayerDeath(player, event);
    }

    // ============================================================
    //  击杀事件（低优先级）→ 战斗系统 + 永怒
    // ============================================================

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (!FeatureManager.hasEternalHeart(player)) return;
        FeatureManager.dispatchPlayerKill(player, event);
    }

    // ============================================================
    //  方块破坏事件 → 便利功能
    // ============================================================

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (!FeatureManager.hasEternalHeart(player)) return;
        if (player.isCreative()) return;
        FeatureManager.dispatchBlockBreak(player, event);
    }

    // ============================================================
    //  负面效果预防（Applicable 事件）→ 在效果施加前拒绝
    // ============================================================

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!FeatureManager.hasEternalHeart(player)) return;
        if (!EternalHeartConfig.DEBUFF_CLEAR_INTERVAL.get().equals(0)
                && EternalHeartConfig.DEBUFF_CLEAR_INTERVAL.get() > 0) {
            // 防御系统的负面效果清除逻辑：直接在这里拦截
            MobEffect effect = event.getEffectInstance().getEffect();
            if (effect.getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
                event.setResult(Event.Result.DENY);
                return;
            }
            // 检查属性修饰符是否有负面效果
            if (!effect.getAttributeModifiers().isEmpty()) {
                for (var mod : effect.getAttributeModifiers().values()) {
                    if (mod.getAmount() < 0) {
                        event.setResult(Event.Result.DENY);
                        return;
                    }
                }
            }
        }
    }

    // ============================================================
    //  和平光环 → 阻止敌对生物生成
    // ============================================================

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Monster)) return;

        double range = EternalHeartConfig.PEACE_AURA_RANGE.get();
        if (range <= 0) return;

        AABB area = event.getEntity().getBoundingBox().inflate(range);
        List<Player> players = event.getLevel().getEntitiesOfClass(Player.class, area,
                p -> FeatureManager.hasEternalHeart(p));
        if (!players.isEmpty()) {
            event.setCanceled(true);
        }
    }

    // ============================================================
    //  便携末影箱 → 潜行+右键空气/方块
    // ============================================================

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        handleRemoteEnderChest(event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().getBlockState(event.getPos()).getMenuProvider(event.getLevel(), event.getPos()) != null)
            return;
        handleRemoteEnderChest(event.getEntity(), event);
    }

    private static void handleRemoteEnderChest(Player player, PlayerInteractEvent event) {
        if (!FeatureManager.hasEternalHeart(player)) return;
        if (!EternalHeartConfig.ENDER_CHEST_REMOTE.get()) return;
        if (!player.isCrouching()) return;
        if (!player.getMainHandItem().isEmpty()) return;
        if (player.level().isClientSide()) return;

        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> ChestMenu.threeRows(id, inv, p.getEnderChestInventory()),
                Component.translatable("container.enderchest")));
        event.setCanceled(true);
    }

    // ============================================================
    //  经验倍率
    // ============================================================

    @SubscribeEvent
    public static void onXpDrop(LivingExperienceDropEvent event) {
        Player player = event.getAttackingPlayer();
        if (player == null) return;
        if (!FeatureManager.hasEternalHeart(player)) return;
        int multiplied = (int) (event.getDroppedExperience() * EternalHeartConfig.XP_MULTIPLIER.get());
        event.setDroppedExperience(multiplied);
    }

    // ============================================================
    //  抢夺加成
    // ============================================================

    @SubscribeEvent
    public static void onLootingLevel(LootingLevelEvent event) {
        if (event.getDamageSource() == null) return;
        if (!(event.getDamageSource().getEntity() instanceof Player player)) return;
        if (!FeatureManager.hasEternalHeart(player)) return;
        event.setLootingLevel(event.getLootingLevel() + EternalHeartConfig.LOOTING_BONUS.get());
    }

    // ============================================================
    //  无限箭矢 — 弓的 ArrowLooseEvent 拦截
    // ============================================================

    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        if (!EternalHeartConfig.INFINITE_ARROWS.get()) return;
        Player player = event.getEntity();
        if (!FeatureManager.hasEternalHeart(player)) return;
        if (player.getAbilities().instabuild) return;

        ItemStack bow = event.getBow();
        if (!bow.is(Items.BOW)) return;

        // 有真实箭矢或有无限附魔 → 交给原版
        if (!player.getProjectile(bow).isEmpty()) return;
        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, bow) > 0) return;

        float power = BowItem.getPowerForTime(event.getCharge());
        if (power < 0.1F) return;

        event.setCanceled(true);
        if (!(player.level() instanceof ServerLevel level)) return;

        Arrow arrow = new Arrow(level, player);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, power * 3.0F, 1.0F);

        if (power >= 1.0F) arrow.setCritArrow(true);

        int powerLvl = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, bow);
        if (powerLvl > 0)
            arrow.setBaseDamage(arrow.getBaseDamage() + powerLvl * 0.5D + 0.5D);

        int punchLvl = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, bow);
        if (punchLvl > 0) arrow.setKnockback(punchLvl);

        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, bow) > 0)
            arrow.setSecondsOnFire(100);

        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;

        level.addFreshEntity(arrow);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS,
                1.0F, 1.0F / (level.random.nextFloat() * 0.4F + 1.2F) + power * 0.5F);

        bow.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));
    }
}
