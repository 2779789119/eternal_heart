package com.eternal_heart.mixin;

import com.eternal_heart.EternalHeartMod;
import com.eternal_heart.features.HeartFusion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 融合饰品的「虚拟已装备」查询。
 *
 * <p>融合饰品没有真的占用 Curios 槽位，但其它模组的事件型能力普遍靠
 * {@code CuriosApi.findFirstCurio(自己的物品)} 判断是否佩戴。这里在 Curios 的库存实现上兜底：
 * 真实槽位里找不到时，再查一遍已佩戴的永恒之心里的融合列表。</p>
 *
 * <p>只拦查询层，<b>不碰</b>真实库存、槽位数量、GUI 与同步 —— 玩家仍然只装备了一件永恒之心。
 * 属性 / tick / 装备回调由 {@link HeartFusion} 另行转发。</p>
 *
 * <p>查询用 {@link HeartFusion#activeFused}：配置里关掉「融合饰品生效」后，虚拟槽位同样不再出现。</p>
 *
 * <p>覆盖不到：直接遍历 {@code getCurios()} / {@code getStacksHandler()} 的模组
 * （它们看不到虚拟槽位）。这是有意的取舍 —— 那类查询要真正伪造库存，必须改 Curios 内部，
 * 会牵连 GUI 与同步，风险远高于收益。</p>
 */
@Mixin(targets = "top.theillusivec4.curios.common.capability.CurioInventoryCapability$CurioInventoryWrapper", remap = false)
public abstract class CurioInventoryWrapperMixin {

    @Shadow
    Map<String, ICurioStacksHandler> curios;

    @Shadow
    LivingEntity wearer;

    @Inject(
            method = "findFirstCurio(Ljava/util/function/Predicate;)Ljava/util/Optional;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false)
    private void eternalHeart$findFusedCurio(Predicate<ItemStack> filter,
                                             CallbackInfoReturnable<Optional<SlotResult>> cir) {
        Optional<SlotResult> real = cir.getReturnValue();
        if (real != null && real.isPresent()) return; // 真实装备优先

        ItemStack heart = eternalHeart$equippedHeart();
        if (heart.isEmpty()) return;

        for (HeartFusion.Fused fused : HeartFusion.activeFused(heart)) {
            ItemStack stack = fused.stack();
            if (!filter.test(stack)) continue;
            cir.setReturnValue(Optional.of(new SlotResult(
                    HeartFusion.virtualContext(wearer, fused), stack)));
            return;
        }
    }

    /** 复数查询（{@code findCurios}）：把命中的融合饰品追加到真实结果后面。 */
    @Inject(
            method = "findCurios(Ljava/util/function/Predicate;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false)
    private void eternalHeart$findFusedCurios(Predicate<ItemStack> filter,
                                              CallbackInfoReturnable<List<SlotResult>> cir) {
        ItemStack heart = eternalHeart$equippedHeart();
        if (heart.isEmpty()) return;

        List<SlotResult> virtual = null;
        for (HeartFusion.Fused fused : HeartFusion.activeFused(heart)) {
            ItemStack stack = fused.stack();
            if (!filter.test(stack)) continue;
            if (virtual == null) virtual = new ArrayList<>();
            virtual.add(new SlotResult(HeartFusion.virtualContext(wearer, fused), stack));
        }
        if (virtual == null) return;

        List<SlotResult> merged = new ArrayList<>(cir.getReturnValue());
        merged.addAll(virtual);
        cir.setReturnValue(merged);
    }

    /** 在真实槽位里找已佩戴的永恒之心（不调用 Curios 查询，避免递归回本方法）。 */
    private ItemStack eternalHeart$equippedHeart() {
        for (ICurioStacksHandler handler : curios.values()) {
            IDynamicStackHandler stacks = handler.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (stack.is(EternalHeartMod.ETERNAL_HEART.get())) return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
