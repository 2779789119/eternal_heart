package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

public class AuraSystem implements IFeature {

    private static final TagKey<EntityType<?>> BOSS_TAG =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("forge", "bosses"));

    @Override
    public String getName() {
        return "AuraSystem";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void onPlayerTick(Player player, LivingTickEvent event) {
        Level level = player.level();
        if (level.isClientSide) return;

        long tick = level.getGameTime();

        applyDamageAura(player, tick);
        applyWitherAura(player, tick);
        applyGrowthAura(player, tick);
    }

    private void applyDamageAura(Player player, long tick) {
        double range = EternalHeartConfig.DAMAGE_AURA_RANGE.get();
        int interval = EternalHeartConfig.DAMAGE_AURA_TICK_INTERVAL.get();
        if (range <= 0 || interval <= 0 || tick % interval != 0) return;

        float baseDmg = EternalHeartConfig.DAMAGE_AURA_DAMAGE.get().floatValue()
                * (interval / 20f);
        float bossMult = EternalHeartConfig.DAMAGE_AURA_BOSS_MULT.get().floatValue();

        List<LivingEntity> targets = player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(range),
                e -> e != player && e.isAlive() && e instanceof Enemy);

        for (LivingEntity t : targets) {
            float dmg = baseDmg;
            if (isBoss(t)) dmg *= bossMult;
            t.hurt(player.damageSources().magic(), dmg);
        }
    }

    private void applyWitherAura(Player player, long tick) {
        double range = EternalHeartConfig.WITHER_AURA_RANGE.get();
        if (range <= 0 || tick % 20 != 0) return;

        List<LivingEntity> targets = player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(range),
                e -> e != player && e.isAlive() && e instanceof Enemy);

        for (LivingEntity t : targets) {
            t.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1, false, true));
        }
    }

    private void applyGrowthAura(Player player, long tick) {
        double range = EternalHeartConfig.GROWTH_AURA_RANGE.get();
        if (range <= 0 || tick % 20 != 0) return;

        Level level = player.level();
        BlockPos center = player.blockPosition();
        int r = (int) Math.ceil(range);
        float chance = EternalHeartConfig.GROWTH_AURA_CHANCE.get().floatValue();
        int maxBlocks = EternalHeartConfig.GROWTH_AURA_MAX_BLOCKS.get();
        boolean autoReplant = EternalHeartConfig.GROWTH_AUTO_REPLANT.get();

        List<BlockPos> bonemealCandidates = new ArrayList<>();
        List<BlockPos> matureCrops = new ArrayList<>();

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-r, -r, -r),
                center.offset(r, r, r))) {
            if (pos.distSqr(center) > range * range) continue;

            BlockState state = level.getBlockState(pos);

            if (state.getBlock() instanceof BonemealableBlock bonemealable && bonemealable.isValidBonemealTarget(level, pos, state, level.isClientSide)) {
                if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                    matureCrops.add(pos.immutable());
                } else {
                    bonemealCandidates.add(pos.immutable());
                }
            }
        }

        int bonemealed = 0;
        if (level instanceof ServerLevel serverLevel) {
            for (BlockPos pos : bonemealCandidates) {
                if (bonemealed >= maxBlocks) break;
                if (level.random.nextFloat() < chance) {
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof BonemealableBlock bonemealable
                            && bonemealable.isValidBonemealTarget(level, pos, state, level.isClientSide)) {
                        bonemealable.performBonemeal(serverLevel, level.random, pos, state);
                        bonemealed++;
                    }
                }
            }
        }

        if (autoReplant) {
            for (BlockPos cropPos : matureCrops) {
                BlockState cropState = level.getBlockState(cropPos);
                if (!(cropState.getBlock() instanceof CropBlock crop)) continue;

                BlockPos belowPos = cropPos.below();
                BlockState belowState = level.getBlockState(belowPos);
                if (!(belowState.getBlock() instanceof FarmBlock)) continue;

                level.destroyBlock(cropPos, true);
                level.setBlock(cropPos, crop.getStateForAge(0), 3);
            }
        }
    }

    private boolean isBoss(LivingEntity entity) {
        return entity.getType().is(BOSS_TAG)
                || (entity instanceof Enemy && entity.getMaxHealth() >= 100);
    }
}
