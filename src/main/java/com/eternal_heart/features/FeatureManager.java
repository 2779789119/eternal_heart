package com.eternal_heart.features;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.level.BlockEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import com.eternal_heart.EternalHeartMod;

import java.util.ArrayList;
import java.util.List;

/**
 * 功能管理器 - 统一注册和调度所有功能模块
 * 
 * 扩展指南：
 * 1. 创建新类实现 IFeature 接口
 * 2. 在 registerFeatures() 中添加 features.add(new YourFeature());
 * 3. 完成
 */
public class FeatureManager {
    
    private static final List<IFeature> features = new ArrayList<>();
    private static boolean initialized = false;
    
    /** 注册所有功能模块 */
    public static void registerFeatures() {
        if (initialized) return;
        initialized = true;
        
        // 按优先级顺序注册（先注册的先执行）
        // 属性系统
        features.add(new AttributeFeature());
        
        // 永怒系统
        features.add(new FurySystem());
        
        // 防御系统
        features.add(new DefenseSystem());
        
        // 战斗系统
        features.add(new CombatSystem());
        
        // 机动系统
        features.add(new MobilitySystem());
        
        // 生存系统
        features.add(new SurvivalSystem());
        
        // 便利功能
        features.add(new UtilitySystem());
        
        // 光环系统
        features.add(new AuraSystem());
    }
    
    /** 检查玩家是否装备了永恒之心 */
    public static boolean hasEternalHeart(Player player) {
        return CuriosApi.getCuriosInventory(player).resolve().flatMap(
                curios -> curios.findFirstCurio(EternalHeartMod.ETERNAL_HEART.get())
        ).isPresent();
    }
    
    /** 获取所有已启用的功能 */
    public static List<IFeature> getEnabledFeatures() {
        List<IFeature> enabled = new ArrayList<>();
        for (IFeature f : features) {
            if (f.isEnabled()) enabled.add(f);
        }
        return enabled;
    }
    
    // ===== 事件分发 =====
    
    public static void dispatchPlayerTick(Player player, LivingTickEvent event, boolean clientSide) {
        for (IFeature f : features) {
            if (!f.isEnabled()) continue;
            try {
                f.onPlayerTickBoth(player, event);
                if (!clientSide) {
                    f.onPlayerTick(player, event);
                }
            } catch (Exception e) {
                // 单个功能出错不影响其他功能
                System.err.println("[EternalHeart] Feature " + f.getName() + " error in tick: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    public static void dispatchPlayerHurt(Player player, LivingHurtEvent event) {
        for (IFeature f : features) {
            if (!f.isEnabled()) continue;
            try {
                f.onPlayerHurt(player, event);
            } catch (Exception e) {
                System.err.println("[EternalHeart] Feature " + f.getName() + " error in hurt: " + e.getMessage());
            }
        }
    }
    
    public static void dispatchPlayerAttack(Player player, LivingAttackEvent event) {
        for (IFeature f : features) {
            if (!f.isEnabled()) continue;
            try {
                f.onPlayerAttack(player, event);
            } catch (Exception e) {
                System.err.println("[EternalHeart] Feature " + f.getName() + " error in attack: " + e.getMessage());
            }
        }
    }
    
    public static void dispatchTargetHurt(Player player, LivingHurtEvent event) {
        for (IFeature f : features) {
            if (!f.isEnabled()) continue;
            try {
                f.onTargetHurt(player, event);
            } catch (Exception e) {
                System.err.println("[EternalHeart] Feature " + f.getName() + " error in targetHurt: " + e.getMessage());
            }
        }
    }
    
    public static void dispatchCriticalHit(Player player, CriticalHitEvent event) {
        for (IFeature f : features) {
            if (!f.isEnabled()) continue;
            try {
                f.onCriticalHit(player, event);
            } catch (Exception e) {
                System.err.println("[EternalHeart] Feature " + f.getName() + " error in crit: " + e.getMessage());
            }
        }
    }
    
    public static void dispatchPlayerDeath(Player player, LivingDeathEvent event) {
        for (IFeature f : features) {
            if (!f.isEnabled()) continue;
            try {
                f.onPlayerDeath(player, event);
            } catch (Exception e) {
                System.err.println("[EternalHeart] Feature " + f.getName() + " error in death: " + e.getMessage());
            }
        }
    }
    
    public static void dispatchPlayerKill(Player player, LivingDeathEvent event) {
        for (IFeature f : features) {
            if (!f.isEnabled()) continue;
            try {
                f.onPlayerKill(player, event);
            } catch (Exception e) {
                System.err.println("[EternalHeart] Feature " + f.getName() + " error in kill: " + e.getMessage());
            }
        }
    }
    
    public static void dispatchBlockBreak(Player player, BlockEvent.BreakEvent event) {
        for (IFeature f : features) {
            if (!f.isEnabled()) continue;
            try {
                f.onBlockBreak(player, event);
            } catch (Exception e) {
                System.err.println("[EternalHeart] Feature " + f.getName() + " error in blockBreak: " + e.getMessage());
            }
        }
    }
    
    public static Multimap<Attribute, AttributeModifier> buildAttributeModifiers(SlotContext slotContext, java.util.UUID uuid, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> map = ArrayListMultimap.create();
        for (IFeature f : features) {
            if (!f.isEnabled()) continue;
            try {
                f.addAttributeModifiers(slotContext, uuid, stack, map);
            } catch (Exception e) {
                System.err.println("[EternalHeart] Feature " + f.getName() + " error in attributes: " + e.getMessage());
            }
        }
        return map;
    }
    
    public static void dispatchEquip(Player player) {
        for (IFeature f : features) {
            if (!f.isEnabled()) continue;
            try {
                f.onEquip(player);
            } catch (Exception e) {
                System.err.println("[EternalHeart] Feature " + f.getName() + " error in equip: " + e.getMessage());
            }
        }
    }
    
    public static void dispatchUnequip(Player player) {
        for (IFeature f : features) {
            if (!f.isEnabled()) continue;
            try {
                f.onUnequip(player);
            } catch (Exception e) {
                System.err.println("[EternalHeart] Feature " + f.getName() + " error in unequip: " + e.getMessage());
            }
        }
    }
}
