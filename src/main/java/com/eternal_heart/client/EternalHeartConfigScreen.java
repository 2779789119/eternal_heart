package com.eternal_heart.client;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.network.ConfigNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Server-authorized config editor. Widgets edit a draft; Save commits it on the server. */
public class EternalHeartConfigScreen extends Screen {

    // ===================== 布局常量 =====================
    private static final int MAX_PANEL_W = 640;   // 面板最大宽度
    private static final int MAX_PANEL_H = 540;   // 面板最大高度
    private static final int HEADER_H = 34;       // 顶部标题区高度
    private static final int FOOTER_H = 26;       // 底部状态区高度
    private static final int CAT_W = 86;          // 左侧分类栏宽度
    private static final int CAT_ITEM_H = 16;     // 分类项高度
    private static final int CAT_STEP = 18;       // 分类项步进（含间隔）
    private static final int ROW_H = 24;          // 配置行高度
    private static final int HEADER_ROW_H = 22;   // 分类标题行高度
    private static final int NUM_BOX_W = 110;     // 数值输入框宽度（长数字完整可读可编辑）

    // ===================== 配色（深蓝夜空 + 暗金点缀）=====================
    private static final int COL_OVERLAY = 0xB0040609;      // 全屏遮罩
    private static final int COL_PANEL = 0xF60E1521;        // 面板主体
    private static final int COL_SIDEBAR = 0x50060A12;      // 左栏更深底色
    private static final int COL_BORDER = 0xFF9A7B3F;       // 外边框（暗金）
    private static final int COL_BORDER_IN = 0x26FFFFFF;    // 内衬微光线
    private static final int COL_GOLD = 0xFFE8C860;         // 主金色
    private static final int COL_TEXT = 0xFFE6EAF0;         // 行名亮白
    private static final int COL_TEXT_DIM = 0xFF8A93A0;     // 次要灰字
    private static final int COL_ROW_ALT = 0x08FFFFFF;      // 交替行底色
    private static final int COL_ROW_HOVER = 0x16FFFFFF;    // 行悬停高亮

    // ===================== 面板几何（init 时计算，render 直接使用）=====================
    private int panelX, panelY, panelW, panelH;
    private int contentX1, contentX2, contentTop, contentBot;

    // ===================== 状态 =====================
    private final List<Entry> entries = new ArrayList<>();       // 全部配置项
    private final List<Entry> visible = new ArrayList<>();       // 过滤后的可见项
    private final List<String> categoryOrder = new ArrayList<>();// 分类显示顺序
    private final Set<AbstractWidget> rowWidgets = new HashSet<>();// 行内控件集合（用于 scissor 内渲染）
    private String selectedCategory = null;                      // 当前选中分类（null=全部）
    private String lastSearch = "";                              // 搜索词（resize 恢复用）
    private Entry hoveredEntry;                                  // 悬停行（用于悬浮提示）
    private int scroll = 0, scrollMax = 0;                       // 内容滚动
    private int catScroll = 0, catScrollMax = 0;                 // 分类栏滚动
    private int contentH = 0;                                    // 过滤后内容总高
    private EditBox searchBox;
    private Button closeBtn;
    private Button cancelBtn;
    private final Map<String, Object> draft = new LinkedHashMap<>();
    private final Map<String, Object> baseline = new LinkedHashMap<>();
    private long baseRevision;
    private boolean saving;
    private boolean editable = true;
    private int savingTicks;
    private String statusKey = "";
    /** 上一次 tick 时聚焦的数值输入框（用于检测失焦并规整格式） */
    private EditBox focusedNumberBox = null;

    public EternalHeartConfigScreen(long revision, Map<String, Object> values) {
        super(Component.translatable("config.eternal_heart.title"));
        baseRevision = revision;
        baseline.putAll(values);
        draft.putAll(values);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("config.eternal_heart." + key, args).getString();
    }

    @Override
    protected void init() {
        computeGeometry();
        // resize 时 renderables 已被清空，行控件需要全部重建
        rowWidgets.clear();
        entries.clear();
        categoryOrder.clear();

        for (ForgeConfigSpec.ConfigValue<?> cv : collectConfigValues()) {
            String fieldName = fieldNameOf(cv);
            Entry e = new Entry(this, cv, fieldName, prettyName(cv), categoryOf(cv), tomlKeyOf(cv));
            entries.add(e);
            for (AbstractWidget w : e.widgets) {
                rowWidgets.add(w);
                this.addRenderableWidget(w);
            }
            if (!categoryOrder.contains(e.category)) categoryOrder.add(e.category);
        }

        // 搜索框（header 右侧）
        int sw = Math.min(170, this.panelW / 3);
        this.searchBox = new EditBox(this.font, this.panelX + this.panelW - sw - 10,
                this.panelY + 9, sw, 16, Component.translatable("config.eternal_heart.search"));
        this.searchBox.setHint(Component.translatable("config.eternal_heart.search_hint"));
        this.searchBox.setMaxLength(32);
        this.searchBox.setValue(lastSearch); // resize 恢复（responder 幂等）
        this.searchBox.setResponder(s -> {
            lastSearch = s;
            if (!s.isBlank()) selectedCategory = null; // 输入搜索词 → 回到全局视图
            applyFilter();
        });
        this.addRenderableWidget(this.searchBox);

        // 关闭按钮（footer 右侧）
        this.closeBtn = this.addRenderableWidget(Button.builder(Component.translatable("config.eternal_heart.save"),
                        b -> this.onClose())
                .bounds(this.panelX + this.panelW - 104, this.panelY + this.panelH - FOOTER_H + 4, 96, 18)
                .build());

        this.cancelBtn = this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),
                        b -> Minecraft.getInstance().setScreen(null))
                .bounds(this.panelX + this.panelW - 166, this.panelY + this.panelH - FOOTER_H + 4, 58, 18).build());
        applyFilter();
    }

    /** 计算面板几何（居中 + 边界保护） */
    private void computeGeometry() {
        this.panelW = Math.min(this.width - 12, MAX_PANEL_W);
        this.panelH = Math.min(this.height - 12, MAX_PANEL_H);
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;
        this.contentTop = this.panelY + HEADER_H;
        this.contentBot = this.panelY + this.panelH - FOOTER_H;
        this.contentX1 = this.panelX + CAT_W + 4;
        this.contentX2 = this.panelX + this.panelW - 7; // 右侧留出滚动条
    }

    /** 根据搜索词 / 选中分类重建可见列表，并同步内容总高 */
    private void applyFilter() {
        visible.clear();
        // 过滤结果变化：统一隐藏全部行控件并清理焦点。
        // 切换分类 / 搜索后，不在新列表中的旧行控件由这里清掉（renderRows 只管理当前 visible 列表）。
        // 只在此处（过滤变化时）执行，不影响渲染帧内用户正在编辑的输入框焦点。
        for (Entry e : entries) {
            for (AbstractWidget w : e.widgets) {
                w.visible = false;
                w.setFocused(false);
            }
        }
        String q = lastSearch == null ? "" : lastSearch.trim().toLowerCase(Locale.ROOT);
        boolean searchMode = !q.isEmpty();
        for (Entry e : entries) {
            if (searchMode ? e.matches(q)
                    : (selectedCategory == null || e.category.equals(selectedCategory))) {
                visible.add(e);
            }
        }
        // 内容总高（「全部」/ 搜索视图含分类标题行）
        contentH = 0;
        if (selectedCategory == null) {
            String prev = null;
            for (Entry e : visible) {
                if (!e.category.equals(prev)) {
                    contentH += HEADER_ROW_H;
                    prev = e.category;
                }
                contentH += ROW_H;
            }
        } else {
            contentH = visible.size() * ROW_H;
        }
        scroll = 0;
        recalcScroll();
    }

    /** 滚动边界钳制（每帧调用，廉价） */
    private void recalcScroll() {
        int viewH = contentBot - contentTop;
        scrollMax = Math.max(0, contentH - viewH);
        if (scroll > scrollMax) scroll = scrollMax;
        if (scroll < 0) scroll = 0;
        int listTop = contentTop + 14; // 分类栏小标题占位
        catScrollMax = Math.max(0, (1 + categoryOrder.size()) * CAT_STEP - (contentBot - listTop));
        if (catScroll > catScrollMax) catScroll = catScrollMax;
        if (catScroll < 0) catScroll = 0;
    }

    // ===================== 渲染 =====================

    @Override
    public void renderBackground(GuiGraphics gui) {
        // 故意留空——所有背景在 render() 中直接绘制，避免默认背景覆盖自定义面板
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        recalcScroll();

        // 1) 全屏遮罩
        g.fill(0, 0, this.width, this.height, COL_OVERLAY);
        // 2) 面板框架 + header + 左栏 + footer
        drawPanelFrame(g);
        drawHeader(g);
        drawSidebar(g, mx, my);
        // 3) 内容区（scissor 裁剪，平滑滚动不溢出）
        g.enableScissor(contentX1, contentTop, contentX2, contentBot);
        renderRows(g, mx, my, pt);
        for (Renderable r : this.renderables) {
            if (rowWidgets.contains(r)) r.render(g, mx, my, pt);
        }
        g.disableScissor();
        // 4) 滚动条 + footer 状态文字
        drawScrollbar(g);
        drawFooter(g);
        // 5) scissor 外的常驻控件
        this.searchBox.render(g, mx, my, pt);
        this.closeBtn.render(g, mx, my, pt);
        this.cancelBtn.render(g, mx, my, pt);
        // 6) 悬浮提示（最后绘制，确保浮在最上层）
        if (hoveredEntry != null) {
            g.renderComponentTooltip(this.font, tooltipFor(hoveredEntry), mx, my);
        }
    }

    // ===================== 悬浮提示 =====================

    /**
     * 每个配置行的悬浮说明：名称 + 说明 + 当前值 / 默认值。
     *
     * <p>说明文字优先取语言键 {@code config.eternal_heart.desc.<字段名>}（便于本地化），
     * 找不到时回退到配置 schema 里的注释 —— 也就是 toml 中那段中文说明，
     * 因此无需为 140 多项逐个补翻译就能立刻有可读提示。</p>
     */
    private List<Component> tooltipFor(Entry entry) {
        String key = entry.fieldName.toLowerCase(Locale.ROOT);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("config.eternal_heart.field." + key)
                .withStyle(style -> style.withColor(0xFFE8C860)));

        String descKey = "config.eternal_heart.desc." + key;
        if (net.minecraft.client.resources.language.I18n.exists(descKey)) {
            lines.add(Component.translatable(descKey).withStyle(style -> style.withColor(0xFFB9C2CE)));
        } else {
            // 1.20.1 的 ValueSpec#getComment() 返回单个字符串（多行以 \n 分隔）
            String comment = commentOf(entry.cv);
            if (comment != null) {
                for (String line : comment.split("\n")) {
                    if (line.isBlank()) continue;
                    // Forge 会为 defineInRange 自动追加 "Range: min ~ max"；本项目把上限放开到
                    // ±Double.MAX_VALUE，于是提示里显示成 ±1.7976931348623157E308（天文数字，可读性为零）
                    // → 替换成人话
                    if (line.contains("1.7976931348623157E308")) {
                        lines.add(Component.translatable("config.eternal_heart.range.unlimited")
                                .withStyle(style -> style.withColor(0xFFB9C2CE)));
                    } else {
                        lines.add(Component.literal(line).withStyle(style -> style.withColor(0xFFB9C2CE)));
                    }
                }
            }
        }

        lines.add(Component.translatable("config.eternal_heart.tooltip.value",
                        displayValue(entry.fieldName, draft.get(entry.fieldName)),
                        displayValue(entry.fieldName, entry.cv.getDefault()))
                .withStyle(style -> style.withColor(0xFF8A93A0)));
        return lines;
    }

    /** 配置项在 schema 里的注释（与 toml 中显示的一致；多行以 \n 分隔）。 */
    private static String commentOf(ForgeConfigSpec.ConfigValue<?> cv) {
        ForgeConfigSpec.ValueSpec spec = EternalHeartConfig.SPEC.getSpec().get(cv.getPath());
        return spec == null ? null : spec.getComment();
    }

    /** 配置值 → 可读文本（数值走显示单位换算，布尔走开/关，列表给项数）。 */
    private String displayValue(String field, Object value) {
        if (value == null) return "-";
        if (value instanceof Boolean on) return tr(on ? "on" : "off");
        if (value instanceof List<?> list) return tr("items", list.size());
        if (value instanceof Number number) return fmt(ConfigValues.toDisplay(field, number));
        if (value instanceof Enum<?> mode) return flightModeName(mode);
        return String.valueOf(value);
    }

    /** 面板底色、双线边框、header 发光分隔线、footer/侧栏分隔线 */
    private void drawPanelFrame(GuiGraphics g) {
        // 主体
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, COL_PANEL);
        // 左栏更深底色
        g.fill(panelX + 1, contentTop, panelX + CAT_W, contentBot, COL_SIDEBAR);
        // 外边框（暗金 1px）
        g.fill(panelX, panelY, panelX + panelW, panelY + 1, COL_BORDER);
        g.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, COL_BORDER);
        g.fill(panelX, panelY, panelX + 1, panelY + panelH, COL_BORDER);
        g.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, COL_BORDER);
        // 内衬微光线（双线框效果）
        g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 2, COL_BORDER_IN);
        g.fill(panelX + 1, panelY + panelH - 2, panelX + panelW - 1, panelY + panelH - 1, COL_BORDER_IN);
        g.fill(panelX + 1, panelY + 1, panelX + 2, panelY + panelH - 1, COL_BORDER_IN);
        g.fill(panelX + panelW - 2, panelY + 1, panelX + panelW - 1, panelY + panelH - 1, COL_BORDER_IN);
        // header 底部中心发光金线
        drawGlowLine(g, panelX + 2, panelX + panelW - 2, panelY + HEADER_H);
        // footer 顶部分隔
        g.fill(panelX + 2, panelY + panelH - FOOTER_H, panelX + panelW - 2,
                panelY + panelH - FOOTER_H + 1, 0x25FFFFFF);
        // 侧栏右侧分隔
        g.fill(panelX + CAT_W, contentTop, panelX + CAT_W + 1, contentBot, 0x25FFFFFF);
    }

    /** 左上标题 + 副标题（搜索框为组件，在 render 末尾统一渲染） */
    private void drawHeader(GuiGraphics g) {
        g.drawString(this.font, tr("title"), panelX + 10, panelY + 6, COL_GOLD);
        g.drawString(this.font, trimToWidth(tr("subtitle"), panelW - searchBox.getWidth() - 30),
                panelX + 10, panelY + 19, 0xFF7E8896);
    }

    /** 左侧分类导航栏（手动绘制，命中检测见 mouseClicked） */
    private void drawSidebar(GuiGraphics g, int mx, int my) {
        int listTop = contentTop + 14;
        g.drawString(this.font, tr("categories"), panelX + 10, contentTop + 3, 0xFF6E7887);

        int total = 1 + categoryOrder.size();
        for (int i = 0; i < total; i++) {
            int iy = listTop - catScroll + i * CAT_STEP;
            if (iy + CAT_ITEM_H <= contentTop + 13 || iy >= contentBot) continue;
            boolean sel;
            if (i == 0) sel = selectedCategory == null;
            else sel = categoryOrder.get(i - 1).equals(selectedCategory);
            boolean hov = !sel && mx >= panelX + 4 && mx < panelX + CAT_W - 2
                    && my >= iy && my < iy + CAT_ITEM_H;
            if (sel) {
                g.fill(panelX + 4, iy - 1, panelX + CAT_W - 2, iy + CAT_ITEM_H + 1, 0x35E8C860);
                g.fill(panelX + 4, iy - 1, panelX + 6, iy + CAT_ITEM_H + 1, COL_GOLD);
            } else if (hov) {
                g.fill(panelX + 4, iy - 1, panelX + CAT_W - 2, iy + CAT_ITEM_H + 1, 0x18FFFFFF);
            }
            String label = i == 0 ? tr("all") : categoryOrder.get(i - 1);
            g.drawString(this.font, trimToWidth(label, CAT_W - 20), panelX + 11, iy + 4,
                    sel ? 0xFFFFE9A8 : 0xFFAEB8C4);
        }
    }

    /** 内容区主体：分类标题 + 行背景/行名 + 控件布局（在 scissor 内调用） */
    private void renderRows(GuiGraphics g, int mx, int my, float pt) {
        // 注意：绝不能在渲染循环里做"每帧清焦点"的全量清理 —— 那会把用户刚点击聚焦的
        // 输入框焦点在下一帧立刻清掉（表现为输入框点不到、无法输入）。
        // 旧行控件的隐藏与焦点清理统一放在 applyFilter()（仅在过滤结果变化时执行一次）。
        boolean showHeaders = selectedCategory == null;
        int rowIdx = 0;
        String prevCat = null;
        int y = contentTop - scroll;
        hoveredEntry = null; // 每帧重算

        for (Entry e : visible) {
            if (showHeaders && !e.category.equals(prevCat)) {
                if (y + HEADER_ROW_H > contentTop && y < contentBot) {
                    drawCategoryHeader(g, e.category, y);
                }
                y += HEADER_ROW_H;
                prevCat = e.category;
            }
            e.y = y;
            e.visibleNow = y + ROW_H > contentTop && y < contentBot;
            if (e.visibleNow) {
                // 行背景：交替底色 + 悬停高亮
                boolean hover = mx >= contentX1 && mx < contentX2 && my >= y && my < y + ROW_H;
                if ((rowIdx & 1) == 1) g.fill(contentX1, y, contentX2, y + ROW_H, COL_ROW_ALT);
                if (hover) {
                    g.fill(contentX1, y, contentX2, y + ROW_H, COL_ROW_HOVER);
                    hoveredEntry = e; // 记录悬停行，由 render 末尾绘制悬浮提示
                }
                // 控件布局：从右往左放置（+ 在最右）
                int xr = contentX2 - 2;
                for (int i = e.widgets.size() - 1; i >= 0; i--) {
                    AbstractWidget w = e.widgets.get(i);
                    xr -= w.getWidth();
                    w.setPosition(xr, y + (ROW_H - 18) / 2);
                    w.visible = true;
                }
                int leftmost = e.widgets.isEmpty() ? contentX2 - 2 : xr;
                // 行名（超出可用宽度时截断）
                int nameW = leftmost - 8 - contentX1 - 4;
                g.drawString(this.font, trimToWidth(e.name, nameW),
                        contentX1 + 4, y + 8, COL_TEXT);
            } else {
                // 滚出视口的行：隐藏控件并清理焦点（不影响视口内正在编辑的输入框）
                for (AbstractWidget w : e.widgets) {
                    w.visible = false;
                    w.setFocused(false);
                }
            }
            y += ROW_H;
            rowIdx++;
        }

        if (visible.isEmpty()) {
            g.drawCenteredString(this.font, tr("no_results"),
                    (contentX1 + contentX2) / 2, (contentTop + contentBot) / 2 - 4, COL_TEXT_DIM);
        }
    }

    /** 分类标题行：金色标题 + 右侧渐隐装饰线 */
    private void drawCategoryHeader(GuiGraphics g, String cat, int y) {
        String label = "◆ " + cat;
        g.drawString(this.font, label, contentX1 + 4, y + 7, COL_GOLD);
        int tx = contentX1 + 8 + this.font.width(label);
        drawGradientH(g, tx, contentX2, y + 11, 0x70E8C860, 0x06E8C860);
    }

    /** 右侧滚动条（金色拇指） */
    private void drawScrollbar(GuiGraphics g) {
        if (scrollMax <= 0) return;
        int trackH = contentBot - contentTop;
        int thumbH = Math.max(14, (int) ((long) trackH * trackH / Math.max(1, contentH)));
        int thumbY = contentTop + (trackH - thumbH) * scroll / Math.max(1, scrollMax);
        int tx = panelX + panelW - 5;
        g.fill(tx, contentTop, tx + 3, contentBot, 0x20FFFFFF);
        g.fill(tx, thumbY, tx + 3, thumbY + thumbH, 0x90E8C860);
    }

    /** footer 动态状态文字 */
    private void drawFooter(GuiGraphics g) {
        String status = statusKey.isEmpty() ? tr("count", visible.size()) : tr("status." + statusKey);
        g.drawString(this.font, trimToWidth(status, panelW - 184),
                panelX + 10, panelY + panelH - FOOTER_H + 9, COL_TEXT_DIM);
    }

    // ===================== 渐变绘制辅助 =====================

    /** 中心发光水平线：中间亮金、两端渐隐 */
    private void drawGlowLine(GuiGraphics g, int x1, int x2, int y) {
        int mid = (x1 + x2) / 2;
        drawGradientH(g, x1, mid, y, 0x00E8C860, 0xE0E8C860);
        drawGradientH(g, mid, x2, y, 0xE0E8C860, 0x00E8C860);
    }

    /** 水平渐变线（分段 fill 模拟） */
    private void drawGradientH(GuiGraphics g, int x1, int x2, int y, int ca, int cb) {
        if (x2 <= x1) return;
        int seg = Math.max(1, (x2 - x1) / 3);
        for (int x = x1; x < x2; x += seg) {
            float t = (x - x1) / (float) (x2 - x1);
            g.fill(x, y, Math.min(x + seg, x2), y + 1, lerpColor(ca, cb, t));
        }
    }

    private static int lerpColor(int a, int b, float t) {
        int aA = (a >>> 24) & 0xFF, aR = (a >>> 16) & 0xFF, aG = (a >>> 8) & 0xFF, aB = a & 0xFF;
        int bA = (b >>> 24) & 0xFF, bR = (b >>> 16) & 0xFF, bG = (b >>> 8) & 0xFF, bB = b & 0xFF;
        return ((int) (aA + (bA - aA) * t) << 24) | ((int) (aR + (bR - aR) * t) << 16)
                | ((int) (aG + (bG - aG) * t) << 8) | (int) (aB + (bB - aB) * t);
    }

    /** 文字超宽截断（追加省略号） */
    private String trimToWidth(String s, int w) {
        if (w <= 0) return "";
        if (this.font.width(s) <= w) return s;
        return this.font.plainSubstrByWidth(s, Math.max(0, w - 6)) + "…";
    }

    // ===================== 输入处理 =====================

    @Override
    public boolean mouseClicked(double mxd, double myd, int button) {
        int mx = (int) mxd, my = (int) myd;
        // 左侧分类栏命中检测
        int listTop = contentTop + 14;
        if (button == 0 && mx >= panelX + 4 && mx < panelX + CAT_W - 2
                && my >= listTop && my < contentBot) {
            int rel = my - listTop + catScroll;
            int idx = rel / CAT_STEP;
            if (idx >= 0 && idx < 1 + categoryOrder.size() && rel % CAT_STEP < CAT_ITEM_H) {
                if (idx == 0) {
                    selectedCategory = null;
                } else {
                    String cat = categoryOrder.get(idx - 1);
                    // 再次点击已选分类 → 取消选择回全部
                    selectedCategory = cat.equals(selectedCategory) ? null : cat;
                    if (selectedCategory != null) {
                        searchBox.setValue(""); // 清空搜索（responder 幂等，不会清掉分类选择）
                    }
                }
                applyFilter();
                return true;
            }
        }
        return super.mouseClicked(mxd, myd, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        // 鼠标在左栏 → 滚动分类栏；否则滚动内容区
        if (mx >= panelX && mx < panelX + CAT_W && my >= contentTop && my < contentBot) {
            catScroll = (int) Math.max(0, Math.min(catScrollMax, catScroll - delta * CAT_STEP));
        } else {
            scroll = (int) Math.max(0, Math.min(scrollMax, scroll - delta * ROW_H));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 搜索框聚焦时 ESC 先退出焦点，而不是直接关屏
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && searchBox != null && searchBox.isFocused()) {
            searchBox.setFocused(false);
            return true;
        }
        // 数值输入框按回车 → 提交并失焦（格式规整由 tick 的失焦检测统一执行）
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && this.getFocused() instanceof EditBox eb && eb != searchBox) {
            eb.setFocused(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        closeBtn.active = editable && !saving;
        cancelBtn.active = !saving;
        if (saving && ++savingTicks > 200) {
            saving = false;
            statusKey = "timeout";
        }
        for (Entry e : entries) for (AbstractWidget w : e.widgets) w.active = editable && !saving;
        // 检测数值输入框失焦（点击其他控件/空白区域/按回车后）：
        // 把可能残缺或非法的文字（""、"1."、"abc"）规整为当前配置值的标准格式。
        // 输入框聚焦编辑期间绝不回写，光标才能自由移动。
        EditBox now = (this.getFocused() instanceof EditBox eb && eb != this.searchBox) ? eb : null;
        if (focusedNumberBox != null && focusedNumberBox != now) {
            for (Entry e : entries) {
                if (e.box == focusedNumberBox) {
                    e.syncBoxFromValue();
                    break;
                }
            }
        }
        focusedNumberBox = now;
    }

    // ===================== 配置收集与元数据 =====================

    private List<ForgeConfigSpec.ConfigValue<?>> collectConfigValues() {
        // 列表类配置（黑白名单）同样进入面板：由 Entry 渲染为「编辑」按钮 + 效果选择器
        return List.copyOf(ConfigValues.entries().values());
    }

    private static String categoryOf(ForgeConfigSpec.ConfigValue<?> cv) {
        List<String> path = cv.getPath();
        if (path == null || path.size() < 2) return tr("category.other");
        String raw = path.get(0);
        int p = raw.indexOf(" (");
        String key = p >= 0 ? raw.substring(p + 2, raw.length() - 1) : raw;
        return tr("category." + key.toLowerCase(Locale.ROOT).replace(' ', '_'));
    }

    /** toml 键名（如 attackDamage，用于搜索匹配） */
    private static String tomlKeyOf(ForgeConfigSpec.ConfigValue<?> cv) {
        List<String> path = cv.getPath();
        return path == null || path.isEmpty() ? "" : path.get(path.size() - 1);
    }

    private static String flightModeName(Object e) {
        return tr("flight." + e.toString().toLowerCase(Locale.ROOT));
    }

    private String prettyName(ForgeConfigSpec.ConfigValue<?> cv) {
        return tr("field." + fieldNameOf(cv).toLowerCase(Locale.ROOT));
    }

    private Map<String, Object> changes() {
        Map<String, Object> patch = new LinkedHashMap<>();
        draft.forEach((key, value) -> {
            if (!java.util.Objects.equals(value, baseline.get(key))) patch.put(key, value);
        });
        return patch;
    }

    @Override
    public void onClose() {
        if (saving) return;
        if (!editable) { super.onClose(); return; }
        if (entries.stream().anyMatch(e -> e.invalid)) { statusKey = "invalid"; return; }
        Map<String, Object> patch = changes();
        if (patch.isEmpty()) { super.onClose(); return; }
        saving = true;
        savingTicks = 0;
        statusKey = "saving";
        ConfigNetwork.submit(baseRevision, patch);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    /**
     * 打开列表配置的可视化编辑器。
     *
     * <p>按配置项分派：黑白名单 → 效果选择器；自定义属性 → 属性选择器。
     * 选择器直接编辑草稿（draft），返回后行内按钮的项数即时更新；
     * 真正的保存仍由本屏的「保存并关闭」统一提交。</p>
     */
    void openListEditor(String fieldName) {
        Object raw = draft.get(fieldName);
        List<String> initial = new ArrayList<>();
        if (raw instanceof List<?> list) for (Object element : list) initial.add(String.valueOf(element));
        java.util.function.Consumer<List<String>> onChange = updated -> {
            draft.put(fieldName, List.copyOf(updated));
            statusKey = "draft";
        };
        Minecraft.getInstance().setScreen(switch (fieldName) {
            case "EXTRA_ATTRIBUTES" -> new AttributeListScreen(this, initial, onChange);
            // 自由文本列表（配方 JSON）：不能交给效果选择器——它会把非 ID 文本当效果处理，
            // 点一次「完成」就会把内容改坏，必须走通用文本编辑器
            // 配方：默认走图形化编辑器（点格子选物品），内部可切到文本模式
            case "CUSTOM_RECIPES" -> new RecipeEditorScreen(this, initial, onChange);
            default -> new EffectListScreen(this, fieldName, initial, onChange);
        });
    }

    public void receive(ConfigNetwork.Snapshot packet, Map<String, Object> values) {
        editable = packet.editable();
        if (packet.status().equals("saved") && saving) {
            saving = false;
            Minecraft.getInstance().setScreen(null);
            return;
        }
        if (packet.status().equals("sync") && saving) return;
        if (packet.status().equals("sync") && packet.revision() == baseRevision) return;
        Map<String, Object> pending = changes();
        baseline.clear();
        baseline.putAll(values);
        draft.clear();
        draft.putAll(values);
        draft.putAll(pending);
        baseRevision = packet.revision();
        saving = false;
        statusKey = packet.status().equals("sync") ? "conflict" : packet.status();
        for (Entry e : entries) e.refresh();
    }

    // ===================== 配置行 =====================

    private static String fieldNameOf(ForgeConfigSpec.ConfigValue<?> cv) {
        for (Field f : EternalHeartConfig.class.getDeclaredFields()) {
            try {
                if (f.get(null) == cv) return f.getName();
            } catch (IllegalAccessException ignored) {
            }
        }
        return null;
    }

    private static String fmt(double value) {
        return com.eternal_heart.core.Numbers.format(value);
    }

    static class Entry {
        final EternalHeartConfigScreen screen;
        final String fieldName;
        final String name;
        final String category;
        final String searchKey;
        final ForgeConfigSpec.ConfigValue<?> cv;
        final List<AbstractWidget> widgets = new ArrayList<>();
        EditBox box;
        Button button;
        boolean invalid;
        boolean listEntry;
        boolean boxUpdating;
        int y;
        boolean visibleNow;

        Entry(EternalHeartConfigScreen screen, ForgeConfigSpec.ConfigValue<?> cv,
              String fieldName, String name, String category, String tomlKey) {
            this.screen = screen;
            this.cv = cv;
            this.fieldName = fieldName;
            this.name = name;
            this.category = category;
            this.searchKey = (category + ' ' + name + ' ' + fieldName + ' ' + tomlKey).toLowerCase(Locale.ROOT);
            Object value = screen.draft.get(fieldName);
            if (value instanceof List<?>) {
                // 列表配置：行内按钮显示当前项数，点击进入可视化选择器
                listEntry = true;
                button = Button.builder(buttonText(), btn -> screen.openListEditor(fieldName))
                        .bounds(0, 0, 96, 18).build();
                widgets.add(button);
            } else if (value instanceof Boolean || value instanceof Enum<?>) {
                button = Button.builder(buttonText(), btn -> {
                    Object current = screen.draft.get(fieldName);
                    Object next;
                    if (current instanceof Boolean on) next = !on;
                    else {
                        Object[] constants = current.getClass().getEnumConstants();
                        next = constants[(((Enum<?>) current).ordinal() + 1) % constants.length];
                    }
                    ConfigValues.validate(fieldName, next);
                    screen.draft.put(fieldName, next);
                    btn.setMessage(buttonText());
                    screen.statusKey = "draft";
                }).bounds(0, 0, value instanceof Boolean ? 60 : 96, 18).build();
                widgets.add(button);
            } else if (value instanceof Number number) {
                box = new NumberEditBox(Minecraft.getInstance().font, 0, 0, NUM_BOX_W, 18, Component.literal(name));
                box.setMaxLength(48);
                // 允许科学计数法与符号中间态：Numbers.format 对极大值输出 1.798E308 这类紧凑写法，
                // 旧过滤器只认数字与小数点 → setValue 被拒绝 → 输入框显示为空（数值很高的配置项都会中招）
                box.setFilter(text -> text.isEmpty()
                        || text.matches("[-+]?(\\d+\\.?\\d*|\\.\\d*)?([eE][-+]?\\d*)?"));
                box.setValue(fmt(ConfigValues.toDisplay(fieldName, number)));
                box.setResponder(text -> {
                    if (boxUpdating) return;
                    try {
                        Number parsed = ConfigValues.fromDisplay(fieldName, Double.parseDouble(text));
                        screen.draft.put(fieldName, parsed);
                        invalid = false;
                        screen.statusKey = "draft";
                        box.setTextColor(0xFFE6EAF0);
                    } catch (IllegalArgumentException e) {
                        invalid = true;
                        screen.statusKey = "invalid";
                        box.setTextColor(0xFFFF7777);
                    }
                });
                widgets.add(box);
            }
        }

        Component buttonText() {
            Object value = screen.draft.get(fieldName);
            if (value instanceof Boolean on) return Component.translatable("config.eternal_heart." + (on ? "on" : "off"));
            // 列表类（黑白名单）：按钮文字显示当前项数
            if (value instanceof List<?> list)
                return Component.translatable("config.eternal_heart.effect_list.open", list.size());
            return Component.literal(flightModeName(value));
        }

        boolean matches(String query) { return searchKey.contains(query); }

        void syncBoxFromValue() {
            if (box == null || box.isFocused() || invalid) return;
            boxUpdating = true;
            box.setValue(fmt(ConfigValues.toDisplay(fieldName, (Number) screen.draft.get(fieldName))));
            boxUpdating = false;
        }

        void refresh() {
            if (button != null) button.setMessage(buttonText());
            syncBoxFromValue();
        }
    }

    /** 数值输入框：首次点击聚焦时自动全选内容，直接键入即可覆盖旧值（再次点击可正常定位光标）。 */
    static class NumberEditBox extends EditBox {
        NumberEditBox(Font font, int x, int y, int w, int h, Component msg) {
            super(font, x, y, w, h, msg);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            boolean wasFocused = this.isFocused();
            boolean handled = super.mouseClicked(mx, my, button);
            if (handled && !wasFocused) {
                // 刚获得焦点 → 全选内容，方便直接输入覆盖旧值
                this.setCursorPosition(this.getValue().length());
                this.setHighlightPos(0);
            }
            return handled;
        }
    }
}
