package com.eternal_heart.loot;

import com.eternal_heart.features.EquipTracker;
import com.eternal_heart.features.UtilitySystem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

/** Shared by vanilla, built-in vein mining and successful third-party player breaks. */
public final class HeartBlockLootModifier extends LootModifier {
    public static final Codec<HeartBlockLootModifier> CODEC = RecordCodecBuilder.create(
            instance -> codecStart(instance).apply(instance, HeartBlockLootModifier::new));

    public HeartBlockLootModifier(LootItemCondition[] conditions) { super(conditions); }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> drops, LootContext context) {
        var state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        var entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (state == null || !(entity instanceof Player player) || player.isCreative() || !EquipTracker.isEquipped(player)) return drops;
        return new ObjectArrayList<>(UtilitySystem.processDrops(player, state, drops));
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() { return CODEC; }
}
