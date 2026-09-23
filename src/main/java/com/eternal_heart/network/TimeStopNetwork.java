package com.eternal_heart.network;

import com.eternal_heart.EternalHeartMod;
import com.eternal_heart.features.TimeStop;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 时停的网络通道。
 *
 * <ul>
 *   <li>{@link Request}（C→S）：快捷键触发的发动请求，结果用动作栏消息反馈；</li>
 *   <li>{@link Sync}（S→C）：把时停状态同步给客户端 —— 客户端必须自己也知道时停，
 *       否则弹射物会在本地继续飞行（位置虽由服务端包驱动，但实体 tick 在客户端也会跑）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EternalHeartMod.MODID)
public final class TimeStopNetwork {

    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            EternalHeartMod.id("timestop"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    /** 由客户端入口注入（专用服务器不会加载客户端类）。 */
    private static Consumer<Sync> clientReceiver = packet -> {
    };

    private TimeStopNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(Request.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((msg, buf) -> {
                }).decoder(buf -> new Request())
                .consumerMainThread(TimeStopNetwork::handleRequest).add();
        CHANNEL.messageBuilder(Sync.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Sync::encode).decoder(Sync::decode)
                .consumerMainThread((msg, ctx) -> {
                    clientReceiver.accept(msg);
                    ctx.get().setPacketHandled(true);
                }).add();
    }

    public static void setClientReceiver(Consumer<Sync> receiver) {
        clientReceiver = receiver;
    }

    /** 客户端调用：请求发动时停。 */
    public static void request() {
        CHANNEL.sendToServer(new Request());
    }

    /** 服务端调用：把状态同步给指定玩家。 */
    public static void send(Sync packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    private static void handleRequest(Request msg, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            Component failure = TimeStop.start(player);
            if (failure != null) player.displayClientMessage(failure, true);
        }
        context.get().setPacketHandled(true);
    }

    /** 玩家登录时清一次客户端状态，避免上一局残留。 */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            send(new Sync(player.level().dimension(), 0, player.getUUID(), Vec3.ZERO, true), player);
        }
    }

    private record Request() {
    }

    /**
     * 时停状态同步包。
     *
     * @param clear true 表示时停结束（清除客户端状态）
     */
    public record Sync(ResourceKey<Level> dimension, int remaining, UUID caster, Vec3 center, boolean clear) {

        void encode(FriendlyByteBuf buf) {
            buf.writeResourceKey(dimension);
            buf.writeVarInt(remaining);
            buf.writeUUID(caster);
            buf.writeDouble(center.x);
            buf.writeDouble(center.y);
            buf.writeDouble(center.z);
            buf.writeBoolean(clear);
        }

        static Sync decode(FriendlyByteBuf buf) {
            ResourceKey<Level> dimension = buf.readResourceKey(Registries.DIMENSION);
            int remaining = buf.readVarInt();
            UUID caster = buf.readUUID();
            Vec3 center = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            return new Sync(dimension, remaining, caster, center, buf.readBoolean());
        }
    }
}
