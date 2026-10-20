package com.eternal_heart.client;

import com.eternal_heart.client.ui.UiButton;
import com.eternal_heart.client.ui.UiEditBox;
import com.eternal_heart.client.ui.UiScreen;
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
public class AttributeListScreen extends UiScreen {

    private static final int ROW_H = 22;
    private static final int ROW_STEP = 26;
    private static final int HEADER_H = 52;
    private static final int FOOTER_H = 38;

    private final Screen parent;
    private final Consumer<List<String>> onChange;
    /** 已配置的属性：ID → 设置（保持插入顺序，写回时排序）。 */
    private final Map<String, CustomAttributes.Spec> selected = new LinkedHashMap<>();
    private final List<ResourceLocation> all;
    private final List<ResourceLocation> visible = new ArrayList<>();
    private UiEditBox searchBox;
    private UiButton doneBtn;
    private UiButton clearBtn;
    private String lastSearch = "";
    private int scroll, scrollMax;
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
        layoutPanel(620, 500, 320, 260);
        listX1 = panelX + 16;
        listX2 = panelX + panelW - 16;
        listTop = panelY + HEADER_H + 38;
        listBot = panelY + panelH - FOOTER_H - 8;

        searchBox = new UiEditBox(this.font, listX1, panelY + HEADER_H + 10, listX2 - listX1, 20,
                Component.translatable("config.eternal_heart.attribute_list.search_hint"));
        searchBox.setMaxLength(48);
        searchBox.setValue(lastSearch);
        searchBox.setResponder(s -> {
            lastSearch = s;
            rebuild();
        });
        this.addRenderableWidget(searchBox);

        int footerTop = panelY + panelH - FOOTER_H;
        doneBtn = this.addRenderableWidget(UiButton.primary(
                panelX + panelW - 16 - 76, footerTop + 8, 76, 22,
                Component.translatable("config.eternal_heart.attribute_list.done"), this::onClose));

        clearBtn = this.addRenderableWidget(UiButton.danger(
                panelX + panelW - 16 - 76 - 8 - 64, footerTop + 8, 64, 22,
                Component.translatable("config.eternal_heart.attribute_list.clear"), () -> {
                    selected.clear();
                    push();
                }));

        rebuild();
    }

    private void rebuild() {
        visible.clear();
        String q = lastSearch == null ? "" : lastSearch.trim().toLowerCase(Locale.ROOT);
        for (ResourceLocation id : all) {
            if (q.isEmpty() || matches(id, q)) visible.add(id);
        }
        scroll = 0;
        scrollMax = Math.max(0, visible.size() * ROW_STEP - 6 - (listBot - listTop));
    }

    private boolean matches(ResourceLocation id, String q) {
        if (id.toString().toLowerCase(Locale.ROOT).contains(q)) return true;
        String key = CustomAttributes.displayNameKey(id);
        return Component.translatable(key).getString().toLowerCase(Locale.ROOT).contains(q);
    }

    // ===================== 渲染 =====================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        drawPanel(g);
        drawHeader(g, Component.translatable("config.eternal_heart.attribute_list.title"),
                Component.translatable("config.eternal_heart.attribute_list.hint"), HEADER_H);

        g.enableScissor(listX1, listTop, listX2, listBot);
        renderRows(g, mx, my);
        g.disableScissor();
        drawScrollbar(g, listTop, listBot, scroll, scrollMax, Math.max(1, visible.size() * ROW_STEP));

        drawFooterDivider(g, FOOTER_H);
        g.drawString(this.font, tr("count", selected.size()),
                panelX + 16, panelY + panelH - FOOTER_H + 15, UiTheme.TEXT_MUTED);

        super.render(g, mx, my, pt);
    }

    private void renderRows(GuiGraphics g, int mx, int my) {
        for (int i = 0; i < visible.size(); i++) {
            int y = listTop - scroll + i * ROW_STEP;
            if (y + ROW_H <= listTop || y >= listBot) continue;

            ResourceLocation id = visible.get(i);
            CustomAttributes.Spec spec = selected.get(id.toString());
            boolean hover = mx >= listX1 && mx < listX2 && my >= y && my < y + ROW_H;

            UiTheme.card(g, listX1, y, listX2 - listX1, ROW_H, 5, hover, spec != null);
            // 左侧色条：已配置 = 青绿，未配置 = 灰
            UiTheme.roundRect(g, listX1 + 6, y + 6, 3, ROW_H - 12, 1,
                    spec != null ? UiTheme.ACCENT : 0x40FFFFFF);

            String name = Component.translatable(CustomAttributes.displayNameKey(id)).getString();
            g.drawString(this.font, trim(name, 190), listX1 + 16, y + 7,
                    spec != null ? UiTheme.ACCENT : UiTheme.TEXT);
            g.drawString(this.font, trim(id.toString(), listX2 - listX1 - 320), listX1 + 216, y + 7,
                    UiTheme.TEXT_MUTED);

            String status = spec == null ? tr("not_set")
                    : Numbers.format(spec.value()) + " " + CustomAttributes.operationName(spec.operation());
            int statusW = Math.min(150, this.font.width(status));
            int statusX = listX2 - 12 - statusW;
            if (spec != null) {
                UiTheme.roundRect(g, statusX - 7, y + 4, statusW + 14, 14, 7, UiTheme.ACCENT_SOFT);
                g.drawString(this.font, trim(status, 150), statusX, y + 7, UiTheme.ACCENT);
            } else {
                g.drawString(this.font, trim(status, 150), statusX, y + 7, UiTheme.TEXT_MUTED);
            }
        }
        if (visible.isEmpty()) {
            drawEmpty(g, tr("empty"), listX1, listTop, listX2, listBot);
        }
    }

    private String tr(String key, Object... args) {
        return Component.translatable("config.eternal_heart.attribute_list." + key, args).getString();
    }

    // ===================== 输入处理 =====================

    @Override
    public boolean mouseClicked(double mxd, double myd, int button) {
        if (button == 0 && mxd >= listX1 && mxd < listX2 && myd >= listTop && myd < listBot) {
            int idx = (int) ((myd - listTop + scroll) / ROW_STEP);
            if (idx >= 0 && idx < visible.size()) {
                Minecraft.getInstance().setScreen(new AttributeValueScreen(this, visible.get(idx)));
                return true;
            }
        }
        return super.mouseClicked(mxd, myd, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = (int) Math.max(0, Math.min(scrollMax, scroll - delta * ROW_STEP));
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
