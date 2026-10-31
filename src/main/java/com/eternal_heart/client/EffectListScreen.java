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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 效果名单选择器 —— 负面效果清除的黑名单 / 白名单可视化编辑界面。
 *
 * <ul>
 *   <li>列出所有已注册效果（含其它模组），按 ID 排序，左侧色条区分 有益 / 负面 / 中性；</li>
 *   <li>搜索框按本地化名称或 ID 过滤；</li>
 *   <li>点击任意行切换「在名单中 / 不在名单中」，改动即时写回父界面草稿；</li>
 *   <li>白名单优先于黑名单（与 {@code Debuffs} 判定一致），界面内提示。</li>
 * </ul>
 */
public class EffectListScreen extends UiScreen {

    private static final int HEADER = 52;
    private static final int FOOTER = 38;
    private static final int ROW_H = 24;

    private final Screen parent;
    private final List<String> selected;
    private final Consumer<List<String>> onChange;
    private final boolean blacklist;
    private final List<ResourceLocation> all;
    private final List<ResourceLocation> visible = new ArrayList<>();

    private UiTextField searchBox;
    private UiList<ResourceLocation> list;
    private String lastSearch = "";

    public EffectListScreen(Screen parent, String fieldName, List<String> initial,
                            Consumer<List<String>> onChange) {
        super(Component.translatable("config.eternal_heart.effect_list.title"));
        this.parent = parent;
        this.selected = new ArrayList<>(initial);
        this.onChange = onChange;
        this.blacklist = fieldName.equals("DEBUFF_BLACKLIST");
        this.all = new ArrayList<>(BuiltInRegistries.MOB_EFFECT.keySet());
        this.all.sort(Comparator.comparing(ResourceLocation::toString));
    }

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
        layoutPanel(640, 500, 340, 280);

        int x1 = contentLeft();
        int x2 = contentRight();

        searchBox = add(new UiTextField(x1, panelY + HEADER + 8, x2 - x1, 20)
                .setHint(Component.translatable("config.eternal_heart.effect_list.search_hint"))
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
                .setOnClick((index, mx, my, button) -> {
                    ResourceLocation id = list.get(index);
                    if (id != null) toggle(id);
                });

        add(UiButton.primary(panelX + panelW - 16 - 76, footerTop() + 8, 76, 22,
                Component.translatable("config.eternal_heart.effect_list.done"), this::onClose));
        add(UiButton.danger(panelX + panelW - 16 - 76 - 8 - 64, footerTop() + 8, 64, 22,
                Component.translatable("config.eternal_heart.effect_list.clear"), this::clearAll));

        rebuild();
    }

    /** 按搜索词重建可见列表。 */
    private void rebuild() {
        visible.clear();
        String query = lastSearch == null ? "" : lastSearch.trim().toLowerCase(Locale.ROOT);
        for (ResourceLocation id : all) {
            if (query.isEmpty() || matches(id, query)) visible.add(id);
        }
        list.setItems(visible);
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
        boolean inList = selected.contains(id.toString());
        UiTheme.cardFade(g, rowX, rowY, rowW, rowH, UiTheme.R_SM, hover, inList);
        // 分类色条：有益 / 负面 / 中性
        UiTheme.roundRect(g, rowX + 6, rowY + 6, 3, rowH - 12, 1, categoryColor(effect));

        String name = effect == null ? id.toString()
                : Component.translatable(effect.getDescriptionId()).getString();
        g.drawString(this.font, trim(name, 180), rowX + 16, rowY + (rowH - 8) / 2,
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

        int idX = rowX + 200;
        int idW = Math.max(24, markX - 12 - idX);
        g.drawString(this.font, trim(id.toString(), idW), idX, rowY + (rowH - 8) / 2,
                UiTheme.TEXT_MUTED, false);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partial) {
        drawHeader(g, Component.translatable(blacklist
                        ? "config.eternal_heart.effect_list.title_blacklist"
                        : "config.eternal_heart.effect_list.title_whitelist"),
                Component.translatable(blacklist
                        ? "config.eternal_heart.effect_list.hint_blacklist"
                        : "config.eternal_heart.effect_list.hint_whitelist"), HEADER);
        renderWidgets(g, mouseX, mouseY, partial);
        drawFooterDivider(g, FOOTER);
        drawStatus(g, tr("count", selected.size()), UiTheme.TEXT_MUTED, panelW - 200);

        int hovered = list == null ? -1 : list.indexAt(mouseX, mouseY);
        if (hovered >= 0) {
            ResourceLocation id = list.get(hovered);
            if (id != null) {
                tooltip(Component.literal(id.toString()).withStyle(style -> style.withColor(UiTheme.ACCENT_DIM)));
            }
        }
    }

    private String tr(String key, Object... args) {
        return Component.translatable("config.eternal_heart.effect_list." + key, args).getString();
    }

    // ===================== 输入处理 =====================

    /** 切换某一效果是否在名单中，并即时写回父界面草稿。 */
    private void toggle(ResourceLocation id) {
        String key = id.toString();
        if (!selected.remove(key)) selected.add(key);
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
