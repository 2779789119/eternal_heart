package com.eternal_heart.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 自绘按钮：圆角、悬停提亮、按压下沉、键盘聚焦光环。
 *
 * <p>不再继承原版 {@code Button} —— 原版按钮的边框贴图与间距无法与主题统一，
 * 因此点击判定、文本截断与状态插值都在这里自己做。</p>
 */
public class UiButton extends UiWidget {

    public enum Style {
        /** 青绿实心，用于「保存 / 完成」等主操作。 */
        PRIMARY,
        /** 深色描边，用于普通操作。 */
        SECONDARY,
        /** 无底色，用于次要操作。 */
        GHOST,
        /** 红调，用于删除 / 清空。 */
        DANGER,
        /** 分段控件（药丸标签），配合 {@link #setSelected}。 */
        SEGMENT,
        /** 18px 小方格按钮（+ / - / 序号）。 */
        SQUARE
    }

    private final Style style;
    private Component message;
    private Runnable action;
    private boolean selected;

    public UiButton(int x, int y, int w, int h, Component message, Style style, Runnable action) {
        super(x, y, w, h);
        this.message = message == null ? Component.empty() : message;
        this.style = style;
        this.action = action;
    }

    public static UiButton primary(int x, int y, int w, int h, Component message, Runnable action) {
        return new UiButton(x, y, w, h, message, Style.PRIMARY, action);
    }

    public static UiButton secondary(int x, int y, int w, int h, Component message, Runnable action) {
        return new UiButton(x, y, w, h, message, Style.SECONDARY, action);
    }

    public static UiButton ghost(int x, int y, int w, int h, Component message, Runnable action) {
        return new UiButton(x, y, w, h, message, Style.GHOST, action);
    }

    public static UiButton danger(int x, int y, int w, int h, Component message, Runnable action) {
        return new UiButton(x, y, w, h, message, Style.DANGER, action);
    }

    public static UiButton segment(int x, int y, int w, int h, Component message, Runnable action) {
        return new UiButton(x, y, w, h, message, Style.SEGMENT, action);
    }

    public static UiButton square(int x, int y, int size, Component message, Runnable action) {
        return new UiButton(x, y, size, size, message, Style.SQUARE, action);
    }

    public UiButton onClick(Runnable action) {
        this.action = action;
        return this;
    }

    public Component getMessage() {
        return message;
    }

    public void setMessage(Component message) {
        this.message = message == null ? Component.empty() : message;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isEnabled() || !isVisible() || button != 0 || !contains(mouseX, mouseY)) return false;
        press = 1f;
        if (action != null) action.run();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused() || !isEnabled()) return false;
        if (keyCode == 257 || keyCode == 335 || keyCode == 32) { // Enter / KP_Enter / Space
            press = 1f;
            if (action != null) action.run();
            return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        if (!isVisible()) return;
        boolean hovered = isEnabled() && (contains(mouseX, mouseY) || isFocused());
        hover = approach(hover, hovered ? 1f : 0f, 16f);
        press = approach(press, 0f, 14f);

        int drawY = y + Math.round(press * 0.8f);
        int radius = style == Style.SEGMENT || style == Style.SQUARE ? Math.min(6, h / 2) : Math.min(7, h / 2);
        Font font = Minecraft.getInstance().font;

        // 键盘聚焦：外侧一圈淡青绿
        if (isFocused() && isEnabled()) {
            UiTheme.roundRect(g, x - 2, drawY - 2, w + 4, h + 4, radius + 2, 0x4D5EEAD4);
        }

        if (!isEnabled()) {
            UiTheme.roundRect(g, x, drawY, w, h, radius, UiTheme.BORDER);
            UiTheme.roundRect(g, x + 1, drawY + 1, w - 2, h - 2, Math.max(0, radius - 1), 0xFF171E27);
            label(g, font, UiTheme.TEXT_MUTED, drawY);
            return;
        }

        switch (style) {
            case PRIMARY -> {
                int top = UiTheme.mix(UiTheme.ACCENT_DIM, 0xFF9BFAEC, hover);
                int bottom = UiTheme.mix(UiTheme.ACCENT_DEEP, UiTheme.ACCENT_DIM, 0.45f + hover * 0.55f);
                UiTheme.roundRectVGradient(g, x, drawY, w, h, radius, top, bottom);
                UiTheme.sheen(g, x + 1, drawY + 1, w - 2, radius);
                label(g, font, UiTheme.TEXT_ON_ACCENT, drawY);
            }
            case DANGER -> {
                int top = UiTheme.mix(0xFF3A1D22, 0xFF4E262C, hover);
                int bottom = UiTheme.mix(0xFF1E1214, 0xFF2C181B, hover);
                UiTheme.roundRectVGradient(g, x, drawY, w, h, radius, top, bottom);
                if (w > radius * 2) {
                    UiTheme.hLine(g, x + radius, x + w - radius, drawY + 1,
                            UiTheme.withAlpha(UiTheme.DANGER, (int) (0x30 + 0x40 * hover)));
                }
                label(g, font, UiTheme.mix(0xFFFCA5A5, 0xFFFFD4D4, hover), drawY);
            }
            case GHOST -> {
                if (hover > 0.02f) {
                    UiTheme.roundRect(g, x, drawY, w, h, radius,
                            UiTheme.withAlpha(0xFFFFFF, (int) (0x1A * hover)));
                }
                label(g, font, UiTheme.mix(UiTheme.TEXT_DIM, UiTheme.TEXT, hover), drawY);
            }
            case SEGMENT -> {
                if (selected) {
                    UiTheme.roundRectVGradient(g, x, drawY, w, h, radius,
                            UiTheme.mix(UiTheme.ACCENT, 0xFF9BFAEC, hover),
                            UiTheme.ACCENT_DEEP);
                    label(g, font, UiTheme.TEXT_ON_ACCENT, drawY);
                } else {
                    if (hover > 0.02f) {
                        UiTheme.roundRect(g, x, drawY, w, h, radius,
                                UiTheme.withAlpha(0xFFFFFF, (int) (0x16 * hover)));
                    }
                    label(g, font, UiTheme.mix(UiTheme.TEXT_DIM, UiTheme.TEXT, hover), drawY);
                }
            }
            case SQUARE -> {
                int bg = UiTheme.mix(UiTheme.CARD, UiTheme.CARD_HOVER, hover);
                int border = UiTheme.mix(UiTheme.BORDER, UiTheme.ACCENT_DIM, hover * 0.8f);
                UiTheme.roundRect(g, x, drawY, w, h, radius, border);
                UiTheme.roundRect(g, x + 1, drawY + 1, w - 2, h - 2, Math.max(0, radius - 1), bg);
                label(g, font, UiTheme.mix(UiTheme.TEXT, 0xFFFFFFFF, hover * 0.5f), drawY);
            }
            default -> {
                int bg = UiTheme.mix(UiTheme.CARD, UiTheme.CARD_HOVER, hover);
                int border = UiTheme.mix(UiTheme.BORDER, UiTheme.ACCENT_DIM, hover * 0.7f);
                UiTheme.roundRectVGradient(g, x, drawY, w, h, radius,
                        UiTheme.mix(bg, 0xFFFFFFFF, 0.05f), bg);
                // 1px 描边：沿上下边缘补线，不在圆角处叠第二层圆角（那样转角会变粗）
                if (w > radius * 2 + 2) {
                    int lineColor = UiTheme.withAlpha(border, 0xC0);
                    UiTheme.hLine(g, x + radius, x + w - radius, drawY, lineColor);
                    UiTheme.hLine(g, x + radius, x + w - radius, drawY + h - 1, lineColor);
                }
                label(g, font, UiTheme.mix(UiTheme.TEXT, 0xFFFFFFFF, hover * 0.4f), drawY);
            }
        }
    }

    private void label(GuiGraphics g, Font font, int color, int drawY) {
        String text = UiTheme.trim(font, message.getString(), w - 10);
        g.drawCenteredString(font, text, x + w / 2, drawY + (h - 8) / 2 + Math.round(press * 0.6f), color);
    }
}
