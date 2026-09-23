package com.eternal_heart.mixin;

import com.eternal_heart.features.TimeStop;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 时停的基础冻结层 —— 覆盖**非生物实体**（掉落物、弹射物、TNT、矿车等）。
 *
 * <p>{@code Entity#tick} 是所有实体 tick 的必经之处（子类重写后都会调用
 * {@code super.tick()}），在最头部取消即可让实体：位置不更新、速度不生效、
 * 重力与碰撞都不再推进 —— 视觉上彻底静止。</p>
 *
 * <p>生物由 {@code LivingEntityMixin} 中的同名注入负责（{@code LivingEntity#tick}
 * 的其余逻辑不经过 {@code Entity#tick}，必须单独拦截）。</p>
 */
@Mixin(Entity.class)
public abstract class EntityFreezeMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void eternalHeart$freezeInTimeStop(CallbackInfo ci) {
        if (TimeStop.frozen((Entity) (Object) this)) {
            ci.cancel();
        }
    }
}
