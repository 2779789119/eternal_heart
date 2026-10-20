package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.UUID;

/**
 * 攻击压制 —— 被永恒之心击中的目标「回不了血，也保不住增益」。
 *
 * <p>两大效果：</p>
 * <ol>
 *   <li><b>禁疗</b>：命中时把目标登记为「被压制」，一段时间内它的任何回血都被拒绝
 *       （生命回复 / 再生药水 / 治疗药水 / 金苹果 / 自然恢复 / 命令与其它模组的回血）。</li>
 *   <li><b>清除增益</b>：命中时移除目标身上全部有益效果（与「剥离增益效果」共用同一实现）。</li>
 * </ol>
 *
 * <p><b>为什么不做一个自定义 {@code MobEffect}</b>：效果会被生物自身的「效果免疫」拦掉
 * （凋灵、末影龙、带免疫标签的 BOSS 恰好是最需要禁疗的目标），而禁疗是压制流的核心能力，
 * 不能在最关键的敌人身上静默失效。因此这里用一张轻量记账表保存「到期时刻」，
 * 由 {@code LivingEntity#heal} / {@code LivingEntity#setHealth} 两个注入点直接判定 ——
 * 不依赖效果系统，也无法被「清除负面效果」类机制顺手解掉。</p>
 *
 * <p>记账表按实体 UUID 保存到期游戏时间，过期条目惰性删除，并做周期性清扫避免长期
 * 刷怪导致表无限增长（与 {@link CombatSystem} 的冷却表同一套思路）。</p>
 */
public class SuppressionSystem implements IFeature {

    /** 禁疗到期时刻（实体 UUID → 到期游戏时间）。 */
    private static final Object2LongOpenHashMap<UUID> SUPPRESSED_UNTIL = new Object2LongOpenHashMap<>();

    /** 清扫周期（tick）与触发清扫的条目数阈值。 */
    private static final int SWEEP_INTERVAL = 600;
    private static final int SWEEP_THRESHOLD = 256;

    /** 单次压制的最长秒数（防溢出：秒 × 20 写入 long，但仍给个理智上限）。 */
    private static final int MAX_SECONDS = 1_000_000;

    private static long lastSweep;

    @Override
    public String getName() {
        return "SuppressionSystem";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * 该实体此刻是否处于禁疗状态。
     *
     * <p>{@code heal} / {@code setHealth} 的注入点共用此判定（热路径：无记录时只查一次空表）。
     * 客户端同样返回 {@code false} —— 服务端才是记账方，单人游戏两边共表也无副作用。</p>
     */
    public static boolean suppressed(LivingEntity entity) {
        if (SUPPRESSED_UNTIL.isEmpty()) return false;
        UUID id = entity.getUUID();
        long until = SUPPRESSED_UNTIL.getLong(id);
        if (until == 0L) return false;

        if (entity.level() == null || entity.level().getGameTime() >= until) {
            SUPPRESSED_UNTIL.removeLong(id);
            return false;
        }
        return true;
    }

    // ============================================================
    //  命中结算
    // ============================================================

    @Override
    public void onTargetHurt(Player player, LivingHurtEvent event) {
        if (!ConfigValues.get(EternalHeartConfig.SUPPRESSION_ENABLED)) return;

        LivingEntity target = event.getEntity();
        if (target == player || !target.isAlive()) return;
        if (target.level().isClientSide()) return; // 记账只在服务端进行，客户端由同步的血量体现

        // 玩家默认不吃压制：联机里互相禁疗会变成无解的死循环
        if (target instanceof Player && !ConfigValues.get(EternalHeartConfig.SUPPRESSION_AFFECT_PLAYERS)) return;

        long now = target.level().getGameTime();

        if (ConfigValues.get(EternalHeartConfig.SUPPRESSION_ANTI_HEAL)) {
            int seconds = Math.min(ConfigValues.get(EternalHeartConfig.SUPPRESSION_DURATION), MAX_SECONDS);
            if (seconds > 0) {
                // 每次命中刷新持续时间：持续挂住的敌人会一直被压在「回不了血」的状态里
                SUPPRESSED_UNTIL.put(target.getUUID(), now + (long) seconds * 20L);
                sweep(now);
            }
        }

        if (ConfigValues.get(EternalHeartConfig.SUPPRESSION_STRIP_BENEFITS)) {
            CombatSystem.stripBenefits(target);
        }
    }

    /** 周期性清扫过期条目（只在表变大后触发，平时零开销）。 */
    private static void sweep(long now) {
        if (SUPPRESSED_UNTIL.size() < SWEEP_THRESHOLD) return;
        if (now - lastSweep < SWEEP_INTERVAL) return;
        lastSweep = now;

        ObjectIterator<Object2LongMap.Entry<UUID>> iterator = SUPPRESSED_UNTIL.object2LongEntrySet().fastIterator();
        while (iterator.hasNext()) {
            if (iterator.next().getLongValue() <= now) iterator.remove();
        }
    }
}
