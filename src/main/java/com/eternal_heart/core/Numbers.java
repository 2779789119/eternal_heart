package com.eternal_heart.core;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * 数值格式化 —— 全项目统一的「配置数值 → 显示文本」实现。
 *
 * <p>配置上限已放开为 {@code Double.MAX_VALUE}（约 1.8E308）与 {@code Integer.MAX_VALUE}，
 * 传统 {@link BigDecimal#toPlainString()} 展开会输出 300 多位数字，配置面板与物品提示
 * 都会被撑爆。这里统一规则：</p>
 * <ul>
 *   <li>绝对值 ≥ 1E7 或 &lt; 1E-4 → 科学计数法（如 {@code 1.798E308}）；</li>
 *   <li>其余保持无多余小数位的普通写法（{@code 2.5}、{@code 1000000}）。</li>
 * </ul>
 *
 * <p>{@link DecimalFormat} 非线程安全，用 {@link ThreadLocal} 各线程一份实例
 * （配置面板按帧格式化上百次，不能每次新建）。</p>
 */
public final class Numbers {

    /** 科学计数法阈值：10 亿以上才用（常见大数值如 1000000 仍按普通写法显示，便于手工编辑）。 */
    private static final double SCIENTIFIC_ABOVE = 1.0E9;
    private static final double SCIENTIFIC_BELOW = 1.0E-4;

    private static final ThreadLocal<DecimalFormat> SCIENTIFIC = ThreadLocal.withInitial(
            () -> new DecimalFormat("0.###E0", DecimalFormatSymbols.getInstance(Locale.ROOT)));

    private Numbers() {
    }

    /** 把配置数值格式化为适合展示/编辑的文本。 */
    public static String format(double value) {
        if (!Double.isFinite(value)) return "0";
        if (value != 0.0 && (Math.abs(value) >= SCIENTIFIC_ABOVE || Math.abs(value) < SCIENTIFIC_BELOW)) {
            return SCIENTIFIC.get().format(value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
