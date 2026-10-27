package com.eternal_heart.client.tooltip;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 箴言行的渲染器：<b>逐字符</b>波浪起伏 + 彩虹配色 + 整体淡入淡出。
 *
 * <p>提示框的每一行每帧都会重新渲染，所以这里直接用 {@link Util#getMillis()} 算相位：
 * 相邻字符的相位相差 {@link #GLYPH_STEP} 弧度，整句就会出现 1~2 个波峰，像水波一样上下流动；
 * 色相同时随时间前进、按字符递增，形成沿文字流动的彩虹。</p>
 *
 * <p>浮动幅度必须小（±1.4 像素）且行高留 1 像素余量：提示框背景是按 {@link #getHeight()}
 * 开出来的，偏移过大会顶到边框或压到上一行的物品名。</p>
 */
public class ClientFloatingLineTooltip implements ClientTooltipComponent {

    /** 波浪前进一个周期的时长。 */
    private static final long PERIOD = 2600L;
    /** 上下浮动的最大像素。 */
    private static final float BOB = 1.4F;
    /** 相邻字符的相位差（弧度）：约 11 个字符一个波峰。 */
    private static final float GLYPH_STEP = 0.55F;
    /** 相邻字符的色相差：整句跨过将近一整圈色环。 */
    private static final float GLYPH_HUE = 0.045F;
    /** 彩虹饱和度：低一点避免刺眼。 */
    private static final float SATURATION = 0.85F;
    /** 最暗时的透明度（完全 0 会像渲染故障，留一点底）。 */
    private static final int MIN_ALPHA = 20;
    private static final int MAX_ALPHA = 255;

    private final String text;
    /** 按码点切好的单字（保留代理对，避免把 emoji 劈成两半）。 */
    private final List<String> glyphs;

    public ClientFloatingLineTooltip(FloatingLineTooltip source) {
        this.text = source.text().getString();
        this.glyphs = split(this.text);
    }

    @Override
    public int getHeight() {
        // 比常规行（10）高 1 像素，给上下浮动留余量
        return 11;
    }

    @Override
    public int getWidth(Font font) {
        return font.width(text);
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource buffer) {
        float phase = (Util.getMillis() % PERIOD) / (float) PERIOD;      // 0..1
        float angle = phase * (float) (Math.PI * 2);
        int alpha = (int) (MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * (0.5F + 0.5F * Mth.sin(angle)));

        float cursor = x;
        for (int i = 0; i < glyphs.size(); i++) {
            float offset = Mth.sin(angle + i * GLYPH_STEP) * BOB;
            float hue = (i * GLYPH_HUE + phase) % 1.0F;
            int color = (alpha << 24) | (Mth.hsvToRgb(hue, SATURATION, 1.0F) & 0xFFFFFF);
            // 不画阴影：阴影是纯黑不透明方块，淡出时会先于文字显形
            cursor = font.drawInBatch(glyphs.get(i), cursor, y + offset, color, false, matrix, buffer,
                    Font.DisplayMode.NORMAL, 0, 0xF000F0);
        }
    }

    private static List<String> split(String text) {
        List<String> glyphs = new ArrayList<>();
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int length = Character.charCount(codePoint);
            glyphs.add(text.substring(i, i + length));
            i += length;
        }
        return glyphs;
    }
}
