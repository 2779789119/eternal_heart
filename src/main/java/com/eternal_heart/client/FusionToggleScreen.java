package com.eternal_heart.client;

import com.eternal_heart.EternalHeartMod;
import com.eternal_heart.client.ui.UiButton;
import com.eternal_heart.client.ui.UiList;
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
 * 关掉后该饰品不再提供任何能力，但融合记录仍然保留，随时可以再打开。</p>
 *
 * <p>开关状态存在物品 NBT 上（{@code eternal_heart_fused[i].enabled}），因此是
 * <b>这一颗心</b>的状态。客户端点击后先本地翻转（提示框立即更新）并请求服务端；
 * 服务端拒绝时用回执把本地翻回去。</p>
 */
public class FusionToggleScreen extends UiScreen {

    private static final int HEADER = 52;
    private static final int FOOTER = 38;
    private static final int ROW_H = 30;
    private static final int SWITCH_W = 40;
    private static final int SWITCH_H = 20;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private ItemStack heart = ItemStack.EMPTY;
    private UiList<Row> list;

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
        clearWidgets();
        headerH = HEADER;
        footerH = FOOTER;
        layoutPanel(560, 460, 360, 280);

        reload();

        int x1 = contentLeft();
        int x2 = contentRight();
        int top = panelY + HEADER + 8;
        int bottom = footerTop() - 8;

        list = add(new UiList<Row>(x1, top, x2 - x1, bottom - top))
                .setRowMetrics(ROW_H, 4)
                .setEmptyText(Component.translatable(heart.isEmpty()
                        ? "config.eternal_heart.fusion.no_heart"
                        : "config.eternal_heart.fusion.empty").getString())
                .setPainter(this::paintRow)
                // 点整行即切换（开关区域之外也生效，省得要求精准点到小圆钮上）
                .setOnClick((index, mx, my, button) -> {
                    Row row = list.get(index);
                    if (row != null) toggle(row);
                });
        list.setItems(rows);

        add(UiButton.primary(panelX + panelW - 16 - 72, footerTop() + 8, 72, 22,
                Component.translatable("config.eternal_heart.fusion.done"), this::onClose));
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

    private void paintRow(GuiGraphics g, Row row, int index, int rowX, int rowY, int rowW, int rowH,
                          float hover, boolean selected) {
        // 关闭的行用暗色卡片，开着的用青绿描边，一眼能看出哪些能力还在
        UiTheme.cardFade(g, rowX, rowY, rowW, rowH, UiTheme.R_SM, hover, row.enabled);
        if (row.enabled) {
            UiTheme.roundRect(g, rowX + 3, rowY + 7, 3, rowH - 14, 1, UiTheme.ACCENT);
        }

        if (!row.stack.isEmpty()) {
            g.renderItem(row.stack, rowX + 14, rowY + (rowH - 16) / 2);
        }
        String name = row.stack.isEmpty()
                ? Component.translatable("config.eternal_heart.fusion.unknown").getString()
                : row.stack.getHoverName().getString();
        int nameW = rowW - 32 - SWITCH_W - 30;
        g.drawString(this.font, trim(name, nameW), rowX + 38, rowY + (rowH - 8) / 2,
                row.enabled ? UiTheme.TEXT : UiTheme.TEXT_MUTED, false);

        drawSwitch(g, rowX + rowW - 14 - SWITCH_W, rowY + (rowH - SWITCH_H) / 2, row.enabled, hover);
    }

    /** 与 {@link com.eternal_heart.client.ui.UiSwitch} 同款外观（单层药丸轨道 + 抗锯齿圆钮）。 */
    private static void drawSwitch(GuiGraphics g, int x, int y, boolean on, float hover) {
        int track = on ? UiTheme.mix(UiTheme.ACCENT_DEEP, UiTheme.ACCENT, 0.25f + hover * 0.45f)
                : UiTheme.mix(0xFF2B3542, 0xFF3B4A5D, hover);
        if (on) {
            UiTheme.roundRect(g, x - 1, y - 1, SWITCH_W + 2, SWITCH_H + 2, (SWITCH_H + 2) / 2, 0x2E5EEAD4);
        }
        UiTheme.roundRect(g, x, y, SWITCH_W, SWITCH_H, SWITCH_H / 2, track);
        UiTheme.hLine(g, x + SWITCH_H / 2, x + SWITCH_W - SWITCH_H / 2, y + 1,
                on ? 0x3DFFFFFF : 0x1AFFFFFF);

        double radius = (SWITCH_H - 4) / 2.0;
        double half = SWITCH_H / 2.0;
        double cx = x + half + (SWITCH_W - SWITCH_H) * (on ? 1 : 0);
        UiTheme.disc(g, cx, y + half + 1.2, radius, 0x33000000);
        UiTheme.disc(g, cx, y + half, radius, on ? 0xFFF4FFFC : 0xFFC9D4E0);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partial) {
        drawHeader(g, Component.translatable("config.eternal_heart.fusion.title"),
                Component.translatable(heart.isEmpty()
                        ? "config.eternal_heart.fusion.hint_no_heart"
                        : "config.eternal_heart.fusion.hint"), HEADER);
        renderWidgets(g, mouseX, mouseY, partial);
        drawFooterDivider(g, FOOTER);

        int disabled = 0;
        for (Row row : rows) if (!row.enabled) disabled++;
        drawStatus(g, tr("count", rows.size(), disabled), UiTheme.TEXT_MUTED, panelW - 200);

        int hovered = list == null ? -1 : list.indexAt(mouseX, mouseY);
        if (hovered >= 0) {
            Row row = list.get(hovered);
            if (row != null && !row.stack.isEmpty()) tooltip(row.stack.getHoverName());
        }
    }

    // ============================================================
    //  交互
    // ============================================================

    private void toggle(Row row) {
        boolean next = !row.enabled;
        row.enabled = next;
        // 本地先写 NBT：提示框 / 客户端查询立即反映，不用等服务端往返
        HeartFusion.setEnabled(heart, row.index, next);
        FusionNetwork.toggle(row.index, next);
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
     * <p>成功时不用做任何事：客户端点击时已经把状态写进本地 NBT 了。</p>
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
}
