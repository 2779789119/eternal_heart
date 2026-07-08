package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.List;

public class CombatSystem implements IFeature {

    private static final String EXTRA_HIT_CD_KEY = "eternal_heart_extra_hit_cd";

    @SuppressWarnings("unchecked")
    private static final TagKey<EntityType<?>> BOSS_TAG =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("forge", "bosses"));

    @Override
    public String getName() {
        return "CombatSystem";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void onPlayerAttack(Player player, LivingAttackEvent event) {
        LivingEntity target = event.getEntity();

        if (player.getRandom().nextFloat() < EternalHeartConfig.IGNITE_CHANCE.get().floatValue()) {
            target.setSecondsOnFire(EternalHeartConfig.IGNITE_DURATION.get());
        }
    }

    @Override
    public void onTargetHurt(Player player, LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target == player) return;

        float currentDmg = event.getAmount();

        float bossBonus = EternalHeartConfig.BOSS_DAMAGE_BONUS.get().floatValue();
        if (bossBonus > 0 && isBoss(target)) {
            currentDmg *= (1f + bossBonus);
        }

        float threshold = EternalHeartConfig.LOW_HP_THRESHOLD.get().floatValue();
        if (threshold > 0 && player.getHealth() / player.getMaxHealth() <= threshold) {
            currentDmg *= EternalHeartConfig.LOW_HP_DAMAGE_MULT.get().floatValue();
        }

        float executeThreshold = EternalHeartConfig.EXECUTE_THRESHOLD.get().floatValue();
        if (executeThreshold > 0 && target.getHealth() / target.getMaxHealth() <= executeThreshold) {
            target.hurt(player.damageSources().magic(), 9999f);
            event.setCanceled(true);
            return;
        }

        float glowBonus = EternalHeartConfig.GLOW_DAMAGE_BONUS.get().floatValue();
        if (glowBonus > 0 && target.hasEffect(MobEffects.GLOWING)) {
            currentDmg *= (1f + glowBonus);
        }

        event.setAmount(currentDmg);

        float splashRatio = EternalHeartConfig.AOE_SPLASH_RATIO.get().floatValue();
        double splashRange = EternalHeartConfig.AOE_SPLASH_RANGE.get();
        if (splashRatio > 0 && splashRange > 0 && currentDmg > 0) {
            float splashDmg = currentDmg * splashRatio;
            EntityType<?> targetType = target.getType();
            AABB area = target.getBoundingBox().inflate(splashRange);
            List<LivingEntity> nearby = target.level().getEntitiesOfClass(LivingEntity.class, area,
                    e -> e != target && e != player && e.isAlive()
                            && e.getType() == targetType && e instanceof Enemy);
            for (LivingEntity e : nearby) {
                e.invulnerableTime = 0;
                e.hurt(player.damageSources().playerAttack(player), splashDmg);
            }
        }

        float extraDmg = EternalHeartConfig.EXTRA_HIT_DAMAGE.get().floatValue();
        if (extraDmg > 0) {
            long now = target.level().getGameTime();
            long lastExtra = target.getPersistentData().getLong(EXTRA_HIT_CD_KEY);
            int cooldownTicks = EternalHeartConfig.EXTRA_HIT_COOLDOWN.get();
            if (now - lastExtra >= cooldownTicks) {
                target.getPersistentData().putLong(EXTRA_HIT_CD_KEY, now);
                target.invulnerableTime = 0;
                target.hurt(player.damageSources().magic(), extraDmg);
            }
        }

        if (EternalHeartConfig.BENEFICIAL_STRIP.get()) {
            target.getActiveEffects().stream()
                    .filter(e -> e.getEffect().getCategory() == MobEffectCategory.BENEFICIAL)
                    .map(MobEffectInstance::getEffect)
                    .toList()
                    .forEach(target::removeEffect);
        }
    }

    @Override
    public void onPlayerKill(Player player, LivingDeathEvent event) {
        float healRatio = EternalHeartConfig.KILL_HEAL_RATIO.get().floatValue();
        if (healRatio > 0) player.heal(player.getMaxHealth() * healRatio);

        if (EternalHeartConfig.KILL_EXPLOSION.get()) {
            Entity target = event.getEntity();
            target.level().explode(null, target.getX(), target.getY(), target.getZ(),
                    EternalHeartConfig.KILL_EXPLOSION_POWER.get().floatValue(),
                    Level.ExplosionInteraction.NONE);
        }

        if (EternalHeartConfig.KILL_CHAIN.get()) {
            double kcRange = EternalHeartConfig.KILL_CHAIN_RANGE.get();
            LivingEntity killed = event.getEntity();
            AABB area = killed.getBoundingBox().inflate(kcRange);
            List<LivingEntity> nearby = killed.level().getEntitiesOfClass(LivingEntity.class, area,
                    e -> e != killed && e != player && e.isAlive() && e instanceof Enemy);
            float chainDmg = killed.getMaxHealth() * 0.5f;
            for (LivingEntity e : nearby) {
                e.invulnerableTime = 0;
                e.hurt(player.damageSources().playerAttack(player), chainDmg);
                Vec3 kb = e.position().subtract(killed.position()).normalize().scale(2.5).add(0, 0.6, 0);
                if (kb.lengthSqr() > 0) {
                    e.setDeltaMovement(kb);
                    e.hurtMarked = true;
                }
            }
        }
    }

    private boolean isBoss(LivingEntity entity) {
        return entity.getType().is(BOSS_TAG)
            || (entity instanceof Enemy && entity.getMaxHealth() >= 100);
    }
}
