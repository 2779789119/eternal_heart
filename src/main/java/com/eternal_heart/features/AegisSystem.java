package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.core.PlayerScoped;
import com.eternal_heart.core.Ticker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;

import java.util.List;
import java.util.UUID;

/**
 * 防护机制（Aegis）—— 「防止被改血、防止被秒、防止被收」的兜底防线。
 *
 * <h3>1. 血量防篡改（{@code healthGuard} / {@code healthGuardHard}）</h3>
 * <p>伤害是唯一被允许降低生命值的途径。本模块分两层拦：</p>
 * <ul>
 *   <li><b>硬锁</b>（{@link #blockHealthChange}，由 {@code LivingEntityMixin} 注入
 *       {@code setHealth}）：写入发生的瞬间就拒绝未经授权的降血，零窗口；</li>
 *   <li><b>每 tick 审计</b>（{@code guardHealth}）：硬锁被关闭、或降血绕过
 *       {@code setHealth} 直接改字段时，下一 tick 发现「没人受伤却掉血」就回滚。</li>
 * </ul>
 * <p>判定的核心是「这笔下降有没有经过伤害管线」：{@link #onFinalDamage} 会把本 tick
 * 的最终伤害量记成硬锁额度，{@code setHealth} 按实际下降量逐点消耗；治疗、钳制到上限、
 * 本模组主动改血（{@link #applyOwnedHealth}）走白名单。正常受伤、饥饿、中毒、
 * 虚空伤害都会被正确放行。</p>
 *
 * <h3>2. 生命上限保护（{@code maxHealthGuard}）</h3>
 * <p>佩戴瞬间记录最大生命值为基线；此后若最大生命值被外部削减到基线以下，
 * 用固定 UUID 的补足修饰符把差额补回来；一旦外部恢复（或玩家换上更强装备），
 * 补足自动撤销，不会叠加。上限被削期间被钳制掉的当前血量会记账，上限恢复后原数退还
 * （否则「上限被偷」最终会变成「上限恢了，血却永久少一截」）。</p>
 *
 * <h3>3. 致死保护（{@code lethalGuard}）/ 死亡兜底（{@code deathGuard}）</h3>
 * <p>致死保护在最终伤害阶段拦截「一击必杀」：把伤害下调到只扣到保留生命比例为止，
 * 并进入冷却。死亡兜底（{@link #lastStand}）补的是伤害管线之外的路：
 * 直接 {@code setHealth(0)} 再 {@code die()}、完全绕过伤害事件的致死实现——
 * 在死亡收尾阶段强行取消死亡并恢复生命值，与致死保护共享冷却。</p>
 *
 * <h3>4. 防被收（{@code keepInventory} / {@code keepExperience} / {@code itemGuard}）</h3>
 * <ul>
 *   <li>死亡时先把物品栏与经验备份到内存，然后<strong>立即清空</strong>：
 *       这样原版的死亡掉落流程无物可掉（不会有掉落物被别人或收集类机制收走），
 *       重生时再把备份原样归还；</li>
 *   <li>死亡若最终被其它模组的复活类机制取消（灵魂石 / 不死图腾等在更晚的优先级取消事件），
 *       在事件收尾阶段把备份<strong>原样还回去</strong> —— 否则会出现「复活了却既没有物品、
 *       也没有掉落物」（{@link #undoDeath}）；</li>
 *   <li>自己主动丢出的物品会打上归属标记，保护时长内只有本人能拾取，
 *       防止被其它玩家或第三方磁铁类机制收走。</li>
 * </ul>
 */
public class AegisSystem implements IFeature {

    /** 生命值审计容差（小于此差值视为同步误差，不触发回滚）。 */
    private static final double HEALTH_EPSILON = 0.01;

    /** 最大生命值补足修饰符的固定 UUID。 */
    private static final UUID GUARD_HEALTH_UUID = UUID.fromString("e1a0000a-0000-0000-0000-000000000000");

    /** 生命上限低于基线的确认窗口：连续多少 tick 低于才判定为「被削减」（约 0.5 秒）。 */
    private static final int MAX_HEALTH_CONFIRM_TICKS = 10;

    /** 抛出的物品：归属玩家 UUID 与保护到期时间。 */
    private static final String ITEM_OWNER_KEY = "eternal_heart_item_owner";
    private static final String ITEM_UNTIL_KEY = "eternal_heart_item_guard_until";

    /**
     * 死亡备份（同时写入玩家持久化数据，防止"死亡后服务器崩溃"导致物品永久丢失）。
     *
     * <p>键名由玩家 UUID 派生，不再使用固定字面量：外部模组无法靠一个固定名字
     * 把「死亡保留物品」的备份删掉（历史实现里固定键就是被这样绕过的）。</p>
     */
    private static final String DEATH_BACKUP_PREFIX = "eh_db_";
    /** 旧版本使用的固定键：只用于升级时把遗留备份读出来。 */
    private static final String DEATH_BACKUP_LEGACY_KEY = "eternal_heart_death_backup";

    /** 该玩家的备份键（UUID 派生：同一存档内不同玩家不冲突，外部无法预测）。 */
    private static String backupKey(Player player) {
        UUID id = player.getUUID();
        return DEATH_BACKUP_PREFIX
                + Long.toHexString(id.getMostSignificantBits() ^ id.getLeastSignificantBits() ^ 0x5EED_BEEFL);
    }

    private static final String BACKUP_INVENTORY = "inventory";
    private static final String BACKUP_LEVEL = "level";
    private static final String BACKUP_PROGRESS = "progress";
    private static final String BACKUP_TOTAL = "total";

    private static final PlayerScoped<HealthWatch> HEALTH_WATCH = new PlayerScoped<>();
    private static final PlayerScoped<HealthBaseline> HEALTH_BASELINE = new PlayerScoped<>();
    private static final PlayerScoped<LethalState> LETHAL_STATE = new PlayerScoped<>();
    private static final PlayerScoped<DeathCache> DEATH_CACHES = new PlayerScoped<>();

    /**
     * 佩戴期间最后一份「合法生命比例」（血量 / 上限）。
     *
     * <p>卸下时上限会从「基础 + 加成」骤降回基础值，而当前血量仍是旧上限的数值。
     * 直接把血量夹到新上限会丢掉玩家的生命比例（残血摘下变满血、或反过来掉光），
     * 因此这里持续记录比例，卸下时按比例换算到新的上限。</p>
     *
     * <p>只在血量处于 {@code (0, 上限]} 时更新：上限撤掉之后血量会暂时大于上限，
     * 那种脏值算出的比例没有意义，不能覆盖掉有用的一份。</p>
     */
    private static final PlayerScoped<Float> HEALTH_RATIO = new PlayerScoped<>();

    /**
     * 「饰品刚离开槽位、等待下一次 tick 修复血量」标记。
     *
     * <p>卸下判定有 10 tick 防抖（{@code EquipTracker}），不能等它：这 10 tick 里
     * 生命上限已经掉了、血量还是旧值，其它模组（按上限变化重算血量 / 夹取上限的实现）
     * 很容易把它结算成 0 —— 表现就是「摘下饰品当场暴毙」。</p>
     */
    private static final PlayerScoped<Boolean> PENDING_UNEQUIP = new PlayerScoped<>();

    /**
     * 卸下后的守护窗口（剩余 tick 数）。
     *
     * <p>为什么需要：生命上限的修饰符由 Curios 在<b>它自己的 tick 处理</b>里撤销，
     * 撤销之后才轮到其它模组的 tick 逻辑。此刻「上限已降、血量仍是旧值」，按上限变化
     * 重算血量 / 夹取上限的实现很容易把它算成 0 并直接判定死亡 —— 这就是「摘下即暴毙」。
     * 窗口期内：</p>
     * <ul>
     *   <li>每 tick 把异常血量（≤0 / NaN / 超过上限）按卸下时的比例修回来；</li>
     *   <li>{@link #blockHealthChange} 继续拒绝「绕过伤害管线的致死写入」（写 0 / NaN）；</li>
     *   <li>若仍然触发了死亡事件，{@link #lastStand} 会兜底取消。</li>
     * </ul>
     *
     * <p>用「剩余 tick 计数」而不是时间戳：跨维度时各维度的 gameTime 基准不同，
     * 时间戳会让窗口意外变长或失效。</p>
     */
    private static final PlayerScoped<Integer> UNEQUIP_GUARD_LEFT = new PlayerScoped<>();

    /**
     * 濒死无敌窗口（剩余 tick 数）。
     *
     * <p>致死保护 / 死亡兜底只保证「这一击不致死」，玩家往往紧接着被下一击打死
     * （群体围攻、持续伤害、多段攻击）。这里在一次拦截之后给出一段完全免伤的窗口，
     * 与不死图腾的语义一致。</p>
     *
     * <p>同样用「剩余 tick 计数」而不是时间戳：跨维度时各维度的 gameTime 基准不同。</p>
     */
    private static final PlayerScoped<Integer> LETHAL_IMMUNE_LEFT = new PlayerScoped<>();

    /**
     * 最后一次「安全落点」（带维度）。
     *
     * <p>虚空拦截要把玩家送回一个能站住的地方，而不是简单地抬到建筑上限
     * （那等于从高空坠落）。只在「站住、没在高速移动、不在世界底部」时采样。</p>
     */
    private static final PlayerScoped<SafeSpot> SAFE_SPOT = new PlayerScoped<>();

    /** 安全落点采样间隔（tick）：标准移动下 10 tick 只走 2~3 格，精度足够。 */
    private static final int SAFE_SPOT_INTERVAL = 10;

    /** 采样安全落点时的水平速度平方上限（约 1.7 格/tick）：飞行 / 弹射途中不采样。 */
    private static final double SAFE_SPOT_MAX_HORIZONTAL_SPEED_SQR = 3.0;

    /** 安全落点的最低高度余量：低于「世界底部 + 该值」时不采样（那里已经是虚空边缘）。 */
    private static final int SAFE_SPOT_MIN_HEIGHT_MARGIN = 8;

    /** 安全落点快照。 */
    private record SafeSpot(ResourceKey<Level> dimension, double x, double y, double z) {
    }

    /** 卸下守护窗口长度（tick）：覆盖 Curios 重算上限 + 其它模组同一 tick 的跟进处理。 */
    private static final int UNEQUIP_GUARD_TICKS = 100;

    /**
     * 该玩家正处在「真实死亡流程」中（死亡事件已发生、还没重生）。
     *
     * <p>用途：死亡瞬间饰品会被取下（CurioGuard），随后 {@code EquipTracker} 会正常分发一次
     * 「卸下」——若卸下时的血量修复不问青红皂白执行，就会把已经死掉的玩家拉回满血。
     * 因此死亡流程内一律跳过血量修复。</p>
     */
    private static final PlayerScoped<Boolean> DYING = new PlayerScoped<>();

    /**
     * 死亡瞬间确实佩戴着饰品（死亡事件已走到这里、还没重生）。
     *
     * <p>死亡兜底要用它判断「该不该救」：死亡瞬间饰品已被 {@link CurioGuard} 取下，
     * 现场查佩戴状态必然是 false；而能在死亡事件里登记这个标记本身就说明
     * 当时佩戴判定为 true（事件层只在佩戴时分发）。</p>
     */
    private static final PlayerScoped<Boolean> GUARDED_AT_DEATH = new PlayerScoped<>();

    /** 生命值审计状态。 */
    private static final class HealthWatch {
        double expected;
        boolean damaged;
        boolean initialized;
        /** 本 tick 伤害管线授权的降血量（硬锁消耗式放行，见 {@link #blockHealthChange}）。 */
        float damageAllowance;
        /** 本模组主动改血（负回复 / 负吸血）：本次 setHealth 直接放行。 */
        boolean ownedChange;
    }

    /** 最大生命值基线（佩戴瞬间的快照 + 当前补足量）。 */
    private static final class HealthBaseline {
        double value;
        double guard;
        boolean initialized;
        /** 连续低于基线的 tick 数：装备切换的属性重算等瞬时变化不触发补足 */
        int lowTicks;
        /** 上限被削减期间被钳制掉的血量：上限补回来后原数退还，避免「上限恢了、血却少了」 */
        float refund;
    }

    /** 致死保护冷却。 */
    private static final class LethalState {
        long readyAt;
    }

    /** 死亡时的物品 / 经验备份。 */
    private record DeathCache(ListTag inventory, int level, float progress, int total) {
    }

    @Override
    public String getName() {
        return "AegisSystem";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void onPlayerTick(Player player, LivingTickEvent event) {
        recordHealthRatio(player);
        guardHealth(player);
        guardMaxHealth(player);
        recordSafeSpot(player);
    }

    /** 重新戴上：卸下守护窗口立即失效，恢复常规佩戴保护。 */
    @Override
    public void onEquip(Player player) {
        UNEQUIP_GUARD_LEFT.remove(player.getUUID());
        PENDING_UNEQUIP.remove(player.getUUID());
    }

    @Override
    public void onUnequip(Player player) {
        // 卸下后不再保护，撤销补足并清空全部防护状态。
        // 注意：死亡备份（DEATH_CACHES）属于「死亡流程」而非「装备状态」，
        // 这里绝不能清 —— 死亡瞬间饰品会被取下（CurioGuard），若此时顺带清掉备份，
        // 重生就无法归还物品（历史故障：复活后物品与掉落物同时消失）。
        releaseMaxHealthGuard(player);
        HEALTH_WATCH.remove(player.getUUID());
        HEALTH_BASELINE.remove(player.getUUID());
        LETHAL_STATE.remove(player.getUUID());
        PENDING_UNEQUIP.remove(player.getUUID());
        // 饰品已经不在身上：濒死无敌窗口必须一并失效，
        // 否则「刚被救回 → 摘下饰品」会白白继承一整段无敌
        LETHAL_IMMUNE_LEFT.remove(player.getUUID());
        SAFE_SPOT.remove(player.getUUID());

        // 属性撤下时生命上限会从「饰品加成的巨大值」掉回基础值，而原版并不会把当前血量
        // 一起夹回去（见 setHealth / RangedAttribute 的实现）——残留的巨大血量会让原版血条
        // 被画到屏幕外、NaN 血量则让血条直接消失，表现都是「拿下来血条就空了」。
        // 这里只做兜底夹取：按比例换算已由离槽瞬间的修复（applyUnequipHealth）完成，
        // 此时再按比例压血会在「摘下 0.5 秒后」莫名掉一截。
        sanitizeHealth(player);
        HEALTH_RATIO.remove(player.getUUID());
    }

    /**
     * 记录「上一次合法的生命比例」（每 tick，佩戴期间）。
     */
    private static void recordHealthRatio(Player player) {
        float max = player.getMaxHealth();
        float health = player.getHealth();
        if (!Float.isFinite(max) || max <= 0f) return;
        if (!Float.isFinite(health) || health <= 0f || health > max) return;
        HEALTH_RATIO.put(player.getUUID(), health / max);
    }

    /**
     * 饰品刚离开槽位：立即修一次血，并开启卸下守护窗口。
     *
     * <p>为什么不能只靠 {@code EquipTracker} 的卸下边沿：那条边沿带 10 tick 防抖，
     * 而生命上限的修饰符由 Curios 在<b>它自己的 tick 处理</b>里撤销，撤销之后其它模组的
     * tick 逻辑就会看到「上限已降、血量仍是旧值」。按上限变化重算血量的实现很容易把它
     * 算成 0 并直接判死 —— 这就是「摘下饰品暴毙」。</p>
     *
     * <p>窗口内的三道保险见 {@link #UNEQUIP_GUARD_LEFT}：每 tick 修血、
     * {@link #blockHealthChange} 拒绝致死写入、{@link #lastStand} 兜底取消死亡。</p>
     */
    public static void rememberUnequip(Player player) {
        if (player.level().isClientSide()) return;
        UNEQUIP_GUARD_LEFT.put(player.getUUID(), UNEQUIP_GUARD_TICKS);
        PENDING_UNEQUIP.put(player.getUUID(), Boolean.TRUE);
        // 事件此刻属性可能还没重算（此时是旧上限，修复只做合法性检查、不会误伤）；
        // 真正的按比例换算交给下一 tick，那里一定能看到新上限
        applyUnequipHealth(player);
    }

    /** 玩家是否仍处在「摘下饰品后的守护窗口」内。 */
    public static boolean withinUnequipGuard(Player player) {
        Integer left = UNEQUIP_GUARD_LEFT.get(player.getUUID());
        return left != null && left > 0;
    }

    /**
     * 每 tick（服务端、佩戴判定之前）推进卸下守护窗口：
     * 先做一次按比例换算，再兜底修正窗口内出现的异常血量。
     */
    public static void tickUnequipGuard(Player player) {
        if (player.level().isClientSide()) return;

        boolean pending = PENDING_UNEQUIP.remove(player.getUUID()) != null;
        if (pending) applyUnequipHealth(player);

        Integer left = UNEQUIP_GUARD_LEFT.get(player.getUUID());
        if (left == null) return;
        if (left <= 0) {
            UNEQUIP_GUARD_LEFT.remove(player.getUUID());
            return;
        }
        UNEQUIP_GUARD_LEFT.put(player.getUUID(), left - 1);
        // 窗口内：上限骤降的残留值 / 被外部写成 0 / NaN → 按卸下时的比例修回合法区间
        repairHealth(player, false);
    }

    /**
     * 卸下血量修复：把当前血量换算到「摘掉加成后的上限」，并保证不会因此致死。
     *
     * <p>换算规则：</p>
     * <ul>
     *   <li>血量本来就合法（{@code 0 < 血量 <= 新上限}）→ 不动，不凭空加血；</li>
     *   <li>血量是旧上限的残留、或已被外部写成 0 / NaN → 按佩戴期间记录的
     *       <b>生命比例</b>换算：{@code 比例 × 新上限}，最少 1 点。
     *       例：满血摘下 → 新上限满血；50% 血摘下 → 新上限的 50%，既不会「夹到满血」
     *       也不会被结算成 0。</li>
     * </ul>
     *
     * <p>写入走 {@link #applyOwnedHealth}：此刻卸下边沿可能还没到（防抖窗口内
     * {@code EquipTracker.isEquipped} 仍是 true），普通 {@code setHealth} 会被血量硬锁
     * 判成「非法降血」拒绝，血量就永远修不回来。</p>
     */
    public static void applyUnequipHealth(Player player) {
        repairHealth(player, true);
    }

    /**
     * 血量修复的统一实现。
     *
     * @param alwaysRatio true = 只允许「按比例换算后的值」为上限（卸下瞬间用，
     *                    把旧上限的残留血量压回同等生命比例）；
     *                    false = 只在血量异常（≤0 / NaN / 超过上限）时用比例值兜底，
     *                    合法血量（包括正常受伤掉下来的）一律不干预
     */
    private static void repairHealth(Player player, boolean alwaysRatio) {
        if (player.level().isClientSide()) return;
        if (Boolean.TRUE.equals(DYING.get(player.getUUID()))) return; // 真实死亡流程：不动
        purgeNonFiniteModifiers(player);

        float max = player.getMaxHealth();
        if (!Float.isFinite(max) || max <= 0f) return; // 上限仍不正常：不硬来（避免掩盖其它模组的问题）

        // 比例目标：佩戴期间最后一次合法比例 × 新上限，至少 1 点（永不低于致死线）
        float target = Math.max(1f, Math.min(max, healthRatio(player) * max));

        float health = player.getHealth();
        // 血量已低于比例目标（摘下前后被打过 / 本来就残血）：保持玩家的真实血量，绝不凭空加血
        if (Float.isFinite(health) && health > 0f && health <= max && health <= target) return;
        // 合法但高于比例目标：只有「按比例换算」模式才压回来
        if (!alwaysRatio && Float.isFinite(health) && health > 0f && health <= max) return;

        applyOwnedHealth(player, target);
        // 修复用的审计状态不必保留（此刻通常已判定卸下，不再有 tick 维护它）
        HEALTH_WATCH.remove(player.getUUID());
    }

    /** 佩戴期间记录的生命比例（0~1）；没有记录或记录已坏时按满血处理。 */
    private static float healthRatio(Player player) {
        Float ratio = HEALTH_RATIO.get(player.getUUID());
        if (ratio == null || !Float.isFinite(ratio) || ratio <= 0f) return 1f;
        return Math.min(1f, ratio);
    }

    /**
     * 剔除生命上限上的「非有限」修饰符（NaN / Infinity 会让上限永久变脏、血条画成 0 颗心）。
     * 我方补足（固定 UUID）走撤销入口。
     */
    private static void purgeNonFiniteModifiers(Player player) {
        AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null) return;
        for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
            if (Double.isFinite(modifier.getAmount())) continue;
            if (GUARD_HEALTH_UUID.equals(modifier.getId())) {
                releaseMaxHealthGuard(player);
            } else {
                instance.removeModifier(modifier);
            }
        }
    }

    /**
     * 卸下 / 属性变动后的「血量修复」：把非有限（NaN、Infinity）、非正、或超过新上限的血量
     * 拉回合法区间，并剔除生命上限上「非有限」的坏修饰符。
     *
     * <p>为什么需要：生命上限下降时原版<b>不会</b>重新钳制当前血量（{@code setHealth} 只在被调用时
     * 才 clamp，而 {@code RangedAttribute} 对 NaN 是放行的）。饰品一摘，上限从巨大值掉回基础值，
     * 残留血量就成了脏数据：原版 HUD 用它算血条位置（{@code renderPlayerHealth} 的
     * {@code ceil(health)} / 行偏移），结果整条血条被画到屏幕外或被画成 0 颗心，
     * 看起来就是「血条直接空了、又没有死亡提示」。</p>
     *
     * <p>死亡流程内直接跳过：饰品在死亡瞬间被取下是正常行为，不能借修复之名把死者拉回满血。</p>
     */
    public static void sanitizeHealth(Player player) {
        if (player.level().isClientSide()) return;
        if (Boolean.TRUE.equals(DYING.get(player.getUUID()))) return; // 真实死亡流程：不动

        purgeNonFiniteModifiers(player);

        float max = player.getMaxHealth();
        if (!Float.isFinite(max) || max <= 0) return; // 上限仍不正常：不硬来（避免掩盖其它模组的问题）

        float health = player.getHealth();
        if (!Float.isFinite(health) || health <= 0) {
            player.setHealth(max);          // NaN / 0：属性切换残留 → 回满，血条恢复正常
            acceptHealthChange(player);
            return;
        }
        if (health > max) {
            // 上限被外部削减时原版不会自动钳制当前血量，这里先夹回上限（否则血条画到屏幕外）；
            // 但被夹掉的那部分要记账 —— 上限保护把上限补回来之后原数退还，
            // 否则「上限被削」最终会变成「上限恢复了，当前血量却永久少了一截」。
            rememberHealthLoss(player, health - max);
            player.setHealth(max);          // 超过新上限：原版 HUD 会把血条画到屏幕外 → 夹回上限
            acceptHealthChange(player);
        }
    }

    /** 记录「因上限被削减而被钳制掉的血量」，仅在上限保护开启且确实佩戴时记账。 */
    private static void rememberHealthLoss(Player player, float loss) {
        if (loss <= 0 || !Float.isFinite(loss)) return;
        if (!ConfigValues.get(EternalHeartConfig.MAX_HEALTH_GUARD)) return;
        if (!EquipTracker.isEquipped(player)) return;
        HealthBaseline baseline = HEALTH_BASELINE.compute(player.getUUID(), HealthBaseline::new);
        // 上限 1e9：任何正常生命值都够用，同时避免 float 累加溢出成 Infinity
        baseline.refund = Math.min(baseline.refund + loss, 1.0e9f);
    }

    // ============================================================
    //  1. 血量防篡改
    // ============================================================

    private void guardHealth(Player player) {
        if (!ConfigValues.get(EternalHeartConfig.HEALTH_GUARD)) return;

        HealthWatch watch = HEALTH_WATCH.compute(player.getUUID(), HealthWatch::new);
        // 上 tick 残留的伤害额度作废：授权只在「伤害事件 → setHealth」这一小段内有效
        watch.damageAllowance = 0f;
        double current = player.getHealth();

        // 血量 / 上限出现 NaN、Infinity（属性被写坏），或血量超过当前上限
        // （摘下饰品后上限掉回基础值、血量还是旧值）时，先修好再对账：
        // 否则审计会把这串脏值原样写回，血条就会一直空着。
        if (!Double.isFinite(current) || !Float.isFinite(player.getMaxHealth())
                || current > player.getMaxHealth()) {
            sanitizeHealth(player);
            current = player.getHealth();
            watch.expected = current;
            watch.initialized = true;
            return;
        }

        if (!watch.initialized || !Double.isFinite(watch.expected)) {
            // 首次对账 / 期望值已损坏：以当前值为准重同步，绝不把脏值写回
            watch.expected = current;
            watch.initialized = true;
            return;
        }

        if (watch.damaged) {
            // 期间发生过合法伤害：接受新的生命值
            watch.damaged = false;
            watch.expected = current;
            return;
        }

        if (player.isAlive() && current < watch.expected - HEALTH_EPSILON) {
            // 没有任何伤害事件却掉血 = 被旁路改血 → 回滚
            player.setHealth((float) watch.expected);
            current = player.getHealth();
        }
        watch.expected = current;
    }

    /**
     * 声明一次「由本模组按配置主动施加」的生命值变更。
     *
     * <p>负回复 / 负吸血这类配置会让生命值下降但不经过伤害管线，
     * 若不告知审计层就会被判定为「被改血」而回滚（表现为功能无效）。
     * 调用方应先完成生命值修改，再调用本方法同步审计基线。</p>
     */
    public static void acceptHealthChange(Player player) {
        HealthWatch watch = HEALTH_WATCH.get(player.getUUID());
        if (watch == null) return;
        watch.expected = player.getHealth();
        watch.damaged = false;
    }

    /**
     * {@code setHealth} 硬锁判定（由 {@code LivingEntityMixin} 在方法头调用）。
     *
     * <p>与「每 tick 审计 + 回滚」的区别：这里在写入发生的那一瞬间就拒绝，
     * 不存在「先掉血、下一 tick 才补回来」的窗口期，其它模组也不会先看到虚假的低血量。</p>
     *
     * <p>只拦「下降」，放行三类写入：</p>
     * <ol>
     *   <li><b>治疗 / 钳制到上限</b>——{@code next >= current} 或 {@code next >= maxHealth}；</li>
     *   <li><b>伤害管线授权</b>——{@link #onFinalDamage} 记录的本 tick 最终伤害量，
     *       在本次写入发生时一次性用掉（完全吸收的伤害不产生血量下降，也不会留下额度）；</li>
     *   <li><b>本模组主动改血</b>——{@link #applyOwnedHealth} 标记的负回复 / 负吸血。</li>
     * </ol>
     *
     * <p>基线尚未建立（登录 / 数据加载阶段）时一律放行：此时 {@code setHealth} 是原版在读存档，
     * 拦下反而会把血量改成默认值。</p>
     *
     * @return true = 拒绝这次写入
     */
    public static boolean blockHealthChange(Player player, float next) {
        if (!ConfigValues.get(EternalHeartConfig.HEALTH_GUARD)) return false;
        if (!ConfigValues.get(EternalHeartConfig.HEALTH_GUARD_HARD)) return false;
        if (player.level().isClientSide()) return false;
        if (!EquipTracker.isEquipped(player)) {
            // 卸下守护窗口：上限刚被 Curios 撤掉，此刻任何「绕过伤害管线的致死写入」
            // （把血量写成 0 / NaN）都直接拒绝 —— 这是「摘下饰品暴毙」的最后一道拦截。
            // 正常伤害在窗口内同样放行（它有伤害额度，且真死了还有 lastStand 兜底）。
            if (withinUnequipGuard(player) && player.isAlive()
                    && (!Float.isFinite(next) || next <= 0f)) {
                return true;
            }
            return false;
        }

        HealthWatch watch = HEALTH_WATCH.compute(player.getUUID(), HealthWatch::new);
        if (!watch.initialized) {
            // 基线缺失（清理后重新登录，或外部模组把状态表清空）时不要无条件放行：
            // 就地以当前血量为基线，但「把活人写成 0 / NaN」依旧拒绝 ——
            // 状态刚被清空不等于授权这一次降血。
            watch.expected = player.getHealth();
            watch.initialized = true;
            watch.damageAllowance = 0f;
            if (!Float.isFinite(next)) return true;
            return player.isAlive() && next <= 0f;
        }

        if (!Float.isFinite(next)) return true;            // NaN / Infinity：脏值一律拒绝
        float current = player.getHealth();
        if (!Float.isFinite(current)) return false;        // 当前值已脏：交给审计层修复
        if (!player.isAlive()) return false;               // 死亡流程：不干涉
        if (next >= current - (float) HEALTH_EPSILON) return false;
        if (watch.ownedChange) return false;

        float drop = current - next;
        if (watch.damageAllowance >= drop - (float) HEALTH_EPSILON) {
            // 一次伤害事件只授权一次写入：额度当场用掉，避免致命伤害「少扣的那部分」
            // 在同一 tick 里被其它模组的直接 setHealth 顺手用掉
            watch.damageAllowance = 0f;
            return false;
        }

        // 钳制到生命上限（sanitizeHealth）永远放行，否则属性切换后的修复会被自己拦下
        float max = player.getMaxHealth();
        return !(Float.isFinite(max) && next >= max - (float) HEALTH_EPSILON);
    }

    /**
     * 本模组主动修改生命值（负回复 / 负吸血等不经过伤害管线的路径）。
     *
     * <p>硬锁开启时直接 {@code setHealth} 会被判定为「被改血」；这里在写入瞬间放行，
     * 并把审计基线同步到新值，避免下一 tick 再被回滚一次。</p>
     */
    public static void applyOwnedHealth(Player player, float health) {
        HealthWatch watch = HEALTH_WATCH.compute(player.getUUID(), HealthWatch::new);
        watch.ownedChange = true;
        try {
            player.setHealth(health);
        } finally {
            watch.ownedChange = false;
        }
        watch.expected = player.getHealth();
        watch.damaged = false;
        watch.damageAllowance = 0f;
    }

    /** 最终伤害结算：执行虚空拦截与致死保护，并登记「合法伤害」与硬锁额度。 */
    @Override
    public void onFinalDamage(Player player, LivingDamageEvent event) {
        // 虚空优先：它负责「把人送回安全落点」，拦下之后没必要再走致死保护
        if (guardVoid(player, event)) return;
        guardLethal(player, event);
        if (event.isCanceled() || event.getAmount() <= 0) return;

        HealthWatch watch = HEALTH_WATCH.get(player.getUUID());
        if (watch == null) return;
        watch.damaged = true;
        // 额度必须取「致死保护钳制之后」的最终值：多留的额度会被同一 tick 里的
        // 其它写入顺手用掉（例如外部模组的直接 setHealth）
        watch.damageAllowance = event.getAmount();
    }

    // ============================================================
    //  2. 生命上限保护
    // ============================================================

    private void guardMaxHealth(Player player) {
        if (!ConfigValues.get(EternalHeartConfig.MAX_HEALTH_GUARD)) return;

        AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null) return;

        HealthBaseline baseline = HEALTH_BASELINE.compute(player.getUUID(), HealthBaseline::new);
        double withGuard = player.getMaxHealth();
        double raw = withGuard - baseline.guard; // 剔除我方补足后的"外部值"

        // NaN / Infinity 把关：差额一旦算成 NaN 就会挂到生命上限上，让上限永久变脏
        //（NaN 会让原版血条画成 0 颗心），宁可重置基线也不写这种值
        if (!Double.isFinite(withGuard) || !Double.isFinite(raw)) {
            releaseMaxHealthGuard(player);
            baseline.initialized = false;
            baseline.lowTicks = 0;
            return;
        }

        if (!baseline.initialized || !Double.isFinite(baseline.value)) {
            baseline.value = raw;
            baseline.initialized = true;
            return;
        }

        if (raw >= baseline.value - HEALTH_EPSILON) {
            // 未被削减（或已恢复）：撤销补足并抬高峰值基线
            baseline.lowTicks = 0;
            if (baseline.guard != 0) {
                instance.removeModifier(GUARD_HEALTH_UUID);
                baseline.guard = 0;
            }
            refundHealth(player, baseline); // 上限已恢复：把期间被钳制掉的血量还回去
            if (raw > baseline.value) baseline.value = raw;
            return;
        }

        // 防抖：装备切换 / 属性重算期间的瞬时下降不补足，只有持续低于基线才算「被削减」
        if (++baseline.lowTicks < MAX_HEALTH_CONFIRM_TICKS) return;

        // 被削减：补足差额（始终只有一条修饰符，重复施加前先移除）
        double deficit = baseline.value - raw;
        if (!Double.isFinite(deficit) || deficit <= 0) {
            baseline.lowTicks = 0;
            return;
        }
        instance.removeModifier(GUARD_HEALTH_UUID);
        instance.addTransientModifier(new AttributeModifier(
                GUARD_HEALTH_UUID, "Eternal Heart Health Guard", deficit, AttributeModifier.Operation.ADDITION));
        baseline.guard = deficit;
        refundHealth(player, baseline); // 上限已补足：把期间被钳制掉的血量还回去
    }

    /** 退还「上限被削减期间被钳制掉的血量」（上限已恢复，按当前上限封顶即可）。 */
    private static void refundHealth(Player player, HealthBaseline baseline) {
        if (baseline.refund <= 0f) return;
        float amount = baseline.refund;
        baseline.refund = 0f;
        if (!player.isAlive()) return; // 死亡流程：不借此回血
        float max = player.getMaxHealth();
        if (!Float.isFinite(max) || max <= 0f) return;
        player.heal(Math.min(amount, max));
        acceptHealthChange(player);
    }

    private static void releaseMaxHealthGuard(Player player) {
        AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
        if (instance != null) instance.removeModifier(GUARD_HEALTH_UUID);
        HEALTH_BASELINE.remove(player.getUUID());
    }

    // ============================================================
    //  3. 致死保护
    // ============================================================

    /**
     * 致死保护：把「这一击必死」的伤害压到只扣到保留比例为止。
     *
     * <p><b>可被外部直接调用</b>（卸下守护窗口内佩戴判定已是 false，事件层仍需要这道保护，
     * 见 {@code EternalHeartEvents#onLivingDamage}）。</p>
     *
     * <p>两类「通常不在保护范围」的伤害由配置决定：</p>
     * <ul>
     *   <li><b>虚空伤害</b>（{@code fell_out_of_world}）永远交给出「虚空拦截」处理 ——
     *       在这里再拦一次会让玩家卡在虚空里无限挨打；</li>
     *   <li><b>无视无敌的强制击杀</b>（{@code /kill} 的 {@code generic_kill} 等）
     *       在「强制击杀兜底」开启后同样受保护。</li>
     * </ul>
     */
    public static void guardLethal(Player player, LivingDamageEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0) return;
        if (!ConfigValues.get(EternalHeartConfig.LETHAL_GUARD)) return;

        DamageSource source = event.getSource();
        // 虚空伤害：由虚空拦截专管（否则致死保护会把人钉在虚空里反复挨打）
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) return;
        // 无视无敌的伤害（/kill 等）：仅在「强制击杀兜底」开启时纳入保护
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && !ConfigValues.get(EternalHeartConfig.KILL_GUARD)) return;

        float health = player.getHealth();
        if (event.getAmount() < health) return; // 伤害不足以致命

        long now = player.level().getGameTime();
        LethalState state = LETHAL_STATE.compute(player.getUUID(), LethalState::new);
        if (now < state.readyAt) return;

        // 保留生命 = 最大生命 × 比例，最低 1 点
        float keep = Math.max(1f,
                (float) (player.getMaxHealth() * ConfigValues.get(EternalHeartConfig.LETHAL_GUARD_RATIO)));
        event.setAmount(Math.max(0f, health - keep));

        state.readyAt = now + ConfigValues.get(EternalHeartConfig.LETHAL_GUARD_COOLDOWN);
        grantLethalImmunity(player);

        player.displayClientMessage(Component.translatable(source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                ? "message.eternal_heart.kill_guard" : "message.eternal_heart.lethal_guard"), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    // ============================================================
    //  濒死无敌窗口
    // ============================================================

    /** 玩家是否仍处在致死保护 / 死亡兜底给出的无敌窗口内。 */
    public static boolean inLethalImmunity(Player player) {
        Integer left = LETHAL_IMMUNE_LEFT.get(player.getUUID());
        return left != null && left > 0;
    }

    /**
     * 每 tick 推进无敌窗口。
     *
     * <p><b>必须无条件调用</b>（由事件层在佩戴判定之前执行）：死亡兜底救人的瞬间饰品已被取下，
     * 若受佩戴判定门控，这个计数就永远不递减 —— 表现为「被救过一次之后永久无敌」。</p>
     */
    public static void tickLethalImmunity(Player player) {
        if (player.level().isClientSide()) return;
        Integer left = LETHAL_IMMUNE_LEFT.get(player.getUUID());
        if (left == null) return;
        if (left <= 1) LETHAL_IMMUNE_LEFT.remove(player.getUUID());
        else LETHAL_IMMUNE_LEFT.put(player.getUUID(), left - 1);
    }

    /** 开启无敌窗口（配置为 0 / 负时不做任何事）。 */
    private static void grantLethalImmunity(Player player) {
        int seconds = ConfigValues.get(EternalHeartConfig.LETHAL_GUARD_IMMUNE);
        if (seconds <= 0) return;
        // 秒 → tick 用 long 换算：配置上限放开后 int 乘法会溢出（表现为窗口瞬间消失）
        long ticks = Math.min(Integer.MAX_VALUE, (long) seconds * 20L);
        LETHAL_IMMUNE_LEFT.put(player.getUUID(), (int) ticks);
    }

    // ============================================================
    //  虚空拦截
    // ============================================================

    /**
     * 虚空拦截：取消掉出世界的伤害，并把玩家送回最后一次安全落点。
     *
     * <p>为什么必须拦在伤害阶段而不是只在 tick 阶段兜底：虚空伤害每 tick 都触发，
     * 单纯在底部传送（{@code SurvivalSystem#applyVoidRescue}）会先让玩家掉血几 tick，
     * 极端情况下（血量很低）还没来得及传送就已经死了。</p>
     *
     * @return true = 本次伤害已被本机制处理（调用方应停止后续处理）
     */
    private static boolean guardVoid(Player player, LivingDamageEvent event) {
        if (!ConfigValues.get(EternalHeartConfig.VOID_GUARD)) return false;
        if (!event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) return false;

        event.setCanceled(true);
        rescueFromVoid(player);
        player.displayClientMessage(
                Component.translatable("message.eternal_heart.void_guard"), true);
        return true;
    }

    /** 把玩家送回最后一次安全落点；没有落点（或维度不符）时抬到建筑上限。 */
    private static void rescueFromVoid(Player player) {
        SafeSpot spot = SAFE_SPOT.get(player.getUUID());
        Level level = player.level();

        if (spot != null && spot.dimension().equals(level.dimension())) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.teleportTo(serverPlayer.serverLevel(),
                        spot.x(), spot.y(), spot.z(), serverPlayer.getYRot(), serverPlayer.getXRot());
            } else {
                player.teleportTo(spot.x(), spot.y(), spot.z());
            }
        } else {
            // 没有可用落点：与生存系统的虚空救援保持一致（抬到建筑上限，配合摔落免疫不会摔死）
            player.teleportTo(player.getX(), level.getMaxBuildHeight(), player.getZ());
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        player.clearFire();
    }

    /**
     * 采样「安全落点」：站住、没在高速移动、不在世界底部时才记录。
     *
     * <p>只走佩戴者的 tick 路径，节流 {@link #SAFE_SPOT_INTERVAL} tick，
     * 常态开销是每 0.5 秒一次地面判定。</p>
     */
    private static void recordSafeSpot(Player player) {
        if (player.level().isClientSide()) return;
        if (!player.isAlive() || !player.onGround()) return;
        if (!Ticker.due(player.getUUID(), player.level().getGameTime(), SAFE_SPOT_INTERVAL)) return;

        Level level = player.level();
        if (player.getY() < level.getMinBuildHeight() + SAFE_SPOT_MIN_HEIGHT_MARGIN) return;
        if (player.getDeltaMovement().horizontalDistanceSqr() > SAFE_SPOT_MAX_HORIZONTAL_SPEED_SQR) return;

        Vec3 pos = player.position();
        SAFE_SPOT.put(player.getUUID(), new SafeSpot(level.dimension(), pos.x(), pos.y(), pos.z()));
    }

    /**
     * 死亡兜底：致死保护没拦住、死亡事件已经走到收尾阶段时，强行取消这次死亡。
     *
     * <p>覆盖致死保护管不到的路径：其它模组直接 {@code setHealth(0)} 后调用 {@code die()}、
     * 完全不经过 {@code LivingDamageEvent} 的伤害实现等。判定依据是
     * {@link #GUARDED_AT_DEATH}——死亡瞬间饰品会被 {@link CurioGuard} 取下，
     * 收尾阶段现场查佩戴状态必然是 false。</p>
     *
     * <p>与致死保护<b>共享冷却</b>：刚被致死保护救过的那段时间里不再重复兜底，
     * 避免「致死保护 + 死亡兜底」叠成无冷却的绝对无敌。</p>
     *
     * @return true = 已取消死亡并恢复生命值
     */
    public static boolean lastStand(Player player, LivingDeathEvent event) {
        boolean guardedAtDeath = Boolean.TRUE.equals(GUARDED_AT_DEATH.remove(player.getUUID()));
        // 「血量仍大于 0 却触发死亡」只可能来自绕过伤害管线的强制击杀：
        // 典型形态是先 setHealth(0)（被硬锁拒绝）再直接 die()。这类路径既有伤害事件
        // 之外的写法，也不能指望 GUARDED_AT_DEATH 被登记，必须在这里兜底。
        boolean bypassedHurtPipeline = player.getHealth() > 0f;
        // 摘下饰品后的守护窗口同样适用：上限刚被撤掉、血量被其它模组结算成 0 的路径，
        // 此刻佩戴判定已经是 false，靠 GUARDED_AT_DEATH 等不到登记
        if (!guardedAtDeath && !withinUnequipGuard(player) && !bypassedHurtPipeline) return false;
        if (!ConfigValues.get(EternalHeartConfig.DEATH_GUARD)) return false;

        DamageSource source = event.getSource();
        // 虚空伤害：交给虚空拦截（在这里兜底会让人卡在虚空里反复触发）
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) return false;
        // 无视无敌的强制击杀（/kill 等）：仅在「强制击杀兜底」开启时纳入保护
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && !ConfigValues.get(EternalHeartConfig.KILL_GUARD)) return false;

        if (!recoverFromLethal(player, source)) return false;
        event.setCanceled(true);
        return true;
    }

    /**
     * 死亡判定的最终硬拦截（由 {@code LivingEntityMixin} 注入 {@code die} 的 HEAD 调用）。
     *
     * <p>正常死亡一定经过伤害管线：血量先被扣到 0，{@code die()} 才被调用。反过来，
     * 「血量仍大于 0 却要 {@code die()}」只可能来自绕过伤害管线的强制击杀
     * （写血被硬锁拒绝后仍然调用 {@code die()}、直接 {@code kill()} 等）。
     * 这里在死亡流程真正开始之前拦下，比收尾阶段的 {@link #lastStand} 更早 ——
     * 此时死亡事件还没 post，背包不会被清空，也不存在任何中间态。</p>
     *
     * @return true = 已恢复生命值，调用方应取消这次 {@code die()}
     */
    public static boolean blockInstantDeath(Player player, DamageSource source) {
        if (player.level().isClientSide()) return false;
        if (!ConfigValues.get(EternalHeartConfig.DEATH_GUARD)) return false;
        // 虚空：交给「虚空拦截」专管，在这里拦会让人卡在虚空里反复触发
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) return false;
        // 血量已经归零 = 正常伤害致死，交给致死保护 / 死亡兜底按原逻辑处理
        if (!(player.getHealth() > 0f)) return false;
        if (!EquipTracker.isEquipped(player) && !withinUnequipGuard(player)) return false;
        return recoverFromLethal(player, source);
    }

    /**
     * 致死保护 / 死亡兜底 / 死亡硬拦截的公共收尾：按保留比例回血、进入共享冷却、
     * 给出一段濒死无敌窗口。
     *
     * @return true = 已恢复（调用方应取消死亡）
     */
    private static boolean recoverFromLethal(Player player, DamageSource source) {
        long now = player.level().getGameTime();
        LethalState state = LETHAL_STATE.compute(player.getUUID(), LethalState::new);
        if (now < state.readyAt) return false;

        float keep = Math.max(1f,
                (float) (player.getMaxHealth() * ConfigValues.get(EternalHeartConfig.LETHAL_GUARD_RATIO)));
        // 直接 die() 的路径里血量可能仍 > 0：不低于保留比例时保持原血量，不白白扣一截
        float current = player.getHealth();
        float restored = Float.isFinite(current) ? Math.max(keep, current) : keep;
        // 走「本模组主动改血」入口：普通 setHealth 会被硬锁判定为非法降血
        applyOwnedHealth(player, restored);
        player.clearFire();
        state.readyAt = now + ConfigValues.get(EternalHeartConfig.LETHAL_GUARD_COOLDOWN);
        grantLethalImmunity(player);

        player.displayClientMessage(Component.translatable(source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                ? "message.eternal_heart.kill_guard" : "message.eternal_heart.death_guard"), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    // ============================================================
    //  4. 死亡保留（防被收）
    // ============================================================

    @Override
    public void onPlayerDeath(Player player, LivingDeathEvent event) {
        // 只要死亡事件走到这里就登记「死亡流程中」：卸下时的血量修复要跳过死者
        //（死亡瞬间饰品会被 CurioGuard 取下，随后会正常分发一次卸下）。
        // 死亡被取消（不死图腾 / 灵魂绑定）时由收尾阶段的 undoDeath 清掉。
        DYING.put(player.getUUID(), Boolean.TRUE);

        // 死亡已被其它模块取消（不死图腾 / 灵魂绑定）：不做任何处理
        if (event.isCanceled()) return;

        // 记录「死亡瞬间确实佩戴着」：饰品随后会被 CurioGuard 取下，
        // 死亡兜底（LOWEST 收尾阶段）无法再现场判定佩戴状态
        GUARDED_AT_DEATH.put(player.getUUID(), Boolean.TRUE);

        boolean keepItems = ConfigValues.get(EternalHeartConfig.KEEP_INVENTORY);
        boolean keepExperience = ConfigValues.get(EternalHeartConfig.KEEP_EXPERIENCE);
        if (!keepItems && !keepExperience) return;

        ListTag inventory = keepItems ? player.getInventory().save(new ListTag()) : null;
        DeathCache cache = new DeathCache(inventory,
                player.experienceLevel, player.experienceProgress, player.totalExperience);
        DEATH_CACHES.put(player.getUUID(), cache);
        writeBackup(player, cache);

        // 立即清空：原版死亡掉落流程将无物可掉，重生时再原样归还
        if (keepItems) player.getInventory().clearContent();
        if (keepExperience) {
            player.experienceLevel = 0;
            player.experienceProgress = 0F;
            player.totalExperience = 0;
        }
    }

    /**
     * 死亡最终被取消时撤销处理（由事件层在 {@code LivingDeathEvent} 的收尾阶段调用）。
     *
     * <p>清空动作必须跑在很早的优先级：其它模组看到的是空背包，灵魂石 / 墓碑 / 收集类机制
     * 无从下手。但<strong>复活类模组会在更晚的优先级取消死亡事件</strong>（灵魂石、不死图腾等）。
     * 若不做收尾，玩家就会「复活了，可物品栏是空的、地上也没有掉落物」——
     * 这里把备份原样还回去，两种结局都成立：真死 → 重生归还；假死 → 立即归还。</p>
     */
    public static void undoDeath(Player player) {
        DYING.remove(player.getUUID()); // 死亡没真的发生：解除死亡流程标记
        GUARDED_AT_DEATH.remove(player.getUUID());
        DeathCache cache = DEATH_CACHES.remove(player.getUUID());
        if (cache == null) cache = readBackup(player);
        if (cache == null) return;
        clearBackup(player);
        apply(player, cache);
    }

    /**
     * 重生数据复制时归还物品与经验（由事件层在 {@code PlayerEvent.Clone} 中调用）。
     * 内存备份优先，持久化备份兜底（服务器在玩家死亡与重生之间重启的场景）。
     *
     * @param wasDeath 是否为死亡重生（仅死亡重生才归还）
     */
    public static void restoreOnRespawn(Player oldPlayer, Player newPlayer, boolean wasDeath) {
        DYING.remove(oldPlayer.getUUID());
        DYING.remove(newPlayer.getUUID());
        GUARDED_AT_DEATH.remove(oldPlayer.getUUID());
        GUARDED_AT_DEATH.remove(newPlayer.getUUID());
        // 真死过一次：上一轮的濒死无敌窗口与安全落点都不该带到新身体上
        LETHAL_IMMUNE_LEFT.remove(oldPlayer.getUUID());
        LETHAL_IMMUNE_LEFT.remove(newPlayer.getUUID());
        SAFE_SPOT.remove(oldPlayer.getUUID());
        SAFE_SPOT.remove(newPlayer.getUUID());

        if (!wasDeath) return;

        DeathCache cache = DEATH_CACHES.remove(oldPlayer.getUUID());
        if (cache == null) cache = readBackup(oldPlayer);
        if (cache == null) cache = readBackup(newPlayer);
        clearBackup(oldPlayer);
        clearBackup(newPlayer);
        apply(newPlayer, cache);
    }

    /**
     * 重生 / 登录兜底：{@code PlayerEvent.Clone} 未触发时把备份还回去。
     *
     * <p>正常流程下备份已在 Clone 阶段消费（这里为空操作）。玩家仍处于死亡状态时直接跳过：
     * 此时归还只会把物品塞回「尸体」，而克隆并不复制背包，反而真的丢失 —— 交给重生流程处理。</p>
     */
    public static void restoreIfPending(Player player) {
        if (!player.isAlive()) return;
        // 活着就说明不在死亡流程里（兜底：死亡被比收尾更晚的优先级取消时也要解除标记）
        DYING.remove(player.getUUID());
        GUARDED_AT_DEATH.remove(player.getUUID());

        DeathCache cache = DEATH_CACHES.remove(player.getUUID());
        if (cache == null) cache = readBackup(player);
        if (cache == null) return;
        clearBackup(player);
        apply(player, cache);
    }

    /**
     * 归还备份：背包为空时直接载入（原样恢复槽位），背包已有物品时改为合并，
     * 绝不因为归还而抹掉玩家手里现有的东西。
     */
    private static void apply(Player player, DeathCache cache) {
        if (cache == null) return;

        if (cache.inventory() != null) {
            if (player.getInventory().isEmpty()) {
                player.getInventory().load(cache.inventory());
            } else {
                for (int i = 0; i < cache.inventory().size(); i++) {
                    ItemStack stack = ItemStack.of(cache.inventory().getCompound(i));
                    if (stack.isEmpty()) continue;
                    if (!player.getInventory().add(stack)) player.drop(stack, false);
                }
            }
        }

        player.experienceLevel = cache.level();
        player.experienceProgress = cache.progress();
        player.totalExperience = cache.total();
        player.getInventory().setChanged();
    }

    private static void writeBackup(Player player, DeathCache cache) {
        CompoundTag backup = new CompoundTag();
        if (cache.inventory() != null) backup.put(BACKUP_INVENTORY, cache.inventory());
        backup.putInt(BACKUP_LEVEL, cache.level());
        backup.putFloat(BACKUP_PROGRESS, cache.progress());
        backup.putInt(BACKUP_TOTAL, cache.total());
        player.getPersistentData().put(backupKey(player), backup);
        // 迁移后不再保留旧键，避免两份备份并存
        player.getPersistentData().remove(DEATH_BACKUP_LEGACY_KEY);
    }

    private static DeathCache readBackup(Player player) {
        CompoundTag data = player.getPersistentData();
        String key = backupKey(player);
        if (!data.contains(key, Tag.TAG_COMPOUND)) {
            // 升级兼容：旧版本固定键里可能还留着未归还的备份
            if (!data.contains(DEATH_BACKUP_LEGACY_KEY, Tag.TAG_COMPOUND)) return null;
            key = DEATH_BACKUP_LEGACY_KEY;
        }
        CompoundTag backup = data.getCompound(key);

        ListTag inventory = backup.contains(BACKUP_INVENTORY, Tag.TAG_LIST)
                ? backup.getList(BACKUP_INVENTORY, Tag.TAG_COMPOUND) : null;
        return new DeathCache(inventory, backup.getInt(BACKUP_LEVEL),
                backup.getFloat(BACKUP_PROGRESS), backup.getInt(BACKUP_TOTAL));
    }

    private static void clearBackup(Player player) {
        player.getPersistentData().remove(backupKey(player));
        player.getPersistentData().remove(DEATH_BACKUP_LEGACY_KEY);
    }

    /**
     * 清理防护状态（对外入口）。
     *
     * <p>只清「可随时重建」的软状态；血量硬锁、致死保护、死亡兜底依赖的状态
     * <b>必须留到玩家真正离线</b>：这些状态一旦被清空，{@code blockHealthChange}
     * 会因为读不到基线而放行任意降血，{@code lastStand} 也会因为等不到
     * {@code GUARDED_AT_DEATH} / 卸下守护窗口而直接放弃。</p>
     *
     * <p>历史实现是全部清空，外部模组只要反射调用一次本方法（或调用
     * {@code FeatureManager.dispatchUnequip} 再调用本方法），整套死亡保护就被拆掉了。</p>
     */
    public static void forget(Player player) {
        UUID id = player.getUUID();
        SAFE_SPOT.remove(id);
        HEALTH_RATIO.remove(id);
        PENDING_UNEQUIP.remove(id);
    }

    /** 玩家真正退出时清理全部防护状态（内存回收；持久化数据不受影响）。 */
    public static void forgetAll(Player player) {
        forget(player);
        UUID id = player.getUUID();
        HEALTH_WATCH.remove(id);
        HEALTH_BASELINE.remove(id);
        LETHAL_STATE.remove(id);
        DEATH_CACHES.remove(id);
        DYING.remove(id);
        GUARDED_AT_DEATH.remove(id);
        UNEQUIP_GUARD_LEFT.remove(id);
        LETHAL_IMMUNE_LEFT.remove(id);
    }

    // ============================================================
    //  掉落物归属保护
    // ============================================================

    /** 为玩家抛出的物品打上归属标记（仅当抛出者佩戴饰品且开启保护）。 */
    public static void markThrown(ItemEntity item, Player thrower) {
        if (!ConfigValues.get(EternalHeartConfig.ITEM_GUARD)) return;
        if (item.level().isClientSide()) return;
        if (!EquipTracker.isEquipped(thrower)) return;

        CompoundTag data = item.getPersistentData();
        data.putUUID(ITEM_OWNER_KEY, thrower.getUUID());
        data.putLong(ITEM_UNTIL_KEY,
                item.level().getGameTime() + ConfigValues.get(EternalHeartConfig.ITEM_GUARD_TICKS));
    }

    /** 该物品当前是否禁止被 picker 拾取（保护期内且非本人）。 */
    public static boolean isPickupBlocked(ItemEntity item, Player picker) {
        if (!ConfigValues.get(EternalHeartConfig.ITEM_GUARD)) return false;
        if (item.level().isClientSide()) return false;

        CompoundTag data = item.getPersistentData();
        if (!data.hasUUID(ITEM_OWNER_KEY)) return false;
        if (data.getUUID(ITEM_OWNER_KEY).equals(picker.getUUID())) return false;
        return item.level().getGameTime() <= data.getLong(ITEM_UNTIL_KEY);
    }
}
