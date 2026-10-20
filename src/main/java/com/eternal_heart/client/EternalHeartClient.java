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

    /** 发动 / 关闭时停的快捷键，默认 G 键（切换与校验都在服务端，见 TimeStopNetwork） */
    public static final KeyMapping TIME_STOP = new KeyMapping(
            "key.eternal_heart.time_stop",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.eternal_heart"
    );

    /**
     * 注册「头顶光环」饰品渲染器。
     *
     * <p>Curios 只在物品真正佩戴于饰品栏时调用渲染器 —— 因此无需额外判定装备状态，
     * 拿在手上 / 放在背包里都不会出现光环，卸下即刻消失。</p>
     *
     * <p><b>装了 YSM 时跳过注册</b>：YSM 接管玩家渲染，原版渲染层根本不会执行，
     * 注册了也是死代码；此时改由 {@link HaloWorldRenderer} 在世界渲染阶段自绘。
     * 两条路径互斥，避免「玩家用原版模型」时同时画出两个光环。</p>
     */
    @SubscribeEvent
    public static void registerCurioRenderers(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        if (com.eternal_heart.integration.Integrations.isYsmLoaded()) return;
        event.enqueueWork(() -> top.theillusivec4.curios.api.client.CuriosRendererRegistry.register(
                EternalHeartMod.ETERNAL_HEART.get(), () -> HaloCurioRenderer.INSTANCE));
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        com.eternal_heart.network.ConfigNetwork.setClientReceiver(ClientConfigState::receive);
        // 融合开关：服务端拒绝时界面要把开关翻回去
        com.eternal_heart.network.FusionNetwork.setClientReceiver(FusionToggleScreen::receive);
        // 时停状态必须同步到客户端：否则弹射物会在本地继续飞（客户端也会 tick 实体）
        com.eternal_heart.network.TimeStopNetwork.setClientReceiver(sync ->
                com.eternal_heart.features.TimeStop.applySync(
                        sync.dimension(), sync.remaining(), sync.caster(), sync.center(), sync.clear()));
        event.register(OPEN_PANEL);
        event.register(TIME_STOP);
    }
}
