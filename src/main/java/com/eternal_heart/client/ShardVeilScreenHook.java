package com.eternal_heart.client;

import com.eternal_heart.EternalHeartMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 背包 / 容器界面里的「碎影」层。
 *
 * <p>为什么需要它：Forge 的 HUD 覆盖层只在<b>没有界面打开</b>时绘制，所以背包一开，
 * 碎片就全没了。这里挂 {@link ScreenEvent.Render.Post} 把同一套表现补到界面上。</p>
 *
 * <p>两种触发：</p>
 * <ul>
 *   <li><b>鼠标指针悬停</b>在永恒之心上 —— "在背包里用鼠标指针看也是同理"；</li>
 *   <li><b>手持</b>（主手/副手）。</li>
 * </ul>
 *
 * <p><b>佩戴在饰品栏位（Curios）不触发</b> —— 用户 2026-09-23 明确这是 BUG：饰品栏位属于被动佩戴，
 * 碎片与面板都只在"手里拿着"或"界面里指着"时出现。</p>
 *
 * <p>按住 Shift 依旧是碎片聚合成数据面板，实现完全复用 {@link ShardVeilOverlay}。</p>
 */
@Mod.EventBusSubscriber(modid = EternalHeartMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class ShardVeilScreenHook {

    /** 与 HUD 层各持一份实例（同一时刻只会画其中一个，互不干扰）。 */
    private static final ShardVeilOverlay OVERLAY = new ShardVeilOverlay();

    private ShardVeilScreenHook() {
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!ShardVeilOverlay.shardVeilEnabled()) {
            return;        // 关闭碎片渲染 → 界面层整层退出，退回原版显示
        }
        Minecraft mc = Minecraft.getInstance();
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        if (mc.player == null || mc.options == null || mc.options.hideGui) {
            return;
        }
        if (event.getGuiGraphics() == null) {
            return;
        }

        Slot hovered = screen.getSlotUnderMouse();
        if (hovered != null && ShardVeilOverlay.isHeart(hovered.getItem())) {
            // 指针悬停在心上 → 环形模式，环心 = 这个物品格的中心（换算成屏幕 GUI 坐标）
            float[] c = ringCenter(screen, hovered);
            OVERLAY.renderVeil(event.getGuiGraphics(), screen.width, screen.height,
                    true, hovered.getItem(), event.getPartialTick(), c[0], c[1], true);
            return;
        }
        // 指针没指着心：手持 → 外围碎片 + Shift 面板；饰品栏位佩戴 → 只出碎片
        ItemStack hand = ShardVeilOverlay.handHeart(mc.player);
        if (!hand.isEmpty()) {
            OVERLAY.renderVeil(event.getGuiGraphics(), screen.width, screen.height,
                    true, hand, event.getPartialTick(), Float.NaN, Float.NaN, true);
            return;
        }
        ItemStack worn = ShardVeilOverlay.wornHeart(mc.player);
        if (!worn.isEmpty()) {
            OVERLAY.renderVeil(event.getGuiGraphics(), screen.width, screen.height,
                    true, worn, event.getPartialTick(), Float.NaN, Float.NaN, false);
        }
    }

    /**
     * 悬停物品格的<b>屏幕 GUI 坐标</b>中心。
     *
     * <p>坑：1.20.1 的 {@code Slot#x/#y} 是<b>相对 GUI 原点</b>的（vanilla 是
     * {@code pose.translate(leftPos, topPos)} 之后才拿它画格子），不是屏幕绝对坐标。
     * 直接拿来当屏幕坐标用，碎片圈会整体偏掉一个 (leftPos, topPos)，看着就是
     * "圈不是以永恒之心为中心"。</p>
     *
     * <p>这里两个候选都算 —— 绝对语义与相对语义各一个 —— 取离鼠标更近的那个，
     * 于是不管哪套语义都不会错；万一鼠标根本不在格子上（拿不到 hovered 的极端情形），
     * 就直接用鼠标位置当环心（物品就在指针底下）。</p>
     */
    private static float[] ringCenter(AbstractContainerScreen<?> screen, Slot slot) {
        Minecraft mc = Minecraft.getInstance();
        double mx = mc.mouseHandler.xpos() * (double) screen.width
                / Math.max(1.0D, mc.getWindow().getScreenWidth());
        double my = mc.mouseHandler.ypos() * (double) screen.height
                / Math.max(1.0D, mc.getWindow().getScreenHeight());
        float absX = slot.x + 8.0F;
        float absY = slot.y + 8.0F;
        float relX = screen.getGuiLeft() + slot.x + 8.0F;
        float relY = screen.getGuiTop() + slot.y + 8.0F;
        double dAbs = Math.hypot(absX - mx, absY - my);
        double dRel = Math.hypot(relX - mx, relY - my);
        if (Math.min(dAbs, dRel) < 14.0D) {
            return dAbs <= dRel ? new float[]{absX, absY} : new float[]{relX, relY};
        }
        return new float[]{(float) mx, (float) my};
    }

    /**
     * 指针悬停在心上的时候，<b>原版那个提示框不再画</b> —— 屏幕交给碎影层。
     * 用户原话："鼠标移动到心之后只显示碎片"，"如果要看面板就按住 shift"。
     */
    @SubscribeEvent
    public static void onTooltipPre(net.minecraftforge.client.event.RenderTooltipEvent.Pre event) {
        if (!ShardVeilOverlay.shardVeilEnabled()) {
            return;        // 关闭碎片渲染 → 不再接管提示框，原版提示框照常显示
        }
        if (ShardVeilOverlay.isHeart(event.getItemStack())) {
            event.setCanceled(true);
        }
    }
}
