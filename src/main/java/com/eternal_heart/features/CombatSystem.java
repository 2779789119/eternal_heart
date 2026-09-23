package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.core.Scan;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 战斗系统 —— 增伤、斩杀、溅射、额外打击、连锁击杀、击杀反馈。
 *
 * <p>重写要点：</p>
 * <ul>
 *   <li><b>额外打击冷却表</b>：历史实现每次都往目标实体的持久化 NBT 写一个字符串键
 *       （命中热路径上的哈希 + 字符串分配）。现在改用 fastutil 的长整型值表按实体记账，
 *       并做无泄漏的惰性清理（实体死亡 / 卸载后自动回收）。</li>
 *   <li><b>级联伤害防护升级为深度计数</b>：{@link #runCascade} 支持嵌套进入，
 *       内层提前退出不会误解除外层的保护（历史实现用布尔标记，嵌套时会提前解锁）。</li>
 *   <li><b>零分配移除增益</b>：目标身上没有增益效果时不再构造 stream / 列表。</li>
 *   <li>增伤管线按原顺序保留：Boss 加成 → 低血加成 → 满怒 +5% → 斩杀 → 发光加成。</li>
 * </ul>
 */
public class CombatSystem implements IFeature {

    /** 点燃秒数的安全上限（原版按 秒 × 20 写入 tick，过大会 int 溢出）。 */
    private static final int MAX_FIRE_SECONDS = 1_000_000;

    /** 额外打击的下次可用时刻（实体 → 游戏时间）。 */
    private static final Object2LongOpenHashMap<LivingEntity> EXTRA_HIT_AT = new Object2LongOpenHashMap<>();

    /** 冷却表清理周期与触发清理的体积阈值。 */
    private static final int SWEEP_INTERVAL = 600;
    private static final int SWEEP_THRESHOLD = 256;
    private static long lastSweep;

    /**
     * 级联伤害重入深度。
     *
     * <p>本模块的「溅射伤害」与「击杀连锁」都会用 playerAttack 伤害源对附近怪物造成伤害，
     * 而这次伤害会再次触发 onTargetHurt / onPlayerKill，进而再次产生级联伤害——在密集
     * 怪群中形成怪物间的无限递归，把服务端主线程淹没（表现为「能移动但开不了箱子」假死）。</p>
     *
     * <p>用线程级深度计数标记「当前正处于次级伤害处理中」，该期间不再产生新的级联伤害。
     * 用计数而非布尔，保证嵌套调用时内层退出不会误解除外层保护。服务端 / 客户端各线程独立。</p>
     */
    private static final ThreadLocal<int[]> CASCADE_DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    @Override
    public String getName() {
        return "CombatSystem";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    // ============================================================
    //  攻击方
    // ============================================================

    @Override
    public void onPlayerAttack(Player player, LivingAttackEvent event) {
        // 永怒叠层由 FurySystem 处理；固定 50% 暴击率与暴击驱动叠层由 FurySystem.onCriticalHit 处理。
        if (player.getRandom().nextFloat() < ConfigValues.get(EternalHeartConfig.IGNITE_CHANCE).floatValue()) {
            int seconds = Math.min(ConfigValues.get(EternalHeartConfig.IGNITE_DURATION), MAX_FIRE_SECONDS);
            event.getEntity().setSecondsOnFire(Math.max(0, seconds));
        }
    }

    // ============================================================
    //  目标结算
    // ============================================================

    @Override
    public void onTargetHurt(Player player, LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target == player) return;

        long tick = target.level().getGameTime();
        sweepCooldowns(tick);

        // 增伤管线（斩杀前，不含发光加成）
        float damage = amplify(player, target, event.getAmount());

        // 斩杀：按目标最大生命值 10 倍结算，确保任何血量的目标都能被秒杀
        float execute = ConfigValues.get(EternalHeartConfig.EXECUTE_THRESHOLD).floatValue();
        if (execute > 0 && target.getHealth() / target.getMaxHealth() <= execute) {
            event.setCanceled(true);
            target.hurt(player.damageSources().magic(), target.getMaxHealth() * 10f);
            return;
        }

        float glowBonus = ConfigValues.get(EternalHeartConfig.GLOW_DAMAGE_BONUS).floatValue();
        if (glowBonus > 0 && target.hasEffect(MobEffects.GLOWING)) {
            damage *= (1f + glowBonus);
        }
        event.setAmount(damage);

        // 记录我方期望伤害：最终结算阶段用它突破其它模组的单次伤害上限
        rememberExpectedDamage(target, damage, tick);

        // 次级效果（受级联防护保护，避免怪群中的无限递归）
        float splashRatio = ConfigValues.get(EternalHeartConfig.AOE_SPLASH_RATIO).floatValue();
        if (splashRatio > 0 && !cascading()) {
            splash(player, target, damage * splashRatio);
        }
        applyExtraHit(player, target, tick);

        if (ConfigValues.get(EternalHeartConfig.BENEFICIAL_STRIP)) {
            stripBenefits(target);
        }
    }

    /** 增伤管线：Boss 加成 → 低血加成 → 满怒额外 +5%。 */
    private static float amplify(Player player, LivingEntity target, float damage) {
        float bossBonus = ConfigValues.get(EternalHeartConfig.BOSS_DAMAGE_BONUS).floatValue();
        if (bossBonus > 0 && Scan.isBoss(target)) {
            damage *= (1f + bossBonus);
        }

        float threshold = ConfigValues.get(EternalHeartConfig.LOW_HP_THRESHOLD).floatValue();
        if (threshold > 0 && player.getHealth() / player.getMaxHealth() <= threshold) {
            damage *= ConfigValues.get(EternalHeartConfig.LOW_HP_DAMAGE_MULT).floatValue();
        }

        // 永怒每层伤害加成走 EternalFuryEffect 属性修饰符（原版伤害管线）；
        // 满怒额外 +5% 在此集中处理（与 DefenseSystem 满怒 -5% 减伤对称）。
        if (FurySystem.isFuryMaxed(player)) {
            damage *= 1.05f;
        }
        return damage;
    }

    /** 溅射：对目标周围同种敌人造成一定比例的伤害。 */
    private static void splash(Player player, LivingEntity primary, float damage) {
        if (damage <= 0) return;
        double range = ConfigValues.get(EternalHeartConfig.AOE_SPLASH_RANGE);
        if (range <= 0) return;

        EntityType<?> type = primary.getType();
        List<LivingEntity> nearby = primary.level().getEntitiesOfClass(LivingEntity.class,
                Scan.box(primary, range),
                e -> e != primary && e != player && e.isAlive() && e.getType() == type && e instanceof Enemy);
        if (nearby.isEmpty()) return;

        runCascade(() -> {
            for (LivingEntity enemy : nearby) strike(player, enemy, damage);
        });
    }

    /** 额外打击：对同一目标的追加魔法伤害，带独立冷却。 */
    private static void applyExtraHit(Player player, LivingEntity target, long tick) {
        float extra = ConfigValues.get(EternalHeartConfig.EXTRA_HIT_DAMAGE).floatValue();
        if (extra <= 0) return;

        long last = EXTRA_HIT_AT.getLong(target);
        if (tick - last < ConfigValues.get(EternalHeartConfig.EXTRA_HIT_COOLDOWN)) return;

        EXTRA_HIT_AT.put(target, tick);
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().magic(), extra);
    }

    private static void stripBenefits(LivingEntity target) {
        List<MobEffect> doomed = null;
        for (MobEffectInstance instance : target.getActiveEffects()) {
            if (instance.getEffect().getCategory() == MobEffectCategory.BENEFICIAL) {
                if (doomed == null) doomed = new ArrayList<>(4);
                doomed.add(instance.getEffect());
            }
        }
        if (doomed == null) return;
        for (MobEffect effect : doomed) target.removeEffect(effect);
    }

    // ============================================================
    //  击杀方
    // ============================================================

    @Override
    public void onPlayerKill(Player player, LivingDeathEvent event) {
        float healRatio = ConfigValues.get(EternalHeartConfig.KILL_HEAL_RATIO).floatValue();
        if (healRatio != 0) Health.apply(player, player.getMaxHealth() * healRatio);

        LivingEntity killed = event.getEntity();

        // 爆炸威力允许负数（配置下限已放开），但负半径对原版爆炸没有意义，钳制到 0
        float power = Math.max(0f, ConfigValues.get(EternalHeartConfig.KILL_EXPLOSION_POWER).floatValue());
        if (ConfigValues.get(EternalHeartConfig.KILL_EXPLOSION) && power > 0) {
            killed.level().explode(null, killed.getX(), killed.getY(), killed.getZ(),
                    power, Level.ExplosionInteraction.NONE);
        }

        if (ConfigValues.get(EternalHeartConfig.KILL_CHAIN) && !cascading()) {
            killChain(player, killed);
        }
    }

    /** 击杀连锁：对附近敌人造成被击杀者最大生命值 50% 的伤害并击退。 */
    private static void killChain(Player player, LivingEntity killed) {
        double range = ConfigValues.get(EternalHeartConfig.KILL_CHAIN_RANGE);
        if (range <= 0) return;

        List<LivingEntity> nearby = killed.level().getEntitiesOfClass(LivingEntity.class,
                Scan.box(killed, range),
                e -> e != killed && e != player && e.isAlive() && e instanceof Enemy);
        if (nearby.isEmpty()) return;

        float chainDamage = killed.getMaxHealth() * 0.5f;
        runCascade(() -> {
            for (LivingEntity enemy : nearby) {
                strike(player, enemy, chainDamage);
                Vec3 push = enemy.position().subtract(killed.position()).normalize().scale(2.5).add(0, 0.6, 0);
                if (push.lengthSqr() > 0) {
                    enemy.setDeltaMovement(push);
                    enemy.hurtMarked = true;
                }
            }
        });
    }

    // ============================================================
    //  限伤穿透
    // ============================================================

    /** 我方期望伤害（目标 → 数值）。 */
    private static final Object2FloatOpenHashMap<LivingEntity> EXPECTED_DAMAGE = new Object2FloatOpenHashMap<>();
    /** 记录期望值时的游戏时间（目标 → tick），用于保证只与同一 tick 的最终结算配对。 */
    private static final Object2LongOpenHashMap<LivingEntity> EXPECTED_TICK = new Object2LongOpenHashMap<>();

    private static void rememberExpectedDamage(LivingEntity target, float damage, long tick) {
        if (damage <= 0) return;
        EXPECTED_DAMAGE.put(target, damage);
        EXPECTED_TICK.put(target, tick);
    }

    /**
     * 最终伤害结算阶段（护甲 / 抗性 / 吸收计算之前）：突破其它模组的「单次伤害上限」。
     *
     * <p>原理：很多模组用「在伤害事件里把 amount 压到上限」来实现限伤。我们在自己的伤害
     * 计算完成后把期望值记录下来，等伤害走到<strong>最后一道</strong>事件时比对——
     * 如果被压低了就恢复。</p>
     *
     * <p>三点保证不会帮倒忙：</p>
     * <ul>
     *   <li><b>只提升不压低</b>：其它模组的合法增伤（暴击词缀等）不会被我们改回去；</li>
     *   <li><b>不影响护甲的正当减伤</b>：本阶段拿到的 amount 是「护甲之前」的值，
     *       恢复它不会绕过护甲 / 抗性 / 吸收的正常结算；</li>
     *   <li><b>只与本 tick 的伤害配对</b>：伤害被无敌帧拦掉（事件不触发）时记录自然过期，
     *       不会把上一次的期望值用到下一次攻击上。</li>
     * </ul>
     *
     * @return 是否处理了本次伤害
     */
    public static boolean pierceDamageCap(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        boolean fresh = EXPECTED_DAMAGE.containsKey(target)
                && EXPECTED_TICK.getLong(target) == target.level().getGameTime();
        EXPECTED_TICK.removeLong(target);
        float expected = EXPECTED_DAMAGE.removeFloat(target);
        if (!fresh || expected <= 0) return false;

        if (event.isCanceled()) {
            // 免伤型拦截（直接取消事件）：可选用真实伤害穿透，仅对非玩家目标生效
            if (ConfigValues.get(EternalHeartConfig.PIERCE_IMMUNITY) && !(target instanceof Player)) {
                applyTrueDamage(target, expected, event.getSource());
                return true;
            }
            return false;
        }

        if (!ConfigValues.get(EternalHeartConfig.PIERCE_DAMAGE_CAP)) return false;
        if (event.getAmount() < expected) {
            event.setAmount(expected);
            return true;
        }
        return false;
    }

    /**
     * 攻击在 {@code LivingAttackEvent} 阶段就被拦下（BOSS 无敌阶段、闪避类模组）时的可选穿透。
     *
     * <p>这类拦截发生在伤害管线的最前端，后续的伤害事件根本不会触发，因此必须在
     * 攻击事件里补一份真实伤害。注意此时拿到的是原始伤害值，这里会按本模块的增伤
     * 管线（Boss 加成 / 低血加成 / 满怒 / 发光）自行算一遍再扣血。</p>
     */
    public static void pierceBlockedAttack(Player player, LivingAttackEvent event) {
        if (!ConfigValues.get(EternalHeartConfig.PIERCE_IMMUNITY)) return;

        LivingEntity target = event.getEntity();
        if (target instanceof Player) return;     // 不对玩家生效，避免误伤队友
        if (!target.isAlive()) return;

        float damage = amplify(player, target, event.getAmount());
        float glowBonus = ConfigValues.get(EternalHeartConfig.GLOW_DAMAGE_BONUS).floatValue();
        if (glowBonus > 0 && target.hasEffect(MobEffects.GLOWING)) {
            damage *= (1f + glowBonus);
        }
        if (damage <= 0) return;

        applyTrueDamage(target, damage, player.damageSources().playerAttack(player));
    }

    /** 真实伤害：直接扣血（完全跳过伤害管线），用于突破「取消伤害事件」型的免伤。 */
    private static void applyTrueDamage(LivingEntity target, float damage, DamageSource source) {
        float remaining = target.getHealth() - damage;
        if (remaining <= 0f) {
            target.setHealth(0f);
            target.die(source); // 走正常死亡流程（掉落 / 统计 / 战利品）
        } else {
            target.setHealth(remaining);
        }
    }

    // ============================================================
    //  工具
    // ============================================================

    /** 次级打击：清零无敌帧后结算，保证连续多次命中都能生效。 */
    private static void strike(Player player, LivingEntity target, float damage) {
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().playerAttack(player), damage);
    }

    private static boolean cascading() {
        return CASCADE_DEPTH.get()[0] > 0;
    }

    private static void runCascade(Runnable action) {
        int[] depth = CASCADE_DEPTH.get();
        depth[0]++;
        try {
            action.run();
        } finally {
            depth[0]--;
        }
    }

    /** 惰性清理各实体记账表：移除已死亡 / 已移除的实体，防止长会话内存增长。 */
    private static void sweepCooldowns(long tick) {
        if (tick - lastSweep < SWEEP_INTERVAL
                && EXTRA_HIT_AT.size() < SWEEP_THRESHOLD && EXPECTED_TICK.size() < SWEEP_THRESHOLD) {
            return;
        }
        lastSweep = tick;

        var cooldownIterator = EXTRA_HIT_AT.object2LongEntrySet().iterator();
        while (cooldownIterator.hasNext()) {
            Object2LongMap.Entry<LivingEntity> entry = cooldownIterator.next();
            LivingEntity entity = entry.getKey();
            if (!entity.isAlive() || entity.isRemoved()) cooldownIterator.remove();
        }

        var expectedIterator = EXPECTED_TICK.object2LongEntrySet().iterator();
        while (expectedIterator.hasNext()) {
            Object2LongMap.Entry<LivingEntity> entry = expectedIterator.next();
            LivingEntity entity = entry.getKey();
            if (!entity.isAlive() || entity.isRemoved()) {
                expectedIterator.remove();
                EXPECTED_DAMAGE.removeFloat(entity);
            }
        }
    }
}
