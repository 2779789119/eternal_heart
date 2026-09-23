package com.eternal_heart.integration;

import net.minecraft.world.entity.player.Player;

/**
 * FTB Ultimine（连锁破坏）集成 — 反射调用，避免硬编译依赖。
 * Optional bridge: installation alone does not prove API compatibility.
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
    private static boolean warned;

    static {
        try {
            ultimineClass = Class.forName("dev.ftb.mods.ftbultimine.FTBUltimine");
            instance = ultimineClass.getField("instance").get(null);
            setActive = ultimineClass.getMethod("setActive", Player.class, boolean.class, boolean.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            warn(e);
        }
    }

    /** 激活无形状模式：扫描相连的同类方块 */
    static boolean activate(Player player) {
        if (setActive == null || instance == null) return false;
        try {
            Object result = setActive.invoke(instance, player, true, false);
            if (result instanceof Boolean success && !success) {
                deactivate(player);
                return false;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            warn(e);
            deactivate(player);
            setActive = null;
            return false;
        }
    }

    /** 关闭 Ultimine */
    static void deactivate(Player player) {
        if (setActive == null) return;
        try {
            setActive.invoke(instance, player, false, false);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            warn(e);
        }
    }

    private static void warn(Throwable error) {
        if (warned) return;
        warned = true;
        com.mojang.logging.LogUtils.getLogger().warn("FTB Ultimine bridge unavailable; using built-in vein mining", error);
    }
}
