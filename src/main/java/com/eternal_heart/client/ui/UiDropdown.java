package com.eternal_heart.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;

/**
 * 自绘下拉框：闭合时只有一行（当前值 + 三角），展开后浮在其它控件之上。
 *
 * <p>选项用字符串 id 表达，显示名由 {@link IntFunction} 决定 —— 这样仪式编辑器可以
 * 依次尝试自己的语言键、Goety 的语言键，最后退回原始 id，附属模组的新类型也能显示。</p>
 *
 * <p>展开状态由 {@link UiScreen} 统一管理：有下拉框展开时，点击 / 滚轮优先给它。</p>
 */
public class UiDropdown extends UiWidget {

    private static final int ROW_H = 18;
    private static final int MAX_ROWS = 9;

    private final List<String> ids;
    /** 为 null 时直接显示原始 id。 */
    private IntFunction<String> labeler;
    private int index;
    private int scroll;
    private boolean open;
    private Runnable onChange;
    private int lastClickRow = -1;

    public UiDropdown(int x, int y, int w, int h, List<String> ids, int index) {
        super(x, y, w, h);
        this.ids = new ArrayList<>(ids == null ? List.of() : ids);
        this.index = clampIndex(index);
    }

    public UiDropdown setLabeler(IntFunction<String> labeler) {
        this.labeler = labeler;
        return this;
    }

    public UiDropdown setOnChange(Runnable onChange) {
        this.onChange = onChange;
        return this;
    }

    private int clampIndex(int value) {
        if (ids.isEmpty()) return 0;
        return Math.max(0, Math.min(ids.size() - 1, value));
    }

    public String id() {
        return ids.isEmpty() ? "" : ids.get(index);
    }

    public int index() {
        return index;
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
    }

    public List<String> options() {
        return ids;
    }

    /** 选中指定 id；不存在时动态加入并排序（附属模组的自定义值）。 */
    public void select(String id) {
        if (ids.isEmpty()) return;
        int found = ids.indexOf(id);
        if (found < 0) {
            ids.add(id == null ? "" : id);
            ids.sort(Comparator.naturalOrder());
            found = ids.indexOf(id);
        }
        index = clampIndex(found);
        setScrollAroundIndex();
    }

    public void selectIndex(int value) {
        index = clampIndex(value);
    }

    private void setScrollAroundIndex() {
        scroll = Math.max(0, Math.min(maxScroll(), index - visibleRows() / 2));
    }

    private int visibleRows() {
        return Math.min(MAX_ROWS, Math.max(1, ids.size()));
    }

    private int maxScroll() {
        return Math.max(0, ids.size() - visibleRows());
    }

    private int listTop() {
        return y + h + 3;
    }

    private int listBottom() {
        return listTop() + visibleRows() * ROW_H;
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < x || mouseX > x + w) return -1;
        int top = listTop();
        if (mouseY < top) return -1;
        int row = (int) ((mouseY - top) / ROW_H) + scroll;
        if (row < 0 || row >= ids.size()) return -1;
        return row;
    }

    // ===================== 渲染 =====================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        if (!isVisible()) return;
        Font font = Minecraft.getInstance().font;
        boolean hovered = isEnabled() && (contains(mouseX, mouseY) || open);
        hover = approach(hover, hovered ? 1f : 0f, 16f);

        UiTheme.input(g, x, y, w, h, open);
        if (hover > 0.05f && !open) {
            UiTheme.roundRect(g, x, y, w, h, Math.min(UiTheme.R_MD, h / 2),
                    UiTheme.withAlpha(0xFFFFFF, (int) (0x0C * hover)));
        }
        g.drawString(font, UiTheme.trim(font, currentLabel(), w - 26), x + 7, y + (h - 8) / 2,
                isEnabled() ? UiTheme.TEXT : UiTheme.TEXT_MUTED, false);
        UiTheme.triangle(g, x + w - 13, y + h / 2 - 1, 7, !open, UiTheme.ACCENT_DIM);
    }

    private String currentLabel() {
        if (ids.isEmpty()) return "";
        return labeler == null ? ids.get(index) : labeler.apply(index);
    }

    private String labelOf(int row) {
        return labeler == null ? ids.get(row) : labeler.apply(row);
    }

    /** 展开的候选列表，必须在所有控件之后绘制。 */
    public void renderPopup(GuiGraphics g, int mouseX, int mouseY) {
        if (!isVisible() || !open) return;
        Font font = Minecraft.getInstance().font;
        int top = listTop();
        int bottom = listBottom();

        UiTheme.shadow(g, x, top, w, bottom - top, UiTheme.R_MD, 0x50);
        UiTheme.roundRect(g, x, top, w, bottom - top, UiTheme.R_MD, UiTheme.BORDER_STRONG);
        UiTheme.roundRect(g, x + 1, top + 1, w - 2, bottom - top - 2, UiTheme.R_MD - 1, UiTheme.PANEL);

        int hovered = rowAt(mouseX, mouseY);
        int rows = visibleRows();
        for (int row = 0; row < rows; row++) {
            int option = scroll + row;
            if (option >= ids.size()) break;
            int rowY = top + row * ROW_H;
            if (option == index) {
                UiTheme.roundRect(g, x + 4, rowY + 2, w - 8, ROW_H - 4, 4, UiTheme.ACCENT_SOFT);
                UiTheme.roundRect(g, x + 4, rowY + 4, 2, ROW_H - 8, 1, UiTheme.ACCENT);
            } else if (option == hovered) {
                UiTheme.roundRect(g, x + 4, rowY + 2, w - 8, ROW_H - 4, 4, 0x18FFFFFF);
            }
            g.drawString(font, UiTheme.trim(font, labelOf(option), w - 22), x + 10, rowY + 5,
                    option == index ? UiTheme.ACCENT : UiTheme.TEXT, false);
        }
        if (maxScroll() > 0) {
            int trackH = bottom - top - 4;
            UiTheme.roundRect(g, x + w - 5, top + 2, 2, trackH, 1, UiTheme.TRACK);
            int thumbH = Math.max(10, trackH * rows / Math.max(1, ids.size()));
            int thumbY = top + 2 + (trackH - thumbH) * scroll / Math.max(1, maxScroll());
            UiTheme.roundRect(g, x + w - 5, thumbY, 2, thumbH, 1, UiTheme.ACCENT_DIM);
        }
    }

    // ===================== 事件 =====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible() || !isEnabled() || button != 0) return false;
        if (open) {
            int row = rowAt(mouseX, mouseY);
            lastClickRow = row;
            open = false;
            if (row >= 0 && row != index) {
                index = row;
                if (onChange != null) onChange.run();
            }
            return true;
        }
        if (!contains(mouseX, mouseY)) return false;
        open = true;
        setScrollAroundIndex();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!open) return false;
        scroll = (int) Math.max(0, Math.min(maxScroll(), scroll - Math.signum(delta)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) return false;
        switch (keyCode) {
            case 265 -> { // UP
                index = clampIndex(index - 1);
                setScrollAroundIndex();
                if (onChange != null) onChange.run();
                return true;
            }
            case 264 -> { // DOWN
                index = clampIndex(index + 1);
                setScrollAroundIndex();
                if (onChange != null) onChange.run();
                return true;
            }
            case 257, 335 -> { // ENTER
                open = false;
                return true;
            }
            case 256 -> { // ESC
                open = false;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** 最近一次点击命中的行（供界面区分「选中」与「只是收起」）。 */
    public int lastClickRow() {
        return lastClickRow;
    }
}
