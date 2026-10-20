package com.eternal_heart.mixin;

import com.eternal_heart.features.UtilitySystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 「不可破坏」方块的挖掘进度改写。
 *
 * <p><b>为什么必须动方法层</b>：原版 {@code getDestroyProgress} 对硬度 -1 的方块
 * 在最前面就 {@code return 0.0F}，根本不读取挖掘速度 —— 单纯抬高
 * {@code PlayerEvent.BreakSpeed} 对基岩 / 屏障毫无作用。这里在返回值上改成 1.0，
 * 让客户端与服务端都认为「一次结算就挖穿」。</p>
 *
 * <p>是否生效由 {@link UtilitySystem#canBreakUnbreakable} 判定（佩戴 + 瞬挖 +
 * 破不可破坏开关）。只改硬度 -1 的方块，不影响正常方块与其它模组自定义的进度逻辑。</p>
 */
@Mixin(BlockBehaviour.class)
public abstract class BlockBehaviourMixin {

    @Inject(method = "getDestroyProgress", at = @At("RETURN"), cancellable = true)
    private void eternalHeart$breakUnbreakable(BlockState state, Player player, BlockGetter level, BlockPos pos,
                                               CallbackInfoReturnable<Float> cir) {
        // 绝大多数方块返回非 0：先做最便宜的判断，避免每 tick 都去查配置 / 饰品
        if (cir.getReturnValueF() != 0.0F) return;
        // 硬度 -1 才是「不可破坏」；进度为 0 但硬度正常的方块（保护类）不碰
        if (state.getDestroySpeed(level, pos) >= 0.0F) return;
        if (!UtilitySystem.canBreakUnbreakable(player)) return;
        cir.setReturnValue(1.0F);
    }
}
