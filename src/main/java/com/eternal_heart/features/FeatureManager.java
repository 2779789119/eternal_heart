package com.eternal_heart.features;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.level.BlockEvent;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.SlotContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 功能管理器 —— 统一注册与调度所有功能模块。
 *
 * <p>重写要点：</p>
 * <ul>
 *   <li><b>数组化注册</b>：模块列表在类加载时一次性构建为不可变数组
 *       （原先每次调度都要遍历可变 {@code ArrayList} 并逐个空检查）；</li>
 *   <li><b>统一错误处理与限流</b>：单个模块抛错不再刷屏（原实现每次都
 *       {@code printStackTrace}），同一模块同一阶段最多打印 5 条日志，
 *       之后仅计数——避免异常情况下每秒 20 行堆栈拖垮日志；</li>
 *   <li><b>捕获范围扩展</b>：同时捕获 {@link LinkageError}，
 *       可选模组缺失导致的桥接类加载失败不会再打断其它模块的 tick。</li>
 * </ul>
 *
 * <p>扩展指南：</p>
 * <ol>
 *   <li>创建新类实现 {@link IFeature}；</li>
 *   <li>在 {@link #FEATURES} 数组中按优先级顺序登记（先登记的先执行）；</li>
 *   <li>完成。</li>
 * </ol>
 */
public final class FeatureManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 同一模块同一阶段的错误日志上限（超出后只计数，不再打印）。 */
    private static final int MAX_ERROR_LOGS = 5;

    /** 所有功能模块（登记顺序 = 执行顺序）。 */
    private static final IFeature[] FEATURES = {
            new AttributeFeature(),
            new FurySystem(),
            new DefenseSystem(),
            new CombatSystem(),
            new MobilitySystem(),
            new SurvivalSystem(),
            new UtilitySystem(),
            new AuraSystem(),
            // 防护机制放最后：死亡处理需要看到前面模块（不死图腾 / 灵魂绑定）是否已取消死亡
            new AegisSystem(),
            // 饰品守护：死亡不掉落 / 无视绑定（同样位于死亡处理链的最后）
            new CurioGuard(),
    };

    private static final Map<String, Integer> ERROR_COUNTS = new HashMap<>();

    private FeatureManager() {
    }

    /** 触发类加载并完成注册（由主类在构造阶段调用；模块实例在类加载时已构建）。 */
    public static void registerFeatures() {
        // 实例构建无副作用，已随类加载完成；此方法保留为初始化入口与调用点兼容。
        LOGGER.debug("[永恒之心] 已注册 {} 个功能模块", FEATURES.length);
    }

    /** 当前已启用的功能模块（快照，供 UI / 调试使用）。 */
    public static List<IFeature> getEnabledFeatures() {
        List<IFeature> enabled = new ArrayList<>(FEATURES.length);
        for (IFeature feature : FEATURES) {
            if (feature.isEnabled()) enabled.add(feature);
        }
        return Collections.unmodifiableList(enabled);
    }

    // ============================================================
    //  事件分发
    // ============================================================

    public static void dispatchPlayerTick(Player player, LivingTickEvent event, boolean clientSide) {
        for (IFeature feature : FEATURES) {
            if (!feature.isEnabled()) continue;
            try {
                feature.onPlayerTickBoth(player, event);
                if (!clientSide) feature.onPlayerTick(player, event);
            } catch (Exception | LinkageError error) {
                report(feature, "tick", error);
            }
        }
    }

    public static void dispatchPlayerHurt(Player player, LivingHurtEvent event) {
        for (IFeature feature : FEATURES) {
            if (event.isCanceled() || event.getAmount() <= 0) break;
            if (!feature.isEnabled()) continue;
            try {
                feature.onPlayerHurt(player, event);
            } catch (Exception | LinkageError error) {
                report(feature, "hurt", error);
            }
        }
    }

    public static void dispatchPlayerAttack(Player player, LivingAttackEvent event) {
        for (IFeature feature : FEATURES) {
            if (!feature.isEnabled()) continue;
            try {
                feature.onPlayerAttack(player, event);
            } catch (Exception | LinkageError error) {
                report(feature, "attack", error);
            }
        }
    }

    public static void dispatchTargetHurt(Player player, LivingHurtEvent event) {
        for (IFeature feature : FEATURES) {
            if (!feature.isEnabled()) continue;
            try {
                feature.onTargetHurt(player, event);
            } catch (Exception | LinkageError error) {
                report(feature, "targetHurt", error);
            }
        }
    }

    public static void dispatchCriticalHit(Player player, CriticalHitEvent event) {
        for (IFeature feature : FEATURES) {
            if (!feature.isEnabled()) continue;
            try {
                feature.onCriticalHit(player, event);
            } catch (Exception | LinkageError error) {
                report(feature, "critical", error);
            }
        }
    }

    /** 最终伤害结算（护甲 / 抗性 / 吸收之后，扣血之前）。 */
    public static void dispatchFinalDamage(Player player, LivingDamageEvent event) {
        for (IFeature feature : FEATURES) {
            if (!feature.isEnabled()) continue;
            try {
                feature.onFinalDamage(player, event);
            } catch (Exception | LinkageError error) {
                report(feature, "finalDamage", error);
            }
        }
    }

    public static void dispatchPlayerDeath(Player player, LivingDeathEvent event) {
        for (IFeature feature : FEATURES) {
            if (!feature.isEnabled()) continue;
            try {
                feature.onPlayerDeath(player, event);
            } catch (Exception | LinkageError error) {
                report(feature, "death", error);
            }
        }
    }

    public static void dispatchPlayerKill(Player player, LivingDeathEvent event) {
        for (IFeature feature : FEATURES) {
            if (!feature.isEnabled()) continue;
            try {
                feature.onPlayerKill(player, event);
            } catch (Exception | LinkageError error) {
                report(feature, "kill", error);
            }
        }
    }

    public static void dispatchBlockBreak(Player player, BlockEvent.BreakEvent event) {
        for (IFeature feature : FEATURES) {
            if (!feature.isEnabled()) continue;
            try {
                feature.onBlockBreak(player, event);
            } catch (Exception | LinkageError error) {
                report(feature, "blockBreak", error);
            }
        }
    }

    /** 汇总所有模块的属性修饰符（Curios 装备时调用）。 */
    public static Multimap<Attribute, AttributeModifier> buildAttributeModifiers(SlotContext slotContext, UUID uuid,
                                                                                 ItemStack stack) {
        Multimap<Attribute, AttributeModifier> modifiers = ArrayListMultimap.create();
        for (IFeature feature : FEATURES) {
            if (!feature.isEnabled()) continue;
            try {
                feature.addAttributeModifiers(slotContext, uuid, stack, modifiers);
            } catch (Exception | LinkageError error) {
                report(feature, "attributes", error);
            }
        }
        return modifiers;
    }

    public static void dispatchEquip(Player player) {
        dispatchLifecycle(player, true);
    }

    public static void dispatchUnequip(Player player) {
        dispatchLifecycle(player, false);
    }

    private static void dispatchLifecycle(Player player, boolean equip) {
        for (IFeature feature : FEATURES) {
            if (!feature.isEnabled()) continue;
            try {
                if (equip) feature.onEquip(player); else feature.onUnequip(player);
            } catch (Exception | LinkageError error) {
                report(feature, equip ? "equip" : "unequip", error);
            }
        }
    }

    // ============================================================
    //  错误上报（限流）
    // ============================================================

    private static void report(IFeature feature, String phase, Throwable error) {
        String key = feature.getName() + '@' + phase;
        int count = ERROR_COUNTS.merge(key, 1, Integer::sum);
        if (count <= MAX_ERROR_LOGS) {
            LOGGER.error("[永恒之心] 功能模块 {} 在 {} 阶段出错（第 {} 次）",
                    feature.getName(), phase, count, error);
        } else if (count == MAX_ERROR_LOGS + 1) {
            LOGGER.error("[永恒之心] 功能模块 {} 在 {} 阶段的错误过多，后续同类错误不再打印",
                    feature.getName(), phase);
        }
    }
}
