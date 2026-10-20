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

/**
 * 饰品融合配方 —— 工作台里放「永恒之心 + 任意一件饰品」。
 *
 * <p>为什么用特殊配方（{@link CustomRecipe}）：无法为"任意饰品"写死 JSON 配方，
 * 必须在运行时判断物品是否带 Curios 能力。特殊配方不进配方书，但工作台里照常生效。</p>
 *
 * <p>匹配规则：格子里<b>有且只有</b>一颗永恒之心 + 一件饰品，其余必须为空；
 * 心上的融合数量不能超过 {@code fusionLimit}。产物是原样复制的心（保留已有融合），
 * 再把新饰品追加进去；原饰品被合成消耗。</p>
 */
public class HeartFusionRecipe extends CustomRecipe {

    public HeartFusionRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        ItemStack heart = ItemStack.EMPTY;
        ItemStack curio = ItemStack.EMPTY;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.is(EternalHeartMod.ETERNAL_HEART.get())) {
                if (!heart.isEmpty()) return false; // 两颗心：不合成
                heart = stack;
            } else if (HeartFusion.isCurio(stack)) {
                if (!curio.isEmpty()) return false; // 两件饰品：不合成
                curio = stack;
            } else {
                return false; // 混入其它物品
            }
        }

        return !heart.isEmpty() && !curio.isEmpty() && HeartFusion.canFuse(heart);
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        ItemStack heart = ItemStack.EMPTY;
        ItemStack curio = ItemStack.EMPTY;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(EternalHeartMod.ETERNAL_HEART.get())) heart = stack;
            else if (HeartFusion.isCurio(stack)) curio = stack;
        }

        if (heart.isEmpty() || curio.isEmpty()) return ItemStack.EMPTY;

        ItemStack result = heart.copy();
        result.setCount(1);
        return HeartFusion.fuse(result, curio) ? result : ItemStack.EMPTY;
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
