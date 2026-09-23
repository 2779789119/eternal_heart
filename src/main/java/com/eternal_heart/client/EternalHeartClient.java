package com.eternal_heart.client;

import com.eternal_heart.EternalHeartMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端入口（仅客户端加载）。
 * 注册「打开配置面板」与「时停」两个快捷键，并交由 {@link EternalHeartKeyHandler} 处理按下事件。
 */
@Mod.EventBusSubscriber(modid = EternalHeartMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class EternalHeartClient {

    /** 打开配置面板的快捷键，默认 + 键（主键盘 =/+ 键位） */
    public static final KeyMapping OPEN_PANEL = new KeyMapping(
            "key.eternal_heart.open_panel",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_EQUAL,
            "key.categories.eternal_heart"
    );

    /** 发动时停的快捷键，默认 G 键（发动与校验都在服务端，见 TimeStopNetwork） */
    public static final KeyMapping TIME_STOP = new KeyMapping(
            "key.eternal_heart.time_stop",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.eternal_heart"
    );

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        com.eternal_heart.network.ConfigNetwork.setClientReceiver(ClientConfigState::receive);
        // 时停状态必须同步到客户端：否则弹射物会在本地继续飞（客户端也会 tick 实体）
        com.eternal_heart.network.TimeStopNetwork.setClientReceiver(sync ->
                com.eternal_heart.features.TimeStop.applySync(
                        sync.dimension(), sync.remaining(), sync.caster(), sync.center(), sync.clear()));
        event.register(OPEN_PANEL);
        event.register(TIME_STOP);
    }

    /**
     * 注册「永恒之心·碎影」屏幕覆盖层（手持/佩戴饰品时的碎片层 + 按住 Shift 聚合出数据面板）。
     *
     * <p>用 {@code registerBelowAll}：碎片层画在<b>所有原版 HUD 之下</b> —— 血条、饥饿、经验、
     * 物品栏、聊天都盖在碎片上面，不会再出现碎片压在物品栏/血条上的情况
     * （用户 2026-09-23："把碎片显示弄在血条和物品栏以下，不能覆盖物品栏与血条"）。</p>
     */
    @SubscribeEvent
    public static void registerGuiOverlays(net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) {
        event.registerBelowAll("eternal_heart_shards", new ShardVeilOverlay());
    }
}
