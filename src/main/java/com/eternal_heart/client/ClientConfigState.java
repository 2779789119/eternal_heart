package com.eternal_heart.client;

import com.eternal_heart.EternalHeartMod;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.network.ConfigNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

@Mod.EventBusSubscriber(modid = EternalHeartMod.MODID, value = Dist.CLIENT)
public final class ClientConfigState {
    private ClientConfigState() {}

    public static void receive(ConfigNetwork.Snapshot packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        Map<String, Object> values = ConfigValues.decode(packet.json());
        // Integrated server and client share ConfigValue objects. Only the server mutates them.
        if (!mc.hasSingleplayerServer()) {
            ConfigValues.setRemote(values);
        }
        if (packet.open() && packet.editable()) {
            mc.setScreen(new EternalHeartConfigScreen(packet.revision(), values));
        } else if (mc.screen instanceof EternalHeartConfigScreen screen) {
            screen.receive(packet, values);
        } else if (!packet.status().equals("sync") && mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("config.eternal_heart.status." + packet.status()), false);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ConfigValues.clearRemote();
    }
}
