package com.eternal_heart.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 物品选择器 —— 图形化配方编辑器的"选物品"弹层。
 *
 * <p>列出全部已注册物品（含其它模组），显示图标与本地化名称，支持按名称或 ID 搜索。
 * 给出简洁的网格化浏览：左侧色条 = 该物品是否有对应方块形态（视觉辅助，无实际作用）。</p>
 */
public class ItemPickerScreen extends Screen {

    private static final int ROW_H = 20;

    private static final int COL_OVERLAY = 0xB0040609;
    private static final int COL_PANEL = 0xF60E1521;
    private static final int COL_BORDER = 0xFF9A7B3F;
    private static final int COL_GOLD = 0xFFE8C860;
    private static final int COL_TEXT = 0xFFE6EAF0;
    private static final int COL_TEXT_DIM = 0xFF8A93A0;
    private static final int COL_ROW_ALT = 0x08FFFFFF;
    private static final int COL_ROW_HOVER = 0x16FFFFFF;
    private static final int COL_SLOT = 0x40000000;
    private static final int COL_SLOT_BORDER = 0xFF6B6152;

    private final Screen parent;
    private final Consumer<Item> onPick;
    private final List<Item> all = new ArrayList<>();
    private final List<Item> visible = new ArrayList<>();

    private EditBox searchBox;
    private String lastSearch = "";
    private int scroll, scrollMax;
    private int panelX, panelY, panelW, panelH;
    private int listX1, listX2, listTop, listBot;

    public ItemPickerScreen(Screen parent, Consumer<Item> onPick) {
        super(Component.translatable("config.eternal_heart.item_picker.title"));
        this.parent = parent;
        this.onPick = onPick;

        BuiltInRegistries.ITEM.stream()
                .filter(item -> item != net.minecraft.world.item.Items.AIR)
                .sorted(Comparator.comparing(item -> item.getDescriptionId()))
                .forEach(all::add);
    }

    @Override
    protected void init() {
        panelW = Math.max(260, Math.min(this.width - 12, 480));
        panelH = Math.max(160, Math.min(this.height - 12, 400));
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        listX1 = panelX + 8;
        listX2 = panelX + panelW - 8;
        listTop = panelY + 52;
        listBot = panelY + panelH - 28;

        searchBox = new EditBox(this.font, listX1, panelY + 30, panelW - 16, 16,
                Component.translatable("config.eternal_heart.item_picker.search"));
        searchBox.setHint(Component.translatable("config.eternal_heart.item_picker.search"));
        searchBox.setMaxLength(48);
        searchBox.setValue(lastSearch);
        searchBox.setResponder(text -> {
            lastSearch = text;
            rebuild();
        });
        this.addRenderableWidget(searchBox);
        this.setFocused(searchBox);

        this.addRenderableWidget(Button.builder(
                        Component.translatable("config.eternal_heart.item_picker.cancel"), b -> onClose())
                .bounds(panelX + panelW - 92, panelY + panelH - 24, 84, 18).build());

        rebuild();
    }

    private void rebuild() {
        visible.clear();
        String query = lastSearch == null ? "" : lastSearch.trim().toLowerCase(Locale.ROOT);
        for (Item item : all) {
            if (query.isEmpty() || matches(item, query)) visible.add(item);
        }
        scroll = 0;
        scrollMax = Math.max(0, visible.size() * ROW_H - (listBot - listTop));
    }

    private boolean matches(Item item, String query) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id.toString().toLowerCase(Locale.ROOT).contains(query)) return true;
        return new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT).contains(query);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, COL_PANEL);
        graphics.renderOutline(panelX, panelY, panelW, panelH, COL_BORDER);
        graphics.drawString(this.font, this.title, panelX + 8, panelY + 8, COL_GOLD, false);
        graphics.drawString(this.font,
                Component.translatable("config.eternal_heart.item_picker.count", visible.size()),
                panelX + 8, listBot - 11, COL_TEXT_DIM, false);

        graphics.enableScissor(listX1, listTop, listX2, listBot);
        int hovered = rowAt(mouseX, mouseY);
        int first = Math.max(0, scroll / ROW_H);
        int last = Math.min(visible.size(), first + (listBot - listTop) / ROW_H + 2);

        for (int index = first; index < last; index++) {
            Item item = visible.get(index);
            int rowY = listTop + index * ROW_H - scroll;
            if (index == hovered) {
                graphics.fill(listX1, rowY, listX2, rowY + ROW_H, COL_ROW_HOVER);
            } else if ((index & 1) == 1) {
                graphics.fill(listX1, rowY, listX2, rowY + ROW_H, COL_ROW_ALT);
            }
            // 物品图标 + 名称
            ItemStack stack = new ItemStack(item);
            graphics.renderItem(stack, listX1 + 2, rowY + 2);
            graphics.drawString(this.font, stack.getHoverName(), listX1 + 24, rowY + 6, COL_TEXT, false);
        }
        graphics.disableScissor();

        if (visible.isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("config.eternal_heart.item_picker.empty"),
                    listX1 + 4, listTop + 4, COL_TEXT_DIM, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        // 悬停时显示完整 ID，方便确认是哪一个模组的物品
        int hoveredIndex = rowAt(mouseX, mouseY);
        if (hoveredIndex >= 0 && hoveredIndex < visible.size()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(visible.get(hoveredIndex));
            graphics.renderTooltip(this.font, Component.literal(id.toString()), mouseX, mouseY);
        }
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < listX1 || mouseX > listX2 || mouseY < listTop || mouseY > listBot) return -1;
        int index = (int) ((mouseY - listTop + scroll) / ROW_H);
        return index >= 0 && index < visible.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = rowAt(mouseX, mouseY);
        if (index >= 0) {
            onPick.accept(visible.get(index));
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = (int) Math.max(0, Math.min(scrollMax, scroll - delta * ROW_H * 2));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
