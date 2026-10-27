package com.eternal_heart.core;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 附加被动效果表 —— 「被动药水效果」分类里由玩家自由选择的效果（支持其它模组注册的效果）。
 *
 * <p>配置以字符串列表持久化，每条形如 {@code modid:effect_id|等级|时长tick}：</p>
 * <ul>
 *   <li>等级从 <b>1</b> 开始（1 = I 级，内部换算成 amplifier = 等级 - 1）；</li>
 *   <li>时长为 tick，<b>-1 表示永久</b>（原版无限时长语义）；</li>
 *   <li>后两段可省略，默认 I 级 + 永久；无法解析 / 未注册的条目静默忽略，
 *       不让手写 toml 的一个错字影响其余条目。</li>
 * </ul>
 *
 * <p><b>为什么单独做一层缓存</b>：{@code SurvivalSystem} 每 tick 对每位佩戴者都要取这份表，
 * 若每次都拆分字符串 + 查注册表，几百条配置就是每 tick 数千次字符串操作。这里只在
 * <b>配置列表引用变化 / 效果注册表尺寸变化</b>时重新编译，热路径退化为一次引用比较。</p>
 */
public final class PassiveEffects {

    /** 等级上限（原版 amplifier 是 int，但 255 级已经远超任何可感知范围）。 */
    public static final int MAX_LEVEL = 255;
    /** 单条时长上限（tick）：1 小时。 */
    public static final int MAX_DURATION = 20 * 60 * 60;

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 一条已解析的附加被动效果。
     *
     * @param effect   目标效果
     * @param amplifier 原版 amplifier（等级 - 1）
     * @param duration 持续时长（tick）；-1 = 永久
     */
    public record Entry(MobEffect effect, int amplifier, int duration) {
    }

    private static volatile List<Entry> cache = List.of();
    private static volatile List<? extends String> cacheRef;
    private static volatile int cacheRegistrySize = -1;

    private PassiveEffects() {
    }

    /** 当前生效的附加被动效果（已解析、已去重，保持配置顺序）。 */
    public static List<Entry> all() {
        List<? extends String> raw = ConfigValues.get(EternalHeartConfig.PASSIVE_EXTRA_EFFECTS);
        int registrySize = BuiltInRegistries.MOB_EFFECT.size();
        if (raw == cacheRef && registrySize == cacheRegistrySize) return cache;
        return compile(raw, registrySize);
    }

    private static synchronized List<Entry> compile(List<? extends String> raw, int registrySize) {
        if (raw == cacheRef && registrySize == cacheRegistrySize) return cache;

        Map<MobEffect, Entry> unique = new LinkedHashMap<>();
        List<String> unknown = new ArrayList<>();
        for (String text : raw) {
            Entry entry = parse(text);
            if (entry == null) {
                unknown.add(text);
                continue;
            }
            // 同一效果重复配置：先写的优先，避免「等级高的被后面的覆盖」这种隐式规则
            unique.putIfAbsent(entry.effect(), entry);
        }
        if (!unknown.isEmpty() && LOGGER.isDebugEnabled()) {
            LOGGER.debug("[永恒之心] 附加被动效果中 {} 条无法解析（未注册或格式错误），已忽略: {}",
                    unknown.size(), unknown);
        }

        List<Entry> compiled = List.copyOf(unique.values());
        cache = compiled;
        cacheRef = raw;
        cacheRegistrySize = registrySize;
        return compiled;
    }

    /** 解析一条配置；无法识别返回 null。 */
    public static Entry parse(String text) {
        if (text == null) return null;
        String raw = text.trim();
        if (raw.isEmpty()) return null;

        String[] parts = raw.split("\\|");
        MobEffect effect = effectOf(parts[0].trim());
        if (effect == null) return null;

        int level = parts.length > 1 ? parseInt(parts[1].trim(), 1) : 1;
        if (level < 1) level = 1;
        if (level > MAX_LEVEL) level = MAX_LEVEL;

        int duration = parts.length > 2 ? parseInt(parts[2].trim(), -1) : -1;
        if (duration == 0 || duration < -1) duration = -1;
        if (duration > MAX_DURATION) duration = MAX_DURATION;

        return new Entry(effect, level - 1, duration);
    }

    /** 序列化一条配置（与 {@link #parse} 互逆，供面板写回）。 */
    public static String format(ResourceLocation id, int level, int duration) {
        int safeLevel = Math.max(1, Math.min(MAX_LEVEL, level));
        int safeDuration = duration < 0 ? -1 : Math.min(MAX_DURATION, Math.max(1, duration));
        return id + "|" + safeLevel + "|" + safeDuration;
    }

    /** 效果 ID → 效果；未注册返回 null。 */
    public static MobEffect effectOf(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) return null;
        return BuiltInRegistries.MOB_EFFECT.get(location);
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
