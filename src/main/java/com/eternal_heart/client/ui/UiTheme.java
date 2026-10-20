package com.eternal_heart.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 统一视觉主题 —— 深色石板底 + 青绿点缀。
 *
 * <p>全部界面（配置面板 / 名单选择器 / 配方与仪式编辑器 …）共用同一套色板与绘制原语，
 * 避免各屏各写一份 {@code COL_*} 常量导致风格漂移。Minecraft 1.20.1 没有圆角 API，
 * 这里用「中间大矩形 + 上下逐行内缩」近似圆角，单次调用最多十几个 {@code fill}，
 * 对每帧渲染开销可以忽略。</p>
 */
public final class UiTheme {

    private UiTheme() {
    }

    // ===================== 背景 / 容器 =====================
    public static final int BACKDROP_TOP = 0xE6070A0E;
    public static final int BACKDROP_BOTTOM = 0xF20A0E14;
    public static final int PANEL = 0xFF121821;
    public static final int PANEL_DEEP = 0xFF0D131A;
    public static final int CARD = 0xFF19212C;
    public static final int CARD_HOVER = 0xFF212C3A;
    public static final int FIELD = 0xFF0E141C;
    public static final int FIELD_FOCUS = 0xFF111A24;
    public static final int BORDER = 0xFF26303C;
    public static final int BORDER_STRONG = 0xFF3B4A5B;

    // ===================== 强调色 =====================
    public static final int ACCENT = 0xFF5EEAD4;
    public static final int ACCENT_DIM = 0xFF2DD4BF;
    public static final int ACCENT_SOFT = 0x335EEAD4;
    public static final int ACCENT_FAINT = 0x1A5EEAD4;

    // ===================== 文字 =====================
    public static final int TEXT = 0xFFE8EEF6;
    public static final int TEXT_DIM = 0xFF98A7B8;
    public static final int TEXT_MUTED = 0xFF697787;

    // ===================== 语义色 =====================
    public static final int DANGER = 0xFFF87171;
    public static final int DANGER_SOFT = 0x33F87171;
    public static final int SUCCESS = 0xFF4ADE80;
    public static final int WARN = 0xFFFBBF24;
    public static final int TRACK = 0x1FFFFFFF;

    /** 全屏遮罩（渐变，靠近底部略深）。 */
    public static void backdrop(GuiGraphics g, int width, int height) {
        g.fillGradient(0, 0, width, height, BACKDROP_TOP, BACKDROP_BOTTOM);
    }

    /** 圆角矩形。半径会被自动钳制到不超过短边一半。 */
    public static void roundRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(radius, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        // 中间主体
        g.fill(x, y + r, x + w, y + h - r, color);
        // 上下圆角区：逐行内缩
        for (int row = 0; row < r; row++) {
            int inset = cornerInset(row, r);
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
            g.fill(x + inset, y + h - row - 1, x + w - inset, y + h - row, color);
        }
    }

    /** 第 {@code row} 行（从角顶算）需要内缩的像素数。 */
    private static int cornerInset(int row, int radius) {
        double dy = radius - row - 0.5;
        double squared = radius * radius - dy * dy;
        return squared <= 0 ? radius : (int) Math.round(radius - Math.sqrt(squared));
    }

    /** 面板：投影 + 1px 边框 + 主体。 */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        roundRect(g, x + 2, y + 4, w, h, 10, 0x55000000);
        roundRect(g, x, y, w, h, 10, BORDER);
        roundRect(g, x + 1, y + 1, w - 2, h - 2, 9, PANEL);
    }

    /** 卡片：悬停提亮；{@code accent} 为真时用青绿描边（选中 / 已配置）。 */
    public static void card(GuiGraphics g, int x, int y, int w, int h, int radius,
                            boolean hovered, boolean accent) {
        int bg = hovered ? CARD_HOVER : CARD;
        int border = accent ? ACCENT_DIM : (hovered ? BORDER_STRONG : BORDER);
        if (accent) bg = hovered ? 0xFF243440 : 0xFF1C2833;
        roundRect(g, x, y, w, h, radius, border);
        roundRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), bg);
    }

    /** 输入框底：聚焦时青绿描边。 */
    public static void input(GuiGraphics g, int x, int y, int w, int h, boolean focused) {
        int radius = Math.min(6, h / 2);
        roundRect(g, x, y, w, h, radius, focused ? ACCENT_DIM : BORDER);
        roundRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1),
                focused ? FIELD_FOCUS : FIELD);
    }

    /** 物品槽：圆角 + 悬停青绿描边。 */
    public static void slot(GuiGraphics g, int x, int y, int size, boolean hovered) {
        roundRect(g, x, y, size, size, 5, hovered ? ACCENT : BORDER);
        roundRect(g, x + 1, y + 1, size - 2, size - 2, 4, hovered ? 0xFF263340 : 0xFF161D26);
    }

    /** 细横线（1px）。 */
    public static void hLine(GuiGraphics g, int x1, int x2, int y, int color) {
        if (x2 > x1) g.fill(x1, y, x2, y + 1, color);
    }

    /** 水平渐变线（分段插值，用于标题下的强调线）。 */
    public static void hGradient(GuiGraphics g, int x1, int x2, int y, int left, int right) {
        if (x2 <= x1) return;
        int segments = Math.max(1, Math.min(32, (x2 - x1) / 8));
        int step = Math.max(1, (x2 - x1) / segments);
        for (int x = x1; x < x2; x += step) {
            float t = (x - x1) / (float) (x2 - x1);
            g.fill(x, y, Math.min(x + step, x2), y + 1, mix(left, right, t));
        }
    }

    /** 右侧细滚动条。 */
    public static void scrollbar(GuiGraphics g, int x, int top, int bottom,
                                 int scroll, int max, int contentHeight) {
        if (max <= 0 || bottom <= top) return;
        int trackHeight = bottom - top;
        roundRect(g, x, top, 3, trackHeight, 2, TRACK);
        int thumbHeight = Math.max(20, (int) ((long) trackHeight * trackHeight / Math.max(1, contentHeight)));
        thumbHeight = Math.min(trackHeight, thumbHeight);
        int thumbY = top + (trackHeight - thumbHeight) * scroll / Math.max(1, max);
        roundRect(g, x, thumbY, 3, thumbHeight, 2, ACCENT_DIM);
    }

    /** 文字超宽截断（追加省略号）。 */
    public static String trim(Font font, String text, int width) {
        if (text == null) return "";
        if (width <= 0) return "";
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    /** 颜色线性插值（含 alpha）。 */
    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        int ra = (int) (aa + (ba - aa) * t);
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }
}
