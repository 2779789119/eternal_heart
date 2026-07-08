package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;

import java.util.List;
import java.util.Set;

public class SurvivalSystem implements IFeature {

    private static final String UNDYING_COOLDOWN_KEY = "eternal_heart_undying_cd";
    private static final String GHOST_ARROW_TAG = "eternal_heart_ghost";

    private static final Set<MobEffect> PRIORITY_EFFECTS = Set.of(
            MobEffects.HEAL, MobEffects.REGENERATION,
            MobEffects.HEALTH_BOOST, MobEffects.DAMAGE_RESISTANCE);

    @Override
    public String getName() {
        return "SurvivalSystem";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void onPlayerTick(Player player, LivingTickEvent event) {
        long tick = player.level().getGameTime();

        applyTickRegen(player);
        applyPassiveEffects(player, tick);
        applyVoidRescue(player);
        applyAutoPotion(player, tick);
        applyInfiniteArrows(player);
    }

    @Override
    public void onPlayerDeath(Player player, LivingDeathEvent event) {
        long now = player.level().getGameTime();

        if (EternalHeartConfig.UNDYING_TOTEM.get()) {
            long cooldown = player.getPersistentData().getLong(UNDYING_COOLDOWN_KEY);
            if (now >= cooldown) {
                event.setCanceled(true);
                player.setHealth(player.getMaxHealth() * 0.5f);
                player.clearFire();
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 2));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 2));
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 4));
                player.getPersistentData().putLong(UNDYING_COOLDOWN_KEY,
                        now + EternalHeartConfig.UNDYING_COOLDOWN.get() * 20L);
                return;
            }
        }

        if (EternalHeartConfig.SOUL_BIND.get()) {
            event.setCanceled(true);
            player.setHealth(1f);
            player.clearFire();
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 3));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 2));

            if (player instanceof ServerPlayer sp) {
                BlockPos respawn = sp.getRespawnPosition();
                if (respawn != null) {
                    sp.teleportTo(respawn.getX() + 0.5, respawn.getY(), respawn.getZ() + 0.5);
                    return;
                }
            }
            BlockPos worldSpawn = player.level().getSharedSpawnPos();
            player.teleportTo(worldSpawn.getX() + 0.5, worldSpawn.getY(), worldSpawn.getZ() + 0.5);
        }
    }

    private void applyTickRegen(Player player) {
        float regen = EternalHeartConfig.TICK_REGEN.get().floatValue();
        if (regen <= 0) return;
        if (player.getHealth() < player.getMaxHealth() && player.getHealth() > 0) {
            player.heal(regen);
        }
    }

    private void applyPassiveEffects(Player player, long tick) {
        if (tick % 100 != 0) return;
        int duration = 1200;

        if (EternalHeartConfig.NIGHT_VISION.get())
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, duration, 0, false, false, true));
        if (EternalHeartConfig.WATER_BREATHING.get())
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, duration, 0, false, false, true));
        int haste = EternalHeartConfig.HASTE_LEVEL.get();
        if (haste > 0)
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, duration, haste - 1, false, false, true));
        if (EternalHeartConfig.FIRE_RESISTANCE_POTION.get())
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, duration, 0, false, false, true));
        if (EternalHeartConfig.SATURATION.get())
            player.addEffect(new MobEffectInstance(MobEffects.SATURATION, duration, 0, false, false, true));
        if (EternalHeartConfig.PERMANENT_LUCK.get())
            player.addEffect(new MobEffectInstance(MobEffects.LUCK, duration, 0, false, false, true));
    }

    private void applyVoidRescue(Player player) {
        if (!EternalHeartConfig.VOID_RESCUE.get()) return;

        Level level = player.level();
        int minY = level.getMinBuildHeight();
        if (player.position().y > minY - 4) return;

        int topY = level.getMaxBuildHeight();
        BlockPos safePos = new BlockPos((int) player.getX(), topY, (int) player.getZ());
        player.teleportTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);
        player.fallDistance = 0;
    }

    private void applyAutoPotion(Player player, long tick) {
        if (!EternalHeartConfig.AUTO_POTION.get()) return;
        if (tick % 20 != 0) return;
        double threshold = EternalHeartConfig.AUTO_POTION_THRESHOLD.get();
        if (player.getHealth() / player.getMaxHealth() > threshold) return;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!(stack.getItem() instanceof PotionItem)) continue;

            List<MobEffectInstance> effects = PotionUtils.getMobEffects(stack);
            for (MobEffectInstance eff : effects) {
                if (PRIORITY_EFFECTS.contains(eff.getEffect())) {
                    drinkPotion(player, i, stack, effects);
                    return;
                }
            }
        }

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof PotionItem) {
                List<MobEffectInstance> effects = PotionUtils.getMobEffects(stack);
                drinkPotion(player, i, stack, effects);
                return;
            }
        }
    }

    private void drinkPotion(Player player, int slot, ItemStack stack, List<MobEffectInstance> effects) {
        for (MobEffectInstance e : effects) {
            player.addEffect(new MobEffectInstance(e));
        }
        stack.shrink(1);
        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
        if (stack.isEmpty()) {
            player.getInventory().setItem(slot, bottle);
        } else if (!player.getInventory().add(bottle)) {
            player.level().addFreshEntity(new ItemEntity(player.level(),
                    player.getX(), player.getY() + 1, player.getZ(), bottle));
        }
    }

    private void applyInfiniteArrows(Player player) {
        if (!EternalHeartConfig.INFINITE_ARROWS.get()) return;
        if (player.getAbilities().instabuild) return;

        ItemStack mainHand = player.getMainHandItem();
        boolean holdsCrossbow = mainHand.is(Items.CROSSBOW);

        if (!holdsCrossbow) {
            sweepGhostArrows(player);
            return;
        }

        if (player.getProjectile(mainHand).isEmpty()) {
            ensureGhostArrow(player);
        }
    }

    private void ensureGhostArrow(Player player) {
        for (int i = 0; i < 36; i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot.isEmpty()) {
                ItemStack ghost = new ItemStack(Items.ARROW);
                ghost.getOrCreateTag().putBoolean(GHOST_ARROW_TAG, true);
                player.getInventory().setItem(i, ghost);
                return;
            }
        }
    }

    private void sweepGhostArrows(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isGhostArrow(player.getInventory().getItem(i))) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private boolean isGhostArrow(ItemStack stack) {
        return stack.is(Items.ARROW)
                && stack.hasTag()
                && stack.getTag().getBoolean(GHOST_ARROW_TAG);
    }
}
