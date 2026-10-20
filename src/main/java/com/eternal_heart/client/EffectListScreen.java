package com.eternal_heart.client;

import com.eternal_heart.client.ui.UiButton;
import com.eternal_heart.client.ui.UiEditBox;
import com.eternal_heart.client.ui.UiScreen;
import com.eternal_heart.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 效果名单选择器 —— 负面效果清除的黑名单 / 白名单可视化编辑界面。
 *
 * <p>把「手写 modid:effect_id 字符串」换成「从全部已注册效果里点选」：
 * <ul>
 *   <li>列出所有已注册效果（含其它模组的效果），按 ID 排序，左侧色条区分 有益 / 负面 / 中性；</li>
 *   <li>搜索框按本地化名称或 ID 过滤；</li>
 *   <li>点击任意行切换「在名单中 / 不在名单中」，改动即时写回父界面草稿；</li>
 *   <li>白名单优先于黑名单（与 {@code Debuffs} 判定一致），界面内提示。</li>
 * </ul>
 */
public class EffectListScreen extends UiScreen {

    // ===================== 布局 =====================
    private static final int ROW_H = 22;
    private static final int ROW_STEP = 26;
    private static final int HEADER_H = 52;
    private static final int FOOTER_H = 38;

    // ===================== 状态 =====================
    private final Screen parent;
    private final String fieldName;
    private final List<String> selected;
    private final Consumer<List<String>> onChange;
    private final boolean blacklist;
    private final List<ResourceLocation> all;
    private final List<ResourceLocation> visible = new ArrayList<>();
    private UiEditBox searchBox;
    private UiButton doneBtn;
    private UiButton clearBtn;
    private String lastSearch = "";
    private int scroll, scrollMax;
    private int listX1, listX2, listTop, listBot;

    public EffectListScreen(Screen parent, String fieldName, List<String> initial,
                            Consumer<List<String>> onChange) {
        super(Component.translatable("config.eternal_heart.effect_list.title"));
        this.parent = parent;
        this.fieldName = fieldName;
        this.selected = new ArrayList<>(initial);
        this.onChange = onChange;
        this.blacklist = fieldName.equals("DEBUFF_BLACKLIST");
        this.all = new ArrayList<>(BuiltInRegistries.MOB_EFFECT.keySet());
        this.all.sort(Comparator.comparing(ResourceLocation::toString));
    }

    private static int categoryColor(MobEffect effect) {
        if (effect == null) return 0xFF8A94A2;
        return switch (effect.getCategory()) {
            case HARMFUL -> 0xFFF87171;
            case BENEFICIAL -> 0xFF4ADE80;
            default -> 0xFF8A94A2;
        };
    }

    @Override
    protected void init() {
        layoutPanel(620, 500, 320, 260);
        listX1 = panelX + 16;
        listX2 = panelX + panelW - 16;
        listTop = panelY + HEADER_H + 38;
        listBot = panelY + panelH - FOOTER_H - 8;

        searchBox = new UiEditBox(this.font, listX1, panelY + HEADER_H + 10, listX2 - listX1, 20,
                Component.translatable("config.eternal_heart.effect_list.search_hint"));
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
                Component.translatable("config.eternal_heart.effect_list.done"), this::onClose));

        clearBtn = this.addRenderableWidget(UiButton.danger(
                panelX + panelW - 16 - 76 - 8 - 64, footerTop + 8, 64, 22,
                Component.translatable("config.eternal_heart.effect_list.clear"), this::clearAll));

        rebuild();
    }

    /** 按搜索词重建可见列表并钳制滚动。 */
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
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);
        return effect != null && Component.translatable(effect.getDescriptionId()).getString()
                .toLowerCase(Locale.ROOT).contains(q);
    }

    // ===================== 渲染 =====================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        drawPanel(g);
        drawHeader(g, Component.translatable(blacklist
                        ? "config.eternal_heart.effect_list.title_blacklist"
                        : "config.eternal_heart.effect_list.title_whitelist"),
                Component.translatable(blacklist
                        ? "config.eternal_heart.effect_list.hint_blacklist"
                        : "config.eternal_heart.effect_list.hint_whitelist"), HEADER_H);

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
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);
            boolean inList = selected.contains(id.toString());
            boolean hover = mx >= listX1 && mx < listX2 && my >= y && my < y + ROW_H;

            UiTheme.card(g, listX1, y, listX2 - listX1, ROW_H, 5, hover, inList);
            // 分类色条：有益 / 负面 / 中性
            UiTheme.roundRect(g, listX1 + 6, y + 6, 3, ROW_H - 12, 1, categoryColor(effect));

            String name = effect == null ? id.toString()
                    : Component.translatable(effect.getDescriptionId()).getString();
            g.drawString(this.font, trim(name, 190), listX1 + 16, y + 7,
                    inList ? UiTheme.ACCENT : UiTheme.TEXT);
            g.drawString(this.font, trim(id.toString(), listX2 - listX1 - 310), listX1 + 216, y + 7,
                    UiTheme.TEXT_MUTED);

            String mark = inList ? tr("added") : "+";
            int markW = this.font.width(mark) + 14;
            int markX = listX2 - 12 - markW;
            if (inList) {
                UiTheme.roundRect(g, markX, y + 4, markW, 14, 7, UiTheme.ACCENT_SOFT);
                g.drawString(this.font, mark, markX + 7, y + 7, UiTheme.ACCENT);
            } else {
                g.drawString(this.font, mark, markX + 7, y + 7, UiTheme.TEXT_MUTED);
            }
        }
        if (visible.isEmpty()) {
            drawEmpty(g, tr("empty"), listX1, listTop, listX2, listBot);
        }
    }

    private String tr(String key, Object... args) {
        return Component.translatable("config.eternal_heart.effect_list." + key, args).getString();
    }

    // ===================== 输入处理 =====================

    @Override
    public boolean mouseClicked(double mxd, double myd, int button) {
        if (button == 0 && mxd >= listX1 && mxd < listX2 && myd >= listTop && myd < listBot) {
            int idx = (int) ((myd - listTop + scroll) / ROW_STEP);
            if (idx >= 0 && idx < visible.size()) {
                toggle(visible.get(idx));
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
