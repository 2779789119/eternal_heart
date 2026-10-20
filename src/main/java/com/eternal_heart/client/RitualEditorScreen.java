package com.eternal_heart.client;

import com.eternal_heart.client.ui.UiButton;
import com.eternal_heart.client.ui.UiEditBox;
import com.eternal_heart.client.ui.UiScreen;
import com.eternal_heart.client.ui.UiTheme;
import com.eternal_heart.features.RecipeText;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.RegistryManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 自定义仪式配方的**图形化编辑器** —— 诡厄巫法（Goety）暗黑祭坛仪式。
 *
 * <p>界面：仪式流派下拉（游戏里显示的仪式名）+ 配方工厂类型下拉 + 激活物槽 + 产出槽（含数量）
 * + 基座材料网格（按需扩展，滚轮翻看）+ 灵魂 / 时长 + 随类型变化的额外字段 + 限制条件（献祭）
 * + 已配置仪式列表。</p>
 *
 * <p><b>限制条件</b>：{@code entity_to_sacrifice} 与工厂类型无关，任何仪式都可要求献祭某个实体标签
 * （如 {@code #minecraft:raiders}），不填即不要求。</p>
 *
 * <p><b>流派</b>（{@code craftType}）与 <b>工厂类型</b>（{@code ritual_type}）两类都在运行时
 * 动态读取 Goety，因此自动包含附属模组新增的类型；未安装 Goety 时退回官方列表。</p>
 *
 * <p>生成的结果仍是 {@code ritual: …} 简化语法文本，与手写、配置文件完全一致 ——
 * 图形编辑只是把这段文本"点"出来。含标签（{@code #tag}）的仪式无法还原成具体物品，
 * 会提示改用文本模式。</p>
 */
public class RitualEditorScreen extends UiScreen {

    // ===================== 布局 =====================
    private static final int CELL = 30;
    private static final int GAP = 3;
    private static final int ROW_H = 22;
    private static final int ROW_STEP = 26;
    private static final int HEADER_H = 52;
    private static final int FOOTER_H = 38;

    /**
     * 基座材料槽位——Goety 不限制配方材料数量：{@code Ritual.getPedestals()} 扫描祭坛
     * {@code MainConfig.RitualRange} 范围内的全部基座方块，配方 ingredients 长度没有上限。
     * 因此这里按需扩展（每次加一行），视口只显示 {@link #GRID_VISIBLE_ROWS} 行，其余滚轮翻看。
     */
    private static final int GRID_COLS = 4;
    private static final int GRID_VISIBLE_ROWS = 2;
    private static final int GRID_MIN = 8;
    private static final int GRID_MAX = 64;
    /** 下拉框一行的高度与最多同时显示的行数。 */
    private static final int DROP_H = 20;
    private static final int DROP_ROW = 18;
    private static final int DROP_MAX_ROWS = 9;

    /** 额外字段（语义名 == RecipeText 的键名）→ 标签语言键。 */
    private static final Map<String, String> EXTRA_LABELS = Map.of(
            "summon", "config.eternal_heart.ritual_editor.summon",
            "summon_life", "config.eternal_heart.ritual_editor.summon_life",
            "convert", "config.eternal_heart.ritual_editor.convert_tag",
            "convert_into", "config.eternal_heart.ritual_editor.convert_into",
            "enchant", "config.eternal_heart.ritual_editor.enchant",
            "xp", "config.eternal_heart.ritual_editor.xp",
            "locate", "config.eternal_heart.ritual_editor.locate",
            "locate_name", "config.eternal_heart.ritual_editor.locate_name");

    /** 未安装 Goety 时的官方流派兜底（craftType）。 */
    private static final List<String> FALLBACK_CRAFT = List.of(
            "animation", "necroturgy", "forge", "magic", "adept_nether", "expert_nether",
            "sabbath", "end", "sky", "storm", "geoturgy", "frost", "deep", "overgrown", "divination");

    /** 未安装 Goety 时的官方工厂类型兜底（ritual_type）。 */
    private static final List<String> FALLBACK_FACTORY = List.of(
            "goety:craft", "goety:summon", "goety:summon_tamed", "goety:summon_tamed_no_variant",
            "goety:convert", "goety:convert_tamed", "goety:convert_complete_tamed",
            "goety:enchant", "goety:teleport", "goety:locate");

    // ===================== 状态 =====================
    private final Screen parent;
    private final List<String> entries;
    private final Consumer<List<String>> onChange;
    /** 基座材料槽位——按需扩展的动态列表（Goety 对 ingredients 数量没有上限）。 */
    private final List<ItemStack> grid = new ArrayList<>();

    private ItemStack activation = ItemStack.EMPTY;
    private ItemStack output = ItemStack.EMPTY;
    private int outputCount = 1;
    private int selected = -1;
    private int scroll, scrollMax;
    /** 基座材料网格的滚动行偏移（0 = 显示最上面一行）。 */
    private int gridScroll;

    private Dropdown craftDrop;
    private Dropdown factoryDrop;
    private UiEditBox soulBox;
    private UiEditBox timeBox;
    private UiEditBox extra1Box;
    private UiEditBox extra2Box;
    /** 限制条件：献祭实体标签 / 显示名（不依赖工厂类型，任何仪式都可设置）。 */
    private UiEditBox sacTagBox;
    private UiEditBox sacNameBox;
    /** 当前工厂类型下额外字段的语义名（null = 隐藏该字段）。 */
    private String extra1Kind;
    private String extra2Kind;

    private int actX, actY, resX, resY, countX;
    private int gridX, gridY;
    private int listX1, listX2, listTop, listBot;

    public RitualEditorScreen(Screen parent, List<String> initial, Consumer<List<String>> onChange) {
        super(Component.translatable("config.eternal_heart.ritual_editor.title"));
        this.parent = parent;
        this.entries = new ArrayList<>(initial);
        this.onChange = onChange;
        ensureGridSlots(GRID_MIN);
    }

    // ============================================================
    //  布局
    // ============================================================

    @Override
    protected void init() {
        layoutPanel(660, 540, 470, 420);

        int contentTop = panelY + HEADER_H;
        int rowY = contentTop + 40;

        actX = panelX + 20;
        actY = rowY;
        resX = actX + 44;
        resY = rowY;
        countX = resX + 38;
        gridX = panelX + 20;
        gridY = rowY + 52;

        int rightX = panelX + 208;
        int rightW = Math.max(180, panelW - 208 - 20);

        // 流派：第 0 项为空（不指定），其后是全部 craftType
        List<String> crafts = new ArrayList<>();
        crafts.add("");
        crafts.addAll(craftTypes());
        craftDrop = new Dropdown(rightX, rowY, rightW, DROP_H, crafts, 0,
                "config.eternal_heart.ritual_type.");

        List<String> factories = factoryTypes();
        factoryDrop = new Dropdown(rightX, rowY + 34, rightW, DROP_H, factories,
                Math.max(0, factories.indexOf("goety:craft")), "config.eternal_heart.ritual_factory.");

        soulBox = field(rightX, rowY + 68, 96, "\\d*");
        timeBox = field(rightX + rightW - 96, rowY + 68, 96, "\\d*");
        extra1Box = field(rightX, rowY + 102, rightW, null);
        extra2Box = field(rightX, rowY + 136, rightW, null);
        applyFactoryFields();

        // 限制条件（献祭）：与工厂类型无关，任何仪式都可要求献祭；放在基座网格下方
        int condLabelY = gridY + gridViewportH() + 12;
        sacTagBox = field(actX, condLabelY + 12, 170, null);
        sacNameBox = field(actX, condLabelY + 42, 170, null);
        this.condLabelY = condLabelY;

        // 产出数量
        this.addRenderableWidget(UiButton.secondary(countX, resY + 5, 18, 18,
                Component.literal("-"), () -> outputCount = Math.max(1, outputCount - 1)));
        this.addRenderableWidget(UiButton.secondary(countX + 46, resY + 5, 18, 18,
                Component.literal("+"), () -> outputCount = Math.min(64, outputCount + 1)));

        int listTopY = Math.max(sacNameBox.getY() + 20, rowY + 156) + 14;
        listX1 = panelX + 20;
        listX2 = panelX + panelW - 20;
        listTop = listTopY;
        listBot = panelY + panelH - FOOTER_H - 8;

        // 底部操作
        int buttonY = panelY + panelH - FOOTER_H + 8;
        this.addRenderableWidget(UiButton.primary(panelX + 20, buttonY, 54, 22,
                Component.translatable("config.eternal_heart.ritual_editor.add"), () -> commit(false)));
        this.addRenderableWidget(UiButton.secondary(panelX + 80, buttonY, 72, 22,
                Component.translatable("config.eternal_heart.ritual_editor.update"), () -> commit(true)));
        this.addRenderableWidget(UiButton.danger(panelX + 158, buttonY, 72, 22,
                Component.translatable("config.eternal_heart.ritual_editor.remove"), this::removeSelected));
        this.addRenderableWidget(UiButton.ghost(panelX + 236, buttonY, 70, 22,
                Component.translatable("config.eternal_heart.ritual_editor.text_mode"), this::openTextMode));
        this.addRenderableWidget(UiButton.secondary(panelX + panelW - 20 - 60, buttonY, 60, 22,
                Component.translatable("config.eternal_heart.ritual_editor.done"), this::onClose));
    }

    private int condLabelY;

    private UiEditBox field(int x, int y, int width, String filter) {
        UiEditBox box = new UiEditBox(this.font, x, y, width, 20, Component.empty());
        box.setMaxLength(200);
        if (filter != null) box.setFilter(text -> text.isEmpty() || text.matches(filter));
        this.addRenderableWidget(box);
        return box;
    }

    // ============================================================
    //  类型数据（运行时动态读取 Goety，未装则退回官方列表）
    // ============================================================

    private static List<String> CRAFT_CACHE;
    private static List<String> FACTORY_CACHE;

    private static List<String> craftTypes() {
        if (CRAFT_CACHE == null) {
            List<String> ids = queryGoetyCraftTypes();
            if (ids.isEmpty()) ids.addAll(FALLBACK_CRAFT);
            ids.sort(Comparator.naturalOrder());
            CRAFT_CACHE = ids;
        }
        return CRAFT_CACHE;
    }

    private static List<String> factoryTypes() {
        if (FACTORY_CACHE == null) {
            List<String> ids = queryGoetyFactoryTypes();
            if (ids.isEmpty()) ids.addAll(FALLBACK_FACTORY);
            ids.sort(Comparator.naturalOrder());
            FACTORY_CACHE = ids;
        }
        return FACTORY_CACHE;
    }

    /** 反射读取 {@code RitualType.getRitualTypeList()}，自动包含附属模组注册的流派。 */
    private static List<String> queryGoetyCraftTypes() {
        List<String> ids = new ArrayList<>();
        try {
            Class<?> type = Class.forName("com.Polarice3.Goety.api.ritual.RitualType");
            Object value = type.getMethod("getRitualTypeList").invoke(null);
            if (value instanceof Map<?, ?> map) {
                for (Object key : map.keySet()) if (key != null) ids.add(String.valueOf(key));
            }
        } catch (Throwable ignored) {
            // 未安装 Goety 或版本不同 —— 用官方列表兜底
        }
        return ids;
    }

    /** 按注册表键读取 {@code goety:ritual_factory}，自动包含附属模组注册的工厂类型。 */
    private static List<String> queryGoetyFactoryTypes() {
        List<String> ids = new ArrayList<>();
        try {
            ResourceKey<Registry<Object>> key =
                    ResourceKey.createRegistryKey(new ResourceLocation("goety", "ritual_factory"));
            var registry = RegistryManager.ACTIVE.getRegistry(key);
            if (registry != null) {
                for (ResourceLocation id : registry.getKeys()) ids.add(id.toString());
            }
        } catch (Throwable ignored) {
            // 同上
        }
        return ids;
    }

    /** 工厂类型决定需要哪些额外字段。 */
    private void applyFactoryFields() {
        String path = pathOf(factoryDrop.id());
        String first = null;
        String second = null;
        if (path.startsWith("summon")) {
            first = "summon";
            second = "summon_life";
        } else if (path.startsWith("convert")) {
            first = "convert";
            second = "convert_into";
        } else if (path.equals("enchant")) {
            first = "enchant";
            second = "xp";
        } else if (path.equals("locate")) {
            first = "locate";
            second = "locate_name";
        }
        if (!Objects.equals(first, extra1Kind)) {
            extra1Kind = first;
            if (extra1Box != null) extra1Box.setValue("");
        }
        if (!Objects.equals(second, extra2Kind)) {
            extra2Kind = second;
            if (extra2Box != null) extra2Box.setValue("");
        }
        if (extra1Box != null) {
            extra1Box.setVisible(extra1Kind != null);
            extra1Box.setFilter(filterOf(extra1Kind));
        }
        if (extra2Box != null) {
            extra2Box.setVisible(extra2Kind != null);
            extra2Box.setFilter(filterOf(extra2Kind));
        }
    }

    private static Predicate<String> filterOf(String kind) {
        if ("summon_life".equals(kind) || "xp".equals(kind)) {
            return text -> text.isEmpty() || text.matches("\\d*");
        }
        // 不能返回 null：EditBox.setFilter(null) 会清掉默认过滤器，随后 setValue 触发 NPE
        return text -> true;
    }

    private static String pathOf(String id) {
        if (id == null) return "";
        int colon = id.indexOf(':');
        return (colon >= 0 ? id.substring(colon + 1) : id).toLowerCase(Locale.ROOT);
    }

    // ============================================================
    //  渲染
    // ============================================================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        drawPanel(g);
        drawHeader(g, this.title, null, HEADER_H);

        // 字段标签
        label(g, "config.eternal_heart.ritual_editor.activation", actX, actY - 12);
        label(g, "config.eternal_heart.ritual_editor.output", resX, resY - 12);
        label(g, Component.translatable("config.eternal_heart.ritual_editor.ingredients", grid.size()),
                gridX, gridY - 12);
        label(g, "config.eternal_heart.ritual_editor.craft_type", craftDrop.x(), craftDrop.y() - 12);
        label(g, "config.eternal_heart.ritual_editor.factory_type", factoryDrop.x(), factoryDrop.y() - 12);
        label(g, "config.eternal_heart.ritual_editor.soul", soulBox.getX(), soulBox.getY() - 12);
        label(g, "config.eternal_heart.ritual_editor.time", timeBox.getX(), timeBox.getY() - 12);
        if (extra1Kind != null) label(g, EXTRA_LABELS.get(extra1Kind), extra1Box.getX(), extra1Box.getY() - 12);
        if (extra2Kind != null) label(g, EXTRA_LABELS.get(extra2Kind), extra2Box.getX(), extra2Box.getY() - 12);

        // 限制条件（献祭）
        label(g, "config.eternal_heart.ritual_editor.conditions", actX, condLabelY);
        label(g, "config.eternal_heart.ritual_editor.sacrifice", sacTagBox.getX(), sacTagBox.getY() - 12);
        label(g, "config.eternal_heart.ritual_editor.sacrifice_name", sacNameBox.getX(), sacNameBox.getY() - 12);

        // 激活物 / 产出
        slot(g, actX, actY, activation, onActivation(mx, my));
        slot(g, resX, resY, output, onOutput(mx, my));
        g.drawString(this.font, Component.translatable("config.eternal_heart.ritual_editor.count", outputCount),
                countX + 24, resY + 10, UiTheme.TEXT);

        // 基座材料（视口固定 2 行，其余滚轮翻看）
        int hoveredCell = cellAt(mx, my);
        int gridW = GRID_COLS * (CELL + GAP) - GAP;
        int gridViewH = gridViewportH();
        g.enableScissor(gridX, gridY, gridX + gridW, gridY + gridViewH);
        for (int index = 0; index < grid.size(); index++) {
            int row = index / GRID_COLS;
            if (row < gridScroll || row >= gridScroll + GRID_VISIBLE_ROWS) continue;
            int x = gridX + (index % GRID_COLS) * (CELL + GAP);
            int y = gridY + (row - gridScroll) * (CELL + GAP);
            slot(g, x, y, grid.get(index), index == hoveredCell);
        }
        g.disableScissor();
        drawGridScrollbar(g);

        // 已配置列表
        g.drawString(this.font, Component.translatable("config.eternal_heart.ritual_editor.saved", entries.size()),
                listX1, listTop - 14, UiTheme.TEXT_DIM);
        g.enableScissor(listX1, listTop, listX2, listBot);
        int hovered = rowAt(mx, my);
        for (int index = 0; index < entries.size(); index++) {
            int rowY = listTop + index * ROW_STEP - scroll;
            if (rowY + ROW_H <= listTop || rowY >= listBot) continue;
            boolean active = index == selected;
            UiTheme.card(g, listX1, rowY, listX2 - listX1, ROW_H, 5, index == hovered, active);
            g.drawString(this.font, trim(entries.get(index), listX2 - listX1 - 24), listX1 + 12,
                    rowY + 7, active ? UiTheme.ACCENT : UiTheme.TEXT);
        }
        g.disableScissor();
        drawScrollbar(g, listTop, listBot, scroll, scrollMax, Math.max(1, entries.size() * ROW_STEP));
        if (entries.isEmpty()) {
            g.drawString(this.font, Component.translatable("config.eternal_heart.ritual_editor.no_rituals"),
                    listX1 + 4, listTop + 4, UiTheme.TEXT_MUTED, false);
        }

        drawFooterDivider(g, FOOTER_H);

        // 原生控件（输入框 / 按钮）
        super.render(g, mx, my, pt);

        // 下拉框（画在最上层，含展开列表）；展开的那个最后画，避免被另一个盖住
        if (craftDrop.isOpen()) {
            factoryDrop.render(g, mx, my);
            craftDrop.render(g, mx, my);
        } else {
            craftDrop.render(g, mx, my);
            factoryDrop.render(g, mx, my);
        }

        // 悬浮提示
        int cell = cellAt(mx, my);
        if (cell >= 0 && !grid.get(cell).isEmpty()) {
            g.renderTooltip(this.font, grid.get(cell).getHoverName(), mx, my);
        } else if (onActivation(mx, my) && !activation.isEmpty()) {
            g.renderTooltip(this.font, activation.getHoverName(), mx, my);
        } else if (onOutput(mx, my) && !output.isEmpty()) {
            g.renderTooltip(this.font, output.getHoverName(), mx, my);
        } else {
            String id = craftDrop.hoveredId(mx, my);
            if (id == null) id = factoryDrop.hoveredId(mx, my);
            if (id != null && !id.isEmpty()) {
                g.renderTooltip(this.font, Component.literal(id), mx, my);
            }
        }
    }

    private void slot(GuiGraphics g, int x, int y, ItemStack stack, boolean hovered) {
        UiTheme.slot(g, x, y, CELL, hovered);
        if (!stack.isEmpty()) g.renderItem(stack, x + 7, y + 7);
    }

    private void label(GuiGraphics g, String key, int x, int y) {
        g.drawString(this.font, Component.translatable(key), x, y, UiTheme.TEXT_DIM, false);
    }

    private void label(GuiGraphics g, Component text, int x, int y) {
        g.drawString(this.font, text, x, y, UiTheme.TEXT_DIM, false);
    }

    // ============================================================
    //  基座材料网格（按需扩展 + 滚轮翻看）
    // ============================================================

    private int gridRows() {
        return (grid.size() + GRID_COLS - 1) / GRID_COLS;
    }

    /** 视口像素高度：{@link #GRID_VISIBLE_ROWS} 行（末行不含行间距）。 */
    private int gridViewportH() {
        return GRID_VISIBLE_ROWS * (CELL + GAP) - GAP;
    }

    private int maxGridScroll() {
        return Math.max(0, gridRows() - GRID_VISIBLE_ROWS);
    }

    /** 保证至少 {@code slots} 个槽位（按整行向上取整，受 {@link #GRID_MIN}/{@link #GRID_MAX} 约束）。 */
    private void ensureGridSlots(int slots) {
        int target = Math.max(GRID_MIN, Math.min(GRID_MAX, ((slots + GRID_COLS - 1) / GRID_COLS) * GRID_COLS));
        while (grid.size() < target) grid.add(ItemStack.EMPTY);
    }

    /** 末槽已填满则追加一行空槽（上限 {@link #GRID_MAX}），供继续添加材料。 */
    private void growGridIfNeeded() {
        if (grid.isEmpty() || !grid.get(grid.size() - 1).isEmpty()) {
            ensureGridSlots(Math.min(GRID_MAX, grid.size() + GRID_COLS));
        }
        gridScroll = Math.min(gridScroll, maxGridScroll());
    }

    /** 清空后回收末尾空行（至少保留 {@link #GRID_MIN} 个槽位）。 */
    private void trimGrid() {
        while (grid.size() > GRID_MIN && grid.size() % GRID_COLS == 0) {
            boolean empty = true;
            for (int index = grid.size() - GRID_COLS; index < grid.size(); index++) {
                if (!grid.get(index).isEmpty()) {
                    empty = false;
                    break;
                }
            }
            if (!empty) break;
            grid.subList(grid.size() - GRID_COLS, grid.size()).clear();
        }
    }

    private void drawGridScrollbar(GuiGraphics g) {
        int max = maxGridScroll();
        if (max <= 0) return;
        int x = gridX + GRID_COLS * (CELL + GAP) - GAP + 4;
        int height = gridViewportH();
        UiTheme.roundRect(g, x, gridY, 3, height, 1, UiTheme.TRACK);
        int barH = Math.max(12, height * GRID_VISIBLE_ROWS / gridRows());
        int barY = gridY + (height - barH) * gridScroll / max;
        UiTheme.roundRect(g, x, barY, 3, barH, 1, UiTheme.ACCENT_DIM);
    }

    // ============================================================
    //  交互
    // ============================================================

    private int cellAt(double mouseX, double mouseY) {
        if (!overGrid(mouseX, mouseY)) return -1;
        int col = (int) ((mouseX - gridX) / (CELL + GAP));
        int row = (int) ((mouseY - gridY) / (CELL + GAP)) + gridScroll;
        if (col < 0 || col >= GRID_COLS) return -1;
        int index = row * GRID_COLS + col;
        return index >= 0 && index < grid.size() ? index : -1;
    }

    /** 鼠标是否落在基座网格视口内。 */
    private boolean overGrid(double mouseX, double mouseY) {
        int width = GRID_COLS * (CELL + GAP) - GAP;
        return mouseX >= gridX && mouseX <= gridX + width
                && mouseY >= gridY && mouseY <= gridY + gridViewportH();
    }

    private boolean onActivation(double mouseX, double mouseY) {
        return mouseX >= actX && mouseX <= actX + CELL && mouseY >= actY && mouseY <= actY + CELL;
    }

    private boolean onOutput(double mouseX, double mouseY) {
        return mouseX >= resX && mouseX <= resX + CELL && mouseY >= resY && mouseY <= resY + CELL;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (factoryDrop.mouseClicked(mouseX, mouseY)) {
            if (factoryDrop.isOpen()) craftDrop.close();
            applyFactoryFields();
            return true;
        }
        if (craftDrop.mouseClicked(mouseX, mouseY)) {
            if (craftDrop.isOpen()) factoryDrop.close();
            return true;
        }
        int cell = cellAt(mouseX, mouseY);
        if (cell >= 0) {
            if (button == 1) grid.set(cell, ItemStack.EMPTY); // 右键清空该格
            else pickItem(item -> {
                grid.set(cell, new ItemStack(item));
                growGridIfNeeded();
            });
            return true;
        }
        if (onActivation(mouseX, mouseY)) {
            if (button == 1) activation = ItemStack.EMPTY;
            else pickItem(item -> activation = new ItemStack(item));
            return true;
        }
        if (onOutput(mouseX, mouseY)) {
            if (button == 1) output = ItemStack.EMPTY;
            else pickItem(item -> output = new ItemStack(item));
            return true;
        }
        int index = rowAt(mouseX, mouseY);
        if (index >= 0) {
            selected = index;
            loadFrom(entries.get(index));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void pickItem(Consumer<Item> apply) {
        if (this.minecraft == null) return;
        this.minecraft.setScreen(new ItemPickerScreen(this, item -> {
            apply.accept(item);
            this.minecraft.setScreen(this);
        }));
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < listX1 || mouseX > listX2 || mouseY < listTop || mouseY > listBot) return -1;
        int index = (int) ((mouseY - listTop + scroll) / ROW_STEP);
        return index >= 0 && index < entries.size() ? index : -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (factoryDrop.mouseScrolled(mouseX, mouseY, delta)) return true;
        if (craftDrop.mouseScrolled(mouseX, mouseY, delta)) return true;
        // 悬停基座网格：翻看材料行
        if (overGrid(mouseX, mouseY) && maxGridScroll() > 0) {
            gridScroll = (int) Math.max(0, Math.min(maxGridScroll(), gridScroll - delta));
            return true;
        }
        scrollMax = Math.max(0, entries.size() * ROW_STEP - 6 - (listBot - listTop));
        scroll = (int) Math.max(0, Math.min(scrollMax, scroll - delta * ROW_STEP));
        return true;
    }

    // ============================================================
    //  生成 / 载入
    // ============================================================

    /** 把当前编辑内容生成 {@code ritual: …} 简化语法文本；内容不完整返回 null。 */
    private String toText() {
        if (activation.isEmpty()) return null;
        String factory = factoryDrop.id();
        String path = pathOf(factory);
        boolean needsResult = path.equals("craft") || path.equals("enchant");
        if (needsResult && output.isEmpty()) return null;

        List<String> parts = new ArrayList<>();
        if (!output.isEmpty()) {
            parts.add("result=" + shortId(output) + (outputCount > 1 ? " x" + outputCount : ""));
        }
        parts.add("activation=" + shortId(activation));

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack stack : grid) {
            if (!stack.isEmpty()) counts.merge(shortId(stack), 1, Integer::sum);
        }
        if (counts.isEmpty()) return null;
        StringBuilder ingredients = new StringBuilder();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (ingredients.length() > 0) ingredients.append(" + ");
            if (entry.getValue() > 1) ingredients.append(entry.getValue()).append('x').append(' ');
            ingredients.append(entry.getKey());
        }
        parts.add("ingredients=" + ingredients);

        parts.add("type=" + factory);
        if (!craftDrop.id().isBlank()) parts.add("craft=" + craftDrop.id());
        parts.add("soul=" + integerOr(soulBox.getValue(), 0));
        parts.add("time=" + integerOr(timeBox.getValue(), 30));
        addExtra(parts, extra1Kind, extra1Box);
        addExtra(parts, extra2Kind, extra2Box);

        // 限制条件：献祭（显示名缺省时由 RecipeText 用标签兜底，故仅在填写时输出）
        String sacTag = sacTagBox.getValue().trim();
        if (!sacTag.isEmpty()) {
            parts.add("sacrifice=" + sacTag);
            String sacName = sacNameBox.getValue().trim();
            if (!sacName.isEmpty()) parts.add("sacrifice_name=" + sacName);
        }
        return "ritual: " + String.join("; ", parts);
    }

    private void addExtra(List<String> parts, String kind, UiEditBox box) {
        if (kind == null || box == null || box.getValue().isBlank()) return;
        parts.add(kind + "=" + box.getValue().trim());
    }

    /** 把已配置的仪式载回编辑器（含标签的仪式无法还原成具体物品，会提示）。 */
    private void loadFrom(String text) {
        JsonObject json;
        try {
            json = RecipeText.parse(text);
        } catch (RuntimeException e) {
            json = null;
        }
        if (json == null || !"goety:ritual".equals(json.has("type") ? json.get("type").getAsString() : "")) {
            message("config.eternal_heart.ritual_editor.cannot_load");
            return;
        }
        clearDraft();
        boolean tagged = false;

        factoryDrop.select(json.has("ritual_type") ? json.get("ritual_type").getAsString() : "goety:craft");
        craftDrop.select(json.has("craftType") ? json.get("craftType").getAsString() : "");
        applyFactoryFields();

        // 产物
        if (json.has("result") && json.get("result").isJsonObject()) {
            JsonObject result = json.getAsJsonObject("result");
            if (!isDummy(result)) {
                Item item = itemOf(result);
                if (item != null) output = new ItemStack(item);
            }
            outputCount = result.has("count") ? Math.max(1, result.get("count").getAsInt()) : 1;
        }
        // 激活物
        if (json.has("activation_item")) {
            Item item = itemOf(json.getAsJsonObject("activation_item"));
            if (item != null) activation = new ItemStack(item);
            else tagged = true;
        }
        // 基座材料
        if (json.has("ingredients") && json.get("ingredients").isJsonArray()) {
            JsonArray ingredients = json.getAsJsonArray("ingredients");
            ensureGridSlots(ingredients.size());
            for (int index = 0; index < ingredients.size(); index++) {
                Item item = itemOf(ingredients.get(index).getAsJsonObject());
                if (item == null) {
                    tagged = true;
                    continue;
                }
                if (index < grid.size()) grid.set(index, new ItemStack(item));
                else tagged = true;
            }
            growGridIfNeeded();
        }
        // 通用字段
        soulBox.setValue(String.valueOf(json.has("soulCost") ? json.get("soulCost").getAsInt() : 0));
        timeBox.setValue(String.valueOf(json.has("duration") ? json.get("duration").getAsInt() : 30));
        // 类型相关字段
        fillExtra(extra1Kind, extra1Box, json);
        fillExtra(extra2Kind, extra2Box, json);
        // 限制条件：献祭（display_name 与标签相同时视为缺省，避免再次写回冗余字段）
        String sacTag = tagOf(json, "entity_to_sacrifice");
        sacTagBox.setValue(sacTag);
        String sacName = nameOf(json, "entity_to_sacrifice");
        sacNameBox.setValue(sacName.equals(sacTag) ? "" : sacName);
        if (tagged) message("config.eternal_heart.ritual_editor.tagged");
    }

    private void fillExtra(String kind, UiEditBox box, JsonObject json) {
        if (box == null || kind == null) return;
        switch (kind) {
            case "summon" -> box.setValue(stringOf(json, "entity_to_summon"));
            case "summon_life" -> box.setValue(json.has("summonLife")
                    ? String.valueOf(json.get("summonLife").getAsInt()) : "");
            case "convert" -> box.setValue(tagOf(json, "entity_to_convert"));
            case "convert_into" -> box.setValue(stringOf(json, "entity_to_convert_into"));
            case "enchant" -> box.setValue(stringOf(json, "enchantment"));
            case "xp" -> box.setValue(json.has("xpLevelCost")
                    ? String.valueOf(json.get("xpLevelCost").getAsInt()) : "");
            case "locate" -> box.setValue(tagOf(json, "structure_to_locate"));
            case "locate_name" -> box.setValue(nameOf(json, "structure_to_locate"));
            default -> box.setValue("");
        }
    }

    private String stringOf(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsString() : "";
    }

    private String tagOf(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonObject()) return "";
        return stringOf(json.getAsJsonObject(key), "tag");
    }

    private String nameOf(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonObject()) return "";
        return stringOf(json.getAsJsonObject(key), "display_name");
    }

    /** Goety 非合成类仪式的占位产物 {@code goety:jei_dummy/none}，编辑器中忽略。 */
    private boolean isDummy(JsonObject result) {
        if (!result.has("item")) return false;
        return result.get("item").getAsString().startsWith("goety:jei_dummy");
    }

    private Item itemOf(JsonObject ingredient) {
        if (ingredient == null) return null;
        if (ingredient.has("tag")) return null; // 标签无法还原成具体物品
        if (!ingredient.has("item")) return null;
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(ingredient.get("item").getAsString()));
        return item == Items.AIR ? null : item;
    }

    // ============================================================
    //  列表操作
    // ============================================================

    private void commit(boolean replaceSelected) {
        String text = toText();
        if (text == null) {
            message("config.eternal_heart.ritual_editor.incomplete");
            return;
        }
        if (replaceSelected && selected >= 0 && selected < entries.size()) {
            entries.set(selected, text);
        } else {
            entries.add(text);
            selected = entries.size() - 1;
        }
        clearDraft();
    }

    private void removeSelected() {
        if (selected < 0 || selected >= entries.size()) return;
        entries.remove(selected);
        if (selected >= entries.size()) selected = entries.size() - 1;
        clearDraft();
    }

    private void clearDraft() {
        for (int index = 0; index < grid.size(); index++) grid.set(index, ItemStack.EMPTY);
        trimGrid();
        gridScroll = 0;
        activation = ItemStack.EMPTY;
        output = ItemStack.EMPTY;
        outputCount = 1;
        if (craftDrop != null) craftDrop.select("");
        if (factoryDrop != null) factoryDrop.select("goety:craft");
        if (soulBox != null) soulBox.setValue("");
        if (timeBox != null) timeBox.setValue("");
        if (extra1Box != null) extra1Box.setValue("");
        if (extra2Box != null) extra2Box.setValue("");
        if (sacTagBox != null) sacTagBox.setValue("");
        if (sacNameBox != null) sacNameBox.setValue("");
        if (factoryDrop != null) applyFactoryFields();
    }

    private void openTextMode() {
        if (this.minecraft == null) return;
        this.minecraft.setScreen(new TextListScreen(this,
                "config.eternal_heart.text_list.title_rituals",
                "config.eternal_heart.text_list.hint_rituals", entries, updated -> {
            entries.clear();
            entries.addAll(updated);
        }));
    }

    private void message(String key) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(Component.translatable(key), true);
        }
    }

    private static int integerOr(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String shortId(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
    }

    @Override
    public void onClose() {
        onChange.accept(List.copyOf(entries));
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ============================================================
    //  下拉框
    // ============================================================

    /**
     * 简易下拉框：闭合时只画一行（当前值 + ▾），展开后覆盖在下层内容之上。
     *
     * <p>选项 id 由 {@code labelPrefix + 去命名空间后的 id} 取本地化名；取不到时退回
     * Goety 自带的 {@code jei.goety.craftType.*}，再退回原始 id —— 因此附属模组的
     * 新类型也能正常显示。</p>
     */
    private final class Dropdown {

        private final int x, y, w, h;
        private final List<String> ids;
        private final String labelPrefix;
        private int index;
        private boolean open;
        private int scroll;

        Dropdown(int x, int y, int w, int h, List<String> ids, int index, String labelPrefix) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.ids = ids;
            this.labelPrefix = labelPrefix;
            this.index = Math.max(0, Math.min(Math.max(0, ids.size() - 1), index));
        }

        int x() {
            return x;
        }

        int y() {
            return y;
        }

        String id() {
            return ids.isEmpty() ? "" : ids.get(index);
        }

        boolean isOpen() {
            return open;
        }

        void close() {
            open = false;
        }

        /** 选中指定 id；不存在时动态加入列表（例如附属模组或反解出的自定义值）。 */
        void select(String id) {
            if (ids.isEmpty()) return;
            int found = ids.indexOf(id);
            if (found < 0) {
                ids.add(id);
                ids.sort(Comparator.naturalOrder());
                found = ids.indexOf(id);
            }
            index = Math.max(0, found);
        }

        private int visibleRows() {
            return Math.min(DROP_MAX_ROWS, ids.size());
        }

        private int listTop() {
            return y + h + 3;
        }

        private int rowAt(double mouseX, double mouseY) {
            int top = listTop();
            if (mouseX < x || mouseX > x + w || mouseY < top) return -1;
            int row = (int) ((mouseY - top) / DROP_ROW) + scroll;
            return row >= 0 && row < ids.size() ? row : -1;
        }

        String hoveredId(double mouseX, double mouseY) {
            if (!open) return null;
            int row = rowAt(mouseX, mouseY);
            return row >= 0 ? ids.get(row) : null;
        }

        String label(String id) {
            if (id.isEmpty()) {
                return Component.translatable("config.eternal_heart.ritual_editor.none").getString();
            }
            String path = pathOf(id);
            String key = labelPrefix + path;
            String text = Component.translatable(key).getString();
            if (!text.equals(key)) return text;
            String goetyKey = "jei.goety.craftType." + id;
            String goety = Component.translatable(goetyKey).getString();
            if (!goety.equals(goetyKey)) return goety;
            return id;
        }

        boolean mouseClicked(double mouseX, double mouseY) {
            if (open) {
                int row = rowAt(mouseX, mouseY);
                if (row >= 0) index = row;
                open = false;
                return true; // 展开时点击任何位置都只负责选择 / 收起
            }
            if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
                open = true;
                scroll = Math.max(0, Math.min(Math.max(0, ids.size() - visibleRows()), index - visibleRows() / 2));
                return true;
            }
            return false;
        }

        boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (!open) return false;
            int max = Math.max(0, ids.size() - visibleRows());
            scroll = (int) Math.max(0, Math.min(max, scroll - delta));
            return true;
        }

        void render(GuiGraphics g, int mouseX, int mouseY) {
            // 闭合态：一行当前值
            UiTheme.input(g, x, y, w, h, open);
            g.drawString(font, trim(label(id()), w - 26), x + 7, y + 6, UiTheme.TEXT, false);
            g.drawString(font, open ? "▲" : "▼", x + w - 12, y + 6, UiTheme.ACCENT_DIM, false);

            if (!open) return;
            int top = listTop();
            int rows = visibleRows();
            int bottom = top + rows * DROP_ROW;
            UiTheme.roundRect(g, x + 2, top + 4, w, bottom - top, 6, 0x55000000);
            UiTheme.roundRect(g, x, top, w, bottom - top, 6, UiTheme.BORDER_STRONG);
            UiTheme.roundRect(g, x + 1, top + 1, w - 2, bottom - top - 2, 5, UiTheme.PANEL);
            int hovered = rowAt(mouseX, mouseY);
            for (int row = 0; row < rows; row++) {
                int option = scroll + row;
                if (option >= ids.size()) break;
                int rowY = top + row * DROP_ROW;
                if (option == index) {
                    UiTheme.roundRect(g, x + 4, rowY + 2, w - 8, DROP_ROW - 4, 4, UiTheme.ACCENT_SOFT);
                } else if (option == hovered) {
                    UiTheme.roundRect(g, x + 4, rowY + 2, w - 8, DROP_ROW - 4, 4, 0x14FFFFFF);
                }
                g.drawString(font, trim(label(ids.get(option)), w - 18), x + 9, rowY + 5,
                        option == index ? UiTheme.ACCENT : UiTheme.TEXT, false);
            }
        }
    }
}
