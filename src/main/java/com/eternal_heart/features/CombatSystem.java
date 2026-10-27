package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.core.Scan;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.ArrayList;
import java.util.Comparator;
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
 *   <li><b>破限改为单开关语义</b>：事件内恢复只管「被压低的 amount」，管不到「事件之前就压完」
 *       「事件之后再夹一次」「hurt() 直接 return false 导致事件根本不触发」这三类限伤。
 *       因此额外记一张按目标记账的期望表，下一 tick 用实际掉血核对并补足差额，
 *       由 {@code PIERCE_DAMAGE_CAP} 一个开关统一裁决开 / 关。</li>
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

    /**
     * 「正在补最后一刀」标记。
     *
     * <p>锁血重试/结算补足把血量压到接近 0 之后，最后一刀改为走正常的 {@code hurt()} ——
     * 这样掉落、战利品、复活机制、死亡事件全都能正常感知。但那一刀会重新进入
     * {@link #onTargetHurt}，若继续记账就会在下一 tick 再补一次，形成「补足 → hurt → 补足」的循环。
     * 因此收尾期间的这一刀只放大伤害，不记账。</p>
     */
    private static final ThreadLocal<Boolean> FINISHING = ThreadLocal.withInitial(() -> Boolean.FALSE);



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
        // 记账点在 hurt() 之前（observeMeleeAttack / observeProjectileHit / strike），
        // 这里是 hurt() 内部，已经算晚，不再重复记账。

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
        // 收尾那一刀只放大伤害，不记账（否则下一 tick 又会按期望值补一次，形成补足循环）
        boolean finishing = FINISHING.get();

        // 增伤管线（斩杀前，不含发光加成）
        float damage = amplify(player, target, event.getAmount());

        float glowBonus = ConfigValues.get(EternalHeartConfig.GLOW_DAMAGE_BONUS).floatValue();
        if (glowBonus > 0 && target.hasEffect(MobEffects.GLOWING)) {
            damage *= (1f + glowBonus);
        }

        // 斩杀：目标血量低于阈值时，把这一击的伤害抬到「最大生命 × 10」，保证一击必杀。
        //
        // 只改 amount，不再「取消事件 + 自行 hurt」：自行 hurt 会重新进入本方法，
        // 而第二次进入时 invulnerableTime 已 > 10，原版只结算「新伤害 - lastHurt」的差额 ——
        // 两者是同一个数，差额为 0，于是这一刀实际一点血都没掉；真正的伤害全靠下一 tick
        // 的破限补足兜底。表现出来就是「残血目标打不死，要连打好几下」。
        float execute = ConfigValues.get(EternalHeartConfig.EXECUTE_THRESHOLD).floatValue();
        if (!finishing && execute > 0 && target.getMaxHealth() > 0f
                && target.getHealth() / target.getMaxHealth() <= execute) {
            damage = Math.max(damage, Math.max(target.getMaxHealth() * 10f, 1f));
        }

        event.setAmount(damage);

        if (!finishing) {
            // 逐击记录：伤害走到最后一道事件时，用它把被压低的值提回期望
            rememberExpectedDamage(target, damage, tick);
            // 累计记录：事件之后再夹一次 / 事件根本不触发时，由下一 tick 的核对补足
            recordSettlement(player, target, damage, tick);
        }

        // 次级效果（受级联防护保护，避免怪群中的无限递归）
        // 先算溢出再算溅射：溢出量由「这一击打穿目标的程度」决定，与后面的溅射无关
        if (!finishing && !cascading()) {
            spillOver(player, target, damage);
        }
        float splashRatio = ConfigValues.get(EternalHeartConfig.AOE_SPLASH_RATIO).floatValue();
        if (!finishing && splashRatio > 0 && !cascading()) {
            splash(player, target, damage * splashRatio);
        }
        applyExtraHit(player, target, tick);

        if (ConfigValues.get(EternalHeartConfig.BENEFICIAL_STRIP)) {
            stripBenefits(target);
        }
    }

    /** 增伤管线：Boss 加成 → 低血加成 → 满怒额外 +5% → 处决增伤。 */
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

        // 处决增伤：目标生命比例低于阈值后，按「缺失生命」线性放大到配置上限。
        // 与「斩杀」互补 —— 斩杀负责收割最后一段，这里负责阈值之下的整段过渡，
        // 避免斩杀阈值之上伤害曲线出现断层。
        double executeThreshold = ConfigValues.get(EternalHeartConfig.EXECUTE_DAMAGE_THRESHOLD);
        double executeBonus = ConfigValues.get(EternalHeartConfig.EXECUTE_DAMAGE_BONUS);
        if (executeThreshold > 0 && executeBonus > 0) {
            float max = target.getMaxHealth();
            if (Float.isFinite(max) && max > 0f) {
                float ratio = Math.max(0f, target.getHealth() / max);
                if (ratio < executeThreshold) {
                    // 阈值处 = 0（×1.0），血量趋近 0 时趋近 1（×1 + bonus）
                    float missing = (float) ((executeThreshold - ratio) / executeThreshold);
                    damage *= (1f + (float) executeBonus * Math.min(1f, missing));
                }
            }
        }

        // 配置可以把倍率放到「一刀必杀」的量级，连乘后会溢出成 Infinity。
        // 归一成 float 上限：秒杀效果不变，但不会把 Infinity / NaN 写进记账与重试表。
        return Float.isFinite(damage) ? damage : Float.MAX_VALUE;
    }

    /**
     * 光环 / 范围伤害的统一入口（供 {@link AuraSystem} 调用）。
     *
     * <p>历史上的光环直接用 {@code damageSources().magic()} 打伤害，而该伤害源<b>没有
     * 攻击者实体</b>，因此它完全绕过了本模块——Boss 增伤、斩杀、处决增伤、溢出转移、
     * 限伤穿透一个都不生效，配置里还因此存在「伤害光环对 Boss 倍率」和「对 Boss 增伤」
     * 两份互不相干的数值。</p>
     *
     * <p>现在改走 {@link #strike}（{@code playerAttack} 伤害源），光环伤害自然会进
     * {@link #onTargetHurt}，复用同一套管线；同时受级联深度约束，不会与溅射 / 溢出互相放大成无限递归。</p>
     */
    public static void auraStrike(Player player, LivingEntity target, float damage) {
        if (damage <= 0 || target == player) return;
        if (!ConfigValues.get(EternalHeartConfig.AURA_USE_COMBAT_PIPELINE)) {
            // 旧行为：固定数值直接结算，只有「对 Boss 倍率」这一个加成
            target.hurt(player.damageSources().magic(), damage);
            return;
        }
        runCascade(() -> strike(player, target, damage));
    }

    /** 溅射：对目标周围的敌人造成一定比例的伤害（默认不分种类）。 */
    private static void splash(Player player, LivingEntity primary, float damage) {
        if (damage <= 0) return;
        double range = ConfigValues.get(EternalHeartConfig.AOE_SPLASH_RANGE);
        if (range <= 0) return;

        EntityType<?> type = primary.getType();
        boolean allTypes = ConfigValues.get(EternalHeartConfig.AOE_SPLASH_ALL_TYPES);
        List<LivingEntity> nearby = primary.level().getEntitiesOfClass(LivingEntity.class,
                Scan.box(primary, range),
                e -> e != primary && e != player && e.isAlive() && e instanceof Enemy
                        && (allTypes || e.getType() == type));
        if (nearby.isEmpty()) return;

        runCascade(() -> {
            for (LivingEntity enemy : nearby) strike(player, enemy, damage);
        });
    }

    /**
     * 溢出转移：单次伤害超过目标剩余生命（含吸收）时，把超出部分按比例打向附近敌人。
     *
     * <p>为什么值得单独做：溅射按「命中伤害的固定比例」分摊，而溢出按「这一击打穿了多少」计算。
     * 打小怪时溢出的伤害本来会被浪费掉，转移出去之后高伤一击才真正具备连锁清场能力。</p>
     *
     * <p>三点约束：</p>
     * <ul>
     *   <li>只对普通命中生效 —— 斩杀本身以 10 倍最大生命结算，让它的「溢出」参与转移会凭空造出
     *       远超实际的伤害；</li>
     *   <li>按距离由近到远取前 N 个，避免随机挑到远处的目标；</li>
     *   <li>走 {@link #runCascade}，受级联深度上限约束，怪群里不会无限递归。</li>
     * </ul>
     */
    private static void spillOver(Player player, LivingEntity primary, float damage) {
        double ratio = ConfigValues.get(EternalHeartConfig.OVERFLOW_RATIO);
        if (ratio <= 0) return;
        int maxTargets = ConfigValues.get(EternalHeartConfig.OVERFLOW_MAX_TARGETS);
        if (maxTargets <= 0) return;

        float pool = primary.getHealth() + primary.getAbsorptionAmount();
        float overflow = damage - pool;
        if (overflow <= 0) return;

        double range = ConfigValues.get(EternalHeartConfig.AOE_SPLASH_RANGE);
        if (range <= 0) return;

        List<LivingEntity> nearby = primary.level().getEntitiesOfClass(LivingEntity.class,
                Scan.box(primary, range),
                e -> e != primary && e != player && e.isAlive() && !e.isRemoved() && e instanceof Enemy);
        if (nearby.isEmpty()) return;

        // 由近到远：溢出伤害优先落在最近的目标上（打穿前排才波及后排）
        nearby.sort(Comparator.comparingDouble(e -> e.distanceToSqr(primary)));
        int count = Math.min(maxTargets, nearby.size());
        float rawSpill = overflow * (float) ratio;
        if (rawSpill <= 0) return;
        // 倍率配到极大时归一，避免 Infinity 进伤害管线（下面 lambda 里要引用，必须是有效 final）
        final float spill = Float.isFinite(rawSpill) ? rawSpill : Float.MAX_VALUE;

        runCascade(() -> {
            for (int i = 0; i < count; i++) strike(player, nearby.get(i), spill);
        });
    }

    /**
     * 额外打击：对同一目标的追加魔法伤害，带独立冷却。
     *
     * <p><b>伤害源必须带攻击者实体。</b>原来的 {@code damageSources().magic()} 没有攻击者
     * （{@code getEntity() == null}），于是这笔伤害走不进战斗模块：吃不到 Boss 增伤 / 低血狂暴 /
     * 处决增伤，不触发溅射与溢出，击杀也不归给玩家 —— 配置里的「额外打击伤害」实际只是裸数值，
     * 与旁边一整套增伤管线完全脱钩。</p>
     *
     * <p>改用 {@code indirectMagic(player, player)}：伤害类型仍是魔法（无视护甲），但攻击者是我方，
     * 因此会正常进入 {@link #onTargetHurt} 复用全部增伤逻辑。</p>
     */
    private static void applyExtraHit(Player player, LivingEntity target, long tick) {
        // 次级伤害自身不再产生额外打击。
        // 额外打击现在带攻击者（会重新进入 onTargetHurt），若只靠冷却防重入，
        // 配置把 extraHitCooldown 写成 0 时就会形成 hurt → LivingHurtEvent → onTargetHurt
        // → applyExtraHit → hurt 的无限递归（实测 47 层后 StackOverflowError）。
        if (cascading()) return;

        float configured = ConfigValues.get(EternalHeartConfig.EXTRA_HIT_DAMAGE).floatValue();
        if (configured <= 0) return;
        // 配置允许填到 double 极限（1e90 这类「一刀必杀」），转 float 会变成 Infinity；
        // 之后补足 / 重试里的 Infinity - Infinity 会算出 NaN。钳到 float 上限：
        // 秒杀效果不变，但数值保持有限，不会往记账表里写脏值。
        // （lambda 里要引用，因此先算成 final 局部变量）
        final float extra = Float.isFinite(configured) ? configured : Float.MAX_VALUE;

        // 冷却下限 1 tick：配置写 0 / 负数时，同一次命中的递归调用会在同一 tick 反复通过判定
        int cooldown = Math.max(1, ConfigValues.get(EternalHeartConfig.EXTRA_HIT_COOLDOWN));
        long last = EXTRA_HIT_AT.getLong(target);
        if (tick - last < cooldown) return;

        EXTRA_HIT_AT.put(target, tick);
        target.invulnerableTime = 0;
        // 记账按增伤管线换算后的量：这一击被免伤完全拦下时，破限核对补的也是放大后的值
        notePreHurtDamage(player, target, amplify(player, target, extra));
        runSecondary(() -> target.hurt(player.damageSources().indirectMagic(player, player), extra));
    }

    /**
     * 以「次级伤害」身份结算：屏蔽它自己再产生溅射 / 溢出 / 连锁。
     *
     * <p>额外打击改带攻击者之后会重新进入 {@link #onTargetHurt}（这正是它接入增伤管线的代价），
     * 若不屏蔽，每次近战都会多出一整轮溅射 —— 伤害会在玩家看不到的地方翻倍，而且与
     * 「额外打击」这个配置项的字面语义（多打一下）不符。</p>
     */
    private static void runSecondary(Runnable action) {
        int[] depth = CASCADE_DEPTH.get();
        int bump = ConfigValues.get(EternalHeartConfig.CASCADE_DEPTH_LIMIT) + 1 - depth[0];
        if (bump <= 0) {
            action.run();
            return;
        }
        depth[0] += bump;
        try {
            action.run();
        } finally {
            depth[0] -= bump;
        }
    }

    /** 移除目标身上全部有益效果（攻击压制模块复用同一实现，避免两套判定）。 */
    public static void stripBenefits(LivingEntity target) {
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

    /** 击杀连锁：对附近敌人造成被击杀者最大生命值一定比例（可配）的伤害并击退。 */
    private static void killChain(Player player, LivingEntity killed) {
        double range = ConfigValues.get(EternalHeartConfig.KILL_CHAIN_RANGE);
        if (range <= 0) return;

        List<LivingEntity> nearby = killed.level().getEntitiesOfClass(LivingEntity.class,
                Scan.box(killed, range),
                e -> e != killed && e != player && e.isAlive() && e instanceof Enemy);
        if (nearby.isEmpty()) return;

        float chainDamage = killed.getMaxHealth()
                * ConfigValues.get(EternalHeartConfig.KILL_CHAIN_RATIO).floatValue();
        if (chainDamage <= 0) return;
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
    //  限伤穿透（单开关：事件内恢复 + 结算后核对补足）
    // ============================================================

    /** 补足判定容差：float 血量往返换算的误差，小于它的差额不值得动一次 setHealth。 */
    private static final float SETTLE_EPSILON = 0.01F;
    /** 核对窗口的存活上限（tick）。窗口正常只活 1 tick，超过说明时钟 / 维度对不上了，直接丢弃。 */
    private static final long SETTLE_STALE = 40L;

    /** 我方期望伤害（目标 → 数值），逐击覆盖：事件内恢复用它把被压低的 amount 提回来。 */
    private static final Object2FloatOpenHashMap<LivingEntity> EXPECTED_DAMAGE = new Object2FloatOpenHashMap<>();
    /** 记录期望值时的游戏时间（目标 → tick），用于保证只与同一 tick 的最终结算配对。 */
    private static final Object2LongOpenHashMap<LivingEntity> EXPECTED_TICK = new Object2LongOpenHashMap<>();

    /*
     * 结算后核对用的记账表。
     *
     * 为什么用一串并行的 fastutil 原生表而不是「目标 → 小对象」：记账发生在命中热路径上，
     * 每多一次对象分配都会在密集战斗里堆成 GC 压力；这里键就是实体引用，值全是基本类型，
     * 写入零装箱零分配。
     *
     * 血量与吸收合成一列（pool = hp + abs）：实际掉血恒等于 pool 的差值，
     * 既少一张表，又天然把「黄心被打掉」算作已掉血（不会把吸收当成没打掉而多补一次）。
     */
    private static final Object2FloatOpenHashMap<LivingEntity> SETTLE_POOL = new Object2FloatOpenHashMap<>();
    /** 伤害阶段累计的期望值（同一 tick 的多次命中累加）。 */
    private static final Object2FloatOpenHashMap<LivingEntity> SETTLE_HURT = new Object2FloatOpenHashMap<>();
    /** 攻击阶段的兜底观测（同一 tick 取最大值，不累加）。 */
    private static final Object2FloatOpenHashMap<LivingEntity> SETTLE_ATK = new Object2FloatOpenHashMap<>();
    /** 窗口起始 tick：窗口本身的存活由它判定。 */
    private static final Object2LongOpenHashMap<LivingEntity> SETTLE_TICK = new Object2LongOpenHashMap<>();
    /** 窗口归属的玩家：收尾死亡时用它算击杀归属与伤害源。 */
    private static final Object2ObjectOpenHashMap<LivingEntity, Player> SETTLE_ATTACKER = new Object2ObjectOpenHashMap<>();
    /** 窗口所属维度：换维度后不在另一个维度凭空补刀。 */
    private static final Object2ObjectOpenHashMap<LivingEntity, Level> SETTLE_LEVEL = new Object2ObjectOpenHashMap<>();

    /*
     * 锁血重试表：补足被目标「锁血 / 阶段无敌」挡住时，把没打进去的差额排队重试。
     *
     * 此前 settle 只补一次、写完就清账 —— 对覆写 setHealth 拒绝降血、或阶段无敌期间
     * 把血量钳制住的目标，这一整笔破限伤害会被永久吞掉（表现就是「破限对这只有用，
     * 对那只完全无效」）。这里把写不进去的部分按 tick 重试，次数由 PIERCE_RETRY_TICKS 约束。
     */
    private static final Object2LongOpenHashMap<LivingEntity> RETRY_AT = new Object2LongOpenHashMap<>();
    private static final Object2FloatOpenHashMap<LivingEntity> RETRY_MISSING = new Object2FloatOpenHashMap<>();
    private static final Object2IntOpenHashMap<LivingEntity> RETRY_LEFT = new Object2IntOpenHashMap<>();
    private static final Object2ObjectOpenHashMap<LivingEntity, Player> RETRY_ATTACKER = new Object2ObjectOpenHashMap<>();
    /** 上一次全局核对的游戏时间（同一 tick 多个玩家只核对一次）。 */
    private static long lastSettleTick = Long.MIN_VALUE;

    private static void rememberExpectedDamage(LivingEntity target, float damage, long tick) {
        if (damage <= 0) return;
        EXPECTED_DAMAGE.put(target, damage);
        EXPECTED_TICK.put(target, tick);
    }

    /**
     * 最终伤害结算阶段（护甲 / 抗性 / 吸收计算之后，扣血之前）：把被其它模组压低的 amount 提回期望值。
     *
     * <p>这一层只能处理「在伤害事件里把 amount 压小」的限伤。比它更早（目标 hurt() 内部就压完）、
     * 比它更晚（本事件返回之后再夹一次）、或者根本没走到这里（免伤 / 无敌帧）的三类，
     * 都由下一 tick 的结算后核对用血量补足。</p>
     *
     * <p>三点保证不会帮倒忙：</p>
     * <ul>
     *   <li><b>只提升不压低</b>：其它模组的合法增伤（暴击词缀等）不会被我们改回去；</li>
     *   <li><b>只与本 tick 的伤害配对</b>：伤害被彻底拦掉（事件不触发）时记录自然过期，
     *       不会把上一次的期望值用到下一次攻击上；</li>
     *   <li><b>事件被取消时不再补刀</b>：取消型免伤统一交给结算后核对，避免同一段伤害结算两次。</li>
     * </ul>
     *
     * @return 是否处理了本次伤害
     */
    public static boolean pierceDamageCap(LivingDamageEvent event) {
        if (!ConfigValues.get(EternalHeartConfig.PIERCE_DAMAGE_CAP)) return false;

        LivingEntity target = event.getEntity();
        if (target instanceof Player) return false;

        boolean fresh = EXPECTED_DAMAGE.containsKey(target)
                && EXPECTED_TICK.getLong(target) == target.level().getGameTime();
        EXPECTED_TICK.removeLong(target);
        float expected = EXPECTED_DAMAGE.removeFloat(target);
        if (!fresh || expected <= 0) return false;

        // 事件已被取消（免伤型拦截）：这里不自己扣血，留给结算后核对按实际掉血补足
        if (event.isCanceled()) return false;
        if (event.getAmount() < expected) {
            event.setAmount(expected);
            return true;
        }
        return false;
    }

    /**
     * 玩家近战：在 {@code AttackEntityEvent} 里记账，也就是 {@code Player.attack} 的第一行。
     *
     * <p>这是唯一稳定早于目标 {@code hurt()} 的位置：它不经过目标的任何重写，冷却、阶段免伤、
     * 闪电 / 窒息 / 摔落这些「在 hurt() 里直接 return false」的逻辑都拦不住它。
     * 挂在这里，目标哪怕一点伤害事件都不触发，账也已经记下了。</p>
     *
     * <p>代价是拿不到伤害管线的最终值，只能按原版口径自己估一遍（攻击力 × 蓄力曲线 + 附魔 × 蓄力）。
     * <b>不含暴击</b>：本模块的强制暴击是在 {@code CriticalHitEvent} 里掷骰的，这里再掷一次会打乱
     * 随机序列，所以保守取小。正常命中时伤害阶段会用真实值覆盖它，它只在伤害事件完全不触发时生效。</p>
     */
    public static void observeMeleeAttack(Player player, Entity rawTarget) {
        if (!(rawTarget instanceof LivingEntity target)) return;
        if (target instanceof Player) return; // 不对玩家记账，省掉后续的管线计算
        if (!ConfigValues.get(EternalHeartConfig.PIERCE_DAMAGE_CAP)) return;
        if (player.level().isClientSide()) return;

        float scale = player.getAttackStrengthScale(0.5F);
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.2F + scale * scale * 0.8F)
                + EnchantmentHelper.getDamageBonus(player.getMainHandItem(), target.getMobType()) * scale;
        observeAttackStage(player, target, damage);
    }

    /**
     * 远程：在 {@code ProjectileImpactEvent} 里记账，同样早于目标 {@code hurt()}。
     *
     * <p>只取我方弹射物，且只处理箭 / 弩箭 / 三叉戟：它们的伤害口径唯一且可复算
     * （基础伤害 × 速度，与原版 {@code AbstractArrow.onHitEntity} 一致）。
     * 火球这类由发射者自行结算的弹射物不入账，避免口径对不上而多打。</p>
     */
    public static void observeProjectileHit(Projectile projectile, LivingEntity target) {
        if (!(projectile instanceof AbstractArrow arrow)) return;
        if (!(arrow.getOwner() instanceof Player player)) return; // 只取我方弹射物
        if (!ConfigValues.get(EternalHeartConfig.PIERCE_DAMAGE_CAP)) return;
        if (arrow.level().isClientSide()) return;

        double speed = arrow.getDeltaMovement().length();
        float damage = Mth.ceil(Mth.clamp(arrow.getBaseDamage() * speed, 0.0D, Integer.MAX_VALUE));
        observeAttackStage(player, target, damage);
    }

    /** 出手前观测：把「原始值」按本模块增伤管线换算后入账（近战 / 远程用）。 */
    private static void observeAttackStage(Player player, LivingEntity target, float amount) {
        if (!ConfigValues.get(EternalHeartConfig.PIERCE_DAMAGE_CAP)) return;

        float damage = amplify(player, target, amount);
        float glowBonus = ConfigValues.get(EternalHeartConfig.GLOW_DAMAGE_BONUS).floatValue();
        if (glowBonus > 0 && target.hasEffect(MobEffects.GLOWING)) {
            damage *= (1f + glowBonus);
        }
        notePreHurtDamage(player, target, damage);
    }

    /** 出手前观测：值已经是最终伤害（自家溅射 / 额外打击，量本来就是算好的）。 */
    private static void notePreHurtDamage(Player player, LivingEntity target, float damage) {
        if (!ConfigValues.get(EternalHeartConfig.PIERCE_DAMAGE_CAP)) return;
        if (!trackable(player, target, damage)) return;

        long tick = target.level().getGameTime();
        settleIfDue(target, tick);
        if (!target.isAlive() || target.isRemoved()) return; // 结清旧账可能已经把目标打死了
        openWindowIfNeeded(target, tick);

        // 同一段伤害在出手前可能被重复观测（(目标, tick) 之外没有可靠身份可用），
        // 所以这里取最大值而不是累加 —— 累加会让期望值直接翻倍，然后补一整份，等于一刀两倍伤害
        if (damage > SETTLE_ATK.getFloat(target)) SETTLE_ATK.put(target, damage);
        SETTLE_ATTACKER.put(target, player);
    }

    /** 伤害阶段（{@code LivingHurtEvent}）的精确记录：此时已含本模块的增伤管线。 */
    private static void recordSettlement(Player player, LivingEntity target, float damage, long tick) {
        if (!ConfigValues.get(EternalHeartConfig.PIERCE_DAMAGE_CAP)) return;
        if (!trackable(player, target, damage)) return;

        // 旧窗口还没结清就先结清：否则本次命中造成的掉血会被算进上一笔的「实际掉血」，
        // 补足额度凭空少一截（表现为少打一份伤害）
        settleIfDue(target, tick);
        if (!target.isAlive() || target.isRemoved()) return; // 结清旧账可能已经把目标打死了
        openWindowIfNeeded(target, tick);

        if (SETTLE_HURT.containsKey(target)) {
            // 同一 tick 的后续命中（溅射 / 额外打击）：累加，不能互相覆盖
            SETTLE_HURT.addTo(target, damage);
        } else {
            // 本 tick 的第一笔伤害记录：替换掉攻击阶段的兜底观测（口径更精确）
            SETTLE_HURT.put(target, damage);
            SETTLE_ATK.removeFloat(target);
        }
        SETTLE_ATTACKER.put(target, player);
    }

    /** 记账前置判定：开关、服务端、非玩家目标、目标仍在场。 */
    private static boolean trackable(Player player, LivingEntity target, float damage) {
        if (damage <= 0f) return false;
        if (target == player || target instanceof Player) return false; // 绝不作用于玩家
        if (target.level().isClientSide()) return false;
        return target.isAlive() && !target.isRemoved();
    }

    /** 建立本 tick 的核对窗口（已有同 tick 的窗口则复用，快照只在窗口创建时取一次）。 */
    private static void openWindowIfNeeded(LivingEntity target, long tick) {
        if (SETTLE_TICK.containsKey(target) && SETTLE_TICK.getLong(target) == tick) return;
        SETTLE_TICK.put(target, tick);
        SETTLE_POOL.put(target, target.getHealth() + target.getAbsorptionAmount());
        SETTLE_HURT.removeFloat(target);
        SETTLE_ATK.removeFloat(target);
        SETTLE_LEVEL.put(target, target.level());
    }

    /** 该目标的窗口若属于更早的 tick，说明已经过期：先结清再记新的。 */
    private static void settleIfDue(LivingEntity target, long tick) {
        if (!SETTLE_TICK.containsKey(target)) return;
        if (SETTLE_TICK.getLong(target) >= tick) return;
        settle(target);
    }

    // ---- 结算后核对（下一 tick）----

    @Override
    public void onPlayerTick(Player player, LivingTickEvent event) {
        if (player.level().isClientSide()) return;
        long tick = player.level().getGameTime();
        // 同一 tick 里每个已装备的玩家都会被回调一次，全局核对只做一次
        if (tick <= lastSettleTick) return;
        lastSettleTick = tick;

        settleDue(tick);
        retryDue(tick);
    }

    /** 结清所有已过期的核对窗口。 */
    private static void settleDue(long tick) {
        if (SETTLE_TICK.isEmpty()) return;
        // 摘一个 → 结清 → 再摘：结清可能触发死亡流程（进而触发击杀连锁、又写回本表），
        // 用迭代器边遍历边删会撞上 fastutil 的结构修改检查
        while (true) {
            LivingEntity due = firstSettleDue(tick);
            if (due == null) return;
            settle(due);
        }
    }

    private static LivingEntity firstSettleDue(long tick) {
        for (Object2LongMap.Entry<LivingEntity> entry : SETTLE_TICK.object2LongEntrySet()) {
            if (entry.getLongValue() < tick) return entry.getKey();
        }
        return null;
    }

    /**
     * 结清一个目标的窗口：按实际掉血补足差额。
     *
     * <p>实际掉血 = (hp0 + abs0) - (hp + abs)。把吸收并进同一个池子是必须的：黄心被打掉的那部分
     * 同样是「已经打掉的」，漏掉它就会把吸收算成没掉血，于是多补一次伤害。</p>
     */
    private static void settle(LivingEntity target) {
        float pool0 = SETTLE_POOL.removeFloat(target);
        long windowTick = SETTLE_TICK.removeLong(target);
        float expected = SETTLE_HURT.removeFloat(target);
        float observed = SETTLE_ATK.removeFloat(target);
        Player attacker = SETTLE_ATTACKER.remove(target);
        Level windowLevel = SETTLE_LEVEL.remove(target);

        // 没有伤害阶段的记录，就退回攻击阶段的兜底观测（这一刀根本没走到伤害事件）
        if (expected <= 0f) expected = observed;
        if (expected <= 0f) return;
        if (windowLevel != null && target.level() != windowLevel) return;    // 换维度：账随维度作废
        if (target.level().getGameTime() - windowTick > SETTLE_STALE) return; // 时钟对不上：丢弃
        if (!target.isAlive() || target.isRemoved()) return;                 // 已死亡 / 被移除：账随实体消失

        float actual = Math.max(0f, pool0 - (target.getHealth() + target.getAbsorptionAmount()));
        float missing = expected - actual;
        if (missing <= SETTLE_EPSILON) return;

        // 完全在伤害管线之外补足：这一步才真正破得掉「hurt() 里先压完」「事件之后再夹一次」
        float before = target.getHealth();
        float after = before - missing;
        if (after <= 0f) {
            // 致死：不动血量，直接让最后一刀走完整伤害管线（掉落 / 战利品 / 复活机制都只认
            // hurt() → actuallyHurt → die()）。把血量先压到 0.01 会让「取消这次伤害」的目标
            // 永久停在 1 滴血上，表现就是残血打不死。
            finishOff(target, attacker);
            return;
        }
        target.setHealth(after);

        // 回读校验：目标可能对 setHealth 做了钳制（锁血 / 阶段无敌 / 0 血自动回满）。
        // 写不进去的部分不能直接丢，排队到下一 tick 重试。
        float leftover = missing - Math.max(0f, before - target.getHealth());
        if (leftover > SETTLE_EPSILON) scheduleRetry(target, leftover, attacker);
    }

    // ---- 锁血重试 ----

    /** 把「没打进去」的差额排队重试（同一目标累加，总次数由配置约束）。 */
    private static void scheduleRetry(LivingEntity target, float leftover, Player attacker) {
        int limit = ConfigValues.get(EternalHeartConfig.PIERCE_RETRY_TICKS);
        if (limit <= 0) return;
        if (!target.isAlive() || target.isRemoved()) return;

        int left = RETRY_LEFT.containsKey(target) ? RETRY_LEFT.getInt(target) : limit;
        if (left <= 0) return;

        RETRY_MISSING.addTo(target, leftover);
        RETRY_LEFT.put(target, left);
        RETRY_AT.put(target, target.level().getGameTime() + 1);
        if (attacker != null) RETRY_ATTACKER.put(target, attacker);
    }

    /** 处理到期的重试：继续尝试把被锁血挡住的补足打进去。 */
    private static void retryDue(long tick) {
        if (RETRY_AT.isEmpty()) return;
        while (true) {
            LivingEntity target = firstRetryDue(tick);
            if (target == null) return;
            RETRY_AT.removeLong(target);
            float missing = RETRY_MISSING.removeFloat(target);
            int left = RETRY_LEFT.removeInt(target);
            Player attacker = RETRY_ATTACKER.remove(target);

            if (missing <= SETTLE_EPSILON) continue;
            if (!target.isAlive() || target.isRemoved()) continue;
            float before = target.getHealth();
            if (before <= 0f) continue;

            float after = before - missing;
            if (after <= 0f) {
                finishOff(target, attacker);
                continue;
            }
            target.setHealth(after);

            float leftover = missing - Math.max(0f, before - target.getHealth());
            if (leftover > SETTLE_EPSILON && left > 1) {
                RETRY_AT.put(target, tick + 1);
                RETRY_MISSING.put(target, leftover);
                RETRY_LEFT.put(target, left - 1);
                if (attacker != null) RETRY_ATTACKER.put(target, attacker);
            }
        }
    }

    private static LivingEntity firstRetryDue(long tick) {
        for (Object2LongMap.Entry<LivingEntity> entry : RETRY_AT.object2LongEntrySet()) {
            if (entry.getLongValue() <= tick) return entry.getKey();
        }
        return null;
    }

    /**
     * 血量被补到 0 之后收尾。
     *
     * <p>只调用一次 {@code die()}，不做事件重放：重放会二次触发复活模组（重复扣复活次数、重复播报）。</p>
     *
     * <p><b>不再强制移除实体。</b>此前这里会把「0 血但未被移除」的目标登记到待移除表，
     * 下一 tick 用 {@code remove(RemovalReason.KILLED)} 清掉。但那条路径<b>什么都不掉</b> ——
     * {@code dropAllDeathLoot} 只在 {@code die()} 里执行，直接 remove 等于让实体凭空消失
     * （实际表现就是「有的怪被打死后没有掉落物」）。现在把决定权交回原版：
     * 真死 → 原版按 deathTime 自行移除；被取消 → 回填一部分血量，不留 0 血幽灵。</p>
     */
    /**
     * 补最后一刀：走一次<b>正常的伤害结算</b>，而不是直接写 0 血再 {@code die()}。
     *
     * <p>为什么必须走 {@code hurt()}：</p>
     * <ul>
     *   <li>掉落 / 战利品 / 经验 / 死亡动画只在 {@code hurt → actuallyHurt → die} 这条链里发生；</li>
     *   <li>「在 {@code LivingDeathEvent} 里取消死亡并排队回血」的模组需要一个能被感知的伤害来源，
     *       直接 {@code setHealth(0)} 会让它收不到任何通知；</li>
     *   <li>若目标取消了这次伤害（阶段免伤 / 无敌），血量保持原值、不会变成 0 ——
     *       既不会留下「0 血却打不动」的假死实体，也不会停在 1 滴血上反复挨打。</li>
     * </ul>
     *
     * <p>收尾期间置 {@link #FINISHING}，让 {@link #onTargetHurt} 跳过记账，避免形成补足循环。</p>
     */
    private static void finishOff(LivingEntity target, Player attacker) {
        if (target.isRemoved()) return;
        if (attacker != null) target.setLastHurtByPlayer(attacker); // 击杀归属：经验 / 统计记在玩家头上
        DamageSource source = attacker != null
                ? attacker.damageSources().playerAttack(attacker)
                : target.damageSources().generic();

        target.invulnerableTime = 0;
        boolean previous = FINISHING.get();
        FINISHING.set(Boolean.TRUE);
        try {
            runSecondary(() -> target.hurt(source, Float.MAX_VALUE));
        } finally {
            FINISHING.set(previous);
            // 收尾这一刀会把 lastHurt 抬到极大、并留下 20 tick 无敌帧。
            // 如果这一刀被目标取消（没打死），不清零的话接下来玩家的每次攻击都会因为
            // 「新伤害 <= lastHurt」被原版直接 return false —— 表现正是「残血要连打好多下」。
            target.invulnerableTime = 0;
        }
    }

    // ============================================================
    //  工具
    // ============================================================

    /** 次级打击：清零无敌帧后结算，保证连续多次命中都能生效。 */
    private static void strike(Player player, LivingEntity target, float damage) {
        target.invulnerableTime = 0;
        notePreHurtDamage(player, target, damage); // 出手前记账：命中被拦下也照样算进期望
        target.hurt(player.damageSources().playerAttack(player), damage);
    }

    /** 当前是否已到达「不允许再产生级联伤害」的深度（0 = 旧行为，完全不允许）。 */
    private static boolean cascading() {
        return CASCADE_DEPTH.get()[0] > ConfigValues.get(EternalHeartConfig.CASCADE_DEPTH_LIMIT);
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
                && EXTRA_HIT_AT.size() < SWEEP_THRESHOLD && EXPECTED_TICK.size() < SWEEP_THRESHOLD
                && SETTLE_TICK.size() < SWEEP_THRESHOLD && RETRY_AT.size() < SWEEP_THRESHOLD) {
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

        // 核对窗口 / 待确认移除：死亡、被移除、换维度之外的陈旧条目也要回收
        //（正常路径下一 tick 就会结清，这里只兜底「玩家中途卸下饰品后不再有核对驱动」的情形）
        var settleIterator = SETTLE_TICK.object2LongEntrySet().iterator();
        while (settleIterator.hasNext()) {
            Object2LongMap.Entry<LivingEntity> entry = settleIterator.next();
            LivingEntity entity = entry.getKey();
            boolean stale = tick - entry.getLongValue() > SETTLE_STALE;
            if (stale || !entity.isAlive() || entity.isRemoved()) {
                settleIterator.remove();
                forgetSettlement(entity);
            }
        }

        // 锁血重试：实体死亡 / 卸载 / 卡住的条目一并回收，避免长会话里留下悬空账
        var retryIterator = RETRY_AT.object2LongEntrySet().iterator();
        while (retryIterator.hasNext()) {
            Object2LongMap.Entry<LivingEntity> entry = retryIterator.next();
            LivingEntity entity = entry.getKey();
            if (entity.isRemoved() || !entity.isAlive() || tick - entry.getLongValue() > SETTLE_STALE) {
                retryIterator.remove();
                RETRY_MISSING.removeFloat(entity);
                RETRY_LEFT.removeInt(entity);
                RETRY_ATTACKER.remove(entity);
            }
        }
    }

    /** 摘掉一个目标的全部核对记账（窗口本身由调用方负责）。 */
    private static void forgetSettlement(LivingEntity target) {
        SETTLE_POOL.removeFloat(target);
        SETTLE_HURT.removeFloat(target);
        SETTLE_ATK.removeFloat(target);
        SETTLE_ATTACKER.remove(target);
        SETTLE_LEVEL.remove(target);
    }
}
