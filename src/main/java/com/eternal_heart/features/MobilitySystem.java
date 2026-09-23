package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.core.Ticker;
import com.eternal_heart.integration.Integrations;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * 机动系统 —— 飞行、滑翔、台阶辅助、跳跃提升、水面 / 熔岩行走、海豚的恩惠。
 *
 * <p>重写要点：</p>
 * <ul>
 *   <li><b>流体查询合并</b>：水 / 岩浆行走历史实现每 tick 最多 4 次
 *       {@code getFluidState} + 多次类型判定；现在脚下与脚下方块合计最多 2 次，
 *       选中判定收进单一谓词 {@link #isWalkableFluid}；</li>
 *   <li><b>飞行状态机职责单一</b>：创造 / 鞘翅 / 关闭三条分支各自独立方法，
 *       服务端绝不写回 flying（避免与客户端能力包竞态导致无法起飞）；</li>
 *   <li><b>内置滑翔</b>：未安装 Caelus 时以纯事件驱动实现鞘翅语义
 *       （自由落体触发缓降 + 沿视角前进，潜行取消、免摔伤），不依赖原版鞘翅判定；</li>
 *   <li>清理死代码：移除从未写入的旧下潜标记。</li>
 * </ul>
 */
public class MobilitySystem implements IFeature {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 已授予创造飞行的标记（供无限加点等其它模组识别并避让，避免抢占 mayfly） */
    private static final String FLIGHT_GRANTED_KEY = "eternal_heart_creative_flight";
    /** 熔岩行走授予的短时抗火到期时间（游戏时间），由 SurvivalSystem 读取 */
    public static final String LAVA_RESIST_UNTIL = "eternal_heart_lava_resist_until";

    private static final UUID STEP_UUID = UUID.fromString("e1a00009-0000-0000-0000-000000000000");

    // ==================== 内置自定义滑翔参数（未安装 Caelus 时使用） ====================

    /** 进入滑翔的下落速度阈值：下落速度低于此值（自由落体）时自动开始滑翔 */
    private static final double GLIDE_START_FALL_SPEED = -0.6;
    /** 滑翔时下落速度的衰减系数（每 tick 乘以此值，平滑过渡到缓降） */
    private static final double GLIDE_FALL_DAMPING = 0.7;
    /** 滑翔的最大下落速度（格/tick） */
    private static final double GLIDE_MAX_FALL_SPEED = -0.22;
    /** 滑翔时沿视角方向的加速度（每 tick，格/tick²） */
    private static final double GLIDE_ACCELERATION = 0.06;
    /** 滑翔的水平速度上限（格/tick） */
    private static final double GLIDE_MAX_HORIZONTAL_SPEED = 1.1;

    /** 台阶辅助的额外高度（格） */
    private static final double STEP_HEIGHT_BONUS = 0.65;

    /**
     * 能力包补发间隔（tick）：服务端「已授权所以不再发包」时，
     * 客户端若因重建/被其它机制重置而停留在 mayfly=false，只能靠周期性补发自愈。
     */
    private static final int FLIGHT_RESYNC_INTERVAL = 100;

    // ==================== 台阶 / 流体行走吸附参数 ====================

    /** 液面吸附生效的最大垂直偏差（格）：超出视为游泳或已跳出，不干预 */
    private static final double FLUID_SNAP_RANGE = 0.6;
    /** 已在液面上方时的维持阈值（格） */
    private static final double FLUID_STAND_THRESHOLD = 0.15;

    @Override
    public String getName() {
        return "MobilitySystem";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    // ============================================================
    //  Tick
    // ============================================================

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
    }

    @Override
    public void onUnequip(Player player) {
        revokeCreativeFlight(player, false);
        Integrations.setElytraFlight(player, false);

        AttributeInstance step = player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
        if (step != null) step.removeModifier(STEP_UUID);

        OwnedEffects.release(player, MobEffects.JUMP);
        OwnedEffects.release(player, MobEffects.SLOW_FALLING);
        OwnedEffects.release(player, MobEffects.DOLPHINS_GRACE);

        CompoundTag data = player.getPersistentData();
        data.remove(FLIGHT_GRANTED_KEY);
        data.remove(LAVA_RESIST_UNTIL);
    }

    // ============================================================
    //  飞行
    // ============================================================

    /**
     * 飞行总控：以 {@link EternalHeartConfig.FlightMode} 为<b>唯一控制源</b>，三种模式互斥清晰。
     *
     * <ul>
     *   <li><b>CREATIVE</b> —— 创造飞行：仅确保 mayfly 开启。flying 的切换完全交给原版
     *       LocalPlayer（双击空格 → 客户端置 flying 并通过能力包同步服务端）。
     *       服务端【绝不能】每 tick 写回 flying —— 会与客户端同步包竞态，
     *       把刚起飞的 true 覆盖回 false（历史 bug：永远无法起飞）。</li>
     *   <li><b>ELYTRA</b> —— 鞘翅滑翔：回收创造飞行后，装了 Caelus 用其属性实现「真鞘翅」；
     *       未装 Caelus 自动改用内置自定义滑翔（{@link #applyCustomGlide}）。</li>
     *   <li><b>OFF</b> —— 关闭：回收一切飞行能力（创造 + 鞘翅）。</li>
     * </ul>
     */
    private void handleFlight(Player player) {
        applyFlight(player, false);
    }

    /**
     * 跨维度 / 重生 / 登录后的飞行能力重同步。
     *
     * <p><b>为什么需要</b>：这些时刻客户端会重建玩家实例，能力完全由服务端的
     * {@code ClientboundPlayerAbilitiesPacket} 驱动；而常规授权逻辑是「mayfly 为 false 才授权并发包」——
     * 服务端状态没变化就不会再发包，客户端于是停留在默认的 mayfly=false，
     * 表现为<b>「切换维度后无法飞行」</b>。这里按当前配置重新授权并强制发包，
     * 并覆盖「服务端已授权但客户端不知道」的所有场景。</p>
     */
    public static void resyncFlight(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) return;
        boolean granted = player.getPersistentData().getBoolean(FLIGHT_GRANTED_KEY);
        boolean equipped = EquipTracker.isEquipped(player);
        if (granted || (equipped
                && ConfigValues.get(EternalHeartConfig.FLIGHT_MODE) == EternalHeartConfig.FlightMode.CREATIVE)) {
            applyFlight(player, true);
        } else {
            // 未授权过：只补发一次能力包，保证两端状态一致（不改变授权）
            player.onUpdateAbilities();
        }
    }

    /** 飞行总控（forceSync = 强制重发能力包，用于客户端重建后的补同步）。 */
    private static void applyFlight(Player player, boolean forceSync) {
        if (player.isCreative() || player.isSpectator()) {
            player.getPersistentData().remove(FLIGHT_GRANTED_KEY);
            return;
        }
        switch (ConfigValues.get(EternalHeartConfig.FLIGHT_MODE)) {
            case CREATIVE -> applyCreativeFlight(player, forceSync);
            case ELYTRA -> applyElytraFlight(player, forceSync);
            case OFF -> revokeAllFlight(player, forceSync);
        }
    }

    /** 创造飞行：确保 mayfly 开启（起飞/降落由原版双击空格控制，客户端负责同步飞行状态）。 */
    private static void applyCreativeFlight(Player player, boolean forceSync) {
        if (player.level().isClientSide()) return; // 服务端负责授权，客户端只负责切换 flying
        CompoundTag data = player.getPersistentData();
        boolean granted = data.getBoolean(FLIGHT_GRANTED_KEY);
        // granted 校验：持久化标记若丢失（旧存档 / 其它途径清空）也能重建，
        // 否则卸下时 owned=false 会导致飞行能力残留
        boolean due = Ticker.due(player.getUUID(), player.level().getGameTime(), FLIGHT_RESYNC_INTERVAL);
        if (!granted || !player.getAbilities().mayfly || forceSync || due) {
            player.getAbilities().mayfly = true;
            data.putBoolean(FLIGHT_GRANTED_KEY, true);
            player.onUpdateAbilities();
        }
        Integrations.setElytraFlight(player, false);
    }

    /** 鞘翅滑翔：回收创造飞行后，优先用 Caelus 真鞘翅；无 Caelus 时使用内置自定义滑翔。 */
    private static void applyElytraFlight(Player player, boolean forceSync) {
        revokeCreativeFlight(player, forceSync);
        if (Integrations.isElytraFlightAvailable()) {
            if (!player.level().isClientSide()) Integrations.setElytraFlight(player, true);
        } else {
            applyCustomGlide(player);
        }
    }

    /** 关闭飞行：回收创造飞行 + 鞘翅飞行。 */
    private static void revokeAllFlight(Player player, boolean forceSync) {
        revokeCreativeFlight(player, forceSync);
        if (!player.level().isClientSide()) Integrations.setElytraFlight(player, false);
    }

    /** 回收创造飞行能力（mayfly/flying）并清理避让标记。 */
    private static void revokeCreativeFlight(Player player, boolean forceSync) {
        if (player.level().isClientSide()) return;
        CompoundTag data = player.getPersistentData();
        boolean owned = data.getBoolean(FLIGHT_GRANTED_KEY);
        if ((owned || forceSync) && !player.isCreative() && !player.isSpectator()) {
            // 只回收「我们自己授予」的能力，绝不误关其它模组给的飞行
            if (owned) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
            }
            player.onUpdateAbilities();
            if (owned) LOGGER.debug("[永恒之心] 回收创造飞行（{}）", player.getName().getString());
        }
        data.remove(FLIGHT_GRANTED_KEY);
    }

    /**
     * 内置自定义滑翔（未安装 Caelus 的保底实现，纯事件驱动、不依赖原版鞘翅判定与 Mixin）。
     *
     * <p>触发：空中自由落体（下落速度低于 {@link #GLIDE_START_FALL_SPEED}）时自动进入滑翔 ——
     * 下落速度每 tick 按 {@link #GLIDE_FALL_DAMPING} 平滑衰减至 {@link #GLIDE_MAX_FALL_SPEED}，
     * 同时沿视角方向持续加速（水平速度上限 {@link #GLIDE_MAX_HORIZONTAL_SPEED} 格/tick）。</p>
     *
     * <p>取消方式：潜行键（主动快速下落）；落地 / 入水 / 攀爬 / 骑乘时自动失效。
     * 滑翔期间摔落距离清零（免摔伤，符合鞘翅特性）。</p>
     */
    private static void applyCustomGlide(Player player) {
        if (player.onGround() || player.isInWater() || player.isInLava()
                || player.onClimbable() || player.isPassenger() || player.getAbilities().flying) {
            return;
        }
        if (player.isShiftKeyDown()) return; // 潜行 = 主动快速下落

        Vec3 motion = player.getDeltaMovement();
        if (motion.y > GLIDE_START_FALL_SPEED) return; // 尚未进入自由落体

        // 平滑缓降 + 沿视角方向前进
        double vy = Math.max(motion.y * GLIDE_FALL_DAMPING, GLIDE_MAX_FALL_SPEED);
        Vec3 look = player.getLookAngle();
        double vx = motion.x + look.x * GLIDE_ACCELERATION;
        double vz = motion.z + look.z * GLIDE_ACCELERATION;

        // 水平速度上限（防止无限加速）
        double horizontal = Math.sqrt(vx * vx + vz * vz);
        if (horizontal > GLIDE_MAX_HORIZONTAL_SPEED) {
            double scale = GLIDE_MAX_HORIZONTAL_SPEED / horizontal;
            vx *= scale;
            vz *= scale;
        }

        player.setDeltaMovement(vx, vy, vz);
        player.fallDistance = 0;   // 免摔伤
        player.hurtMarked = true;  // 标记速度变化，随常规同步发往对端
    }

    // ============================================================
    //  台阶 / 跳跃 / 缓降
    // ============================================================

    private void handleStepAssist(Player player) {
        if (player.level().isClientSide()) return;
        AttributeInstance step = player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
        if (step == null) return;
        if (ConfigValues.get(EternalHeartConfig.STEP_ASSIST)) {
            if (step.getModifier(STEP_UUID) == null) {
                step.addTransientModifier(new AttributeModifier(
                        STEP_UUID, "Eternal Heart Step", STEP_HEIGHT_BONUS, AttributeModifier.Operation.ADDITION));
            }
        } else {
            step.removeModifier(STEP_UUID);
        }
    }

    private void handleJumpBoost(Player player) {
        OwnedEffects.maintain(player, MobEffects.JUMP, ConfigValues.get(EternalHeartConfig.JUMP_BOOST), 80, 0, 20);
    }

    /** 潜行缓降：空中潜行时缓速下落并允许向前滑行。 */
    private void handleSlowFallGlide(Player player) {
        if (!ConfigValues.get(EternalHeartConfig.SLOW_FALL_GLIDE)) {
            OwnedEffects.release(player, MobEffects.SLOW_FALLING);
            return;
        }
        if (player.onGround() || !player.isShiftKeyDown() || player.isInWater()
                || player.getAbilities().flying) {
            return;
        }
        OwnedEffects.maintain(player, MobEffects.SLOW_FALLING, true, 20, 0, 5);

        if (player.zza > 0) {
            Vec3 look = player.getLookAngle();
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.add(look.x * 0.08, 0, look.z * 0.08));
        }
    }

    private void handleDolphinsGrace(Player player) {
        OwnedEffects.maintain(player, MobEffects.DOLPHINS_GRACE,
                ConfigValues.get(EternalHeartConfig.DOLPHINS_GRACE) && player.isInWater(), 40, 0, 10);
    }

    // ============================================================
    //  水面 / 熔岩行走
    // ============================================================

    /**
     * 水上 / 熔岩行走。
     *
     * <p>核心规则：</p>
     * <ul>
     *   <li>只有脚底距液面较近（±{@link #FLUID_SNAP_RANGE} 格以内）时才施加「站在液面」的支撑；</li>
     *   <li>更深 → 玩家在游泳/下潜，不干预；更高 → 玩家已跳出液面，不干预；</li>
     *   <li>潜行 → 允许主动下潜，不施加任何抬升；</li>
     *   <li>熔岩行走时附带短时抗火（记录到期时间供效果系统读取）。</li>
     * </ul>
     */
    private void handleFluidWalking(Player player) {
        boolean waterWalk = ConfigValues.get(EternalHeartConfig.WATER_WALKING);
        boolean lavaWalk = ConfigValues.get(EternalHeartConfig.LAVA_WALKING);
        if (!waterWalk && !lavaWalk) return;
        if (player.getAbilities().flying || player.isShiftKeyDown()) return;

        Level level = player.level();
        BlockPos footPos = player.blockPosition();
        BlockPos belowPos = footPos.below();

        // 优先脚下、其次脚下方块（合并历史实现的 4 次流体查询）
        FluidState footFluid = level.getFluidState(footPos);
        FluidState fluid;
        BlockPos fluidPos;
        if (isWalkableFluid(footFluid, waterWalk, lavaWalk)) {
            fluid = footFluid;
            fluidPos = footPos;
        } else {
            fluid = level.getFluidState(belowPos);
            fluidPos = belowPos;
            if (!isWalkableFluid(fluid, waterWalk, lavaWalk)) return;
        }

        double surfaceY = fluidPos.getY() + (fluid.isSource() ? 1.0 : fluid.getHeight(level, fluidPos));
        double depth = surfaceY - player.getY(); // 正值 = 液面之下，负值 = 液面之上
        if (depth > FLUID_SNAP_RANGE || depth < -FLUID_SNAP_RANGE) return;

        Vec3 motion = player.getDeltaMovement();
        if (player.getY() < surfaceY) {
            // 低于液面：抬到液面上并抵消下沉动量
            player.setPos(player.getX(), surfaceY, player.getZ());
            player.setOnGround(true);
            player.resetFallDistance();
            if (motion.y < 0) player.setDeltaMovement(motion.x, 0.0, motion.z);
        } else if (player.getY() - surfaceY < FLUID_STAND_THRESHOLD) {
            // 已在液面附近：维持站立，阻止下沉
            player.setOnGround(true);
            if (motion.y < 0) player.setDeltaMovement(motion.x, 0.0, motion.z);
        }

        if (fluid.getType().is(FluidTags.LAVA)) {
            player.clearFire();
            player.setRemainingFireTicks(0);
            if (!level.isClientSide()) {
                player.getPersistentData().putLong(LAVA_RESIST_UNTIL, level.getGameTime() + 20);
            }
        }
    }

    /** 该流体是否允许被"行走"支撑。 */
    private static boolean isWalkableFluid(FluidState state, boolean waterWalk, boolean lavaWalk) {
        if (state.isEmpty()) return false;
        if (waterWalk && state.getType().is(FluidTags.WATER)) return true;
        return lavaWalk && state.getType().is(FluidTags.LAVA);
    }
}
