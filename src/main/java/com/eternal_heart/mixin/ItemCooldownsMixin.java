package com.eternal_heart.mixin;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.core.CooldownOwner;
import com.eternal_heart.features.EquipTracker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 冷却缩减 —— 把「物品 / 装备技能」的冷却时长按配置倍率缩放。
 *
 * <p><b>为什么要 Mixin</b>：原版冷却没有任何事件可挂钩，{@code ItemCooldowns} 也没有公开的
 * 「读取剩余时间」接口，唯一的施加入口就是 {@link ItemCooldowns#addCooldown}。
 * 大量模组（例如灾变 Cataclysm 的武器技能：{@code player.getCooldowns().addCooldown(this, cooldown)}）
 * 正是通过它进入冷却，因此在最上游缩放时长即可<b>通用地</b>生效。</p>
 *
 * <p>用 {@code @ModifyVariable} 改写入参而不是取消后重入：无递归、无重入保护、
 * 也不改变原方法语义（倍率 1.0 时返回值与原值完全相同）。</p>
 *
 * <p>两端都会生效：服务端决定技能实际可用性，客户端负责 HUD 冷却显示，
 * 两边读同一份配置（客户端读服务器同步值）因此表现一致。</p>
 */
@Mixin(ItemCooldowns.class)
public abstract class ItemCooldownsMixin implements CooldownOwner {

    /** 本冷却表的归属玩家（由 {@code PlayerCooldownOwnerMixin} 在玩家构造末尾写入）。 */
    @Unique
    private Player eternalHeart$owner;

    @Override
    public void eternalHeart$setOwner(Player player) {
        this.eternalHeart$owner = player;
    }

    @Override
    public Player eternalHeart$getOwner() {
        return this.eternalHeart$owner;
    }

    /**
     * 缩放冷却时长。
     *
     * <p>倍率语义：{@code 1.0} 不变、{@code 0.5} 减半、{@code 0} 无冷却；
     * 值域完全放开（负数按无冷却处理，大于 1 则延长冷却）。</p>
     */
    @ModifyVariable(method = "addCooldown", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int eternalHeart$shortenCooldown(int ticks) {
        Player owner = this.eternalHeart$owner;
        if (owner == null || ticks <= 0) return ticks;

        double ratio = ConfigValues.get(EternalHeartConfig.COOLDOWN_RATIO);
        if (ratio == 1.0) return ticks; // 默认：完全不干预
        if (!EquipTracker.isEquipped(owner)) return ticks;

        double scaled = ticks * ratio;
        if (scaled >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) Math.max(0, Math.ceil(scaled));
    }
}
