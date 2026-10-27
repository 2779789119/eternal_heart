package com.eternal_heart.client.tooltip;

import net.minecraft.network.chat.Style;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * 一条"已经折好行"的提示框文字。
 *
 * text 是纯字符串，colors 是渲染前就算好的逐字颜色（每个字符一个，含透明度），
 * base 只保留斜体/加粗这类排版标记（颜色由炫彩接管）。
 *
 * 绘制时由 ClientRainbowLineTooltip 逐字调用 Font.drawInBatch 直接上色，
 * 完全不经过富文本"样式段"体系——每一次绘制传下去的段落里只有 1 种样式。
 * 因此文字再多也不可能触发整合包字体库的样式段数上限崩溃
 * （旧版每字符一个样式段，一次展开上千段，直接超出 128 段限制）。
 * 同时逐字变色是真正的平滑渐变，和最早版本的观感一致。
 */
public record RainbowLineTooltip(String text, int[] colors, Style base) implements TooltipComponent {
}
