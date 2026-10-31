package com.eternal_heart.client;

import com.eternal_heart.client.ui.UiButton;
import com.eternal_heart.client.ui.UiList;
import com.eternal_heart.client.ui.UiScreen;
import com.eternal_heart.client.ui.UiTextField;
import com.eternal_heart.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 物品选择器 —— 图形化配方编辑器的「选物品」弹层。
 *
 * <p>列出全部已注册物品（含其它模组），显示图标与本地化名称，支持按名称或 ID 搜索。</p>
 */
public class ItemPickerScreen extends UiScreen {

    private static final int HEADER = 52;
    private static final int FOOTER = 38;
    private static final int ROW_H = 26;

    private final Screen parent;
    private final Consumer<Item> onPick;
    private final List<Item> all = new ArrayList<>();
    private final List<Item> visible = new ArrayList<>();

    private UiTextField searchBox;
    private UiList<Item> list;
    private String lastSearch = "";

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
        clearWidgets();
        headerH = HEADER;
        footerH = FOOTER;
        layoutPanel(620, 500, 340, 280);

        int x1 = contentLeft();
        int x2 = contentRight();

        searchBox = add(new UiTextField(x1, panelY + HEADER + 8, x2 - x1, 20)
                .setHint(Component.translatable("config.eternal_heart.item_picker.search"))
                .setMaxLength(48)
                .setText(lastSearch)
                .setResponder(text -> {
                    lastSearch = text;
                    rebuild();
                }));
        focusWidget(searchBox);

        int listTop = searchBox.getY() + 28;
        int listBot = footerTop() - 8;
        list = add(new UiList<Item>(x1, listTop, x2 - x1, listBot - listTop))
                .setRowMetrics(ROW_H, 2)
                .setEmptyText(Component.translatable("config.eternal_heart.item_picker.empty").getString())
                .setPainter(this::paintRow)
                .setOnClick((index, mx, my, button) -> {
                    Item item = list.get(index);
                    if (item == null) return;
                    onPick.accept(item);
                    onClose();
                });

        add(UiButton.secondary(panelX + panelW - 16 - 80, footerTop() + 8, 80, 22,
                Component.translatable("config.eternal_heart.item_picker.cancel"), this::onClose));

        rebuild();
    }

    private void rebuild() {
        visible.clear();
        String query = lastSearch == null ? "" : lastSearch.trim().toLowerCase(Locale.ROOT);
        for (Item item : all) {
            if (query.isEmpty() || matches(item, query)) visible.add(item);
        }
        list.setItems(visible);
        list.scrollToTop();
    }

    private boolean matches(Item item, String query) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id.toString().toLowerCase(Locale.ROOT).contains(query)) return true;
        return new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT).contains(query);
    }

    private void paintRow(GuiGraphics g, Item item, int index, int rowX, int rowY, int rowW, int rowH,
                          float hover, boolean selected) {
        UiTheme.cardFade(g, rowX, rowY, rowW, rowH, UiTheme.R_SM, hover, selected);
        ItemStack stack = new ItemStack(item);
        g.renderItem(stack, rowX + 8, rowY + (rowH - 16) / 2);
        g.drawString(this.font, trim(stack.getHoverName().getString(), rowW - 46),
                rowX + 30, rowY + (rowH - 8) / 2,
                hover > 0.35f ? UiTheme.TEXT : UiTheme.TEXT_DIM, false);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partial) {
        drawHeader(g, this.title, null, HEADER);
        renderWidgets(g, mouseX, mouseY, partial);
        drawFooterDivider(g, FOOTER);
        drawStatus(g, Component.translatable("config.eternal_heart.item_picker.count", visible.size()).getString(),
                UiTheme.TEXT_MUTED, panelW - 200);

        // 悬停时显示完整 ID，方便确认是哪一个模组的物品
        int hovered = list == null ? -1 : list.indexAt(mouseX, mouseY);
        if (hovered >= 0) {
            Item item = list.get(hovered);
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            tooltip(Component.literal(id.toString()).withStyle(style -> style.withColor(UiTheme.ACCENT)));
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }
}
