package com.eternal_heart.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 通用虚拟列表：只渲染视口内的行，自带平滑滚动、滚动条拖拽、逐行悬停插值。
 *
 * <p>行内容由 {@link Painter} 决定，因此同一套滚动 / 命中 / hover 逻辑能服务
 * 「效果名单」「物品选择」「饰品槽位」「已配置配方」等所有列表界面。</p>
 */
public class UiList<T> extends UiWidget {

    /** 行绘制回调；{@code hover} 是 0..1 的插值强度，可用来做渐变高亮。 */
    public interface Painter<T> {
        void paint(GuiGraphics g, T item, int index, int rowX, int rowY, int rowW, int rowH,
                   float hover, boolean selected);
    }

    /** 行点击回调（带鼠标坐标，便于区分「点勾选框」与「点整行」）。 */
    public interface RowClick {
        void onClick(int index, double mouseX, double mouseY, int button);
    }

    private final List<T> items = new ArrayList<>();
    private final UiScroll scroll = new UiScroll();
    private Painter<T> painter;
    private RowClick clickHandler;

    private int rowH = 22;
    private int rowGap = 4;
    private int selected = -1;
    private float[] hoverAnim = new float[0];
    private String emptyText = "";
    private int gutter = 12;
    private boolean scrollCaptured;

    public UiList(int x, int y, int w, int h) {
        super(x, y, w, h);
    }

    // ===================== 配置 =====================

    public UiList<T> setRowMetrics(int rowH, int rowGap) {
        this.rowH = Math.max(8, rowH);
        this.rowGap = Math.max(0, rowGap);
        return this;
    }

    public UiList<T> setPainter(Painter<T> painter) {
        this.painter = painter;
        return this;
    }

    public UiList<T> setOnClick(RowClick handler) {
        this.clickHandler = handler;
        return this;
    }

    public UiList<T> setEmptyText(String emptyText) {
        this.emptyText = emptyText == null ? "" : emptyText;
        return this;
    }

    public UiList<T> setItems(Collection<T> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        if (hoverAnim.length != items.size()) hoverAnim = new float[items.size()];
        if (selected >= items.size()) selected = items.isEmpty() ? -1 : items.size() - 1;
        ensureVisible(selected);
        return this;
    }

    public void setRowHeight(int rowH) {
        this.rowH = Math.max(8, rowH);
    }

    public List<T> getItems() {
        return items;
    }

    public int size() {
        return items.size();
    }

    public T get(int index) {
        return index >= 0 && index < items.size() ? items.get(index) : null;
    }

    public int getSelectedIndex() {
        return selected;
    }

    public T getSelected() {
        return get(selected);
    }

    public void setSelected(int index) {
        this.selected = index >= 0 && index < items.size() ? index : -1;
    }

    public void clearSelection() {
        this.selected = -1;
    }

    public void setGutter(int gutter) {
        this.gutter = Math.max(0, gutter);
    }

    public int rowStep() {
        return rowH + rowGap;
    }

    public int contentHeight() {
        return items.isEmpty() ? 0 : items.size() * rowStep() - rowGap;
    }

    public UiScroll scroll() {
        return scroll;
    }

    // ===================== 命中 =====================

    /** 鼠标下的行下标；不在列表内或落在空行处返回 -1。 */
    public int indexAt(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)) return -1;
        int local = (int) Math.round(mouseY - y) + scroll.offset();
        int index = local / rowStep();
        int within = local % rowStep();
        if (index < 0 || index >= items.size() || within > rowH) return -1;
        return index;
    }

    public int rowY(int index) {
        return y + index * rowStep() - scroll.offset();
    }

    public int rowWidth() {
        return Math.max(1, w - gutter);
    }

    /** 滚到指定行（把它放进视口中部附近）。 */
    public void ensureVisible(int index) {
        if (index < 0 || index >= items.size()) return;
        int top = index * rowStep();
        int bottom = top + rowH;
        int view = scroll.offset();
        if (top < view) scroll.jumpTo(top);
        else if (bottom > view + h) scroll.jumpTo(bottom - h);
    }

    public void scrollToTop() {
        scroll.jumpTo(0);
    }

    public void scrollToBottom() {
        scroll.jumpTo(scroll.max());
    }

    // ===================== 渲染 =====================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        if (!isVisible()) return;
        scroll.layout(h, contentHeight());
        scroll.tick();
        int off = scroll.offset();
        int step = rowStep();
        int rowW = rowWidth();

        g.enableScissor(x, y, x + w, y + h);
        int first = Math.max(0, off / step);
        int last = Math.min(items.size(), first + h / step + 2);
        for (int i = first; i < last; i++) {
            int ry = y + i * step - off;
            if (ry + rowH < y || ry > y + h) continue;
            boolean hovered = isEnabled() && mouseX >= x && mouseX < x + rowW
                    && mouseY >= ry && mouseY < ry + rowH;
            hoverAnim[i] = approach(hoverAnim[i], hovered ? 1f : 0f, 18f);
            if (painter != null) {
                painter.paint(g, items.get(i), i, x, ry, rowW, rowH, hoverAnim[i], i == selected);
            }
        }
        if (items.isEmpty() && !emptyText.isEmpty()) {
            Font font = Minecraft.getInstance().font;
            g.drawCenteredString(font, emptyText, x + w / 2, y + h / 2 - 4, UiTheme.TEXT_MUTED);
        }
        g.disableScissor();

        scroll.renderBar(g, x + w - 5, y + 2, y + h - 2);
    }

    // ===================== 事件 =====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible()) return false;
        // 滚动条优先（轨道点击也能跳）
        if (isEnabled() && scroll.mouseClicked(mouseX, mouseY, x + w - 5, y + 2, y + h - 2)) {
            scrollCaptured = true;
            return true;
        }
        if (!contains(mouseX, mouseY)) return false;
        int index = indexAt(mouseX, mouseY);
        if (index < 0) return true;
        if (isEnabled() && clickHandler != null) clickHandler.onClick(index, mouseX, mouseY, button);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean was = scrollCaptured;
        scrollCaptured = false;
        scroll.mouseReleased();
        return was;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollCaptured) return scroll.mouseDragged(mouseY, y + 2, y + h - 2);
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isVisible() || !contains(mouseX, mouseY)) return false;
        scroll.scrollBy(delta, rowStep());
        return true;
    }
}
