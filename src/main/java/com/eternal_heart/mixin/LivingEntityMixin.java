package com.eternal_heart.mixin;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.core.Debuffs;
import com.eternal_heart.features.EquipTracker;
import com.eternal_heart.features.TimeStop;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 原版方法层的效果硬拦截。
 *
 * <p><b>为什么需要 Mixin</b>：{@code MobEffectEvent.Applicable} 只覆盖
 * {@code addEffect(...)} 这条常规路径；原版还提供
 * {@code LivingEntity#forceAddEffect(MobEffectInstance, Entity)}——「无视既有状态、
 * 强制写入」的入口，<b>不触发任何事件</b>。其它模组（或原版机制）经由它施加负面效果时，
 * 纯事件层方案会被完全绕过，只能在周期清扫阶段补救（最长一个清扫间隔的窗口期）。</p>
 *
 * <p>这里在最头部直接取消（cancel = 效果不写入），做到「零窗口期」。</p>
 *
 * <p><b>三级防线各司其职</b>：</p>
 * <ol>
 *   <li>{@code MobEffectEvent.Applicable}（预防层）—— 常规 {@code addEffect} 路径；</li>
 *   <li>本 Mixin（原版方法层）—— {@code forceAddEffect} 强制写入路径；</li>
 *   <li>{@code DefenseSystem} 周期清扫（兜底）—— 直接改写 activeEffects 等极端路径。</li>
 * </ol>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    /**
     * 拦截强制写入的负面效果。
     *
     * <p>目标方法签名：{@code forceAddEffect(MobEffectInstance, Entity)V}
     * （SRG {@code m_147215_}）—— 返回 void，故使用 {@link CallbackInfo}。</p>
     */
    @Inject(method = "forceAddEffect", at = @At("HEAD"), cancellable = true)
    private void eternal_heart$blockForcedDebuffs(MobEffectInstance instance, Entity source, CallbackInfo ci) {
        // Mixin 类的 this 静态类型是 mixin 自身，需先转 Object 再做类型判断
        if (!((Object) this instanceof Player player)) return;
        if (!EquipTracker.isEquipped(player)) return;
        if (!ConfigValues.get(EternalHeartConfig.DEBUFF_GUARD)) return;
        if (Debuffs.blocked(instance.getEffect())) {
            ci.cancel();
        }
    }

    /**
     * 时停冻结层 —— 覆盖**生物实体**。
     *
     * <p>{@code LivingEntity#tick} 的 AI / 移动 / 感知等逻辑不经过 {@code Entity#tick}，
     * 因此必须在这里单独拦截：取消即彻底静止（不移动、不攻击、不呼吸、不执行 AI）。</p>
     *
     * <p>是否冻结（含「发动者不受影响」「其它玩家按配置」）全部由
     * {@link TimeStop#frozen} 判定，本注入只做转发。</p>
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void eternalHeart$freezeInTimeStop(CallbackInfo ci) {
        if (TimeStop.frozen((Entity) (Object) this)) {
            ci.cancel();
        }
    }
}
