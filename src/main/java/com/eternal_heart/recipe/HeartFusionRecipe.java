package com.eternal_heart.recipe;

import com.eternal_heart.EternalHeartMod;
import com.eternal_heart.features.HeartFusion;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 饰品融合配方 —— 工作台里放「永恒之心 + 任意一件或多件饰品」。
 *
 * <p>为什么用特殊配方（{@link CustomRecipe}）：无法为"任意饰品"写死 JSON 配方，
 * 必须在运行时判断物品是否带 Curios 能力。特殊配方不进配方书，但工作台里照常生效。</p>
 *
 * <p>匹配规则：格子里<b>有且只有</b>一颗永恒之心，其余格子必须全是饰品，至少一件；
 * 心上的融合数量 + 本次投入的饰品数不能超过 {@code fusionLimit}（超了整次合成不成立，
 * 不会先融几个再丢剩下的）。产物是原样复制的心（保留已有融合），再把所有投入的饰品
 * 按格子顺序依次追加；每件原饰品被合成消耗 1 个。</p>
 */
public class HeartFusionRecipe extends CustomRecipe {

    public HeartFusionRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        ItemStack heart = ItemStack.EMPTY;
        int curios = 0;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.is(EternalHeartMod.ETERNAL_HEART.get())) {
                if (!heart.isEmpty()) return false; // 两颗心：不合成
                heart = stack;
            } else if (HeartFusion.isCurio(stack)) {
                curios++;
            } else {
                return false; // 混入其它物品
            }
        }

        return !heart.isEmpty() && curios > 0 && HeartFusion.canFuse(heart, curios);
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        ItemStack heart = ItemStack.EMPTY;
        List<ItemStack> curios = new ArrayList<>();

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(EternalHeartMod.ETERNAL_HEART.get())) heart = stack;
            else if (HeartFusion.isCurio(stack)) curios.add(stack);
        }

        if (heart.isEmpty() || curios.isEmpty()) return ItemStack.EMPTY;
        // 整批校验：中途某一件失败（例如刚好撞到上限）就整次合成作废，避免半成品
        if (!HeartFusion.canFuse(heart, curios.size())) return ItemStack.EMPTY;

        ItemStack result = heart.copy();
        result.setCount(1);
        for (ItemStack curio : curios) {
            if (!HeartFusion.fuse(result, curio)) return ItemStack.EMPTY;
        }
        return result;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return new ItemStack(EternalHeartMod.ETERNAL_HEART.get());
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2; // 2×2 玩家合成格 / 3×3 工作台都可以
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return EternalHeartMod.HEART_FUSION_SERIALIZER.get();
    }
}
