package com.eternal_heart.client;

import com.eternal_heart.client.ui.UiButton;
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
 * 与手写、配置文件完全一致 —— 图形编辑只是把这段文本"点"出来。</p>
 *
 * <p>左键点格子 = 选物品，右键点格子 = 清空；点击右侧已配置列表可载回编辑器修改
 * （含标签 {@code #tag} 的配方无法还原成具体物品，会提示改用文本模式）。</p>
 */
public class RecipeEditorScreen extends UiScreen {

    // ===================== 布局 =====================
    private static final int CELL = 30;
    private static final int GAP = 3;
    private static final int ROW_H = 22;
    private static final int ROW_STEP = 26;
    private static final int HEADER_H = 52;
    private static final int FOOTER_H = 38;

    private enum Mode {SHAPED, SHAPELESS, COOKING}

    // ===================== 状态 =====================
    private final Screen parent;
    private final List<String> entries;
    private final Consumer<List<String>> onChange;
    private final ItemStack[] grid = new ItemStack[9];
    private final UiButton[] modeButtons = new UiButton[Mode.values().length];

    private ItemStack output = ItemStack.EMPTY;
    private int outputCount = 1;
    private Mode mode = Mode.SHAPED;
    private int selected = -1;
    private int scroll, scrollMax;

    private int gridX, gridY;
    private int outputX, outputY;
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
        layoutPanel(540, 410, 420, 320);

        int contentTop = panelY + HEADER_H;
        gridX = panelX + 20;
        gridY = contentTop + 50;
        outputX = gridX + 3 * (CELL + GAP) - GAP + 34;
        outputY = gridY;
        listX1 = panelX + 20;
        listX2 = panelX + panelW - 20;
        listTop = gridY + CELL + 40;
        listBot = panelY + panelH - FOOTER_H - 8;

        // 模式切换（分段控件）
        Mode[] modes = Mode.values();
        int modeY = contentTop + 12;
        for (int i = 0; i < modes.length; i++) {
            Mode m = modes[i];
            modeButtons[i] = this.addRenderableWidget(UiButton.segment(
                    panelX + 20 + i * 84, modeY, 78, 20, modeLabel(m), () -> {
                        mode = m;
                        refreshModeButtons();
                    }));
        }
        refreshModeButtons();

        // 产出数量
        this.addRenderableWidget(UiButton.secondary(outputX + 36, outputY + 6, 18, 18,
                Component.literal("-"), () -> outputCount = Math.max(1, outputCount - 1)));
        this.addRenderableWidget(UiButton.secondary(outputX + 84, outputY + 6, 18, 18,
                Component.literal("+"), () -> outputCount = Math.min(64, outputCount + 1)));

        // 底部操作
        int buttonY = panelY + panelH - FOOTER_H + 8;
        this.addRenderableWidget(UiButton.primary(panelX + 20, buttonY, 54, 22,
                Component.translatable("config.eternal_heart.recipe_editor.add"), () -> commit(false)));
        this.addRenderableWidget(UiButton.secondary(panelX + 80, buttonY, 72, 22,
                Component.translatable("config.eternal_heart.recipe_editor.update"), () -> commit(true)));
        this.addRenderableWidget(UiButton.danger(panelX + 158, buttonY, 72, 22,
                Component.translatable("config.eternal_heart.recipe_editor.remove"), this::removeSelected));
        this.addRenderableWidget(UiButton.ghost(panelX + 236, buttonY, 70, 22,
                Component.translatable("config.eternal_heart.recipe_editor.text_mode"), this::openTextMode));
        this.addRenderableWidget(UiButton.secondary(panelX + panelW - 20 - 60, buttonY, 60, 22,
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

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        drawPanel(g);
        drawHeader(g, this.title, null, HEADER_H);

        // 3×3 网格
        int hoveredCell = cellAt(mx, my);
        for (int index = 0; index < 9; index++) {
            int column = index % 3;
            int row = index / 3;
            int x = gridX + column * (CELL + GAP);
            int y = gridY + row * (CELL + GAP);
            UiTheme.slot(g, x, y, CELL, index == hoveredCell);
            if (!grid[index].isEmpty()) g.renderItem(grid[index], x + 7, y + 7);
        }

        // 箭头 + 产出槽
        g.drawString(this.font, "→", gridX + 3 * (CELL + GAP) - GAP + 12, gridY + 11,
                UiTheme.TEXT_MUTED);
        UiTheme.slot(g, outputX, outputY, CELL, onOutput(mx, my));
        if (!output.isEmpty()) g.renderItem(output, outputX + 7, outputY + 7);
        g.drawString(this.font, Component.translatable("config.eternal_heart.recipe_editor.output"),
                outputX, outputY - 11, UiTheme.TEXT_DIM);
        g.drawCenteredString(this.font,
                Component.literal("x" + outputCount).getString(), outputX + 69, outputY + 11, UiTheme.TEXT);

        // 已配置列表
        g.drawString(this.font,
                Component.translatable("config.eternal_heart.recipe_editor.saved", entries.size()),
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
            g.drawString(this.font,
                    Component.translatable("config.eternal_heart.recipe_editor.no_recipes"),
                    listX1 + 4, listTop + 4, UiTheme.TEXT_MUTED, false);
        }

        drawFooterDivider(g, FOOTER_H);
        super.render(g, mx, my, pt);

        // 悬停提示
        int cell = cellAt(mx, my);
        if (cell >= 0 && !grid[cell].isEmpty()) {
            g.renderTooltip(this.font, grid[cell].getHoverName(), mx, my);
        } else if (onOutput(mx, my) && !output.isEmpty()) {
            g.renderTooltip(this.font, output.getHoverName(), mx, my);
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
        int index = (int) ((mouseY - listTop + scroll) / ROW_STEP);
        return index >= 0 && index < entries.size() ? index : -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollMax = Math.max(0, entries.size() * ROW_STEP - 6 - (listBot - listTop));
        scroll = (int) Math.max(0, Math.min(scrollMax, scroll - delta * ROW_STEP));
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
