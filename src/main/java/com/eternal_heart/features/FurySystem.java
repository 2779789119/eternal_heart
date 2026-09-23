package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.EternalHeartMod;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.core.PlayerScoped;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.Event;

/**
 * 永怒（Eternal Fury）系统。
 *
 * <p>层数（stacks）是唯一状态源，通过 {@link EternalFuryEffect} 的属性修饰符
 * 提供「每层 +FURY_DAMAGE_PER_STACK 攻击伤害」，并随层数显示原版效果图标。</p>
 *
 * <p>重写要点（叠层热路径的合并同步）：</p>
 * <ul>
 *   <li>历史实现里每一次叠层都执行「移除效果 → 重新添加效果」，一次连击
 *       （攻击 +1、暴击 +2、击杀 +N）会触发多次属性重算与原版效果同步包；
 *       满怒后层数不再变化，却仍在每击重建。</li>
 *   <li>现在层数变化只写内存并置<b>脏标记</b>，在同一 tick 结尾统一同步一次
 *       （一次攻击链的多次叠层合并为一次同步）；满怒时零同步。</li>
 *   <li>同步策略也做了区分：升层直接 {@code addEffect}（原版内部走
 *       {@code update} 并自动重算属性，无需移除重建）；只有降层（原版 update
 *       不会降级）才移除重建。</li>
 *   <li>状态存于内存（{@link PlayerScoped}），仅在变化时写回 NBT 持久化。</li>
 * </ul>
 */
public class FurySystem implements IFeature {

    public static final String FURY_STACKS_KEY = "eternal_heart_fury_stacks";
    public static final String LAST_HIT_TIME_KEY = "eternal_heart_last_hit";

    /** 永怒效果持续时间（tick）。每次叠层都会刷新，正常情况下不会自然消失。 */
    private static final int FURY_EFFECT_DURATION = 400;

    /** 非原版暴击时的额外补判概率；保留原版暴击成立条件。 */
    private static final float FURY_CRIT_CHANCE = 0.5f;
    /** 暴击额外伤害加成（与 tooltip「暴击时 +10%」一致），叠加在原版 1.5 倍之上。 */
    private static final float FURY_CRIT_DAMAGE_BONUS = 0.10f;
    /** 原版暴击基础倍率。 */
    private static final float VANILLA_CRIT_MULTIPLIER = 1.5f;

    private static final PlayerScoped<Fury> STATES = new PlayerScoped<>();

    /** 单玩家的永怒状态。 */
    private static final class Fury {
        int stacks;
        long lastHit;
        /** 层数/时间戳自上次持久化后有变化，待同步到效果与 NBT */
        boolean dirty;
    }

    /** 状态载入：内存优先，其次从持久化 NBT 恢复（跨重启 / 重登保持层数）。 */
    private static Fury state(Player player) {
        Fury fury = STATES.get(player.getUUID());
        if (fury != null) return fury;

        fury = new Fury();
        CompoundTag data = player.getPersistentData();
        fury.stacks = clamp(data.getInt(FURY_STACKS_KEY));
        fury.lastHit = data.getLong(LAST_HIT_TIME_KEY);
        STATES.put(player.getUUID(), fury);
        return fury;
    }

    private static int clamp(int stacks) {
        return Math.max(0, Math.min(stacks, ConfigValues.get(EternalHeartConfig.FURY_MAX_STACKS)));
    }

    // ============================================================
    //  公开状态访问
    // ============================================================

    public static int getFuryStacks(Player player) {
        return state(player).stacks;
    }

    /** 直接设置层数并<strong>立即</strong>同步效果与持久化（配置同步、清理等场景使用）。 */
    public static void setFuryStacks(Player player, int stacks) {
        Fury fury = state(player);
        fury.stacks = clamp(stacks);
        fury.lastHit = player.level().getGameTime();
        flush(player, fury);
    }

    public static boolean isFuryMaxed(Player player) {
        return getFuryStacks(player) >= ConfigValues.get(EternalHeartConfig.FURY_MAX_STACKS);
    }

    /** 叠层（热路径）：只改内存 + 置脏标记，由 tick 统一同步，合并一次攻击链中的多次变化。 */
    private static void gainStacks(Player player, int delta) {
        if (delta == 0) return;
        Fury fury = state(player);
        // 用 long 相加：配置上限放开后 stacks + delta 可能越过 int 上限
        long next = (long) fury.stacks + delta;
        int stacked = clamp((int) Math.max(0, Math.min(next, Integer.MAX_VALUE)));
        fury.lastHit = player.level().getGameTime();
        if (stacked != fury.stacks) {
            fury.stacks = stacked;
            fury.dirty = true;
        }
    }

    private static void clearStacks(Player player) {
        Fury fury = state(player);
        if (fury.stacks == 0 && !player.hasEffect(EternalHeartMod.ETERNAL_FURY.get())) return;
        fury.stacks = 0;
        fury.dirty = true;
    }

    /** 配置热刷新：强制重算效果属性（层数不变也会触发一次属性重算）。 */
    public static void refreshEffect(Player player) {
        flush(player, state(player));
    }

    /** 玩家退出时清理内存状态。 */
    public static void forget(Player player) {
        STATES.remove(player.getUUID());
    }

    // ============================================================
    //  效果同步与持久化
    // ============================================================

    private static void flush(Player player, Fury fury) {
        syncEffect(player, fury);
        save(player, fury);
        fury.dirty = false;
    }

    /** 把层数同步到永怒效果：升层 addEffect（属性自动重算），降层 forceAddEffect 原地替换，清除则移除。 */
    private static void syncEffect(Player player, Fury fury) {
        if (player.level().isClientSide()) return;
        MobEffect effect = EternalHeartMod.ETERNAL_FURY.get();

        if (fury.stacks <= 0) {
            if (player.hasEffect(effect)) {
                player.getPersistentData().putInt(FURY_STACKS_KEY, 0);
                player.removeEffect(effect);
            }
            return;
        }

        int amplifier = fury.stacks - 1;
        MobEffectInstance current = player.getEffect(effect);
        MobEffectInstance next =
                new MobEffectInstance(effect, FURY_EFFECT_DURATION, amplifier, false, true, true);

        if (current != null && current.getAmplifier() > amplifier) {
            // 降层：原版 update 不会降级。但不能用「移除 + 重新添加」——
            // 客户端会先收移除包再收添加包，图标消失后重新插入（表现为闪烁）。
            // forceAddEffect 是原版「直接替换实例」入口：客户端只收更新包，图标原地刷新。
            player.forceAddEffect(next, null);
            return;
        }
        player.addEffect(next);
    }

    private static void save(Player player, Fury fury) {
        if (player.level().isClientSide()) return;
        CompoundTag data = player.getPersistentData();
        data.putInt(FURY_STACKS_KEY, fury.stacks);
        data.putLong(LAST_HIT_TIME_KEY, fury.lastHit);
    }

    // ============================================================
    //  IFeature
    // ============================================================

    @Override
    public String getName() {
        return "FurySystem";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void onPlayerTick(Player player, LivingTickEvent event) {
        Fury fury = state(player);
        long tick = player.level().getGameTime();

        if (fury.dirty) flush(player, fury);

        // 长时间未命中：每衰减周期掉一层（衰减是低频事件，立即同步以便图标层数即时反馈）
        if (fury.lastHit > 0 && tick - fury.lastHit > ConfigValues.get(EternalHeartConfig.FURY_DECAY_TICKS)
                && fury.stacks > 0) {
            fury.stacks--;
            fury.lastHit = tick;
            flush(player, fury);
        }
    }

    @Override
    public void onPlayerAttack(Player player, LivingAttackEvent event) {
        gainStacks(player, 1);

        // 满怒时每击获得生命窃取（与 tooltip「满 100% 后每击获得 10% 生命窃取」一致）
        if (!isFuryMaxed(player)) return;

        float lifesteal = event.getAmount() * ConfigValues.get(EternalHeartConfig.FURY_LIFESTEAL).floatValue();
        if (lifesteal == 0) return;

        // 负数 = 吸血变失血（配置下限已放开），走统一入口以免被生命审计回滚
        Health.apply(player, lifesteal);
        if (lifesteal <= 0) return;

        float foodRestore = ConfigValues.get(EternalHeartConfig.LIFESTEAL_FOOD_RESTORE).floatValue();
        float satRestore = ConfigValues.get(EternalHeartConfig.LIFESTEAL_SATURATION_RESTORE).floatValue();
        if (foodRestore > 0 || satRestore > 0) {
            var food = player.getFoodData();
            int newFood = Math.min(20, food.getFoodLevel() + (int) foodRestore);
            food.setFoodLevel(newFood);
            food.setSaturation(Math.min(newFood, food.getSaturationLevel() + satRestore));
        }
    }

    @Override
    public void onCriticalHit(Player player, CriticalHitEvent event) {
        boolean critical = event.isVanillaCritical();

        if (!critical) {
            // 非原版暴击时再做 50% 补判，不改变原版暴击的成立条件。
            if (player.getRandom().nextFloat() < FURY_CRIT_CHANCE) {
                event.setResult(Event.Result.ALLOW);
                // 强制暴击默认倍率为 1.0，需补回原版 1.5 倍基础，再叠加 +10%
                event.setDamageModifier(VANILLA_CRIT_MULTIPLIER + FURY_CRIT_DAMAGE_BONUS);
                critical = true;
            }
        } else {
            // 原版暴击：叠加 +10% 暴击伤害
            event.setDamageModifier(event.getDamageModifier() + FURY_CRIT_DAMAGE_BONUS);
        }

        if (critical) {
            // 暴击驱动叠层（+2 层，与玩家攻击本身的 +1 层合并为一次同步）
            gainStacks(player, 2);
        }
    }

    @Override
    public void onPlayerKill(Player player, LivingDeathEvent event) {
        gainStacks(player, ConfigValues.get(EternalHeartConfig.KILL_FURY_BONUS));
    }

    /** 伤害真正落到生命值之后调用：清空层数（闪避 / 免疫 / 完全吸收时保留）。 */
    public static void onDamageResolved(Player player, LivingDamageEvent event) {
        if (!event.isCanceled() && event.getAmount() > 0 && !player.level().isClientSide()) {
            clearStacks(player);
        }
    }

    @Override
    public void onUnequip(Player player) {
        // 卸下永恒之心时立即清理永怒状态（此后 tick 分发会停止，必须立即生效）
        setFuryStacks(player, 0);
    }
}
