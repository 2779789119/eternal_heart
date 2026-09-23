package com.eternal_heart.client;

import com.eternal_heart.EternalHeartMod;
import com.eternal_heart.network.ConfigNetwork;
import com.eternal_heart.network.TimeStopNetwork;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端 FORGE 总线监听：检测快捷键按下后打开配置面板。
 *
 * <p>打开限制：<b>只验证权限</b>——由服务器检查「单人主机 / OP（权限等级 ≥ 2）」并返回最新配置，
 * 提交时再次检查。不再要求佩戴饰品。</p>
 *
 * <p>无权限时服务器回 {@code forbidden}，客户端在动作栏给出提示
 * （见 {@link ClientConfigState#receive}），不会「按了没反应」。</p>
 */
@Mod.EventBusSubscriber(modid = EternalHeartMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class EternalHeartKeyHandler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        // 客户端镜像状态递减（服务端结束包丢失时的兜底）
        com.eternal_heart.features.TimeStop.clientTick();
        // 仅在无其它界面打开时响应，避免与聊天/输入框冲突
        while (EternalHeartClient.OPEN_PANEL.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null || mc.player == null) continue;
            // 权限的权威判定在服务端；被拒绝时由服务端返回 forbidden 并提示
            ConfigNetwork.requestEditor();
        }
        // 时停：客户端只发请求，是否允许由服务端判定（失败原因走动作栏消息）
        while (EternalHeartClient.TIME_STOP.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null || mc.player == null) continue;
            TimeStopNetwork.request();
        }
    }
}
