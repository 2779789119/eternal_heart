package com.eternal_heart.network;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.EternalHeartMod;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.core.Debuffs;
import com.eternal_heart.features.AttributeFeature;
import com.eternal_heart.features.CustomRecipes;
import com.eternal_heart.features.EquipTracker;
import com.eternal_heart.features.FurySystem;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = EternalHeartMod.MODID)
public final class ConfigNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            EternalHeartMod.id("config"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static long revision;
    private static String published = "";
    // Installed by the client entrypoint; the dedicated server never loads a client class.
    private static Consumer<Snapshot> clientReceiver = packet -> {};

    private ConfigNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(Request.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((msg, buf) -> {}).decoder(buf -> new Request())
                .consumerMainThread(ConfigNetwork::handleRequest).add();
        CHANNEL.messageBuilder(Submit.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder((msg, buf) -> { buf.writeLong(msg.revision()); buf.writeUtf(msg.json(), ConfigValues.MAX_JSON_LENGTH); })
                .decoder(buf -> new Submit(buf.readLong(), buf.readUtf(ConfigValues.MAX_JSON_LENGTH)))
                .consumerMainThread(ConfigNetwork::handleSubmit).add();
        CHANNEL.messageBuilder(Snapshot.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Snapshot::encode).decoder(Snapshot::decode)
                .consumerMainThread((msg, ctx) -> { clientReceiver.accept(msg); ctx.get().setPacketHandled(true); }).add();
    }

    public static void setClientReceiver(Consumer<Snapshot> receiver) { clientReceiver = receiver; }
    public static void requestEditor() { CHANNEL.sendToServer(new Request()); }
    public static void submit(long baseRevision, Map<String, Object> patch) {
        CHANNEL.sendToServer(new Submit(baseRevision, ConfigValues.encode(patch)));
    }

    /**
     * 编辑权限校验（服务端权威，请求与提交两条路径共用）。
     *
     * <p><b>只验证权限</b>：单人主机，或 OP（权限等级 ≥ 2）。
     * 不再要求佩戴饰品——配置面板是管理入口，不应与佩戴状态绑定。</p>
     */
    public static boolean canEdit(ServerPlayer player) {
        var server = player.getServer();
        return server != null
                && (player.hasPermissions(2) || server.isSingleplayerOwner(player.getGameProfile()));
    }

    private static void handleRequest(Request msg, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            boolean allowed = canEdit(player);
            send(player, allowed, allowed ? "opened" : "forbidden");
        }
        context.get().setPacketHandled(true);
    }

    private static void handleSubmit(Submit msg, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) save(player, msg);
        context.get().setPacketHandled(true);
    }

    private static void save(ServerPlayer player, Submit msg) {
        if (!canEdit(player)) { send(player, false, "forbidden"); return; }
        if (msg.revision() != revision) { send(player, false, "conflict"); return; }
        Map<String, Object> patch;
        try {
            patch = ConfigValues.decode(msg.json());
        } catch (RuntimeException e) {
            send(player, false, "invalid");
            return;
        }
        Map<String, Object> previous = ConfigValues.snapshot();
        try {
            if (EternalHeartConfig.CONFIG == null) throw new IllegalStateException("Config not loaded");
            ConfigValues.apply(patch);
            EternalHeartConfig.CONFIG.save();
        } catch (RuntimeException e) {
            ConfigValues.apply(previous);
            LogUtils.getLogger().error("Could not save Eternal Heart config; restored previous values", e);
            send(player, false, "save_failed");
            return;
        }
        publish();
        // 配置已保存：立即重新注入自定义配方 —— 面板里改完配方点「保存并关闭」即生效，
        // 无需再手动执行 /eternalheart recipe reload（inject 幂等，未改配方时开销可忽略）
        CustomRecipes.inject(ServerLifecycleHooks.getCurrentServer());
        send(player, false, "saved");
    }

    /** Must run on the server thread, including file reloads. */
    public static void publish() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        String json = ConfigValues.encode(ConfigValues.snapshot());
        if (!json.equals(published)) {
            published = json;
            revision++;
        }
        Debuffs.invalidate();
        AttributeFeature.refreshAllOnline();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (EquipTracker.query(player)) FurySystem.refreshEffect(player);
            send(player, false, "sync");
        }
    }

    private static void send(ServerPlayer player, boolean open, String status) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new Snapshot(revision, ConfigValues.encode(ConfigValues.snapshot()), canEdit(player), open, status));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player, false, "sync");
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        ConfigValues.clearRemote();
        revision = 0;
        published = ConfigValues.encode(ConfigValues.snapshot());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        revision = 0;
        published = "";
    }

    private record Request() {}
    private record Submit(long revision, String json) {}
    public record Snapshot(long revision, String json, boolean editable, boolean open, String status) {
        private void encode(FriendlyByteBuf buf) {
            buf.writeLong(revision);
            buf.writeUtf(json, ConfigValues.MAX_JSON_LENGTH);
            buf.writeBoolean(editable);
            buf.writeBoolean(open);
            buf.writeUtf(status, 32);
        }
        private static Snapshot decode(FriendlyByteBuf buf) {
            return new Snapshot(buf.readLong(), buf.readUtf(ConfigValues.MAX_JSON_LENGTH),
                    buf.readBoolean(), buf.readBoolean(), buf.readUtf(32));
        }
    }
}
