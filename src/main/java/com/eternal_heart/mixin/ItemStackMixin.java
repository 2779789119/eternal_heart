package com.eternal_heart.mixin;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.features.EquipTracker;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * 工具不掉耐久 —— 佩戴时所有耐久消耗被跳过。
 *
 * <p><b>为什么需要 Mixin</b>：原版耐久消耗没有事件可挂，
 * {@code ItemStack#hurtAndBreak} 是挖掘、近战、自动钓鱼、连锁挖矿、
 * 甚至其它模组「自损」逻辑的<b>唯一</b>公共入口。在这个方法头部直接取消，
 * 等于让佩戴者的物品变成无限耐久，而不必逐个调用点打补丁。</p>
 *
 * <p>只对玩家生效：怪物 / 其它实体的物品耐久不受影响。附魔、属性、
 * 时运等与耐久无关的结算照常走原版。</p>
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = "hurtAndBreak", at = @At("HEAD"), cancellable = true)
    private void eternalHeart$skipDurability(int amount, LivingEntity entity,
                                             Consumer<LivingEntity> onBroken, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        if (!ConfigValues.get(EternalHeartConfig.ITEM_UNBREAKABLE)) return;
        if (!EquipTracker.isEquipped(player)) return;
        ci.cancel();
    }
}
