package com.eternal_heart.client;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.EternalHeartMod;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.features.EquipTracker;
import com.eternal_heart.integration.Integrations;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 世界渲染路径 —— 给「接管了玩家渲染」的模组兜底，目前针对 YSM（Yes Steve Model）。
 *
 * <p><b>为什么需要它</b>：YSM 会替换玩家的渲染器，原版实体渲染的渲染层
 * （Curios 的 {@code CuriosLayer} 也在其中）因此根本不会执行 —— 表现就是
 * 「装了 YSM 之后看不到光环」。而 YSM 2.x 的 Java 层完全混淆、没有任何对外的
 * 渲染 API（官方扩展 API {@code com.elfmcys.ysm.api} 与 {@code RenderModelEvent}
 * 是 3.0 才引入的），所以这里不去接它的渲染管线，而是在世界渲染的
 * <b>实体阶段之后</b>自己遍历玩家画环：不引用 YSM 的任何类型或方法，
 * 因此对任意 YSM 版本都成立，也不可能因为 YSM 内部改动而链接失败。</p>
 *
 * <p><b>互斥</b>：只在检测到 YSM 时启用。没装 YSM 时由 {@link HaloCurioRenderer}
 * 负责（那条路径跑在原版管线里，能跟随模型骨骼），两者不会同时生效，
 * 因此不会出现「两个光环」。</p>
 */
@Mod.EventBusSubscriber(modid = EternalHeartMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HaloWorldRenderer {

    /** 超过这个距离的玩家不画（平方值，避免每帧开方）。 */
    private static final double MAX_DISTANCE_SQR = 64.0 * 64.0;

    private HaloWorldRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!Integrations.isYsmLoaded()) return;   // 未装 YSM：走 Curios 渲染路径，避免重复渲染
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || level.players().isEmpty()) return;
        if (!ConfigValues.get(EternalHeartConfig.HALO_ENABLED)) return;

        Vec3 camera = event.getCamera().getPosition();
        float partialTicks = event.getPartialTick();
        PoseStack poseStack = event.getPoseStack();

        for (AbstractClientPlayer player : level.players()) {
            if (!shouldRender(minecraft, player, camera)) continue;

            // 位置按渲染插值取，否则实体移动时球会按 20Hz 抖动
            double x = Mth.lerp(partialTicks, player.xOld, player.getX());
            double y = Mth.lerp(partialTicks, player.yOld, player.getY());
            double z = Mth.lerp(partialTicks, player.zOld, player.getZ());

            poseStack.pushPose();
            // 世界坐标 → 相机空间（世界渲染阶段的 PoseStack 原点在相机处）
            poseStack.translate(x - camera.x, y - camera.y, z - camera.z);
            // 让轨道朝向对齐身体（Curios 那条路径由原版实体渲染代劳）
            poseStack.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot)));
            // 顶点缓冲由 HaloGeometry 自管（自带大缓冲、画完即时 flush），这里不掺和
            HaloGeometry.render(poseStack, player, partialTicks);
            poseStack.popPose();
        }
    }

    /** 便宜的条件先判（隐形 / 视角 / 距离），最后才查饰品栏。 */
    private static boolean shouldRender(Minecraft minecraft, AbstractClientPlayer player, Vec3 camera) {
        if (player.isInvisible() || player.isSpectator()) return false;
        // 第一人称下不画自己的光环：看不到自己的模型，凭空浮一个环只会挡视线
        if (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) return false;
        if (player.distanceToSqr(camera.x, camera.y, camera.z) > MAX_DISTANCE_SQR) return false;
        return EquipTracker.queryDefinite(player);
    }
}
