/*
 * 永恒之心 - 「自定义饰品防收」编辑界面
 *
 * 自动读取当前整合包内所有饰品：遍历物品注册表，用 Curios 的
 * getCurioTags(Item)（与饰品栏 / 提示框同一套判定：槽位条件 + curios 标签）
 * 判断一个物品是不是能进某个饰品槽，是饰品才列进来。
 * 样式与「附加被动效果」编辑界面一致：搜索 + 多选 + 图标 + 槽位标注。
 */
package com.eternal_heart.client;

import com.eternal_heart.client.ui.UiButton;
import com.eternal_heart.client.ui.UiEditBox;
import com.eternal_heart.client.ui.UiScreen;
import com.eternal_heart.client.ui.UiTheme;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import top.theillusivec4.curios.api.CuriosApi;

public class CurioListScreen
extends UiScreen {
    private static final int ROW_H = 22;
    private static final int ROW_STEP = 26;
    private static final int HEADER_H = 52;
    private static final int FOOTER_H = 38;
    private final Screen parent;
    private final List<String> selected;
    private final Consumer<List<String>> onChange;
    private final List<Item> all = new ArrayList<Item>();
    private final Map<Item, String> slotLabel = new java.util.HashMap<Item, String>();
    private final List<Item> visible = new ArrayList<Item>();
    private UiEditBox searchBox;
    private String lastSearch = "";
    private int scroll;
    private int scrollMax;
    private int listX1;
    private int listX2;
    private int listTop;
    private int listBot;

    public CurioListScreen(Screen parent, List<String> initial, Consumer<List<String>> onChange) {
        super((Component)Component.translatable((String)"config.eternal_heart.curio_list.title"));
        this.parent = parent;
        this.selected = new ArrayList<String>(initial);
        this.onChange = onChange;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            Set<String> slots;
            try {
                slots = CuriosApi.getCuriosHelper().getCurioTags(item);
            }
            catch (Throwable t) {
                slots = Set.of();
            }
            if (slots == null || slots.isEmpty()) continue;
            this.all.add(item);
            this.slotLabel.put(item, String.join(", ", new TreeSet<String>(slots)));
        }
        this.all.sort(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
    }

    private static String tr(String key, Object ... args) {
        return Component.translatable((String)("config.eternal_heart.curio_list." + key), (Object[])args).getString();
    }

    private static String idOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    protected void init() {
        this.layoutPanel(640, 520, 400, 320);
        this.listX1 = this.panelX + 16;
        this.listX2 = this.panelX + this.panelW - 16;
        this.listTop = this.panelY + 52 + 38;
        this.listBot = this.panelY + this.panelH - 38 - 8;
        this.searchBox = new UiEditBox(this.font, this.listX1, this.panelY + 52 + 10, this.listX2 - this.listX1, 20, (Component)Component.translatable((String)"config.eternal_heart.curio_list.search_hint"));
        this.searchBox.setMaxLength(48);
        this.searchBox.setValue(this.lastSearch);
        this.searchBox.setResponder(text -> {
            this.lastSearch = text;
            this.rebuild();
        });
        this.addRenderableWidget(this.searchBox);
        int footerTop = this.panelY + this.panelH - 38;
        this.addRenderableWidget(UiButton.primary(this.panelX + this.panelW - 16 - 76, footerTop + 8, 76, 22, (Component)Component.translatable((String)"config.eternal_heart.curio_list.done"), this::onClose));
        this.addRenderableWidget(UiButton.danger(this.panelX + this.panelW - 16 - 76 - 8 - 62, footerTop + 8, 62, 22, (Component)Component.translatable((String)"config.eternal_heart.curio_list.clear"), this::clearAll));
        this.rebuild();
    }

    private void rebuild() {
        this.visible.clear();
        String query = this.lastSearch == null ? "" : this.lastSearch.trim().toLowerCase(Locale.ROOT);
        for (Item item : this.all) {
            if (!query.isEmpty() && !this.matches(item, query)) continue;
            this.visible.add(item);
        }
        this.scroll = 0;
        this.scrollMax = Math.max(0, this.visible.size() * 26 - 6 - (this.listBot - this.listTop));
    }

    private boolean matches(Item item, String query) {
        if (idOf(item).toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        String slots = this.slotLabel.get(item);
        if (slots != null && slots.toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        return new ItemStack((ItemLike)item).getHoverName().getString().toLowerCase(Locale.ROOT).contains(query);
    }

    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        this.drawPanel(g);
        this.drawHeader(g, (Component)Component.translatable((String)"config.eternal_heart.curio_list.title"), (Component)Component.translatable((String)"config.eternal_heart.curio_list.subtitle"), 52);
        g.enableScissor(this.listX1, this.listTop, this.listX2, this.listBot);
        this.renderRows(g, mx, my);
        g.disableScissor();
        this.drawScrollbar(g, this.listTop, this.listBot, this.scroll, this.scrollMax, Math.max(1, this.visible.size() * 26));
        this.drawFooterDivider(g, 38);
        g.drawString(this.font, tr("count", this.selected.size()), this.panelX + 16, this.panelY + this.panelH - 38 + 15, -9865337);
        super.render(g, mx, my, pt);
    }

    private void renderRows(GuiGraphics g, int mx, int my) {
        for (int i = 0; i < this.visible.size(); ++i) {
            int y = this.listTop - this.scroll + i * 26;
            if (y + 22 <= this.listTop || y >= this.listBot) continue;
            Item item = this.visible.get(i);
            String id = idOf(item);
            boolean inList = this.containsId(id);
            boolean hover = mx >= this.listX1 && mx < this.listX2 && my >= y && my < y + 22;
            UiTheme.card(g, this.listX1, y, this.listX2 - this.listX1, 22, 5, hover, inList);
            g.renderItem(new ItemStack((ItemLike)item), this.listX1 + 6, y + 3);
            String name = new ItemStack((ItemLike)item).getHoverName().getString();
            g.drawString(this.font, this.trim(name, 150), this.listX1 + 28, y + 7, inList ? -10556716 : -1511690);
            String slots = this.slotLabel.getOrDefault(item, "");
            int markW = 34;
            String idText = this.trim(id + (slots.isEmpty() ? "" : " \u00b7 " + slots), this.listX2 - 12 - markW - (this.listX1 + 186));
            g.drawString(this.font, idText, this.listX1 + 186, y + 7, -9865337);
            String mark = inList ? tr("added") : "+";
            int markX = this.listX2 - 12 - this.font.width(mark) - 8;
            if (inList) {
                UiTheme.roundRect(g, markX - 4, y + 4, this.font.width(mark) + 8, 14, 7, 861858516);
            }
            g.drawString(this.font, mark, markX, y + 7, inList ? -10556716 : -9865337);
        }
        if (this.visible.isEmpty()) {
            this.drawEmpty(g, tr("empty"), this.listX1, this.listTop, this.listX2, this.listBot);
        }
    }

    private boolean containsId(String id) {
        for (String entry : this.selected) {
            if (CurioListScreen.normalize(entry).equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String entry) {
        if (entry == null) return "";
        String text = entry.trim();
        return text.contains(":") ? text : "minecraft:" + text;
    }

    public boolean mouseClicked(double mxd, double myd, int button) {
        int idx;
        if (button == 0 && mxd >= (double)this.listX1 && mxd < (double)this.listX2 && myd >= (double)this.listTop && myd < (double)this.listBot && (idx = (int)((myd - (double)this.listTop + (double)this.scroll) / 26.0)) >= 0 && idx < this.visible.size()) {
            this.toggle(idOf(this.visible.get(idx)));
            return true;
        }
        return super.mouseClicked(mxd, myd, button);
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        this.scroll = (int)Math.max(0.0, Math.min((double)this.scrollMax, (double)this.scroll - delta * 26.0));
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && this.searchBox != null && this.searchBox.isFocused()) {
            this.searchBox.setFocused(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void toggle(String id) {
        String existing = null;
        for (String entry : this.selected) {
            if (!normalize(entry).equals(id)) continue;
            existing = entry;
            break;
        }
        if (existing != null) {
            this.selected.remove(existing);
        } else {
            this.selected.add(id);
        }
        this.selected.sort(Comparator.naturalOrder());
        this.onChange.accept(List.copyOf(this.selected));
    }

    private void clearAll() {
        this.selected.clear();
        this.onChange.accept(List.of());
    }

    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    public boolean isPauseScreen() {
        return false;
    }
}
