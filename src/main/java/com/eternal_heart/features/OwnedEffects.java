package com.eternal_heart.features;

import com.eternal_heart.EternalHeartMod;
import com.eternal_heart.core.Debuffs;
import com.eternal_heart.core.PlayerScoped;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Map;

/**
 * 被动效果所有账本 —— 跟踪「我方施加的被动效果」，并在卸下 / 清理时精确归还
 * 玩家原本拥有的外部效果（含隐藏效果链的剩余时长）。
 *
 * <p>重写要点（行为与原实现完全一致，仅换掉底层存储）：</p>
 * <ul>
 *   <li><b>内存镜像</b>：历史实现每次 {@code maintain} / {@code release} 都要在 NBT 里
 *       做 2~4 次复合标签查找、可能新建标签对象、再写回；现在热路径只访问一张
 *       fastutil 哈希表，NBT 仅在<strong>账本发生变更时</strong>整体写回一次，
 *       用于跨存档 / 跨重启的持久化。</li>
 *   <li><b>惰性加载</b>：玩家重登或服务器重启后，账本在首次访问时从 NBT 还原，
 *       保证「重启后卸下饰品不会残留我们施加的效果」。</li>
 *   <li><b>零分配快速路径</b>：效果移除事件先查内存账本，没有记录直接返回，
 *       不再为无关效果构建任何对象。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EternalHeartMod.MODID)
public final class OwnedEffects {

    private static final String KEY = "eternal_heart_owned_effects";

    /**
     * 诊断日志（debug 级，仅在被动效果真的被重建 / 被外部移除时输出）。
     *
     * <p>用于定位「效果图标每秒闪一下」这类现象：日志会明确给出
     * 「哪个效果、剩余时长、由维持逻辑重建」还是「被外部移除后由我们补回」。</p>
     */
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 标记「正在施加我方效果」，供 {@code MobEffectEvent.Added} 识别并忽略。 */
    private static final ThreadLocal<MobEffectInstance> ADDING = new ThreadLocal<>();
    /** 标记「正在移除我方效果（为归还外部效果）」，供 {@code MobEffectEvent.Remove} 识别并忽略。 */
    private static final ThreadLocal<Player> REMOVING = new ThreadLocal<>();

    private static final PlayerScoped<Ledger> LEDGERS = new PlayerScoped<>();

    private OwnedEffects() {
    }

    /** 单个效果的所有权记录：外部效果快照 + 快照时刻 + 我方施加的等级。 */
    private record Entry(CompoundTag outside, long time, int amplifier) {
    }

    /** 一名玩家的全部所有权记录。 */
    private static final class Ledger {
        final Map<MobEffect, Entry> entries = new Object2ObjectOpenHashMap<>(8);
    }

    // ============================================================
    //  维持 / 释放
    // ============================================================

    /**
     * 维持一个被动效果：缺失或即将淡出时补加，并记录玩家原本持有的外部效果。
     *
     * @param duration     我方施加的持续时间
     * @param amplifier    要求的等级（现存效果等级更高时不覆盖）
     * @param refreshBelow 剩余时长低于此值时刷新（避免夜视等效果的淡出闪烁）
     */
    public static void maintain(Player player, MobEffect effect, boolean enabled,
                                int duration, int amplifier, int refreshBelow) {
        if (player.level().isClientSide()) return;
        if (!enabled || Debuffs.blocked(effect)) {
            // 被强制拦截的效果（黑名单 / 负面分类）：不维持并归还外部效果——
            // 让黑名单对永恒之心维持的被动效果同样生效，避免「清扫掉又立刻加回」的拉锯
            release(player, effect);
            return;
        }

        Ledger ledger = ledger(player);

        // 等级调整：旧记录等级与目标不一致时先归还外部效果，重新记录
        Entry entry = ledger.entries.get(effect);
        if (entry != null && entry.amplifier() != amplifier) {
            release(player, effect);
        }

        MobEffectInstance current = player.getEffect(effect);
        if (current != null && current.getAmplifier() >= amplifier
                && (current.isInfiniteDuration() || current.getDuration() > refreshBelow)) {
            return;
        }

        if (!ledger.entries.containsKey(effect)) {
            CompoundTag outside = current == null ? null : current.save(new CompoundTag());
            ledger.entries.put(effect, new Entry(outside, player.level().getGameTime(), amplifier));
            flush(player, ledger);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[永恒之心] 重建被动效果 {}（原有剩余 {} → {}，层数 {}）",
                    BuiltInRegistries.MOB_EFFECT.getKey(effect),
                    current == null ? "无" : current.getDuration(), duration, amplifier);
        }
        add(player, new MobEffectInstance(effect, duration, amplifier, false, false, true));
    }

    /** 释放单个效果：归还外部效果并删除记录。 */
    public static void release(Player player, MobEffect effect) {
        if (player.level().isClientSide()) return;
        Ledger ledger = presentLedger(player);
        if (ledger == null) return;
        if (releaseEntry(player, ledger, effect)) flush(player, ledger);
    }

    /** 释放全部记录（卸下饰品 / 玩家退出时调用）。 */
    public static void releaseAll(Player player) {
        if (player.level().isClientSide()) return;
        Ledger ledger = presentLedger(player);
        if (ledger == null) return;
        for (MobEffect effect : new ArrayList<>(ledger.entries.keySet())) {
            releaseEntry(player, ledger, effect);
        }
        flush(player, ledger);
    }

    /** 玩家退出时清除内存镜像（账本已随 NBT 持久化）。 */
    public static void forget(Player player) {
        LEDGERS.remove(player.getUUID());
    }

    /**
     * 归还单个效果。返回是否修改了账本。
     * 移除被外部否决（veto）时保留记录，避免把仍存活的我们施加的效果重复叠加。
     */
    private static boolean releaseEntry(Player player, Ledger ledger, MobEffect effect) {
        Entry entry = ledger.entries.get(effect);
        if (entry == null) return false;

        MobEffectInstance outside = outside(entry, player.level().getGameTime());
        if (player.getEffect(effect) == null) {
            ledger.entries.remove(effect);
            return true;
        }
        if (!removeOwned(player, effect)) return false;
        ledger.entries.remove(effect);
        if (outside != null) add(player, outside);
        return true;
    }

    private static void add(Player player, MobEffectInstance effect) {
        MobEffectInstance previous = ADDING.get();
        ADDING.set(effect);
        try {
            player.addEffect(effect);
        } finally {
            if (previous == null) ADDING.remove(); else ADDING.set(previous);
        }
    }

    private static boolean removeOwned(Player player, MobEffect effect) {
        Player previous = REMOVING.get();
        REMOVING.set(player);
        try {
            return player.removeEffect(effect);
        } finally {
            if (previous == null) REMOVING.remove(); else REMOVING.set(previous);
        }
    }

    // ============================================================
    //  外部效果变更监听
    // ============================================================

    /** 外部来源在我们持有记录期间施加了同一效果：并入快照，卸下时一并归还。 */
    @SubscribeEvent
    public static void onAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()
                || event.getEffectInstance() == ADDING.get()) {
            return;
        }
        MobEffect effect = event.getEffectInstance().getEffect();
        Ledger ledger = presentLedger(player);
        if (ledger == null) return;
        Entry entry = ledger.entries.get(effect);
        if (entry == null) return;

        long now = player.level().getGameTime();
        MobEffectInstance external = outside(entry, now);
        MobEffectInstance incoming = MobEffectInstance.load(event.getEffectInstance().save(new CompoundTag()));
        if (external == null) external = incoming; else external.update(incoming);

        ledger.entries.put(effect, new Entry(external.save(new CompoundTag()), now, entry.amplifier()));
        flush(player, ledger);
    }

    /** 外部来源移除了效果（牛奶 / 命令）：同步清空记录，否则卸下时会"复活"它。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRemoved(MobEffectEvent.Remove event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()
                || REMOVING.get() == player) {
            return;
        }
        Ledger ledger = presentLedger(player);
        if (ledger == null || ledger.entries.remove(event.getEffect()) == null) return;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[永恒之心] 被动效果 {} 被外部移除，账本记录已清空",
                    BuiltInRegistries.MOB_EFFECT.getKey(event.getEffect()));
        }
        flush(player, ledger);
    }

    /** 效果自然过期：记录一并失效。 */
    @SubscribeEvent
    public static void onExpired(MobEffectEvent.Expired event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()
                || event.getEffectInstance() == null) {
            return;
        }
        Ledger ledger = presentLedger(player);
        if (ledger == null || ledger.entries.remove(event.getEffectInstance().getEffect()) == null) return;
        flush(player, ledger);
    }

    // ============================================================
    //  账本存取
    // ============================================================

    /** 读取（必要时创建）账本。 */
    private static Ledger ledger(Player player) {
        Ledger cached = LEDGERS.get(player.getUUID());
        if (cached != null) return cached;
        Ledger loaded = load(player);
        LEDGERS.put(player.getUUID(), loaded);
        return loaded;
    }

    /** 读取账本；若确实没有记录则返回 null（供只读路径使用，避免无意义地建立空账本）。 */
    private static Ledger presentLedger(Player player) {
        Ledger cached = LEDGERS.get(player.getUUID());
        if (cached != null) return cached.entries.isEmpty() ? null : cached;
        Ledger loaded = load(player);
        if (loaded.entries.isEmpty()) return null;
        LEDGERS.put(player.getUUID(), loaded);
        return loaded;
    }

    /** 从持久化 NBT 还原账本。 */
    private static Ledger load(Player player) {
        Ledger ledger = new Ledger();
        CompoundTag saved = player.getPersistentData().getCompound(KEY);
        if (saved.isEmpty()) return ledger;
        for (String key : saved.getAllKeys()) {
            if (!(saved.get(key) instanceof CompoundTag record)) continue;
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(key));
            if (effect == null) continue;
            CompoundTag outside = record.contains("outside", Tag.TAG_COMPOUND)
                    ? record.getCompound("outside") : null;
            ledger.entries.put(effect, new Entry(outside, record.getLong("time"), record.getInt("amplifier")));
        }
        return ledger;
    }

    /** 账本变更后整体写回持久化 NBT。 */
    private static void flush(Player player, Ledger ledger) {
        CompoundTag data = player.getPersistentData();
        if (ledger.entries.isEmpty()) {
            data.remove(KEY);
            return;
        }
        CompoundTag saved = new CompoundTag();
        ledger.entries.forEach((effect, entry) -> {
            CompoundTag record = new CompoundTag();
            if (entry.outside() != null) record.put("outside", entry.outside().copy());
            record.putLong("time", entry.time());
            record.putInt("amplifier", entry.amplifier());
            saved.put(BuiltInRegistries.MOB_EFFECT.getKey(effect).toString(), record);
        });
        data.put(KEY, saved);
    }

    // ============================================================
    //  外部效果快照老化
    // ============================================================

    /** 按经过时间老化外部效果快照；已过期返回 null。 */
    private static MobEffectInstance outside(Entry entry, long now) {
        if (entry.outside() == null) return null;
        CompoundTag aged = age(entry.outside().copy(), Math.max(0L, now - entry.time()));
        return aged == null ? null : MobEffectInstance.load(aged);
    }

    /**
     * 递归老化效果 NBT（含隐藏效果链）。返回 null 表示该效果已过期。
     * 无限时长的效果（Duration = -1）不老化。
     */
    private static CompoundTag age(CompoundTag tag, long elapsed) {
        CompoundTag hidden = tag.contains("HiddenEffect", Tag.TAG_COMPOUND)
                ? age(tag.getCompound("HiddenEffect").copy(), elapsed) : null;
        if (hidden == null) tag.remove("HiddenEffect"); else tag.put("HiddenEffect", hidden);

        int duration = tag.getInt("Duration");
        if (duration == -1) return tag;
        long remaining = duration - elapsed;
        if (remaining <= 0) return hidden;
        tag.putInt("Duration", (int) remaining);
        return tag;
    }
}
