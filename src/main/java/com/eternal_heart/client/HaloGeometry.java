package com.eternal_heart.client;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 头顶光环 / 环绕光球的几何与动画本体 —— 与「由谁触发渲染」完全解耦。
 *
 * <p>两种样式共用同一套外观参数（颜色 / 彩虹 / 半径 / 旋转速度），由配置项
 * {@code haloOrbs} 切换：</p>
 * <ul>
 *   <li><b>光球（默认）</b>：若干发光球体均匀分布在一个<b>以玩家中点为球心</b>的圆轨道上，
 *       整体缓慢自转，每个球身后带一串渐小渐淡的拖影。</li>
 *   <li><b>圆环</b>：头顶上方一圈悬浮的圆环体（甜甜圈），是 1.23.0 的形态。</li>
 * </ul>
 *
 * <p><b>坐标系约定</b>：调用前 PoseStack 必须已经定位到<b>实体脚底</b>，且 <b>+Y 向上</b>
 * （世界方向）。两条渲染路径各自负责把自己的坐标系折算成这个约定：</p>
 * <ul>
 *   <li>{@link HaloCurioRenderer}：Curios 渲染层拿到的是 Y 轴朝下、<b>原点在世界高度 1.501</b>
 *       的模型空间，需要翻转 + 下移 1.501 才能落到脚底；</li>
 *   <li>{@link HaloWorldRenderer}：世界渲染阶段拿到的本来就是「相机原点 + Y 向上」，直接平移即可。</li>
 * </ul>
 *
 * <p>顶点一律用 {@code POSITION_COLOR}（无纹理、无光照贴图），因此「立体感」必须自己算：
 * 光环靠逐段色相铺彩虹；光球则用<b>多层同心球</b>制造径向渐变 ——
 * 从任何角度看，视线穿过球心的路径都最长，中心自然叠加得最亮、边缘最淡，
 * 于是不必知道相机朝向就能得到「中心发亮、边缘柔和」的发光球观感。
 * （1.25.0 曾用顶点法线的方向光照，等于把饱和度 1 的纯色压到 32% 亮度，
 * 结果是一块暗色的多边形 —— 发光体不该被「照亮」，它自己就是光源。）</p>
 *
 * <p>缓冲自管：不借用调用方的 {@code MultiBufferSource}，而是用本类自带的
 * {@link BufferBuilder}（按 8 球满配 + 拖影的最坏情况估算容量），画完立即 {@code endBatch}
 * —— 原版默认缓冲只有 1536 顶点，球数 / 拖影调高时会溢出。</p>
 */
final class HaloGeometry {

    // ==================== 圆环样式 ====================

    /** 主环方向分段数：越高越圆滑（72 ≈ 每 5° 一段，肉眼已足够平滑）。 */
    private static final int RING_SEGMENTS = 72;
    /** 管截面分段数：圆环的「厚度」方向（6 边形截面在小粗细下看不出棱角）。 */
    private static final int TUBE_SEGMENTS = 6;
    /** 光环整体倾斜角（度）：完全水平的光环从正侧面会被看成一条线，倾斜一点更立体。 */
    private static final float TILT_DEGREES = 12.0F;

    // ==================== 光球样式 ====================

    /**
     * 光球的一层同心球壳。
     *
     * @param scale      相对球半径的倍率（&gt;1 在球体外面，&lt;1 在球体里面）
     * @param alpha      该层的不透明度
     * @param latitudes  纬线分段
     * @param longitudes 经线分段
     * @param highlight  向白色提亮的比例（0 = 本色，1 = 纯白）
     */
    private record Shell(float scale, float alpha, int latitudes, int longitudes, float highlight) {
    }

    /**
     * 光球的四层同心结构，数组顺序即绘制顺序（由外向内）。
     *
     * <p>之所以不用「一层实心球 + 一层光晕」，是因为那样中心与边缘的颜色一样、只能靠
     * 方向光照硬做明暗；同心多层则天然形成径向渐变：<b>中心同时叠了四层 → 最亮；
     * 最外圈只有最外那层 → 最淡</b>。内两层再向白色提亮，球心就有了「发光」的核心。</p>
     *
     * <p>分段数从外到内递增：外层的轮廓最显眼（曾被 3×4 的分段坑出明显的多边形），
     * 必须够圆；最内的白色高光点很小，粗一点看不出来。</p>
     */
    private static final Shell[] ORB_SHELLS = {
            new Shell(1.90F, 0.16F, 4, 6, 0.00F),   // 最外圈柔光
            new Shell(1.42F, 0.38F, 5, 7, 0.15F),   // 中圈
            new Shell(1.00F, 1.00F, 6, 8, 0.62F),   // 内核（接近不透明）
            new Shell(0.46F, 0.95F, 4, 5, 0.85F),   // 球心高光
    };

    /**
     * 拖影每一级沿轨道后退的弧度，以及逐级缩小的比例（间隔大、缩得慢 = 尾巴更长）。
     *
     * <p><b>间距必须大于球半径</b>，否则拖影会和主球完全重叠、糊成一坨 ——
     * 1.27.4 之前这里是 0.20 弧度（默认半径 0.45 格 → 只后退 0.09 格，不到球的半径），
     * 表现就是「彩色方块糊在一起」，几个球连成一圈像条马赛克彩带。</p>
     */
    private static final float TRAIL_STEP = 0.55F;
    private static final float TRAIL_SHRINK = 0.28F;
    /** 拖影的基础不透明度（再乘上呼吸系数）。 */
    private static final float TRAIL_ALPHA = 0.22F;

    /** 自带顶点缓冲的容量（顶点数）：8 球 ×（四层 ≈127 个四边形 + 拖影 8×20 个）≈ 9000。 */
    private static final int BUFFER_VERTICES = 1 << 14;

    /** 渲染线程专用的复用缓冲，避免每帧分配（渲染线程唯一，无需同步）。 */
    private static BufferBuilder builder;

    /**
     * 使用的渲染类型：原版调试用的 {@code POSITION_COLOR} 四边形渲染 ——
     * 顶点格式为「坐标 + 颜色」（无纹理、无光照贴图），半透明、双面、上传前排序。
     *
     * <p>这里刻意复用原版现成的 RenderType 而不是自建：自建所需的
     * {@code RenderStateShard} 常量在官方映射下是 protected，需要 Access Transformer 或
     * Mixin 才能引用，属于为一个纯视觉特性引入不必要的底层注入。</p>
     */
    static RenderType renderType() {
        return RenderType.debugQuads();
    }

    private static BufferBuilder buffer() {
        if (builder == null) builder = new BufferBuilder(BUFFER_VERTICES);
        return builder;
    }

    /**
     * 在当前 PoseStack 位置绘制永恒之心的环绕特效。
     *
     * @param poseStack    已定位到实体脚底、+Y 向上的姿势栈
     * @param wearer       佩戴者（提供身高与动画时间轴）
     * @param partialTicks 渲染插值，用于让自转 / 呼吸在 60fps 下平滑（不会按 20Hz 卡顿）
     */
    static void render(PoseStack poseStack, LivingEntity wearer, float partialTicks) {
        if (!ConfigValues.get(EternalHeartConfig.HALO_ENABLED)) return;
        if (wearer.isInvisible()) return;

        MultiBufferSource.BufferSource buffer = MultiBufferSource.immediate(buffer());
        VertexConsumer consumer = buffer.getBuffer(renderType());

        if (ConfigValues.get(EternalHeartConfig.HALO_ORBS)) {
            renderOrbs(poseStack, consumer, wearer, partialTicks);
        } else {
            renderRing(poseStack, consumer, wearer, partialTicks);
        }

        buffer.endBatch(renderType());
    }

    // ==================== 光球 ====================

    /**
     * 环绕光球：N 个发光球均匀分布在一个以<b>玩家中点</b>为球心的圆轨道上，整体自转。
     *
     * <p>轨道绕 X 轴倾斜 {@code haloOrbTilt} 度，因此从侧面看球会前后错落，
     * 而不是挤在同一个水平面上（更接近「环绕身体」的观感）。</p>
     *
     * <p>每个球身后再补几级渐小渐淡的拖影，做出「运转中划出光痕」的感觉
     * （拖影方向跟随自转方向，不转时拖影与球重合、自然消失）。</p>
     */
    private static void renderOrbs(PoseStack poseStack, VertexConsumer consumer, LivingEntity wearer,
                                   float partialTicks) {
        // 用光球专属的轨道半径，与圆环样式的「光环半径」分开 —— 两种样式互不干扰
        double radius = ConfigValues.get(EternalHeartConfig.HALO_ORB_RADIUS);
        double height = ConfigValues.get(EternalHeartConfig.HALO_ORB_HEIGHT);
        double size = ConfigValues.get(EternalHeartConfig.HALO_ORB_SIZE);
        int count = ConfigValues.get(EternalHeartConfig.HALO_ORB_COUNT);
        double tilt = ConfigValues.get(EternalHeartConfig.HALO_ORB_TILT);
        int trail = ConfigValues.get(EternalHeartConfig.HALO_ORB_TRAIL);
        double spinSpeed = ConfigValues.get(EternalHeartConfig.HALO_SPIN_SPEED);
        int rgb = ConfigValues.get(EternalHeartConfig.HALO_COLOR);
        boolean rainbow = ConfigValues.get(EternalHeartConfig.HALO_RAINBOW);
        double rainbowSpeed = ConfigValues.get(EternalHeartConfig.HALO_RAINBOW_SPEED);
        int rainbowBands = ConfigValues.get(EternalHeartConfig.HALO_RAINBOW_BANDS);

        float time = wearer.tickCount + partialTicks;
        // 呼吸：球心上下浮动 ±0.03 格；最外圈柔光与拖影的亮度一起起伏（内核保持稳定）
        float breathe = (float) Math.sin(time / 14.0);
        float pulse = 0.85F + 0.15F * breathe;

        float red = ((rgb >> 16) & 0xFF) / 255.0F;
        float green = ((rgb >> 8) & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;
        // 彩虹模式下按球的序号铺色相：条数决定「一圈球铺几条色带」，同样必须是整数
        float hueDrift = rainbow ? (float) (rainbowSpeed * time / 20.0) : 0.0F;

        // 拖影方向跟随自转方向；不转（速度为 0）时拖影与球重合，看不出多余的东西
        float trailDirection = spinSpeed >= 0.0 ? 1.0F : -1.0F;

        poseStack.pushPose();
        // 球心 = 玩家中点（默认 0.9 格 ≈ 1.8 身高的一半）
        poseStack.translate(0.0, height + 0.03 * breathe, 0.0);
        poseStack.mulPose(Axis.XP.rotationDegrees((float) tilt));
        poseStack.mulPose(Axis.YP.rotationDegrees((float) (spinSpeed * time / 20.0)));

        for (int i = 0; i < count; i++) {
            float angle = (float) (i * Math.PI * 2.0 / count);
            float orbRed = red;
            float orbGreen = green;
            float orbBlue = blue;
            if (rainbow) {
                int packed = RainbowPalette.hueToRgb(hueDrift + (float) rainbowBands * i / count);
                orbRed = ((packed >> 16) & 0xFF) / 255.0F;
                orbGreen = ((packed >> 8) & 0xFF) / 255.0F;
                orbBlue = (packed & 0xFF) / 255.0F;
            }

            // 先画拖影（从最远的一级往回画），最后画主体，保证主体压在最上层
            for (int k = trail; k >= 1; k--) {
                // 符号很关键：自转用的是 mulPose(Y, +spin)，它把球的有效角度拉向 θ - spin，
                // 也就是球朝「角度减小」的方向转；拖影要落在球刚经过的那一侧（角度更大处），
                // 所以这里是加号。写成减号会把拖影甩到球的前方，看起来像球在倒着走
                //（1.26.0 的观感问题）。
                float back = angle + trailDirection * TRAIL_STEP * k;
                // 逐级缩小 / 变淡；缩得比初版慢，尾巴才拉得出来
                double shrunk = size * Math.max(0.30, 1.0 - TRAIL_SHRINK * k);
                float faded = TRAIL_ALPHA * pulse / (1.0F + 0.35F * k);

                poseStack.pushPose();
                poseStack.translate(Math.cos(back) * radius, 0.0, Math.sin(back) * radius);
                drawSphere(poseStack.last().pose(), consumer, (float) shrunk,
                        4, 5, orbRed, orbGreen, orbBlue, faded);
                poseStack.popPose();
            }

            poseStack.pushPose();
            poseStack.translate(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            drawOrb(poseStack.last().pose(), consumer, (float) size, orbRed, orbGreen, orbBlue, pulse);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    /**
     * 一个光球 = 四层同心球壳，按 {@link #ORB_SHELLS} 的顺序由外向内绘制。
     *
     * @param pulse 呼吸系数：只作用于最外圈柔光，内核不跟着忽明忽暗，否则会像在「闪」
     */
    private static void drawOrb(Matrix4f matrix, VertexConsumer consumer, float radius,
                                float red, float green, float blue, float pulse) {
        for (int i = 0; i < ORB_SHELLS.length; i++) {
            Shell shell = ORB_SHELLS[i];
            float lightness = shell.highlight();
            drawSphere(matrix, consumer, radius * shell.scale(), shell.latitudes(), shell.longitudes(),
                    toWhite(red, lightness), toWhite(green, lightness), toWhite(blue, lightness),
                    i == 0 ? shell.alpha() * pulse : shell.alpha());
        }
    }

    /** 向白色提亮：{@code highlight} = 0 保持本色，= 1 变成纯白。 */
    private static float toWhite(float channel, float highlight) {
        return channel + (1.0F - channel) * highlight;
    }

    /**
     * 以原点为球心画一个球体网格（经线 × 纬线，每格一个四边形）。
     *
     * <p>颜色不再做任何方向光照：球体的「立体感」由多层球壳叠加产生，
     * 见 {@link #ORB_SHELLS} 的说明。</p>
     */
    private static void drawSphere(Matrix4f matrix, VertexConsumer consumer, float radius,
                                   int latitudes, int longitudes,
                                   float red, float green, float blue, float alpha) {
        for (int lat = 0; lat < latitudes; lat++) {
            float phi0 = (float) (Math.PI * lat / latitudes);
            float phi1 = (float) (Math.PI * (lat + 1) / latitudes);
            float sinPhi0 = (float) Math.sin(phi0);
            float cosPhi0 = (float) Math.cos(phi0);
            float sinPhi1 = (float) Math.sin(phi1);
            float cosPhi1 = (float) Math.cos(phi1);

            for (int lon = 0; lon < longitudes; lon++) {
                float theta0 = (float) (Math.PI * 2.0 * lon / longitudes);
                float theta1 = (float) (Math.PI * 2.0 * (lon + 1) / longitudes);
                float cosT0 = (float) Math.cos(theta0);
                float sinT0 = (float) Math.sin(theta0);
                float cosT1 = (float) Math.cos(theta1);
                float sinT1 = (float) Math.sin(theta1);

                sphereVertex(consumer, matrix, sinPhi0, cosPhi0, cosT0, sinT0, radius, red, green, blue, alpha);
                sphereVertex(consumer, matrix, sinPhi0, cosPhi0, cosT1, sinT1, radius, red, green, blue, alpha);
                sphereVertex(consumer, matrix, sinPhi1, cosPhi1, cosT1, sinT1, radius, red, green, blue, alpha);
                sphereVertex(consumer, matrix, sinPhi1, cosPhi1, cosT0, sinT0, radius, red, green, blue, alpha);
            }
        }
    }

    /** 球面网格的一个顶点：位置 = 单位法线 × 半径，颜色直接取传入的本色。 */
    private static void sphereVertex(VertexConsumer consumer, Matrix4f matrix,
                                     float sinPhi, float cosPhi, float cosTheta, float sinTheta,
                                     float radius, float red, float green, float blue, float alpha) {
        float nx = sinPhi * cosTheta;
        float ny = cosPhi;
        float nz = sinPhi * sinTheta;

        consumer.vertex(matrix, nx * radius, ny * radius, nz * radius)
                .color(red, green, blue, alpha)
                .endVertex();
    }

    // ==================== 圆环 ====================

    /** 经典形态：头顶上方一圈悬浮的圆环体。 */
    private static void renderRing(PoseStack poseStack, VertexConsumer consumer, LivingEntity wearer,
                                   float partialTicks) {
        double radius = ConfigValues.get(EternalHeartConfig.HALO_RADIUS);
        double height = ConfigValues.get(EternalHeartConfig.HALO_HEIGHT);
        double thickness = ConfigValues.get(EternalHeartConfig.HALO_THICKNESS);
        double spinSpeed = ConfigValues.get(EternalHeartConfig.HALO_SPIN_SPEED);
        int rgb = ConfigValues.get(EternalHeartConfig.HALO_COLOR);
        boolean rainbow = ConfigValues.get(EternalHeartConfig.HALO_RAINBOW);
        double rainbowSpeed = ConfigValues.get(EternalHeartConfig.HALO_RAINBOW_SPEED);
        int rainbowBands = ConfigValues.get(EternalHeartConfig.HALO_RAINBOW_BANDS);

        float time = wearer.tickCount + partialTicks;
        // 呼吸：半径 ±4%、亮度 ±0.12、上下浮动 ±0.03 格
        float breathe = (float) Math.sin(time / 14.0);
        float alpha = 0.78F + 0.12F * breathe;
        double floatOffset = 0.03 * breathe;

        float red = ((rgb >> 16) & 0xFF) / 255.0F;
        float green = ((rgb >> 8) & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;
        // 彩虹模式的色相基准：hueDrift 让整条色带随时间流动（圈/秒 × 秒），
        // hueSpread 是「一圈铺几条色带」——必须是整数，否则色环首尾接不上，
        // 会出现一条从某色直接跳到互补色的硬边（1.21.1 的观感问题就出在这里）
        float hueDrift = rainbow ? (float) (rainbowSpeed * time / 20.0) : 0.0F;
        float hueSpread = rainbow ? rainbowBands : 0.0F;

        double major = Math.max(0.02, radius * (1.0 + 0.04 * breathe));
        double minor = Math.max(0.005, thickness / 2.0);

        poseStack.pushPose();
        poseStack.translate(0.0, wearer.getBbHeight() + height + floatOffset, 0.0);
        poseStack.mulPose(Axis.XP.rotationDegrees(TILT_DEGREES));
        poseStack.mulPose(Axis.YP.rotationDegrees((float) (spinSpeed * time / 20.0)));
        // 顶点坐标按「外径 = 1」归一化，这里统一缩放到实际尺寸（三轴同缩放，管粗细才不会被压扁）
        poseStack.scale((float) (major + minor), (float) (major + minor), (float) (major + minor));

        drawTorus(poseStack.last().pose(), consumer,
                (float) (major / (major + minor)), (float) (minor / (major + minor)),
                red, green, blue, rainbow, hueDrift, hueSpread, alpha);

        poseStack.popPose();
    }

    /**
     * 绘制一圈圆环体（torus）。
     *
     * <p>在 XZ 平面上生成完整网格：主环 72 段 × 管截面 6 段，每段一个四边形。
     * 坐标已按「外径 = 1」归一化，调用方通过 {@code poseStack.scale} 控制实际大小，
     * 这样顶点数量与尺寸无关，缩放不会改变几何精度。</p>
     *
     * @param matrix    当前姿势矩阵
     * @param consumer  顶点写入目标
     * @param ringRatio 主环半径相对外径的比例（内缩量由管粗细决定）
     * @param tubeRatio 管截面半径相对外径的比例
     * @param red       固定色模式下的基准颜色（彩虹模式下被逐段色相覆盖）
     * @param rainbow   是否按色相逐段取色
     * @param hueDrift  彩虹色相的全局漂移量（随时间推进 = 整条色带在环上流动）
     * @param hueSpread 一圈光环铺多少条色带（整数；0 = 整环同色、只随时间整体变色）
     */
    private static void drawTorus(Matrix4f matrix, VertexConsumer consumer,
                                  float ringRatio, float tubeRatio,
                                  float red, float green, float blue,
                                  boolean rainbow, float hueDrift, float hueSpread, float alpha) {
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double angleA = i * Math.PI * 2.0 / RING_SEGMENTS;
            double angleB = (i + 1) * Math.PI * 2.0 / RING_SEGMENTS;
            float cosA = (float) Math.cos(angleA);
            float sinA = (float) Math.sin(angleA);
            float cosB = (float) Math.cos(angleB);
            float sinB = (float) Math.sin(angleB);

            // 每个四边形取一个色相（段中点），同一段四角同色：
            // 段宽只有 5°，足够平滑，同时避免了四角异色在 QUADS 拆分时的对角插值伪影
            float segRed = red;
            float segGreen = green;
            float segBlue = blue;
            if (rainbow) {
                int packed = RainbowPalette.hueToRgb(hueDrift + hueSpread * (i + 0.5F) / RING_SEGMENTS);
                segRed = ((packed >> 16) & 0xFF) / 255.0F;
                segGreen = ((packed >> 8) & 0xFF) / 255.0F;
                segBlue = (packed & 0xFF) / 255.0F;
            }

            for (int j = 0; j < TUBE_SEGMENTS; j++) {
                double tubeA = j * Math.PI * 2.0 / TUBE_SEGMENTS;
                double tubeB = (j + 1) * Math.PI * 2.0 / TUBE_SEGMENTS;
                float cosTA = (float) Math.cos(tubeA);
                float sinTA = (float) Math.sin(tubeA);
                float cosTB = (float) Math.cos(tubeB);
                float sinTB = (float) Math.sin(tubeB);

                // 管截面上四个角点 → 相邻两段主环之间的四边形
                torusVertex(consumer, matrix, cosA, sinA, cosTA, sinTA, ringRatio, tubeRatio, segRed, segGreen, segBlue, alpha);
                torusVertex(consumer, matrix, cosB, sinB, cosTA, sinTA, ringRatio, tubeRatio, segRed, segGreen, segBlue, alpha);
                torusVertex(consumer, matrix, cosB, sinB, cosTB, sinTB, ringRatio, tubeRatio, segRed, segGreen, segBlue, alpha);
                torusVertex(consumer, matrix, cosA, sinA, cosTB, sinTB, ringRatio, tubeRatio, segRed, segGreen, segBlue, alpha);
            }
        }
    }

    /** 圆环体表面的一点：主环角度 (cosA,sinA) × 管截面角度 (cosT,sinT)，结果落在 XZ 平面上。 */
    private static void torusVertex(VertexConsumer consumer, Matrix4f matrix,
                                    float cosA, float sinA, float cosT, float sinT,
                                    float ringRatio, float tubeRatio,
                                    float red, float green, float blue, float alpha) {
        float radial = ringRatio + tubeRatio * cosT;
        consumer.vertex(matrix, radial * cosA, tubeRatio * sinT, radial * sinA)
                .color(red, green, blue, alpha)
                .endVertex();
    }
}
