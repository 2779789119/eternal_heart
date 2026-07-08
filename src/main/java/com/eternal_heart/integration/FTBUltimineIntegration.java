package com.eternal_heart.integration;

import net.minecraft.world.entity.player.Player;

/**
 * FTB Ultimine（连锁破坏）集成 — 反射调用，避免硬编译依赖。
 * FTB Ultimine 已安装在整合包中 (ftb-ultimine-forge-2001.1.8)。
 *
 * 工作原理：
 * 1. 永恒之心玩家破坏矿石时，先激活 FTB Ultimine 的无形状模式
 * 2. FTB Ultimine 自动扫描相连的同类方块并全部破坏（含工具耐久、掉落等）
 * 3. 操作完成后关闭 FTB Ultimine
 *
 * 相比自定义 BFS 的优势：
 * - FTB Ultimine 已处理所有边缘情况（工具耐久、附魔、掉落保护等）
 * - 配置灵活（玩家可用默认快捷键自定义形状）
 * - 第三方模组兼容性更好
 */
final class FTBUltimineIntegration {

    private static Class<?> ultimineClass;
    private static Object instance;
    private static java.lang.reflect.Method setActive;

    static {
        try {
            ultimineClass = Class.forName("dev.ftb.mods.ftbultimine.FTBUltimine");
            instance = ultimineClass.getField("instance").get(null);
            setActive = ultimineClass.getMethod("setActive", Player.class, boolean.class, boolean.class);
        } catch (Exception ignored) {
            // 反射初始化失败则降级为自定义 BFS
        }
    }

    /** 激活无形状模式：扫描相连的同类方块 */
    static void activate(Player player) {
        if (setActive == null) return;
        try {
            setActive.invoke(instance, player, true, false);
        } catch (Exception ignored) {}
    }

    /** 关闭 Ultimine */
    static void deactivate(Player player) {
        if (setActive == null) return;
        try {
            setActive.invoke(instance, player, false, false);
        } catch (Exception ignored) {}
    }
}
