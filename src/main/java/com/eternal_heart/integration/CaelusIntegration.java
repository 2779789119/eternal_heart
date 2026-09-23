package com.eternal_heart.integration;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/**
 * Caelus 飞行集成 — 通过属性系统授予鞘翅飞行能力，无需依赖 Caelus 编译期 API。
 *
 * Caelus 的工作机制：
 * - 注册属性 caelus:elytra_flight，值 > 0 即允许玩家使用鞘翅飞行
 * - 优于 raw mayfly 操控：不泄漏状态、与其他模组兼容、自动清理
 *
 * 整合包来源：caelus-forge-3.2.0+1.20.1.jar
 */
final class CaelusIntegration {

    /** caelus:elytra_flight 属性的修改器 UUID（固定，与 mod_id 无关）。 */
    private static final UUID FLIGHT_UUID = UUID.fromString("e8f1b6a2-3d7c-4f9a-a1b2-c3d4e5f67890");

    private static net.minecraft.world.entity.ai.attributes.Attribute FLIGHT_ATTR;

    static {
        // 从注册表中查找 Caelus 的飞行属性
        try {
            FLIGHT_ATTR = ForgeRegistries.ATTRIBUTES.getValue(
                    new net.minecraft.resources.ResourceLocation("caelus", "elytra_flight"));
        } catch (Exception ignored) {}
    }

    /** 鞘翅飞行是否真正可用（Caelus 已安装且属性注册成功） */
    static boolean isAvailable() {
        return FLIGHT_ATTR != null;
    }

    /** 启用/禁用鞘翅飞行 */
    static void setFlight(Player player, boolean enable) {
        if (FLIGHT_ATTR == null) return;
        AttributeInstance attr = player.getAttribute(FLIGHT_ATTR);
        if (attr == null) return;

        if (enable && attr.getModifier(FLIGHT_UUID) == null) {
            attr.addTransientModifier(new AttributeModifier(
                    FLIGHT_UUID, "Eternal Heart Elytra Flight",
                    1.0, AttributeModifier.Operation.ADDITION));
        } else if (!enable) {
            attr.removeModifier(FLIGHT_UUID);
        }
    }
}
