package com.eternal_heart.mixin;

import com.eternal_heart.core.CooldownOwner;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把玩家的冷却表与玩家本人绑定 —— 冷却缩减的前置条件。
 *
 * <p>原版 {@code ItemCooldowns} 不持有玩家引用，而缩放时必须判断
 * 「这张冷却表的主人是否佩戴永恒之心」。在玩家构造末尾写入一次即可：
 * 冷却表实例是玩家的 final 字段，随玩家实例共存亡，重生 / 换维度后新建的实例
 * 会在构造时自动重新绑定，无需在 tick 中反复检查。</p>
 *
 * <p>写法说明：{@code getCooldowns()} 返回的对象在运行时已被
 * {@link ItemCooldownsMixin} 赋予 {@link CooldownOwner} 能力，
 * 因此这里的转型是安全的（若该 Mixin 未生效，本注入也不会生效）。</p>
 */
@Mixin(Player.class)
public abstract class PlayerCooldownOwnerMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void eternalHeart$bindCooldownOwner(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        ((CooldownOwner) self.getCooldowns()).eternalHeart$setOwner(self);
    }
}
