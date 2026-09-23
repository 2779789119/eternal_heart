package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.core.Scan;
import com.eternal_heart.core.Ticker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 光环系统 —— 伤害光环、凋零光环、生长光环。
 *
 * <p>重写要点：</p>
 * <ul>
 *   <li><b>实体扫描合并</b>：伤害光环与凋零光环在同一 tick 到期时只做一次范围查询，
 *       各自按自身半径过滤后应用（历史实现是两次独立查询）；</li>
 *   <li><b>按需收集</b>：生长光环只在配置开启自动补种时才收集成熟作物，
 *       候选列表按实际数量惰性创建，常见情况（无作物）零分配；</li>
 *   <li><b>相位错开</b>：所有周期改用 {@link Ticker}，多人服务器上不同玩家的方块扫描
 *       不再集中在同一 tick；</li>
 *   <li>Boss 判定统一走 {@link Scan#isBoss}（与战斗系统同一来源）。</li>
 * </ul>
 */
public class AuraSystem implements IFeature {

    /** 生长光环垂直扫描范围：作物 / 树苗不会悬空更远（相比整立方体显著减少方块查询）。 */
    private static final int GROWTH_VERTICAL = 3;

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
        if (level.isClientSide()) return;

        long tick = level.getGameTime();
        applyCombatAuras(player, tick);
        if (Ticker.due(player.getUUID(), tick, 20)) applyGrowthAura(player);
    }

    // ============================================================
    //  伤害 / 凋零光环（合并扫描）
    // ============================================================

    private void applyCombatAuras(Player player, long tick) {
        Level level = player.level();

        double damageRange = ConfigValues.get(EternalHeartConfig.DAMAGE_AURA_ENABLED)
                ? ConfigValues.get(EternalHeartConfig.DAMAGE_AURA_RANGE) : 0.0;
        int damageInterval = ConfigValues.get(EternalHeartConfig.DAMAGE_AURA_TICK_INTERVAL);
        boolean damageDue = damageRange > 0 && damageInterval > 0
                && Ticker.due(player.getUUID(), tick, damageInterval);

        double witherRange = ConfigValues.get(EternalHeartConfig.WITHER_AURA_ENABLED)
                ? ConfigValues.get(EternalHeartConfig.WITHER_AURA_RANGE) : 0.0;
        boolean witherDue = witherRange > 0 && Ticker.due(player.getUUID(), tick, 20);

        if (!damageDue && !witherDue) return;

        // 一次查询覆盖两个光环：各自按自身半径过滤
        double scanRange = Math.max(damageDue ? damageRange : 0, witherDue ? witherRange : 0);
        AABB damageBox = damageDue ? Scan.box(player, damageRange) : null;
        float damage = damageDue ? auraDamage(damageInterval) : 0;
        float bossMultiplier = ConfigValues.get(EternalHeartConfig.DAMAGE_AURA_BOSS_MULT).floatValue();

        for (LivingEntity target : Scan.hostiles(level, player, scanRange)) {
            if (damageBox != null && damageBox.intersects(target.getBoundingBox())) {
                float amount = Scan.isBoss(target) ? damage * bossMultiplier : damage;
                target.hurt(player.damageSources().magic(), amount);
            }
            if (witherDue) {
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1, false, true));
            }
        }
    }

    /** 单次触发的伤害 = 配置值 × 触发间隔（保持「每秒伤害」与间隔无关）。 */
    private static float auraDamage(int interval) {
        return ConfigValues.get(EternalHeartConfig.DAMAGE_AURA_DAMAGE).floatValue() * (interval / 20f);
    }

    // ============================================================
    //  生长光环
    // ============================================================

    /** 加速周围作物生长：以配置概率施加骨粉，可选自动补种成熟作物。 */
    private void applyGrowthAura(Player player) {
        if (!ConfigValues.get(EternalHeartConfig.GROWTH_AURA_ENABLED)) return;
        double range = ConfigValues.get(EternalHeartConfig.GROWTH_AURA_RANGE);
        if (range <= 0) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        BlockPos center = player.blockPosition();
        int radius = (int) Math.ceil(range);
        double rangeSq = range * range;
        boolean autoReplant = ConfigValues.get(EternalHeartConfig.GROWTH_AUTO_REPLANT);

        List<BlockPos> candidates = null;
        List<BlockPos> mature = null;

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -GROWTH_VERTICAL, -radius),
                center.offset(radius, GROWTH_VERTICAL, radius))) {

            if (pos.distSqr(center) > rangeSq) continue;

            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BonemealableBlock bonemealable)) continue;
            if (!bonemealable.isValidBonemealTarget(level, pos, state, false)) continue;

            if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                if (autoReplant) {
                    if (mature == null) mature = new ArrayList<>(8);
                    mature.add(pos.immutable());
                }
            } else {
                if (candidates == null) candidates = new ArrayList<>(16);
                candidates.add(pos.immutable());
            }
        }

        if (candidates != null) {
            float chance = ConfigValues.get(EternalHeartConfig.GROWTH_AURA_CHANCE).floatValue();
            int limit = ConfigValues.get(EternalHeartConfig.GROWTH_AURA_MAX_BLOCKS);
            int grown = 0;
            for (BlockPos pos : candidates) {
                if (grown >= limit) break;
                if (level.random.nextFloat() >= chance) continue;

                // 二次校验：第一次收集后世界可能已变化
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof BonemealableBlock bonemealable
                        && bonemealable.isValidBonemealTarget(level, pos, state, false)) {
                    bonemealable.performBonemeal(level, level.random, pos, state);
                    grown++;
                }
            }
        }

        if (mature != null) replant(level, mature);
    }

    /** 自动补种：收获成熟作物后立即种回幼苗（仅限种在耕地上的作物）。 */
    private static void replant(ServerLevel level, List<BlockPos> matureCrops) {
        for (BlockPos cropPos : matureCrops) {
            BlockState cropState = level.getBlockState(cropPos);
            if (!(cropState.getBlock() instanceof CropBlock crop)) continue;
            if (!(level.getBlockState(cropPos.below()).getBlock() instanceof FarmBlock)) continue;

            level.destroyBlock(cropPos, true);
            level.setBlock(cropPos, crop.getStateForAge(0), 3);
        }
    }
}
