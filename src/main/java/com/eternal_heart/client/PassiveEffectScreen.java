package com.eternal_heart.client;

import com.eternal_heart.client.ui.UiButton;
import com.eternal_heart.client.ui.UiList;
import com.eternal_heart.client.ui.UiScreen;
import com.eternal_heart.client.ui.UiTextField;
import com.eternal_heart.client.ui.UiTheme;
import com.eternal_heart.client.ui.UiWidget;
import com.eternal_heart.core.Debuffs;
import com.eternal_heart.core.PassiveEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 附加被动效果选择器 —— 「被动药水效果」分类里那个「编辑」按钮打开的页面。
 *
 * <p>与黑 / 白名单选择器（{@link EffectListScreen}）的区别：这里每条效果还带
 * <b>等级</b>与<b>时长</b>：列表点击加入 / 选中，勾选框移除；底部编辑区调整等级（1 = I 级）
 * 与时长（秒；勾「永久」写入 -1 无限时长）。改动即时写回父面板草稿。</p>
 *
 * <p>被「负面效果拦截」挡住的效果（HARMFUL 分类 / 黑名单）在行末显示警告：
 * 它们配置了也不会生效，需要先在防御分类里加入白名单。</p>
 */
public class PassiveEffectScreen extends UiScreen {

    private static final int HEADER = 52;
    private static final int FOOTER = 86;
    private static final int ROW_H = 24;

    /** 单条配置的编辑状态（等级从 1 开始，时长 tick / -1 永久）。 */
    private static final class Spec {
        int level;
        int duration;

        Spec(int level, int duration) {
            this.level = level;
            this.duration = duration;
        }
    }

    private final Screen parent;
    private final Consumer<List<String>> onChange;
    private final Map<String, Spec> entries = new LinkedHashMap<>();
    private final List<ResourceLocation> all = new ArrayList<>();
    private final List<ResourceLocation> visible = new ArrayList<>();

    private String selectedId;
    private UiTextField searchBox;
    private UiTextField levelBox;
    private UiTextField durationBox;
    private UiButton levelDown;
    private UiButton levelUp;
    private UiButton permanentBtn;
    private UiButton removeBtn;
    private UiButton clearBtn;
    private UiList<ResourceLocation> list;

    private String lastSearch = "";
    private boolean syncing;
    private int labelLevelX, labelDurationX, labelSecondsX, row1Y;

    public PassiveEffectScreen(Screen parent, List<String> initial, Consumer<List<String>> onChange) {
        super(Component.translatable("config.eternal_heart.passive_list.title"));
        this.parent = parent;
        this.onChange = onChange;
        this.all.addAll(BuiltInRegistries.MOB_EFFECT.keySet());
        this.all.sort(Comparator.comparing(ResourceLocation::toString));
        for (String text : initial) {
            PassiveEffects.Entry entry = PassiveEffects.parse(text);
            if (entry == null) continue;
            ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(entry.effect());
            if (id == null) continue;
            entries.putIfAbsent(id.toString(), new Spec(entry.amplifier() + 1, entry.duration()));
        }
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("config.eternal_heart.passive_list." + key, args).getString();
    }

    /** 行左侧色条：有益 / 负面 / 中性。 */
    private static int categoryColor(MobEffect effect) {
        if (effect == null) return 0xFF8A94A2;
        return switch (effect.getCategory()) {
            case HARMFUL -> UiTheme.DANGER;
            case BENEFICIAL -> UiTheme.SUCCESS;
            default -> 0xFF8A94A2;
        };
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
                .setHint(Component.translatable("config.eternal_heart.passive_list.search_hint"))
                .setMaxLength(48)
                .setText(lastSearch)
                .setResponder(text -> {
                    lastSearch = text;
                    rebuild();
                }));

        int top = searchBox.getY() + 28;
        int bottom = footerTop() - 8;
        list = add(new UiList<ResourceLocation>(x1, top, x2 - x1, bottom - top))
                .setRowMetrics(ROW_H, 2)
                .setEmptyText(tr("empty"))
                .setPainter(this::paintRow)
                .setOnClick(this::clickRow);

        // 编辑区第一行：等级 [-] [输入] [+]   时长 [输入] 秒   [永久]
        row1Y = footerTop() + 12;
        int cursor = x1;
        labelLevelX = cursor;
        cursor += this.font.width(tr("level")) + 10;
        levelDown = add(UiButton.square(cursor, row1Y - 1, 20, Component.literal("-"),
                () -> adjustLevel(-1)));
        cursor += 24;
        levelBox = add(new UiTextField(cursor, row1Y - 1, 40, 20)
                .setMaxLength(3)
                .setFilter(text -> text.isEmpty() || text.matches("\\d{1,3}"))
                .setResponder(this::onLevelTyped)
                .selectAllOnFocus());
        cursor += 44;
        levelUp = add(UiButton.square(cursor, row1Y - 1, 20, Component.literal("+"),
                () -> adjustLevel(1)));

        cursor = x1 + 170;
        labelDurationX = cursor;
        cursor += this.font.width(tr("duration")) + 10;
        durationBox = add(new UiTextField(cursor, row1Y - 1, 68, 20)
                .setMaxLength(8)
                .setFilter(text -> text.isEmpty() || text.matches("\\d{0,5}(\\.\\d?)?"))
                .setResponder(this::onDurationTyped)
                .selectAllOnFocus());
        cursor += 74;
        labelSecondsX = cursor;
        cursor += this.font.width(tr("seconds")) + 10;
        permanentBtn = add(UiButton.segment(cursor, row1Y - 1, 60, 20,
                Component.translatable("config.eternal_heart.passive_list.permanent"),
                this::togglePermanent));

        // 编辑区第二行：左侧状态文字 + 右侧操作
        int row2 = footerTop() + 46;
        add(UiButton.primary(panelX + panelW - 16 - 76, row2, 76, 22,
                Component.translatable("config.eternal_heart.passive_list.done"), this::onClose));
        clearBtn = add(UiButton.danger(panelX + panelW - 16 - 76 - 8 - 62, row2, 62, 22,
                Component.translatable("config.eternal_heart.passive_list.clear"), this::clearAll));
        removeBtn = add(UiButton.secondary(panelX + panelW - 16 - 76 - 8 - 62 - 8 - 62, row2, 62, 22,
                Component.translatable("config.eternal_heart.passive_list.remove"), this::removeSelected));

        rebuild();
        syncEditors();
        updateStates();
    }

    /** 按搜索词重建可见列表。 */
    private void rebuild() {
        visible.clear();
        String query = lastSearch == null ? "" : lastSearch.trim().toLowerCase(Locale.ROOT);
        for (ResourceLocation id : all) {
            if (query.isEmpty() || matches(id, query)) visible.add(id);
        }
        list.setItems(visible);
        list.setSelected(indexOfSelected());
    }

    /** 选中项在可见列表里的下标（按 id 字符串匹配，避免 ResourceLocation 重新解析）。 */
    private int indexOfSelected() {
        if (selectedId == null) return -1;
        for (int i = 0; i < visible.size(); i++) {
            if (visible.get(i).toString().equals(selectedId)) return i;
        }
        return -1;
    }

    private boolean matches(ResourceLocation id, String query) {
        if (id.toString().toLowerCase(Locale.ROOT).contains(query)) return true;
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);
        return effect != null && Component.translatable(effect.getDescriptionId()).getString()
                .toLowerCase(Locale.ROOT).contains(query);
    }

    // ===================== 渲染 =====================

    private void paintRow(GuiGraphics g, ResourceLocation id, int index, int rowX, int rowY, int rowW, int rowH,
                          float hover, boolean active) {
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);
        Spec spec = entries.get(id.toString());
        boolean added = spec != null;
        boolean editing = id.toString().equals(selectedId);
        UiTheme.cardFade(g, rowX, rowY, rowW, rowH, UiTheme.R_SM, hover, editing);
        UiTheme.roundRect(g, rowX + 6, rowY + 6, 3, rowH - 12, 1, categoryColor(effect));

        // 勾选框
        int boxX = rowX + 14, boxY = rowY + (rowH - 14) / 2, box = 14;
        UiTheme.roundRect(g, boxX, boxY, box, box, 4, added ? UiTheme.ACCENT : UiTheme.BORDER_STRONG);
        if (added) {
            UiTheme.roundRect(g, boxX + 1, boxY + 1, box - 2, box - 2, 3, 0xFF10231F);
            UiTheme.check(g, boxX + 3, boxY + 3, box - 6, UiTheme.ACCENT);
        }

        String name = effect == null ? id.toString()
                : Component.translatable(effect.getDescriptionId()).getString();
        boolean blocked = effect != null && Debuffs.blocked(effect);
        g.drawString(this.font, trim(name, 176), rowX + 34, rowY + (rowH - 8) / 2,
                blocked ? UiTheme.WARN : (added ? UiTheme.ACCENT : UiTheme.TEXT), false);

        String state = spec == null ? "" : tr("level_short", spec.level) + " · " + durationText(spec.duration);
        int stateW = this.font.width(state);
        int stateX = rowX + rowW - 12 - stateW;
        if (blocked) {
            UiTheme.chip(g, stateX - 22, rowY + (rowH - 14) / 2, 16, 14,
                    0x33F87171, 0x66F87171);
            g.drawString(this.font, "!", stateX - 17, rowY + (rowH - 8) / 2, UiTheme.DANGER, false);
        }
        int idX = rowX + 214;
        g.drawString(this.font, trim(id.toString(), Math.max(24, stateX - 26 - idX)),
                idX, rowY + (rowH - 8) / 2, UiTheme.TEXT_MUTED, false);
        if (!state.isEmpty()) {
            g.drawString(this.font, state, stateX, rowY + (rowH - 8) / 2,
                    blocked ? UiTheme.WARN : UiTheme.TEXT_DIM, false);
        }
    }

    private String footerStatus() {
        Spec spec = currentSpec();
        if (spec == null) return tr("pick_hint");
        ResourceLocation id = ResourceLocation.tryParse(selectedId);
        MobEffect effect = id == null ? null : BuiltInRegistries.MOB_EFFECT.get(id);
        String name = effect == null ? String.valueOf(selectedId)
                : Component.translatable(effect.getDescriptionId()).getString();
        String base = tr("selected", name);
        if (effect != null && Debuffs.blocked(effect)) return base + " · " + tr("blocked");
        return base;
    }

    private String durationText(int ticks) {
        if (ticks < 0) return tr("permanent_short");
        return secondsText(ticks) + tr("seconds");
    }

    private static String secondsText(int ticks) {
        double seconds = ticks / 20.0;
        return seconds == Math.rint(seconds)
                ? String.valueOf((int) seconds)
                : String.format(Locale.ROOT, "%.1f", seconds);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partial) {
        drawHeader(g, Component.translatable("config.eternal_heart.passive_list.title"),
                Component.translatable("config.eternal_heart.passive_list.subtitle"), HEADER);
        renderWidgets(g, mouseX, mouseY, partial);
        drawFooterDivider(g, FOOTER);

        int labelY = row1Y + 5;
        g.drawString(this.font, tr("level"), labelLevelX, labelY, UiTheme.TEXT_DIM, false);
        g.drawString(this.font, tr("duration"), labelDurationX, labelY, UiTheme.TEXT_DIM, false);
        g.drawString(this.font, tr("seconds"), labelSecondsX, labelY, UiTheme.TEXT_DIM, false);
        // 状态文字放在编辑区第二行左侧（与右下角按钮同一水平线，不会压住上面的输入框）
        g.drawString(this.font, UiTheme.trim(this.font, footerStatus(), panelW - 300),
                contentLeft(), row1Y + 51,
                selectedId == null ? UiTheme.TEXT_MUTED : UiTheme.ACCENT_DIM, false);

        int hovered = list == null ? -1 : list.indexAt(mouseX, mouseY);
        if (hovered >= 0) {
            ResourceLocation id = list.get(hovered);
            if (id != null) {
                MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);
                Component name = effect == null ? Component.literal(id.toString())
                        : Component.translatable(effect.getDescriptionId());
                tooltip(name.copy().withStyle(style -> style.withColor(UiTheme.ACCENT)),
                        Component.literal(id.toString())
                                .withStyle(style -> style.withColor(UiTheme.TEXT_MUTED)),
                        Component.literal(tr("level_short", Math.max(1, entries.containsKey(id.toString())
                                        ? entries.get(id.toString()).level : 1))
                                + " · " + (entries.containsKey(id.toString())
                                ? durationText(entries.get(id.toString()).duration) : "-"))
                                .withStyle(style -> style.withColor(UiTheme.TEXT_DIM)));
            }
        }
    }

    // ===================== 交互 =====================

    private void clickRow(int index, double mouseX, double mouseY, int button) {
        ResourceLocation id = list.get(index);
        if (id == null) return;
        String key = id.toString();
        boolean onCheckbox = mouseX < list.getX() + 32;
        if (!entries.containsKey(key)) {
            entries.put(key, new Spec(1, -1));
            selectedId = key;
            push();
        } else if (onCheckbox) {
            entries.remove(key);
            if (key.equals(selectedId)) selectedId = null;
            push();
        } else {
            selectedId = key;
        }
        list.setSelected(visible.indexOf(id));
        syncEditors();
        updateStates();
    }

    private void adjustLevel(int delta) {
        Spec spec = currentSpec();
        if (spec == null) return;
        spec.level = Math.max(1, Math.min(PassiveEffects.MAX_LEVEL, spec.level + delta));
        push();
        syncEditors();
    }

    private void togglePermanent() {
        Spec spec = currentSpec();
        if (spec == null) return;
        // 永久 ↔ 30 秒：切回有限时长时给一个能立刻感知的默认值
        spec.duration = spec.duration < 0 ? 20 * 30 : -1;
        push();
        syncEditors();
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

    private void onLevelTyped(String text) {
        if (syncing) return;
        Spec spec = currentSpec();
        if (spec == null) return;
        try {
            int level = Integer.parseInt(text.trim());
            spec.level = Math.max(1, Math.min(PassiveEffects.MAX_LEVEL, level));
            push();
        } catch (NumberFormatException ignored) {
            // 输入中间态（空 / 非法）：保留旧值，失焦时回填
        }
    }

    private void onDurationTyped(String text) {
        if (syncing) return;
        Spec spec = currentSpec();
        if (spec == null || spec.duration < 0) return;
        try {
            double seconds = Double.parseDouble(text.trim());
            if (!Double.isFinite(seconds) || seconds <= 0) return;
            spec.duration = (int) Math.min(PassiveEffects.MAX_DURATION, Math.max(1, Math.round(seconds * 20)));
            push();
        } catch (NumberFormatException ignored) {
            // 同上
        }
    }

    /** 把缓存的 Spec 写回输入框（切换选中 / 再次进入时调用）。 */
    private void syncEditors() {
        syncing = true;
        try {
            Spec spec = currentSpec();
            if (spec == null) {
                levelBox.setText("");
                durationBox.setText("");
            } else {
                levelBox.setText(String.valueOf(spec.level));
                durationBox.setText(spec.duration < 0 ? "" : secondsText(spec.duration));
            }
            boolean permanent = spec != null && spec.duration < 0;
            permanentBtn.setSelected(permanent);
        } finally {
            syncing = false;
        }
    }

    private void updateStates() {
        Spec spec = currentSpec();
        boolean hasSelection = spec != null;
        boolean permanent = hasSelection && spec.duration < 0;
        levelBox.setEnabled(hasSelection);
        levelDown.setEnabled(hasSelection);
        levelUp.setEnabled(hasSelection);
        durationBox.setEnabled(hasSelection && !permanent);
        permanentBtn.setEnabled(hasSelection);
        removeBtn.setEnabled(hasSelection);
        clearBtn.setEnabled(!entries.isEmpty());
    }

    private Spec currentSpec() {
        return selectedId == null ? null : entries.get(selectedId);
    }

    /** 写回父面板草稿（每次改动立即同步，父面板保存时统一提交）。 */
    private void push() {
        List<String> list = new ArrayList<>(entries.size());
        for (Map.Entry<String, Spec> entry : entries.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id == null) continue;
            Spec spec = entry.getValue();
            list.add(PassiveEffects.format(id, spec.level, spec.duration));
        }
        onChange.accept(List.copyOf(list));
    }

    /** 输入框失焦：把中间态（空 / 半截数字）规整回缓存值。 */
    @Override
    protected void onFocusChanged(UiWidget previous, UiWidget current) {
        if (previous == levelBox || previous == durationBox) syncEditors();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
