package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.Set;
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

    /** 清扫周期（tick）。 */
    private static final int SWEEP_INTERVAL = 600;

    /** 单次压制的最长秒数（防溢出：秒 × 20 写入 long，但仍给个理智上限）。 */
    private static final int MAX_SECONDS = 1_000_000;

    /**
     * 治疗类效果：压制期间禁止获得。
     *
     * <p>这些效果都能绕开 {@code heal} 注入点：
     * 吸收值直接是一层额外的血量（金苹果），生命提升抬高上限后再回填，
     * 瞬间治疗走 {@code setHealth} 的抬高路径（部分模组实现会绕过 {@code heal}）。
     * 从施加阶段就拒绝，比事后逐个拦截可靠。</p>
     */
    private static final Set<MobEffect> HEALING_EFFECTS = Set.of(
            MobEffects.HEAL, MobEffects.REGENERATION,
            MobEffects.ABSORPTION, MobEffects.HEALTH_BOOST);

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

    /** 压制期间是否禁止获得吸收值（{@code setAbsorptionAmount} 注入点使用）。 */
    public static boolean absorptionBlocked() {
        if (!ConfigValues.get(EternalHeartConfig.SUPPRESSION_ENABLED)) return false;
        return ConfigValues.get(EternalHeartConfig.SUPPRESSION_STRIP_ABSORPTION);
    }

    /**
     * 该实体此刻是否不能获得这个效果（压制期间的治疗类效果）。
     *
     * <p>由 {@code MobEffectEvent.Applicable} 调用，从施加阶段就拒绝 —— 这条比事后拦截更早：
     * 效果一旦挂上，客户端就会先显示、再生还会立刻回一次血。</p>
     */
    public static boolean blocksHealingEffect(LivingEntity entity, MobEffect effect) {
        if (effect == null || !HEALING_EFFECTS.contains(effect)) return false;
        if (!ConfigValues.get(EternalHeartConfig.SUPPRESSION_ENABLED)) return false;
        if (!ConfigValues.get(EternalHeartConfig.SUPPRESSION_STRIP_HEALING_EFFECTS)) return false;
        return suppressed(entity);
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
                // 吸收值（金苹果的黄心）是等价回血：压制生效瞬间清零，
                // 之后由 setAbsorptionAmount 注入点拒绝重新获得
                if (ConfigValues.get(EternalHeartConfig.SUPPRESSION_STRIP_ABSORPTION)
                        && target.getAbsorptionAmount() > 0f) {
                    target.setAbsorptionAmount(0f);
                }
            }
        }

        if (ConfigValues.get(EternalHeartConfig.SUPPRESSION_STRIP_BENEFITS)) {
            CombatSystem.stripBenefits(target);
        }
    }

    /**
     * 周期性清扫过期条目（空表直接返回，常态零开销）。
     *
     * <p>原实现对表大小也有阈值（≥256 才扫），于是「目标死了、之后再也不被访问」的过期条目
     * 会一直留着，直到表偶然涨大才回收。按时间清扫就够了，空表判断已经把常态开销压到一次 isEmpty。</p>
     */
    private static void sweep(long now) {
        if (SUPPRESSED_UNTIL.isEmpty()) return;
        if (now - lastSweep < SWEEP_INTERVAL) return;
        lastSweep = now;

        ObjectIterator<Object2LongMap.Entry<UUID>> iterator = SUPPRESSED_UNTIL.object2LongEntrySet().fastIterator();
        while (iterator.hasNext()) {
            if (iterator.next().getLongValue() <= now) iterator.remove();
        }
    }
}
