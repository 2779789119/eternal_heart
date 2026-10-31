package com.eternal_heart.client;

import com.eternal_heart.client.ui.UiButton;
import com.eternal_heart.client.ui.UiList;
import com.eternal_heart.client.ui.UiScreen;
import com.eternal_heart.client.ui.UiTextField;
import com.eternal_heart.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * 永恒之心 - 「自定义饰品防收」编辑界面。
 *
 * <p>自动读取当前整合包内所有饰品：遍历物品注册表，用 Curios 的
 * {@code getCurioTags(Item)}（与饰品栏 / 提示框同一套判定）判断一个物品能不能进某个
 * 饰品槽，是饰品才列进来。样式与「附加被动效果」一致：搜索 + 多选 + 图标 + 槽位标注。</p>
 */
public class CurioListScreen extends UiScreen {

    private static final int HEADER = 52;
    private static final int FOOTER = 38;
    private static final int ROW_H = 24;

    private final Screen parent;
    private final List<String> selected;
    private final Consumer<List<String>> onChange;
    private final List<Item> all = new ArrayList<>();
    private final Map<Item, String> slotLabel = new HashMap<>();
    private final List<Item> visible = new ArrayList<>();

    private UiTextField searchBox;
    private UiList<Item> list;
    private String lastSearch = "";

    public CurioListScreen(Screen parent, List<String> initial, Consumer<List<String>> onChange) {
        super(Component.translatable("config.eternal_heart.curio_list.title"));
        this.parent = parent;
        this.selected = new ArrayList<>(initial);
        this.onChange = onChange;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            Set<String> slots;
            try {
                slots = CuriosApi.getCuriosHelper().getCurioTags(item);
            } catch (Throwable t) {
                slots = Set.of();
            }
            if (slots == null || slots.isEmpty()) continue;
            this.all.add(item);
            this.slotLabel.put(item, String.join(", ", new TreeSet<>(slots)));
        }
        this.all.sort(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("config.eternal_heart.curio_list." + key, args).getString();
    }

    private static String idOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    @Override
    protected void init() {
        clearWidgets();
        headerH = HEADER;
        footerH = FOOTER;
        layoutPanel(660, 520, 400, 320);

        int x1 = contentLeft();
        int x2 = contentRight();

        searchBox = add(new UiTextField(x1, panelY + HEADER + 8, x2 - x1, 20)
                .setHint(Component.translatable("config.eternal_heart.curio_list.search_hint"))
                .setMaxLength(48)
                .setText(lastSearch)
                .setResponder(text -> {
                    lastSearch = text;
                    rebuild();
                }));

        int top = searchBox.getY() + 28;
        int bottom = footerTop() - 8;
        list = add(new UiList<Item>(x1, top, x2 - x1, bottom - top))
                .setRowMetrics(ROW_H, 2)
                .setEmptyText(tr("empty"))
                .setPainter(this::paintRow)
                .setOnClick((index, mx, my, button) -> {
                    Item item = list.get(index);
                    if (item != null) toggle(idOf(item));
                });

        add(UiButton.primary(panelX + panelW - 16 - 76, footerTop() + 8, 76, 22,
                Component.translatable("config.eternal_heart.curio_list.done"), this::onClose));
        add(UiButton.danger(panelX + panelW - 16 - 76 - 8 - 62, footerTop() + 8, 62, 22,
                Component.translatable("config.eternal_heart.curio_list.clear"), this::clearAll));

        rebuild();
    }

    private void rebuild() {
        visible.clear();
        String query = lastSearch == null ? "" : lastSearch.trim().toLowerCase(Locale.ROOT);
        for (Item item : all) {
            if (query.isEmpty() || matches(item, query)) visible.add(item);
        }
        list.setItems(visible);
    }

    private boolean matches(Item item, String query) {
        if (idOf(item).toLowerCase(Locale.ROOT).contains(query)) return true;
        String slots = slotLabel.get(item);
        if (slots != null && slots.toLowerCase(Locale.ROOT).contains(query)) return true;
        return new ItemStack((ItemLike) item).getHoverName().getString().toLowerCase(Locale.ROOT).contains(query);
    }

    // ===================== 渲染 =====================

    private void paintRow(GuiGraphics g, Item item, int index, int rowX, int rowY, int rowW, int rowH,
                          float hover, boolean active) {
        String id = idOf(item);
        boolean inList = containsId(id);
        UiTheme.cardFade(g, rowX, rowY, rowW, rowH, UiTheme.R_SM, hover, inList);
        g.renderItem(new ItemStack((ItemLike) item), rowX + 8, rowY + (rowH - 16) / 2);

        String name = new ItemStack((ItemLike) item).getHoverName().getString();
        g.drawString(this.font, trim(name, 150), rowX + 30, rowY + (rowH - 8) / 2,
                inList ? UiTheme.ACCENT : UiTheme.TEXT, false);

        String mark = inList ? tr("added") : "+";
        int markW = this.font.width(mark) + 14;
        int markX = rowX + rowW - 12 - markW;
        if (inList) {
            UiTheme.chip(g, markX, rowY + (rowH - 14) / 2, markW, 14,
                    UiTheme.ACCENT_SOFT, UiTheme.withAlpha(UiTheme.ACCENT_DIM, 0x66));
        }
        g.drawString(this.font, mark, markX + 7, rowY + (rowH - 8) / 2,
                inList ? UiTheme.ACCENT : UiTheme.TEXT_MUTED, false);

        String slots = slotLabel.getOrDefault(item, "");
        int idX = rowX + 186;
        String idText = id + (slots.isEmpty() ? "" : " · " + slots);
        g.drawString(this.font, trim(idText, Math.max(24, markX - 12 - idX)),
                idX, rowY + (rowH - 8) / 2, UiTheme.TEXT_MUTED, false);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partial) {
        drawHeader(g, Component.translatable("config.eternal_heart.curio_list.title"),
                Component.translatable("config.eternal_heart.curio_list.subtitle"), HEADER);
        renderWidgets(g, mouseX, mouseY, partial);
        drawFooterDivider(g, FOOTER);
        drawStatus(g, tr("count", selected.size()), UiTheme.TEXT_MUTED, panelW - 200);

        int hovered = list == null ? -1 : list.indexAt(mouseX, mouseY);
        if (hovered >= 0) {
            Item item = list.get(hovered);
            if (item != null) tooltip(new ItemStack((ItemLike) item).getHoverName());
        }
    }

    // ===================== 判定 / 操作 =====================

    private boolean containsId(String id) {
        for (String entry : selected) {
            if (normalize(entry).equals(id)) return true;
        }
        return false;
    }

    private static String normalize(String entry) {
        if (entry == null) return "";
        String text = entry.trim();
        return text.contains(":") ? text : "minecraft:" + text;
    }

    private void toggle(String id) {
        String existing = null;
        for (String entry : selected) {
            if (!normalize(entry).equals(id)) continue;
            existing = entry;
            break;
        }
        if (existing != null) selected.remove(existing);
        else selected.add(id);
        selected.sort(Comparator.naturalOrder());
        onChange.accept(List.copyOf(selected));
    }

    private void clearAll() {
        selected.clear();
        onChange.accept(List.of());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
