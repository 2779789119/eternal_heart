package com.eternal_heart.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 自绘控件基类。
 *
 * <p>本项目<b>不继承原版 {@code AbstractWidget}</b>：所有控件由 {@link UiScreen} 自己
 * 保存、排版、派发事件与绘制，视觉与动效完全可控。基类只提供几何、可见性、悬停/
 * 按压插值这三件通用的事。</p>
 *
 * <p>动画统一用「指数趋近」：{@code cur += (target - cur) * (1 - e^(-speed*dt))}，
 * 与帧率无关；{@code dt} 由 {@link UiScreen} 每帧通过 {@link #setFrameDt} 注入。</p>
 */
public abstract class UiWidget {

    private static float frameDt = 0.016f;

    /** 由 {@link UiScreen} 每帧调用，钳制到 1ms..100ms 之间，避免卡顿后动画瞬移。 */
    public static void setFrameDt(float dt) {
        frameDt = Math.max(0.001f, Math.min(0.1f, dt));
    }

    protected static float frameDt() {
        return frameDt;
    }

    /** 指数趋近；{@code speed} 越大越快（10 ≈ 0.1 秒收敛）。 */
    protected static float approach(float current, float target, float speed) {
        if (Math.abs(target - current) < 0.001f) return target;
        float t = 1f - (float) Math.exp(-speed * frameDt);
        return current + (target - current) * t;
    }

    protected int x, y, w, h;
    private boolean visible = true;
    private boolean enabled = true;
    private boolean focused;
    /** 标记为「跟随列表行滚动」的控件：由界面决定画在裁剪区内还是区外。 */
    private boolean rowOwned;

    protected float hover;
    protected float press;

    protected UiWidget(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    // ===================== 几何 =====================

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return w;
    }

    public int getHeight() {
        return h;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setSize(int w, int h) {
        this.w = w;
        this.h = h;
    }

    public void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** 命中区外扩，用于 18px 小按钮之类不好点的控件。 */
    public boolean containsPadded(double mouseX, double mouseY, int pad) {
        return mouseX >= x - pad && mouseX < x + w + pad && mouseY >= y - pad && mouseY < y + h + pad;
    }

    // ===================== 状态 =====================

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRowOwned() {
        return rowOwned;
    }

    public void setRowOwned(boolean rowOwned) {
        this.rowOwned = rowOwned;
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
        if (focused) this.hover = Math.max(this.hover, 0.5f);
    }

    /** 是否接受键盘焦点（输入框之类）。 */
    public boolean canFocus() {
        return false;
    }

    // ===================== 生命周期 =====================

    /** 子类可在 {@code render} 里自行驱动动画；此方法留给需要按 tick 计算的控件。 */
    public void tick() {
    }

    public abstract void render(GuiGraphics g, int mouseX, int mouseY, float partial);

    // ===================== 事件（返回是否消费） =====================

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return false;
    }
}
