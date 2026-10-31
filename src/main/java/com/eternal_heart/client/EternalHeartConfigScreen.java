package com.eternal_heart.client;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.client.ui.UiButton;
import com.eternal_heart.client.ui.UiScreen;
import com.eternal_heart.client.ui.UiScroll;
import com.eternal_heart.client.ui.UiSwitch;
import com.eternal_heart.client.ui.UiTextField;
import com.eternal_heart.client.ui.UiTheme;
import com.eternal_heart.client.ui.UiWidget;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.network.ConfigNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Server-authorized config editor.
 *
 * <p>视觉：深色石板面板 + 青绿点缀；左侧为胶囊式分类导航，右侧每项配置是一张
 * 「名称 / 说明 / 控件」卡片。控件编辑草稿，保存后由服务器统一应用。</p>
 *
 * <p>控件全部是自绘的 {@link UiWidget}：列表类配置 → 编辑按钮，布尔 → 开关，
 * 枚举 → 循环切换按钮，数值 → 带校验的输入框。行内控件在每帧重新定位，
 * 滚出视口即隐藏并释放焦点。</p>
 */
public class EternalHeartConfigScreen extends UiScreen {

    // ===================== 布局常量 =====================
    private static final int MAX_PANEL_W = 800;
    private static final int MAX_PANEL_H = 570;
    private static final int HEADER = 52;
    private static final int FOOTER = 38;
    private static final int CAT_W = 138;
    private static final int CAT_ITEM_H = 24;
    private static final int CAT_STEP = 28;
    private static final int ROW_H = 38;
    private static final int ROW_GAP = 5;
    private static final int ROW_STEP = ROW_H + ROW_GAP;
    private static final int HEADER_ROW_H = 30;
    private static final int NUM_BOX_W = 122;

    // ===================== 面板几何 =====================
    private int contentX1, contentX2, contentTopY, contentBotY;
    private int sidebarX1, sidebarX2;

    // ===================== 状态 =====================
    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> visible = new ArrayList<>();
    private final List<String> categoryOrder = new ArrayList<>();
    private String selectedCategory = null;
    private String lastSearch = "";
    private Entry hoveredEntry;
    /** 内容区滚动（平滑滚动 + 滚动条可拖拽）。 */
    private final UiScroll contentScroll = new UiScroll();
    private int catScroll = 0, catScrollMax = 0;
    private int contentH = 0;
    private float[] catHover = new float[0];

    private UiTextField searchBox;
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

    public EternalHeartConfigScreen(long revision, Map<String, Object> values) {
        super(Component.translatable("config.eternal_heart.title"));
        baseRevision = revision;
        baseline.putAll(values);
        draft.putAll(values);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("config.eternal_heart." + key, args).getString();
    }

    // ============================================================
    //  构建
    // ============================================================

    @Override
    protected void init() {
        clearWidgets();
        headerH = HEADER;
        footerH = FOOTER;
        computeGeometry();
        entries.clear();
        categoryOrder.clear();
        catHover = new float[0];

        for (ForgeConfigSpec.ConfigValue<?> cv : collectConfigValues()) {
            String fieldName = fieldNameOf(cv);
            Entry entry = new Entry(this, cv, fieldName, prettyName(cv), categoryOf(cv), tomlKeyOf(cv));
            entries.add(entry);
            for (UiWidget widget : entry.widgets) add(widget);
            if (!categoryOrder.contains(entry.category)) categoryOrder.add(entry.category);
        }
        catHover = new float[categoryOrder.size() + 1];

        // 「饰品融合」分类顶部的固定入口：打开逐条开关界面。
        fusionEntryBtn = add(UiButton.secondary(0, 0, 76, 22,
                Component.translatable("config.eternal_heart.fusion.open"),
                () -> Minecraft.getInstance().setScreen(new FusionToggleScreen(this))));
        fusionEntryBtn.setVisible(false);
        fusionEntryBtn.setRowOwned(true);

        // 搜索框（header 右侧）
        int sw = Math.min(220, this.panelW / 3);
        searchBox = add(new UiTextField(this.panelX + this.panelW - sw - 16, this.panelY + 15, sw, 22)
                .setHint(Component.translatable("config.eternal_heart.search_hint"))
                .setMaxLength(32)
                .setText(lastSearch)
                .setResponder(text -> {
                    lastSearch = text;
                    if (!text.isBlank()) selectedCategory = null; // 输入搜索词 → 回到全局视图
                    applyFilter();
                }));

        // 底部操作按钮
        int footerY = this.panelY + this.panelH - FOOTER + 8;
        closeBtn = add(UiButton.primary(this.panelX + this.panelW - 16 - 118, footerY, 118, 22,
                Component.translatable("config.eternal_heart.save"), this::onClose));
        cancelBtn = add(UiButton.ghost(this.panelX + this.panelW - 16 - 118 - 8 - 72, footerY, 72, 22,
                Component.translatable("gui.cancel"), () -> Minecraft.getInstance().setScreen(null)));

        applyFilter();
    }

    /** 计算面板几何（居中 + 边界保护）。 */
    private void computeGeometry() {
        layoutPanel(MAX_PANEL_W, MAX_PANEL_H, 560, 400);
        contentTopY = panelY + HEADER;
        contentBotY = panelY + panelH - FOOTER;
        sidebarX1 = panelX + 10;
        sidebarX2 = panelX + CAT_W - 8;
        contentX1 = panelX + CAT_W + 12;
        contentX2 = panelX + panelW - 14; // 右侧留出滚动条
    }

    /** 根据搜索词 / 选中分类重建可见列表，并同步内容总高。 */
    private void applyFilter() {
        visible.clear();
        // 过滤结果变化：统一隐藏全部行控件并清理焦点（只在这里做，避免每帧清掉用户刚点的输入框）
        for (Entry entry : entries) {
            for (UiWidget widget : entry.widgets) {
                widget.setVisible(false);
                if (getFocusedWidget() == widget) focusWidget(null);
            }
        }
        String query = lastSearch == null ? "" : lastSearch.trim().toLowerCase(Locale.ROOT);
        boolean searchMode = !query.isEmpty();
        for (Entry entry : entries) {
            if (searchMode ? entry.matches(query)
                    : (selectedCategory == null || entry.category.equals(selectedCategory))) {
                visible.add(entry);
            }
        }
        // 内容总高（「全部」/ 搜索视图含分类标题行）
        contentH = 10;
        if (selectedCategory == null) {
            String previous = null;
            for (Entry entry : visible) {
                if (!entry.category.equals(previous)) {
                    contentH += HEADER_ROW_H;
                    previous = entry.category;
                }
                contentH += ROW_STEP;
            }
        } else {
            contentH += visible.size() * ROW_STEP;
        }
        if (showFusionEntryRow()) contentH += ROW_STEP;
        contentScroll.jumpTo(0);
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

    /** 滚动边界钳制（每帧调用，廉价）。 */
    private void recalcScroll() {
        int viewH = contentBotY - contentTopY;
        contentScroll.layout(viewH, contentH);
        int listTop = contentTopY + 28; // 分类栏小标题占位
        catScrollMax = Math.max(0, (1 + categoryOrder.size()) * CAT_STEP - (contentBotY - listTop));
        if (catScroll > catScrollMax) catScroll = catScrollMax;
        if (catScroll < 0) catScroll = 0;
    }

    // ============================================================
    //  渲染
    // ============================================================

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partial) {
        recalcScroll();
        contentScroll.tick();
        float dt = frameDelta();

        drawTopBar(g);
        drawSidebar(g, mouseX, mouseY, dt);

        // 内容区：只在裁剪区内绘制行卡片与行控件
        g.enableScissor(contentX1, contentTopY, contentX2, contentBotY);
        renderRows(g, mouseX, mouseY, dt);
        for (UiWidget widget : widgets()) {
            if (widget.isRowOwned() && widget.isVisible()) widget.render(g, mouseX, mouseY, partial);
        }
        g.disableScissor();

        contentScroll.renderBar(g, panelX + panelW - 9, contentTopY + 6, contentBotY - 6);
        drawFooter(g, mouseX, mouseY);

        // 常驻控件（搜索框 / 底部按钮）在裁剪区外绘制
        for (UiWidget widget : widgets()) {
            if (!widget.isRowOwned() && widget.isVisible()) widget.render(g, mouseX, mouseY, partial);
        }

        if (hoveredEntry != null) tooltip(tooltipFor(hoveredEntry));
    }

    /** 左上标题 + 副标题（搜索框为控件，单独绘制）。 */
    private void drawTopBar(GuiGraphics g) {
        int searchLeft = searchBox.getX();
        int subtitleWidth = Math.max(80, searchLeft - (panelX + 16) - 16);
        drawHeader(g, Component.translatable("config.eternal_heart.title"),
                Component.literal(trim(tr("subtitle"), subtitleWidth)), HEADER);
    }

    /** 左侧分类导航栏（手动绘制，命中检测见 onMouseClicked）。 */
    private void drawSidebar(GuiGraphics g, int mx, int my, float dt) {
        int listTop = contentTopY + 28;
        // 侧栏底板：比主面板更深一档，形成分区
        UiTheme.roundRect(g, panelX + 6, contentTopY + 6, CAT_W - 12,
                contentBotY - contentTopY - 12, UiTheme.R_MD, UiTheme.PANEL_DEEP);
        g.drawString(this.font, tr("categories"), panelX + 18, contentTopY + 10,
                UiTheme.TEXT_MUTED, false);

        int total = 1 + categoryOrder.size();
        for (int i = 0; i < total; i++) {
            int itemY = listTop - catScroll + i * CAT_STEP;
            if (itemY + CAT_ITEM_H <= contentTopY + 26 || itemY >= contentBotY) continue;

            boolean selected;
            if (i == 0) selected = selectedCategory == null;
            else selected = categoryOrder.get(i - 1).equals(selectedCategory);
            boolean hovered = !selected && mx >= sidebarX1 && mx < sidebarX2
                    && my >= itemY && my < itemY + CAT_ITEM_H;

            float target = selected ? 1f : (hovered ? 0.55f : 0f);
            if (i < catHover.length) {
                catHover[i] = catHover[i] + (target - catHover[i]) * (1f - (float) Math.exp(-18f * dt));
            }
            float heat = i < catHover.length ? catHover[i] : target;

            if (heat > 0.02f) {
                int bg = selected
                        ? UiTheme.mix(0xFF16241F, UiTheme.ACCENT_SOFT, 0.85f)
                        : UiTheme.withAlpha(0xFFFFFF, (int) (0x10 * heat));
                UiTheme.roundRect(g, sidebarX1, itemY, sidebarX2 - sidebarX1, CAT_ITEM_H, 5, bg);
            }
            if (selected) {
                UiTheme.roundRect(g, sidebarX1 + 2, itemY + 6, 3, CAT_ITEM_H - 12, 1, UiTheme.ACCENT);
            }
            String label = i == 0 ? tr("all") : categoryOrder.get(i - 1);
            g.drawString(this.font, trim(label, CAT_W - 40), sidebarX1 + 12, itemY + 8,
                    selected ? UiTheme.ACCENT : UiTheme.mix(UiTheme.TEXT_DIM, UiTheme.TEXT, heat * 0.6f), false);
        }

        // 分类过多时显示细滚动条
        if (catScrollMax > 0) {
            int trackTop = listTop;
            int trackH = contentBotY - listTop;
            UiTheme.roundRect(g, panelX + CAT_W - 14, trackTop, 3, trackH, 1, UiTheme.TRACK);
            int thumbH = Math.max(20, trackH * trackH / Math.max(trackH, total * CAT_STEP));
            int thumbY = trackTop + (trackH - thumbH) * catScroll / Math.max(1, catScrollMax);
            UiTheme.roundRect(g, panelX + CAT_W - 14, thumbY, 3, thumbH, 1, UiTheme.ACCENT_FAINT);
        }
    }

    /** 内容区主体：分类标题 + 卡片式配置行（在 scissor 内调用）。 */
    private void renderRows(GuiGraphics g, int mx, int my, float dt) {
        boolean showHeaders = selectedCategory == null;
        String previousCategory = null;
        int y = contentTopY - contentScroll.offset() + 10;
        hoveredEntry = null;

        fusionEntryBtn.setVisible(false);
        if (showFusionEntryRow()) {
            if (y + ROW_H > contentTopY && y < contentBotY) {
                boolean hover = mx >= contentX1 && mx < contentX2 && my >= y && my < y + ROW_H;
                UiTheme.card(g, contentX1, y, contentX2 - contentX1, ROW_H, 7, hover, true);
                int buttonX = contentX2 - 10 - fusionEntryBtn.getWidth();
                fusionEntryBtn.setPosition(buttonX, y + (ROW_H - fusionEntryBtn.getHeight()) / 2);
                fusionEntryBtn.setVisible(true);
                int nameW = buttonX - 10 - (contentX1 + 14);
                g.drawString(this.font, trim(tr("fusion.title"), nameW), contentX1 + 14, y + 8,
                        UiTheme.TEXT, false);
                g.drawString(this.font, trim(tr("fusion.row_desc"), nameW), contentX1 + 14, y + 22,
                        UiTheme.TEXT_MUTED, false);
            }
            y += ROW_STEP;
        }

        for (Entry entry : visible) {
            if (showHeaders && !entry.category.equals(previousCategory)) {
                if (y + HEADER_ROW_H > contentTopY && y < contentBotY) {
                    drawCategoryHeader(g, entry.category, y);
                }
                y += HEADER_ROW_H;
                previousCategory = entry.category;
            }
            entry.y = y;
            entry.visibleNow = y + ROW_H > contentTopY && y < contentBotY;
            if (entry.visibleNow) {
                boolean hover = mx >= contentX1 && mx < contentX2 && my >= y && my < y + ROW_H;
                entry.hover = entry.hover + ((hover ? 1f : 0f) - entry.hover)
                        * (1f - (float) Math.exp(-18f * dt));

                UiTheme.cardFade(g, contentX1, y, contentX2 - contentX1, ROW_H, 7, entry.hover, false);
                if (hover) {
                    UiTheme.roundRect(g, contentX1 + 3, y + 9, 3, ROW_H - 18, 1, UiTheme.ACCENT);
                }

                // 控件布局：从右往左放置（最右侧控件贴边）
                int right = contentX2 - 10;
                for (int i = entry.widgets.size() - 1; i >= 0; i--) {
                    UiWidget widget = entry.widgets.get(i);
                    right -= widget.getWidth();
                    widget.setPosition(right, y + (ROW_H - widget.getHeight()) / 2);
                    widget.setVisible(true);
                    widget.setEnabled(editable && !saving);
                }
                // 只在「名称 / 说明」区域显示悬浮提示，避免盖住右侧控件
                if (hover && mx < right - 4) hoveredEntry = entry;

                int leftmost = entry.widgets.isEmpty() ? contentX2 - 10 : right;
                int nameW = leftmost - 10 - (contentX1 + 14);
                g.drawString(this.font, trim(entry.name, nameW), contentX1 + 14, y + 8,
                        entry.invalid ? UiTheme.DANGER : UiTheme.TEXT, false);
                if (!entry.description.isBlank()) {
                    g.drawString(this.font, trim(entry.description, nameW), contentX1 + 14, y + 22,
                            UiTheme.TEXT_MUTED, false);
                }
            } else {
                // 滚出视口的行：隐藏控件并清理焦点（不影响视口内正在编辑的输入框）
                for (UiWidget widget : entry.widgets) {
                    widget.setVisible(false);
                    if (getFocusedWidget() == widget) focusWidget(null);
                }
            }
            y += ROW_STEP;
        }

        if (visible.isEmpty()) {
            drawEmpty(g, tr("no_results"), contentX1, contentTopY, contentX2, contentBotY);
        }
    }

    /** 分类标题行：青绿小标题 + 右侧渐隐装饰线。 */
    private void drawCategoryHeader(GuiGraphics g, String category, int y) {
        g.drawString(this.font, category, contentX1 + 4, y + 12, UiTheme.ACCENT_DIM, false);
        int textX = contentX1 + 12 + this.font.width(category);
        UiTheme.hGradient(g, textX, contentX2 - 8, y + 16, 0x505EEAD4, 0x005EEAD4);
    }

    /** footer：左侧状态文字 + 右侧按钮。 */
    private void drawFooter(GuiGraphics g, int mx, int my) {
        drawFooterDivider(g, FOOTER);
        String status = statusKey.isEmpty() ? tr("count", visible.size()) : tr("status." + statusKey);
        int color = switch (statusKey) {
            case "" -> UiTheme.TEXT_MUTED;
            case "draft", "saving", "opened", "sync", "saved" -> UiTheme.ACCENT_DIM;
            case "invalid", "save_failed", "timeout", "conflict", "forbidden" -> UiTheme.WARN;
            default -> UiTheme.TEXT_DIM;
        };
        // 状态点：给 footer 一个视觉锚点
        int dotY = panelY + panelH - FOOTER + 19;
        UiTheme.roundRect(g, panelX + 16, dotY, 5, 5, 2, color);
        g.drawString(this.font, trim(status, panelW - 280), panelX + 27, panelY + panelH - FOOTER + 15,
                color, false);
    }

    // ============================================================
    //  悬浮提示
    // ============================================================

    /**
     * 每个配置行的悬浮说明：名称 + 说明 + 当前值 / 默认值。
     *
     * <p>说明文字优先取语言键 {@code config.eternal_heart.desc.<字段名>}，
     * 找不到时回退到配置 schema 里的注释（toml 中那段中文说明）。</p>
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
            String comment = commentOf(entry.cv);
            if (comment != null) {
                for (String line : comment.split("\n")) {
                    if (line.isBlank()) continue;
                    // Forge 会为 defineInRange 自动追加 "Range: min ~ max"；本项目把上限放开到
                    // ±Double.MAX_VALUE / Integer.MAX_VALUE，显示成天文数字没有可读性 → 换成人话
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

    // ============================================================
    //  输入处理
    // ============================================================

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX, my = (int) mouseY;
        // 滚动条：滑块拖动 / 点轨道跳转（与列表页同一套 UiScroll）
        if (button == 0 && contentScroll.mouseClicked(mouseX, mouseY,
                panelX + panelW - 9, contentTopY + 6, contentBotY - 6)) {
            return true;
        }
        // 左侧分类栏命中检测
        int listTop = contentTopY + 28;
        if (button == 0 && mx >= sidebarX1 && mx < sidebarX2
                && my >= listTop && my < contentBotY) {
            int relative = my - listTop + catScroll;
            int index = relative / CAT_STEP;
            if (index >= 0 && index < 1 + categoryOrder.size() && relative % CAT_STEP < CAT_ITEM_H) {
                if (index == 0) {
                    selectedCategory = null;
                } else {
                    String category = categoryOrder.get(index - 1);
                    // 再次点击已选分类 → 取消选择回全部
                    selectedCategory = category.equals(selectedCategory) ? null : category;
                    if (selectedCategory != null) searchBox.setText(""); // 清空搜索（responder 幂等）
                }
                applyFilter();
                return true;
            }
        }
        // 「饰品融合」分类顶部的入口行：点整行也能打开开关界面
        if (button == 0 && showFusionEntryRow()
                && mx >= contentX1 && mx < contentX2 && my >= contentTopY && my < contentBotY) {
            int rowY = contentTopY - contentScroll.offset() + 10;
            if (my >= rowY && my < rowY + ROW_H) {
                Minecraft.getInstance().setScreen(new FusionToggleScreen(this));
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean onMouseScrolled(double mouseX, double mouseY, double delta) {
        // 鼠标在左栏 → 滚动分类栏；否则滚动内容区
        if (mouseX >= panelX && mouseX < panelX + CAT_W && mouseY >= contentTopY && mouseY < contentBotY) {
            catScroll = (int) Math.max(0, Math.min(catScrollMax, catScroll - delta * CAT_STEP));
        } else {
            contentScroll.scrollBy(delta, ROW_STEP);
        }
        return true;
    }

    @Override
    protected boolean onMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return contentScroll.mouseDragged(mouseY, contentTopY + 6, contentBotY - 6);
    }

    @Override
    protected boolean onMouseReleased(double mouseX, double mouseY, int button) {
        contentScroll.mouseReleased();
        return false;
    }

    /** 数值输入框回车 = 提交并失焦（格式规整由 {@link #onFocusChanged} 统一执行）。 */
    @Override
    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)
                && getFocusedWidget() instanceof UiTextField field && field != searchBox) {
            focusWidget(null);
            return true;
        }
        return false;
    }

    /** 行控件的裁剪区：内容区之外一律不派发点击（避免点到被滚上去的半截行）。 */
    @Override
    protected boolean isOutsideRowClip(double mouseX, double mouseY) {
        return mouseX < contentX1 || mouseX >= contentX2 || mouseY < contentTopY || mouseY >= contentBotY;
    }

    /** 数值输入框失焦：把可能残缺或非法的文字规整为当前配置值的标准格式。 */
    @Override
    protected void onFocusChanged(UiWidget previous, UiWidget current) {
        if (previous instanceof UiTextField field) {
            for (Entry entry : entries) {
                if (entry.box == field) {
                    entry.syncBoxFromValue();
                    break;
                }
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        closeBtn.setEnabled(editable && !saving);
        cancelBtn.setEnabled(!saving);
        if (saving && ++savingTicks > 200) {
            saving = false;
            statusKey = "timeout";
        }
    }

    // ============================================================
    //  配置收集与元数据
    // ============================================================

    private List<ForgeConfigSpec.ConfigValue<?>> collectConfigValues() {
        // 列表类配置（黑白名单）同样进入面板：由 Entry 渲染为「编辑」按钮 + 选择器
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

    /** toml 键名（如 attackDamage，用于搜索匹配）。 */
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
            if (!Objects.equals(value, baseline.get(key))) patch.put(key, value);
        });
        return patch;
    }

    @Override
    public void onClose() {
        if (saving) return;
        if (!editable) {
            super.onClose();
            return;
        }
        if (entries.stream().anyMatch(e -> e.invalid)) {
            statusKey = "invalid";
            return;
        }
        Map<String, Object> patch = changes();
        if (patch.isEmpty()) {
            super.onClose();
            return;
        }

        saving = true;
        savingTicks = 0;
        statusKey = "saving";
        ConfigNetwork.submit(baseRevision, patch);
    }

    /**
     * 打开列表配置的可视化编辑器。
     *
     * <p>按配置项分派：黑白名单 → 效果选择器；自定义属性 → 属性选择器；
     * 配方 / 仪式 → 图形化编辑器；饰品与槽位 → 自动读取整合包条目的选择器。
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
            for (Entry entry : entries) entry.refresh();
        };
        Minecraft.getInstance().setScreen(switch (fieldName) {
            case "EXTRA_ATTRIBUTES" -> new AttributeListScreen(this, initial, onChange);
            // 自由文本列表（配方 JSON）：不能交给效果选择器——它会把非 ID 文本当效果处理
            case "CUSTOM_RECIPES" -> new RecipeEditorScreen(this, initial, onChange);
            case "CUSTOM_RITUALS" -> new RitualEditorScreen(this, initial, onChange);
            // 附加被动效果：每条还带等级与时长，走专用选择器（支持其它模组的效果）
            case "PASSIVE_EXTRA_EFFECTS" -> new PassiveEffectScreen(this, initial, onChange);
            // 豁免饰品 / 槽位扩容：都从整合包里自动读取可选条目，点选加入后填数量
            case "REVELATIONFIX_EXTRA_ITEMS" -> new CurioListScreen(this, initial, onChange);
            case "CURIOS_SLOT_EXPANSION" -> new CurioSlotScreen(this, initial, onChange);
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
        for (Entry entry : entries) entry.refresh();
    }

    // ============================================================
    //  配置行
    // ============================================================

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

        final List<UiWidget> widgets = new ArrayList<>();
        UiTextField box;
        UiButton button;
        UiSwitch toggle;
        boolean invalid;
        boolean listEntry;
        boolean boxUpdating;
        int y;
        boolean visibleNow;
        float hover;

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
                button = new UiButton(0, 0, 112, 22, buttonText(),
                        UiButton.Style.SECONDARY, () -> screen.openListEditor(fieldName));
                markRowOwned(button);
                widgets.add(button);
            } else if (value instanceof Boolean) {
                toggle = new UiSwitch(0, 0,
                        () -> Boolean.TRUE.equals(screen.draft.get(fieldName)),
                        next -> {
                            ConfigValues.validate(fieldName, next);
                            screen.draft.put(fieldName, next);
                            screen.statusKey = "draft";
                        });
                markRowOwned(toggle);
                widgets.add(toggle);
            } else if (value instanceof Enum<?>) {
                button = new UiButton(0, 0, 112, 22, buttonText(),
                        UiButton.Style.SECONDARY, () -> {
                    Object current = screen.draft.get(fieldName);
                    Object[] constants = current.getClass().getEnumConstants();
                    Object next = constants[(((Enum<?>) current).ordinal() + 1) % constants.length];
                    ConfigValues.validate(fieldName, next);
                    screen.draft.put(fieldName, next);
                    button.setMessage(buttonText());
                    screen.statusKey = "draft";
                });
                markRowOwned(button);
                widgets.add(button);
            } else if (value instanceof Number number) {
                box = new UiTextField(0, 0, NUM_BOX_W, 22)
                        .setMaxLength(48)
                        // 允许科学计数法与符号中间态：Numbers.format 对极大值输出 1.798E308 这类紧凑写法
                        .setFilter(text -> text.isEmpty()
                                || text.matches("[-+]?(\\d+\\.?\\d*|\\.\\d*)?([eE][-+]?\\d*)?"))
                        .setText(fmt(ConfigValues.toDisplay(fieldName, number)))
                        .selectAllOnFocus()
                        .setResponder(text -> {
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
                markRowOwned(box);
                widgets.add(box);
            }
        }

        /** 标记为「跟随行滚动的控件」，配置面板据此决定在裁剪区内还是外绘制。 */
        private void markRowOwned(UiWidget widget) {
            widget.setRowOwned(true);
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
            Object value = screen.draft.get(fieldName);
            if (!(value instanceof Number number)) return;
            boxUpdating = true;
            box.setText(fmt(ConfigValues.toDisplay(fieldName, number)));
            boxUpdating = false;
        }

        void refresh() {
            if (button != null) button.setMessage(buttonText());
            syncBoxFromValue();
        }
    }
}
