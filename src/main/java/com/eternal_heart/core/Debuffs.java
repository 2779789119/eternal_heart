package com.eternal_heart.core;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

import java.util.List;

/**
 * 负面效果判定 —— 「预防层」（{@code MobEffectEvent.Applicable}）与
 * 「周期清扫层」（{@code DefenseSystem}）共用的唯一判定入口。
 *
 * <p>判定优先级：<b>白名单（永不拦截）＞ 黑名单（强制拦截）＞ 原版分类
 * （BENEFICIAL 放行 / HARMFUL 拦截）＞ 中性效果的负属性修饰符（拦截）</b>。</p>
 *
 * <h3>实现要点：把「每次判定现算」换成「配置变更时编译」</h3>
 * <ul>
 *   <li><b>规则集预编译</b>：配置里的字符串 ID 列表在变更时一次性解析为
 *       {@link MobEffect} 引用集合（fastutil 开放寻址），判定退化为
 *       「2 次 O(1) 集合查询 + 1 次枚举判断」——<b>零字符串分配、零列表遍历</b>；</li>
 *   <li><b>中性效果的负属性预计算</b>：注册表里"中性但带负属性修饰符"的效果在建集时
 *       一并分类，热路径不再遍历属性修饰符；</li>
 *   <li><b>变更检测两条路</b>：配置 List 引用变化（面板保存 / 文件重载后生成新实例）
 *       与效果注册表尺寸变化都会触发重建；{@code invalidate()} 作为显式失效入口保留；</li>
 *   <li><b>可观测</b>：{@link #source(MobEffect)} 给出判定来源（命令
 *       {@code /eternalheart debuff check} 使用），排查"为什么这个效果被/没被拦"。</li>
 * </ul>
 */
public final class Debuffs {

    /** 判定来源（调试 / 命令展示）。 */
    public enum Source {
        /** 白名单保护：永不拦截。 */
        WHITELIST,
        /** 黑名单强制拦截（即使标注为有益）。 */
        BLACKLIST,
        /** 原版负面分类。 */
        HARMFUL,
        /** 原版有益分类。 */
        BENEFICIAL,
        /** 中性效果但带负属性修饰符。 */
        NEUTRAL_NEGATIVE,
        /** 中性效果且无负面属性：放行。 */
        NEUTRAL_NEUTRAL
    }

    /**
     * 编译后的规则集（不可变快照，整体替换而非原地修改）。
     *
     * @param whitelist       白名单（保护）效果集合
     * @param blacklist       黑名单（强制拦截）效果集合
     * @param neutralNegative 中性但带负属性的效果集合
     * @param registrySize    建集时的效果注册表尺寸（用于检测运行期注册变化）
     */
    private record Rules(ObjectSet<MobEffect> whitelist, ObjectSet<MobEffect> blacklist,
                         ObjectSet<MobEffect> neutralNegative, int registrySize) {
    }

    private static volatile Rules rules;

    /** 上一次编译使用的配置引用（引用比较即检测变更）。 */
    private static List<? extends String> whitelistRef;
    private static List<? extends String> blacklistRef;

    private Debuffs() {
    }

    /** 该效果是否应被拦截（不清除 / 不施加）。 */
    public static boolean blocked(MobEffect effect) {
        Rules current = current();
        if (current.whitelist().contains(effect)) return false;
        if (current.blacklist().contains(effect)) return true;
        return switch (effect.getCategory()) {
            case HARMFUL -> true;
            case BENEFICIAL -> false;
            case NEUTRAL -> current.neutralNegative().contains(effect);
        };
    }

    /** 判定来源（与 {@link #blocked} 同一套规则，供调试与命令使用）。 */
    public static Source source(MobEffect effect) {
        Rules current = current();
        if (current.whitelist().contains(effect)) return Source.WHITELIST;
        if (current.blacklist().contains(effect)) return Source.BLACKLIST;
        return switch (effect.getCategory()) {
            case HARMFUL -> Source.HARMFUL;
            case BENEFICIAL -> Source.BENEFICIAL;
            case NEUTRAL -> current.neutralNegative().contains(effect)
                    ? Source.NEUTRAL_NEGATIVE : Source.NEUTRAL_NEUTRAL;
        };
    }

    /** 强制失效（配置写入后由网络层调用；引用检测之外的兜底）。 */
    public static void invalidate() {
        rules = null;
        whitelistRef = null;
        blacklistRef = null;
    }

    // ============================================================
    //  规则集获取与编译
    // ============================================================

    private static Rules current() {
        Rules current = rules;
        List<? extends String> whitelist = ConfigValues.get(EternalHeartConfig.DEBUFF_WHITELIST);
        List<? extends String> blacklist = ConfigValues.get(EternalHeartConfig.DEBUFF_BLACKLIST);

        if (current != null && whitelist == whitelistRef && blacklist == blacklistRef
                && current.registrySize() == BuiltInRegistries.MOB_EFFECT.size()) {
            return current;
        }
        return compile(whitelist, blacklist);
    }

    /** 编译规则集（低频：配置变更 / 注册表变化 / 首次判定时）。 */
    private static synchronized Rules compile(List<? extends String> whitelist,
                                              List<? extends String> blacklist) {
        // 双重检查：并发进入时避免重复编译
        Rules current = rules;
        if (current != null && whitelist == whitelistRef && blacklist == blacklistRef
                && current.registrySize() == BuiltInRegistries.MOB_EFFECT.size()) {
            return current;
        }

        ObjectSet<MobEffect> neutralNegative = new ObjectOpenHashSet<>();
        for (MobEffect effect : BuiltInRegistries.MOB_EFFECT) {
            if (effect.getCategory() != MobEffectCategory.NEUTRAL) continue;
            if (hasNegativeModifier(effect)) neutralNegative.add(effect);
        }

        Rules compiled = new Rules(resolve(whitelist), resolve(blacklist), neutralNegative,
                BuiltInRegistries.MOB_EFFECT.size());
        whitelistRef = whitelist;
        blacklistRef = blacklist;
        rules = compiled;
        return compiled;
    }

    /** 把配置里的字符串 ID 解析成效果引用集合（未注册的 ID 静默忽略）。 */
    private static ObjectSet<MobEffect> resolve(List<? extends String> ids) {
        ObjectSet<MobEffect> set = new ObjectOpenHashSet<>(Math.max(4, ids.size()));
        for (String raw : ids) {
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id == null) continue;
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);
            if (effect != null) set.add(effect);
        }
        return set;
    }

    private static boolean hasNegativeModifier(MobEffect effect) {
        for (var modifier : effect.getAttributeModifiers().values()) {
            if (modifier.getAmount() < 0) return true;
        }
        return false;
    }
}
