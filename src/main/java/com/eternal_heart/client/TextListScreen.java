package com.eternal_heart.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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
public class TextListScreen extends Screen {

    // ===================== 布局 =====================
    private static final int ROW_H = 14;

    // ===================== 配色（与配置面板一致）=====================
    private static final int COL_OVERLAY = 0xB0040609;
    private static final int COL_PANEL = 0xF60E1521;
    private static final int COL_BORDER = 0xFF9A7B3F;
    private static final int COL_GOLD = 0xFFE8C860;
    private static final int COL_TEXT = 0xFFE6EAF0;
    private static final int COL_TEXT_DIM = 0xFF8A93A0;
    private static final int COL_ROW_ALT = 0x08FFFFFF;
    private static final int COL_ROW_HOVER = 0x16FFFFFF;
    private static final int COL_SELECTED = 0x30E8C860;

    // ===================== 状态 =====================
    private final Screen parent;
    private final List<String> entries;
    private final Consumer<List<String>> onChange;
    private final String titleKey;
    private final String hintKey;

    private EditBox editor;
    private int selected = -1;
    private boolean syncing;
    private int scroll, scrollMax;
    private int panelX, panelY, panelW, panelH;
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
        this.titleKey = titleKey;
        this.hintKey = hintKey;
        this.entries = new ArrayList<>(initial);
        this.onChange = onChange;
    }

    @Override
    protected void init() {
        panelW = Math.max(260, Math.min(this.width - 12, 520));
        panelH = Math.max(160, Math.min(this.height - 12, 420));
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        listX1 = panelX + 8;
        listX2 = panelX + panelW - 8;
        listTop = panelY + 26;
        listBot = panelY + panelH - 74;

        editor = new EditBox(this.font, listX1, listBot + 6, panelW - 16, 16,
                Component.translatable(hintKey));
        editor.setHint(Component.translatable(hintKey));
        editor.setMaxLength(1024);
        // 实时写回选中条目：输入过程即生效，无需额外的「应用」按钮
        editor.setResponder(text -> {
            if (syncing || selected < 0 || selected >= entries.size()) return;
            entries.set(selected, text);
        });
        this.addRenderableWidget(editor);

        int y = listBot + 26;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("config.eternal_heart.text_list.add"), b -> addEntry())
                .bounds(listX1, y, 70, 18).build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("config.eternal_heart.text_list.remove"), b -> removeEntry())
                .bounds(listX1 + 74, y, 70, 18).build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("config.eternal_heart.text_list.clear"), b -> {
                    entries.clear();
                    selected = -1;
                    syncEditor();
                    clampScroll();
                })
                .bounds(listX1 + 148, y, 70, 18).build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("config.eternal_heart.text_list.done"), b -> onClose())
                .bounds(listX2 - 70, y, 70, 18).build());

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
        scrollMax = Math.max(0, entries.size() * ROW_H - (listBot - listTop));
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, COL_PANEL);
        graphics.renderOutline(panelX, panelY, panelW, panelH, COL_BORDER);
        graphics.drawString(this.font, this.title, panelX + 8, panelY + 8, COL_GOLD, false);
        graphics.drawString(this.font,
                Component.translatable("config.eternal_heart.text_list.count", entries.size()),
                panelX + 8, listBot - 12, COL_TEXT_DIM, false);

        // 列表区裁剪 + 逐行绘制
        graphics.enableScissor(listX1, listTop, listX2, listBot);
        int hovered = rowAt(mouseX, mouseY);
        for (int i = 0; i < entries.size(); i++) {
            int rowY = listTop + i * ROW_H - scroll;
            if (rowY + ROW_H < listTop || rowY > listBot) continue;
            if (i == selected) {
                graphics.fill(listX1, rowY, listX2, rowY + ROW_H, COL_SELECTED);
            } else if (i == hovered) {
                graphics.fill(listX1, rowY, listX2, rowY + ROW_H, COL_ROW_HOVER);
            } else if ((i & 1) == 1) {
                graphics.fill(listX1, rowY, listX2, rowY + ROW_H, COL_ROW_ALT);
            }
            graphics.drawString(this.font, trim(entries.get(i)), listX1 + 4, rowY + 3,
                    i == selected ? COL_GOLD : COL_TEXT, false);
        }
        graphics.disableScissor();
        if (entries.isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("config.eternal_heart.text_list.empty"),
                    listX1 + 4, listTop + 4, COL_TEXT_DIM, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 条目过长时截断显示（编辑框里始终是完整内容）。 */
    private String trim(String text) {
        int max = (listX2 - listX1 - 8) / 6;
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 3)) + "...";
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < listX1 || mouseX > listX2 || mouseY < listTop || mouseY > listBot) return -1;
        int index = (int) ((mouseY - listTop + scroll) / ROW_H);
        return index >= 0 && index < entries.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = rowAt(mouseX, mouseY);
        if (index >= 0) {
            selected = index;
            syncEditor();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = (int) Math.max(0, Math.min(scrollMax, scroll - delta * ROW_H * 2));
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
