package com.eternal_heart.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 主题化按钮：圆角、悬停渐变、禁用态降饱和。
 *
 * <p>继承 {@link Button} 以复用点击 / 键盘导航 / 提示框逻辑，只接管外观绘制。</p>
 */
public class UiButton extends Button {

    public enum Style {
        /** 青绿实心，用于「保存 / 完成」等主操作。 */
        PRIMARY,
        /** 深色描边，用于普通操作。 */
        SECONDARY,
        /** 无底色，用于次要操作。 */
        GHOST,
        /** 红调，用于删除 / 清空。 */
        DANGER,
        /** 分段控件（药丸标签），配合 {@link #setSelected(boolean)}。 */
        SEGMENT
    }

    private final Style style;
    private float hover;
    private boolean selected;

    public UiButton(int x, int y, int w, int h, Component message, OnPress onPress, Style style) {
        super(x, y, w, h, message, onPress, supplier -> supplier.get());
        this.style = style;
    }

    public static UiButton primary(int x, int y, int w, int h, Component message, Runnable action) {
        return new UiButton(x, y, w, h, message, b -> action.run(), Style.PRIMARY);
    }

    public static UiButton secondary(int x, int y, int w, int h, Component message, Runnable action) {
        return new UiButton(x, y, w, h, message, b -> action.run(), Style.SECONDARY);
    }

    public static UiButton ghost(int x, int y, int w, int h, Component message, Runnable action) {
        return new UiButton(x, y, w, h, message, b -> action.run(), Style.GHOST);
    }

    public static UiButton danger(int x, int y, int w, int h, Component message, Runnable action) {
        return new UiButton(x, y, w, h, message, b -> action.run(), Style.DANGER);
    }

    public static UiButton segment(int x, int y, int w, int h, Component message, Runnable action) {
        return new UiButton(x, y, w, h, message, b -> action.run(), Style.SEGMENT);
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isSelected() {
        return selected;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float target = (this.isHovered || this.isFocused()) && this.active ? 1f : 0f;
        hover += (target - hover) * 0.28f;
        if (hover < 0.01f) hover = target;

        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int radius = Math.min(6, h / 2);

        // 键盘聚焦光环
        if (this.isFocused() && this.active) {
            UiTheme.roundRect(g, x - 1, y - 1, w + 2, h + 2, radius + 1, 0x805EEAD4);
        }

        if (!this.active) {
            UiTheme.roundRect(g, x, y, w, h, radius, UiTheme.BORDER);
            UiTheme.roundRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), 0xFF171F28);
            drawLabel(g, x, y, w, h, UiTheme.TEXT_MUTED);
            return;
        }

        switch (style) {
            case PRIMARY -> {
                int body = UiTheme.mix(UiTheme.ACCENT_DIM, 0xFF8FF5E4, hover);
                UiTheme.roundRect(g, x, y, w, h, radius, body);
                UiTheme.hLine(g, x + radius, x + w - radius, y + 1, 0x40FFFFFF);
                drawLabel(g, x, y, w, h, 0xFF07161A);
            }
            case DANGER -> {
                int body = UiTheme.mix(0xFF2A1719, 0xFF3B1E22, hover);
                int border = UiTheme.mix(0xFF5A2C30, UiTheme.DANGER, hover);
                UiTheme.roundRect(g, x, y, w, h, radius, border);
                UiTheme.roundRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), body);
                drawLabel(g, x, y, w, h, 0xFFFCA5A5);
            }
            case GHOST -> {
                if (hover > 0.02f) {
                    UiTheme.roundRect(g, x, y, w, h, radius, ((int) (0x18 * hover) << 24) | 0xFFFFFF);
                }
                drawLabel(g, x, y, w, h, UiTheme.mix(UiTheme.TEXT_DIM, UiTheme.TEXT, hover));
            }
            case SEGMENT -> {
                if (selected) {
                    UiTheme.roundRect(g, x, y, w, h, radius, UiTheme.ACCENT_DIM);
                    UiTheme.roundRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), UiTheme.ACCENT_SOFT);
                } else if (hover > 0.02f) {
                    UiTheme.roundRect(g, x, y, w, h, radius, ((int) (0x18 * hover) << 24) | 0xFFFFFF);
                }
                drawLabel(g, x, y, w, h, selected ? UiTheme.ACCENT
                        : UiTheme.mix(UiTheme.TEXT_DIM, UiTheme.TEXT, hover));
            }
            default -> {
                int body = UiTheme.mix(UiTheme.CARD, UiTheme.CARD_HOVER, hover);
                int border = UiTheme.mix(UiTheme.BORDER, UiTheme.ACCENT_DIM, hover * 0.65f);
                UiTheme.roundRect(g, x, y, w, h, radius, border);
                UiTheme.roundRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), body);
                drawLabel(g, x, y, w, h, UiTheme.mix(UiTheme.TEXT, 0xFFFFFFFF, hover * 0.4f));
            }
        }
    }

    private void drawLabel(GuiGraphics g, int x, int y, int w, int h, int color) {
        var font = Minecraft.getInstance().font;
        g.drawCenteredString(font, UiTheme.trim(font, getMessage().getString(), w - 8),
                x + w / 2, y + (h - 8) / 2, color);
    }
}
