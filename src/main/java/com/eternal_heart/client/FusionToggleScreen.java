package com.eternal_heart.client;

import com.eternal_heart.EternalHeartMod;
import com.eternal_heart.client.ui.UiButton;
import com.eternal_heart.client.ui.UiScreen;
import com.eternal_heart.client.ui.UiTheme;
import com.eternal_heart.features.HeartFusion;
import com.eternal_heart.network.FusionNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

/**
 * 「融合饰品开关」界面 —— 逐条开关永恒之心融合进来的能力。
 *
 * <p>列表来自玩家身上（饰品栏优先，其次主手）的永恒之心，每行一个开关：
 * 关掉后该饰品不再提供任何能力（属性 / 每 tick 效果 / 装备回调 / 时运等），
 * 但融合记录仍然保留，随时可以再打开。</p>
 *
 * <p>开关状态存在物品 NBT 上（{@code eternal_heart_fused[i].enabled}），因此是
 * <b>这一颗心</b>的状态：不同副本 / 不同玩家可以各不相同。客户端点击后先本地翻转
 * （提示框立即更新）并请求服务端；服务端拒绝时用回执把本地翻回去。</p>
 */
public class FusionToggleScreen extends UiScreen {

    // ===================== 布局 =====================
    private static final int HEADER_H = 52;
    private static final int FOOTER_H = 38;
    private static final int ROW_H = 26;
    private static final int ROW_STEP = 30;
    private static final int SWITCH_W = 38;
    private static final int SWITCH_H = 18;
    private static final int ICON = 16;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private ItemStack heart = ItemStack.EMPTY;
    private int scroll, scrollMax;
    private int listX1, listX2, listTop, listBot;

    /** 一行 = 一条融合记录（{@code index} 是 NBT 下标，服务端按它回写）。 */
    private static final class Row {
        final int index;
        final ItemStack stack;
        boolean enabled;

        Row(int index, ItemStack stack, boolean enabled) {
            this.index = index;
            this.stack = stack;
            this.enabled = enabled;
        }
    }

    public FusionToggleScreen(Screen parent) {
        super(Component.translatable("config.eternal_heart.fusion.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layoutPanel(560, 460, 340, 260);
        listX1 = panelX + 16;
        listX2 = panelX + panelW - 16;
        listTop = panelY + HEADER_H + 8;
        listBot = panelY + panelH - FOOTER_H - 8;

        reload();

        int footerTop = panelY + panelH - FOOTER_H;
        this.addRenderableWidget(UiButton.primary(
                panelX + panelW - 16 - 72, footerTop + 8, 72, 22,
                Component.translatable("config.eternal_heart.fusion.done"), this::onClose));
        clampScroll();
    }

    /** 重新读取玩家身上的永恒之心与融合列表（打开界面时 / 界面重建时调用）。 */
    private void reload() {
        rows.clear();
        heart = ItemStack.EMPTY;
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        heart = findHeart(player);
        if (heart.isEmpty()) return;
        for (HeartFusion.Fused fused : HeartFusion.fused(heart)) {
            rows.add(new Row(fused.index(), fused.stack(), fused.enabled()));
        }
    }

    /** 饰品栏优先（真正生效的位置），其次主手（刚合成完、还没戴上的情况）。 */
    private static ItemStack findHeart(Player player) {
        var handler = CuriosApi.getCuriosInventory(player).resolve();
        if (handler.isPresent()) {
            var found = handler.get().findFirstCurio(EternalHeartMod.ETERNAL_HEART.get());
            if (found.isPresent()) return found.get().stack();
        }
        ItemStack held = player.getMainHandItem();
        return held.is(EternalHeartMod.ETERNAL_HEART.get()) ? held : ItemStack.EMPTY;
    }

    // ============================================================
    //  渲染
    // ============================================================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        drawPanel(g);
        drawHeader(g, Component.translatable("config.eternal_heart.fusion.title"),
                Component.translatable(heart.isEmpty()
                        ? "config.eternal_heart.fusion.hint_no_heart"
                        : "config.eternal_heart.fusion.hint"), HEADER_H);

        g.enableScissor(listX1, listTop, listX2, listBot);
        renderRows(g, mx, my);
        g.disableScissor();
        drawScrollbar(g, listTop, listBot, scroll, scrollMax, Math.max(1, rows.size() * ROW_STEP));

        drawFooterDivider(g, FOOTER_H);
        int disabled = 0;
        for (Row row : rows) if (!row.enabled) disabled++;
        g.drawString(this.font, tr("count", rows.size(), disabled),
                panelX + 16, panelY + panelH - FOOTER_H + 15, UiTheme.TEXT_MUTED);

        super.render(g, mx, my, pt);
    }

    private void renderRows(GuiGraphics g, int mx, int my) {
        for (int i = 0; i < rows.size(); i++) {
            int y = listTop - scroll + i * ROW_STEP;
            if (y + ROW_H <= listTop || y >= listBot) continue;

            Row row = rows.get(i);
            boolean hover = mx >= listX1 && mx < listX2 && my >= y && my < y + ROW_H;

            // 关闭的行用暗色卡片，开着的用青绿描边，一眼能看出哪些能力还在
            UiTheme.card(g, listX1, y, listX2 - listX1, ROW_H, 5, hover, row.enabled);

            if (!row.stack.isEmpty()) {
                g.renderItem(row.stack, listX1 + 7, y + (ROW_H - ICON) / 2);
            }
            String name = row.stack.isEmpty()
                    ? Component.translatable("config.eternal_heart.fusion.unknown").getString()
                    : row.stack.getHoverName().getString();
            int nameW = listX2 - listX1 - 32 - SWITCH_W - 24;
            g.drawString(this.font, trim(name, nameW), listX1 + 30, y + 9,
                    row.enabled ? UiTheme.TEXT : UiTheme.TEXT_MUTED);

            drawSwitch(g, switchX(), y + (ROW_H - SWITCH_H) / 2, row.enabled, hover);
        }
        if (rows.isEmpty()) {
            drawEmpty(g, Component.translatable(heart.isEmpty()
                    ? "config.eternal_heart.fusion.no_heart"
                    : "config.eternal_heart.fusion.empty").getString(),
                    listX1, listTop, listX2, listBot);
        }
    }

    private int switchX() {
        return listX2 - 12 - SWITCH_W;
    }

    /** 与 {@link com.eternal_heart.client.ui.UiSwitch} 同款外观（药丸轨道 + 滑动圆钮）。 */
    private static void drawSwitch(GuiGraphics g, int x, int y, boolean on, boolean hovered) {
        int track = on
                ? (hovered ? UiTheme.ACCENT : UiTheme.ACCENT_DIM)
                : (hovered ? 0xFF39485A : 0xFF2B3542);
        UiTheme.roundRect(g, x, y, SWITCH_W, SWITCH_H, SWITCH_H / 2, track);

        int knob = SWITCH_H - 4;
        int travel = SWITCH_W - knob - 4;
        int knobX = x + 2 + (on ? travel : 0);
        UiTheme.roundRect(g, knobX, y + 2, knob, knob, knob / 2,
                on ? 0xFFF2FFFB : 0xFFB9C7D6);
    }

    // ============================================================
    //  交互
    // ============================================================

    @Override
    public boolean mouseClicked(double mxd, double myd, int button) {
        if (button == 0 && mxd >= listX1 && mxd < listX2 && myd >= listTop && myd < listBot) {
            int idx = (int) ((myd - listTop + scroll) / ROW_STEP);
            if (idx >= 0 && idx < rows.size()) {
                toggle(rows.get(idx));
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

    /** 点整行即切换（开关区域之外也生效，省得要求精准点到小圆钮上）。 */
    private void toggle(Row row) {
        boolean next = !row.enabled;
        row.enabled = next;
        // 本地先写 NBT：提示框 / 客户端查询立即反映，不用等服务端往返
        HeartFusion.setEnabled(heart, row.index, next);
        FusionNetwork.toggle(row.index, next);
    }

    private void clampScroll() {
        scrollMax = Math.max(0, rows.size() * ROW_STEP - 6 - (listBot - listTop));
        if (scroll > scrollMax) scroll = scrollMax;
        if (scroll < 0) scroll = 0;
    }

    // ============================================================
    //  服务端回执
    // ============================================================

    /** 由客户端入口注入的回调（{@code FusionNetwork.setClientReceiver}）。 */
    public static void receive(FusionNetwork.Ack ack) {
        if (Minecraft.getInstance().screen instanceof FusionToggleScreen screen) {
            screen.applyAck(ack);
        }
    }

    /**
     * 服务端拒绝时回滚本地状态。
     *
     * <p>成功时不用做任何事：客户端点击时已经把状态写进本地 NBT 了。
     * 失败（物品已不在手上 / 下标失效）时把开关翻回去，并同步回本地 NBT，
     * 避免界面显示与服务端真实状态不一致。</p>
     */
    private void applyAck(FusionNetwork.Ack ack) {
        for (Row row : rows) {
            if (row.index != ack.index()) continue;
            if (!ack.ok()) {
                row.enabled = !ack.enabled();
                HeartFusion.setEnabled(heart, row.index, row.enabled);
            }
            return;
        }
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("config.eternal_heart.fusion." + key, args).getString();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
