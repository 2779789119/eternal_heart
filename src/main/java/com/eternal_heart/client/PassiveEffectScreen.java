package com.eternal_heart.client;

import com.eternal_heart.client.ui.UiButton;
import com.eternal_heart.client.ui.UiEditBox;
import com.eternal_heart.client.ui.UiScreen;
import com.eternal_heart.client.ui.UiTheme;
import com.eternal_heart.core.Debuffs;
import com.eternal_heart.core.PassiveEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import org.lwjgl.glfw.GLFW;

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
 * <p>与黑 / 白名单选择器（{@link EffectListScreen}）的区别：那里只有「在名单 / 不在名单」，
 * 这里每条效果还带<b>等级</b>与<b>时长</b>：</p>
 * <ul>
 *   <li>列表列出全部已注册效果（含其它模组），点击一行即加入 / 选中，左侧勾选框可移除；</li>
 *   <li>底部编辑区调整等级（1 = I 级）与时长（秒；勾「永久」写入 -1 无限时长）；</li>
 *   <li>改动即时写回父面板草稿，返回后由「保存并关闭」统一提交给服务器。</li>
 * </ul>
 *
 * <p>被「负面效果拦截」挡住的效果（HARMFUL 分类 / 黑名单）在行末显示警告：
 * 它们配置了也不会生效，需要先在防御分类里加入白名单。</p>
 */
public class PassiveEffectScreen extends UiScreen {

    // ===================== 布局 =====================
    private static final int ROW_H = 22;
    private static final int ROW_STEP = 26;
    private static final int HEADER_H = 52;
    private static final int FOOTER_H = 82;

    /** 单条配置的编辑状态（等级从 1 开始，时长 tick / -1 永久）。 */
    private static final class Spec {
        int level;
        int duration;

        Spec(int level, int duration) {
            this.level = level;
            this.duration = duration;
        }
    }

    // ===================== 状态 =====================
    private final Screen parent;
    private final Consumer<List<String>> onChange;
    private final Map<String, Spec> entries = new LinkedHashMap<>();
    private final List<ResourceLocation> all = new ArrayList<>();
    private final List<ResourceLocation> visible = new ArrayList<>();

    private String selectedId;
    private UiEditBox searchBox;
    private UiEditBox levelBox;
    private UiEditBox durationBox;
    private UiButton levelDown;
    private UiButton levelUp;
    private UiButton permanentBtn;
    private UiButton removeBtn;
    private UiButton clearBtn;
    private UiButton doneBtn;

    private String lastSearch = "";
    private boolean syncing;
    private EditBox focusedBox;
    private int scroll, scrollMax;
    private int listX1, listX2, listTop, listBot;
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
            case HARMFUL -> 0xFFF87171;
            case BENEFICIAL -> 0xFF4ADE80;
            default -> 0xFF8A94A2;
        };
    }

    @Override
    protected void init() {
        layoutPanel(660, 520, 400, 320);
        listX1 = panelX + 16;
        listX2 = panelX + panelW - 16;
        listTop = panelY + HEADER_H + 36;
        listBot = panelY + panelH - FOOTER_H - 8;

        int footerTop = panelY + panelH - FOOTER_H;
        row1Y = footerTop + 12;

        searchBox = new UiEditBox(this.font, listX1, panelY + HEADER_H + 8, listX2 - listX1, 20,
                Component.translatable("config.eternal_heart.passive_list.search_hint"));
        searchBox.setMaxLength(48);
        searchBox.setValue(lastSearch);
        searchBox.setResponder(text -> {
            lastSearch = text;
            rebuild();
        });
        this.addRenderableWidget(searchBox);

        // 编辑区第一行：等级 [-] [输入] [+]   时长 [输入] 秒   [永久]
        int x = listX1;
        labelLevelX = x;
        x += this.font.width(tr("level")) + 8;
        levelDown = this.addRenderableWidget(UiButton.secondary(x, row1Y, 18, 18,
                Component.literal("-"), () -> adjustLevel(-1)));
        x += 20;
        levelBox = this.addRenderableWidget(new UiEditBox(this.font, x, row1Y, 36, 18, null)
                .selectAllOnFocus());
        levelBox.setMaxLength(3);
        levelBox.setFilter(text -> text.isEmpty() || text.matches("\\d{1,3}"));
        levelBox.setResponder(this::onLevelTyped);
        x += 38;
        levelUp = this.addRenderableWidget(UiButton.secondary(x, row1Y, 18, 18,
                Component.literal("+"), () -> adjustLevel(1)));

        x = listX1 + 150;
        labelDurationX = x;
        x += this.font.width(tr("duration")) + 8;
        durationBox = this.addRenderableWidget(new UiEditBox(this.font, x, row1Y, 64, 18, null)
                .selectAllOnFocus());
        durationBox.setMaxLength(8);
        durationBox.setFilter(text -> text.isEmpty() || text.matches("\\d{0,5}(\\.\\d?)?"));
        durationBox.setResponder(this::onDurationTyped);
        x += 66;
        labelSecondsX = x;
        x += this.font.width(tr("seconds")) + 10;
        permanentBtn = this.addRenderableWidget(UiButton.segment(x, row1Y, 58, 18,
                Component.translatable("config.eternal_heart.passive_list.permanent"),
                this::togglePermanent));

        // 编辑区第二行：左侧状态文字 + 右侧操作
        int row2Y = footerTop + 44;
        doneBtn = this.addRenderableWidget(UiButton.primary(
                panelX + panelW - 16 - 76, row2Y, 76, 22,
                Component.translatable("config.eternal_heart.passive_list.done"), this::onClose));
        clearBtn = this.addRenderableWidget(UiButton.danger(
                panelX + panelW - 16 - 76 - 8 - 62, row2Y, 62, 22,
                Component.translatable("config.eternal_heart.passive_list.clear"), this::clearAll));
        removeBtn = this.addRenderableWidget(UiButton.secondary(
                panelX + panelW - 16 - 76 - 8 - 62 - 8 - 62, row2Y, 62, 22,
                Component.translatable("config.eternal_heart.passive_list.remove"), this::removeSelected));

        rebuild();
        syncEditors();
    }

    /** 按搜索词重建可见列表并钳制滚动。 */
    private void rebuild() {
        visible.clear();
        String query = lastSearch == null ? "" : lastSearch.trim().toLowerCase(Locale.ROOT);
        for (ResourceLocation id : all) {
            if (query.isEmpty() || matches(id, query)) visible.add(id);
        }
        scroll = 0;
        scrollMax = Math.max(0, visible.size() * ROW_STEP - 6 - (listBot - listTop));
    }

    private boolean matches(ResourceLocation id, String query) {
        if (id.toString().toLowerCase(Locale.ROOT).contains(query)) return true;
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);
        return effect != null && Component.translatable(effect.getDescriptionId()).getString()
                .toLowerCase(Locale.ROOT).contains(query);
    }

    // ===================== 渲染 =====================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        drawPanel(g);
        drawHeader(g, Component.translatable("config.eternal_heart.passive_list.title"),
                Component.translatable("config.eternal_heart.passive_list.subtitle"), HEADER_H);

        g.enableScissor(listX1, listTop, listX2, listBot);
        renderRows(g, mx, my);
        g.disableScissor();
        drawScrollbar(g, listTop, listBot, scroll, scrollMax, Math.max(1, visible.size() * ROW_STEP));

        drawFooterDivider(g, FOOTER_H);
        int labelY = row1Y + 5;
        g.drawString(this.font, tr("level"), labelLevelX, labelY, UiTheme.TEXT_MUTED);
        g.drawString(this.font, tr("duration"), labelDurationX, labelY, UiTheme.TEXT_MUTED);
        g.drawString(this.font, tr("seconds"), labelSecondsX, labelY, UiTheme.TEXT_MUTED);
        g.drawString(this.font, footerStatus(), listX1, row1Y + 46,
                selectedId == null ? UiTheme.TEXT_MUTED : UiTheme.ACCENT_DIM);

        super.render(g, mx, my, pt);
    }

    private void renderRows(GuiGraphics g, int mx, int my) {
        for (int i = 0; i < visible.size(); i++) {
            int y = listTop - scroll + i * ROW_STEP;
            if (y + ROW_H <= listTop || y >= listBot) continue;

            ResourceLocation id = visible.get(i);
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);
            Spec spec = entries.get(id.toString());
            boolean added = spec != null;
            boolean editing = id.toString().equals(selectedId);
            boolean hover = mx >= listX1 && mx < listX2 && my >= y && my < y + ROW_H;

            UiTheme.card(g, listX1, y, listX2 - listX1, ROW_H, 5, hover, editing);
            UiTheme.roundRect(g, listX1 + 6, y + 6, 3, ROW_H - 12, 1, categoryColor(effect));

            // 勾选框
            int boxX = listX1 + 14, boxY = y + 5;
            UiTheme.roundRect(g, boxX, boxY, 12, 12, 3, added ? UiTheme.ACCENT : UiTheme.BORDER_STRONG);
            if (added) {
                UiTheme.roundRect(g, boxX + 1, boxY + 1, 10, 10, 2, 0xFF10231F);
                g.drawCenteredString(this.font, "✓", boxX + 6, boxY + 2, UiTheme.ACCENT);
            }

            String name = effect == null ? id.toString()
                    : Component.translatable(effect.getDescriptionId()).getString();
            boolean blocked = effect != null && Debuffs.blocked(effect);
            g.drawString(this.font, trim(name, 176), listX1 + 32, y + 7,
                    blocked ? UiTheme.WARN : (added ? UiTheme.ACCENT : UiTheme.TEXT));

            String state = spec == null ? "" : tr("level_short", spec.level) + " · " + durationText(spec.duration);
            int stateW = this.font.width(state);
            int stateX = listX2 - 12 - stateW;
            if (blocked) {
                g.drawString(this.font, "!", stateX - 8, y + 7, UiTheme.DANGER);
            }
            String idText = trim(id.toString(), Math.max(24, stateX - 14 - (listX1 + 216)));
            g.drawString(this.font, idText, listX1 + 216, y + 7, UiTheme.TEXT_MUTED);
            if (!state.isEmpty()) {
                g.drawString(this.font, state, stateX, y + 7, blocked ? UiTheme.WARN : UiTheme.TEXT_DIM);
            }
        }
        if (visible.isEmpty()) {
            drawEmpty(g, tr("empty"), listX1, listTop, listX2, listBot);
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

    // ===================== 输入处理 =====================

    @Override
    public boolean mouseClicked(double mxd, double myd, int button) {
        int mx = (int) mxd, my = (int) myd;
        if (button == 0 && mx >= listX1 && mx < listX2 && my >= listTop && my < listBot) {
            int index = (int) ((my - listTop + scroll) / ROW_STEP);
            if (index >= 0 && index < visible.size()) {
                int rowY = listTop - scroll + index * ROW_STEP;
                if (my >= rowY && my < rowY + ROW_H) {
                    clickRow(visible.get(index), mx < listX1 + 32);
                    return true;
                }
            }
        }
        return super.mouseClicked(mxd, myd, button);
    }

    /** 点击行：未添加则加入并选中；已添加时点勾选框移除，点其他区域仅切换编辑目标。 */
    private void clickRow(ResourceLocation id, boolean onCheckbox) {
        String key = id.toString();
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
        syncEditors();
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
        syncEditors();
    }

    private void clearAll() {
        entries.clear();
        selectedId = null;
        push();
        syncEditors();
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
                levelBox.setValue("");
                durationBox.setValue("");
            } else {
                levelBox.setValue(String.valueOf(spec.level));
                durationBox.setValue(spec.duration < 0 ? "" : secondsText(spec.duration));
            }
            boolean permanent = spec != null && spec.duration < 0;
            permanentBtn.setSelected(permanent);
            permanentBtn.setMessage(Component.translatable("config.eternal_heart.passive_list.permanent")
                    .append(permanent ? Component.literal(" ✓") : Component.empty()));
        } finally {
            syncing = false;
        }
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

    @Override
    public void tick() {
        super.tick();
        boolean hasSelection = currentSpec() != null;
        boolean permanent = hasSelection && currentSpec().duration < 0;
        levelBox.active = hasSelection;
        levelDown.active = hasSelection;
        levelUp.active = hasSelection;
        durationBox.active = hasSelection && !permanent;
        permanentBtn.active = hasSelection;
        removeBtn.active = hasSelection;
        clearBtn.active = !entries.isEmpty();

        // 输入框失焦：把中间态（空 / 半截数字）规整回缓存值，光标才好操作
        EditBox now = this.getFocused() instanceof EditBox box ? box : null;
        if (focusedBox != null && focusedBox != now) syncEditors();
        focusedBox = now;
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
