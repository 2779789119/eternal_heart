package com.eternal_heart.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 现代开关（药丸轨道 + 滑动圆钮），用于布尔配置项。
 *
 * <p>状态直接读写外部数据源（草稿 Map），因此配置同步后无需额外刷新控件。</p>
 */
public class UiSwitch extends Button {

    private static final int TRACK_W = 34;
    private static final int TRACK_H = 16;
    private static final int KNOB = 12;

    private final BooleanSupplier getter;
    private float knob;

    public UiSwitch(int x, int y, BooleanSupplier getter, Consumer<Boolean> setter) {
        super(x, y, TRACK_W + 4, TRACK_H + 2, Component.empty(),
                b -> setter.accept(!getter.getAsBoolean()), supplier -> supplier.get());
        this.getter = getter;
        this.knob = getter.getAsBoolean() ? 1f : 0f;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean on = getter.getAsBoolean();
        float target = on ? 1f : 0f;
        knob += (target - knob) * 0.35f;
        if (Math.abs(knob - target) < 0.01f) knob = target;

        int x = getX();
        int y = getY() + 1;
        boolean hovered = this.isHovered && this.active;
        int trackColor = on
                ? (hovered ? UiTheme.ACCENT : UiTheme.ACCENT_DIM)
                : (hovered ? 0xFF39485A : 0xFF2B3542);
        if (!this.active) trackColor = 0xFF252E39;

        UiTheme.roundRect(g, x + 2, y, TRACK_W, TRACK_H, TRACK_H / 2, trackColor);

        int travel = TRACK_W - KNOB - 4;
        int knobX = x + 2 + 2 + Math.round(travel * knob);
        int knobColor = !this.active ? 0xFF4A5766 : (on ? 0xFFF2FFFB : 0xFFB9C7D6);
        UiTheme.roundRect(g, knobX, y + 2, KNOB, KNOB, KNOB / 2, knobColor);
    }
}
