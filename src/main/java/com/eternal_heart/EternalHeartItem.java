package com.eternal_heart;

import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.core.Numbers;
import com.eternal_heart.features.CustomAttributes;
import com.eternal_heart.features.FeatureManager;
import com.eternal_heart.integration.Integrations;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class EternalHeartItem extends Item implements ICurioItem {
    public EternalHeartItem(Properties properties) { super(properties); }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext context, UUID uuid, ItemStack stack) {
        return FeatureManager.buildAttributeModifiers(context, uuid, stack);
    }

    @Override
    public boolean canEquipFromUse(SlotContext context, ItemStack stack) { return true; }

    /**
     * 无视绑定：永恒之心永远允许装备与取下。
     *
     * <p>与 {@code CurioGuard} 配合：这里声明「物品自身不设限」，
     * CurioGuard 负责清除外在的绑定来源（绑定诅咒附魔等）并提供强制取下兜底。</p>
     */
    @Override
    public boolean canEquip(SlotContext context, ItemStack stack) { return true; }

    @Override
    public boolean canUnequip(SlotContext context, ItemStack stack) { return true; }

    private static MutableComponent text(String key, Object... args) {
        return Component.translatable("tooltip.eternal_heart." + key, args);
    }

    private static String number(double value) {
        return Numbers.format(value);
    }

    private static Object setting(String key) { return ConfigValues.get(ConfigValues.entries().get(key)); }
    private static String value(String key) { return number(((Number) setting(key)).doubleValue()); }
    private static String percent(String key) { return number(((Number) setting(key)).doubleValue() * 100); }
    private static boolean on(String key) { return (Boolean) setting(key); }
    private static boolean positive(String key) { return ((Number) setting(key)).doubleValue() > 0; }

    /** 总倍率类配置：不等于 1.0 才算改动过（用于扩展属性的"有别于默认"显示）。 */
    private static boolean gained(String key) { return ((Number) setting(key)).doubleValue() != 1.0; }

    /** 自定义属性配置（原始文本列表，可能为空）。 */
    private static List<String> customAttributes() {
        List<String> result = new ArrayList<>();
        if (setting("EXTRA_ATTRIBUTES") instanceof List<?> list) {
            for (Object element : list) result.add(String.valueOf(element));
        }
        return result;
    }

    private static void flags(List<Component> parts, String... keys) {
        for (String key : keys) if (on(key))
            parts.add(Component.translatable("config.eternal_heart.field." + key.toLowerCase(Locale.ROOT)));
    }

    private static void range(List<Component> parts, String key, String config) {
        if (positive(config)) parts.add(text(key, value(config)));
    }

    private static void row(List<Component> tooltip, ChatFormatting color, List<Component> parts) {
        if (parts.isEmpty()) return;
        MutableComponent joined = Component.empty();
        for (Component part : parts) {
            if (!joined.getSiblings().isEmpty()) joined.append("  |  ");
            joined.append(part);
        }
        tooltip.add(joined.withStyle(color));
        parts.clear();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(text("flavor").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.empty());
        tooltip.add(text("crit1").withStyle(ChatFormatting.GOLD));
        tooltip.add(text("crit2", value("FURY_MAX_STACKS"), percent("FURY_DAMAGE_PER_STACK"), percent("FURY_LIFESTEAL")).withStyle(ChatFormatting.GOLD));
        tooltip.add(text("crit3").withStyle(ChatFormatting.GOLD));
        tooltip.add(text("crit4").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.empty());
        tooltip.add(text("more").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(text("attack", value("ATTACK_DAMAGE"), value("ATTACK_SPEED")).withStyle(ChatFormatting.RED));
        tooltip.add(text("hp", value("MAX_HEALTH"), value("ARMOR"), value("ARMOR_TOUGHNESS")).withStyle(ChatFormatting.RED));
        tooltip.add(text(Integrations.isAttributeFixLoaded() ? "health_cap_modded" : "health_cap_vanilla").withStyle(ChatFormatting.GRAY));

        // 扩展属性（仅在非默认时才显示，避免默认配置下刷屏）
        List<Component> extended = new ArrayList<>();
        if (positive("ATTACK_KNOCKBACK")) extended.add(text("hit_knockback", value("ATTACK_KNOCKBACK")));
        if (positive("ENTITY_REACH")) extended.add(text("reach_entity", value("ENTITY_REACH")));
        if (positive("BLOCK_REACH")) extended.add(text("reach_block", value("BLOCK_REACH")));
        if (positive("STEP_HEIGHT")) extended.add(text("step_up", value("STEP_HEIGHT")));
        if (positive("NAMETAG_DISTANCE")) extended.add(text("nametag", value("NAMETAG_DISTANCE")));
        if (gained("SWIM_SPEED")) extended.add(text("swim", value("SWIM_SPEED")));
        if (gained("FLYING_SPEED")) extended.add(text("fly", value("FLYING_SPEED")));
        row(tooltip, ChatFormatting.BLUE, extended);

        // 自定义属性（任意模组属性）：最多列 3 条，其余折叠显示
        List<String> custom = customAttributes();
        if (!custom.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            int shown = Math.min(custom.size(), 3);
            for (int i = 0; i < shown; i++) {
                CustomAttributes.Spec spec = CustomAttributes.parseLine(custom.get(i));
                if (spec == null) continue;
                lines.add(text("custom_attr",
                        Component.translatable(CustomAttributes.displayNameKey(spec.attribute())),
                        number(spec.value()), CustomAttributes.operationName(spec.operation())));
            }
            if (custom.size() > shown) lines.add(text("custom_attr_more", custom.size() - shown));
            row(tooltip, ChatFormatting.BLUE, lines);
        }

        List<Component> parts = new ArrayList<>();
        if (((Number) setting("KNOCKBACK_RESIST")).doubleValue() >= 1 || on("KNOCKBACK_IMMUNITY")) parts.add(text("knockback"));
        parts.add(text("reduction", percent("DAMAGE_REDUCTION")));
        if (positive("DAMAGE_CAP_RATIO")) parts.add(text("cap", percent("DAMAGE_CAP_RATIO")));
        row(tooltip, ChatFormatting.GRAY, parts);
        tooltip.add(text("speed", percent("MOVEMENT_SPEED"), value("LUCK")).withStyle(ChatFormatting.AQUA));
        tooltip.add(text("regen", number(((Number) setting("TICK_REGEN")).doubleValue() * 20)).withStyle(ChatFormatting.GREEN));
        if (positive("DEBUFF_CLEAR_INTERVAL"))
            tooltip.add(text("debuff", number(((Number) setting("DEBUFF_CLEAR_INTERVAL")).doubleValue() / 20)).withStyle(ChatFormatting.YELLOW));
        flags(parts, "FIRE_IMMUNE", "DROWN_IMMUNE", "SUFFOCATE_IMMUNE", "CACTUS_IMMUNE");
        parts.add(text("fall", percent("FALL_DAMAGE_RATIO")));
        parts.add(text("reflect", number((((Number) setting("REFLECT_RATIO")).doubleValue() + ((Number) setting("COUNTER_DAMAGE_RATIO")).doubleValue()) * 100)));
        parts.add(text("explosion", percent("EXPLOSION_RESIST")));
        row(tooltip, ChatFormatting.LIGHT_PURPLE, parts);
        flags(parts, "NIGHT_VISION", "WATER_BREATHING", "FIRE_RESISTANCE_POTION", "SATURATION", "PERMANENT_LUCK");
        if (positive("HASTE_LEVEL")) parts.add(text("haste", value("HASTE_LEVEL")));
        row(tooltip, ChatFormatting.AQUA, parts);
        parts.add(text("dodge", percent("DODGE_CHANCE")));
        parts.add(text("projectiles", percent("REFLECT_PROJECTILE_CHANCE")));
        if (positive("LOW_HP_THRESHOLD")) parts.add(text("berserk", percent("LOW_HP_THRESHOLD"), value("LOW_HP_DAMAGE_MULT")));
        if (positive("BOSS_DAMAGE_BONUS")) parts.add(text("boss", percent("BOSS_DAMAGE_BONUS")));
        if (positive("EXECUTE_THRESHOLD")) parts.add(text("execute", percent("EXECUTE_THRESHOLD")));
        if (positive("IGNITE_CHANCE")) parts.add(text("ignite", percent("IGNITE_CHANCE"), value("IGNITE_DURATION")));
        if (on("PIERCE_DAMAGE_CAP")) parts.add(text("pierce"));
        row(tooltip, ChatFormatting.RED, parts);
        if (positive("SHIELD_PER_SEC")) parts.add(text("shield", value("SHIELD_PER_SEC"), value("SHIELD_MAX")));
        if (positive("SPEED_BURST_DURATION")) parts.add(text("burst", number(((Number) setting("SPEED_BURST_DURATION")).doubleValue() / 20)));
        if (gained("COOLDOWN_RATIO")) parts.add(text("cooldown", percent("COOLDOWN_RATIO")));
        if (on("TIME_STOP")) parts.add(text("timestop", value("TIME_STOP_DURATION")));
        row(tooltip, ChatFormatting.YELLOW, parts);
        flags(parts, "AUTO_SMELT");
        if (on("VEIN_MINER")) parts.add(text("vein", value("VEIN_MAX_BLOCKS")));
        if (positive("FORTUNE_BONUS")) parts.add(text("fortune", value("FORTUNE_BONUS")));
        if (positive("LOOTING_BONUS")) parts.add(text("looting", value("LOOTING_BONUS")));
        row(tooltip, ChatFormatting.YELLOW, parts);
        if (on("UNDYING_TOTEM")) parts.add(text("undying", value("UNDYING_COOLDOWN")));
        flags(parts, "VOID_RESCUE", "KILL_EXPLOSION", "INFINITE_ARROWS", "ENDER_CHEST_REMOTE");
        if (on("SOUL_BIND")) parts.add(text("soul"));
        if (positive("KILL_HEAL_RATIO")) parts.add(text("kill_heal", percent("KILL_HEAL_RATIO")));
        row(tooltip, ChatFormatting.GREEN, parts);
        if (on("HEALTH_GUARD")) parts.add(text("guard_health"));
        if (on("MAX_HEALTH_GUARD")) parts.add(text("guard_max_health"));
        if (on("LETHAL_GUARD")) parts.add(text("guard_lethal", percent("LETHAL_GUARD_RATIO")));
        if (on("KEEP_INVENTORY") || on("KEEP_EXPERIENCE")) parts.add(text("guard_keep"));
        if (on("ITEM_GUARD")) parts.add(text("guard_item"));
        if (on("CURIO_KEEP_ON_DEATH")) parts.add(text("curio_keep"));
        if (on("CURIO_UNBIND")) parts.add(text("curio_unbind"));
        if (on("CURIO_SEIZE_GUARD")) parts.add(text("curio_seize"));
        row(tooltip, ChatFormatting.GOLD, parts);
        range(parts, "magnet", "MAGNET_RANGE");
        range(parts, "glow", "GLOW_RANGE");
        flags(parts, "LAVA_WALKING");
        if (on("WATER_WALKING")) parts.add(text("water"));
        row(tooltip, ChatFormatting.LIGHT_PURPLE, parts);
        String mode = setting("FLIGHT_MODE").toString();
        if (!mode.equals("OFF")) parts.add(Component.translatable("config.eternal_heart.flight." + mode.toLowerCase(Locale.ROOT)));
        flags(parts, "JUMP_BOOST", "STEP_ASSIST", "SLOW_FALL_GLIDE", "DOLPHINS_GRACE");
        row(tooltip, ChatFormatting.AQUA, parts);
        if (on("DAMAGE_AURA_ENABLED")) range(parts, "damage_aura", "DAMAGE_AURA_RANGE");
        if (on("WITHER_AURA_ENABLED")) range(parts, "wither", "WITHER_AURA_RANGE");
        if (on("PEACE_AURA_ENABLED")) range(parts, "peace", "PEACE_AURA_RANGE");
        if (on("GROWTH_AURA_ENABLED")) range(parts, "growth", "GROWTH_AURA_RANGE");
        row(tooltip, ChatFormatting.DARK_PURPLE, parts);
        if (on("AUTO_POTION")) parts.add(text("potion", percent("AUTO_POTION_THRESHOLD")));
        if (on("LIGHTNING_REFLECT")) parts.add(text("lightning", percent("LIGHTNING_REFLECT_CHANCE")));
        if (on("KILL_CHAIN")) parts.add(text("chain", value("KILL_CHAIN_RANGE")));
        row(tooltip, ChatFormatting.DARK_RED, parts);
        if (on("AUTO_TORCH")) parts.add(text("torch", value("AUTO_TORCH_LIGHT_LEVEL")));
        flags(parts, "AUTO_DOOR");
        if (on("ORE_HIGHLIGHT")) parts.add(text("cave", value("ORE_HIGHLIGHT_RANGE")));
        if (on("AUTO_FISH")) parts.add(text("fish"));
        if (on("AUTO_REFILL")) parts.add(text("refill"));
        if (on("INFINITE_BUCKET")) {
            parts.add(text("water_bucket"));
            if (on("INFINITE_BUCKET_LAVA")) parts.add(text("lava_bucket"));
        }
        row(tooltip, ChatFormatting.AQUA, parts);
        tooltip.add(Component.empty());
        tooltip.add(text("extras").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        tooltip.add(text("xp", value("XP_MULTIPLIER")).withStyle(ChatFormatting.GREEN));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public boolean isFoil(ItemStack stack) { return true; }
}
