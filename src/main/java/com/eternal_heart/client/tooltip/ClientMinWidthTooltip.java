package com.eternal_heart.client.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

/**
 * {@link MinWidthTooltip} 的客户端侧：高度 0、不画任何像素，
 * 只把"最小框宽"报给原版的宽度统计。
 */
public class ClientMinWidthTooltip implements ClientTooltipComponent {
    private final int width;

    public ClientMinWidthTooltip(MinWidthTooltip source) {
        this.width = source.width();
    }

    @Override
    public int getHeight() {
        return 0;
    }

    @Override
    public int getWidth(Font font) {
        return this.width;
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource buffer) {
        // 什么都不画
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        // 什么都不画
    }
}
