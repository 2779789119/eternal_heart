package com.eternal_heart.client;

import com.eternal_heart.client.ui.UiButton;
import com.eternal_heart.client.ui.UiEditBox;
import com.eternal_heart.client.ui.UiScreen;
import com.eternal_heart.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
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
 * <p>列出全部已注册物品（含其它模组），显示图标与本地化名称，支持按名称或 ID 搜索。</p>
 */
public class ItemPickerScreen extends UiScreen {

    private static final int ROW_H = 24;
    private static final int ROW_STEP = 26;
    private static final int HEADER_H = 52;
    private static final int FOOTER_H = 38;

    private final Screen parent;
    private final Consumer<Item> onPick;
    private final List<Item> all = new ArrayList<>();
    private final List<Item> visible = new ArrayList<>();

    private UiEditBox searchBox;
    private String lastSearch = "";
    private int scroll, scrollMax;
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
        layoutPanel(620, 500, 320, 260);
        listX1 = panelX + 16;
        listX2 = panelX + panelW - 16;
        listTop = panelY + HEADER_H + 38;
        listBot = panelY + panelH - FOOTER_H - 8;

        searchBox = new UiEditBox(this.font, listX1, panelY + HEADER_H + 10, listX2 - listX1, 20,
                Component.translatable("config.eternal_heart.item_picker.search"));
        searchBox.setMaxLength(48);
        searchBox.setValue(lastSearch);
        searchBox.setResponder(text -> {
            lastSearch = text;
            rebuild();
        });
        this.addRenderableWidget(searchBox);
        this.setFocused(searchBox);

        this.addRenderableWidget(UiButton.secondary(
                panelX + panelW - 16 - 80, panelY + panelH - FOOTER_H + 8, 80, 22,
                Component.translatable("config.eternal_heart.item_picker.cancel"), this::onClose));

        rebuild();
    }

    private void rebuild() {
        visible.clear();
        String query = lastSearch == null ? "" : lastSearch.trim().toLowerCase(Locale.ROOT);
        for (Item item : all) {
            if (query.isEmpty() || matches(item, query)) visible.add(item);
        }
        scroll = 0;
        scrollMax = Math.max(0, visible.size() * ROW_STEP - 6 - (listBot - listTop));
    }

    private boolean matches(Item item, String query) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id.toString().toLowerCase(Locale.ROOT).contains(query)) return true;
        return new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT).contains(query);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        drawPanel(g);
        drawHeader(g, this.title, null, HEADER_H);

        g.enableScissor(listX1, listTop, listX2, listBot);
        int hovered = rowAt(mx, my);
        int first = Math.max(0, scroll / ROW_STEP);
        int last = Math.min(visible.size(), first + (listBot - listTop) / ROW_STEP + 2);

        for (int index = first; index < last; index++) {
            Item item = visible.get(index);
            int rowY = listTop + index * ROW_STEP - scroll;
            if (rowY + ROW_H <= listTop || rowY >= listBot) continue;
            UiTheme.card(g, listX1, rowY, listX2 - listX1, ROW_H, 5, index == hovered, false);
            // 物品图标 + 名称
            ItemStack stack = new ItemStack(item);
            g.renderItem(stack, listX1 + 8, rowY + 4);
            g.drawString(this.font, trim(stack.getHoverName().getString(), listX2 - listX1 - 48),
                    listX1 + 32, rowY + 8, UiTheme.TEXT);
        }
        g.disableScissor();
        drawScrollbar(g, listTop, listBot, scroll, scrollMax, Math.max(1, visible.size() * ROW_STEP));

        if (visible.isEmpty()) {
            drawEmpty(g, Component.translatable("config.eternal_heart.item_picker.empty").getString(),
                    listX1, listTop, listX2, listBot);
        }

        drawFooterDivider(g, FOOTER_H);
        g.drawString(this.font,
                Component.translatable("config.eternal_heart.item_picker.count", visible.size()),
                panelX + 16, panelY + panelH - FOOTER_H + 15, UiTheme.TEXT_MUTED);

        super.render(g, mx, my, pt);

        // 悬停时显示完整 ID，方便确认是哪一个模组的物品
        if (hovered >= 0 && hovered < visible.size()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(visible.get(hovered));
            g.renderTooltip(this.font, Component.literal(id.toString()), mx, my);
        }
    }

    private int rowAt(double mx, double my) {
        if (mx < listX1 || mx > listX2 || my < listTop || my > listBot) return -1;
        int index = (int) ((my - listTop + scroll) / ROW_STEP);
        return index >= 0 && index < visible.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int index = rowAt(mx, my);
        if (index >= 0) {
            onPick.accept(visible.get(index));
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = (int) Math.max(0, Math.min(scrollMax, scroll - delta * ROW_STEP));
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
