package com.eternal_heart;

import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.core.Debuffs;
import com.eternal_heart.features.*;
import com.eternal_heart.integration.Integrations;
import com.eternal_heart.network.ConfigNetwork;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

@GameTestHolder(EternalHeartMod.MODID)
@PrefixGameTestTemplate(false)
public final class RepairGameTests {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void close(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 0.0001, message + ": " + actual + " != " + expected);
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid input was accepted");
    }

    private static void withConfig(Map<String, Object> patch, Runnable action) {
        Map<String, Object> saved = ConfigValues.snapshot();
        try { ConfigValues.apply(patch); action.run(); }
        finally { ConfigValues.apply(saved); }
    }

    @GameTest(template = "empty")
    public static void refillConserves65Items(GameTestHelper helper) {
        var inv = helper.makeMockPlayer().getInventory();
        inv.clearContent();
        inv.setItem(0, new ItemStack(Items.COBBLESTONE, 1));
        inv.setItem(9, new ItemStack(Items.COBBLESTONE, 64));
        UtilitySystem.refillHotbar(inv);
        check(inv.getItem(0).getCount() == 64 && inv.getItem(9).getCount() == 1, "1 + 64 must become 64 + 1");
        UtilitySystem.refillHotbar(inv);
        check(inv.countItem(Items.COBBLESTONE) == 65, "Repeated refills must conserve items");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void refillPreservesNbtAndMultipleSources(GameTestHelper helper) {
        var inv = helper.makeMockPlayer().getInventory();
        inv.clearContent();
        ItemStack target = new ItemStack(Items.COBBLESTONE);
        target.getOrCreateTag().putString("variant", "a");
        ItemStack other = new ItemStack(Items.COBBLESTONE, 64);
        other.getOrCreateTag().putString("variant", "b");
        inv.setItem(0, target);
        inv.setItem(9, other);
        inv.setItem(10, target.copyWithCount(20));
        inv.setItem(11, target.copyWithCount(50));
        UtilitySystem.refillHotbar(inv);
        check(inv.getItem(0).getCount() == 64 && inv.getItem(11).getCount() == 7, "Must combine compatible sources only");
        check(inv.getItem(9).getCount() == 64 && inv.getItem(9).getTag().getString("variant").equals("b"), "Different NBT changed");
        check(inv.countItem(Items.COBBLESTONE) == 135, "NBT refill lost items");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void refillRespects16ItemLimit(GameTestHelper helper) {
        var inv = helper.makeMockPlayer().getInventory();
        inv.clearContent();
        inv.setItem(0, new ItemStack(Items.ENDER_PEARL));
        inv.setItem(9, new ItemStack(Items.ENDER_PEARL, 16));
        inv.setItem(1, new ItemStack(Items.DIAMOND_PICKAXE));
        inv.setItem(10, new ItemStack(Items.DIAMOND_PICKAXE));
        UtilitySystem.refillHotbar(inv);
        check(inv.getItem(0).getCount() == 16 && inv.getItem(9).getCount() == 1, "Pearl stack overflow or loss");
        check(inv.getItem(1).getCount() == 1 && inv.getItem(10).getCount() == 1, "Unstackable item moved");
        check(inv.getItem(2).isEmpty(), "Empty hotbar slot must not guess an item");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullFuryReducesBeforeFinalDamage(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        withConfig(Map.of("DODGE_CHANCE", 0.0, "DAMAGE_REDUCTION", 0.5, "DAMAGE_CAP_RATIO", 0.0), () -> {
            FurySystem.setFuryStacks(player, ConfigValues.get(EternalHeartConfig.FURY_MAX_STACKS));
            LivingHurtEvent hurt = new LivingHurtEvent(player, player.damageSources().generic(), 20);
            FeatureManager.dispatchPlayerHurt(player, hurt);
            close(hurt.getAmount(), 9.5, "Full Fury reduction missing");
            check(FurySystem.isFuryMaxed(player), "Fury cleared before damage resolution");
            FurySystem.onDamageResolved(player, new LivingDamageEvent(player, player.damageSources().generic(), 0));
            check(FurySystem.isFuryMaxed(player), "Fully absorbed damage cleared Fury");
            LivingDamageEvent canceled = new LivingDamageEvent(player, player.damageSources().generic(), 5);
            canceled.setCanceled(true);
            FurySystem.onDamageResolved(player, canceled);
            check(FurySystem.isFuryMaxed(player), "Canceled damage cleared Fury");
            FurySystem.onDamageResolved(player, new LivingDamageEvent(player, player.damageSources().generic(), 1));
            check(FurySystem.getFuryStacks(player) == 0, "Positive health damage did not clear Fury");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dodgeAndImmunityPreserveFury(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        withConfig(Map.of("DODGE_CHANCE", 1.0, "FIRE_IMMUNE", true), () -> {
            FurySystem.setFuryStacks(player, 5);
            LivingHurtEvent fire = new LivingHurtEvent(player, player.damageSources().onFire(), 10);
            FeatureManager.dispatchPlayerHurt(player, fire);
            check(fire.isCanceled() && FurySystem.getFuryStacks(player) == 5, "Immunity cleared Fury");
            LivingHurtEvent hit = new LivingHurtEvent(player, player.damageSources().generic(), 10);
            FeatureManager.dispatchPlayerHurt(player, hit);
            check(hit.isCanceled() && FurySystem.getFuryStacks(player) == 5, "Dodge cleared Fury");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unitsAndInvalidPackets(GameTestHelper helper) {
        close(ConfigValues.toDisplay("DEBUFF_CLEAR_INTERVAL", 60), 3, "Ticks to seconds");
        close(ConfigValues.fromDisplay("DEBUFF_CLEAR_INTERVAL", 3).intValue(), 60, "Seconds to ticks");
        close(ConfigValues.toDisplay("TICK_REGEN", 2), 40, "HP/tick to HP/s");
        close(ConfigValues.fromDisplay("TICK_REGEN", 40).doubleValue(), 2, "HP/s to HP/tick");
        close(ConfigValues.fromDisplay("DAMAGE_REDUCTION", 50).doubleValue(), 0.5, "Percent to ratio");
        close(ConfigValues.fromDisplay("ATTACK_DAMAGE", 2.5).doubleValue(), 2.5, "Total multiplier");
        // 配置上下限已全面放开（可填负数/超大值）：1.1、0 这类数值现在是合法值，
        // 这里改用「类型 / 精度」非法的样例来验证拒绝路径
        rejects(() -> ConfigValues.decode("{\"DODGE_CHANCE\":\"x\"}"));
        rejects(() -> ConfigValues.decode("{\"DODGE_CHANCE\":1e309}"));
        rejects(() -> ConfigValues.decode("{\"FURY_MAX_STACKS\":1.5}"));
        rejects(() -> ConfigValues.decode("{\"AUTO_FISH_INTERVAL\":1.5}"));
        rejects(() -> ConfigValues.decode("{\"UNKNOWN\":true}"));
        rejects(() -> ConfigValues.fromDisplay("TICK_REGEN", Double.NaN));
        double previous = ConfigValues.get(EternalHeartConfig.ATTACK_DAMAGE);
        rejects(() -> ConfigValues.apply(Map.of("ATTACK_DAMAGE", 7.0, "FURY_MAX_STACKS", 1.5)));
        close(ConfigValues.get(EternalHeartConfig.ATTACK_DAMAGE), previous, "Invalid batch partially applied");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void attackMultipliersMatchDescription(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        double base = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        withConfig(Map.of("ATTACK_DAMAGE", 2.5), () -> {
            AttributeFeature.refresh(player);
            close(player.getAttributeValue(Attributes.ATTACK_DAMAGE), base * 2.5, "2.5 should multiply by 2.5");
            AttributeFeature.refresh(player);
            close(player.getAttributeValue(Attributes.ATTACK_DAMAGE), base * 2.5, "Refresh duplicated attributes");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void externalEffectsSurviveUnequip(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 200, 0, true, true, true));
        OwnedEffects.maintain(player, MobEffects.NIGHT_VISION, true, 1200, 0, 600);
        OwnedEffects.releaseAll(player);
        check(player.getEffect(MobEffects.NIGHT_VISION).getDuration() == 200, "Original potion duration lost");
        check(player.getEffect(MobEffects.NIGHT_VISION).isAmbient(), "Original potion flags lost");
        OwnedEffects.maintain(player, MobEffects.NIGHT_VISION, true, 1200, 0, 600);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0));
        OwnedEffects.releaseAll(player);
        check(player.getEffect(MobEffects.NIGHT_VISION).getDuration() == 400, "Potion applied while equipped lost");
        player.removeEffect(MobEffects.NIGHT_VISION);
        OwnedEffects.maintain(player, MobEffects.NIGHT_VISION, true, 1200, 0, 600);
        OwnedEffects.releaseAll(player);
        check(!player.hasEffect(MobEffects.NIGHT_VISION), "Owned-only potion leaked");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void milkDoesNotResurrectEffects(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 200, 0));
        OwnedEffects.maintain(player, MobEffects.NIGHT_VISION, true, 1200, 0, 600);
        player.removeAllEffects();
        OwnedEffects.releaseAll(player);
        check(!player.hasEffect(MobEffects.NIGHT_VISION), "Milk-cleared potion resurrected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shieldKeepsOutsideContribution(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.setAbsorptionAmount(8);
        OwnedShield.charge(player, 4, 20);
        close(player.getAbsorptionAmount(), 12, "Shield did not charge");
        player.setAbsorptionAmount(10); // spend 2 of our 4
        OwnedShield.reconcile(player);
        player.setAbsorptionAmount(16); // another source adds 6
        OwnedShield.release(player);
        close(player.getAbsorptionAmount(), 14, "Unequip removed outside absorption");
        OwnedShield.release(player);
        close(player.getAbsorptionAmount(), 14, "Repeated unequip removed absorption");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeFlightSurvivesUnequip(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.getPersistentData().putBoolean("eternal_heart_creative_flight", true);
        new MobilitySystem().onUnequip(player);
        check(player.getAbilities().mayfly && player.getAbilities().flying, "Creative flight removed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nonOperatorCannotEdit(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ConfigDenied"));
        check(!ConfigNetwork.canEdit(player), "Unauthorized player can edit server config");
        if (!Integrations.isFtbUltimineLoaded()) check(!Integrations.activateUltimine(player), "Absent FTB reports success");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lootProcessingKeepsOriginalQuantity(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        withConfig(Map.of("AUTO_SMELT", true, "FORTUNE_BONUS", 0), () -> {
            var drops = UtilitySystem.processDrops(player, Blocks.IRON_ORE.defaultBlockState(),
                    java.util.List.of(new ItemStack(Items.RAW_IRON, 3)));
            check(drops.size() == 1 && drops.get(0).is(Items.IRON_INGOT) && drops.get(0).getCount() == 3,
                    "Smelting lost or duplicated the original loot quantity");
            var empty = UtilitySystem.processDrops(player, Blocks.IRON_ORE.defaultBlockState(), java.util.List.of());
            check(empty.isEmpty(), "Unharvestable blocks gained loot");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void debuffRulesCompileAndHonorLists(GameTestHelper helper) {
        // 默认分类判定与来源
        check(Debuffs.blocked(MobEffects.POISON), "HARMFUL effect must be blocked");
        check(!Debuffs.blocked(MobEffects.NIGHT_VISION), "BENEFICIAL effect must be allowed");
        check(Debuffs.source(MobEffects.POISON) == Debuffs.Source.HARMFUL, "Wrong source for poison");

        // 白名单优先于分类
        withConfig(Map.of("DEBUFF_WHITELIST", java.util.List.of("minecraft:poison")), () -> {
            Debuffs.invalidate();
            check(!Debuffs.blocked(MobEffects.POISON), "Whitelist must protect");
            check(Debuffs.source(MobEffects.POISON) == Debuffs.Source.WHITELIST, "Whitelist source missing");
        });
        // 配置恢复后规则集自动重建（这里不显式 invalidate，走引用检测路径）
        check(Debuffs.blocked(MobEffects.POISON), "Rules did not rebuild after restore");

        // 黑名单强制清除（即使标注为有益）
        withConfig(Map.of("DEBUFF_BLACKLIST", java.util.List.of("minecraft:night_vision")), () -> {
            Debuffs.invalidate();
            check(Debuffs.blocked(MobEffects.NIGHT_VISION), "Blacklist must force-block");
            check(Debuffs.source(MobEffects.NIGHT_VISION) == Debuffs.Source.BLACKLIST, "Blacklist source missing");
        });
        check(!Debuffs.blocked(MobEffects.NIGHT_VISION), "Rules did not rebuild after restore");

        // 黑名单对永恒之心维持的被动效果同样生效（maintain 不再拉锯）
        Player player = helper.makeMockPlayer();
        withConfig(Map.of("DEBUFF_BLACKLIST", java.util.List.of("minecraft:night_vision")), () -> {
            Debuffs.invalidate();
            OwnedEffects.maintain(player, MobEffects.NIGHT_VISION, true, 1200, 0, 600);
            check(!player.hasEffect(MobEffects.NIGHT_VISION), "Blacklisted passive effect must not be maintained");
        });
        check(!player.hasEffect(MobEffects.NIGHT_VISION), "Effect leaked after restore");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void remoteSnapshotDoesNotChangeLocalConfig(GameTestHelper helper) {
        double local = EternalHeartConfig.ATTACK_DAMAGE.get();
        try {
            ConfigValues.setRemote(Map.of("ATTACK_DAMAGE", 4.0));
            close(ConfigValues.get(EternalHeartConfig.ATTACK_DAMAGE), 4, "Remote value not used");
            close(EternalHeartConfig.ATTACK_DAMAGE.get(), local, "Remote snapshot changed local ConfigValue");
        } finally {
            ConfigValues.clearRemote();
        }
        close(ConfigValues.get(EternalHeartConfig.ATTACK_DAMAGE), local, "Disconnect did not restore local value");
        helper.succeed();
    }
}
