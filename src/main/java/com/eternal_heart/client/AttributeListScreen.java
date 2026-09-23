package com.eternal_heart.client;

import com.eternal_heart.core.Numbers;
import com.eternal_heart.features.CustomAttributes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

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
 * <p>列出 {@link ForgeRegistries#ATTRIBUTES} 中的全部属性（原版 + Forge + 其它模组），
 * 点击属性行进入 {@link AttributeValueScreen} 设置数值与操作类型；已配置的行显示当前设置。
 * 与内置属性重复的属性同样可选——两者会各自生效（UUID 不同，互不覆盖）。</p>
 */
public class AttributeListScreen extends Screen {

    private static final int ROW_H = 20;

    // 配色（与配置面板一致：深蓝夜空 + 暗金）
    private static final int COL_OVERLAY = 0xB0040609;
    private static final int COL_PANEL = 0xF60E1521;
    private static final int COL_BORDER = 0xFF9A7B3F;
    private static final int COL_GOLD = 0xFFE8C860;
    private static final int COL_TEXT = 0xFFE6EAF0;
    private static final int COL_TEXT_DIM = 0xFF8A93A0;
    private static final int COL_ROW_ALT = 0x08FFFFFF;
    private static final int COL_ROW_HOVER = 0x16FFFFFF;

    private final Screen parent;
    private final Consumer<List<String>> onChange;
    /** 已配置的属性：ID → 设置（保持插入顺序，写回时排序）。 */
    private final Map<String, CustomAttributes.Spec> selected = new LinkedHashMap<>();
    private final List<ResourceLocation> all;
    private final List<ResourceLocation> visible = new ArrayList<>();
    private EditBox searchBox;
    private Button doneBtn;
    private Button clearBtn;
    private String lastSearch = "";
    private int scroll, scrollMax;
    private int panelX, panelY, panelW, panelH;
    private int listX1, listX2, listTop, listBot;

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
        panelW = Math.max(260, Math.min(this.width - 12, 500));
        panelH = Math.max(140, Math.min(this.height - 12, 440));
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        listX1 = panelX + 8;
        listX2 = panelX + panelW - 8;
        listTop = panelY + 52;
        listBot = panelY + panelH - 28;

        searchBox = new EditBox(this.font, listX1, panelY + 30, panelW - 16, 16,
                Component.translatable("config.eternal_heart.attribute_list.search_hint"));
        searchBox.setHint(Component.translatable("config.eternal_heart.attribute_list.search_hint"));
        searchBox.setMaxLength(48);
        searchBox.setValue(lastSearch);
        searchBox.setResponder(s -> {
            lastSearch = s;
            rebuild();
        });
        this.addRenderableWidget(searchBox);

        doneBtn = this.addRenderableWidget(Button.builder(
                        Component.translatable("config.eternal_heart.attribute_list.done"), b -> onClose())
                .bounds(panelX + panelW - 92, panelY + panelH - 24, 84, 18).build());

        clearBtn = this.addRenderableWidget(Button.builder(
                        Component.translatable("config.eternal_heart.attribute_list.clear"), b -> {
                    selected.clear();
                    push();
                }).bounds(panelX + panelW - 182, panelY + panelH - 24, 84, 18).build());

        rebuild();
    }

    private void rebuild() {
        visible.clear();
        String q = lastSearch == null ? "" : lastSearch.trim().toLowerCase(Locale.ROOT);
        for (ResourceLocation id : all) {
            if (q.isEmpty() || matches(id, q)) visible.add(id);
        }
        scroll = 0;
        scrollMax = Math.max(0, visible.size() * ROW_H - (listBot - listTop));
    }

    private boolean matches(ResourceLocation id, String q) {
        if (id.toString().toLowerCase(Locale.ROOT).contains(q)) return true;
        String key = CustomAttributes.displayNameKey(id);
        return Component.translatable(key).getString().toLowerCase(Locale.ROOT).contains(q);
    }

    // ===================== 渲染 =====================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, COL_OVERLAY);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, COL_PANEL);
        g.fill(panelX, panelY, panelX + panelW, panelY + 1, COL_BORDER);
        g.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, COL_BORDER);
        g.fill(panelX, panelY, panelX + 1, panelY + panelH, COL_BORDER);
        g.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, COL_BORDER);

        g.drawString(this.font, tr("title"), panelX + 8, panelY + 7, COL_GOLD);
        g.drawString(this.font, trim(tr("hint"), panelW - 16), panelX + 8, panelY + 18, COL_TEXT_DIM);

        g.enableScissor(listX1, listTop, listX2, listBot);
        renderRows(g, mx, my);
        g.disableScissor();
        drawScrollbar(g);

        g.drawString(this.font, tr("count", selected.size()),
                panelX + 8, panelY + panelH - 17, COL_TEXT_DIM);
        searchBox.render(g, mx, my, pt);
        doneBtn.render(g, mx, my, pt);
        clearBtn.render(g, mx, my, pt);
    }

    private void renderRows(GuiGraphics g, int mx, int my) {
        for (int i = 0; i < visible.size(); i++) {
            int y = listTop - scroll + i * ROW_H;
            if (y + ROW_H <= listTop || y >= listBot) continue;

            ResourceLocation id = visible.get(i);
            CustomAttributes.Spec spec = selected.get(id.toString());
            boolean hover = mx >= listX1 && mx < listX2 && my >= y && my < y + ROW_H;

            if ((i & 1) == 1) g.fill(listX1, y, listX2, y + ROW_H, COL_ROW_ALT);
            if (hover) g.fill(listX1, y, listX2, y + ROW_H, COL_ROW_HOVER);

            // 左侧色条：已配置 = 金色，未配置 = 灰
            g.fill(listX1 + 2, y + 3, listX1 + 5, y + ROW_H - 3, spec != null ? COL_GOLD : 0x40FFFFFF);

            String name = Component.translatable(CustomAttributes.displayNameKey(id)).getString();
            g.drawString(this.font, trim(name, 170), listX1 + 10, y + 6,
                    spec != null ? COL_GOLD : COL_TEXT);
            g.drawString(this.font, trim(id.toString(), listX2 - listX1 - 224), listX1 + 188, y + 6,
                    COL_TEXT_DIM);

            String status = spec == null ? tr("not_set")
                    : Numbers.format(spec.value()) + " " + CustomAttributes.operationName(spec.operation());
            g.drawString(this.font, trim(status, 120), listX2 - 8 - Math.min(120, this.font.width(status)),
                    y + 6, spec != null ? COL_GOLD : COL_TEXT_DIM);
        }
        if (visible.isEmpty()) {
            g.drawCenteredString(this.font, tr("empty"), (listX1 + listX2) / 2,
                    (listTop + listBot) / 2 - 4, COL_TEXT_DIM);
        }
    }

    private void drawScrollbar(GuiGraphics g) {
        if (scrollMax <= 0) return;
        int trackH = listBot - listTop;
        int thumbH = Math.max(14, (int) ((long) trackH * trackH / Math.max(1, visible.size() * ROW_H)));
        int thumbY = listTop + (trackH - thumbH) * scroll / Math.max(1, scrollMax);
        int tx = panelX + panelW - 5;
        g.fill(tx, listTop, tx + 3, listBot, 0x20FFFFFF);
        g.fill(tx, thumbY, tx + 3, thumbY + thumbH, 0x90E8C860);
    }

    private String tr(String key, Object... args) {
        return Component.translatable("config.eternal_heart.attribute_list." + key, args).getString();
    }

    private String trim(String s, int w) {
        if (w <= 0) return "";
        if (this.font.width(s) <= w) return s;
        return this.font.plainSubstrByWidth(s, Math.max(0, w - 6)) + "…";
    }

    // ===================== 输入处理 =====================

    @Override
    public boolean mouseClicked(double mxd, double myd, int button) {
        if (button == 0 && mxd >= listX1 && mxd < listX2 && myd >= listTop && myd < listBot) {
            int idx = (int) ((myd - listTop + scroll) / ROW_H);
            if (idx >= 0 && idx < visible.size()) {
                Minecraft.getInstance().setScreen(new AttributeValueScreen(this, visible.get(idx)));
                return true;
            }
        }
        return super.mouseClicked(mxd, myd, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = (int) Math.max(0, Math.min(scrollMax, scroll - delta * ROW_H));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && searchBox != null && searchBox.isFocused()) {
            searchBox.setFocused(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
