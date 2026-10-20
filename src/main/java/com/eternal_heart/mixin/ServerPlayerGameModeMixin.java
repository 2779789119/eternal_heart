package com.eternal_heart.mixin;

import com.eternal_heart.features.UtilitySystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 放开「管理员方块」的破坏限制。
 *
 * <p>命令方块 / 结构方块 / 拼图方块除了硬度 -1 之外，还实现了 {@code GameMasterBlock}：
 * {@code ServerPlayerGameMode#destroyBlock} 会要求 {@code canUseGameMasterBlocks()}
 * （创造 + 权限等级 ≥ 2），否则直接把方块刷回去。只改进度是不够的，这里把这一条判定
 * 在「瞬挖 + 破不可破坏 + 佩戴」时放开。</p>
 *
 * <p>出生点保护 / 冒险模式 / 领地插件走的 {@code blockActionRestricted} 不在此列，
 * 仍然正常生效。</p>
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    @Redirect(
            method = "destroyBlock",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;canUseGameMasterBlocks()Z"))
    private boolean eternalHeart$allowGameMasterBlocks(ServerPlayer player) {
        return UtilitySystem.canBreakUnbreakable(player) || player.canUseGameMasterBlocks();
    }
}
