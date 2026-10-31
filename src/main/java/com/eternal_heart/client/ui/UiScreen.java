package com.eternal_heart.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 全部自定义界面的公共外壳（v2）：统一遮罩、圆角面板、标题栏、控件容器与事件派发。
 *
 * <p>界面不再往 {@code Screen.renderables} 里塞原版控件 —— 控件由这里的
 * {@link #widgets} 自己保存、排版、派发事件。子类只需要：</p>
 * <ol>
 *   <li>{@code init()} 里 {@link #clearWidgets()}、{@link #layoutPanel}、{@link #add} 控件；</li>
 *   <li>实现 {@link #renderContent}，在里面画列表 / 网格并调用 {@link #renderWidgets}；</li>
 *   <li>需要自定义命中区时覆写 {@link #onMouseClicked} / {@link #onMouseScrolled}。</li>
 * </ol>
 */
public abstract class UiScreen extends Screen {

    // ===================== 面板几何 =====================
    protected int panelX, panelY, panelW, panelH;
    protected int headerH = 52;
    protected int footerH = 38;

    // ===================== 控件 =====================
    protected final List<UiWidget> widgets = new ArrayList<>();
    private final List<Component> tooltipLines = new ArrayList<>();

    private UiWidget focusedWidget;
    private UiWidget dragWidget;
    private long lastFrameNanos;
    private float frameDt = 0.016f;
    protected int mouseX, mouseY;

    protected UiScreen(Component title) {
        super(title);
    }

    // ============================================================
    //  布局
    // ============================================================

    /** 居中并钳制面板尺寸（窗口过小时优先保证可见）。 */
    protected void layoutPanel(int maxWidth, int maxHeight, int minWidth, int minHeight) {
        int availW = Math.max(80, this.width - 12);
        int availH = Math.max(60, this.height - 12);
        panelW = Math.min(availW, Math.max(minWidth, Math.min(maxWidth, availW)));
        panelH = Math.min(availH, Math.max(minHeight, Math.min(maxHeight, availH)));
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
    }

    protected int contentLeft() {
        return panelX + 16;
    }

    protected int contentRight() {
        return panelX + panelW - 16;
    }

    protected int contentTop() {
        return panelY + headerH;
    }

    protected int contentBottom() {
        return panelY + panelH - footerH;
    }

    protected int footerTop() {
        return panelY + panelH - footerH;
    }

    /** 上一帧耗时（秒，已钳制），供界面自己做插值动画。 */
    protected float frameDelta() {
        return frameDt;
    }

    // ============================================================
    //  控件容器
    // ============================================================

    protected <T extends UiWidget> T add(T widget) {
        widgets.add(widget);
        return widget;
    }

    protected void removeWidget(UiWidget widget) {
        widgets.remove(widget);
        if (focusedWidget == widget) focusedWidget = null;
        if (dragWidget == widget) dragWidget = null;
    }

    /** 重新构建控件前调用（窗口缩放时 Minecraft 会重跑 init）。 */
    @Override
    protected void clearWidgets() {
        super.clearWidgets();
        widgets.clear();
        focusedWidget = null;
        dragWidget = null;
    }

    protected List<UiWidget> widgets() {
        return widgets;
    }

    /** 按添加顺序绘制全部可见控件（下拉框候选列表不在这里画）。 */
    protected void renderWidgets(GuiGraphics g, int mouseX, int mouseY, float partial) {
        for (UiWidget widget : widgets) {
            if (widget.isVisible()) widget.render(g, mouseX, mouseY, partial);
        }
    }

    /** 下拉框候选列表：必须画在所有内容之上。 */
    protected void renderPopups(GuiGraphics g, int mouseX, int mouseY) {
        for (UiWidget widget : widgets) {
            if (widget instanceof UiDropdown dropdown) dropdown.renderPopup(g, mouseX, mouseY);
        }
    }

    // ============================================================
    //  焦点
    // ============================================================

    protected UiWidget getFocusedWidget() {
        return focusedWidget;
    }

    protected void focusWidget(UiWidget widget) {
        if (focusedWidget == widget) return;
        UiWidget previous = focusedWidget;
        if (previous != null) previous.setFocused(false);
        focusedWidget = widget;
        if (widget != null) widget.setFocused(true);
        onFocusChanged(previous, widget);
    }

    /** 焦点变化回调（配置面板据此把「半截数字」规整回标准格式）。 */
    protected void onFocusChanged(UiWidget previous, UiWidget current) {
    }

    private void cycleFocus(boolean backwards) {
        List<UiWidget> focusable = new ArrayList<>();
        for (UiWidget widget : widgets) {
            if (widget.isVisible() && widget.isEnabled() && widget.canFocus()) focusable.add(widget);
        }
        if (focusable.isEmpty()) return;
        int current = focusable.indexOf(focusedWidget);
        int next = current < 0 ? (backwards ? focusable.size() - 1 : 0)
                : Math.floorMod(current + (backwards ? -1 : 1), focusable.size());
        focusWidget(focusable.get(next));
    }

    private boolean anyDropdownOpen() {
        for (UiWidget widget : widgets) {
            if (widget instanceof UiDropdown dropdown && dropdown.isOpen()) return true;
        }
        return false;
    }

    private void closeDropdowns() {
        for (UiWidget widget : widgets) {
            if (widget instanceof UiDropdown dropdown) dropdown.close();
        }
    }

    // ============================================================
    //  渲染
    // ============================================================

    @Override
    public final void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        long now = System.nanoTime();
        if (lastFrameNanos != 0L) {
            frameDt = (now - lastFrameNanos) / 1_000_000_000f;
        }
        lastFrameNanos = now;
        UiWidget.setFrameDt(frameDt);

        tooltipLines.clear();
        renderBackground(g);
        drawPanel(g);
        renderContent(g, mouseX, mouseY, partial);
        renderPopups(g, mouseX, mouseY);
        if (!tooltipLines.isEmpty() && !anyDropdownOpen()) {
            g.renderComponentTooltip(this.font, tooltipLines, mouseX, mouseY);
        }
    }

    /** 子类在这里画自己的内容；控件通过 {@link #renderWidgets} 绘制。 */
    protected abstract void renderContent(GuiGraphics g, int mouseX, int mouseY, float partial);

    @Override
    public void renderBackground(GuiGraphics g) {
        UiTheme.backdrop(g, this.width, this.height);
        UiTheme.ambientGlow(g, panelX + panelW / 2, panelY + panelH / 3, Math.max(panelW, 320));
    }

    /** 面板投影、圆角与边框。 */
    protected void drawPanel(GuiGraphics g) {
        UiTheme.panel(g, panelX, panelY, panelW, panelH);
    }

    /**
     * 标题栏：顶部青绿渐隐装饰线 + 加粗标题 + 次要说明 + 底部分隔线。
     *
     * @param headerHeight 标题栏高度，内容区从这里开始
     */
    protected void drawHeader(GuiGraphics g, Component title, Component subtitle, int headerHeight) {
        int mid = panelX + panelW / 2;
        UiTheme.hGradient(g, panelX + 18, mid, panelY + 1, 0x005EEAD4, 0xA05EEAD4);
        UiTheme.hGradient(g, mid, panelX + panelW - 18, panelY + 1, 0xA05EEAD4, 0x005EEAD4);

        // 标题前的强调方块
        UiTheme.roundRect(g, panelX + 16, panelY + 12, 3, 12, 1, UiTheme.ACCENT);
        Component bold = Component.literal(title.getString()).withStyle(ChatFormatting.BOLD);
        g.drawString(this.font, bold, panelX + 25, panelY + 12, UiTheme.TEXT, false);
        if (subtitle != null) {
            g.drawString(this.font, UiTheme.trim(this.font, subtitle.getString(), panelW - 42),
                    panelX + 25, panelY + 26, UiTheme.TEXT_MUTED, false);
        }
        UiTheme.divider(g, panelX + 2, panelX + panelW - 2, panelY + headerHeight - 1, UiTheme.BORDER);
        UiTheme.hLine(g, panelX + 16, panelX + 56, panelY + headerHeight - 1, UiTheme.ACCENT);
    }

    /** 底部操作区分隔线。 */
    protected void drawFooterDivider(GuiGraphics g, int footerHeight) {
        int y = panelY + panelH - footerHeight;
        UiTheme.divider(g, panelX + 2, panelX + panelW - 2, y, UiTheme.BORDER);
    }

    /** 左下角状态文字。 */
    protected void drawStatus(GuiGraphics g, String text, int color, int maxWidth) {
        if (text == null || text.isBlank()) return;
        g.drawString(this.font, UiTheme.trim(this.font, text, maxWidth),
                panelX + 16, panelY + panelH - footerH + 15, color, false);
    }

    /** 空列表提示（居中、次要色）。 */
    protected void drawEmpty(GuiGraphics g, String text, int x1, int y1, int x2, int y2) {
        g.drawCenteredString(this.font, text, (x1 + x2) / 2, (y1 + y2) / 2 - 4, UiTheme.TEXT_MUTED);
    }

    protected String trim(String text, int width) {
        return UiTheme.trim(this.font, text, width);
    }

    protected Font font() {
        return this.font;
    }

    // ============================================================
    //  悬浮提示
    // ============================================================

    protected void tooltip(Component line) {
        tooltipLines.clear();
        if (line != null) tooltipLines.add(line);
    }

    protected void tooltip(Component... lines) {
        tooltipLines.clear();
        for (Component line : lines) if (line != null) tooltipLines.add(line);
    }

    protected void tooltip(List<Component> lines) {
        tooltipLines.clear();
        if (lines != null) tooltipLines.addAll(lines);
    }

    // ============================================================
    //  事件派发
    // ============================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 1) 展开的下拉框独占点击（点任意位置只负责选择 / 收起）
        for (UiWidget widget : widgets) {
            if (widget instanceof UiDropdown dropdown && dropdown.isOpen()) {
                dropdown.mouseClicked(mouseX, mouseY, button);
                return true;
            }
        }
        // 2) 控件（后添加的在上层）
        for (int i = widgets.size() - 1; i >= 0; i--) {
            UiWidget widget = widgets.get(i);
            if (!widget.isVisible() || !widget.isEnabled()) continue;
            // 跟随列表行滚动的控件可能有一部分被裁剪区切掉，裁剪区外的点击不派发
            if (widget.isRowOwned() && isOutsideRowClip(mouseX, mouseY)) continue;
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                focusWidget(widget.canFocus() ? widget : null);
                dragWidget = widget;
                return true;
            }
        }
        // 3) 空白区域：取消焦点，再交给界面
        focusWidget(null);
        return onMouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = false;
        if (dragWidget != null) {
            handled = dragWidget.mouseReleased(mouseX, mouseY, button);
            dragWidget = null;
        }
        return handled || onMouseReleased(mouseX, mouseY, button) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragWidget != null && dragWidget.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return onMouseDragged(mouseX, mouseY, button, dragX, dragY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        for (UiWidget widget : widgets) {
            if (widget instanceof UiDropdown dropdown && dropdown.isOpen()) {
                return dropdown.mouseScrolled(mouseX, mouseY, delta) || true;
            }
        }
        for (int i = widgets.size() - 1; i >= 0; i--) {
            UiWidget widget = widgets.get(i);
            if (!widget.isVisible() || !widget.isEnabled()) continue;
            if (widget.mouseScrolled(mouseX, mouseY, delta)) return true;
        }
        return onMouseScrolled(mouseX, mouseY, delta) || super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (anyDropdownOpen()) {
                closeDropdowns();
                return true;
            }
            if (focusedWidget != null) {
                focusWidget(null);
                return true;
            }
            if (onEscape()) return true;
            onClose();
            return true;
        }
        if (focusedWidget != null && focusedWidget.isVisible()
                && focusedWidget.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            cycleFocus((modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
            return true;
        }
        return onKeyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (focusedWidget != null && focusedWidget.isVisible()
                && focusedWidget.charTyped(codePoint, modifiers)) {
            return true;
        }
        return onCharTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        for (UiWidget widget : widgets) widget.tick();
    }

    // ============================================================
    //  子类钩子
    // ============================================================

    /**
     * 行控件（{@link UiWidget#isRowOwned()}）的裁剪区判定。
     *
     * <p>默认不裁剪；带滚动视口的界面（配置面板）覆写它，避免「视觉上被裁掉的控件」
     * 仍然能点到。</p>
     */
    protected boolean isOutsideRowClip(double mouseX, double mouseY) {
        return false;
    }

    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    protected boolean onMouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    protected boolean onMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    protected boolean onMouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    protected boolean onCharTyped(char codePoint, int modifiers) {
        return false;
    }

    /** ESC 在「无下拉框、无焦点」时的额外处理；返回 false 则关闭界面。 */
    protected boolean onEscape() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
