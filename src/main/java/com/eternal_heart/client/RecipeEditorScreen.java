package com.eternal_heart.client;

import com.eternal_heart.features.RecipeText;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
 * 与手写、配置文件完全一致 —— 图形编辑只是把这段文本"点"出来。</p>
 *
 * <p>左键点格子 = 选物品，右键点格子 = 清空；点击右侧已配置列表可载回编辑器修改
 * （含标签 {@code #tag} 的配方无法还原成具体物品，会提示改用文本模式）。</p>
 */
public class RecipeEditorScreen extends Screen {

    // ===================== 布局 =====================
    private static final int CELL = 26;
    private static final int GAP = 2;
    private static final int ROW_H = 14;

    // ===================== 配色 =====================
    private static final int COL_PANEL = 0xF60E1521;
    private static final int COL_BORDER = 0xFF9A7B3F;
    private static final int COL_GOLD = 0xFFE8C860;
    private static final int COL_TEXT = 0xFFE6EAF0;
    private static final int COL_TEXT_DIM = 0xFF8A93A0;
    private static final int COL_SLOT = 0x30FFFFFF;
    private static final int COL_SLOT_HOVER = 0x50E8C860;
    private static final int COL_SLOT_BORDER = 0xFF6B6152;
    private static final int COL_ROW_ALT = 0x08FFFFFF;
    private static final int COL_ROW_HOVER = 0x16FFFFFF;
    private static final int COL_SELECTED = 0x30E8C860;

    private enum Mode {SHAPED, SHAPELESS, COOKING}

    // ===================== 状态 =====================
    private final Screen parent;
    private final List<String> entries;
    private final Consumer<List<String>> onChange;
    private final ItemStack[] grid = new ItemStack[9];

    private ItemStack output = ItemStack.EMPTY;
    private int outputCount = 1;
    private Mode mode = Mode.SHAPED;
    private int selected = -1;
    private int scroll, scrollMax;

    private int panelX, panelY, panelW, panelH;
    private int gridX, gridY;
    private int outputX, outputY, countX;
    private int listX1, listX2, listTop, listBot;

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
        panelW = Math.max(300, Math.min(this.width - 12, 460));
        panelH = Math.max(240, Math.min(this.height - 12, 340));
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        gridX = panelX + 16;
        gridY = panelY + 56;
        outputX = panelX + 16 + 3 * (CELL + GAP) + 26;
        outputY = gridY;
        countX = outputX;
        listX1 = panelX + 12;
        listX2 = panelX + panelW - 12;
        listTop = panelY + 158;
        listBot = panelY + panelH - 28;

        // 模式切换
        int modeY = panelY + 28;
        int modeW = 74;
        Mode[] modes = Mode.values();
        for (int i = 0; i < modes.length; i++) {
            Mode m = modes[i];
            this.addRenderableWidget(Button.builder(modeLabel(m), b -> mode = m)
                    .bounds(panelX + 16 + i * (modeW + 4), modeY, modeW, 18).build());
        }

        // 产出数量
        this.addRenderableWidget(Button.builder(Component.literal("-"), b -> {
                    outputCount = Math.max(1, outputCount - 1);
                })
                .bounds(countX + 62, outputY + 30, 18, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("+"), b -> {
                    outputCount = Math.min(64, outputCount + 1);
                })
                .bounds(countX + 104, outputY + 30, 18, 18).build());

        // 底部操作
        int buttonY = panelY + panelH - 24;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("config.eternal_heart.recipe_editor.add"), b -> commit(false))
                .bounds(panelX + 12, buttonY, 60, 18).build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("config.eternal_heart.recipe_editor.update"), b -> commit(true))
                .bounds(panelX + 76, buttonY, 60, 18).build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("config.eternal_heart.recipe_editor.remove"), b -> removeSelected())
                .bounds(panelX + 140, buttonY, 60, 18).build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("config.eternal_heart.recipe_editor.text_mode"), b -> openTextMode())
                .bounds(panelX + 204, buttonY, 60, 18).build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("config.eternal_heart.recipe_editor.done"), b -> onClose())
                .bounds(panelX + panelW - 72, buttonY, 60, 18).build());
    }

    private Component modeLabel(Mode m) {
        return Component.translatable("config.eternal_heart.recipe_editor.mode." + m.name().toLowerCase());
    }

    // ============================================================
    //  渲染
    // ============================================================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, COL_PANEL);
        graphics.renderOutline(panelX, panelY, panelW, panelH, COL_BORDER);
        graphics.drawString(this.font, this.title, panelX + 12, panelY + 8, COL_GOLD, false);
        graphics.drawString(this.font,
                Component.translatable("config.eternal_heart.recipe_editor.current_mode", modeLabel(mode)),
                panelX + panelW - 150, panelY + 8, COL_TEXT_DIM, false);

        // 3×3 网格
        int hoveredCell = cellAt(mouseX, mouseY);
        for (int index = 0; index < 9; index++) {
            int column = index % 3;
            int row = index / 3;
            int x = gridX + column * (CELL + GAP);
            int y = gridY + row * (CELL + GAP);
            graphics.fill(x, y, x + CELL, y + CELL, index == hoveredCell ? COL_SLOT_HOVER : COL_SLOT);
            graphics.renderOutline(x, y, CELL, CELL, COL_SLOT_BORDER);
            if (!grid[index].isEmpty()) graphics.renderItem(grid[index], x + 5, y + 5);
        }

        // 产出槽
        graphics.fill(outputX, outputY, outputX + CELL, outputY + CELL,
                onOutput(mouseX, mouseY) ? COL_SLOT_HOVER : COL_SLOT);
        graphics.renderOutline(outputX, outputY, CELL, CELL, COL_SLOT_BORDER);
        if (!output.isEmpty()) graphics.renderItem(output, outputX + 5, outputY + 5);
        graphics.drawString(this.font,
                Component.translatable("config.eternal_heart.recipe_editor.output"),
                outputX, outputY - 11, COL_TEXT_DIM, false);
        graphics.drawString(this.font,
                Component.translatable("config.eternal_heart.recipe_editor.count", outputCount),
                countX + 63, outputY + 34, COL_TEXT, false);

        // 已配置列表
        graphics.drawString(this.font,
                Component.translatable("config.eternal_heart.recipe_editor.saved", entries.size()),
                listX1, listTop - 11, COL_TEXT_DIM, false);
        graphics.enableScissor(listX1, listTop, listX2, listBot);
        int hovered = rowAt(mouseX, mouseY);
        for (int index = 0; index < entries.size(); index++) {
            int rowY = listTop + index * ROW_H - scroll;
            if (rowY + ROW_H < listTop || rowY > listBot) continue;
            if (index == selected) graphics.fill(listX1, rowY, listX2, rowY + ROW_H, COL_SELECTED);
            else if (index == hovered) graphics.fill(listX1, rowY, listX2, rowY + ROW_H, COL_ROW_HOVER);
            else if ((index & 1) == 1) graphics.fill(listX1, rowY, listX2, rowY + ROW_H, COL_ROW_ALT);
            graphics.drawString(this.font, trim(entries.get(index)), listX1 + 4, rowY + 3,
                    index == selected ? COL_GOLD : COL_TEXT, false);
        }
        graphics.disableScissor();
        if (entries.isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("config.eternal_heart.recipe_editor.no_recipes"),
                    listX1 + 4, listTop + 2, COL_TEXT_DIM, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        // 悬停提示
        int cell = cellAt(mouseX, mouseY);
        if (cell >= 0 && !grid[cell].isEmpty()) {
            graphics.renderTooltip(this.font, grid[cell].getHoverName(), mouseX, mouseY);
        } else if (onOutput(mouseX, mouseY) && !output.isEmpty()) {
            graphics.renderTooltip(this.font, output.getHoverName(), mouseX, mouseY);
        }
    }

    private String trim(String text) {
        int max = (listX2 - listX1 - 8) / 6;
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 3)) + "...";
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
            pickItem(item -> output = new ItemStack(item));
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
        int index = (int) ((mouseY - listTop + scroll) / ROW_H);
        return index >= 0 && index < entries.size() ? index : -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollMax = Math.max(0, entries.size() * ROW_H - (listBot - listTop));
        scroll = (int) Math.max(0, Math.min(scrollMax, scroll - delta * ROW_H * 2));
        return true;
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
                List<String> rows = new ArrayList<>();
                for (int row = 0; row < 3; row++) {
                    List<String> cells = new ArrayList<>();
                    for (int column = 0; column < 3; column++) {
                        ItemStack stack = grid[row * 3 + column];
                        cells.add(stack.isEmpty() ? "_" : shortId(stack));
                    }
                    rows.add(String.join(" ", cells));
                }
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
                // 逐行裁剪：用裁剪后的矩形重新生成（minCol 会改变列对齐，需以列切片表达）
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
