package com.eternal_heart.client.ui;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 自绘单行输入框：圆角底、聚焦光环、选区、闪烁光标、水平滚动、剪贴板与中文输入。
 *
 * <p>完全替代原版 {@code EditBox}：原版的边框只能用固定贴图、文字起点写死在左上角，
 * 无法与主题统一。这里自己维护 {@code value / cursor / anchor} 三件状态，
 * 由 {@link UiScreen} 把键盘与字符事件转发进来。</p>
 *
 * <p><b>中文输入</b>：Minecraft 把输入法提交的文本拆成单个码点逐个调用
 * {@code Screen.charTyped}，因此这里只要接受 {@code c >= 32} 的字符，中文、日文、
 * 颜文字都能正常输入；全角符号与 emoji 也能落进 {@code value}。</p>
 */
public class UiTextField extends UiWidget {

    private static final int PAD = 6;
    private static final long BLINK_MS = 480L;

    private String value = "";
    private String hint = "";
    private int maxLength = 64;
    private Predicate<String> filter = text -> true;
    private Consumer<String> responder;
    private Runnable onSubmit;

    private int cursor;
    private int anchor;
    private int scroll;
    private boolean dragging;
    private boolean selectAllOnFocus;

    private int textColor = UiTheme.TEXT;
    private int hintColor = UiTheme.TEXT_MUTED;

    public UiTextField(int x, int y, int w, int h) {
        super(x, y, w, h);
    }

    public static UiTextField of(int x, int y, int w) {
        return new UiTextField(x, y, w, 20);
    }

    // ===================== 配置 =====================

    public UiTextField setHint(Component hint) {
        this.hint = hint == null ? "" : hint.getString();
        return this;
    }

    public UiTextField setHint(String hint) {
        this.hint = hint == null ? "" : hint;
        return this;
    }

    public UiTextField setMaxLength(int maxLength) {
        this.maxLength = Math.max(1, maxLength);
        return this;
    }

    public UiTextField setFilter(Predicate<String> filter) {
        this.filter = filter == null ? text -> true : filter;
        return this;
    }

    public UiTextField setResponder(Consumer<String> responder) {
        this.responder = responder;
        return this;
    }

    public UiTextField setOnSubmit(Runnable onSubmit) {
        this.onSubmit = onSubmit;
        return this;
    }

    public UiTextField selectAllOnFocus() {
        this.selectAllOnFocus = true;
        return this;
    }

    public UiTextField setTextColor(int color) {
        this.textColor = color;
        return this;
    }

    public UiTextField setHintColor(int color) {
        this.hintColor = color;
        return this;
    }

    // ===================== 值 =====================

    public String getValue() {
        return value;
    }

    /** 与旧版 EditBox 一致：过滤 + 截断 + 光标归位 + 触发 responder。 */
    public UiTextField setValue(String text) {
        if (text == null) text = "";
        if (!filter.test(text)) return this;
        this.value = text.length() > maxLength ? text.substring(0, maxLength) : text;
        this.cursor = this.value.length();
        this.anchor = this.cursor;
        ensureCursorVisible();
        notifyChanged();
        return this;
    }

    /** 静默写入（不触发 responder），用于把缓存值刷进控件。 */
    public UiTextField setText(String text) {
        if (text == null) text = "";
        if (text.length() > maxLength) text = text.substring(0, maxLength);
        this.value = text;
        this.cursor = text.length();
        this.anchor = cursor;
        ensureCursorVisible();
        return this;
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    public void clear() {
        setValue("");
    }

    private void notifyChanged() {
        if (responder != null) responder.accept(value);
    }

    // ===================== 焦点 =====================

    @Override
    public boolean canFocus() {
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        // 失焦时把选区收掉、并让上方（配置面板）有机会规整内容
        if (!focused) dragging = false;
        super.setFocused(focused);
    }

    // ===================== 渲染 =====================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        if (!isVisible()) return;
        boolean hovered = isEnabled() && contains(mouseX, mouseY);
        hover = approach(hover, hovered ? 1f : 0f, 16f);

        UiTheme.input(g, x, y, w, h, isFocused() && isEnabled());

        Font font = Minecraft.getInstance().font;
        int innerW = Math.max(1, w - PAD * 2);
        int textY = y + (h - 8) / 2;

        String shown = value;
        if (shown.isEmpty() && !hint.isEmpty()) {
            g.drawString(font, UiTheme.trim(font, hint, innerW), x + PAD, textY,
                    isEnabled() ? hintColor : UiTheme.TEXT_MUTED, false);
            if (isFocused()) drawCaret(g, x + PAD, textY);
            return;
        }

        int selStart = Math.min(cursor, anchor);
        int selEnd = Math.max(cursor, anchor);

        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        int baseX = x + PAD - scroll;

        // 选区底色
        if (selEnd > selStart) {
            int sx = baseX + font.width(value.substring(0, selStart));
            int ex = baseX + font.width(value.substring(0, selEnd));
            g.fill(Math.max(x + 1, sx), y + 2, Math.min(x + w - 1, ex), y + h - 2,
                    isFocused() ? 0x665EEAD4 : 0x33697787);
        }

        g.drawString(font, shown, baseX, textY, isEnabled() ? textColor : UiTheme.TEXT_MUTED, false);

        if (isFocused() && isEnabled()) {
            int caretX = baseX + font.width(value.substring(0, Math.min(cursor, value.length())));
            drawCaret(g, caretX, textY);
        }
        g.disableScissor();
    }

    private void drawCaret(GuiGraphics g, int caretX, int textY) {
        if ((Util.getMillis() / BLINK_MS) % 2L != 0L) return;
        g.fill(caretX, textY - 1, caretX + 1, textY + 9, UiTheme.ACCENT);
    }

    // ===================== 事件 =====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isEnabled() || !isVisible() || button != 0 || !contains(mouseX, mouseY)) return false;
        boolean wasFocused = isFocused();
        int pos = positionAt(mouseX);
        cursor = pos;
        if (!wasFocused && selectAllOnFocus) {
            anchor = 0;
            cursor = value.length();
        } else {
            anchor = pos;
        }
        dragging = true;
        ensureCursorVisible();
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!dragging || button != 0) return false;
        cursor = positionAt(mouseX);
        ensureCursorVisible();
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean was = dragging;
        dragging = false;
        return was;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused() || !isEnabled()) return false;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) deleteSelection();
                else if (ctrl) deleteWordBefore();
                else if (cursor > 0) replace(cursor - 1, cursor, "");
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) deleteSelection();
                else if (cursor < value.length()) replace(cursor, cursor + 1, "");
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                moveCursor(ctrl ? wordBoundaryLeft() : cursor - 1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                moveCursor(ctrl ? wordBoundaryRight() : cursor + 1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                moveCursor(0, shift);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                moveCursor(value.length(), shift);
                return true;
            }
            case GLFW.GLFW_KEY_A -> {
                if (ctrl) {
                    anchor = 0;
                    cursor = value.length();
                    ensureCursorVisible();
                    return true;
                }
                return false;
            }
            case GLFW.GLFW_KEY_C -> {
                if (ctrl && hasSelection()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(selectedText());
                    return true;
                }
                return false;
            }
            case GLFW.GLFW_KEY_X -> {
                if (ctrl && hasSelection()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(selectedText());
                    deleteSelection();
                    return true;
                }
                return false;
            }
            case GLFW.GLFW_KEY_V -> {
                if (ctrl) {
                    insert(Minecraft.getInstance().keyboardHandler.getClipboard());
                    return true;
                }
                return false;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (onSubmit != null) {
                    onSubmit.run();
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!isFocused() || !isEnabled()) return false;
        // 控制字符（含退格 / 回车）由 keyPressed 处理；这里只收可见字符与 CJK
        if (codePoint < 32 || codePoint == 127) return false;
        insert(String.valueOf(codePoint));
        return true;
    }

    // ===================== 文本编辑 =====================

    public void insert(String text) {
        if (text == null || text.isEmpty()) return;
        StringBuilder sb = new StringBuilder(text);
        for (int i = sb.length() - 1; i >= 0; i--) {
            if (sb.charAt(i) < 32) sb.deleteCharAt(i);
        }
        String cleaned = sb.toString();
        if (cleaned.isEmpty()) return;
        int start = Math.min(cursor, anchor);
        int end = Math.max(cursor, anchor);
        replace(start, end, cleaned);
    }

    private void replace(int start, int end, String insert) {
        String merged = value.substring(0, start) + insert + value.substring(end);
        if (merged.length() > maxLength) {
            merged = merged.substring(0, maxLength);
        }
        if (!filter.test(merged)) return;
        value = merged;
        cursor = Math.min(maxLength, start + insert.length());
        anchor = cursor;
        ensureCursorVisible();
        notifyChanged();
    }

    private void deleteSelection() {
        int start = Math.min(cursor, anchor);
        int end = Math.max(cursor, anchor);
        replace(start, end, "");
    }

    private void deleteWordBefore() {
        int target = wordBoundaryLeft();
        if (target < cursor) replace(target, cursor, "");
    }

    private int wordBoundaryLeft() {
        int i = cursor;
        while (i > 0 && Character.isWhitespace(value.charAt(i - 1))) i--;
        while (i > 0 && !Character.isWhitespace(value.charAt(i - 1))) i--;
        return i;
    }

    private int wordBoundaryRight() {
        int i = cursor;
        while (i < value.length() && !Character.isWhitespace(value.charAt(i))) i++;
        while (i < value.length() && Character.isWhitespace(value.charAt(i))) i++;
        return i;
    }

    private void moveCursor(int pos, boolean extend) {
        cursor = Math.max(0, Math.min(value.length(), pos));
        if (!extend) anchor = cursor;
        ensureCursorVisible();
    }

    private boolean hasSelection() {
        return cursor != anchor;
    }

    private String selectedText() {
        int start = Math.min(cursor, anchor);
        int end = Math.max(cursor, anchor);
        return value.substring(start, end);
    }

    // ===================== 几何 =====================

    /** 鼠标横坐标 → 最近的字符边界。 */
    private int positionAt(double mouseX) {
        Font font = Minecraft.getInstance().font;
        int rel = (int) Math.round(mouseX - (x + PAD)) + scroll;
        int pos = 0;
        while (pos < value.length()) {
            int wHere = font.width(value.substring(0, pos + 1));
            int wPrev = font.width(value.substring(0, pos));
            if (rel < (wHere + wPrev) / 2) break;
            pos++;
        }
        return pos;
    }

    private void ensureCursorVisible() {
        Font font = Minecraft.getInstance().font;
        int innerW = Math.max(1, w - PAD * 2);
        int caret = font.width(value.substring(0, Math.min(cursor, value.length())));
        int maxScroll = Math.max(0, font.width(value) - innerW);
        if (caret - scroll > innerW) scroll = caret - innerW;
        if (caret - scroll < 0) scroll = caret;
        scroll = Math.max(0, Math.min(maxScroll, scroll));
    }
}
