package com.eternal_heart.features;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * 永恒之心功能模块接口
 * 所有功能模块都实现此接口，由 FeatureManager 统一调度
 */
public interface IFeature {
    
    /** 功能名称，用于调试和日志 */
    String getName();
    
    /** 是否启用该功能 */
    boolean isEnabled();
    
    // ===== Tick 事件 =====
    
    /** 玩家每 tick 调用（服务端） */
    default void onPlayerTick(Player player, LivingTickEvent event) {}
    
    /** 玩家每 tick 调用（客户端+服务端都运行） */
    default void onPlayerTickBoth(Player player, LivingTickEvent event) {}
    
    // ===== 战斗事件 =====
    
    /** 玩家受伤时调用（高优先级，用于免疫/减免） */
    default void onPlayerHurt(Player player, LivingHurtEvent event) {}
    
    /** 玩家攻击时调用 */
    default void onPlayerAttack(Player player, LivingAttackEvent event) {}
    
    /** 攻击目标受伤时调用（低优先级，用于增伤） */
    default void onTargetHurt(Player player, LivingHurtEvent event) {}
    
    /** 暴击事件 */
    default void onCriticalHit(Player player, CriticalHitEvent event) {}
    
    /** 玩家死亡事件 */
    default void onPlayerDeath(Player player, LivingDeathEvent event) {}
    
    /** 玩家击杀事件 */
    default void onPlayerKill(Player player, LivingDeathEvent event) {}
    
    // ===== 物品属性 =====
    
    /** 添加 Curio 装备时的属性修饰符 */
    default void addAttributeModifiers(SlotContext slotContext, java.util.UUID uuid, ItemStack stack, Multimap<Attribute, AttributeModifier> map) {}
    
    // ===== 方块事件 =====
    
    /** 方块破坏事件 */
    default void onBlockBreak(Player player, BlockEvent.BreakEvent event) {}
    
    // ===== 装备/卸下 =====
    
    /** 装备饰品时调用 */
    default void onEquip(Player player) {}
    
    /** 卸下饰品时调用（用于清理状态） */
    default void onUnequip(Player player) {}
}
