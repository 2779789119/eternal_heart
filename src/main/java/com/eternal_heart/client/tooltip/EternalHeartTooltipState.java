/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screens.Screen
 */
package com.eternal_heart.client.tooltip;

import net.minecraft.client.gui.screens.Screen;

public final class EternalHeartTooltipState {
    private EternalHeartTooltipState() {
    }

    public static boolean expanded() {
        return Screen.hasShiftDown();
    }
}

