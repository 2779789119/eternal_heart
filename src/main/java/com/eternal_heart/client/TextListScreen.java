package com.eternal_heart.client;

import com.eternal_heart.client.ui.UiButton;
import com.eternal_heart.client.ui.UiList;
import com.eternal_heart.client.ui.UiScreen;
import com.eternal_heart.client.ui.UiTextField;
import com.eternal_heart.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 通用文本列表编辑器 —— 用于「自由文本列表」类配置（例如自定义配方 JSON）。
 *
 * <p>列表可滚动、点击选中；底部输入框实时写回选中条目；新增 / 删除 / 清空走 footer。
 * 改动只作用于父面板的草稿，返回后由「保存并关闭」统一提交。</p>
 */
public class TextListScreen extends UiScreen {

    private static final int HEADER = 52;
    private static final int FOOTER = 38;
    private static final int ROW_H = 24;

    private final Screen parent;
    private final List<String> entries;
    private final Consumer<List<String>> onChange;
    private final String hintKey;

    private UiTextField editor;
    private UiList<String> list;
    private int selected = -1;
    private boolean syncing;
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
        clearWidgets();
        headerH = HEADER;
        footerH = FOOTER;
        layoutPanel(660, 520, 360, 300);

        listX1 = contentLeft();
        listX2 = contentRight();
        listTop = panelY + HEADER + 10;
        int editorY = footerTop() - 8 - 22;
        listBot = editorY - 10;

        list = add(new UiList<String>(listX1, listTop, listX2 - listX1, listBot - listTop))
                .setRowMetrics(ROW_H, 4)
                .setEmptyText(tr("empty"))
                .setPainter(this::paintRow)
                .setOnClick(this::clickRow);

        editor = add(new UiTextField(listX1, editorY, listX2 - listX1, 22)
                .setHint(Component.translatable(hintKey))
                .setMaxLength(1024)
                .setOnSubmit(this::addEntry)
                // 实时写回选中条目：输入过程即生效，无需额外的「应用」按钮
                .setResponder(text -> {
                    if (syncing || selected < 0 || selected >= entries.size()) return;
                    entries.set(selected, text);
                    list.setItems(entries);
                }));

        int y = footerTop() + 8;
        add(UiButton.primary(listX1, y, 64, 22,
                Component.translatable("config.eternal_heart.text_list.add"), this::addEntry));
        add(UiButton.secondary(listX1 + 72, y, 84, 22,
                Component.translatable("config.eternal_heart.text_list.remove"), this::removeEntry));
        add(UiButton.danger(listX1 + 72 + 92, y, 64, 22,
                Component.translatable("config.eternal_heart.text_list.clear"), this::clearAll));
        add(UiButton.secondary(listX2 - 72, y, 72, 22,
                Component.translatable("config.eternal_heart.text_list.done"), this::onClose));

        refreshList();
    }

    private void refreshList() {
        list.setItems(entries);
        list.setSelected(selected);
    }

    private void paintRow(GuiGraphics g, String text, int index, int rowX, int rowY, int rowW, int rowH,
                          float hover, boolean active) {
        UiTheme.cardFade(g, rowX, rowY, rowW, rowH, UiTheme.R_SM, hover, active);
        if (active) {
            UiTheme.roundRect(g, rowX + 3, rowY + 6, 3, rowH - 12, 1, UiTheme.ACCENT);
        }
        // 行号：小胶囊
        String number = String.valueOf(index + 1);
        int numberW = this.font.width(number) + 10;
        UiTheme.chip(g, rowX + 12, rowY + (rowH - 14) / 2, numberW, 14,
                UiTheme.withAlpha(0xFFFFFF, 0x0E), UiTheme.withAlpha(0xFFFFFF, 0x10));
        g.drawString(this.font, number, rowX + 17, rowY + (rowH - 8) / 2,
                active ? UiTheme.ACCENT : UiTheme.TEXT_MUTED, false);

        g.drawString(this.font, trim(text, rowW - numberW - 34),
                rowX + 12 + numberW + 8, rowY + (rowH - 8) / 2,
                active ? UiTheme.ACCENT : UiTheme.TEXT, false);
    }

    private void clickRow(int index, double mouseX, double mouseY, int button) {
        selected = index;
        list.setSelected(index);
        syncEditor();
    }

    // ============================================================
    //  条目操作
    // ============================================================

    /** 新增：取编辑框当前内容追加为一条并选中它。 */
    private void addEntry() {
        String text = editor == null ? "" : editor.getValue().trim();
        if (text.isEmpty()) return;
        entries.add(text);
        selected = entries.size() - 1;
        syncEditor();
        refreshList();
        list.scrollToBottom();
    }

    private void removeEntry() {
        if (selected < 0 || selected >= entries.size()) return;
        entries.remove(selected);
        if (selected >= entries.size()) selected = entries.size() - 1;
        syncEditor();
        refreshList();
    }

    private void clearAll() {
        entries.clear();
        selected = -1;
        syncEditor();
        refreshList();
    }

    /** 把选中条目的内容写进编辑框（不触发 responder 回写，避免自激）。 */
    private void syncEditor() {
        if (editor == null) return;
        syncing = true;
        editor.setText(selected >= 0 && selected < entries.size() ? entries.get(selected) : "");
        syncing = false;
    }

    // ============================================================
    //  渲染
    // ============================================================

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partial) {
        drawHeader(g, this.title,
                Component.translatable("config.eternal_heart.text_list.count", entries.size()), HEADER);
        renderWidgets(g, mouseX, mouseY, partial);
        drawFooterDivider(g, FOOTER);
        if (editor != null) {
            g.drawString(this.font, tr("editor_hint"), listX1, editor.getY() - 12,
                    UiTheme.TEXT_MUTED, false);
        }
    }

    private String tr(String key, Object... args) {
        return Component.translatable("config.eternal_heart.text_list." + key, args).getString();
    }

    @Override
    public void onClose() {
        // 未点「新增」就把编辑框里的内容补齐，避免输入被静默丢弃
        if (editor != null && selected < 0 && !editor.getValue().isBlank()) {
            entries.add(editor.getValue().trim());
        }
        onChange.accept(List.copyOf(entries));
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }
}
