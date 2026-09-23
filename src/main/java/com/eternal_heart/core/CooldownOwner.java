package com.eternal_heart.core;

import net.minecraft.world.entity.player.Player;

/**
 * 「这张冷却表是谁的」——由 {@code ItemCooldownsMixin} 实现、{@code PlayerCooldownOwnerMixin} 写入。
 *
 * <p>原版 {@code ItemCooldowns}（1.20.1）是<b>无参构造、不持有玩家引用</b>的纯记账类，
 * 而冷却缩减必须知道归属者才能判断「他是否佩戴了永恒之心」。</p>
 *
 * <p><b>为什么放在 core 而不是 mixin 包</b>：Mixin 禁止 mixin 包（由 mixins.json 声明的那个包）
 * 里的普通类被包外代码直接引用，接口放在那里会在运行时抛出
 * {@code ... is in a defined mixin package ... and cannot be referenced directly}。</p>
 */
public interface CooldownOwner {

    void eternalHeart$setOwner(Player player);

    Player eternalHeart$getOwner();
}
