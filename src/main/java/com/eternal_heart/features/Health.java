package com.eternal_heart.features;

import net.minecraft.world.entity.player.Player;

/**
 * 生命值变更统一入口 —— 支持「带符号」的生命变化（正数治疗、负数扣血）。
 *
 * <p>配置下限放开后，「生命回复」「击杀回血」「生命窃取」都可以填负数，
 * 语义即"持续掉血 / 击杀扣血 / 吸血变失血"。</p>
 *
 * <p>为什么需要这个入口：负治疗无法走原版 {@code heal()}（它会直接把血量推到上限），
 * 只能走 {@code setHealth}；而旁路改血会被防护机制的生命审计判定为「被改血」并回滚，
 * 表现为"填了负数却没反应"。因此负值路径在这里主动告知审计层：
 * <b>这次变化是本模组按配置施加的，合法</b>。</p>
 */
public final class Health {

    private Health() {
    }

    /** 施加带符号的生命变化（正数治疗、负数扣血），并保证防护机制不会把它当成改血。 */
    public static void apply(Player player, float amount) {
        if (amount == 0 || player.level().isClientSide()) return;
        if (amount > 0) {
            player.heal(amount);
            return;
        }
        player.setHealth(Math.max(0f, player.getHealth() + amount));
        AegisSystem.acceptHealthChange(player);
    }
}
