package com.eternal_heart.client.tooltip;

import net.minecraft.client.gui.screens.Screen;

/** 客户端提示框的展开状态：按住 Shift 才显示完整列表。 */
public final class EternalHeartTooltipState {

    private EternalHeartTooltipState() {
    }

    public static boolean expanded() {
        return Screen.hasShiftDown();
    }
}
