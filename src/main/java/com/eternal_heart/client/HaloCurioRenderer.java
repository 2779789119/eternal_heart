package com.eternal_heart.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Curios 渲染路径：佩戴「永恒之心」时在玩家周围绘制光球 / 光环。
 *
 * <p>走 {@link ICurioRenderer} 的好处是只在<b>真正佩戴</b>时被调用（拿在手上、放在背包里都不会出现），
 * 并且跑在原版实体渲染管线里 —— 自动跟随模型骨骼、被方块正确遮挡。</p>
 *
 * <p><b>坐标系（关键）</b>：Curios 的 {@code CuriosLayer} 只做 pushPose → 调渲染器 → popPose，
 * 中间不做任何位移，所以这里拿到的是<b>原版实体渲染的模型空间</b>，它有两个坑：</p>
 * <ol>
 *   <li>此前已执行 {@code scale(-1,-1,1)}，<b>+Y 朝下</b>；</li>
 *   <li>此前还执行过 {@code translate(0,-1.501,0)}（原版为了让模型脚底对齐实体原点），
 *       于是<b>原点并不在脚底，而在世界高度 1.501 处</b>（玩家胸口 / 脖子一带）。</li>
 * </ol>
 * <p>因此这里做两步还原：先 {@code scale(1,-1,1)} 把 Y 轴翻正，再 {@code translate(0,-1.501,0)}
 * 把原点挪到脚底 —— 之后交给 {@link HaloGeometry} 的坐标就真正是「脚底为原点、+Y 向上」，
 * 配置里「离脚底的高度」也才名副其实（历史上缺了第二步，导致填 0 会落在脖子上）。</p>
 *
 * <p>注意：装了 YSM（Yes Steve Model）这类接管玩家渲染的模组时，原版渲染层不会执行，
 * 本类也就不会被调用 —— 那种情况由 {@link HaloWorldRenderer} 兜底，两者互斥注册。</p>
 */
public class HaloCurioRenderer implements ICurioRenderer {

    /**
     * 原版实体渲染为对齐模型脚底而施加的固定位移（{@code LivingEntityRenderer} 里写死的 1.501）。
     * 用来把「模型空间原点」落回实体脚底。
     */
    private static final float MODEL_ORIGIN_OFFSET = 1.501F;

    /** 单例：Curios 的渲染器注册接受 Supplier，实例无状态可安全复用。 */
    public static final HaloCurioRenderer INSTANCE = new HaloCurioRenderer();

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext,
                                                                          PoseStack poseStack,
                                                                          RenderLayerParent<T, M> renderLayerParent,
                                                                          MultiBufferSource buffer, int packedLight,
                                                                          float limbSwing, float limbSwingAmount,
                                                                          float partialTicks, float ageInTicks,
                                                                          float netHeadYaw, float headPitch) {
        LivingEntity wearer = slotContext.entity();
        if (wearer == null) return;

        poseStack.pushPose();
        // ① 模型空间 Y 轴朝下 → 翻正（X 轴同时处于镜像状态，但球体与圆环都关于 X 对称，视觉无影响）
        poseStack.scale(1.0F, -1.0F, 1.0F);
        // ② 原点原本在世界高度 1.501 → 下移回脚底
        poseStack.translate(0.0, -MODEL_ORIGIN_OFFSET, 0.0);
        HaloGeometry.render(poseStack, wearer, partialTicks);
        poseStack.popPose();
    }
}
