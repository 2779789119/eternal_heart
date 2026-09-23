package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.core.PlayerScoped;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;

import java.util.UUID;

/**
 * 防护机制（Aegis）—— 「防止被改血、防止被秒、防止被收」的兜底防线。
 *
 * <h3>1. 血量防篡改（{@code healthGuard}）</h3>
 * <p>伤害是唯一被允许降低生命值的途径，因此本模块每 tick 做一次血量审计：</p>
 * <ul>
 *   <li>最终伤害事件（{@link #onFinalDamage}）会把「本周期发生过合法伤害」写入审计状态；</li>
 *   <li>下一 tick 若发现生命值下降，但期间<strong>没有任何合法伤害事件</strong>，
 *       说明生命值是被 {@code setHealth} 之类的旁路手段直接修改的
 *       （典型场景：被其它模组偷血、被机制强制削血）——立即回滚到上一 tick 的值。</li>
 * </ul>
 * <p>因为判定依据是「有没有走过伤害管线」，正常受伤、饥饿、中毒、虚空伤害
 * 都会被正确放行，不会误判成改血。</p>
 *
 * <h3>2. 生命上限保护（{@code maxHealthGuard}）</h3>
 * <p>佩戴瞬间记录最大生命值为基线；此后若最大生命值被外部削减到基线以下，
 * 用固定 UUID 的补足修饰符把差额补回来；一旦外部恢复（或玩家换上更强装备），
 * 补足自动撤销，不会叠加。</p>
 *
 * <h3>3. 致死保护（{@code lethalGuard}）</h3>
 * <p>在最终伤害阶段拦截「一击必杀」：把伤害下调到只扣到保留生命比例为止，
 * 并进入冷却。与「不死图腾 / 灵魂绑定」形成两层防线 —— 前者阻止被秒，
 * 后者兜底阻止死亡。</p>
 *
 * <h3>4. 防被收（{@code keepInventory} / {@code keepExperience} / {@code itemGuard}）</h3>
 * <ul>
 *   <li>死亡时先把物品栏与经验备份到内存，然后<strong>立即清空</strong>：
 *       这样原版的死亡掉落流程无物可掉（不会有掉落物被别人或收集类机制收走），
 *       重生时再把备份原样归还；</li>
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

    /** 死亡备份（同时写入玩家持久化数据，防止"死亡后服务器崩溃"导致物品永久丢失）。 */
    private static final String DEATH_BACKUP_KEY = "eternal_heart_death_backup";
    private static final String BACKUP_INVENTORY = "inventory";
    private static final String BACKUP_LEVEL = "level";
    private static final String BACKUP_PROGRESS = "progress";
    private static final String BACKUP_TOTAL = "total";

    private static final PlayerScoped<HealthWatch> HEALTH_WATCH = new PlayerScoped<>();
    private static final PlayerScoped<HealthBaseline> HEALTH_BASELINE = new PlayerScoped<>();
    private static final PlayerScoped<LethalState> LETHAL_STATE = new PlayerScoped<>();
    private static final PlayerScoped<DeathCache> DEATH_CACHES = new PlayerScoped<>();

    /** 生命值审计状态。 */
    private static final class HealthWatch {
        double expected;
        boolean damaged;
        boolean initialized;
    }

    /** 最大生命值基线（佩戴瞬间的快照 + 当前补足量）。 */
    private static final class HealthBaseline {
        double value;
        double guard;
        boolean initialized;
        /** 连续低于基线的 tick 数：装备切换的属性重算等瞬时变化不触发补足 */
        int lowTicks;
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
        guardHealth(player);
        guardMaxHealth(player);
    }

    @Override
    public void onUnequip(Player player) {
        // 卸下后不再保护，撤销补足并清空全部防护状态
        releaseMaxHealthGuard(player);
        HEALTH_WATCH.remove(player.getUUID());
        HEALTH_BASELINE.remove(player.getUUID());
        LETHAL_STATE.remove(player.getUUID());
        DEATH_CACHES.remove(player.getUUID());
    }

    // ============================================================
    //  1. 血量防篡改
    // ============================================================

    private void guardHealth(Player player) {
        if (!ConfigValues.get(EternalHeartConfig.HEALTH_GUARD)) return;

        HealthWatch watch = HEALTH_WATCH.compute(player.getUUID(), HealthWatch::new);
        double current = player.getHealth();

        if (!watch.initialized) {
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

    /** 最终伤害结算：登记「合法伤害发生」，并执行致死保护。 */
    @Override
    public void onFinalDamage(Player player, LivingDamageEvent event) {
        if (!event.isCanceled() && event.getAmount() > 0) {
            HealthWatch watch = HEALTH_WATCH.get(player.getUUID());
            if (watch != null) watch.damaged = true;
        }
        guardLethal(player, event);
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

        if (!baseline.initialized) {
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
            if (raw > baseline.value) baseline.value = raw;
            return;
        }

        // 防抖：装备切换 / 属性重算期间的瞬时下降不补足，只有持续低于基线才算「被削减」
        if (++baseline.lowTicks < MAX_HEALTH_CONFIRM_TICKS) return;

        // 被削减：补足差额（始终只有一条修饰符，重复施加前先移除）
        double deficit = baseline.value - raw;
        instance.removeModifier(GUARD_HEALTH_UUID);
        instance.addTransientModifier(new AttributeModifier(
                GUARD_HEALTH_UUID, "Eternal Heart Health Guard", deficit, AttributeModifier.Operation.ADDITION));
        baseline.guard = deficit;
    }

    private static void releaseMaxHealthGuard(Player player) {
        AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
        if (instance != null) instance.removeModifier(GUARD_HEALTH_UUID);
        HEALTH_BASELINE.remove(player.getUUID());
    }

    // ============================================================
    //  3. 致死保护
    // ============================================================

    private void guardLethal(Player player, LivingDamageEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0) return;
        if (!ConfigValues.get(EternalHeartConfig.LETHAL_GUARD)) return;
        // 无视无敌的伤害不在保护范围（/kill、虚空等强制击杀途径）
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

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

        player.displayClientMessage(
                Component.translatable("message.eternal_heart.lethal_guard"), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    // ============================================================
    //  4. 死亡保留（防被收）
    // ============================================================

    @Override
    public void onPlayerDeath(Player player, LivingDeathEvent event) {
        // 死亡已被其它模块取消（不死图腾 / 灵魂绑定）：不做任何处理
        if (event.isCanceled()) return;

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
     * 重生数据复制时归还物品与经验（由事件层在 {@code PlayerEvent.Clone} 中调用）。
     * 内存备份优先，持久化备份兜底（服务器在玩家死亡与重生之间重启的场景）。
     *
     * @param wasDeath 是否为死亡重生（仅死亡重生才归还）
     */
    public static void restoreOnRespawn(Player oldPlayer, Player newPlayer, boolean wasDeath) {
        if (!wasDeath) return;

        DeathCache cache = DEATH_CACHES.remove(oldPlayer.getUUID());
        if (cache == null) cache = readBackup(oldPlayer);
        if (cache == null) cache = readBackup(newPlayer);
        clearBackup(oldPlayer);
        clearBackup(newPlayer);
        if (cache == null) return;

        if (cache.inventory() != null) newPlayer.getInventory().load(cache.inventory());
        newPlayer.experienceLevel = cache.level();
        newPlayer.experienceProgress = cache.progress();
        newPlayer.totalExperience = cache.total();
    }

    private static void writeBackup(Player player, DeathCache cache) {
        CompoundTag backup = new CompoundTag();
        if (cache.inventory() != null) backup.put(BACKUP_INVENTORY, cache.inventory());
        backup.putInt(BACKUP_LEVEL, cache.level());
        backup.putFloat(BACKUP_PROGRESS, cache.progress());
        backup.putInt(BACKUP_TOTAL, cache.total());
        player.getPersistentData().put(DEATH_BACKUP_KEY, backup);
    }

    private static DeathCache readBackup(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(DEATH_BACKUP_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag backup = data.getCompound(DEATH_BACKUP_KEY);

        ListTag inventory = backup.contains(BACKUP_INVENTORY, Tag.TAG_LIST)
                ? backup.getList(BACKUP_INVENTORY, Tag.TAG_COMPOUND) : null;
        return new DeathCache(inventory, backup.getInt(BACKUP_LEVEL),
                backup.getFloat(BACKUP_PROGRESS), backup.getInt(BACKUP_TOTAL));
    }

    private static void clearBackup(Player player) {
        player.getPersistentData().remove(DEATH_BACKUP_KEY);
    }

    /** 玩家退出时清理防护状态。 */
    public static void forget(Player player) {
        HEALTH_WATCH.remove(player.getUUID());
        HEALTH_BASELINE.remove(player.getUUID());
        LETHAL_STATE.remove(player.getUUID());
        DEATH_CACHES.remove(player.getUUID());
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
