package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.core.Debuffs;
import com.eternal_heart.core.Scan;
import com.eternal_heart.core.Ticker;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 防御系统 —— 免疫、闪避、减伤、上限、护盾、反伤、负面效果拦截。
 *
 * <p>重写要点：</p>
 * <ul>
 *   <li><b>负面效果判定缓存</b>：改由 {@link Debuffs} 统一承担（开放寻址哈希缓存），
 *       预防层与清扫层共用同一份判定，不再是每次调用都做注册表查表 + 字符串比较；</li>
 *   <li><b>伤害流水线拆分为纯函数</b>：{@code immuneTo / mitigate / retaliate}
 *       各自只做一件事，便于单独推演数值（历史实现是一段两百行的顺序脚本）；</li>
 *   <li><b>零分配清扫</b>：常见情况（没有负面效果）不再构造任何集合；</li>
 *   <li><b>相位错开</b>：周期清扫改用 {@link Ticker}，多人服务器上不同玩家不再同一 tick 同时扫描。</li>
 * </ul>
 *
 * <p>数值语义与原实现逐条对齐：免疫判定顺序、摔落系数、闪避、减伤链、
 * 单次伤害上限、满怒额外 -5%、护盾破碎击退、受伤加速、反伤 + 闪电反射、击退免疫。</p>
 */
public class DefenseSystem implements IFeature {

    /** 全额免疫的火焰类伤害（与原实现一致：营火等其它火焰类型不在免除范围）。 */
    private static final List<ResourceKey<DamageType>> FIRE_DAMAGE = List.of(
            DamageTypes.ON_FIRE, DamageTypes.IN_FIRE, DamageTypes.LAVA, DamageTypes.HOT_FLOOR);

    @Override
    public String getName() {
        return "DefenseSystem";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * 判定某个效果是否属于「应拦截的负面效果」。
     * 预防层（{@code MobEffectEvent.Applicable}）与周期清扫层共用此唯一判定。
     */
    public static boolean isDebuffBlocked(MobEffect effect) {
        return Debuffs.blocked(effect);
    }

    // ============================================================
    //  Tick：护盾充能与周期清扫
    // ============================================================

    @Override
    public void onPlayerTick(Player player, LivingTickEvent event) {
        applyShield(player);

        if (!ConfigValues.get(EternalHeartConfig.DEBUFF_GUARD)) return;
        int interval = ConfigValues.get(EternalHeartConfig.DEBUFF_CLEAR_INTERVAL);
        if (interval > 0 && Ticker.due(player.getUUID(), player.level().getGameTime(), interval)) {
            clearNegativeEffects(player);
        }
    }

    /** 佩戴瞬间立即净化一次：不必等第一个清扫周期（默认最长 3 秒）。 */
    @Override
    public void onEquip(Player player) {
        if (ConfigValues.get(EternalHeartConfig.DEBUFF_GUARD)) clearNegativeEffects(player);
    }

    @Override
    public void onUnequip(Player player) {
        OwnedShield.release(player);
    }

    /** 满血时按配置速率充能吸收护盾（只记账我们贡献的部分）。 */
    private void applyShield(Player player) {
        OwnedShield.reconcile(player);

        float perSecond = ConfigValues.get(EternalHeartConfig.SHIELD_PER_SEC).floatValue();
        float cap = ConfigValues.get(EternalHeartConfig.SHIELD_MAX).floatValue();
        if (perSecond <= 0 || cap <= 0) {
            OwnedShield.release(player);
            return;
        }
        if (player.getHealth() < player.getMaxHealth()) return;
        OwnedShield.charge(player, perSecond / 20f, cap);
    }

    /** 周期清扫：移除所有被判定为负面的效果（预防层被绕开时的兜底）。 */
    private static void clearNegativeEffects(Player player) {
        List<MobEffect> doomed = null;
        for (MobEffectInstance instance : player.getActiveEffects()) {
            if (Debuffs.blocked(instance.getEffect())) {
                if (doomed == null) doomed = new ArrayList<>(4);
                doomed.add(instance.getEffect());
            }
        }
        if (doomed == null) return;
        for (MobEffect effect : doomed) player.removeEffect(effect);
    }

    // ============================================================
    //  受伤处理流水线
    // ============================================================

    @Override
    public void onPlayerHurt(Player player, LivingHurtEvent event) {
        DamageSource source = event.getSource();
        float incoming = event.getAmount();

        // 1) 全额免疫
        if (immuneTo(source)) {
            event.setCanceled(true);
            return;
        }

        // 2) 摔落：按比例结算（0 视为完全免疫）
        if (source.is(DamageTypes.FALL)) {
            float ratio = ConfigValues.get(EternalHeartConfig.FALL_DAMAGE_RATIO).floatValue();
            if (ratio <= 0) {
                event.setCanceled(true);
                return;
            }
            event.setAmount(incoming * ratio);
            return;
        }

        // 3) 闪避
        double dodge = ConfigValues.get(EternalHeartConfig.DODGE_CHANCE);
        if (dodge > 0 && player.getRandom().nextFloat() < dodge) {
            event.setCanceled(true);
            return;
        }

        // 4) 减伤管线
        float reduced = mitigate(player, source, incoming);
        event.setAmount(reduced);

        // 5) 受伤反馈与反击
        knockShieldBreakers(player, incoming);
        applySurvivalBurst(player, reduced);
        retaliate(player, source, reduced);
        suppressKnockback(player);
    }

    /** 全额免疫判定（窒息 / 仙人掌 / 火焰 / 溺水）。 */
    private static boolean immuneTo(DamageSource source) {
        if (ConfigValues.get(EternalHeartConfig.SUFFOCATE_IMMUNE) && source.is(DamageTypes.IN_WALL)) return true;
        if (ConfigValues.get(EternalHeartConfig.CACTUS_IMMUNE) && source.is(DamageTypes.CACTUS)) return true;
        if (ConfigValues.get(EternalHeartConfig.FIRE_IMMUNE)) {
            for (ResourceKey<DamageType> type : FIRE_DAMAGE) {
                if (source.is(type)) return true;
            }
        }
        return ConfigValues.get(EternalHeartConfig.DROWN_IMMUNE) && source.is(DamageTypes.DROWN);
    }

    /**
     * 减伤管线：基础减免 → 弹射 / 魔法 / 爆炸专项减免 → 单次伤害上限 → 满怒额外 -5%。
     * 纯函数，除读取玩家生命上限外无副作用。
     */
    private static float mitigate(Player player, DamageSource source, float amount) {
        float reduced = amount * (1f - ConfigValues.get(EternalHeartConfig.DAMAGE_REDUCTION).floatValue());

        float projectile = ConfigValues.get(EternalHeartConfig.PROJECTILE_RESIST).floatValue();
        if (projectile > 0 && source.is(DamageTypeTags.IS_PROJECTILE)) {
            reduced *= (1f - projectile);
        }

        float magic = ConfigValues.get(EternalHeartConfig.MAGIC_RESIST).floatValue();
        if (magic > 0 && (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC))) {
            reduced *= (1f - magic);
        }

        if (source.is(DamageTypes.EXPLOSION) || source.is(DamageTypes.PLAYER_EXPLOSION)) {
            reduced *= (1f - ConfigValues.get(EternalHeartConfig.EXPLOSION_RESIST).floatValue());
        }

        float capRatio = ConfigValues.get(EternalHeartConfig.DAMAGE_CAP_RATIO).floatValue();
        if (capRatio > 0) {
            reduced = Math.min(reduced, player.getMaxHealth() * capRatio);
        }

        // 满永怒额外 -5% 伤害减免（与 CombatSystem 满怒 +5% 增伤对称）
        if (FurySystem.isFuryMaxed(player)) {
            reduced *= 0.95f;
        }
        // 配置上限已放开：减免比例可以超过 100%，此时结果会变成负数——
        // 负数伤害在原版会反过来给玩家回血，因此钳制到 0（减免最多把伤害压到 0）。
        return Math.max(0f, reduced);
    }

    /** 护盾被一次打穿时，把附近敌人震开（视觉反馈）。 */
    private static void knockShieldBreakers(Player player, float incoming) {
        float radius = ConfigValues.get(EternalHeartConfig.SHIELD_BREAK_KNOCKBACK).floatValue();
        if (radius <= 0 || incoming <= 0) return;
        float absorption = player.getAbsorptionAmount();
        if (absorption <= 0 || incoming < absorption) return;

        for (LivingEntity enemy : Scan.hostiles(player.level(), player, radius, e -> e != player)) {
            Vec3 push = enemy.position().subtract(player.position()).normalize().scale(2.0).add(0, 0.5, 0);
            enemy.setDeltaMovement(push);
            enemy.hurtMarked = true;
        }
    }

    /** 受伤后短暂加速（生存本能）。 */
    private static void applySurvivalBurst(Player player, float taken) {
        int duration = ConfigValues.get(EternalHeartConfig.SPEED_BURST_DURATION);
        if (duration <= 0 || taken <= 0) return;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration,
                ConfigValues.get(EternalHeartConfig.SPEED_BURST_LEVEL), false, false, true));
    }

    /** 反伤（近战反弹 + 受伤反弹叠加）与闪电反射。 */
    private static void retaliate(Player player, DamageSource source, float dealt) {
        LivingEntity attacker = attackerOf(source, player);
        if (attacker == null) return;

        float ratio = ConfigValues.get(EternalHeartConfig.REFLECT_RATIO).floatValue()
                + ConfigValues.get(EternalHeartConfig.COUNTER_DAMAGE_RATIO).floatValue();
        if (ratio > 0) {
            if (ConfigValues.get(EternalHeartConfig.COUNTER_INVULN_RESET)) {
                attacker.invulnerableTime = 0;
            }
            attacker.hurt(player.damageSources().magic(), dealt * ratio);
        }

        if (ConfigValues.get(EternalHeartConfig.LIGHTNING_REFLECT)
                && player.getRandom().nextFloat() < ConfigValues.get(EternalHeartConfig.LIGHTNING_REFLECT_CHANCE)
                && player.level() instanceof ServerLevel level) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(attacker.getX(), attacker.getY(), attacker.getZ());
                level.addFreshEntity(bolt);
            }
        }
    }

    private static LivingEntity attackerOf(DamageSource source, Player player) {
        LivingEntity attacker = source.getEntity() instanceof LivingEntity living ? living : null;
        if (attacker == null && source.getDirectEntity() instanceof LivingEntity living) attacker = living;
        return attacker != null && attacker != player && attacker.isAlive() ? attacker : null;
    }

    private static void suppressKnockback(Player player) {
        if (!ConfigValues.get(EternalHeartConfig.KNOCKBACK_IMMUNITY)) return;
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(0, motion.y, 0);
    }
}
