package com.eternal_heart.client.tooltip;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

/**
 * 逐字上色绘制一行提示框文字。
 *
 * 关键点：每个字符单独调用一次 Font.drawInBatch，颜色直接走绘制参数，
 * 每次调用的"段落"里只有这一个字符，样式段数最多 1~2，
 * 因此无论整份提示框有多少字，都不可能触发整合包字体库
 * （Paragraph exceeds 128 spans）那种样式段数上限崩溃。
 *
 * 行高与原版文本行一致（10 像素），排版和原版完全对齐。
 */
public class ClientRainbowLineTooltip implements ClientTooltipComponent {
    private final String text;
    private final int[] colors;
    private final boolean fancy;
    private final List<String> glyphs = new ArrayList<>();
    private final List<Style> glyphStyles;

    public ClientRainbowLineTooltip(RainbowLineTooltip source) {
        this.text = source.text();
        this.colors = source.colors();
        Style base = source.base();
        this.fancy = base.isBold() || base.isItalic() || base.isUnderlined()
            || base.isStrikethrough() || base.isObfuscated();
        this.glyphStyles = this.fancy ? new ArrayList<>() : null;
        for (int i = 0; i < this.text.length(); ) {
            int codePoint = this.text.codePointAt(i);
            int count = Character.charCount(codePoint);
            String glyph = this.text.substring(i, i + count);
            this.glyphs.add(glyph);
            if (this.fancy) {
                // 保留斜体/加粗等排版标记；颜色稍后由 drawInBatch 的颜色参数接管
                this.glyphStyles.add(base.withColor((TextColor) null));
            }
            i += count;
        }
    }

    public int getHeight() {
        return 10;
    }

    public int getWidth(Font font) {
        return font.width(this.text);
    }

    public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource buffer) {
        float cursor = (float) x;
        for (int i = 0; i < this.glyphs.size(); ++i) {
            int color = i < this.colors.length ? this.colors[i] : 0xFFFFFFFF;
            if (this.fancy) {
                FormattedCharSequence sequence = Component
                    .literal(this.glyphs.get(i))
                    .withStyle(this.glyphStyles.get(i))
                    .getVisualOrderText();
                cursor = (float) font.drawInBatch(sequence, cursor, (float) y, color, true,
                    matrix, (MultiBufferSource) buffer, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            } else {
                cursor = (float) font.drawInBatch(this.glyphs.get(i), cursor, (float) y, color, true,
                    matrix, (MultiBufferSource) buffer, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            }
        }
    }
}
