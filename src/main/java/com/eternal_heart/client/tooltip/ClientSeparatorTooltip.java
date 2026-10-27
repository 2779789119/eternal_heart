package com.eternal_heart.client.tooltip;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.client.RainbowPalette;
import com.eternal_heart.config.ConfigValues;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * 分隔线的绘制：一条贯穿框宽的细线，两端渐隐。
 *
 * 颜色与边框彩虹环共用同一个时间漂移（模组的"炫彩速度"配置），
 * 色相沿线宽推进一整条彩虹——看起来就像从边框上"抽出来"的一条线。
 * 「炫彩提示框」关掉时退化为一条普通的深灰线。
 *
 * 画在 renderImage 里、和文字同一层级（原版会在文字之后统一刷图组件），
 * 位置由提示框排版决定：它排在固定头部后面，就永远贴着头部下沿。
 */
public class ClientSeparatorTooltip implements ClientTooltipComponent {
    /** 这一行占的总高度（线画在中间，上下留呼吸）。 */
    private static final int HEIGHT = 5;
    /** 和原版提示框背景同层级，文字画在它之上。 */
    private static final int BLIT_OFFSET = 400;
    /** 几个像素换一次色（肉眼看不出台阶，又省几百个矩形）。 */
    private static final int STEP = 3;
    /** 整条线走完几个彩虹周期。 */
    private static final float CYCLES = 1.0f;
    /** 不炫彩时的灰线颜色。 */
    private static final int PLAIN_GRAY = 0xFF8A8A8A;

    private final int width;

    public ClientSeparatorTooltip(SeparatorTooltip source) {
        this.width = source.width();
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public int getWidth(Font font) {
        return this.width;
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource buffer) {
        // 不在文字层画东西
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        boolean rainbow = ((Boolean)ConfigValues.get(EternalHeartConfig.RAINBOW_NAME)).booleanValue();
        float base = 0.0f;
        if (rainbow) {
            float speed = ((Double)ConfigValues.get(EternalHeartConfig.RAINBOW_NAME_SPEED)).floatValue();
            base = (float)Util.getMillis() / 1000.0f * speed;
        }
        int left = x - 1;
        int right = x + this.width + 1;
        int span = Math.max(1, right - left);
        int lineY = y + 2;
        for (int px = left; px < right; px += STEP) {
            int end = Math.min(right, px + STEP);
            float pos = (float)(px + end - 1 - left * 2) / (2.0f * (float)span);
            // 两端渐隐：透明度按正弦从 0 抬到中间最亮再落回 0
            int alpha = (int)(0xF0 * Mth.sin((float)Math.PI * Mth.clamp(pos, 0.0f, 1.0f)));
            int rgb = rainbow
                ? alpha << 24 | RainbowPalette.hueToRgb(base + pos * CYCLES)
                : alpha << 24 | (PLAIN_GRAY & 0xFFFFFF);
            graphics.fill(px, lineY, end, lineY + 1, BLIT_OFFSET, rgb);
        }
    }
}
