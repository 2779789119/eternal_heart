package com.eternal_heart.integration;

import net.minecraft.world.entity.player.Player;

/**
 * Pehkui 体型缩放集成 — 可选视觉效果。
 * 整合包来源：Pehkui-3.8.2+1.20.1-forge.jar
 */
final class PehkuiIntegration {

    private static Class<?> scaleTypes;
    private static Class<?> scaleModifier;
    private static Object baseScaleType;

    static {
        try {
            scaleTypes = Class.forName("virtuoel.pehkui.api.ScaleTypes");
            scaleModifier = Class.forName("virtuoel.pehkui.api.ScaleModifier");
            baseScaleType = scaleTypes.getField("BASE").get(null);

            // 缓存关键方法引用
            var getScaleData = scaleTypes.getMethod("getScaleData", net.minecraft.world.entity.Entity.class);
            var setTargetScale = Class.forName("virtuoel.pehkui.api.ScaleData")
                    .getMethod("setTargetScale", float.class);
        } catch (Exception ignored) {
            scaleTypes = null;
        }
    }

    static void setScale(Player player, float scale) {
        if (scaleTypes == null) return;
        try {
            Object scaleData = scaleTypes.getMethod("getScaleData", net.minecraft.world.entity.Entity.class)
                    .invoke(null, player);
            scaleData.getClass().getMethod("setTargetScale", float.class).invoke(scaleData, scale);
        } catch (Exception ignored) {}
    }

    static void resetScale(Player player) {
        setScale(player, 1.0f);
    }
}
