package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;

import java.util.UUID;

public class AttributeFeature implements IFeature {

    private static final UUID ATTACK_DAMAGE_UUID    = UUID.fromString("e1a00001-0000-0000-0000-000000000000");
    private static final UUID ATTACK_SPEED_UUID     = UUID.fromString("e1a00002-0000-0000-0000-000000000000");
    private static final UUID MAX_HEALTH_UUID       = UUID.fromString("e1a00003-0000-0000-0000-000000000000");
    private static final UUID ARMOR_UUID            = UUID.fromString("e1a00004-0000-0000-0000-000000000000");
    private static final UUID ARMOR_TOUGHNESS_UUID  = UUID.fromString("e1a00005-0000-0000-0000-000000000000");
    private static final UUID KNOCKBACK_RES_UUID    = UUID.fromString("e1a00006-0000-0000-0000-000000000000");
    private static final UUID MOVEMENT_SPEED_UUID   = UUID.fromString("e1a00007-0000-0000-0000-000000000000");
    private static final UUID LUCK_UUID             = UUID.fromString("e1a00008-0000-0000-0000-000000000000");

    @Override
    public String getName() {
        return "AttributeFeature";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void addAttributeModifiers(SlotContext slotContext, UUID uuid, ItemStack stack, Multimap<Attribute, AttributeModifier> map) {
        map.put(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(ATTACK_DAMAGE_UUID, "Eternal Heart Attack Damage",
                        EternalHeartConfig.ATTACK_DAMAGE.get(), AttributeModifier.Operation.MULTIPLY_TOTAL));

        map.put(Attributes.ATTACK_SPEED,
                new AttributeModifier(ATTACK_SPEED_UUID, "Eternal Heart Attack Speed",
                        EternalHeartConfig.ATTACK_SPEED.get(), AttributeModifier.Operation.MULTIPLY_TOTAL));

        map.put(Attributes.MAX_HEALTH,
                new AttributeModifier(MAX_HEALTH_UUID, "Eternal Heart Max Health",
                        EternalHeartConfig.MAX_HEALTH.get(), AttributeModifier.Operation.ADDITION));

        map.put(Attributes.ARMOR,
                new AttributeModifier(ARMOR_UUID, "Eternal Heart Armor",
                        EternalHeartConfig.ARMOR.get(), AttributeModifier.Operation.ADDITION));

        map.put(Attributes.ARMOR_TOUGHNESS,
                new AttributeModifier(ARMOR_TOUGHNESS_UUID, "Eternal Heart Armor Toughness",
                        EternalHeartConfig.ARMOR_TOUGHNESS.get(), AttributeModifier.Operation.ADDITION));

        map.put(Attributes.KNOCKBACK_RESISTANCE,
                new AttributeModifier(KNOCKBACK_RES_UUID, "Eternal Heart Knockback Resist",
                        EternalHeartConfig.KNOCKBACK_RESIST.get(), AttributeModifier.Operation.ADDITION));

        map.put(Attributes.MOVEMENT_SPEED,
                new AttributeModifier(MOVEMENT_SPEED_UUID, "Eternal Heart Movement Speed",
                        EternalHeartConfig.MOVEMENT_SPEED.get(), AttributeModifier.Operation.MULTIPLY_TOTAL));

        map.put(Attributes.LUCK,
                new AttributeModifier(LUCK_UUID, "Eternal Heart Luck",
                        EternalHeartConfig.LUCK.get(), AttributeModifier.Operation.ADDITION));
    }
}
