package com.eternal_heart.client;

import com.eternal_heart.client.ui.UiButton;
import com.eternal_heart.client.ui.UiList;
import com.eternal_heart.client.ui.UiScreen;
import com.eternal_heart.client.ui.UiTextField;
import com.eternal_heart.client.ui.UiTheme;
import com.eternal_heart.core.Numbers;
import com.eternal_heart.features.CustomAttributes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 自定义属性选择器 —— 让「永恒之心」能加成任意已注册属性（含其它模组）。
 *
 * <p>列出 {@link ForgeRegistries#ATTRIBUTES} 中的全部属性，点击属性行进入
 * {@link AttributeValueScreen} 设置数值与操作类型；已配置的行显示当前设置。</p>
 */
public class AttributeListScreen extends UiScreen {

    private static final int HEADER = 52;
    private static final int FOOTER = 38;
    private static final int ROW_H = 24;

    private final Screen parent;
    private final Consumer<List<String>> onChange;
    /** 已配置的属性：ID → 设置（保持插入顺序，写回时排序）。 */
    private final Map<String, CustomAttributes.Spec> selected = new LinkedHashMap<>();
    private final List<ResourceLocation> all;
    private final List<ResourceLocation> visible = new ArrayList<>();

    private UiTextField searchBox;
    private UiList<ResourceLocation> list;
    private String lastSearch = "";

    public AttributeListScreen(Screen parent, List<String> initial, Consumer<List<String>> onChange) {
        super(Component.translatable("config.eternal_heart.attribute_list.title"));
        this.parent = parent;
        this.onChange = onChange;
        for (CustomAttributes.Spec spec : CustomAttributes.parse(initial)) {
            selected.put(spec.attribute().toString(), spec);
        }
        this.all = new ArrayList<>(ForgeRegistries.ATTRIBUTES.getKeys());
        this.all.sort(Comparator.comparing(ResourceLocation::toString));
    }

    /** 当前某个属性的设置；未配置返回 null。 */
    CustomAttributes.Spec spec(ResourceLocation id) {
        return selected.get(id.toString());
    }

    /** 写入一条设置（供数值弹层调用），并立即回写父界面草稿。 */
    void put(ResourceLocation id, double value, AttributeModifier.Operation operation) {
        selected.put(id.toString(), new CustomAttributes.Spec(id, value, operation));
        push();
    }

    void remove(ResourceLocation id) {
        selected.remove(id.toString());
        push();
    }

    private void push() {
        List<String> list = new ArrayList<>(selected.size());
        for (CustomAttributes.Spec spec : selected.values()) {
            list.add(CustomAttributes.format(spec.attribute(), spec.value(), spec.operation()));
        }
        list.sort(Comparator.naturalOrder());
        onChange.accept(List.copyOf(list));
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
                .setHint(Component.translatable("config.eternal_heart.attribute_list.search_hint"))
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
                    if (id != null) Minecraft.getInstance().setScreen(new AttributeValueScreen(this, id));
                });

        add(UiButton.primary(panelX + panelW - 16 - 76, footerTop() + 8, 76, 22,
                Component.translatable("config.eternal_heart.attribute_list.done"), this::onClose));
        add(UiButton.danger(panelX + panelW - 16 - 76 - 8 - 64, footerTop() + 8, 64, 22,
                Component.translatable("config.eternal_heart.attribute_list.clear"), () -> {
                    selected.clear();
                    push();
                }));

        rebuild();
    }

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
        String key = CustomAttributes.displayNameKey(id);
        return Component.translatable(key).getString().toLowerCase(Locale.ROOT).contains(query);
    }

    // ===================== 渲染 =====================

    private void paintRow(GuiGraphics g, ResourceLocation id, int index, int rowX, int rowY, int rowW, int rowH,
                          float hover, boolean active) {
        CustomAttributes.Spec spec = selected.get(id.toString());
        boolean configured = spec != null;
        UiTheme.cardFade(g, rowX, rowY, rowW, rowH, UiTheme.R_SM, hover, configured);
        // 左侧色条：已配置 = 青绿，未配置 = 灰
        UiTheme.roundRect(g, rowX + 6, rowY + 6, 3, rowH - 12, 1,
                configured ? UiTheme.ACCENT : 0x40FFFFFF);

        String name = Component.translatable(CustomAttributes.displayNameKey(id)).getString();
        g.drawString(this.font, trim(name, 180), rowX + 16, rowY + (rowH - 8) / 2,
                configured ? UiTheme.ACCENT : UiTheme.TEXT, false);

        String status = configured
                ? Numbers.format(spec.value()) + " " + CustomAttributes.operationName(spec.operation())
                : tr("not_set");
        int statusW = Math.min(150, this.font.width(status));
        int statusX = rowX + rowW - 12 - statusW;
        if (configured) {
            UiTheme.chip(g, statusX - 7, rowY + (rowH - 14) / 2, statusW + 14, 14,
                    UiTheme.ACCENT_SOFT, UiTheme.withAlpha(UiTheme.ACCENT_DIM, 0x66));
        }
        g.drawString(this.font, trim(status, 150), statusX, rowY + (rowH - 8) / 2,
                configured ? UiTheme.ACCENT : UiTheme.TEXT_MUTED, false);

        // ID 填满名称与状态之间的空隙
        int idX = rowX + 200;
        int idW = Math.max(24, statusX - 12 - idX);
        g.drawString(this.font, trim(id.toString(), idW), idX, rowY + (rowH - 8) / 2,
                UiTheme.TEXT_MUTED, false);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partial) {
        drawHeader(g, Component.translatable("config.eternal_heart.attribute_list.title"),
                Component.translatable("config.eternal_heart.attribute_list.hint"), HEADER);
        renderWidgets(g, mouseX, mouseY, partial);
        drawFooterDivider(g, FOOTER);
        drawStatus(g, tr("count", selected.size()), UiTheme.TEXT_MUTED, panelW - 200);
    }

    private String tr(String key, Object... args) {
        return Component.translatable("config.eternal_heart.attribute_list." + key, args).getString();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
