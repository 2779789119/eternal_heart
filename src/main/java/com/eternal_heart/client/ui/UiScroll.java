package com.eternal_heart.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 平滑滚动状态 + 滚动条绘制与拖拽。
 *
 * <p>目标值 {@code target} 由滚轮 / 拖动直接改，实际绘制用的 {@code offset} 每帧向目标
 * 指数趋近，于是滚轮表现是「滑过去」而不是瞬跳。所有对外取整都在 {@link #offset()}。</p>
 */
public final class UiScroll {

    private static final int BAR_W = 4;
    private static final int MIN_THUMB = 18;

    private float offset;
    private float target;
    private int viewH;
    private int contentH;

    private boolean dragging;
    private float grabDelta;

    /** 每帧先算内容高度与视口高度。 */
    public void layout(int viewH, int contentH) {
        this.viewH = Math.max(0, viewH);
        this.contentH = Math.max(0, contentH);
        clamp();
    }

    private void clamp() {
        float max = max();
        if (target > max) target = max;
        if (target < 0) target = 0;
        if (offset > max) offset = max;
        if (offset < 0) offset = 0;
    }

    public float max() {
        return Math.max(0, contentH - viewH);
    }

    /** 绘制用的滚动位置（已取整）。 */
    public int offset() {
        return Math.round(offset);
    }

    public boolean isDragging() {
        return dragging;
    }

    public void jumpTo(float value) {
        offset = clampValue(value);
        target = offset;
    }

    public void scrollBy(double rows, int rowStep) {
        target = clampValue(target + (float) (-rows * rowStep));
    }

    public void scrollPages(double delta, int step) {
        target = clampValue(target + (float) (-delta * step));
    }

    private float clampValue(float value) {
        return Math.max(0, Math.min(max(), value));
    }

    /** 每帧推进动画。 */
    public void tick() {
        if (dragging) {
            offset = target;
            return;
        }
        float diff = target - offset;
        if (Math.abs(diff) < 0.6f) {
            offset = target;
            return;
        }
        float t = 1f - (float) Math.exp(-18f * UiWidget.frameDt());
        offset += diff * t;
    }

    // ===================== 滚动条 =====================

    private int thumbHeight(int trackH) {
        if (contentH <= 0) return trackH;
        long ratio = (long) trackH * trackH / Math.max(1, contentH);
        return Math.min(trackH, Math.max(MIN_THUMB, (int) ratio));
    }

    private int thumbY(int top, int trackH, int thumbH) {
        int travel = trackH - thumbH;
        float max = max();
        if (max <= 0f) return top;
        return top + Math.round(travel * (offset / max));
    }

    /** 画右侧滚动条；内容装得下时不画。 */
    public void renderBar(GuiGraphics g, int x, int top, int bottom) {
        int trackH = bottom - top;
        if (trackH <= 0 || max() <= 0f) return;
        int thumbH = thumbHeight(trackH);
        int ty = thumbY(top, trackH, thumbH);
        UiTheme.roundRect(g, x, top, BAR_W, trackH, BAR_W / 2, UiTheme.TRACK);
        UiTheme.roundRect(g, x, ty, BAR_W, thumbH, BAR_W / 2,
                dragging ? UiTheme.ACCENT : UiTheme.ACCENT_DIM);
    }

    /**
     * 点击滚动条（含轨道空白处跳转到该位置）。
     *
     * @return 是否命中并开始拖拽
     */
    public boolean mouseClicked(double mouseX, double mouseY, int barX, int top, int bottom) {
        if (max() <= 0f) return false;
        int trackH = bottom - top;
        if (trackH <= 0) return false;
        if (mouseX < barX - 5 || mouseX > barX + BAR_W + 5) return false;
        if (mouseY < top || mouseY > bottom) return false;

        int thumbH = thumbHeight(trackH);
        int ty = thumbY(top, trackH, thumbH);
        if (mouseY >= ty && mouseY <= ty + thumbH) {
            dragging = true;
            grabDelta = (float) mouseY - ty;
        } else {
            // 点轨道空白：直接跳，并把抓取点放在滑块中心
            dragging = true;
            grabDelta = thumbH / 2f;
            applyDrag(mouseY, top, bottom);
        }
        return true;
    }

    public boolean mouseDragged(double mouseY, int top, int bottom) {
        if (!dragging) return false;
        applyDrag(mouseY, top, bottom);
        return true;
    }

    private void applyDrag(double mouseY, int top, int bottom) {
        int trackH = bottom - top;
        int thumbH = thumbHeight(trackH);
        int travel = Math.max(1, trackH - thumbH);
        float ratio = (float) ((mouseY - grabDelta - top) / travel);
        ratio = Math.max(0f, Math.min(1f, ratio));
        target = ratio * max();
        offset = target;
    }

    public void mouseReleased() {
        dragging = false;
    }
}
