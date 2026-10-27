package com.eternal_heart.client;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.client.ui.UiButton;
import com.eternal_heart.client.ui.UiEditBox;
import com.eternal_heart.client.ui.UiScreen;
import com.eternal_heart.client.ui.UiSwitch;
import com.eternal_heart.client.ui.UiTheme;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.network.ConfigNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Server-authorized config editor.
 *
 * <p>视觉：深色石板面板 + 青绿点缀；左侧为胶囊式分类导航，右侧每项配置是一张
 * 「名称 / 说明 / 控件」卡片。控件编辑草稿，保存后由服务器统一应用。</p>
 */
public class EternalHeartConfigScreen extends UiScreen {

    // ===================== 布局常量 =====================
    private static final int MAX_PANEL_W = 780;   // 面板最大宽度
    private static final int MAX_PANEL_H = 560;   // 面板最大高度
    private static final int HEADER_H = 52;       // 顶部标题区高度
    private static final int FOOTER_H = 38;       // 底部状态区高度
    private static final int CAT_W = 132;         // 左侧分类栏宽度
    private static final int CAT_ITEM_H = 22;     // 分类胶囊高度
    private static final int CAT_STEP = 26;       // 分类胶囊步进（含间隔）
    private static final int ROW_H = 34;          // 配置卡片高度
    private static final int ROW_GAP = 4;         // 卡片间距
    private static final int ROW_STEP = ROW_H + ROW_GAP;
    private static final int HEADER_ROW_H = 28;   // 分类标题行高度
    private static final int NUM_BOX_W = 118;     // 数值输入框宽度

    // ===================== 面板几何（init 时计算，render 直接使用）=====================
    private int contentX1, contentX2, contentTop, contentBot;
    private int sidebarX1, sidebarX2;

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
    private UiEditBox searchBox;
    private UiButton closeBtn;
    private UiButton cancelBtn;
    /** 「饰品融合」分类里的固定入口行（不是配置项：开关状态存在物品 NBT 上）。 */
    private UiButton fusionEntryBtn;
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

        // 「饰品融合」分类顶部的固定入口：打开逐条开关界面。
        // 它不是一个配置项（状态存在物品 NBT 上），所以不进 entries，只按分类视图单独绘制。
        this.fusionEntryBtn = UiButton.secondary(0, 0, 64, 20,
                Component.translatable("config.eternal_heart.fusion.open"),
                () -> Minecraft.getInstance().setScreen(new FusionToggleScreen(this)));
        this.fusionEntryBtn.visible = false;
        this.rowWidgets.add(this.fusionEntryBtn);
        this.addRenderableWidget(this.fusionEntryBtn);

        // 搜索框（header 右侧）
        int sw = Math.min(220, this.panelW / 3);
        this.searchBox = new UiEditBox(this.font, this.panelX + this.panelW - sw - 16,
                this.panelY + 16, sw, 20, Component.translatable("config.eternal_heart.search_hint"));
        this.searchBox.setMaxLength(32);
        this.searchBox.setValue(lastSearch); // resize 恢复（responder 幂等）
        this.searchBox.setResponder(s -> {
            lastSearch = s;
            if (!s.isBlank()) selectedCategory = null; // 输入搜索词 → 回到全局视图
            applyFilter();
        });
        this.addRenderableWidget(this.searchBox);

        // 底部操作按钮
        int footerTop = this.panelY + this.panelH - FOOTER_H;
        this.closeBtn = this.addRenderableWidget(UiButton.primary(
                this.panelX + this.panelW - 16 - 118, footerTop + 8, 118, 22,
                Component.translatable("config.eternal_heart.save"), this::onClose));
        this.cancelBtn = this.addRenderableWidget(UiButton.ghost(
                this.panelX + this.panelW - 16 - 118 - 8 - 72, footerTop + 8, 72, 22,
                Component.translatable("gui.cancel"), () -> Minecraft.getInstance().setScreen(null)));
        applyFilter();
    }

    /** 计算面板几何（居中 + 边界保护） */
    private void computeGeometry() {
        layoutPanel(MAX_PANEL_W, MAX_PANEL_H, 540, 380);
        this.contentTop = this.panelY + HEADER_H;
        this.contentBot = this.panelY + this.panelH - FOOTER_H;
        this.sidebarX1 = this.panelX + 8;
        this.sidebarX2 = this.panelX + CAT_W - 8;
        this.contentX1 = this.panelX + CAT_W + 10;
        this.contentX2 = this.panelX + this.panelW - 12; // 右侧留出滚动条
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
        contentH = 8; // 顶部留白
        if (selectedCategory == null) {
            String prev = null;
            for (Entry e : visible) {
                if (!e.category.equals(prev)) {
                    contentH += HEADER_ROW_H;
                    prev = e.category;
                }
                contentH += ROW_STEP;
            }
        } else {
            contentH += visible.size() * ROW_STEP;
        }
        if (showFusionEntryRow()) contentH += ROW_STEP; // 分类顶部的固定入口行
        scroll = 0;
        recalcScroll();
    }

    /**
     * 是否显示「饰品融合」分类顶部的固定入口行。
     *
     * <p>只在<b>分类视图</b>里出现：搜索模式下一律不插 —— 搜索结果应当只包含真正命中的
     * 配置项，混进一个固定行会让「搜到几条」对不上。</p>
     */
    private boolean showFusionEntryRow() {
        return selectedCategory != null && lastSearch.isBlank()
                && selectedCategory.equals(tr("category.fusion"));
    }

    /** 滚动边界钳制（每帧调用，廉价） */
    private void recalcScroll() {
        int viewH = contentBot - contentTop;
        scrollMax = Math.max(0, contentH - viewH);
        if (scroll > scrollMax) scroll = scrollMax;
        if (scroll < 0) scroll = 0;
        int listTop = contentTop + 26; // 分类栏小标题占位
        catScrollMax = Math.max(0, (1 + categoryOrder.size()) * CAT_STEP - (contentBot - listTop));
        if (catScroll > catScrollMax) catScroll = catScrollMax;
        if (catScroll < 0) catScroll = 0;
    }

    // ===================== 渲染 =====================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        recalcScroll();

        // 1) 全屏遮罩 + 面板框架
        renderBackground(g);
        drawPanel(g);
        drawTopBar(g);
        drawSidebar(g, mx, my);
        // 2) 内容区（scissor 裁剪，平滑滚动不溢出）
        g.enableScissor(contentX1, contentTop, contentX2, contentBot);
        renderRows(g, mx, my, pt);
        for (Renderable r : this.renderables) {
            if (rowWidgets.contains(r)) r.render(g, mx, my, pt);
        }
        g.disableScissor();
        // 3) 滚动条 + footer + 常驻控件
        drawScrollbar(g, contentTop + 6, contentBot - 6, scroll, scrollMax, contentH);
        drawFooter(g, mx, my);
        this.searchBox.render(g, mx, my, pt);
        this.closeBtn.render(g, mx, my, pt);
        this.cancelBtn.render(g, mx, my, pt);
        // 4) 悬浮提示（最后绘制，确保浮在最上层）
        if (hoveredEntry != null) {
            g.renderComponentTooltip(this.font, tooltipFor(hoveredEntry), mx, my);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        UiTheme.backdrop(g, this.width, this.height);
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
                .withStyle(style -> style.withColor(UiTheme.ACCENT)));

        String descKey = "config.eternal_heart.desc." + key;
        if (I18n.exists(descKey)) {
            lines.add(Component.translatable(descKey).withStyle(style -> style.withColor(UiTheme.TEXT_DIM)));
        } else {
            // 1.20.1 的 ValueSpec#getComment() 返回单个字符串（多行以 \n 分隔）
            String comment = commentOf(entry.cv);
            if (comment != null) {
                for (String line : comment.split("\n")) {
                    if (line.isBlank()) continue;
                    // Forge 会为 defineInRange 自动追加 "Range: min ~ max"；本项目把上限放开到
                    // ±Double.MAX_VALUE / Integer.MAX_VALUE，于是提示里显示成 ±1.7976931348623157E308
                    // 或 2147483647（天文数字，可读性为零）→ 替换成人话
                    if (line.contains("1.7976931348623157E308") || line.contains("2147483647")) {
                        lines.add(Component.translatable("config.eternal_heart.range.unlimited")
                                .withStyle(style -> style.withColor(UiTheme.TEXT_DIM)));
                    } else {
                        lines.add(Component.literal(line).withStyle(style -> style.withColor(UiTheme.TEXT_DIM)));
                    }
                }
            }
        }

        lines.add(Component.translatable("config.eternal_heart.tooltip.value",
                        displayValue(entry.fieldName, draft.get(entry.fieldName)),
                        displayValue(entry.fieldName, entry.cv.getDefault()))
                .withStyle(style -> style.withColor(UiTheme.TEXT_MUTED)));
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

    /** 左上标题 + 副标题（搜索框为组件，在 render 末尾统一渲染） */
    private void drawTopBar(GuiGraphics g) {
        int searchLeft = searchBox.getX();
        int subtitleWidth = Math.max(80, searchLeft - (panelX + 16) - 16);
        drawHeader(g, Component.translatable("config.eternal_heart.title"),
                Component.literal(trim(tr("subtitle"), subtitleWidth)), HEADER_H);
    }

    /** 左侧分类导航栏（手动绘制，命中检测见 mouseClicked） */
    private void drawSidebar(GuiGraphics g, int mx, int my) {
        int listTop = contentTop + 26;
        // 侧栏底板：比主面板更深一档，形成分区
        UiTheme.roundRect(g, panelX + 6, contentTop + 6, CAT_W - 12,
                contentBot - contentTop - 12, 8, UiTheme.PANEL_DEEP);
        g.drawString(this.font, tr("categories"), panelX + 16, contentTop + 8, UiTheme.TEXT_MUTED);

        int total = 1 + categoryOrder.size();
        for (int i = 0; i < total; i++) {
            int iy = listTop - catScroll + i * CAT_STEP;
            if (iy + CAT_ITEM_H <= contentTop + 24 || iy >= contentBot) continue;
            boolean sel;
            if (i == 0) sel = selectedCategory == null;
            else sel = categoryOrder.get(i - 1).equals(selectedCategory);
            boolean hov = !sel && mx >= sidebarX1 && mx < sidebarX2
                    && my >= iy && my < iy + CAT_ITEM_H;

            if (sel) {
                UiTheme.roundRect(g, sidebarX1, iy, sidebarX2 - sidebarX1, CAT_ITEM_H, 5, UiTheme.ACCENT_SOFT);
                UiTheme.roundRect(g, sidebarX1 + 2, iy + 6, 3, CAT_ITEM_H - 12, 1, UiTheme.ACCENT);
            } else if (hov) {
                UiTheme.roundRect(g, sidebarX1, iy, sidebarX2 - sidebarX1, CAT_ITEM_H, 5, 0x14FFFFFF);
            }
            String label = i == 0 ? tr("all") : categoryOrder.get(i - 1);
            g.drawString(this.font, trim(label, CAT_W - 34), sidebarX1 + 12, iy + 7,
                    sel ? UiTheme.ACCENT : (hov ? UiTheme.TEXT : UiTheme.TEXT_DIM));
        }

        // 分类过多时显示细滚动条
        if (catScrollMax > 0) {
            int trackTop = listTop;
            int trackH = contentBot - listTop;
            UiTheme.roundRect(g, panelX + CAT_W - 12, trackTop, 3, trackH, 1, UiTheme.TRACK);
            int thumbH = Math.max(20, trackH * trackH / Math.max(trackH, total * CAT_STEP));
            int thumbY = trackTop + (trackH - thumbH) * catScroll / Math.max(1, catScrollMax);
            UiTheme.roundRect(g, panelX + CAT_W - 12, thumbY, 3, thumbH, 1, UiTheme.ACCENT_FAINT);
        }
    }

    /** 内容区主体：分类标题 + 卡片式配置行（在 scissor 内调用） */
    private void renderRows(GuiGraphics g, int mx, int my, float pt) {
        // 注意：绝不能在渲染循环里做"每帧清焦点"的全量清理 —— 那会把用户刚点击聚焦的
        // 输入框焦点在下一帧立刻清掉（表现为输入框点不到、无法输入）。
        // 旧行控件的隐藏与焦点清理统一放在 applyFilter()（仅在过滤结果变化时执行一次）。
        boolean showHeaders = selectedCategory == null;
        String prevCat = null;
        int y = contentTop - scroll + 8;
        hoveredEntry = null; // 每帧重算

        // 「饰品融合」分类顶部的固定入口（不是配置项，单独画一行并放一个按钮）
        fusionEntryBtn.visible = false;
        if (showFusionEntryRow()) {
            if (y + ROW_H > contentTop && y < contentBot) {
                boolean hover = mx >= contentX1 && mx < contentX2 && my >= y && my < y + ROW_H;
                UiTheme.card(g, contentX1, y, contentX2 - contentX1, ROW_H, 7, hover, true);
                int xr = contentX2 - 10 - fusionEntryBtn.getWidth();
                fusionEntryBtn.setPosition(xr, y + (ROW_H - fusionEntryBtn.getHeight()) / 2);
                fusionEntryBtn.visible = true;
                int nameW = xr - 8 - (contentX1 + 14);
                g.drawString(this.font, trim(tr("fusion.title"), nameW), contentX1 + 14, y + 6, UiTheme.TEXT);
                g.drawString(this.font, trim(tr("fusion.row_desc"), nameW), contentX1 + 14, y + 19,
                        UiTheme.TEXT_MUTED);
            }
            y += ROW_STEP;
        }

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
                boolean hover = mx >= contentX1 && mx < contentX2 && my >= y && my < y + ROW_H;
                UiTheme.card(g, contentX1, y, contentX2 - contentX1, ROW_H, 7, hover, false);
                if (hover) {
                    UiTheme.roundRect(g, contentX1 + 3, y + 8, 3, ROW_H - 16, 1, UiTheme.ACCENT);
                }

                // 控件布局：从右往左放置（最右侧控件贴边）
                int xr = contentX2 - 10;
                for (int i = e.widgets.size() - 1; i >= 0; i--) {
                    AbstractWidget w = e.widgets.get(i);
                    xr -= w.getWidth();
                    w.setPosition(xr, y + (ROW_H - w.getHeight()) / 2);
                    w.visible = true;
                }
                // 只在「名称 / 说明」区域显示悬浮提示，避免盖住右侧控件
                if (hover && mx < xr - 4) hoveredEntry = e;
                int leftmost = e.widgets.isEmpty() ? contentX2 - 10 : xr;
                int nameW = leftmost - 8 - (contentX1 + 14);
                g.drawString(this.font, trim(e.name, nameW), contentX1 + 14, y + 6, UiTheme.TEXT);
                if (!e.description.isBlank()) {
                    g.drawString(this.font, trim(e.description, nameW), contentX1 + 14, y + 19,
                            UiTheme.TEXT_MUTED);
                }
            } else {
                // 滚出视口的行：隐藏控件并清理焦点（不影响视口内正在编辑的输入框）
                for (AbstractWidget w : e.widgets) {
                    w.visible = false;
                    w.setFocused(false);
                }
            }
            y += ROW_STEP;
        }

        if (visible.isEmpty()) {
            drawEmpty(g, tr("no_results"), contentX1, contentTop, contentX2, contentBot);
        }
    }

    /** 分类标题行：青绿小标题 + 右侧渐隐装饰线 */
    private void drawCategoryHeader(GuiGraphics g, String cat, int y) {
        int textY = y + 10;
        g.drawString(this.font, cat, contentX1 + 4, textY, UiTheme.ACCENT_DIM);
        int tx = contentX1 + 12 + this.font.width(cat);
        UiTheme.hGradient(g, tx, contentX2 - 8, y + 14, 0x505EEAD4, 0x005EEAD4);
    }

    /** footer：状态文字（按钮为组件，在 render 末尾统一渲染） */
    private void drawFooter(GuiGraphics g, int mx, int my) {
        drawFooterDivider(g, FOOTER_H);
        String status = statusKey.isEmpty() ? tr("count", visible.size()) : tr("status." + statusKey);
        int statusY = panelY + panelH - FOOTER_H + 15;
        int color = switch (statusKey) {
            case "" -> UiTheme.TEXT_MUTED;
            case "draft", "saving", "opened", "sync", "saved" -> UiTheme.ACCENT_DIM;
            case "invalid", "save_failed", "timeout", "conflict", "forbidden" -> UiTheme.WARN;
            default -> UiTheme.TEXT_DIM;
        };
        g.drawString(this.font, trim(status, panelW - 260), panelX + 16, statusY, color);
    }

    // ===================== 输入处理 =====================

    @Override
    public boolean mouseClicked(double mxd, double myd, int button) {
        int mx = (int) mxd, my = (int) myd;
        // 左侧分类栏命中检测
        int listTop = contentTop + 26;
        if (button == 0 && mx >= sidebarX1 && mx < sidebarX2
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
        // 「饰品融合」分类顶部的入口行：点整行也能打开开关界面
        if (button == 0 && showFusionEntryRow()
                && mx >= contentX1 && mx < contentX2 && my >= contentTop && my < contentBot) {
            int rowY = contentTop - scroll + 8;
            if (my >= rowY && my < rowY + ROW_H) {
                Minecraft.getInstance().setScreen(new FusionToggleScreen(this));
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
            scroll = (int) Math.max(0, Math.min(scrollMax, scroll - delta * ROW_STEP));
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
    public boolean isPauseScreen() {
        return false;
    }

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
            // 自定义仪式配方：图形化编辑器（暗黑祭坛：激活物 + 产出 + 基座材料）
            case "CUSTOM_RITUALS" -> new RitualEditorScreen(this, initial, onChange);
            // 附加被动效果：每条还带等级与时长，走专用选择器（支持其它模组的效果）
            case "PASSIVE_EXTRA_EFFECTS" -> new PassiveEffectScreen(this, initial, onChange);
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

    /** 卡片副标题：优先本地化说明键，其次取配置注释里第一行有意义的文字。 */
    private static String describe(ForgeConfigSpec.ConfigValue<?> cv, String fieldName) {
        if (fieldName == null) return "";
        String descKey = "config.eternal_heart.desc." + fieldName.toLowerCase(Locale.ROOT);
        if (I18n.exists(descKey)) return I18n.get(descKey);
        String comment = commentOf(cv);
        if (comment == null) return "";
        for (String line : comment.split("\n")) {
            String text = line.trim();
            if (text.isEmpty()) continue;
            if (text.startsWith("Range:")) continue;
            if (text.contains("1.7976931348623157E308")) continue;
            return text;
        }
        return "";
    }

    static class Entry {
        final EternalHeartConfigScreen screen;
        final String fieldName;
        final String name;
        final String description;
        final String category;
        final String searchKey;
        final ForgeConfigSpec.ConfigValue<?> cv;
        final List<AbstractWidget> widgets = new ArrayList<>();
        UiEditBox box;
        UiButton button;
        UiSwitch toggle;
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
            this.description = describe(cv, fieldName);
            this.category = category;
            this.searchKey = (category + ' ' + name + ' ' + description + ' ' + fieldName + ' ' + tomlKey)
                    .toLowerCase(Locale.ROOT);
            Object value = screen.draft.get(fieldName);
            if (value instanceof List<?>) {
                // 列表配置：行内按钮显示当前项数，点击进入可视化选择器
                listEntry = true;
                button = UiButton.secondary(0, 0, 108, 20, buttonText(),
                        () -> screen.openListEditor(fieldName));
                widgets.add(button);
            } else if (value instanceof Boolean) {
                toggle = new UiSwitch(0, 0,
                        () -> Boolean.TRUE.equals(screen.draft.get(fieldName)),
                        next -> {
                            ConfigValues.validate(fieldName, next);
                            screen.draft.put(fieldName, next);
                            screen.statusKey = "draft";
                        });
                widgets.add(toggle);
            } else if (value instanceof Enum<?>) {
                button = UiButton.secondary(0, 0, 108, 20, buttonText(), () -> {
                    Object current = screen.draft.get(fieldName);
                    Object[] constants = current.getClass().getEnumConstants();
                    Object next = constants[(((Enum<?>) current).ordinal() + 1) % constants.length];
                    ConfigValues.validate(fieldName, next);
                    screen.draft.put(fieldName, next);
                    button.setMessage(buttonText());
                    screen.statusKey = "draft";
                });
                widgets.add(button);
            } else if (value instanceof Number number) {
                box = new UiEditBox(Minecraft.getInstance().font, 0, 0, NUM_BOX_W, 20, Component.literal(name))
                        .selectAllOnFocus();
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
                        box.setTextColor(UiTheme.TEXT);
                    } catch (IllegalArgumentException e) {
                        invalid = true;
                        screen.statusKey = "invalid";
                        box.setTextColor(UiTheme.DANGER);
                    }
                });
                widgets.add(box);
            }
        }

        Component buttonText() {
            Object value = screen.draft.get(fieldName);
            if (value instanceof Boolean on)
                return Component.translatable("config.eternal_heart." + (on ? "on" : "off"));
            // 列表类（黑白名单）：按钮文字显示当前项数
            if (value instanceof List<?> list)
                return Component.translatable("config.eternal_heart.effect_list.open", list.size());
            return Component.literal(flightModeName(value));
        }

        boolean matches(String query) {
            return searchKey.contains(query);
        }

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

}
