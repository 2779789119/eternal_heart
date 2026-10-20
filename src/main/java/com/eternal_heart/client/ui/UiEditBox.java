package com.eternal_heart.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * 主题化输入框：圆角深色底 + 聚焦青绿描边。
 *
 * <p>原版 {@link EditBox} 的边框只能用固定贴图，因此关掉原生边框（{@code bordered = false}），
 * 自己画底再让父类只画文字 / 光标。无边框模式下父类的文字是从左上角开始画的，
 * 所以这里用 pose 平移补上内边距，并把可用文本宽度同步收窄，避免右侧溢出。</p>
 */
public class UiEditBox extends EditBox {

    private static final int PAD_X = 5;

    private boolean selectAllOnFocus;

    public UiEditBox(Font font, int x, int y, int width, int height, Component hint) {
        super(font, x, y, width, height, Component.empty());
        this.setBordered(false);
        this.setTextColor(UiTheme.TEXT);
        this.setTextColorUneditable(UiTheme.TEXT_MUTED);
        if (hint != null) this.setHint(hint);
    }

    /** 首次点击获得焦点时全选内容，直接键入即可覆盖旧值（再次点击可正常定位光标）。 */
    public UiEditBox selectAllOnFocus() {
        this.selectAllOnFocus = true;
        return this;
    }

    @Override
    public int getInnerWidth() {
        return Math.max(0, super.getInnerWidth() - PAD_X * 2);
    }

    /** 父类按 {@code getX()} 计算光标位置，无边框模式下要补回左侧内边距。 */
    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX - PAD_X, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean wasFocused = this.isFocused();
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (handled && !wasFocused && selectAllOnFocus) {
            this.setCursorPosition(this.getValue().length());
            this.setHighlightPos(0);
        }
        return handled;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!this.isVisible()) return;
        UiTheme.input(g, getX(), getY(), getWidth(), getHeight(), this.isFocused());
        g.pose().pushPose();
        g.pose().translate(PAD_X, (getHeight() - 8) / 2f, 0f);
        super.renderWidget(g, mouseX, mouseY, partialTick);
        g.pose().popPose();
    }
}
