package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;

public class FurySystem implements IFeature {

    public static final String FURY_STACKS_KEY = "eternal_heart_fury_stacks";
    public static final String LAST_HIT_TIME_KEY = "eternal_heart_last_hit";

    public static int getFuryStacks(Player player) {
        return player.getPersistentData().getInt(FURY_STACKS_KEY);
    }

    public static void setFuryStacks(Player player, int stacks) {
        int clamped = Math.max(0, Math.min(stacks, EternalHeartConfig.FURY_MAX_STACKS.get()));
        player.getPersistentData().putInt(FURY_STACKS_KEY, clamped);
        player.getPersistentData().putLong(LAST_HIT_TIME_KEY, player.level().getGameTime());
    }

    public static boolean isFuryMaxed(Player player) {
        return getFuryStacks(player) >= EternalHeartConfig.FURY_MAX_STACKS.get();
    }

    @Override
    public String getName() {
        return "FurySystem";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void onPlayerTick(Player player, LivingTickEvent event) {
        long tick = player.level().getGameTime();
        long lastHit = player.getPersistentData().getLong(LAST_HIT_TIME_KEY);
        if (lastHit > 0 && tick - lastHit > EternalHeartConfig.FURY_DECAY_TICKS.get()) {
            int current = getFuryStacks(player);
            if (current > 0) {
                setFuryStacks(player, current - 1);
            }
        }
    }

    @Override
    public void onPlayerAttack(Player player, LivingAttackEvent event) {
        int current = getFuryStacks(player);
        setFuryStacks(player, current + 1);

        if (isFuryMaxed(player)) {
            float lifestealAmount = event.getAmount() * EternalHeartConfig.FURY_LIFESTEAL.get().floatValue();
            if (lifestealAmount > 0) {
                player.heal(lifestealAmount);

                float foodRestore = EternalHeartConfig.LIFESTEAL_FOOD_RESTORE.get().floatValue();
                float satRestore = EternalHeartConfig.LIFESTEAL_SATURATION_RESTORE.get().floatValue();
                if (foodRestore > 0 || satRestore > 0) {
                    var food = player.getFoodData();
                    int newFood = Math.min(20, food.getFoodLevel() + (int) foodRestore);
                    food.setFoodLevel(newFood);
                    float newSat = Math.min(newFood, food.getSaturationLevel() + satRestore);
                    food.setSaturation(newSat);
                }
            }
        }
    }

    @Override
    public void onCriticalHit(Player player, CriticalHitEvent event) {
        if (!event.isVanillaCritical()) return;
        setFuryStacks(player, getFuryStacks(player) + 2);
    }

    @Override
    public void onPlayerKill(Player player, LivingDeathEvent event) {
        setFuryStacks(player, getFuryStacks(player) + EternalHeartConfig.KILL_FURY_BONUS.get());
    }

    @Override
    public void onPlayerHurt(Player player, LivingHurtEvent event) {
        setFuryStacks(player, 0);
    }
}
