package com.eternal_heart.client;

import com.eternal_heart.client.ui.UiButton;
import com.eternal_heart.client.ui.UiList;
import com.eternal_heart.client.ui.UiScreen;
import com.eternal_heart.client.ui.UiTheme;
import com.eternal_heart.features.RecipeText;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 自定义配方的**图形化编辑器** —— 点格子选物品，不用碰任何文字。
 *
 * <p>界面：3×3 网格 + 产出槽 + 产出数量 + 模式切换 + 已配置配方列表。</p>
 *
 * <ul>
 *   <li><b>有序合成</b>：网格的位置就是配方形状；</li>
 *   <li><b>无序合成</b>：只看网格里放了什么（位置无关），相同物品自动合并计数；</li>
 *   <li><b>熔炼</b>：只用左上角一格作为材料。</li>
 * </ul>
 *
 * <p>生成的结果仍是简化语法文本（{@code shaped: …} / {@code shapeless: …} / {@code smelting: …}），
 * 与手写、配置文件完全一致 —— 图形编辑只是把这段文本「点」出来。</p>
 */
public class RecipeEditorScreen extends UiScreen {

    private static final int HEADER = 52;
    private static final int FOOTER = 38;
    private static final int CELL = 32;
    private static final int GAP = 4;

    private enum Mode {SHAPED, SHAPELESS, COOKING}

    private final Screen parent;
    private final List<String> entries;
    private final Consumer<List<String>> onChange;
    private final ItemStack[] grid = new ItemStack[9];
    private final UiButton[] modeButtons = new UiButton[Mode.values().length];

    private ItemStack output = ItemStack.EMPTY;
    private int outputCount = 1;
    private Mode mode = Mode.SHAPED;
    private UiList<String> list;

    private int gridX, gridY;
    private int outputX, outputY;
    private int countY;

    public RecipeEditorScreen(Screen parent, List<String> initial, Consumer<List<String>> onChange) {
        super(Component.translatable("config.eternal_heart.recipe_editor.title"));
        this.parent = parent;
        this.entries = new ArrayList<>(initial);
        this.onChange = onChange;
        for (int i = 0; i < grid.length; i++) grid[i] = ItemStack.EMPTY;
    }

    // ============================================================
    //  布局
    // ============================================================

    @Override
    protected void init() {
        clearWidgets();
        headerH = HEADER;
        footerH = FOOTER;
        layoutPanel(580, 470, 440, 360);

        int cx = contentLeft();
        int top = contentTop();

        // 模式切换（分段控件，铺满内容宽度）
        Mode[] modes = Mode.values();
        int gap = 6;
        int modeW = (contentRight() - cx - gap * (modes.length - 1)) / modes.length;
        for (int i = 0; i < modes.length; i++) {
            Mode m = modes[i];
            modeButtons[i] = add(UiButton.segment(cx + i * (modeW + gap), top + 10, modeW, 22,
                    modeLabel(m), () -> {
                        mode = m;
                        refreshModeButtons();
                    }));
        }
        refreshModeButtons();

        int gridW = 3 * CELL + 2 * GAP;
        gridX = cx + 4;
        gridY = top + 66;
        outputX = gridX + gridW + 46;
        outputY = gridY + CELL + GAP;
        countY = outputY + CELL + 8;

        add(UiButton.square(outputX - 2, countY, 20, Component.literal("-"),
                () -> outputCount = Math.max(1, outputCount - 1)));
        add(UiButton.square(outputX + CELL + 26, countY, 20, Component.literal("+"),
                () -> outputCount = Math.min(64, outputCount + 1)));

        int listTop = gridY + 3 * CELL + 2 * GAP + 28;
        int listBot = footerTop() - 8;
        list = add(new UiList<String>(cx, listTop, contentRight() - cx, listBot - listTop))
                .setRowMetrics(22, 4)
                .setEmptyText(Component.translatable("config.eternal_heart.recipe_editor.no_recipes").getString())
                .setPainter(this::paintRow)
                .setOnClick((index, mx, my, button) -> {
                    String text = list.get(index);
                    if (text != null) loadFrom(text);
                });
        list.setItems(entries);

        // 底部操作
        int buttonY = footerTop() + 8;
        add(UiButton.primary(cx, buttonY, 60, 22,
                Component.translatable("config.eternal_heart.recipe_editor.add"), () -> commit(false)));
        add(UiButton.secondary(cx + 66, buttonY, 80, 22,
                Component.translatable("config.eternal_heart.recipe_editor.update"), () -> commit(true)));
        add(UiButton.danger(cx + 152, buttonY, 80, 22,
                Component.translatable("config.eternal_heart.recipe_editor.remove"), this::removeSelected));
        add(UiButton.ghost(cx + 238, buttonY, 76, 22,
                Component.translatable("config.eternal_heart.recipe_editor.text_mode"), this::openTextMode));
        add(UiButton.secondary(contentRight() - 64, buttonY, 64, 22,
                Component.translatable("config.eternal_heart.recipe_editor.done"), this::onClose));
    }

    private void refreshModeButtons() {
        Mode[] modes = Mode.values();
        for (int i = 0; i < modes.length; i++) {
            if (modeButtons[i] != null) modeButtons[i].setSelected(mode == modes[i]);
        }
    }

    private Component modeLabel(Mode m) {
        return Component.translatable("config.eternal_heart.recipe_editor.mode." + m.name().toLowerCase());
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
        drawHeader(g, this.title, Component.translatable("config.eternal_heart.recipe_editor.current_mode",
                modeLabel(mode).getString()), HEADER);

        renderWidgets(g, mouseX, mouseY, partial);

        // 3×3 网格
        int hoveredCell = cellAt(mouseX, mouseY);
        for (int index = 0; index < 9; index++) {
            int column = index % 3;
            int row = index / 3;
            int x = gridX + column * (CELL + GAP);
            int y = gridY + row * (CELL + GAP);
            UiTheme.slot(g, x, y, CELL, index == hoveredCell);
            if (!grid[index].isEmpty()) g.renderItem(grid[index], x + 8, y + 8);
        }

        // 箭头 + 产出槽
        g.drawString(this.font, "→", gridX + 3 * CELL + 2 * GAP + 14, outputY + 12,
                UiTheme.TEXT_MUTED, false);
        g.drawString(this.font, Component.translatable("config.eternal_heart.recipe_editor.output").getString(),
                outputX, outputY - 13, UiTheme.TEXT_DIM, false);
        UiTheme.slot(g, outputX, outputY, CELL, onOutput(mouseX, mouseY));
        if (!output.isEmpty()) g.renderItem(output, outputX + 8, outputY + 8);
        g.drawCenteredString(this.font, "x" + outputCount, outputX + CELL / 2, countY + 6, UiTheme.TEXT);

        // 已配置列表
        g.drawString(this.font, Component.translatable("config.eternal_heart.recipe_editor.saved", entries.size())
                .getString(), contentLeft(), list.getY() - 13, UiTheme.TEXT_DIM, false);

        drawFooterDivider(g, FOOTER);

        // 悬停提示
        int cell = cellAt(mouseX, mouseY);
        if (cell >= 0 && !grid[cell].isEmpty()) {
            tooltip(grid[cell].getHoverName());
        } else if (onOutput(mouseX, mouseY) && !output.isEmpty()) {
            tooltip(output.getHoverName());
        } else {
            int hoveredRow = list.indexAt(mouseX, mouseY);
            if (hoveredRow >= 0) {
                String text = list.get(hoveredRow);
                if (text != null) {
                    tooltip(Component.literal(UiTheme.trim(this.font, text, 260))
                            .withStyle(style -> style.withColor(UiTheme.ACCENT_DIM)));
                }
            }
        }
    }

    // ============================================================
    //  交互
    // ============================================================

    private int cellAt(double mouseX, double mouseY) {
        for (int index = 0; index < 9; index++) {
            int x = gridX + (index % 3) * (CELL + GAP);
            int y = gridY + (index / 3) * (CELL + GAP);
            if (mouseX >= x && mouseX <= x + CELL && mouseY >= y && mouseY <= y + CELL) return index;
        }
        return -1;
    }

    private boolean onOutput(double mouseX, double mouseY) {
        return mouseX >= outputX && mouseX <= outputX + CELL && mouseY >= outputY && mouseY <= outputY + CELL;
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        int cell = cellAt(mouseX, mouseY);
        if (cell >= 0) {
            if (button == 1) {
                grid[cell] = ItemStack.EMPTY; // 右键清空该格
            } else {
                pickItem(item -> grid[cell] = new ItemStack(item));
            }
            return true;
        }
        if (onOutput(mouseX, mouseY)) {
            if (button == 1) output = ItemStack.EMPTY;
            else pickItem(item -> output = new ItemStack(item));
            return true;
        }
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

    /** 把当前编辑内容生成简化语法文本；内容不完整返回 null。 */
    private String toText() {
        if (output.isEmpty()) return null;
        String out = shortId(output);
        String outPart = outputCount > 1 ? out + " x" + outputCount : out;

        return switch (mode) {
            case SHAPED -> {
                // 去掉四周空行 / 空列（用户把图案画在中间也能得到正确形状）
                int minRow = 3, maxRow = -1, minCol = 3, maxCol = -1;
                for (int index = 0; index < 9; index++) {
                    if (grid[index].isEmpty()) continue;
                    int row = index / 3, column = index % 3;
                    minRow = Math.min(minRow, row);
                    maxRow = Math.max(maxRow, row);
                    minCol = Math.min(minCol, column);
                    maxCol = Math.max(maxCol, column);
                }
                if (maxRow < 0) yield null;
                List<String> trimmed = new ArrayList<>();
                for (int row = minRow; row <= maxRow; row++) {
                    List<String> cells = new ArrayList<>();
                    for (int column = minCol; column <= maxCol; column++) {
                        ItemStack stack = grid[row * 3 + column];
                        cells.add(stack.isEmpty() ? "_" : shortId(stack));
                    }
                    trimmed.add(String.join(" ", cells));
                }
                yield "shaped: " + outPart + " = " + String.join(" / ", trimmed);
            }
            case SHAPELESS -> {
                Map<String, Integer> counts = new LinkedHashMap<>();
                for (ItemStack stack : grid) {
                    if (!stack.isEmpty()) counts.merge(shortId(stack), 1, Integer::sum);
                }
                if (counts.isEmpty()) yield null;
                StringBuilder materials = new StringBuilder();
                for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                    if (materials.length() > 0) materials.append(" + ");
                    if (entry.getValue() > 1) materials.append(entry.getValue()).append('x').append(' ');
                    materials.append(entry.getKey());
                }
                yield "shapeless: " + outPart + " = " + materials;
            }
            case COOKING -> {
                ItemStack material = grid[0];
                if (material.isEmpty()) yield null;
                yield "smelting: " + out + " <- " + shortId(material);
            }
        };
    }

    /** 把已配置的配方载回编辑器（含标签的配方无法还原，会提示）。 */
    private void loadFrom(String text) {
        JsonObject json;
        try {
            json = RecipeText.parse(text);
        } catch (RuntimeException e) {
            json = null;
        }
        if (json == null) {
            message("config.eternal_heart.recipe_editor.cannot_load");
            return;
        }
        for (int index = 0; index < grid.length; index++) grid[index] = ItemStack.EMPTY;
        output = ItemStack.EMPTY;
        outputCount = 1;
        boolean tagged = false;

        String type = json.has("type") ? json.get("type").getAsString() : "";
        switch (type) {
            case "minecraft:crafting_shaped" -> {
                mode = Mode.SHAPED;
                JsonArray pattern = json.getAsJsonArray("pattern");
                JsonObject key = json.has("key") ? json.getAsJsonObject("key") : new JsonObject();
                for (int row = 0; row < Math.min(3, pattern.size()); row++) {
                    String line = pattern.get(row).getAsString();
                    for (int column = 0; column < Math.min(3, line.length()); column++) {
                        char symbol = line.charAt(column);
                        if (symbol == ' ') continue;
                        JsonObject ingredient = key.has(String.valueOf(symbol))
                                ? key.getAsJsonObject(String.valueOf(symbol)) : null;
                        Item item = itemOf(ingredient);
                        if (item == null) {
                            tagged = true;
                            continue;
                        }
                        grid[row * 3 + column] = new ItemStack(item);
                    }
                }
            }
            case "minecraft:crafting_shapeless" -> {
                mode = Mode.SHAPELESS;
                JsonArray ingredients = json.getAsJsonArray("ingredients");
                for (int index = 0; index < Math.min(9, ingredients.size()); index++) {
                    Item item = itemOf(ingredients.get(index).getAsJsonObject());
                    if (item == null) {
                        tagged = true;
                        continue;
                    }
                    grid[index] = new ItemStack(item);
                }
            }
            case "minecraft:smelting", "minecraft:blasting", "minecraft:smoking" -> {
                mode = Mode.COOKING;
                Item item = itemOf(json.has("ingredient") ? json.getAsJsonObject("ingredient") : null);
                if (item == null) tagged = true;
                else grid[0] = new ItemStack(item);
            }
            default -> {
                message("config.eternal_heart.recipe_editor.cannot_load");
                return;
            }
        }

        if (json.has("result")) {
            JsonElement result = json.get("result");
            if (result.isJsonObject()) {
                JsonObject object = result.getAsJsonObject();
                Item item = itemOf(object);
                if (item != null) output = new ItemStack(item);
                outputCount = object.has("count") ? Math.max(1, object.get("count").getAsInt()) : 1;
            } else if (result.isJsonPrimitive()) {
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(result.getAsString()));
                if (item != Items.AIR) output = new ItemStack(item);
            }
        }
        refreshModeButtons();
        if (tagged) message("config.eternal_heart.recipe_editor.tagged");
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
            message("config.eternal_heart.recipe_editor.incomplete");
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
        for (int index = 0; index < grid.length; index++) grid[index] = ItemStack.EMPTY;
        output = ItemStack.EMPTY;
        outputCount = 1;
    }

    private void openTextMode() {
        if (this.minecraft == null) return;
        this.minecraft.setScreen(new TextListScreen(this,
                "config.eternal_heart.text_list.title_recipes",
                "config.eternal_heart.text_list.hint_recipes", entries, updated -> {
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
