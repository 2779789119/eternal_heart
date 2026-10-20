package com.eternal_heart.mixin;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.features.TimeStop;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * 时停的**主冻结层** —— 一次性覆盖全部实体，包括弹射物。
 *
 * <p>为什么要拦这里而不是 {@code Entity#tick}：弹射物（箭、火球、雪球、三叉戟、烟花……）
 * 各自重写了 {@code tick()} 并且<b>不调用</b> {@code Entity#tick}，只拦基类方法对它们完全无效。
 * 而 {@code Level#guardEntityTick} 是世界 tick 每个实体的必经入口（服务端
 * {@code ServerLevel.tickNonPassenger} 与客户端 {@code ClientLevel.tickEntities} 都经由它），
 * 在这里取消即可同时冻结：</p>
 * <ul>
 *   <li>生物（AI、移动、感知）；</li>
 *   <li>弹射物（飞行、命中判定）；</li>
 *   <li>掉落物、经验球、矿车、TNT 等一切实体。</li>
 * </ul>
 *
 * <p>取消整个 consumer 还意味着实体的 {@code setOldPosAndRot} 不会执行，
 * 位置与朝向都不再推进 —— 客户端插值也因此停在原地。</p>
 *
 * <p>{@code EntityFreezeMixin} 与 {@code LivingEntityMixin} 中的同名注入保留为双保险：
 * 少数模组会绕过世界 tick 直接调用 {@code entity.tick()}。</p>
 *
 * <h3>方块实体</h3>
 * <p>实体的 tick 和方块实体的 tick 是两条独立的路径：{@code Level#tickBlockEntities}
 * 遍历 {@code blockEntityTickers} 逐个调用。旧实现只冻结实体，因此「时停」期间
 * 熔炉照烧、漏斗照搬、刷怪笼照刷。这里在 {@code Level}（两端共用）的入口取消，
 * 让机器逻辑一并停下。</p>
 */
@Mixin(Level.class)
public abstract class LevelFreezeMixin {

    @Inject(method = "guardEntityTick", at = @At("HEAD"), cancellable = true)
    private void eternalHeart$freezeInTimeStop(Consumer<Entity> consumer, Entity entity, CallbackInfo ci) {
        if (TimeStop.frozen(entity)) {
            ci.cancel();
        }
    }

    /**
     * 时停·方块实体冻结（服务端 / 客户端共用入口）。
     *
     * <p>整维度取消而不是逐个判断位置：ticker 表是维度级的，逐条坐标过滤成本远高于收益；
     * 半径模式下多冻结几条远处机器并无副作用（范围内的目标本来就被冻结）。</p>
     */
    @Inject(method = "tickBlockEntities", at = @At("HEAD"), cancellable = true)
    private void eternalHeart$freezeBlockEntities(CallbackInfo ci) {
        if (!ConfigValues.get(EternalHeartConfig.TIME_STOP)) return;
        if (!ConfigValues.get(EternalHeartConfig.TIME_STOP_FREEZE_BLOCK_ENTITIES)) return;
        if (TimeStop.frozenDimension((Level) (Object) this)) {
            ci.cancel();
        }
    }
}
