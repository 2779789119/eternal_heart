package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DefenseSystem implements IFeature {

    private Set<String> cachedDebuffBlacklist = Set.of();
    private Set<String> cachedDebuffWhitelist = Set.of();

    @Override
    public String getName() {
        return "DefenseSystem";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void onPlayerTick(Player player, LivingTickEvent event) {
        long tick = player.level().getGameTime();

        applyShield(player);

        int interval = EternalHeartConfig.DEBUFF_CLEAR_INTERVAL.get();
        if (interval > 0 && tick % interval == 0) {
            clearNegativeEffects(player);
        }
    }

    @Override
    public void onPlayerHurt(Player player, LivingHurtEvent event) {
        DamageSource source = event.getSource();
        float original = event.getAmount();

        if (EternalHeartConfig.SUFFOCATE_IMMUNE.get() && source.is(DamageTypes.IN_WALL)) {
            event.setCanceled(true);
            return;
        }
        if (EternalHeartConfig.CACTUS_IMMUNE.get() && source.is(DamageTypes.CACTUS)) {
            event.setCanceled(true);
            return;
        }
        if (EternalHeartConfig.FIRE_IMMUNE.get() && (source.is(DamageTypes.ON_FIRE)
                || source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.LAVA)
                || source.is(DamageTypes.HOT_FLOOR))) {
            event.setCanceled(true);
            return;
        }
        if (EternalHeartConfig.DROWN_IMMUNE.get() && source.is(DamageTypes.DROWN)) {
            event.setCanceled(true);
            return;
        }
        if (source.is(DamageTypes.FALL)) {
            float ratio = EternalHeartConfig.FALL_DAMAGE_RATIO.get().floatValue();
            if (ratio <= 0) {
                event.setCanceled(true);
                return;
            }
            event.setAmount(original * ratio);
            return;
        }

        double dodge = EternalHeartConfig.DODGE_CHANCE.get();
        if (dodge > 0 && player.getRandom().nextFloat() < dodge) {
            event.setCanceled(true);
            return;
        }

        float reduced = original * (1f - EternalHeartConfig.DAMAGE_REDUCTION.get().floatValue());

        float projResist = EternalHeartConfig.PROJECTILE_RESIST.get().floatValue();
        if (projResist > 0 && source.is(DamageTypeTags.IS_PROJECTILE)) {
            reduced *= (1f - projResist);
        }
        float magicResist = EternalHeartConfig.MAGIC_RESIST.get().floatValue();
        if (magicResist > 0 && (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC))) {
            reduced *= (1f - magicResist);
        }

        if (source.is(DamageTypes.EXPLOSION) || source.is(DamageTypes.PLAYER_EXPLOSION)) {
            reduced *= (1f - EternalHeartConfig.EXPLOSION_RESIST.get().floatValue());
        }

        float capRatio = EternalHeartConfig.DAMAGE_CAP_RATIO.get().floatValue();
        if (capRatio > 0) {
            float maxDamage = player.getMaxHealth() * capRatio;
            if (reduced > maxDamage) reduced = maxDamage;
        }

        event.setAmount(reduced);

        float shieldKb = EternalHeartConfig.SHIELD_BREAK_KNOCKBACK.get().floatValue();
        if (shieldKb > 0 && original > 0) {
            float absBefore = player.getAbsorptionAmount();
            if (absBefore > 0 && original >= absBefore) {
                AABB area = player.getBoundingBox().inflate(shieldKb);
                List<LivingEntity> nearby = player.level().getEntitiesOfClass(LivingEntity.class, area,
                        e -> e != player && e.isAlive() && e instanceof Enemy);
                for (LivingEntity e : nearby) {
                    Vec3 kb = e.position().subtract(player.position()).normalize().scale(2.0).add(0, 0.5, 0);
                    e.setDeltaMovement(kb);
                    e.hurtMarked = true;
                }
            }
        }

        int burstDuration = EternalHeartConfig.SPEED_BURST_DURATION.get();
        if (burstDuration > 0 && reduced > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                    burstDuration, EternalHeartConfig.SPEED_BURST_LEVEL.get(),
                    false, false, true));
        }

        float reflect = EternalHeartConfig.REFLECT_RATIO.get().floatValue();
        Entity srcEntity = source.getEntity();
        Entity directEntity = source.getDirectEntity();
        LivingEntity attacker = null;
        if (srcEntity instanceof LivingEntity le) attacker = le;
        else if (directEntity instanceof LivingEntity le) attacker = le;

        if (attacker != null && attacker != player && attacker.isAlive()) {
            if (reflect > 0) {
                if (EternalHeartConfig.COUNTER_INVULN_RESET.get()) {
                    attacker.invulnerableTime = 0;
                }
                attacker.hurt(player.damageSources().magic(), reduced * reflect);
            }

            if (EternalHeartConfig.LIGHTNING_REFLECT.get()) {
                double lrChance = EternalHeartConfig.LIGHTNING_REFLECT_CHANCE.get();
                if (player.getRandom().nextFloat() < lrChance) {
                    if (player.level() instanceof ServerLevel sl) {
                        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(sl);
                        if (lightning != null) {
                            lightning.moveTo(attacker.getX(), attacker.getY(), attacker.getZ());
                            sl.addFreshEntity(lightning);
                        }
                    }
                }
            }
        }

        if (EternalHeartConfig.KNOCKBACK_IMMUNITY.get()) {
            player.setDeltaMovement(player.getDeltaMovement().multiply(0, 1, 0));
        }
    }

    private void applyShield(Player player) {
        float perSec = EternalHeartConfig.SHIELD_PER_SEC.get().floatValue();
        if (perSec <= 0) return;
        if (player.getHealth() < player.getMaxHealth()) return;

        float current = player.getAbsorptionAmount();
        float max = EternalHeartConfig.SHIELD_MAX.get().floatValue();
        if (current < max) {
            player.setAbsorptionAmount(Math.min(max, current + perSec / 20f));
        }
    }

    private void refreshDebuffCaches() {
        cachedDebuffBlacklist = Set.copyOf(EternalHeartConfig.DEBUFF_BLACKLIST.get());
        cachedDebuffWhitelist = Set.copyOf(EternalHeartConfig.DEBUFF_WHITELIST.get());
    }

    private void clearNegativeEffects(Player player) {
        if (cachedDebuffBlacklist.isEmpty() && !EternalHeartConfig.DEBUFF_BLACKLIST.get().isEmpty()) {
            refreshDebuffCaches();
        }

        List<MobEffect> toRemove = new ArrayList<>();
        for (MobEffectInstance instance : player.getActiveEffects()) {
            MobEffect effect = instance.getEffect();
            String key = BuiltInRegistries.MOB_EFFECT.getKey(effect).toString();

            if (cachedDebuffWhitelist.contains(key)) continue;
            if (cachedDebuffBlacklist.contains(key)) {
                toRemove.add(effect);
                continue;
            }
            if (effect.getCategory() == MobEffectCategory.BENEFICIAL) continue;
            if (effect.getCategory() == MobEffectCategory.HARMFUL) {
                toRemove.add(effect);
                continue;
            }

            if (!effect.getAttributeModifiers().isEmpty()) {
                for (var mod : effect.getAttributeModifiers().values()) {
                    if (mod.getAmount() < 0) {
                        toRemove.add(effect);
                        break;
                    }
                }
            }
        }
        for (MobEffect e : toRemove) player.removeEffect(e);
    }
}
