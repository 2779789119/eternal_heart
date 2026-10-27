/*
 * 永恒之心 - 「Curios 饰品槽位扩容」编辑界面
 *
 * 自动读取当前整合包内所有已注册的饰品槽位（CuriosApi.getSlots()，
 * 即整合包里各模组 + Curios 自带 + 永恒之心的全部槽位），
 * 点选一个槽位 → 在底部输入该槽位扩容后的「总格数」（不是增量）。
 * 数量最大可到整数极限 2147483647。存储格式：「槽位ID|数量」。
 */
package com.eternal_heart.client;

import com.eternal_heart.client.ui.UiButton;
import com.eternal_heart.client.ui.UiEditBox;
import com.eternal_heart.client.ui.UiScreen;
import com.eternal_heart.client.ui.UiTheme;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;

public class CurioSlotScreen
extends UiScreen {
    private static final int ROW_H = 22;
    private static final int ROW_STEP = 26;
    private static final int HEADER_H = 52;
    private static final int FOOTER_H = 66;
    private static final long MAX_AMOUNT = 2147483647L;
    private final Screen parent;
    private final Consumer<List<String>> onChange;
    private final Map<String, Integer> entries = new LinkedHashMap<String, Integer>();
    private final List<String> all = new ArrayList<String>();
    private final List<String> visible = new ArrayList<String>();
    private String selectedId;
    private UiEditBox searchBox;
    private UiEditBox amountBox;
    private String lastSearch = "";
    private boolean syncing;
    private int scroll;
    private int scrollMax;
    private int listX1;
    private int listX2;
    private int listTop;
    private int listBot;

    public CurioSlotScreen(Screen parent, List<String> initial, Consumer<List<String>> onChange) {
        super((Component)Component.translatable((String)"config.eternal_heart.slot_list.title"));
        this.parent = parent;
        this.onChange = onChange;
        for (String id : CuriosApi.getSlots().keySet()) {
            this.all.add(id);
        }
        this.all.sort(Comparator.naturalOrder());
        for (String text : initial) {
            int sep = text.lastIndexOf('|');
            if (sep <= 0 || sep == text.length() - 1) continue;
            try {
                long amount = Long.parseLong(text.substring(sep + 1).trim());
                if (amount < 0L) continue;
                if (amount > MAX_AMOUNT) amount = MAX_AMOUNT;
                this.entries.putIfAbsent(text.substring(0, sep).trim(), (int)amount);
            }
            catch (NumberFormatException e) {
                // 忽略无法解析的历史条目
            }
        }
    }

    private static String tr(String key, Object ... args) {
        return Component.translatable((String)("config.eternal_heart.slot_list." + key), (Object[])args).getString();
    }

    private static String displayName(String slotId) {
        String key = "curios.identifier." + slotId;
        return I18n.exists((String)key) ? I18n.get((String)key, (Object[])new Object[0]) : slotId;
    }

    private static int baseSize(String slotId) {
        ISlotType type = CuriosApi.getSlots().get(slotId);
        return type == null ? 0 : type.getSize();
    }

    protected void init() {
        this.layoutPanel(660, 520, 400, 320);
        this.listX1 = this.panelX + 16;
        this.listX2 = this.panelX + this.panelW - 16;
        this.listTop = this.panelY + 52 + 38;
        this.listBot = this.panelY + this.panelH - 66 - 8;
        int footerTop = this.panelY + this.panelH - 66;
        this.searchBox = new UiEditBox(this.font, this.listX1, this.panelY + 52 + 10, this.listX2 - this.listX1, 20, (Component)Component.translatable((String)"config.eternal_heart.slot_list.search_hint"));
        this.searchBox.setMaxLength(48);
        this.searchBox.setValue(this.lastSearch);
        this.searchBox.setResponder(text -> {
            this.lastSearch = text;
            this.rebuild();
        });
        this.addRenderableWidget(this.searchBox);
        int rowY = footerTop + 10;
        int boxX = this.listX1 + this.font.width(tr("amount")) + 8;
        this.amountBox = (UiEditBox)this.addRenderableWidget(new UiEditBox(this.font, boxX, rowY, 120, 18, null).selectAllOnFocus());
        this.amountBox.setMaxLength(10);
        this.amountBox.setFilter(text -> text.isEmpty() || text.matches("\\d{1,10}"));
        this.amountBox.setResponder(this::onAmountTyped);
        this.addRenderableWidget(UiButton.secondary(boxX + 128, rowY, 18, 18, (Component)Component.literal((String)"-"), () -> this.adjustAmount(-1)));
        this.addRenderableWidget(UiButton.secondary(boxX + 148, rowY, 18, 18, (Component)Component.literal((String)"+"), () -> this.adjustAmount(1)));
        int row2Y = footerTop + 36;
        this.addRenderableWidget(UiButton.primary(this.panelX + this.panelW - 16 - 76, row2Y, 76, 22, (Component)Component.translatable((String)"config.eternal_heart.slot_list.done"), this::onClose));
        this.addRenderableWidget(UiButton.danger(this.panelX + this.panelW - 16 - 76 - 8 - 62, row2Y, 62, 22, (Component)Component.translatable((String)"config.eternal_heart.slot_list.clear"), this::clearAll));
        this.addRenderableWidget(UiButton.secondary(this.panelX + this.panelW - 16 - 76 - 8 - 62 - 8 - 62, row2Y, 62, 22, (Component)Component.translatable((String)"config.eternal_heart.slot_list.remove"), this::removeSelected));
        this.rebuild();
        this.syncEditors();
    }

    private void rebuild() {
        this.visible.clear();
        String query = this.lastSearch == null ? "" : this.lastSearch.trim().toLowerCase(Locale.ROOT);
        for (String id : this.all) {
            if (!query.isEmpty() && !id.toLowerCase(Locale.ROOT).contains(query) && !displayName(id).toLowerCase(Locale.ROOT).contains(query)) continue;
            this.visible.add(id);
        }
        this.scroll = 0;
        this.scrollMax = Math.max(0, this.visible.size() * 26 - 6 - (this.listBot - this.listTop));
    }

    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        this.drawPanel(g);
        this.drawHeader(g, (Component)Component.translatable((String)"config.eternal_heart.slot_list.title"), (Component)Component.translatable((String)"config.eternal_heart.slot_list.subtitle"), 52);
        g.enableScissor(this.listX1, this.listTop, this.listX2, this.listBot);
        this.renderRows(g, mx, my);
        g.disableScissor();
        this.drawScrollbar(g, this.listTop, this.listBot, this.scroll, this.scrollMax, Math.max(1, this.visible.size() * 26));
        this.drawFooterDivider(g, 66);
        int footerTop = this.panelY + this.panelH - 66;
        g.drawString(this.font, tr("amount"), this.listX1, footerTop + 15, -9865337);
        g.drawString(this.font, this.footerStatus(), this.listX1, footerTop + 41, this.selectedId == null ? -9865337 : -13773633);
        super.render(g, mx, my, pt);
    }

    private void renderRows(GuiGraphics g, int mx, int my) {
        for (int i = 0; i < this.visible.size(); ++i) {
            int y = this.listTop - this.scroll + i * 26;
            if (y + 22 <= this.listTop || y >= this.listBot) continue;
            String id = this.visible.get(i);
            Integer amount = this.entries.get(id);
            boolean added = amount != null;
            boolean editing = id.equals(this.selectedId);
            boolean hover = mx >= this.listX1 && mx < this.listX2 && my >= y && my < y + 22;
            UiTheme.card(g, this.listX1, y, this.listX2 - this.listX1, 22, 5, hover, editing);
            UiTheme.roundRect(g, this.listX1 + 6, y + 5, 12, 12, 3, added ? -10556716 : -12891557);
            if (added) {
                UiTheme.roundRect(g, this.listX1 + 7, y + 6, 10, 10, 2, -15719649);
                g.drawCenteredString(this.font, "\u2713", this.listX1 + 12, y + 7, -10556716);
            }
            g.drawString(this.font, this.trim(displayName(id), 170), this.listX1 + 26, y + 7, added ? -10556716 : -1511690);
            String state = added ? tr("total", amount) : tr("base", baseSize(id));
            int stateW = this.font.width(state);
            int stateX = this.listX2 - 12 - stateW;
            String idText = this.trim(id, Math.max(24, stateX - 14 - (this.listX1 + 200)));
            g.drawString(this.font, idText, this.listX1 + 200, y + 7, -9865337);
            g.drawString(this.font, state, stateX, y + 7, added ? -6772808 : -9865337);
        }
        if (this.visible.isEmpty()) {
            this.drawEmpty(g, tr("empty"), this.listX1, this.listTop, this.listX2, this.listBot);
        }
    }

    private String footerStatus() {
        if (this.selectedId == null) {
            return tr("pick_hint");
        }
        Integer amount = this.entries.get(this.selectedId);
        if (amount == null) {
            return tr("selected", this.selectedId) + " \u00b7 " + tr("not_added");
        }
        return tr("selected", this.selectedId) + " \u00b7 " + tr("base", baseSize(this.selectedId)) + " \u2192 " + tr("total", amount);
    }

    public boolean mouseClicked(double mxd, double myd, int button) {
        int rowY;
        int index;
        int mx = (int)mxd;
        int my = (int)myd;
        if (button == 0 && mx >= this.listX1 && mx < this.listX2 && my >= this.listTop && my < this.listBot && (index = (my - this.listTop + this.scroll) / 26) >= 0 && index < this.visible.size() && my >= (rowY = this.listTop - this.scroll + index * 26) && my < rowY + 22) {
            this.clickRow(this.visible.get(index), mx < this.listX1 + 26);
            return true;
        }
        return super.mouseClicked(mxd, myd, button);
    }

    private void clickRow(String id, boolean onCheckbox) {
        this.selectedId = id;
        if (!this.entries.containsKey(id)) {
            this.entries.put(id, Math.max(1, baseSize(id)));
            this.push();
        } else if (onCheckbox) {
            this.entries.remove(id);
            if (id.equals(this.selectedId)) {
                this.selectedId = null;
            }
            this.push();
        }
        this.syncEditors();
    }

    private void adjustAmount(int delta) {
        Integer current = this.selectedId == null ? null : this.entries.get(this.selectedId);
        if (current == null) {
            return;
        }
        long next = (long)current + delta;
        if (next < 0L) next = 0L;
        if (next > MAX_AMOUNT) next = MAX_AMOUNT;
        this.entries.put(this.selectedId, (int)next);
        this.push();
        this.syncEditors();
    }

    private void onAmountTyped(String text) {
        if (this.syncing || this.selectedId == null || !this.entries.containsKey(this.selectedId)) {
            return;
        }
        if (text.isEmpty()) {
            return;
        }
        try {
            long amount = Long.parseLong(text.trim());
            if (amount < 0L) amount = 0L;
            if (amount > MAX_AMOUNT) amount = MAX_AMOUNT;
            this.entries.put(this.selectedId, (int)amount);
            this.push();
        }
        catch (NumberFormatException e) {
            // 输入未成形，忽略本次
        }
    }

    private void removeSelected() {
        if (this.selectedId == null) {
            return;
        }
        this.entries.remove(this.selectedId);
        this.selectedId = null;
        this.push();
        this.syncEditors();
    }

    private void clearAll() {
        this.entries.clear();
        this.selectedId = null;
        this.push();
        this.syncEditors();
    }

    private void syncEditors() {
        this.syncing = true;
        Integer amount = this.selectedId == null ? null : this.entries.get(this.selectedId);
        this.amountBox.setValue(amount == null ? "" : String.valueOf(amount));
        this.syncing = false;
    }

    private void push() {
        ArrayList<String> list = new ArrayList<String>();
        for (Map.Entry<String, Integer> e : this.entries.entrySet()) {
            list.add(e.getKey() + "|" + e.getValue());
        }
        this.onChange.accept(List.copyOf(list));
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

    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    public boolean isPauseScreen() {
        return false;
    }
}
