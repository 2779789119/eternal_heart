package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.integration.Integrations;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;

import java.util.List;

public class MobilitySystem implements IFeature {

    private static final String LAST_DASH_TIME_KEY = "eternal_heart_last_dash";
    private static final String PREV_FORWARD_KEY = "eternal_heart_prev_forward";
    private static final String DASH_KEY_RELEASED_KEY = "eternal_heart_dash_released";
    private static final String SCALE_APPLIED_KEY = "eternal_heart_scale_applied";

    @Override
    public String getName() {
        return "MobilitySystem";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void onPlayerTick(Player player, LivingTickEvent event) {
        handleDolphinsGrace(player);
    }

    @Override
    public void onPlayerTickBoth(Player player, LivingTickEvent event) {
        handleFlight(player);
        handleStepAssist(player);
        handleJumpBoost(player);
        handleSlowFallGlide(player);
        handleFluidWalking(player);
        handleWallClimb(player);
        handleDash(player);
        handlePehkuiScale(player);
    }

    @Override
    public void onUnequip(Player player) {
        if (player.getAbilities().mayfly) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }

        Integrations.setElytraFlight(player, false);

        player.setMaxUpStep(0.6f);

        Integrations.resetScale(player);
        player.getPersistentData().remove(SCALE_APPLIED_KEY);
    }

    private void handleFlight(Player player) {
        EternalHeartConfig.FlightMode mode = EternalHeartConfig.FLIGHT_MODE.get();

        if (player.isCreative() || player.isSpectator()) {
            return;
        }

        if (mode == EternalHeartConfig.FlightMode.CREATIVE) {
            if (!player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
            }
            Integrations.setElytraFlight(player, false);
        } else if (mode == EternalHeartConfig.FlightMode.ELYTRA) {
            if (player.getAbilities().mayfly) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
            Integrations.setElytraFlight(player, true);
        } else {
            if (player.getAbilities().mayfly) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
            Integrations.setElytraFlight(player, false);
        }
    }

    private void handleStepAssist(Player player) {
        if (EternalHeartConfig.STEP_ASSIST.get()) {
            player.setMaxUpStep(1.25f);
        } else {
            player.setMaxUpStep(0.6f);
        }
    }

    private void handleJumpBoost(Player player) {
        if (!EternalHeartConfig.JUMP_BOOST.get()) return;

        long tick = player.level().getGameTime();
        if (tick % 60 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP,
                    80, 0, false, false, true));
        }
    }

    private void handleSlowFallGlide(Player player) {
        if (!EternalHeartConfig.SLOW_FALL_GLIDE.get()) return;

        boolean inAir = !player.onGround();
        boolean sneaking = player.isShiftKeyDown();
        boolean inWater = player.isInWater();
        boolean flying = player.getAbilities().flying;

        if (inAir && sneaking && !inWater && !flying) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,
                    20, 0, false, false, true));

            if (player.zza > 0) {
                Vec3 look = player.getLookAngle();
                double horizontalSpeed = 0.08;
                player.setDeltaMovement(player.getDeltaMovement().add(
                        look.x * horizontalSpeed,
                        0,
                        look.z * horizontalSpeed
                ));
            }
        }
    }

    private void handleDolphinsGrace(Player player) {
        if (!EternalHeartConfig.DOLPHINS_GRACE.get()) return;

        if (player.isInWater()) {
            player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE,
                    40, 0, false, false, true));
        }
    }

    private void handleFluidWalking(Player player) {
        boolean waterWalk = EternalHeartConfig.WATER_WALKING.get();
        boolean lavaWalk = EternalHeartConfig.LAVA_WALKING.get();

        if (!waterWalk && !lavaWalk) return;

        if (player.isShiftKeyDown()) return;
        if (player.getAbilities().flying) return;

        Level level = player.level();
        BlockPos footPos = player.blockPosition();
        BlockPos belowPos = footPos.below();

        FluidState footFluid = level.getFluidState(footPos);
        FluidState belowFluid = level.getFluidState(belowPos);

        boolean inWater = false;
        boolean inLava = false;
        double surfaceY = 0;

        if (waterWalk) {
            if (!footFluid.isEmpty() && footFluid.getType().is(net.minecraft.tags.FluidTags.WATER)) {
                inWater = true;
                surfaceY = footPos.getY() + footFluid.getHeight(level, footPos);
            } else if (!belowFluid.isEmpty() && belowFluid.getType().is(net.minecraft.tags.FluidTags.WATER)) {
                inWater = true;
                surfaceY = belowPos.getY() + belowFluid.getHeight(level, belowPos);
            }
        }

        if (lavaWalk) {
            if (!footFluid.isEmpty() && footFluid.getType().is(net.minecraft.tags.FluidTags.LAVA)) {
                inLava = true;
                surfaceY = footPos.getY() + footFluid.getHeight(level, footPos);
            } else if (!belowFluid.isEmpty() && belowFluid.getType().is(net.minecraft.tags.FluidTags.LAVA)) {
                inLava = true;
                surfaceY = belowPos.getY() + belowFluid.getHeight(level, belowPos);
            }
        }

        if (!inWater && !inLava) return;

        double playerY = player.getY();
        double delta = surfaceY - playerY;

        if (delta > 0.3) {
            player.teleportTo(player.getX(), surfaceY, player.getZ());
        } else if (delta > 0) {
            player.setPos(player.getX(), surfaceY, player.getZ());
        }

        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, Math.min(motion.y, 0.0), motion.z);
        player.setOnGround(true);
        player.resetFallDistance();
    }

    private void handleWallClimb(Player player) {
        if (!EternalHeartConfig.WALL_CLIMB.get()) return;

        if (player.onGround()) return;
        if (player.isShiftKeyDown()) return;
        if (player.getAbilities().flying) return;

        Level level = player.level();
        Vec3 look = player.getLookAngle();
        double reach = 0.4;

        double dx = look.x;
        double dz = look.z;
        double horizLen = Math.sqrt(dx * dx + dz * dz);
        if (horizLen < 0.01) return;

        dx /= horizLen;
        dz /= horizLen;

        boolean movingForward = false;
        if (player.zza > 0) {
            movingForward = true;
        } else {
            Vec3 velocity = player.getDeltaMovement();
            if (velocity.lengthSqr() > 0.01) {
                double dot = velocity.x * dx + velocity.z * dz;
                if (dot > 0.1) movingForward = true;
            }
        }

        if (!movingForward) return;

        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        boolean hasWall = false;

        BlockPos[] checkPositions = {
                new BlockPos((int) Math.floor(px + dx * reach), (int) Math.floor(py + 0.5), (int) Math.floor(pz + dz * reach)),
                new BlockPos((int) Math.floor(px + dx * reach), (int) Math.floor(py + 1.5), (int) Math.floor(pz + dz * reach))
        };

        for (BlockPos pos : checkPositions) {
            BlockState state = level.getBlockState(pos);
            if (state.isSolidRender(level, pos)) {
                hasWall = true;
                break;
            }
        }

        if (hasWall) {
            double climbSpeed = EternalHeartConfig.WALL_CLIMB_SPEED.get();
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, climbSpeed, motion.z);
            player.resetFallDistance();
        }
    }

    private void handleDash(Player player) {
        if (!EternalHeartConfig.DASH.get()) return;

        long now = player.level().getGameTime();
        int cooldown = EternalHeartConfig.DASH_COOLDOWN.get();

        boolean hasForwardInput = player.zza > 0 || player.xxa != 0;
        boolean prevForward = player.getPersistentData().getBoolean(PREV_FORWARD_KEY);

        if (!hasForwardInput && prevForward) {
            player.getPersistentData().putBoolean(DASH_KEY_RELEASED_KEY, true);
        }

        if (hasForwardInput && !prevForward) {
            boolean wasReleased = player.getPersistentData().getBoolean(DASH_KEY_RELEASED_KEY);
            long lastDash = player.getPersistentData().getLong(LAST_DASH_TIME_KEY);

            if (wasReleased && player.onGround() && (now - lastDash) >= cooldown) {
                performDash(player, now);
            }

            player.getPersistentData().putBoolean(DASH_KEY_RELEASED_KEY, false);
        }

        player.getPersistentData().putBoolean(PREV_FORWARD_KEY, hasForwardInput);
    }

    private void performDash(Player player, long now) {
        player.getPersistentData().putLong(LAST_DASH_TIME_KEY, now);

        double xxa = player.xxa;
        double zza = player.zza;

        double length = Math.sqrt(xxa * xxa + zza * zza);
        if (length < 0.01) {
            zza = 1.0;
            length = 1.0;
        }

        double nx = xxa / length;
        double nz = zza / length;

        double force = EternalHeartConfig.DASH_FORCE.get();
        Vec3 look = player.getLookAngle();
        double forwardX = look.x;
        double forwardZ = look.z;
        double fLen = Math.sqrt(forwardX * forwardX + forwardZ * forwardZ);
        if (fLen > 0.01) {
            forwardX /= fLen;
            forwardZ /= fLen;
        }

        double rightX = -forwardZ;
        double rightZ = forwardX;

        double dashX = forwardX * nz + rightX * nx;
        double dashZ = forwardZ * nz + rightZ * nx;

        double dashLen = Math.sqrt(dashX * dashX + dashZ * dashZ);
        if (dashLen > 0.01) {
            dashX /= dashLen;
            dashZ /= dashLen;
        }

        player.setDeltaMovement(dashX * force, 0.3, dashZ * force);

        int invulnTicks = EternalHeartConfig.DASH_INVULN_TICKS.get();
        if (invulnTicks > 0) {
            player.invulnerableTime = invulnTicks;
        }

        double damage = EternalHeartConfig.DASH_DAMAGE.get();
        if (damage > 0) {
            AABB area = player.getBoundingBox().inflate(1.5);
            List<LivingEntity> nearby = player.level().getEntitiesOfClass(LivingEntity.class, area,
                    e -> e != player && e.isAlive() && e instanceof Enemy);
            for (LivingEntity e : nearby) {
                e.invulnerableTime = 0;
                e.hurt(player.damageSources().playerAttack(player), (float) damage);
                Vec3 kb = e.position().subtract(player.position()).normalize().scale(1.0).add(0, 0.3, 0);
                e.setDeltaMovement(kb);
                e.hurtMarked = true;
            }
        }
    }

    private void handlePehkuiScale(Player player) {
        double scale = EternalHeartConfig.PEHKUI_SCALE.get();
        if (scale <= 0) return;
        if (!Integrations.isPehkuiLoaded()) return;

        if (!player.getPersistentData().getBoolean(SCALE_APPLIED_KEY)) {
            Integrations.setScale(player, (float) scale);
            player.getPersistentData().putBoolean(SCALE_APPLIED_KEY, true);
        }
    }
}
