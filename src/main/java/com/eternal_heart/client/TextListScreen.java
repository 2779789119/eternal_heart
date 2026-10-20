package com.eternal_heart.client;

import com.eternal_heart.client.ui.UiButton;
import com.eternal_heart.client.ui.UiEditBox;
import com.eternal_heart.client.ui.UiScreen;
import com.eternal_heart.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 通用文本列表编辑器 —— 用于「自由文本列表」类配置（例如自定义配方 JSON）。
 *
 * <p>与效果 / 属性选择器不同，这类条目没有「可选集合」，只能逐条编辑文本，因此提供：
 * 列表（可滚动 + 点击选中）、底部编辑框（实时写回选中条目）、新增 / 删除 / 清空。</p>
 *
 * <p>改动只作用于父面板的草稿，返回后由「保存并关闭」统一提交。</p>
 */
public class TextListScreen extends UiScreen {

    // ===================== 布局 =====================
    private static final int ROW_H = 22;
    private static final int ROW_STEP = 26;
    private static final int HEADER_H = 52;
    private static final int FOOTER_H = 38;

    private final Screen parent;
    private final List<String> entries;
    private final Consumer<List<String>> onChange;
    private final String hintKey;

    private UiEditBox editor;
    private int selected = -1;
    private boolean syncing;
    private int scroll, scrollMax;
    private int listX1, listX2, listTop, listBot;

    /**
     * @param parent   父界面（关闭时返回）
     * @param titleKey 标题语言键（用于区分配方 / 属性等用途）
     * @param hintKey  输入框提示语言键（配方用途在这里给出一行语法示例）
     * @param initial  初始条目
     * @param onChange 关闭时回传（父界面据此更新草稿）
     */
    public TextListScreen(Screen parent, String titleKey, String hintKey, List<String> initial,
                          Consumer<List<String>> onChange) {
        super(Component.translatable(titleKey));
        this.parent = parent;
        this.hintKey = hintKey;
        this.entries = new ArrayList<>(initial);
        this.onChange = onChange;
    }

    @Override
    protected void init() {
        layoutPanel(660, 520, 340, 280);
        listX1 = panelX + 16;
        listX2 = panelX + panelW - 16;

        int footerTop = panelY + panelH - FOOTER_H;
        int editorY = footerTop - 8 - 22;
        listTop = panelY + HEADER_H + 14;
        listBot = editorY - 10;

        editor = new UiEditBox(this.font, listX1, editorY, listX2 - listX1, 22,
                Component.translatable(hintKey));
        editor.setMaxLength(1024);
        // 实时写回选中条目：输入过程即生效，无需额外的「应用」按钮
        editor.setResponder(text -> {
            if (syncing || selected < 0 || selected >= entries.size()) return;
            entries.set(selected, text);
        });
        this.addRenderableWidget(editor);

        int y = footerTop + 8;
        this.addRenderableWidget(UiButton.primary(listX1, y, 64, 22,
                Component.translatable("config.eternal_heart.text_list.add"), this::addEntry));
        this.addRenderableWidget(UiButton.secondary(listX1 + 72, y, 84, 22,
                Component.translatable("config.eternal_heart.text_list.remove"), this::removeEntry));
        this.addRenderableWidget(UiButton.danger(listX1 + 72 + 92, y, 64, 22,
                Component.translatable("config.eternal_heart.text_list.clear"), () -> {
                    entries.clear();
                    selected = -1;
                    syncEditor();
                    clampScroll();
                }));
        this.addRenderableWidget(UiButton.secondary(listX2 - 72, y, 72, 22,
                Component.translatable("config.eternal_heart.text_list.done"), this::onClose));

        clampScroll();
    }

    // ============================================================
    //  条目操作
    // ============================================================

    /** 新增：取编辑框当前内容追加为一条并选中它。 */
    private void addEntry() {
        String text = editor.getValue() == null ? "" : editor.getValue().trim();
        if (text.isEmpty()) return;
        entries.add(text);
        selected = entries.size() - 1;
        syncEditor();
        scrollToBottom();
    }

    private void removeEntry() {
        if (selected < 0 || selected >= entries.size()) return;
        entries.remove(selected);
        if (selected >= entries.size()) selected = entries.size() - 1;
        syncEditor();
        clampScroll();
    }

    /** 把选中条目的内容写进编辑框（不触发 responder 回写，避免自激）。 */
    private void syncEditor() {
        syncing = true;
        editor.setValue(selected >= 0 && selected < entries.size() ? entries.get(selected) : "");
        syncing = false;
    }

    private void clampScroll() {
        scrollMax = Math.max(0, entries.size() * ROW_STEP - 6 - (listBot - listTop));
        if (scroll > scrollMax) scroll = scrollMax;
        if (scroll < 0) scroll = 0;
    }

    private void scrollToBottom() {
        clampScroll();
        scroll = scrollMax;
    }

    // ============================================================
    //  渲染与交互
    // ============================================================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        drawPanel(g);
        drawHeader(g, this.title,
                Component.translatable("config.eternal_heart.text_list.count", entries.size()), HEADER_H);

        // 列表区裁剪 + 逐行绘制
        g.enableScissor(listX1, listTop, listX2, listBot);
        int hovered = rowAt(mx, my);
        for (int i = 0; i < entries.size(); i++) {
            int rowY = listTop + i * ROW_STEP - scroll;
            if (rowY + ROW_H <= listTop || rowY >= listBot) continue;
            boolean active = i == selected;
            UiTheme.card(g, listX1, rowY, listX2 - listX1, ROW_H, 5, i == hovered, active);
            if (active) UiTheme.roundRect(g, listX1 + 3, rowY + 6, 3, ROW_H - 12, 1, UiTheme.ACCENT);
            g.drawString(this.font, trim(entries.get(i), listX2 - listX1 - 28), listX1 + 14, rowY + 7,
                    active ? UiTheme.ACCENT : UiTheme.TEXT);
        }
        g.disableScissor();
        drawScrollbar(g, listTop, listBot, scroll, scrollMax, Math.max(1, entries.size() * ROW_STEP));
        if (entries.isEmpty()) {
            drawEmpty(g, Component.translatable("config.eternal_heart.text_list.empty").getString(),
                    listX1, listTop, listX2, listBot);
        }

        drawFooterDivider(g, FOOTER_H);
        super.render(g, mx, my, pt);
    }

    private int rowAt(double mx, double my) {
        if (mx < listX1 || mx > listX2 || my < listTop || my > listBot) return -1;
        int index = (int) ((my - listTop + scroll) / ROW_STEP);
        return index >= 0 && index < entries.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int index = rowAt(mx, my);
        if (index >= 0) {
            selected = index;
            syncEditor();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = (int) Math.max(0, Math.min(scrollMax, scroll - delta * ROW_STEP * 2));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            // 回车 = 追加当前输入为新条目（便于连续粘贴多条配方）
            addEntry();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        // 未点「新增」就把编辑框里的内容补齐，避免输入被静默丢弃
        if (selected < 0 && editor.getValue() != null && !editor.getValue().isBlank()) {
            entries.add(editor.getValue().trim());
        }
        onChange.accept(List.copyOf(entries));
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
