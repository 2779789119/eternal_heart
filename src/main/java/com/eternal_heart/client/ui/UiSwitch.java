package com.eternal_heart.client.ui;

import net.minecraft.client.gui.GuiGraphics;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 自绘开关（药丸轨道 + 滑动圆钮）。
 *
 * <p>状态直接读写外部数据源（配置草稿 Map / NBT），因此服务端同步后不需要额外刷新控件；
 * 圆钮位置与颜色都做指数插值，点一下会「滑」过去。</p>
 *
 * <p>轨道只有一层（早期版本用「外框 + 内缩一层」画边框，小尺寸下内外圆角半径差 1px，
 * 转角会突然变粗，看起来像多边形；现在靠抗锯齿边缘和顶部高光表现厚度），
 * 圆钮用 {@link UiTheme#disc} 画真正的圆。</p>
 */
public class UiSwitch extends UiWidget {

    private static final int TRACK_W = 40;
    private static final int TRACK_H = 20;
    private static final int KNOB = 16;

    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;
    private float knob;

    public UiSwitch(int x, int y, BooleanSupplier getter, Consumer<Boolean> setter) {
        super(x, y, TRACK_W, TRACK_H);
        this.getter = getter;
        this.setter = setter;
        this.knob = getter.getAsBoolean() ? 1f : 0f;
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isEnabled() || !isVisible() || button != 0) return false;
        if (!containsPadded(mouseX, mouseY, 2)) return false;
        setter.accept(!getter.getAsBoolean());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused() || !isEnabled()) return false;
        if (keyCode == 257 || keyCode == 335 || keyCode == 32) {
            setter.accept(!getter.getAsBoolean());
            return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        if (!isVisible()) return;
        boolean on = getter.getAsBoolean();
        boolean hovered = isEnabled() && (contains(mouseX, mouseY) || isFocused());
        hover = approach(hover, hovered ? 1f : 0f, 16f);
        knob = approach(knob, on ? 1f : 0f, 18f);

        if (isFocused() && isEnabled()) {
            UiTheme.roundRect(g, x - 2, y - 2, w + 4, h + 4, (h + 4) / 2, 0x3A5EEAD4);
        }

        int trackColor;
        if (!isEnabled()) {
            trackColor = on ? 0xFF1F4A45 : 0xFF252E39;
        } else if (on) {
            trackColor = UiTheme.mix(UiTheme.ACCENT_DEEP, UiTheme.ACCENT, 0.25f + hover * 0.45f);
        } else {
            trackColor = UiTheme.mix(0xFF2B3542, 0xFF3B4A5D, hover);
        }

        // 「开」状态外圈一层柔光，远看也能分辨
        if (on && isEnabled()) {
            UiTheme.roundRect(g, x - 1, y - 1, w + 2, h + 2, (h + 2) / 2, 0x2E5EEAD4);
        }
        UiTheme.roundRect(g, x, y, w, h, h / 2, trackColor);
        if (isEnabled()) {
            // 只在平直段画顶部高光，不碰圆角，避免转角出现亮斑
            UiTheme.hLine(g, x + h / 2, x + w - h / 2, y + 1, on ? 0x3DFFFFFF : 0x1AFFFFFF);
        }

        double knobRadius = KNOB / 2.0;
        double half = h / 2.0;
        double cx = x + half + (w - h) * knob;
        double cy = y + half;

        UiTheme.disc(g, cx, cy + 1.2, knobRadius, 0x33000000);
        int knobColor = !isEnabled() ? 0xFF4A5766 : (on ? 0xFFF4FFFC : 0xFFC9D4E0);
        UiTheme.disc(g, cx, cy, knobRadius, knobColor);
    }
}
