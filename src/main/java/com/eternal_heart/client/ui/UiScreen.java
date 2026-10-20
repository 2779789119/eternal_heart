package com.eternal_heart.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 全部自定义界面的公共外壳：统一遮罩、圆角面板、标题栏、滚动条与文本截断。
 *
 * <p>子类只需在 {@code init()} 里调用 {@link #layoutPanel} 定位面板，随后按自己的
 * 布局摆放控件；{@code render()} 里先 {@link #drawPanel} / {@link #drawHeader} 再画内容。</p>
 */
public abstract class UiScreen extends Screen {

    protected int panelX, panelY, panelW, panelH;

    protected UiScreen(Component title) {
        super(title);
    }

    /** 居中并钳制面板尺寸（窗口过小时优先保证可见）。 */
    protected void layoutPanel(int maxWidth, int maxHeight, int minWidth, int minHeight) {
        int availW = Math.max(80, this.width - 12);
        int availH = Math.max(60, this.height - 12);
        panelW = Math.min(availW, Math.max(minWidth, Math.min(maxWidth, availW)));
        panelH = Math.min(availH, Math.max(minHeight, Math.min(maxHeight, availH)));
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        UiTheme.backdrop(g, this.width, this.height);
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

        Component bold = Component.literal(title.getString()).withStyle(ChatFormatting.BOLD);
        g.drawString(this.font, bold, panelX + 16, panelY + 10, UiTheme.TEXT, false);
        if (subtitle != null) {
            g.drawString(this.font, UiTheme.trim(this.font, subtitle.getString(), panelW - 32),
                    panelX + 16, panelY + 23, UiTheme.TEXT_MUTED, false);
        }
        UiTheme.hLine(g, panelX + 2, panelX + panelW - 2, panelY + headerHeight - 1, UiTheme.BORDER);
        UiTheme.hLine(g, panelX + 16, panelX + 52, panelY + headerHeight - 1, UiTheme.ACCENT);
    }

    /** 底部操作区分隔线。 */
    protected void drawFooterDivider(GuiGraphics g, int footerHeight) {
        UiTheme.hLine(g, panelX + 2, panelX + panelW - 2,
                panelY + panelH - footerHeight, UiTheme.BORDER);
    }

    protected void drawScrollbar(GuiGraphics g, int top, int bottom, int scroll, int max, int contentHeight) {
        UiTheme.scrollbar(g, panelX + panelW - 6, top, bottom, scroll, max, contentHeight);
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
}
