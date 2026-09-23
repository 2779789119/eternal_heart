package com.eternal_heart.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
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
public class EffectListScreen extends Screen {

    // ===================== 布局 =====================
    private static final int ROW_H = 20;

    // ===================== 配色（与配置面板一致：深蓝夜空 + 暗金）=====================
    private static final int COL_OVERLAY = 0xB0040609;
    private static final int COL_PANEL = 0xF60E1521;
    private static final int COL_BORDER = 0xFF9A7B3F;
    private static final int COL_GOLD = 0xFFE8C860;
    private static final int COL_TEXT = 0xFFE6EAF0;
    private static final int COL_TEXT_DIM = 0xFF8A93A0;
    private static final int COL_ROW_ALT = 0x08FFFFFF;
    private static final int COL_ROW_HOVER = 0x16FFFFFF;
    private static final int COL_HARMFUL = 0xFFE06A6A;
    private static final int COL_BENEFICIAL = 0xFF6AD4A0;
    private static final int COL_NEUTRAL = 0xFF9AA4B2;

    // ===================== 状态 =====================
    private final Screen parent;
    private final String fieldName;
    private final List<String> selected;
    private final Consumer<List<String>> onChange;
    private final boolean blacklist;
    private final List<ResourceLocation> all;
    private final List<ResourceLocation> visible = new ArrayList<>();
    private EditBox searchBox;
    private Button doneBtn;
    private Button clearBtn;
    private String lastSearch = "";
    private int scroll, scrollMax;
    private int panelX, panelY, panelW, panelH;
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
        if (effect == null) return COL_NEUTRAL;
        return switch (effect.getCategory()) {
            case HARMFUL -> COL_HARMFUL;
            case BENEFICIAL -> COL_BENEFICIAL;
            case NEUTRAL -> COL_NEUTRAL;
        };
    }

    @Override
    protected void init() {
        // 最小尺寸保护：避免极小窗口下面板高度倒挂（列表裁剪区反向）
        panelW = Math.max(240, Math.min(this.width - 12, 480));
        panelH = Math.max(140, Math.min(this.height - 12, 420));
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        listX1 = panelX + 8;
        listX2 = panelX + panelW - 8;
        listTop = panelY + 52;
        listBot = panelY + panelH - 28;

        searchBox = new EditBox(this.font, listX1, panelY + 30, panelW - 16, 16,
                Component.translatable("config.eternal_heart.effect_list.search_hint"));
        searchBox.setHint(Component.translatable("config.eternal_heart.effect_list.search_hint"));
        searchBox.setMaxLength(48);
        searchBox.setValue(lastSearch);
        searchBox.setResponder(s -> {
            lastSearch = s;
            rebuild();
        });
        this.addRenderableWidget(searchBox);

        doneBtn = this.addRenderableWidget(Button.builder(
                        Component.translatable("config.eternal_heart.effect_list.done"), b -> onClose())
                .bounds(panelX + panelW - 92, panelY + panelH - 24, 84, 18).build());

        clearBtn = this.addRenderableWidget(Button.builder(
                        Component.translatable("config.eternal_heart.effect_list.clear"), b -> clearAll())
                .bounds(panelX + panelW - 182, panelY + panelH - 24, 84, 18).build());

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
        scrollMax = Math.max(0, visible.size() * ROW_H - (listBot - listTop));
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
        g.fill(0, 0, this.width, this.height, COL_OVERLAY);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, COL_PANEL);
        g.fill(panelX, panelY, panelX + panelW, panelY + 1, COL_BORDER);
        g.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, COL_BORDER);
        g.fill(panelX, panelY, panelX + 1, panelY + panelH, COL_BORDER);
        g.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, COL_BORDER);

        g.drawString(this.font, tr(blacklist ? "title_blacklist" : "title_whitelist"),
                panelX + 8, panelY + 7, COL_GOLD);
        g.drawString(this.font, trim(tr(blacklist ? "hint_blacklist" : "hint_whitelist"), panelW - 16),
                panelX + 8, panelY + 18, COL_TEXT_DIM);

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
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);
            boolean inList = selected.contains(id.toString());
            boolean hover = mx >= listX1 && mx < listX2 && my >= y && my < y + ROW_H;

            if ((i & 1) == 1) g.fill(listX1, y, listX2, y + ROW_H, COL_ROW_ALT);
            if (hover) g.fill(listX1, y, listX2, y + ROW_H, COL_ROW_HOVER);

            // 分类色条：有益 / 负面 / 中性
            g.fill(listX1 + 2, y + 3, listX1 + 5, y + ROW_H - 3, categoryColor(effect));

            String name = effect == null ? id.toString()
                    : Component.translatable(effect.getDescriptionId()).getString();
            g.drawString(this.font, trim(name, 170), listX1 + 10, y + 6,
                    inList ? COL_GOLD : COL_TEXT);
            g.drawString(this.font, trim(id.toString(), listX2 - listX1 - 224), listX1 + 188, y + 6,
                    COL_TEXT_DIM);

            String mark = inList ? tr("added") : "+";
            g.drawString(this.font, mark, listX2 - 8 - this.font.width(mark), y + 6,
                    inList ? COL_GOLD : COL_TEXT_DIM);
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
        return Component.translatable("config.eternal_heart.effect_list." + key, args).getString();
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
                toggle(visible.get(idx));
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
