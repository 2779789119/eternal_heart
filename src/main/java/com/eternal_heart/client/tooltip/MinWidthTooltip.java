package com.eternal_heart.client.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * 一个"只占宽度、不占高度、什么都不画"的提示框组件。
 *
 * 原版把提示框宽度算成"所有组件里最宽的那个"，所以往内容里塞一个
 * 固定宽度的占位组件，就能让框宽恒定——文字短时不会缩窄，
 * 文字长时（整合包自定义字体把字画得比测量值宽）也不会撑破边框。
 */
public record MinWidthTooltip(int width) implements TooltipComponent {
}
