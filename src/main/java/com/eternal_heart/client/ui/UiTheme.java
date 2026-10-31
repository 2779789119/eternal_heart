package com.eternal_heart.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 统一视觉主题 —— 深色石板底 + 青绿点缀（v2）。
 *
 * <p>与旧版的区别：色板补上了「层级」（deep / panel / card / cardHover / field），
 * 圆角与间距抽成常量，新增 {@link #shadow}、{@link #sheen}、{@link #vGradient}、
 * {@link #ambientGlow} 等原语，用来叠出投影、顶部高光与背景氛围光。</p>
 *
 * <p>Minecraft 1.20.1 没有圆角 / 阴影 API：圆角用「中间大矩形 + 上下逐行内缩」近似，
 * 阴影用几层逐渐透明、向下偏移的圆角矩形模拟。单次调用最多二三十个 {@code fill}，
 * 一屏几十张卡片仍在可接受范围内。</p>
 */
public final class UiTheme {

    private UiTheme() {
    }

    // ===================== 背景 / 容器 =====================
    public static final int BACKDROP_TOP = 0xF2090C11;
    public static final int BACKDROP_BOTTOM = 0xFC05070A;
    public static final int PANEL = 0xFF121821;
    public static final int PANEL_DEEP = 0xFF0C1017;
    public static final int CARD = 0xFF19212C;
    public static final int CARD_HOVER = 0xFF202B39;
    public static final int CARD_ACTIVE = 0xFF1B2731;
    public static final int FIELD = 0xFF0E141C;
    public static final int FIELD_FOCUS = 0xFF101B26;
    public static final int BORDER = 0xFF26303C;
    public static final int BORDER_STRONG = 0xFF3B4A5B;

    // ===================== 强调色 =====================
    public static final int ACCENT = 0xFF5EEAD4;
    public static final int ACCENT_DIM = 0xFF2DD4BF;
    public static final int ACCENT_DEEP = 0xFF11897C;
    public static final int ACCENT_SOFT = 0x3A5EEAD4;
    public static final int ACCENT_FAINT = 0x1A5EEAD4;

    // ===================== 文字 =====================
    public static final int TEXT = 0xFFE8EEF6;
    public static final int TEXT_DIM = 0xFF98A7B8;
    public static final int TEXT_MUTED = 0xFF697787;
    /** 实心按钮上的深色文字。 */
    public static final int TEXT_ON_ACCENT = 0xFF04181B;

    // ===================== 语义色 =====================
    public static final int DANGER = 0xFFF87171;
    public static final int DANGER_SOFT = 0x33F87171;
    public static final int SUCCESS = 0xFF4ADE80;
    public static final int WARN = 0xFFFBBF24;
    public static final int TRACK = 0x24FFFFFF;
    /** 投影 / 顶部高光的通用叠加色。 */
    public static final int SHEEN = 0x18FFFFFF;

    // ===================== 几何常量 =====================
    public static final int R_SM = 4;
    public static final int R_MD = 6;
    public static final int R_LG = 10;
    public static final int PAD = 16;
    public static final int GAP = 8;

    // ===================== 基础原语 =====================

    /** 全屏遮罩（渐变，靠近底部略深）。 */
    public static void backdrop(GuiGraphics g, int width, int height) {
        g.fillGradient(0, 0, width, height, BACKDROP_TOP, BACKDROP_BOTTOM);
    }

    /**
     * 背景氛围光：面板后方一团极淡的青绿辉光，让纯色遮罩不至于死板。
     *
     * <p>用若干层同心矩形近似径向渐变，全部是常量 alpha，不需要 shader。</p>
     */
    public static void ambientGlow(GuiGraphics g, int centerX, int centerY, int radius) {
        int layers = 14;
        for (int i = layers - 1; i >= 0; i--) {
            float t = (i + 1) / (float) layers;
            int w = (int) (radius * 2 * t);
            int h = (int) (radius * 1.15f * t);
            int alpha = (int) (0x12 * (1f - t) + 0x02);
            g.fill(centerX - w / 2, centerY - h / 2, centerX + w / 2, centerY + h / 2,
                    (alpha << 24) | 0x5EEAD4);
        }
    }

    /**
     * 圆角矩形（带边缘抗锯齿）。半径会被自动钳制到不超过短边一半。
     *
     * <p>小半径（药丸开关、勾选框、胶囊标签）如果用整数内缩会有明显台阶，
     * 因此每一行的左右两端按「理想边缘的整数覆盖率」补一条半透明像素，
     * 视觉上就是平滑的圆弧。</p>
     */
    public static void roundRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        int alpha = (color >>> 24) & 0xFF;
        if (alpha == 0) return;
        int r = Math.min(radius, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        // 中间主体（上下各让出 r 行给圆角）
        g.fill(x, y + r, x + w, y + h - r, color);
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            double squared = (double) r * r - dy * dy;
            double insetF = squared <= 0 ? r : r - Math.sqrt(squared);
            fillRowAA(g, x, w, y + row, insetF, color, alpha);
            fillRowAA(g, x, w, y + h - row - 1, insetF, color, alpha);
        }
    }

    /**
     * 带竖直渐变的圆角矩形（每一行取插值色 + 抗锯齿边缘）。
     *
     * <p>不能用「圆角底色 + 方形渐变」叠加：方形渐变的四个直角会盖不住圆角，
     * 底部两角会露出底色的方块。</p>
     */
    public static void roundRectVGradient(GuiGraphics g, int x, int y, int w, int h, int radius,
                                          int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(radius, Math.min(w, h) / 2);
        for (int row = 0; row < h; row++) {
            int color = mix(top, bottom, h <= 1 ? 0f : row / (float) (h - 1));
            int alpha = (color >>> 24) & 0xFF;
            if (alpha == 0) continue;
            double insetF = 0;
            int fromEdge = Math.min(row, h - 1 - row);
            if (fromEdge < r) {
                double dy = r - fromEdge - 0.5;
                double squared = (double) r * r - dy * dy;
                insetF = squared <= 0 ? r : r - Math.sqrt(squared);
            }
            fillRowAA(g, x, w, y + row, insetF, color, alpha);
        }
    }

    /** 不带抗锯齿的圆角矩形（投影等大面积低透明度元素用，省一半填充）。 */
    public static void roundRectFast(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        if (((color >>> 24) & 0xFF) == 0) return;
        int r = Math.min(radius, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x, y + r, x + w, y + h - r, color);
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

    /**
     * 画一行圆角边缘：{@code insetF} 是理想边缘距左边界的浮点距离。
     *
     * <p>左端像素覆盖率 = 1 - 小数部分，右端镜像对称，中间填实心。</p>
     */
    private static void fillRowAA(GuiGraphics g, int x, int w, int rowY, double insetF, int color, int alpha) {
        int k = (int) Math.floor(insetF);
        if (k * 2 >= w) return;
        double frac = insetF - k;
        if (frac <= 0.004) {
            g.fill(x + k, rowY, x + w - k, rowY + 1, color);
            return;
        }
        int edgeAlpha = (int) Math.round(alpha * (1.0 - frac));
        if (edgeAlpha > 0) {
            int edgeColor = (Math.min(255, edgeAlpha) << 24) | (color & 0xFFFFFF);
            g.fill(x + k, rowY, x + k + 1, rowY + 1, edgeColor);
            g.fill(x + w - k - 1, rowY, x + w - k, rowY + 1, edgeColor);
        }
        int innerX1 = x + k + 1;
        int innerX2 = x + w - k - 1;
        if (innerX2 > innerX1) g.fill(innerX1, rowY, innerX2, rowY + 1, color);
    }

    /**
     * 抗锯齿实心圆（开关圆钮、状态点）。
     *
     * @param cx     圆心 x（可用小数，便于插值动画）
     * @param cy     圆心 y
     * @param radius 半径
     */
    public static void disc(GuiGraphics g, double cx, double cy, double radius, int color) {
        int alpha = (color >>> 24) & 0xFF;
        if (alpha == 0 || radius <= 0) return;
        int top = (int) Math.floor(cy - radius);
        int bottom = (int) Math.ceil(cy + radius);
        for (int rowY = top; rowY < bottom; rowY++) {
            double dy = rowY + 0.5 - cy;
            double squared = radius * radius - dy * dy;
            if (squared <= 0) continue;
            double half = Math.sqrt(squared);
            double left = cx - half;
            double right = cx + half;
            int lk = (int) Math.floor(left);
            int rk = (int) Math.floor(right);
            double leftFrac = left - lk;
            double rightFrac = right - rk;
            int leftAlpha = (int) Math.round(alpha * (1.0 - leftFrac));
            int rightAlpha = (int) Math.round(alpha * rightFrac);
            if (leftAlpha > 0) {
                g.fill(lk, rowY, lk + 1, rowY + 1, (Math.min(255, leftAlpha) << 24) | (color & 0xFFFFFF));
            }
            if (rk > lk + 1) g.fill(lk + 1, rowY, rk, rowY + 1, color);
            if (rightAlpha > 0) {
                g.fill(rk, rowY, rk + 1, rowY + 1, (Math.min(255, rightAlpha) << 24) | (color & 0xFFFFFF));
            }
        }
    }

    /** 柔和投影：三层向下偏移的圆角矩形，越靠下越淡（不吃抗锯齿，省填充）。 */
    public static void shadow(GuiGraphics g, int x, int y, int w, int h, int radius, int strength) {
        int a1 = Math.min(0x40, strength);
        roundRectFast(g, x + 1, y + 3, w, h, radius + 1, (a1 << 24));
        roundRectFast(g, x + 2, y + 6, Math.max(1, w - 2), h, radius, ((a1 * 2 / 3) << 24));
        roundRectFast(g, x + 4, y + 9, Math.max(1, w - 8), h, radius, ((a1 / 3) << 24));
    }

    /** 顶部 1px 高光（内嵌），让实心块有厚度。 */
    public static void sheen(GuiGraphics g, int x, int y, int w, int radius) {
        if (w <= radius * 2) return;
        hLine(g, x + radius, x + w - radius, y, SHEEN);
    }

    // ===================== 组件底 =====================

    /** 面板：投影 + 1px 边框 + 主体 + 顶部高光。 */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        shadow(g, x, y, w, h, R_LG, 0x48);
        roundRect(g, x, y, w, h, R_LG, BORDER);
        roundRect(g, x + 1, y + 1, w - 2, h - 2, R_LG - 1, PANEL);
        // 面板内侧再提亮半档，避免整块死黑（跟着圆角走，别用方形渐变盖出来一块直角）
        roundRectVGradient(g, x + 1, y + 1, w - 2, 28, R_LG - 1, 0x12FFFFFF, 0x00FFFFFF);
        sheen(g, x + 2, y + 1, w - 4, R_LG);
    }

    /** 卡片：悬停提亮；{@code accent} 为真时用青绿描边（选中 / 已配置）。 */
    public static void card(GuiGraphics g, int x, int y, int w, int h, int radius,
                            boolean hovered, boolean accent) {
        int bg = hovered ? CARD_HOVER : (accent ? CARD_ACTIVE : CARD);
        int border = accent ? (hovered ? ACCENT : ACCENT_DIM) : (hovered ? BORDER_STRONG : BORDER);
        roundRect(g, x, y, w, h, radius, border);
        roundRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), bg);
        if (hovered) {
            // 悬停时整行略微上浮的一点高光
            vGradient(g, x + 1, y + 1, w - 2, Math.max(2, h / 3), 0x12FFFFFF, 0x00FFFFFF);
        }
    }

    /** 悬浮版卡片：hover 强度 0..1，用于带插值的列表行。 */
    public static void cardFade(GuiGraphics g, int x, int y, int w, int h, int radius,
                                float hover, boolean accent) {
        int bg = mix(accent ? CARD_ACTIVE : CARD, CARD_HOVER, hover);
        int border = accent ? mix(ACCENT_DIM, ACCENT, hover) : mix(BORDER, BORDER_STRONG, hover);
        roundRect(g, x, y, w, h, radius, border);
        roundRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), bg);
        if (hover > 0.02f) {
            vGradient(g, x + 1, y + 1, w - 2, Math.max(2, h / 3),
                    ((int) (0x14 * hover) << 24) | 0xFFFFFF, 0x00FFFFFF);
        }
    }

    /** 输入框底：聚焦时青绿描边 + 一圈淡光。 */
    public static void input(GuiGraphics g, int x, int y, int w, int h, boolean focused) {
        int radius = Math.min(R_MD, h / 2);
        if (focused) {
            roundRect(g, x - 1, y - 1, w + 2, h + 2, radius + 1, 0x3A5EEAD4);
        }
        roundRect(g, x, y, w, h, radius, focused ? ACCENT_DIM : BORDER);
        roundRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1),
                focused ? FIELD_FOCUS : FIELD);
    }

    /** 物品槽：圆角 + 悬停青绿描边。 */
    public static void slot(GuiGraphics g, int x, int y, int size, boolean hovered) {
        roundRect(g, x, y, size, size, R_MD, hovered ? ACCENT : BORDER);
        roundRect(g, x + 1, y + 1, size - 2, size - 2, R_MD - 1, hovered ? 0xFF263340 : 0xFF151C25);
        hLine(g, x + 3, x + size - 3, y + 1, 0x0CFFFFFF);
    }

    /**
     * 小胶囊标签（项数、状态、ID 之类）。
     *
     * <p>只有一层底色 —— 早先「外框 + 内缩 1px」的做法在小尺寸下内外圆角半径不一致，
     * 边框宽度会在转角处突然变粗；这里改用顶部一条高光表现轮廓。</p>
     */
    public static void chip(GuiGraphics g, int x, int y, int w, int h, int bg, int border) {
        roundRect(g, x, y, w, h, h / 2, bg);
        if (((border >>> 24) & 0xFF) == 0 || w <= h) return;
        hLine(g, x + h / 2, x + w - h / 2, y + 1, border);
    }

    // ===================== 线条 / 渐变 =====================

    /** 细横线（1px）。 */
    public static void hLine(GuiGraphics g, int x1, int x2, int y, int color) {
        if (x2 > x1) g.fill(x1, y, x2, y + 1, color);
    }

    /** 细竖线（1px）。 */
    public static void vLine(GuiGraphics g, int x, int y1, int y2, int color) {
        if (y2 > y1) g.fill(x, y1, x + 1, y2, color);
    }

    /** 水平渐变线（分段插值，用于标题下的强调线）。 */
    public static void hGradient(GuiGraphics g, int x1, int x2, int y, int left, int right) {
        if (x2 <= x1) return;
        int segments = Math.max(1, Math.min(48, (x2 - x1) / 6));
        int step = Math.max(1, (x2 - x1) / segments);
        for (int x = x1; x < x2; x += step) {
            float t = (x - x1) / (float) (x2 - x1);
            g.fill(x, y, Math.min(x + step, x2), y + 1, mix(left, right, t));
        }
    }

    /** 垂直渐变矩形（内部用原版 fillGradient，性能最好）。 */
    public static void vGradient(GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        g.fillGradient(x, y, x + w, y + h, top, bottom);
    }

    /** 水平渐变矩形（逐列插值，列宽 1px）。 */
    public static void hGradientBox(GuiGraphics g, int x, int y, int w, int h, int left, int right) {
        if (w <= 0 || h <= 0) return;
        for (int i = 0; i < w; i++) {
            g.fill(x + i, y, x + i + 1, y + h, mix(left, right, i / (float) w));
        }
    }

    /** 分隔线：中间实、两端渐隐。 */
    public static void divider(GuiGraphics g, int x1, int x2, int y, int color) {
        if (x2 <= x1) return;
        int mid = (x1 + x2) / 2;
        hGradient(g, x1, mid, y, withAlpha(color, 0), color);
        hGradient(g, mid, x2, y, color, withAlpha(color, 0));
    }

    // ===================== 图形符号 =====================

    /** 手绘对勾（两段斜线拼成，用于勾选框）。 */
    public static void check(GuiGraphics g, int x, int y, int size, int color) {
        int step = Math.max(1, size / 6);
        for (int i = 0; i < size / 2; i++) {
            g.fill(x + i, y + size / 2 + i - step, x + i + step, y + size / 2 + i + step, color);
        }
        for (int i = 0; i < size / 2; i++) {
            int px = x + size / 2 + i;
            int py = y + size - 1 - i;
            g.fill(px, py - step, px + step, py, color);
        }
    }

    /** 三角形（下拉箭头 / 排序标记），{@code down} 为真时尖朝下。 */
    public static void triangle(GuiGraphics g, int x, int y, int size, boolean down, int color) {
        int half = size / 2;
        for (int row = 0; row < half; row++) {
            int inset = row;
            int yy = down ? y + row : y + half - 1 - row;
            g.fill(x + inset, yy, x + size - inset, yy + 1, color);
        }
    }

    // ===================== 文本工具 =====================

    /** 文字超宽截断（追加省略号）。 */
    public static String trim(Font font, String text, int width) {
        if (text == null) return "";
        if (width <= 0) return "";
        if (font.width(text) <= width) return text;
        String ellipsis = "…";
        int ellipsisWidth = font.width(ellipsis);
        if (width <= ellipsisWidth) return "";
        return font.plainSubstrByWidth(text, width - ellipsisWidth) + ellipsis;
    }

    /** 从右侧截断（保留尾部，用于长路径 / ID）。 */
    public static String trimLeft(Font font, String text, int width) {
        if (text == null) return "";
        if (width <= 0) return "";
        if (font.width(text) <= width) return text;
        int ellipsisWidth = font.width("…");
        if (width <= ellipsisWidth) return "";
        int skip = 0;
        while (skip < text.length() && font.width(text.substring(skip)) > width - ellipsisWidth) {
            skip++;
        }
        return "…" + text.substring(Math.min(skip, text.length()));
    }

    // ===================== 颜色工具 =====================

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

    /** 替换 alpha 通道。 */
    public static int withAlpha(int color, int alpha) {
        return ((Math.max(0, Math.min(255, alpha))) << 24) | (color & 0xFFFFFF);
    }

    /** 提亮（向白色插值）。 */
    public static int lighten(int color, float amount) {
        return mix(color, 0xFFFFFFFF, amount);
    }
}
