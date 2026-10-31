package com.eternal_heart.client;

import com.eternal_heart.client.ui.UiButton;
import com.eternal_heart.client.ui.UiDropdown;
import com.eternal_heart.client.ui.UiList;
import com.eternal_heart.client.ui.UiScreen;
import com.eternal_heart.client.ui.UiTextField;
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
 * <p>生成的结果仍是 {@code ritual: …} 简化语法文本，与手写、配置文件完全一致 ——
 * 图形编辑只是把这段文本「点」出来。含标签（{@code #tag}）的仪式无法还原成具体物品，
 * 会提示改用文本模式。</p>
 */
public class RitualEditorScreen extends UiScreen {

    // ===================== 布局 =====================
    private static final int HEADER = 52;
    private static final int FOOTER = 38;
    private static final int CELL = 32;
    private static final int GAP = 4;

    /**
     * 基座材料槽位——Goety 不限制配方材料数量：{@code Ritual.getPedestals()} 扫描祭坛
     * {@code MainConfig.RitualRange} 范围内的全部基座方块，配方 ingredients 长度没有上限。
     * 因此这里按需扩展（每次加一行），视口只显示 {@link #GRID_VISIBLE_ROWS} 行，其余滚轮翻看。
     */
    private static final int GRID_COLS = 4;
    private static final int GRID_VISIBLE_ROWS = 2;
    private static final int GRID_MIN = 8;
    private static final int GRID_MAX = 64;

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
    private int gridScroll;
    private boolean gridBarDragging;
    private float gridBarGrab;

    private UiDropdown craftDrop;
    private UiDropdown factoryDrop;
    private UiTextField soulBox;
    private UiTextField timeBox;
    private UiTextField extra1Box;
    private UiTextField extra2Box;
    /** 限制条件：献祭实体标签 / 显示名（不依赖工厂类型，任何仪式都可设置）。 */
    private UiTextField sacTagBox;
    private UiTextField sacNameBox;
    /** 当前工厂类型下额外字段的语义名（null = 隐藏该字段）。 */
    private String extra1Kind;
    private String extra2Kind;

    private UiList<String> list;
    private int actX, actY, resX, resY, countX, countY;
    private int gridX, gridY;
    private int condLabelY;

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
        clearWidgets();
        headerH = HEADER;
        footerH = FOOTER;
        layoutPanel(720, 600, 540, 470);

        int cx = contentLeft();
        int top = contentTop();
        int rowY = top + 40;

        actX = cx;
        actY = rowY;
        resX = actX + 46;
        resY = rowY;
        countX = resX + 40;
        countY = resY + CELL + 8;
        gridX = cx;
        gridY = rowY + 58;

        int rightX = cx + 226;
        int rightW = Math.max(180, contentRight() - rightX);

        // 流派：第 0 项为空（不指定），其后是全部 craftType
        List<String> crafts = new ArrayList<>();
        crafts.add("");
        crafts.addAll(craftTypes());
        craftDrop = add(new UiDropdown(rightX, rowY, rightW, 20, crafts, 0)
                .setLabeler(i -> labelFor(craftDrop, i, "config.eternal_heart.ritual_type.")));

        List<String> factories = factoryTypes();
        factoryDrop = add(new UiDropdown(rightX, rowY + 34, rightW, 20, factories,
                Math.max(0, factories.indexOf("goety:craft")))
                .setLabeler(i -> labelFor(factoryDrop, i, "config.eternal_heart.ritual_factory."))
                .setOnChange(this::applyFactoryFields));

        soulBox = field(rightX, rowY + 68, 100, "\\d*");
        timeBox = field(rightX + rightW - 100, rowY + 68, 100, "\\d*");
        extra1Box = field(rightX, rowY + 102, rightW, null);
        extra2Box = field(rightX, rowY + 136, rightW, null);
        applyFactoryFields();

        // 限制条件（献祭）：与工厂类型无关，任何仪式都可要求献祭；放在基座网格下方
        condLabelY = gridY + gridViewportH() + 14;
        sacTagBox = field(actX, condLabelY + 12, 180, null);
        sacNameBox = field(actX, condLabelY + 42, 180, null);

        // 产出数量
        add(UiButton.square(countX, countY, 20, Component.literal("-"),
                () -> outputCount = Math.max(1, outputCount - 1)));
        add(UiButton.square(countX + 46, countY, 20, Component.literal("+"),
                () -> outputCount = Math.min(64, outputCount + 1)));

        int listTopY = Math.max(sacNameBox.getY() + 24, rowY + 176) + 14;
        int listBot = footerTop() - 8;
        int listH = Math.max(40, listBot - listTopY);
        list = add(new UiList<String>(cx, listTopY, contentRight() - cx, listH))
                .setRowMetrics(22, 4)
                .setEmptyText(Component.translatable("config.eternal_heart.ritual_editor.no_rituals").getString())
                .setPainter(this::paintRow)
                .setOnClick((index, mx, my, button) -> {
                    String text = list.get(index);
                    if (text != null) loadFrom(text);
                });
        list.setItems(entries);

        // 底部操作
        int buttonY = footerTop() + 8;
        add(UiButton.primary(cx, buttonY, 60, 22,
                Component.translatable("config.eternal_heart.ritual_editor.add"), () -> commit(false)));
        add(UiButton.secondary(cx + 66, buttonY, 80, 22,
                Component.translatable("config.eternal_heart.ritual_editor.update"), () -> commit(true)));
        add(UiButton.danger(cx + 152, buttonY, 80, 22,
                Component.translatable("config.eternal_heart.ritual_editor.remove"), this::removeSelected));
        add(UiButton.ghost(cx + 238, buttonY, 76, 22,
                Component.translatable("config.eternal_heart.ritual_editor.text_mode"), this::openTextMode));
        add(UiButton.secondary(contentRight() - 64, buttonY, 64, 22,
                Component.translatable("config.eternal_heart.ritual_editor.done"), this::onClose));
    }

    private UiTextField field(int x, int y, int width, String filter) {
        UiTextField box = new UiTextField(x, y, width, 20);
        box.setMaxLength(200);
        if (filter != null) box.setFilter(text -> text.isEmpty() || text.matches(filter));
        return add(box);
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

    /** 下拉项显示名：自己的语言键 → Goety 自带的 jei 键 → 原始 id。 */
    private static String labelFor(UiDropdown drop, int index, String prefix) {
        if (drop == null || index < 0 || index >= drop.options().size()) return "";
        String id = drop.options().get(index);
        if (id.isEmpty()) return Component.translatable("config.eternal_heart.ritual_editor.none").getString();
        String key = prefix + pathOf(id);
        String text = Component.translatable(key).getString();
        if (!text.equals(key)) return text;
        String goetyKey = "jei.goety.craftType." + id;
        String goety = Component.translatable(goetyKey).getString();
        if (!goety.equals(goetyKey)) return goety;
        return id;
    }

    /** 工厂类型决定需要哪些额外字段。 */
    private void applyFactoryFields() {
        String path = pathOf(factoryDrop == null ? "" : factoryDrop.id());
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
            if (extra1Box != null) extra1Box.setText("");
        }
        if (!Objects.equals(second, extra2Kind)) {
            extra2Kind = second;
            if (extra2Box != null) extra2Box.setText("");
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
        // 不能返回 null
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

    private void paintRow(GuiGraphics g, String text, int index, int rowX, int rowY, int rowW, int rowH,
                          float hover, boolean active) {
        UiTheme.cardFade(g, rowX, rowY, rowW, rowH, UiTheme.R_SM, hover, active);
        if (active) UiTheme.roundRect(g, rowX + 3, rowY + 5, 3, rowH - 10, 1, UiTheme.ACCENT);
        g.drawString(this.font, trim(text, rowW - 26), rowX + 12, rowY + (rowH - 8) / 2,
                active ? UiTheme.ACCENT : UiTheme.TEXT, false);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partial) {
        drawHeader(g, this.title, null, HEADER);

        // 字段标签
        label(g, "config.eternal_heart.ritual_editor.activation", actX, actY - 13);
        label(g, "config.eternal_heart.ritual_editor.output", resX, resY - 13);
        label(g, Component.translatable("config.eternal_heart.ritual_editor.ingredients", grid.size()),
                gridX, gridY - 13);
        label(g, "config.eternal_heart.ritual_editor.craft_type", craftDrop.getX(), craftDrop.getY() - 13);
        label(g, "config.eternal_heart.ritual_editor.factory_type", factoryDrop.getX(), factoryDrop.getY() - 13);
        label(g, "config.eternal_heart.ritual_editor.soul", soulBox.getX(), soulBox.getY() - 13);
        label(g, "config.eternal_heart.ritual_editor.time", timeBox.getX(), timeBox.getY() - 13);
        if (extra1Kind != null) label(g, EXTRA_LABELS.get(extra1Kind), extra1Box.getX(), extra1Box.getY() - 13);
        if (extra2Kind != null) label(g, EXTRA_LABELS.get(extra2Kind), extra2Box.getX(), extra2Box.getY() - 13);

        // 限制条件（献祭）
        label(g, "config.eternal_heart.ritual_editor.conditions", actX, condLabelY);
        label(g, "config.eternal_heart.ritual_editor.sacrifice", sacTagBox.getX(), sacTagBox.getY() - 13);
        label(g, "config.eternal_heart.ritual_editor.sacrifice_name", sacNameBox.getX(), sacNameBox.getY() - 13);

        // 激活物 / 产出
        drawSlot(g, actX, actY, activation, onActivation(mouseX, mouseY));
        drawSlot(g, resX, resY, output, onOutput(mouseX, mouseY));
        g.drawCenteredString(this.font, "x" + outputCount, countX + 33, countY + 6, UiTheme.TEXT);

        // 基座材料（视口固定 2 行，其余滚轮翻看）
        int hoveredCell = cellAt(mouseX, mouseY);
        int gridW = GRID_COLS * (CELL + GAP) - GAP;
        int gridViewH = gridViewportH();
        g.enableScissor(gridX, gridY, gridX + gridW, gridY + gridViewH);
        for (int index = 0; index < grid.size(); index++) {
            int row = index / GRID_COLS;
            if (row < gridScroll || row >= gridScroll + GRID_VISIBLE_ROWS) continue;
            int x = gridX + (index % GRID_COLS) * (CELL + GAP);
            int y = gridY + (row - gridScroll) * (CELL + GAP);
            drawSlot(g, x, y, grid.get(index), index == hoveredCell);
        }
        g.disableScissor();
        drawGridScrollbar(g);

        // 已配置列表
        g.drawString(this.font, Component.translatable("config.eternal_heart.ritual_editor.saved", entries.size())
                .getString(), contentLeft(), list.getY() - 13, UiTheme.TEXT_DIM, false);

        renderWidgets(g, mouseX, mouseY, partial);
        drawFooterDivider(g, FOOTER);

        // 悬浮提示
        int cell = cellAt(mouseX, mouseY);
        if (cell >= 0 && !grid.get(cell).isEmpty()) {
            tooltip(grid.get(cell).getHoverName());
        } else if (onActivation(mouseX, mouseY) && !activation.isEmpty()) {
            tooltip(activation.getHoverName());
        } else if (onOutput(mouseX, mouseY) && !output.isEmpty()) {
            tooltip(output.getHoverName());
        }
    }

    private void drawSlot(GuiGraphics g, int x, int y, ItemStack stack, boolean hovered) {
        UiTheme.slot(g, x, y, CELL, hovered);
        if (!stack.isEmpty()) g.renderItem(stack, x + 8, y + 8);
    }

    private void label(GuiGraphics g, String key, int x, int y) {
        g.drawString(this.font, Component.translatable(key).getString(), x, y, UiTheme.TEXT_DIM, false);
    }

    private void label(GuiGraphics g, Component text, int x, int y) {
        g.drawString(this.font, text.getString(), x, y, UiTheme.TEXT_DIM, false);
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

    private int gridBarX() {
        return gridX + GRID_COLS * (CELL + GAP) - GAP + 6;
    }

    private int gridBarHeight() {
        int height = gridViewportH();
        return Math.min(height, Math.max(12, height * GRID_VISIBLE_ROWS / Math.max(1, gridRows())));
    }

    private void drawGridScrollbar(GuiGraphics g) {
        int max = maxGridScroll();
        if (max <= 0) return;
        int x = gridBarX();
        int height = gridViewportH();
        UiTheme.roundRect(g, x, gridY, 4, height, 2, UiTheme.TRACK);
        int barH = gridBarHeight();
        int barY = gridY + (height - barH) * gridScroll / max;
        UiTheme.roundRect(g, x, barY, 4, barH, 2,
                gridBarDragging ? UiTheme.ACCENT : UiTheme.ACCENT_DIM);
    }

    /** 点击 / 拖动基座网格的滚动条：点滑块开始拖，点轨道直接跳到该位置。 */
    private boolean gridBarClicked(double mouseX, double mouseY) {
        int max = maxGridScroll();
        if (max <= 0) return false;
        int x = gridBarX();
        if (mouseX < x - 5 || mouseX > x + 9) return false;
        int height = gridViewportH();
        if (mouseY < gridY || mouseY > gridY + height) return false;
        int barH = gridBarHeight();
        int barY = gridY + (height - barH) * gridScroll / max;
        gridBarDragging = true;
        if (mouseY >= barY && mouseY <= barY + barH) {
            gridBarGrab = (float) (mouseY - barY);
        } else {
            gridBarGrab = barH / 2f;
            applyGridBarDrag(mouseY);
        }
        return true;
    }

    private void applyGridBarDrag(double mouseY) {
        int max = maxGridScroll();
        int height = gridViewportH();
        int travel = Math.max(1, height - gridBarHeight());
        float ratio = (float) ((mouseY - gridY - gridBarGrab) / travel);
        ratio = Math.max(0f, Math.min(1f, ratio));
        gridScroll = Math.round(ratio * max);
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
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        // 基座网格滚动条：优先于格子命中（它画在格子右侧，不重叠）
        if (button == 0 && gridBarClicked(mouseX, mouseY)) return true;
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
        return false;
    }

    @Override
    protected boolean onMouseScrolled(double mouseX, double mouseY, double delta) {
        // 悬停基座网格：翻看材料行
        if (overGrid(mouseX, mouseY) && maxGridScroll() > 0) {
            gridScroll = (int) Math.max(0, Math.min(maxGridScroll(), gridScroll - Math.signum(delta)));
            return true;
        }
        return false;
    }

    @Override
    protected boolean onMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (gridBarDragging) {
            applyGridBarDrag(mouseY);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onMouseReleased(double mouseX, double mouseY, int button) {
        gridBarDragging = false;
        return false;
    }

    private void pickItem(Consumer<Item> apply) {
        if (this.minecraft == null) return;
        this.minecraft.setScreen(new ItemPickerScreen(this, item -> {
            apply.accept(item);
            this.minecraft.setScreen(this);
        }));
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

    private void addExtra(List<String> parts, String kind, UiTextField box) {
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
        soulBox.setText(String.valueOf(json.has("soulCost") ? json.get("soulCost").getAsInt() : 0));
        timeBox.setText(String.valueOf(json.has("duration") ? json.get("duration").getAsInt() : 30));
        // 类型相关字段
        fillExtra(extra1Kind, extra1Box, json);
        fillExtra(extra2Kind, extra2Box, json);
        // 限制条件：献祭（display_name 与标签相同时视为缺省，避免再次写回冗余字段）
        String sacTag = tagOf(json, "entity_to_sacrifice");
        sacTagBox.setText(sacTag);
        String sacName = nameOf(json, "entity_to_sacrifice");
        sacNameBox.setText(sacName.equals(sacTag) ? "" : sacName);
        if (tagged) message("config.eternal_heart.ritual_editor.tagged");
    }

    private void fillExtra(String kind, UiTextField box, JsonObject json) {
        if (box == null || kind == null) return;
        switch (kind) {
            case "summon" -> box.setText(stringOf(json, "entity_to_summon"));
            case "summon_life" -> box.setText(json.has("summonLife")
                    ? String.valueOf(json.get("summonLife").getAsInt()) : "");
            case "convert" -> box.setText(tagOf(json, "entity_to_convert"));
            case "convert_into" -> box.setText(stringOf(json, "entity_to_convert_into"));
            case "enchant" -> box.setText(stringOf(json, "enchantment"));
            case "xp" -> box.setText(json.has("xpLevelCost")
                    ? String.valueOf(json.get("xpLevelCost").getAsInt()) : "");
            case "locate" -> box.setText(tagOf(json, "structure_to_locate"));
            case "locate_name" -> box.setText(nameOf(json, "structure_to_locate"));
            default -> box.setText("");
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
        int selected = list.getSelectedIndex();
        if (replaceSelected && selected >= 0 && selected < entries.size()) {
            entries.set(selected, text);
        } else {
            entries.add(text);
            selected = entries.size() - 1;
        }
        list.setItems(entries);
        list.setSelected(selected);
        list.ensureVisible(selected);
        clearDraft();
    }

    private void removeSelected() {
        int selected = list.getSelectedIndex();
        if (selected < 0 || selected >= entries.size()) return;
        entries.remove(selected);
        if (selected >= entries.size()) selected = entries.size() - 1;
        list.setItems(entries);
        list.setSelected(selected);
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
        if (soulBox != null) soulBox.setText("");
        if (timeBox != null) timeBox.setText("");
        if (extra1Box != null) extra1Box.setText("");
        if (extra2Box != null) extra2Box.setText("");
        if (sacTagBox != null) sacTagBox.setText("");
        if (sacNameBox != null) sacNameBox.setText("");
        if (factoryDrop != null) applyFactoryFields();
    }

    private void openTextMode() {
        if (this.minecraft == null) return;
        this.minecraft.setScreen(new TextListScreen(this,
                "config.eternal_heart.text_list.title_rituals",
                "config.eternal_heart.text_list.hint_rituals", entries, updated -> {
            entries.clear();
            entries.addAll(updated);
            if (list != null) list.setItems(entries);
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
}
