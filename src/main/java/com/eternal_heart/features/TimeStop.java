package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.EternalHeartMod;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.network.TimeStopNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 时停 —— 冻结范围内除发动者以外的全部实体（含弹射物）。
 *
 * <h3>为什么需要两张状态表</h3>
 * <p>实体的 tick 在两端都会发生：服务端决定权威行为，客户端为了表现（插值、动画、
 * 弹射物本地飞行）也会自己 tick。只冻结服务端的话，箭在客户端仍会继续飞。
 * 因此维护两份状态：</p>
 * <ul>
 *   <li>{@link #SERVER}：权威状态，由 {@link #start} / {@link #tick} 推进；</li>
 *   <li>{@link #CLIENT}：客户端镜像，由 {@link TimeStopNetwork} 的同步包维护并本地递减。</li>
 * </ul>
 * <p>两者必须是独立的表：单人游戏里客户端与服务端同处一个 JVM，共用一张表会让
 * 客户端递减把服务端的权威状态一起吃掉。</p>
 *
 * <h3>为什么用「剩余 tick 计数」而不是时间戳</h3>
 * <p>各维度的 {@code getGameTime()} 基准并不一致，计数可以完全绕开这个问题，
 * 且判定退化为一次哈希表查询（未激活时表为空，几乎零开销）。</p>
 */
public final class TimeStop {

    /** 服务端权威状态：维度 → 时停状态。 */
    private static final Map<ResourceKey<Level>, State> SERVER = new ConcurrentHashMap<>();
    /** 客户端镜像状态（仅用于同步冻结视觉）。 */
    private static final Map<ResourceKey<Level>, State> CLIENT = new ConcurrentHashMap<>();
    /** 玩家 → 冷却结束的游戏刻。 */
    private static final Map<UUID, Long> COOLDOWN = new ConcurrentHashMap<>();

    /** 状态重播间隔（tick）：让中途进入该维度的玩家也能收到状态。 */
    private static final int RESYNC_INTERVAL = 10;

    /**
     * 单次时停的状态。
     *
     * @param remaining 剩余 tick 数
     * @param caster    发动者（永不冻结）
     * @param center    发动时的位置（半径模式下冻结范围的中心）
     */
    private record State(int remaining, UUID caster, Vec3 center) {
    }

    private TimeStop() {
    }

    // ============================================================
    //  冻结判定（mixin 热路径）
    // ============================================================

    /**
     * 该实体此刻是否应被冻结。
     *
     * <p>热路径顺序经过精心安排：未激活时只是一次哈希表查询后返回，
     * 不读取任何配置项，因此常态开销可忽略。</p>
     */
    public static boolean frozen(Entity entity) {
        Level level = entity.level();
        State state = (level.isClientSide() ? CLIENT : SERVER).get(level.dimension());
        if (state == null) return false;

        if (entity instanceof Player player) {
            // 发动者永不受影响；其它玩家按配置（默认不冻结，避免互相卡住）
            if (player.getUUID().equals(state.caster())) return false;
            if (!ConfigValues.get(EternalHeartConfig.TIME_STOP_AFFECT_PLAYERS)) return false;
        }

        double radius = ConfigValues.get(EternalHeartConfig.TIME_STOP_RADIUS);
        if (radius <= 0) return true; // 0 = 整个维度
        return entity.position().distanceToSqr(state.center()) <= radius * radius;
    }

    // ============================================================
    //  服务端：触发与推进
    // ============================================================

    /**
     * 尝试发动时停。
     *
     * @return 失败原因（供调用方提示）；成功返回 {@code null}
     */
    public static Component start(ServerPlayer player) {
        if (!ConfigValues.get(EternalHeartConfig.TIME_STOP)) {
            return Component.translatable("message.eternal_heart.timestop.disabled");
        }

        int duration = ConfigValues.get(EternalHeartConfig.TIME_STOP_DURATION);
        if (duration <= 0) return Component.translatable("message.eternal_heart.timestop.disabled");

        long now = player.level().getGameTime();
        Long until = COOLDOWN.get(player.getUUID());
        if (until != null && now < until) {
            long seconds = (until - now + 19) / 20;
            return Component.translatable("message.eternal_heart.timestop.cooldown", seconds);
        }

        ResourceKey<Level> dimension = player.level().dimension();
        // 同一维度重复发动直接续时并重设中心（语义更直观）
        State state = new State(duration * 20, player.getUUID(), player.position());
        SERVER.put(dimension, state);
        COOLDOWN.put(player.getUUID(), now + ConfigValues.get(EternalHeartConfig.TIME_STOP_COOLDOWN) * 20L);

        MinecraftServer server = player.getServer();
        if (server != null) broadcast(server, dimension, state, false);

        // 发动音效：在发动者位置播放，附近玩家均可听到
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                EternalHeartMod.SHI_TING.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        return null;
    }

    /** 服务器每 tick 推进计数、重播状态并在到期时清理。 */
    public static void tick(MinecraftServer server) {
        if (SERVER.isEmpty()) return;
        for (Map.Entry<ResourceKey<Level>, State> entry : new ArrayList<>(SERVER.entrySet())) {
            State state = entry.getValue();
            if (state.remaining() > 1) {
                State next = new State(state.remaining() - 1, state.caster(), state.center());
                SERVER.put(entry.getKey(), next);
                // 周期性重播：中途进入该维度的玩家不会错过状态
                if (next.remaining() % RESYNC_INTERVAL == 0) {
                    broadcast(server, entry.getKey(), next, false);
                }
                continue;
            }
            SERVER.remove(entry.getKey());
            broadcast(server, entry.getKey(), state, true);
            notifyEnd(server, entry.getKey(), state.caster());
        }
    }

    private static void broadcast(MinecraftServer server, ResourceKey<Level> dimension,
                                  State state, boolean clear) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) return;
        TimeStopNetwork.Sync packet = new TimeStopNetwork.Sync(dimension,
                clear ? 0 : state.remaining(), state.caster(), state.center(), clear);
        for (ServerPlayer player : level.players()) {
            TimeStopNetwork.send(packet, player);
        }
    }

    /** 时停结束：通知发动者（离线则忽略）。 */
    private static void notifyEnd(MinecraftServer server, ResourceKey<Level> dimension, UUID caster) {
        ServerPlayer player = server.getPlayerList().getPlayer(caster);
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.eternal_heart.timestop.ended"), true);
        }
    }

    // ============================================================
    //  客户端：镜像状态
    // ============================================================

    /** 收到服务端同步包（clear = 时停结束）。 */
    public static void applySync(ResourceKey<Level> dimension, int remaining,
                                 UUID caster, Vec3 center, boolean clear) {
        if (clear) {
            CLIENT.remove(dimension);
        } else {
            CLIENT.put(dimension, new State(remaining, caster, center));
        }
    }

    /** 客户端每 tick 递减镜像状态（服务端结束包丢失时的兜底，避免状态残留）。 */
    public static void clientTick() {
        if (CLIENT.isEmpty()) return;
        for (Map.Entry<ResourceKey<Level>, State> entry : new ArrayList<>(CLIENT.entrySet())) {
            State state = entry.getValue();
            if (state.remaining() > 1) {
                CLIENT.put(entry.getKey(), new State(state.remaining() - 1, state.caster(), state.center()));
            } else {
                CLIENT.remove(entry.getKey());
            }
        }
    }

    /** 执行中的时停（诊断用）。 */
    public static int activeCount() {
        return SERVER.size();
    }

    /** 服务器停止 / 退出世界时清空状态，避免残留。 */
    public static void clear() {
        SERVER.clear();
        CLIENT.clear();
        COOLDOWN.clear();
    }
}
