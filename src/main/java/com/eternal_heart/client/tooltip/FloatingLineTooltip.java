package com.eternal_heart.client.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * 「无论凡人或不朽，一切都承认你的神性」这一行的数据载体。
 *
 * <p>仅仅带一句话：真正的位置偏移与透明度由客户端渲染器 {@link ClientFloatingLineTooltip}
 * 按时间实时计算，所以本对象无需同步、无需序列化。</p>
 */
public record FloatingLineTooltip(Component text) implements TooltipComponent {
}
