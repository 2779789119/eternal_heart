package com.eternal_heart.client;

import com.eternal_heart.client.ui.UiButton;
import com.eternal_heart.client.ui.UiList;
import com.eternal_heart.client.ui.UiScreen;
import com.eternal_heart.client.ui.UiTextField;
import com.eternal_heart.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 「Curios 饰品槽位扩容」编辑界面。
 *
 * <p>自动读取当前整合包内所有已注册的饰品槽位，点选一个槽位 → 在底部输入该槽位
 * 扩容后的「总格数」（不是增量）。数量最大可到整数极限 2147483647。存储格式：「槽位ID|数量」。</p>
 */
public class CurioSlotScreen extends UiScreen {

    private static final int HEADER = 52;
    private static final int FOOTER = 68;
    private static final int ROW_H = 24;
    private static final long MAX_AMOUNT = 2147483647L;

    private final Screen parent;
    private final Consumer<List<String>> onChange;
    private final Map<String, Integer> entries = new LinkedHashMap<>();
    private final List<String> all = new ArrayList<>();
    private final List<String> visible = new ArrayList<>();

    private String selectedId;
    private UiTextField searchBox;
    private UiTextField amountBox;
    private UiButton minusBtn;
    private UiButton plusBtn;
    private UiButton removeBtn;
    private UiButton clearBtn;
    private UiList<String> list;

    private String lastSearch = "";
    private boolean syncing;

    public CurioSlotScreen(Screen parent, List<String> initial, Consumer<List<String>> onChange) {
        super(Component.translatable("config.eternal_heart.slot_list.title"));
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
                this.entries.putIfAbsent(text.substring(0, sep).trim(), (int) amount);
            } catch (NumberFormatException e) {
                // 忽略无法解析的历史条目
            }
        }
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("config.eternal_heart.slot_list." + key, args).getString();
    }

    private static String displayName(String slotId) {
        String key = "curios.identifier." + slotId;
        return I18n.exists(key) ? I18n.get(key) : slotId;
    }

    private static int baseSize(String slotId) {
        ISlotType type = CuriosApi.getSlots().get(slotId);
        return type == null ? 0 : type.getSize();
    }

    @Override
    protected void init() {
        clearWidgets();
        headerH = HEADER;
        footerH = FOOTER;
        layoutPanel(680, 520, 420, 340);

        int x1 = contentLeft();
        int x2 = contentRight();

        searchBox = add(new UiTextField(x1, panelY + HEADER + 8, x2 - x1, 20)
                .setHint(Component.translatable("config.eternal_heart.slot_list.search_hint"))
                .setMaxLength(48)
                .setText(lastSearch)
                .setResponder(text -> {
                    lastSearch = text;
                    rebuild();
                }));

        int top = searchBox.getY() + 28;
        int bottom = footerTop() - 8;
        list = add(new UiList<String>(x1, top, x2 - x1, bottom - top))
                .setRowMetrics(ROW_H, 2)
                .setEmptyText(tr("empty"))
                .setPainter(this::paintRow)
                .setOnClick(this::clickRow);

        // 底栏第一行：总格数 [-][输入][+]
        int row1 = footerTop() + 10;
        int amountX = x1 + this.font.width(tr("amount")) + 10;
        amountBox = add(new UiTextField(amountX, row1 - 1, 120, 20)
                .setMaxLength(10)
                .setFilter(text -> text.isEmpty() || text.matches("\\d{1,10}"))
                .setResponder(this::onAmountTyped)
                .selectAllOnFocus());
        minusBtn = add(UiButton.square(amountX + 126, row1 - 1, 20, Component.literal("-"),
                () -> adjustAmount(-1)));
        plusBtn = add(UiButton.square(amountX + 150, row1 - 1, 20, Component.literal("+"),
                () -> adjustAmount(1)));

        // 底栏第二行：左侧状态文字，右侧操作
        int row2 = footerTop() + 36;
        add(UiButton.primary(panelX + panelW - 16 - 76, row2, 76, 22,
                Component.translatable("config.eternal_heart.slot_list.done"), this::onClose));
        clearBtn = add(UiButton.danger(panelX + panelW - 16 - 76 - 8 - 62, row2, 62, 22,
                Component.translatable("config.eternal_heart.slot_list.clear"), this::clearAll));
        removeBtn = add(UiButton.secondary(panelX + panelW - 16 - 76 - 8 - 62 - 8 - 62, row2, 62, 22,
                Component.translatable("config.eternal_heart.slot_list.remove"), this::removeSelected));

        rebuild();
        syncEditors();
        updateStates();
    }

    private void rebuild() {
        visible.clear();
        String query = lastSearch == null ? "" : lastSearch.trim().toLowerCase(Locale.ROOT);
        for (String id : all) {
            if (!query.isEmpty() && !id.toLowerCase(Locale.ROOT).contains(query)
                    && !displayName(id).toLowerCase(Locale.ROOT).contains(query)) continue;
            visible.add(id);
        }
        list.setItems(visible);
        list.setSelected(visible.indexOf(selectedId));
    }

    // ===================== 渲染 =====================

    private void paintRow(GuiGraphics g, String id, int index, int rowX, int rowY, int rowW, int rowH,
                          float hover, boolean active) {
        Integer amount = entries.get(id);
        boolean added = amount != null;
        boolean editing = id.equals(selectedId);
        UiTheme.cardFade(g, rowX, rowY, rowW, rowH, UiTheme.R_SM, hover, editing);

        // 勾选框
        int boxX = rowX + 6, boxY = rowY + (rowH - 14) / 2, box = 14;
        UiTheme.roundRect(g, boxX, boxY, box, box, 4, added ? UiTheme.ACCENT : UiTheme.BORDER_STRONG);
        if (added) {
            UiTheme.roundRect(g, boxX + 1, boxY + 1, box - 2, box - 2, 3, 0xFF10231F);
            UiTheme.check(g, boxX + 3, boxY + 3, box - 6, UiTheme.ACCENT);
        }

        g.drawString(this.font, trim(displayName(id), 170), rowX + 28, rowY + (rowH - 8) / 2,
                added ? UiTheme.ACCENT : UiTheme.TEXT, false);

        String state = added ? tr("total", amount) : tr("base", baseSize(id));
        int stateW = this.font.width(state);
        int stateX = rowX + rowW - 12 - stateW;
        g.drawString(this.font, state, stateX, rowY + (rowH - 8) / 2,
                added ? UiTheme.ACCENT_DIM : UiTheme.TEXT_MUTED, false);

        int idX = rowX + 206;
        g.drawString(this.font, trim(id, Math.max(24, stateX - 14 - idX)),
                idX, rowY + (rowH - 8) / 2, UiTheme.TEXT_MUTED, false);
    }

    private String footerStatus() {
        if (selectedId == null) return tr("pick_hint");
        Integer amount = entries.get(selectedId);
        if (amount == null) {
            return tr("selected", selectedId) + " · " + tr("not_added");
        }
        return tr("selected", selectedId) + " · " + tr("base", baseSize(selectedId))
                + " → " + tr("total", amount);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partial) {
        drawHeader(g, Component.translatable("config.eternal_heart.slot_list.title"),
                Component.translatable("config.eternal_heart.slot_list.subtitle"), HEADER);
        renderWidgets(g, mouseX, mouseY, partial);
        drawFooterDivider(g, FOOTER);

        g.drawString(this.font, tr("amount"), contentLeft(), footerTop() + 15,
                UiTheme.TEXT_DIM, false);
        // 状态文字在第二行左侧，与右下角按钮同一水平线
        g.drawString(this.font, UiTheme.trim(this.font, footerStatus(), panelW - 280),
                contentLeft(), footerTop() + 42,
                selectedId == null ? UiTheme.TEXT_MUTED : UiTheme.ACCENT_DIM, false);

        int hovered = list == null ? -1 : list.indexAt(mouseX, mouseY);
        if (hovered >= 0) {
            String id = list.get(hovered);
            if (id != null) {
                tooltip(Component.literal(id).withStyle(style -> style.withColor(UiTheme.ACCENT)),
                        Component.literal(tr("base", baseSize(id)))
                                .withStyle(style -> style.withColor(UiTheme.TEXT_DIM)));
            }
        }
    }

    // ===================== 交互 =====================

    private void clickRow(int index, double mouseX, double mouseY, int button) {
        String id = list.get(index);
        if (id == null) return;
        // 左侧 26px 是勾选框区域：点它 = 移除
        boolean onCheckbox = mouseX < list.getX() + 26;
        selectedId = id;
        if (!entries.containsKey(id)) {
            entries.put(id, Math.max(1, baseSize(id)));
            push();
        } else if (onCheckbox) {
            entries.remove(id);
            selectedId = null;
            push();
        }
        list.setSelected(visible.indexOf(selectedId));
        syncEditors();
        updateStates();
    }

    private void adjustAmount(int delta) {
        Integer current = selectedId == null ? null : entries.get(selectedId);
        if (current == null) return;
        long next = (long) current + delta;
        if (next < 0L) next = 0L;
        if (next > MAX_AMOUNT) next = MAX_AMOUNT;
        entries.put(selectedId, (int) next);
        push();
        syncEditors();
    }

    private void onAmountTyped(String text) {
        if (syncing || selectedId == null || !entries.containsKey(selectedId)) return;
        if (text.isEmpty()) return;
        try {
            long amount = Long.parseLong(text.trim());
            if (amount < 0L) amount = 0L;
            if (amount > MAX_AMOUNT) amount = MAX_AMOUNT;
            entries.put(selectedId, (int) amount);
            push();
        } catch (NumberFormatException e) {
            // 输入未成形，忽略本次
        }
    }

    private void removeSelected() {
        if (selectedId == null) return;
        entries.remove(selectedId);
        selectedId = null;
        push();
        list.setSelected(-1);
        syncEditors();
        updateStates();
    }

    private void clearAll() {
        entries.clear();
        selectedId = null;
        push();
        list.setSelected(-1);
        syncEditors();
        updateStates();
    }

    private void syncEditors() {
        syncing = true;
        Integer amount = selectedId == null ? null : entries.get(selectedId);
        amountBox.setText(amount == null ? "" : String.valueOf(amount));
        syncing = false;
        updateStates();
    }

    private void updateStates() {
        boolean has = selectedId != null && entries.containsKey(selectedId);
        amountBox.setEnabled(has);
        minusBtn.setEnabled(has);
        plusBtn.setEnabled(has);
        removeBtn.setEnabled(selectedId != null);
        clearBtn.setEnabled(!entries.isEmpty());
    }

    private void push() {
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : entries.entrySet()) {
            list.add(entry.getKey() + "|" + entry.getValue());
        }
        onChange.accept(List.copyOf(list));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
