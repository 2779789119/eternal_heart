package com.eternal_heart.client.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * "固定头部 / 滚动正文"之间的分隔线占位组件。
 *
 * 它本身只是一个标记，画线的是 {@link ClientSeparatorTooltip}：
 * 一条贯穿框宽、两端渐隐的炫彩细线，颜色与边框彩虹环同一条流。
 * 因为它是提示框内容里的一行，位置天然跟着头部走——
 * 头部固定在最上面，线就永远钉在头部下面，滚轮滚的是线以下的正文。
 */
public record SeparatorTooltip(int width) implements TooltipComponent {
}
