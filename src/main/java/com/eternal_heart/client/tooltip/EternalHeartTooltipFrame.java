package com.eternal_heart.client.tooltip;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.EternalHeartMod;
import com.eternal_heart.client.RainbowPalette;
import com.eternal_heart.config.ConfigValues;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 「永恒之心」提示框的自绘边框：深色底板 + 沿周长流动的炫彩渐变环 + 内侧柔光。
 *
 * 为什么画在这里：原版 GuiGraphics 在刷提示框底色前会发一个 {@code RenderTooltipEvent.Color}，
 * 把"用哪些颜色"交给模组决定。这个事件里我们能同时拿到
 * 最终位置 (getX/getY)、组件列表 (getComponents) 和画布 (getGraphics)，
 * 所以框的几何和原版算出来的一模一样——文字位置不用动，边框自然严丝合缝。
 *
 * 拿法：把事件里的底色、边框色全部改成全透明，原版就什么都不刷了；
 * 我们再自己刷底板 + 彩色环。顺序是"我们先画、原版文字后画"，
 * 所以文字永远压在框上面，不会被遮。全程只用原版公开接口，不 mixin、不反射。
 *
 * 边框是一整圈干净的彩虹环，四角不加方块装饰；
 * 头部下面那条分隔线不属于边框，由 {@link ClientSeparatorTooltip} 作为内容的一行来画，
 * 所以它永远钉在固定头部下沿、不随正文滚动。
 * 模组的"炫彩文字"开关关掉时整个自绘边框退场，回到原版的朴素外观。
 */
@Mod.EventBusSubscriber(modid = "eternal_heart", bus = Mod.EventBusSubscriber.Bus.FORGE, value = {Dist.CLIENT})
public final class EternalHeartTooltipFrame {
    /** 和原版提示框背景同一个层级，保证文字（更高层）画在我们之上。 */
    private static final int BLIT_OFFSET = 400;
    /** 底板：上浅下深的暗紫，纵向渐变（原版的深色底偏黑，这里带一点紫，和炫彩更搭）。 */
    private static final int PANEL_TOP = 0xF2161030;
    private static final int PANEL_BOTTOM = 0xF2080512;
    /** 一整圈走完几个彩虹周期：1 圈 2 个周期，绕框两圈彩虹，既有层次又不碎成条纹。
     *  按"周长"归一而不是按像素，所以框大框小的浓淡观感一致，接缝也永远闭合。 */
    private static final float CYCLES_PER_LAP = 2.0f;
    /** 主环几像素换一次色（肉眼看不出台阶，又能少画几百个矩形）。 */
    private static final int RING_STEP = 4;
    /** 内侧柔光几像素换一次色。 */
    private static final int GLOW_STEP = 8;
    /** 内侧柔光的透明度。 */
    private static final int GLOW_ALPHA = 0x3C;

    private EternalHeartTooltipFrame() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void paint(RenderTooltipEvent.Color event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !stack.is(EternalHeartMod.ETERNAL_HEART.get())) {
            return;
        }
        if (!((Boolean)ConfigValues.get(EternalHeartConfig.RAINBOW_NAME)).booleanValue()) {
            return;
        }
        List<ClientTooltipComponent> components = event.getComponents();
        if (components.isEmpty()) {
            return;
        }
        // 框的尺寸自己按原版的算法算一遍（原版算完就直接用，不再发事件告诉我们）。
        Font font = event.getFont();
        int width = 0;
        int height = components.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent line : components) {
            width = Math.max(width, line.getWidth(font));
            height += line.getHeight();
        }
        if (width <= 0 || height <= 0) {
            return;
        }
        // 让原版别再刷自己的黑底和灰边框，整块画面交给我们。
        event.setBackground(0x00000000);
        event.setBorderStart(0x00000000);
        event.setBorderEnd(0x00000000);

        GuiGraphics graphics = event.getGraphics();
        int left = event.getX() - 3;
        int top = event.getY() - 3;
        int right = left + width + 6;
        int bottom = top + height + 6;
        // 边框和文字共用一个时间漂移：速度取模组的炫彩速度配置，
        // 这样整块提示框（字 + 框）像同一条彩虹在流。
        float speed = ((Double)ConfigValues.get(EternalHeartConfig.RAINBOW_NAME_SPEED)).floatValue();
        float base = (float)Util.getMillis() / 1000.0f * speed;

        graphics.fillGradient(left, top, right, bottom, BLIT_OFFSET, PANEL_TOP, PANEL_BOTTOM);
        // 走线一圈的真实像素数：上下各 (right-left) 个、左右各 (bottom-top-2) 个
        float lap = 2.0f * ((right - left) + (bottom - top) - 2);
        float perPixel = CYCLES_PER_LAP / lap;
        drawRing(graphics, left + 1, top + 1, right - 1, bottom - 1, base + 0.05f, GLOW_ALPHA, GLOW_STEP, perPixel);
        drawRing(graphics, left, top, right, bottom, base, 0xFF, RING_STEP, perPixel);
    }

    /**
     * 沿矩形周长画一圈渐变色：色相随"绕框走过的像素数"推进，所以四条边连成一条完整的彩虹。
     * 走线顺序固定为顺时针（上边左→右、右边上→下、下边右→左、左边下→上），
     * 柔光环和主环用同一套走线，两圈的颜色分布对得上。
     */
    private static void drawRing(GuiGraphics graphics, int left, int top, int right, int bottom, float base,
        int alpha, int step, float perPixel) {
        int walked = 0;
        for (int px = left; px < right; px += step) {
            int end = Math.min(right, px + step);
            graphics.fill(px, top, end, top + 1, BLIT_OFFSET, color(base, walked, alpha, perPixel));
            walked += end - px;
        }
        for (int py = top + 1; py < bottom - 1; py += step) {
            int end = Math.min(bottom - 1, py + step);
            graphics.fill(right - 1, py, right, end, BLIT_OFFSET, color(base, walked, alpha, perPixel));
            walked += end - py;
        }
        for (int px = right - 1; px >= left; px -= step) {
            int start = Math.max(left, px - step + 1);
            graphics.fill(start, bottom - 1, px + 1, bottom, BLIT_OFFSET, color(base, walked, alpha, perPixel));
            walked += px + 1 - start;
        }
        for (int py = bottom - 2; py > top; py -= step) {
            int start = Math.max(top + 1, py - step + 1);
            graphics.fill(left, start, left + 1, py + 1, BLIT_OFFSET, color(base, walked, alpha, perPixel));
            walked += py + 1 - start;
        }
    }

    /** 周长了 walked 个像素处的环色。 */
    private static int color(float base, int walked, int alpha, float perPixel) {
        return alpha << 24 | RainbowPalette.hueToRgb(base + (float)walked * perPixel);
    }
}
