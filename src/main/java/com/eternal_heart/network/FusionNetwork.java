package com.eternal_heart.network;

import com.eternal_heart.EternalHeartMod;
import com.eternal_heart.features.HeartFusion;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 融合饰品逐个开关的网络通道。
 *
 * <ul>
 *   <li>{@link Toggle}（C→S）：玩家在「融合饰品开关」界面里点某个开关；</li>
 *   <li>{@link Ack}（S→C）：服务端权威结果。物品 NBT 里存着开关状态，但客户端自己的那份
 *       不会因为服务端改 NBT 而自动刷新，所以这里把结果回传，客户端据此对齐本地 NBT ——
 *       提示框 / 客户端侧查询才不会停留在旧状态。</li>
 * </ul>
 *
 * <p>为什么状态存物品 NBT 而不是配置：融合是「这一颗心」的属性，不同玩家 / 不同副本
 * 上的心可以各自不同；配置是全局的，只能一刀切。</p>
 */
public final class FusionNetwork {

    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            EternalHeartMod.id("fusion"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    /** 由客户端入口注入（专用服务器不会加载客户端类）。 */
    private static Consumer<Ack> clientReceiver = packet -> {
    };

    private FusionNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(Toggle.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Toggle::encode).decoder(Toggle::decode)
                .consumerMainThread(FusionNetwork::handleToggle).add();
        CHANNEL.messageBuilder(Ack.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Ack::encode).decoder(Ack::decode)
                .consumerMainThread((msg, ctx) -> {
                    clientReceiver.accept(msg);
                    ctx.get().setPacketHandled(true);
                }).add();
    }

    public static void setClientReceiver(Consumer<Ack> receiver) {
        clientReceiver = receiver;
    }

    /** 客户端调用：请求把第 {@code index} 条融合记录切到 {@code enabled}。 */
    public static void toggle(int index, boolean enabled) {
        CHANNEL.sendToServer(new Toggle(index, enabled));
    }

    private static void handleToggle(Toggle msg, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            boolean ok = HeartFusion.setEntryEnabled(player, msg.index(), msg.enabled());
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new Ack(msg.index(), msg.enabled(), ok));
        }
        context.get().setPacketHandled(true);
    }

    private record Toggle(int index, boolean enabled) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(index);
            buf.writeBoolean(enabled);
        }

        static Toggle decode(FriendlyByteBuf buf) {
            return new Toggle(buf.readVarInt(), buf.readBoolean());
        }
    }

    /** 服务端回执：{@code ok=false} 时客户端要把本地开关翻回去（物品已不在 / 下标失效）。 */
    public record Ack(int index, boolean enabled, boolean ok) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(index);
            buf.writeBoolean(enabled);
            buf.writeBoolean(ok);
        }

        static Ack decode(FriendlyByteBuf buf) {
            return new Ack(buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
        }
    }
}
