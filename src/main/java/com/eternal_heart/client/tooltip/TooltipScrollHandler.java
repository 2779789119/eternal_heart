package com.eternal_heart.client.tooltip;

import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 永恒之心提示框的"滚轮翻阅"：按行滚动正文。
 *
 * 提示框采用窗口切片渲染——固定头部（物品名/槽位/祷文）和分隔线永远钉在最上面，
 * 每帧只渲染放得进屏幕的那几行正文（见 EternalHeartTooltipHandler），
 * 本类记录正文窗口第一行的行号，并接管滚轮事件：
 * - 只要正文超过一屏，滚轮随时可以翻页，任何窗口尺寸、任何屏幕分辨率都一样；
 * - 滚到顶部/底部时不再拦截事件，滚轮还给原版（切换快捷栏等）；
 * - 收起（松开 Shift）、换别的物品、移开鼠标时，状态自动交还原版。
 */
@Mod.EventBusSubscriber(modid = "eternal_heart", bus = Mod.EventBusSubscriber.Bus.FORGE, value = {Dist.CLIENT})
public final class TooltipScrollHandler {
    /** 滚轮一格翻几行。 */
    private static final int LINES_PER_NOTCH = 2;
    /** 提示框超过这么久没再渲染过，就不再响应滚轮，避免"看不见却在截轮"。 */
    private static final long FRESH_MS = 500L;

    private static ItemStack tracked = ItemStack.EMPTY;
    private static boolean trackedExpanded = false;
    private static boolean ours = false;
    private static long lastMs = 0L;
    private static int start = 0;
    private static int total = 0;
    private static int window = 0;

    private TooltipScrollHandler() {
    }

    /**
     * 展开且内容超过一屏时每帧回调。
     * @param totalLines 内容总行数
     * @param windowLines 一屏能放下的行数
     */
    public static void present(ItemStack stack, int totalLines, int windowLines) {
        if (!ItemStack.isSameItemSameTags(tracked, stack)) {
            tracked = stack;
            start = 0;
        }
        if (!trackedExpanded) {
            // 刚刚按下 Shift 展开：从第一行（物品名）看起
            start = 0;
        }
        trackedExpanded = true;
        total = totalLines;
        window = windowLines;
        ours = true;
        lastMs = Util.getMillis();
        int max = total - window;
        if (max < 0) {
            max = 0;
        }
        if (start > max) {
            start = max;
        }
    }

    /** 未悬停永恒之心 / 收起 / 内容放得下：滚轮交还原版。 */
    public static void clear() {
        ours = false;
        trackedExpanded = false;
        total = 0;
        window = 0;
        start = 0;
    }

    /** 当前窗口第一行的行号（0 起）。由 present 里钳制后取值。 */
    public static int start() {
        return start;
    }

    /** 滚轮事件：内容装得下时不干预原版；翻不动（已到边界）时也交还原版。 */
    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (!ours || window <= 0) {
            return;
        }
        int max = total - window;
        if (max <= 0) {
            return;
        }
        if (Util.getMillis() - lastMs > FRESH_MS) {
            return;
        }
        double delta = event.getScrollDelta();
        if (delta == 0.0) {
            return;
        }
        // 滚轮向下(delta<0) → 看后面的行；向上 → 回看前面的行
        int next = (int)Math.round((double)start - delta * (double)LINES_PER_NOTCH);
        next = Math.max(0, Math.min(max, next));
        if (next == start) {
            return;
        }
        start = next;
        event.setCanceled(true);
    }
}
