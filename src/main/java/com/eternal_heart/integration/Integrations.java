package com.eternal_heart.integration;

import net.minecraftforge.fml.ModList;

/**
 * 统一管理所有可选模组集成的检测与功能分发。
 * 所有外部模组调用通过此类中转，避免 NoClassDefFoundError。
 */
public final class Integrations {

    private static final boolean FTB_ULTIMINE_LOADED;
    private static final boolean CAELUS_LOADED;
    private static final boolean ATTRIBUTE_FIX_LOADED;
    private static final boolean PEHKUI_LOADED;

    static {
        FTB_ULTIMINE_LOADED = ModList.get().isLoaded("ftbultimine");
        CAELUS_LOADED = ModList.get().isLoaded("caelus");
        ATTRIBUTE_FIX_LOADED = ModList.get().isLoaded("attributefix");
        PEHKUI_LOADED = ModList.get().isLoaded("pehkui");
    }

    // ==================== 查询 ====================

    public static boolean isFtbUltimineLoaded() { return FTB_ULTIMINE_LOADED; }
    public static boolean isCaelusLoaded()       { return CAELUS_LOADED; }
    public static boolean isAttributeFixLoaded() { return ATTRIBUTE_FIX_LOADED; }
    public static boolean isPehkuiLoaded()       { return PEHKUI_LOADED; }

    // ==================== FTB Ultimine ====================

    /** 以"无形状"模式激活 FTB Ultimine（自动查找同类方块连锁挖掘）。 */
    public static void activateUltimine(Object player) {
        if (FTB_ULTIMINE_LOADED) FTBUltimineIntegration.activate((net.minecraft.world.entity.player.Player) player);
    }

    /** 关闭 FTB Ultimine。 */
    public static void deactivateUltimine(Object player) {
        if (FTB_ULTIMINE_LOADED) FTBUltimineIntegration.deactivate((net.minecraft.world.entity.player.Player) player);
    }

    // ==================== Caelus - 鞘翅飞行 ====================

    /** 为玩家启用鞘翅飞行（无需装备鞘翅）。 */
    public static void setElytraFlight(Object player, boolean enable) {
        if (CAELUS_LOADED) CaelusIntegration.setFlight((net.minecraft.world.entity.player.Player) player, enable);
    }

    // ==================== AttributeFix ====================

    /** 获取无上限的最大生命值（AttributeFix 移除 1024 上限后可用）。 */
    public static double getEffectiveMaxHealthCap() {
        if (ATTRIBUTE_FIX_LOADED) return Double.MAX_VALUE;
        return 1024.0;
    }

    // ==================== Pehkui ====================

    /** 设置玩家体型缩放（1.0 = 正常）。 */
    public static void setScale(Object player, float scale) {
        if (PEHKUI_LOADED) PehkuiIntegration.setScale((net.minecraft.world.entity.player.Player) player, scale);
    }

    /** 重置玩家体型到默认。 */
    public static void resetScale(Object player) {
        if (PEHKUI_LOADED) PehkuiIntegration.resetScale((net.minecraft.world.entity.player.Player) player);
    }
}
