package com.eternal_heart.client;

import com.eternal_heart.EternalHeartMod;
import com.eternal_heart.features.EquipTracker;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 「永恒之心·碎影」—— 白色细线框玻璃碎片 + 按住 Shift 聚合成数据面板。
 *
 * <p><b>两种分布</b>：</p>
 * <ol>
 *   <li><b>外围公转</b>（HUD / 手持 / 饰品栏位佩戴）：每片碎片绕屏幕中心转，轨迹是在
 *       <b>中心的空心圆</b>（{@link #HOLE_FRAC}）与<b>屏幕边框那个四边形</b>之间、
 *       按每片自己的深度 {@code t} 插值出来的 —— {@code t=0} 是正圆（贴着空心圆），
 *       {@code t=1} 就是屏幕边框本身，中间值 = 圆角矩形。用矩形的极径
 *       （{@link #frameExtent}）当外沿，碎片才会真正贴边且<b>四角不留空隙</b>；
 *       一圈 {@link #ORBIT_LAP} 秒。</li>
 *   <li><b>环绕悬停物</b>（界面里指针指着的永恒之心）：碎片绕着<b>被悬停的那个物品格</b>排成一个环
 *       慢速公转 —— 用户："所有碎片围成一个圈，以永恒之心为中心"。
 *       环心由 {@link ShardVeilScreenHook} 传进来，那里会把 1.20.1 的
 *       {@code Slot#x/#y}（<b>相对 GUI 原点</b>，不是屏幕绝对坐标）换算成屏幕坐标。</li>
 * </ol>
 *
 * <p><b>谁能出面板</b>：只有<b>手持</b>或<b>界面里悬停</b>时按住 Shift 才聚合；饰品栏位（Curios）
 * 佩戴只出碎片、不出面板（用户 2026-09-23 明确）。</p>
 *
 * <p><b>Shift 检测必须读真实按键</b>：界面打开时按键事件被界面接管，
 * {@code mc.player.isShiftKeyDown()} 恒为 false —— 之前"按了 Shift 面板也不聚合"就是这么来的。
 * 见 {@link #shiftDown()}。</p>
 *
 * <p><b>面板阶段为什么拼得成一整块</b>：分格只取碎片总数的<b>因数对</b>（cols×rows 恒等于总数，
 * 每格都有片）；位置严格落在格心，不规则感由每片自己的半宽/半高倍数（恒 &gt; 1）表达 →
 * 并集恒等于整块；填充近不透明 → 板面颜色一致；最外圈朝外的边精确压在面板边界上；四角斜切出圆角。</p>
 *
 * <p><b>致命细节</b>：本类走裸 BufferBuilder 通道，<b>必须自己 {@code disableCull()}</b>。
 * vanilla GUI 靠 {@code RenderType.GUI} 自带 NO_CULL；这里漏掉的话，以中心为扇心的填充三角形
 * 在 NDC 里是顺时针（背面）会被整片剔除 —— 只剩描边，看起来就是"碎片拼不成一块"。</p>
 */
public final class ShardVeilOverlay implements IGuiOverlay {

    // ───────────────────────── 调参区 ─────────────────────────

    /** 三层碎片数量（远/中/近），总和 1500 —— 面板分格取它的因数。 */
    private static final int COUNT_FAR = 700;
    private static final int COUNT_MID = 500;
    private static final int COUNT_NEAR = 300;
    /**
     * 碎片加量倍数（用户 2026-09-23："把拿手上显示的圆碎片数量乘2"，接着"最后再把方碎片数量乘2"）。
     * 做法：按每层一半的量，各补一批"纯圆形"和一批"纯四边形"碎片 →
     * 圆形 ≈750 → 1500、四边形 750 → 1500，总计从 1500 加到 3000 片。
     * 补出来的那批带 {@code Shard.extra} 标记，不进悬停环（那一圈的密度是认可过的）。
     */
    private static final float EXTRA_SHARE = 0.5F;

    /**
     * 每层基础透明度。0.50/0.70/0.90（照参考视频实测灰阶）→ 0.38/0.54/0.72 → <b>0.30/0.42/0.58</b>
     * （用户 2026-09-23 连续两次降透明："将所有碎片再透明一点"、"最后碎片再透明点"）。
     */
    private static final float[] LAYER_ALPHA = {0.30F, 0.42F, 0.58F};
    /**
     * 碎片颜色 = <b>赤橙黄绿青蓝紫七彩循环</b>
     * （用户 2026-09-23："要改成七色的，赤橙黄绿青蓝紫七色…记得是七彩"）。
     *
     * <p>色相 = 时间 × 速度 + 每片自己的相位；{@link #hsvToRgb} 内部取小数部分，
     * 色相自动环绕整个色轮 —— 每片依次走过 赤→橙→黄→绿→青→蓝→紫，
     * 而各片速度/起点都不同，所以同一时刻整片碎片本身就是七彩的。</p>
     *
     * <p>速度单位是 <b>圈/秒</b>：0.17~0.23 → <b>一整圈七彩约 4.3~5.9 秒</b>
     * （用户 2026-09-23："颜色变换速度不够快，5 秒一个轮回"）。</p>
     */
    private static final float HUE_SPEED_LO = 0.17F;
    private static final float HUE_SPEED_HI = 0.23F;
    /** 饱和度区间：低饱和才是"淡彩"（用户："颜色不能太重"），明度恒为 1。 */
    private static final float SAT_LO = 0.30F;
    private static final float SAT_HI = 0.58F;

    /**
     * 碎片分两类（用户 2026-09-23："没带饰品栏之前手持只显示圆形的碎片，隐藏四边形的碎片；
     * 戴上饰品栏位之后隐藏圆形碎片，只显示四边形的碎片"）：
     *
     * <ul>
     *   <li><b>圆形碎片</b>（{@code quad=false}）：深度 t 小 → 轨迹还接近正圆，
     *       绕在中心空心圆外面一小圈。<b>手持时只显示这一半。</b></li>
     *   <li><b>四边形碎片</b>（{@code quad=true}）：深度 t 接近 1 → 轨迹就是屏幕边框那个四边形。
     *       <b>戴在饰品栏位时只显示这一半</b>，而且要非常小 —— 用户："四边形的宽和高只有物品栏
     *       的一半长度，所以碎片也得很小才行"（一个物品格 18 GUI 单位、一半 = 9；而碎片的宽
     *       ≈ 2 × size × stretch，所以 size 得压到 1.1~2.4）。</li>
     * </ul>
     */
    private static final float QUAD_SHARE = 0.50F;
    /** 四边形碎片：贴边框的窄带，t=1 正好压在边框上，>1 就有一点在屏幕外（"不是所有碎片都在屏幕内"）。 */
    private static final float QUAD_T_MIN = 0.80F;
    private static final float QUAD_T_MAX = 1.10F;
    private static final float QUAD_BIAS = 0.60F;
    private static final float QUAD_SIZE_MIN = 1.10F;
    private static final float QUAD_SIZE_MAX = 2.40F;
    private static final float QUAD_EDGE_MIN = 0.40F;
    private static final float QUAD_EDGE_MAX = 0.60F;
    /**
     * 圆形碎片（手持时显示）：<b>1.19.17 那套正圆轨道</b> —— 基准半径 = 半屏幕对角线 × 1.15，
     * 每片自己的半径 = [0.34, 1] × 基准（pow 偏置 → 越靠外越密）。
     * 用户 2026-09-23 特意把 1.19.17 装回去录了视频："只将圆碎片改成这样，记住只改形状"。
     *
     * <p>这是个<b>真圆</b>：x / y 同一个半径、也不跟着屏幕边框走。基准 434 GUI 比半宽 320 大，
     * 所以外圈有一部分碎片本来就在屏幕外（这就是 1.19.17 的样子）；四角也不会有碎片。</p>
     */
    private static final float HELD_ORBIT_MAX = 1.15F;
    private static final float HELD_RADIUS_MIN = 0.34F;
    private static final float HELD_RADIUS_BIAS = 0.75F;
    /** 尺寸 / 描边档位（内沿 → 外沿）= 1.19.17 原值。 */
    private static final float HELD_SIZE_IN = 2.2F;
    private static final float HELD_SIZE_OUT = 8.0F;
    private static final float HELD_EDGE_IN = 0.55F;
    private static final float HELD_EDGE_OUT = 0.95F;
    /**
     * 中间的空心圆：半径 = 半屏<b>短边</b> × 该值。
     * 0.667 → 屏幕上下各留出内侧 2/3 当空心，而且空心是<b>正圆</b>
     * （用户 2026-09-23："形状像椭圆，将他改为圆"）。
     */
    private static final float HOLE_FRAC = 0.667F;
    /**
     * 公转一圈的秒数（用户 2026-09-23："还是 20 秒一圈"），
     * 每片再乘 0.85~1.15 的随机差异 → 每片 17~23.5 秒一圈，不会像联动的齿轮。
     *
     * <p>轨迹：每片在"中心空心圆 ↔ 屏幕边框"之间按自己的深度 t 插值 ——
     * t 小的走正圆，t=1 就是屏幕边框那个四边形（矩形的极径在四角方向最长，
     * 所以四边形轨迹是唯一能让四角不留空隙的形状）。</p>
     */
    private static final float ORBIT_LAP = 20.0F;
    private static final float ORBIT_JITTER_LO = 0.85F;
    private static final float ORBIT_JITTER_HI = 1.15F;
    /** 轨道圆心偏移（让环不是死正的）。 */
    private static final float ORBIT_ECCENTRIC = 0.06F;
    /** 悬停环绕模式的统一放大倍率：那一圈的外观是用户认可过的，不能因为分类变小。 */
    private static final float RING_SIZE_MUL = 2.2F;

    /**
     * 环绕模式：环带内半径与厚度（GUI 单位）。物品格 18×18。
     *
     * <p>2026-09-23 第二次放大 —— 用户："小环再大一点，碎片再散开一点"：
     * 内半径 30~40 → <b>44~58</b>，环带厚度 16~46 → <b>30~80</b>（散开感主要靠厚度）。</p>
     */
    private static final float RING_IN_MIN = 44.0F;
    private static final float RING_IN_MAX = 58.0F;
    private static final float RING_THICK_MIN = 30.0F;
    private static final float RING_THICK_MAX = 80.0F;
    /** 环内径向散布系数区间（拿 s.tint 当随机源）：让碎片别咬在一条细环上。 */
    private static final float RING_SCATTER_LO = 0.86F;
    private static final float RING_SCATTER_HI = 1.14F;
    /** 环绕公转角速度区间（弧度/秒）：一圈 25~90 秒。 */
    private static final float RING_OMEGA_MIN = 0.07F;
    private static final float RING_OMEGA_MAX = 0.25F;
    /** 环绕模式平时只画 1/N（避免糊成一圈白），聚合时全量出现。 */
    private static final int RING_STRIDE = 2;

    /** 聚合时序。 */
    private static final long CONVERGE_BASE_MS = 1180L;
    private static final long CONVERGE_JITTER_MS = 700L;
    private static final long LAYER_STAGGER_MS = 170L;
    private static final long PER_SHARD_JITTER_MS = 300L;
    private static final float CONVERGE_TWIST = 0.45F;

    /** 摊平（形状插值成方板）的起点：k 超过它才开始摊。 */
    private static final float FLATTEN_FROM = 0.42F;
    /**
     * 每片瓷砖的半宽/半高倍数区间。恒 ≥ 1 → 每片必然覆盖自己那一格（拼不成整块的第一保证）。
     * 不规则感全部由这里表达：位置不动，尺寸各异 → 拼缝错落，像玻璃碎块。
     */
    private static final float PIECE_GROW_MIN = 1.06F;
    private static final float PIECE_GROW_MAX = 1.16F;
    /**
     * 面板填充色：<b>浅色磨砂玻璃</b>（照参考视频：半透明乳白/淡蓝玻璃板 + 浅色细边 + 彩色行）。
     */
    private static final float PANEL_R = 0.62F;
    private static final float PANEL_G = 0.67F;
    private static final float PANEL_B = 0.78F;
    /**
     * 面板浓度 <b>0.82</b>（"一点点透明"，用户 2026-09-23）。
     *
     * <p><b>为什么板面必须画成一整块 quad、不能逐片瓷砖填</b>：瓷砖之间会重叠（grow ≥ 1.06），
     * 重叠处 alpha 是 1-(1-a)ⁿ，重叠数逐格不同 → 板面就是"一块深一块浅的方块"（1.19.21 的毛病）；
     * 而 alpha=1 又不透明。想同时要"均匀 + 半透明"，只有整块画一条路 ——
     * 见 {@code drawShards} 里的 {@code plateQuad()}。</p>
     */
    private static final float PANEL_FILL = 0.82F;
    /** 内部碎片之间的细缝 <b>0（不画）</b>：只留最外圈那圈亮边（用户 2026-09-23）。 */
    private static final float SEAM_ALPHA = 0.00F;
    /** 面板四角斜切半径（占一格短边的比例）——与最外圈瓷砖的斜切一致。 */
    private static final float PLATE_CORNER = 0.30F;
    private static final float SEAM_WIDTH = 0.6F;
    /** 最外圈碎片变成的亮边框。 */
    private static final float BORDER_ALPHA = 0.85F;
    private static final float BORDER_WIDTH = 1.6F;

    private static final long FUSE_PAUSE_MS = 130L;
    private static final long PANEL_LEAD_MS = 90L;
    private static final long SETTLE_FLASH_MS = 180L;
    private static final long ROW_STAGGER_MS = 52L;
    private static final long ROW_REVEAL_MS = 190L;

    private static final long DISPERSE_MS = 1150L;
    private static final long DISABLE_FADE_MS = 420L;

    private static final float TAU = (float) (Math.PI * 2.0D);
    private static final int ROW_H = 11;
    /** 正文行首缩进（给行首色标留位置）。 */
    private static final int ROW_INDENT = 6;

    // ───────────────────────── 状态 ─────────────────────────

    private enum Phase { IDLE, CONVERGING, FUSED, PANEL, DISPERSE }

    private static final class Shard {
        int index;
        int layer;
        int dir;
        /** 深度 0~1：0 = 贴着中心空心圆（轨迹是正圆），1 = 贴着屏幕最边缘（轨迹是四边形）。 */
        float radius;
        float theta0;
        float omega;
        /** 环绕模式的公转角速度（慢得多）。 */
        float ringOmega;
        float eccX;
        float eccY;
        float squash;
        float wobble;
        float wobbleSpd;
        float size;
        float edge;
        float poly;
        float angle0;
        float spin;
        float delay;
        float dur;
        float tint;
        float seedJ;
        /** 拼合时这片瓷砖自己的半宽/半高倍数（≥ 1）。 */
        float growX, growY;
        boolean filled;
        /** true = 四边形碎片（贴屏幕边框，戴饰品栏位时显示）；false = 圆形碎片（手持时显示）。 */
        boolean quad;
        /** true = 为"数量翻倍"补出来的那批（不进悬停环，保持那一圈的密度不变）。 */
        boolean extra;
        /** 悬停环绕模式里的环带位置 0~1（与外围轨道深度解耦，保证悬停环外观不变）。 */
        float ringQ;
    }

    private final List<Shard> shards = new ArrayList<>();

    private Phase phase = Phase.IDLE;
    private long phaseStart;
    private float lastProgress;

    private List<Component> cachedLines;
    private Component cachedTitle;
    private ItemStack cachedStack = ItemStack.EMPTY;

    /** 分格缓存：只在面板尺寸变化时重算。 */
    private int gridCacheN = -1;
    private float gridCacheW = -1F;
    private float gridCacheH = -1F;
    private int gridCols = 1;
    private int gridRows = 1;

    // ───────────────────────── 渲染入口 ─────────────────────────

    /**
     * 面板「渲染 → 碎片渲染」开关（默认开）。
     *
     * <p>关闭后本模组的碎片表现整体退出：世界中不再画碎片；背包里既不画碎片、
     * 也不接管提示框（{@link ShardVeilScreenHook} 一并让位），玩家看到的就是
     * 模组原版的物品提示框 —— 悬停即见，无需按住 Shift。</p>
     */
    public static boolean shardVeilEnabled() {
        try {
            return ConfigValues.get(EternalHeartConfig.SHARD_VEIL);
        } catch (Throwable t) {
            return true;   // 配置尚未加载时按默认（开启）处理
        }
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        if (!shardVeilEnabled()) {
            return;        // 关闭碎片渲染 → 世界中的碎片与面板全部不画
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options == null || mc.options.hideGui) {
            return;
        }
        // 手持 → 出碎片也出面板；饰品栏位佩戴 → 只出碎片
        ItemStack hand = handHeart(player);
        ItemStack heart = hand.isEmpty() ? wornHeart(player) : hand;
        renderVeil(graphics, width, height, !heart.isEmpty(), heart, partialTick,
                Float.NaN, Float.NaN, !hand.isEmpty());
    }

    /** HUD 层：无环绕中心（走外围公转）。 */
    public void renderVeil(GuiGraphics graphics, int width, int height,
                           boolean active, ItemStack heart, float partialTick) {
        renderVeil(graphics, width, height, active, heart, partialTick,
                Float.NaN, Float.NaN, true);
    }

    /**
     * @param focusX       环绕中心（屏幕 GUI 坐标）；NaN = 走外围公转
     * @param panelAllowed false = 只出碎片，Shift 不聚合面板（饰品栏位佩戴）
     */
    public void renderVeil(GuiGraphics graphics, int width, int height,
                           boolean active, ItemStack heart, float partialTick,
                           float focusX, float focusY, boolean panelAllowed) {
        long now = Util.getMillis();
        ensureShards();

        float coreX = width * 0.5F;
        float coreY = height * 0.5F;

        // 面板出现的条件：
        //   ① 世界里（没开任何界面）手持 → 按 Shift 就聚合出面板（用户 2026-09-23：
        //      "拿手上时按 shift 显示面板失效了" —— 1.19.32 把它收得过紧，这里放回来）；
        //   ② 开了界面 → 只有指针悬停在永恒之心上时按 Shift 才出面板
        //      （用户："打开背包后只有鼠标移动到永恒之心之后按 shift 才会出现面板"）。
        Minecraft mc = Minecraft.getInstance();
        boolean hovering = !Float.isNaN(focusX) && !Float.isNaN(focusY);
        boolean panelHere = panelAllowed && (hovering || mc.screen == null);
        boolean shift = active && panelHere && shiftDown();
        advancePhase(now, shift, active);
        float master = masterAlpha(now, active);

        // panelAllowed 为 false 的唯一来源就是"戴在饰品栏位"（HUD、界面两条路径都如此）
        boolean worn = !panelAllowed;

        List<Component> lines = panelLines(heart);
        int[] rect = panelHere ? panelRect(mc, lines, width, coreX, coreY) : null;

        drawShards(width, height, now, coreX, coreY, worn, master, rect, focusX, focusY);

        if (rect != null) {
            drawPanelText(graphics, Minecraft.getInstance(), lines, rect, now, master);
        }
    }

    /**
     * Shift 是否真的按着。
     *
     * <p>开界面时不能用 {@code player.isShiftKeyDown()}：界面接管了按键，玩家 Input 的 shift 标记
     * 不会更新，恒为 false（面板永远不聚合）。所以优先读 <b>GLFW 真实按键状态</b>。</p>
     */
    private static boolean shiftDown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isShiftKeyDown()) {
            return true;
        }
        long win = mc.getWindow().getWindow();
        return InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    /**
     * 界面尺寸（GUI scale）补偿系数，<b>以界面尺寸 4 为基准</b>。
     *
     * <p>本类所有几何都在 <b>GUI 单位</b>里画，而 1 GUI 单位 = 若干物理像素，那个"若干"正是
     * 界面尺寸：尺寸调到 5，同样 5 个 GUI 单位的碎片就从 20px 变成 25px —— 就是用户报的
     * "更改界面尺寸的时候碎片也会跟着变换大小，大尺寸碎片也会放大导致占视野"。
     * 所有长度量乘上这个系数之后，碎片在屏幕上的<b>像素大小恒定</b>，永远等于界面尺寸 4 时的样子
     * （用户要求原话："不论切换什么尺寸，都与界面尺寸 4 的大小来改"）。</p>
     *
     * <p>为什么只补偿"长度"就够：轨道的两个半轴是跟着屏幕尺寸走的（半轴 ∝ 半屏宽 / 半屏高），
     * 换算成像素后本来就与界面尺寸无关；只有绝对长度（碎片尺寸、线宽、悬停环半径）会被界面尺寸放大。</p>
     *
     * <p>注意：<b>面板</b>（tooltip 那套）不乘这个系数 —— 它跟着字体走 GUI 缩放才是对的。</p>
     */
    private static float guiComp() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) {
            return 1.0F;
        }
        // 注意：1.20.1 的 Window#getGuiScale() 返回的是 double（不是 int），除完必须显式转 float
        return (float) (4.0D / Math.max(1.0D, mc.getWindow().getGuiScale()));
    }

    /**
     * HSV → 0xRRGGBB。自写而不是用 AWT / Mth，避免版本差异与额外依赖。
     *
     * @param h 色相 0~1（自动取小数部分）
     * @param s 饱和度 0~1
     * @param v 明度 0~1
     */
    private static int hsvToRgb(float h, float s, float v) {
        float hh = (h - (float) Math.floor(h)) * 6.0F;
        int i = (int) hh;
        float f = hh - i;
        float p = v * (1F - s);
        float q = v * (1F - s * f);
        float t = v * (1F - s * (1F - f));
        float r;
        float g;
        float b;
        switch (i % 6) {
            case 0 -> {
                r = v;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = v;
                b = p;
            }
            case 2 -> {
                r = p;
                g = v;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = v;
            }
            case 4 -> {
                r = t;
                g = p;
                b = v;
            }
            default -> {
                r = v;
                g = p;
                b = q;
            }
        }
        int ri = Mth.clamp((int) (r * 255.0F), 0, 255);
        int gi = Mth.clamp((int) (g * 255.0F), 0, 255);
        int bi = Mth.clamp((int) (b * 255.0F), 0, 255);
        return (ri << 16) | (gi << 8) | bi;
    }

    /**
     * 从屏幕中心朝 (cx, cy) 方向看，到屏幕边框的距离 —— 矩形的"极径"。
     *
     * <p>它就是外围公转的外沿：用矩形的极径当外沿，碎片才会真正贴着上下左右四条边，
     * 而且<b>四角实打实走到</b>（极径在四角方向最长）。用圆或椭圆的极径做外沿，
     * 四角一定会留出空隙 —— 用户 2026-09-23："你看四角是不是有小空隙，看着有强迫症"。</p>
     */
    private static float frameExtent(float cx, float cy, float halfW, float halfH) {
        float ax = Math.abs(cx);
        float ay = Math.abs(cy);
        float ex = ax < 1.0E-4F ? Float.MAX_VALUE : halfW / ax;
        float ey = ay < 1.0E-4F ? Float.MAX_VALUE : halfH / ay;
        return Math.min(ex, ey);
    }

    // ───────────────────────── 状态机 ─────────────────────────

    private void advancePhase(long now, boolean shift, boolean active) {
        switch (phase) {
            case IDLE -> {
                if (shift) {
                    phase = Phase.CONVERGING;
                    phaseStart = now;
                    lastProgress = 0F;
                }
            }
            case CONVERGING -> {
                if (!shift) {
                    lastProgress = convergeProgress(now);
                    phase = Phase.DISPERSE;
                    phaseStart = now;
                } else if (now - phaseStart >= convergeSpan()) {
                    phase = Phase.FUSED;
                    phaseStart = now;
                }
            }
            case FUSED -> {
                if (!shift) {
                    lastProgress = 1F;
                    phase = Phase.DISPERSE;
                    phaseStart = now;
                } else if (now - phaseStart >= FUSE_PAUSE_MS) {
                    phase = Phase.PANEL;
                    phaseStart = now;
                }
            }
            case PANEL -> {
                if (!shift) {
                    lastProgress = 1F;
                    phase = Phase.DISPERSE;
                    phaseStart = now;
                }
            }
            case DISPERSE -> {
                if (shift) {
                    lastProgress = 1F - lastProgress;
                    phase = Phase.CONVERGING;
                    phaseStart = now - (long) (convergeSpan() * Mth.clamp(lastProgress, 0F, 1F));
                } else if (now - phaseStart >= DISPERSE_MS) {
                    phase = Phase.IDLE;
                    phaseStart = now;
                    lastProgress = 0F;
                }
            }
        }
        if (!active && phase != Phase.IDLE && phase != Phase.DISPERSE) {
            lastProgress = (phase == Phase.PANEL || phase == Phase.FUSED) ? 1F
                    : Math.max(lastProgress, convergeProgress(now));
            phase = Phase.DISPERSE;
            phaseStart = now;
        }
    }

    private static long convergeSpan() {
        return CONVERGE_BASE_MS + CONVERGE_JITTER_MS + LAYER_STAGGER_MS * 2L + PER_SHARD_JITTER_MS;
    }

    private float masterAlpha(long now, boolean active) {
        if (phase == Phase.IDLE) {
            return active ? 1F : 0F;
        }
        if (active) {
            return 1F;
        }
        return Mth.clamp(1F - (float) (now - phaseStart) / (float) DISABLE_FADE_MS, 0F, 1F);
    }

    private float convergeProgress(long now) {
        return switch (phase) {
            case IDLE -> 0F;
            case CONVERGING -> Mth.clamp((float) (now - phaseStart) / (float) convergeSpan(), 0F, 1F);
            case FUSED, PANEL -> 1F;
            case DISPERSE -> Mth.clamp(1F - (float) (now - phaseStart) / (float) DISPERSE_MS, 0F, 1F);
        };
    }

    // ───────────────────────── 碎片 ─────────────────────────

    private void ensureShards() {
        if (!shards.isEmpty()) {
            return;
        }
        Random rnd = new Random(0x5EED_1EAF_2C0DEL);
        addLayer(rnd, 0, COUNT_FAR);
        addLayer(rnd, 1, COUNT_MID);
        addLayer(rnd, 2, COUNT_NEAR);
        // 两类碎片各 ×2（用户 2026-09-23："把拿手上显示的圆碎片数量乘2" → "最后再把方碎片数量乘2"）——
        // 按每层一半的量各补一批：圆形 ≈750 → 1500、四边形 750 → 1500，总计 3000 片。
        addLayer(rnd, 0, Math.round(COUNT_FAR * EXTRA_SHARE), true, false);
        addLayer(rnd, 1, Math.round(COUNT_MID * EXTRA_SHARE), true, false);
        addLayer(rnd, 2, Math.round(COUNT_NEAR * EXTRA_SHARE), true, false);
        addLayer(rnd, 0, Math.round(COUNT_FAR * EXTRA_SHARE), false, true);
        addLayer(rnd, 1, Math.round(COUNT_MID * EXTRA_SHARE), false, true);
        addLayer(rnd, 2, Math.round(COUNT_NEAR * EXTRA_SHARE), false, true);
    }

    private void addLayer(Random rnd, int layer, int count) {
        addLayer(rnd, layer, count, false, false);
    }

    /**
     * @param forceCirc true = 这一批全部做成圆形碎片（给"手持显示"加量）
     * @param forceQuad true = 这一批全部做成四边形碎片（给"佩戴显示"加量）
     */
    private void addLayer(Random rnd, int layer, int count, boolean forceCirc, boolean forceQuad) {
        for (int i = 0; i < count; i++) {
            Shard s = new Shard();
            s.index = shards.size();
            s.layer = layer;
            s.dir = rnd.nextBoolean() ? 1 : -1;
            s.extra = forceCirc || forceQuad;
            // 分两类：圆形碎片（手持显示）/ 四边形碎片（戴饰品栏位显示）
            boolean quad = forceQuad || (!forceCirc && rnd.nextFloat() < QUAD_SHARE);
            s.quad = quad;
            if (quad) {
                // 四边形碎片：贴屏幕边框的窄带；外端略微超出屏幕 → 不是所有碎片都在屏内
                s.radius = QUAD_T_MIN + (QUAD_T_MAX - QUAD_T_MIN)
                        * (float) Math.pow(rnd.nextFloat(), QUAD_BIAS);
                s.size = QUAD_SIZE_MIN + (QUAD_SIZE_MAX - QUAD_SIZE_MIN) * rnd.nextFloat();
                s.edge = QUAD_EDGE_MIN + (QUAD_EDGE_MAX - QUAD_EDGE_MIN) * rnd.nextFloat();
            } else {
                // 圆形碎片：1.19.17 的正圆轨道 —— 半径系数 0.34~1（pow 偏置 → 越靠外越密）
                s.radius = HELD_RADIUS_MIN + (1F - HELD_RADIUS_MIN)
                        * (float) Math.pow(rnd.nextFloat(), HELD_RADIUS_BIAS);
                // 尺寸/描边同 1.19.17：随半径系数线性放大（2.2 → 8.0 / 0.55 → 0.95）
                s.size = HELD_SIZE_IN + (HELD_SIZE_OUT - HELD_SIZE_IN)
                        * (float) Math.pow(s.radius, 1.10F);
                s.edge = HELD_EDGE_IN + (HELD_EDGE_OUT - HELD_EDGE_IN) * s.radius;
            }
            s.ringQ = rnd.nextFloat();
            s.theta0 = rnd.nextFloat() * TAU;
            // 公转速度：按"20 秒一圈"给基准角速度，再乘每片 0.85~1.15 的个体差异 → 无规律但都在 20 秒上下
            float lapJitter = ORBIT_JITTER_LO + rnd.nextFloat() * (ORBIT_JITTER_HI - ORBIT_JITTER_LO);
            s.omega = (TAU / ORBIT_LAP) * lapJitter;
            s.ringOmega = (RING_OMEGA_MIN + rnd.nextFloat() * (RING_OMEGA_MAX - RING_OMEGA_MIN))
                    * (0.7F + rnd.nextFloat() * 0.6F);
            float ea = rnd.nextFloat() * TAU;
            float er = rnd.nextFloat() * ORBIT_ECCENTRIC;
            s.eccX = Mth.cos(ea) * er;
            s.eccY = Mth.sin(ea) * er * 0.8F;
            s.squash = 0.80F + rnd.nextFloat() * 0.20F;
            s.wobble = 0.04F + rnd.nextFloat() * 0.12F;
            s.wobbleSpd = 0.20F + rnd.nextFloat() * 1.10F;
            s.poly = rnd.nextFloat();
            s.angle0 = rnd.nextFloat() * 360.0F;
            // 自转：慢 + 无规律（用户 2026-09-23："旋转再慢点，记得是无规律的速度旋转"）。
            // 速度取 rnd² 偏置：大部分碎片磨磨蹭蹭（接近 0.05），少数转得明显些（最高 0.40），
            // 再乘 s.dir（正反都有）+ 随半径的小幅线性放大 → 不会像联动的齿轮整排同速。
            // 旧值 0.10 + rnd*0.60（0.10~0.70）整体偏快。
            s.spin = (0.05F + rnd.nextFloat() * rnd.nextFloat() * 0.35F) * (0.35F + s.radius);
            s.delay = rnd.nextFloat();
            s.dur = rnd.nextFloat();
            s.tint = rnd.nextFloat();
            s.seedJ = rnd.nextFloat();
            s.growX = PIECE_GROW_MIN + rnd.nextFloat() * (PIECE_GROW_MAX - PIECE_GROW_MIN);
            s.growY = PIECE_GROW_MIN + rnd.nextFloat() * (PIECE_GROW_MAX - PIECE_GROW_MIN);
            s.filled = rnd.nextInt(12) == 0;
            shards.add(s);
        }
    }

    /**
     * 面板分格：<b>只在碎片总数 n 的因数对里挑</b> —— cols×rows 恒等于 n，
     * 每一格都有碎片，面板不可能缺条。在因数对里选最贴合面板宽高比的一对，瓷砖就基本是方的。
     */
    private int[] gridOf(int n, float w, float h) {
        if (gridCacheN == n && gridCacheW == w && gridCacheH == h) {
            return new int[]{gridCols, gridRows};
        }
        float target = w / Math.max(1F, h);
        int bestC = 1;
        int bestR = n;
        float bestErr = Float.MAX_VALUE;
        for (int c = 1; c * c <= n; c++) {
            if (n % c != 0) {
                continue;
            }
            int r = n / c;
            float errA = Math.abs((float) c / r - target);
            if (errA < bestErr) {
                bestErr = errA;
                bestC = c;
                bestR = r;
            }
            float errB = Math.abs((float) r / c - target);
            if (errB < bestErr) {
                bestErr = errB;
                bestC = r;
                bestR = c;
            }
        }
        gridCacheN = n;
        gridCacheW = w;
        gridCacheH = h;
        gridCols = bestC;
        gridRows = bestR;
        return new int[]{bestC, bestR};
    }

    private void drawShards(int width, int height, long now,
                            float coreX, float coreY, boolean worn, float master,
                            int[] rect, float focusX, float focusY) {
        if (master <= 0.004F) {
            return;
        }
        float seconds = now / 1000.0F;
        float halfW = width * 0.5F;
        float halfH = height * 0.5F;
        // 中心空心圆：半径取半屏短边 —— 上下各留 2/3 当空心；横向因为屏幕更扁，会留得更多
        float holeR = HOLE_FRAC * Math.min(halfW, halfH);
        // 手持圆形碎片的基准半径：半屏幕对角线 × 1.15（1.19.17 的算法 → 正圆，不是椭圆）
        float heldRMax = 0.5F * (float) Math.sqrt((double) width * width + (double) height * height)
                * HELD_ORBIT_MAX;
        boolean merged = phase == Phase.FUSED || phase == Phase.PANEL;
        boolean ringMode = !Float.isNaN(focusX) && !Float.isNaN(focusY);
        // 悬停环绕里那一圈是用户认可过的样子 → 统一放大；外围公转则按两类碎片各自的尺寸走
        sSizeMul = ringMode ? RING_SIZE_MUL : 1.0F;
        float disperse = phase == Phase.DISPERSE
                ? Mth.clamp((float) (now - phaseStart) / (float) DISPERSE_MS, 0F, 1F) : 0F;
        float flash = 0F;
        if (phase == Phase.PANEL) {
            float t = Mth.clamp((float) (now - phaseStart) / (float) SETTLE_FLASH_MS, 0F, 1F);
            flash = (1F - t) * 0.30F;
        }

        int cols = 1;
        int rows = 1;
        float cw = 1F;
        float ch = 1F;
        if (rect != null) {
            int[] g = gridOf(shards.size(), rect[2] - rect[0], rect[3] - rect[1]);
            cols = g[0];
            rows = g[1];
            cw = (rect[2] - rect[0]) / (float) cols;
            ch = (rect[3] - rect[1]) / (float) rows;
        }

        float pivotX = ringMode ? focusX : coreX;
        float pivotY = ringMode ? focusY : coreY;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        // 必须关背面剔除：填充是「中心→角i→角j」的扇形三角形，在 NDC 里是顺时针 = 背面，
        // 不关就会被整片剔掉 —— 之前三版面板"拼不成整块"的真凶就是这一行缺失。
        // vanilla GUI 不需要这事（RenderType.GUI 自带 NO_CULL），我们这条裸通道得自己管。
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        // 面板本体：一整块四角斜切的半透明板，随聚合进度淡入（碎片摊平的同时玻璃面成形）。
        // 必须整块画：逐片瓷砖填的重叠数逐格不同 → 深浅不一（"小块小块"）；整块画才能既均匀又半透明。
        if (rect != null) {
            float plateA = Mth.clamp(
                    PANEL_FILL * easeInOutCubic(convergeProgress(now)) * master, 0F, 1F);
            if (plateA > 0.004F) {
                float lift = flash * 0.30F;
                plateQuad(bb, rect, PLATE_CORNER * Math.min(cw, ch), plateA,
                        Math.min(1F, PANEL_R + lift), Math.min(1F, PANEL_G + lift),
                        Math.min(1F, PANEL_B + lift));
            }
        }

        for (Shard s : shards) {
            // 环绕模式平时只画一部分（避免糊成一圈白）；一开始聚合就全量出现。
            // 加量补出的那批碎片不进悬停环 —— 那一圈的密度是用户认可过的，不能被加量改变。
            boolean thinned = ringMode && (s.extra || s.index % RING_STRIDE != 0);
            if (phase == Phase.IDLE && thinned) {
                continue;
            }

            // 两类碎片各归各的状态（用户 2026-09-23："拿手上不能显示方块碎片，
            // 带饰品不能显示圆形碎片"）：手持（没戴饰品栏）只出圆形碎片，佩戴只出四边形碎片。
            // ⚠ 注意：下面的 if / else-if 分支只是"选轨迹"，它不是过滤 —— 状态过滤必须在这里做，
            //   否则两类会各走各的轨道同时出现（1.19.34 就是这么错的）。
            // 两个例外：
            //   ① 悬停环绕模式两类都要 —— 那一圈是整体外观，不能缺一半；
            //   ② 聚合面板阶段（CONVERGING/FUSED/PANEL）两类都要 —— 板面按 index 分格，
            //      少一类就会整片缺格。
            if (phase == Phase.IDLE && !ringMode && s.quad != worn) {
                continue;
            }

            float idleX;
            float idleY;
            float idleRot;
            if (ringMode) {
                // 环绕被悬停的物品格：环心 = 物品格中心（屏幕坐标）
                // 环带位置用 s.ringQ（与外围轨道的深度解耦，保证这一圈的观感不变）
                float q = s.ringQ;
                float rr = RING_IN_MIN + (RING_IN_MAX - RING_IN_MIN) * q
                        + (RING_THICK_MIN + (RING_THICK_MAX - RING_THICK_MIN) * q)
                        * (1F + s.wobble * Mth.sin(seconds * s.wobbleSpd + s.theta0)) * 0.5F;
                // 径向再撒开一点（用户："碎片再散开一点"）—— 别咬成一条细环
                rr *= RING_SCATTER_LO + (RING_SCATTER_HI - RING_SCATTER_LO) * s.tint;
                // 界面尺寸补偿：环的半径同样以"界面尺寸 4"为基准（屏幕上像素大小恒定）
                rr *= guiComp();
                float ang = s.theta0 + s.dir * s.ringOmega * seconds;
                idleX = focusX + Mth.cos(ang) * rr;
                idleY = focusY + Mth.sin(ang) * rr * s.squash;
                idleRot = s.angle0 + s.dir * s.spin * seconds * 57.29578F;
            } else if (s.quad) {
                // 四边形碎片（佩戴）：路径 = "中心空心圆 ↔ 屏幕边框" 插值 ——
                //   内端是正圆，外端正好是屏幕边框那个四边形（四角实打实走到）；
                //   t 上限 QUAD_T_MAX > 1 → 略微出屏（"不是所有碎片都在屏幕内"）。
                float ang = s.theta0 + s.dir * s.omega * seconds;
                float ca = Mth.cos(ang);
                float sa = Mth.sin(ang);
                float ext = frameExtent(ca, sa, halfW, halfH);
                float t = Mth.clamp(s.radius
                        * (1F + s.wobble * Mth.sin(seconds * s.wobbleSpd + s.theta0)), 0F, QUAD_T_MAX);
                float d = Mth.lerp(t, holeR, ext);
                float orbitCx = coreX + s.eccX * holeR * 0.25F;
                float orbitCy = coreY + s.eccY * holeR * 0.25F;
                idleX = orbitCx + ca * d;
                idleY = orbitCy + sa * d;
                idleRot = s.angle0 + s.dir * s.spin * seconds * 57.29578F;
            } else {
                // 圆形碎片（手持）：<b>1.19.17 那套正圆轨道</b> —— 半径 = 半屏幕对角线 × 1.15 ×
                // 每片自己的 [0.34, 1]（见 HELD_*）。x / y 同一个半径 → 真圆、中间空心是正圆；
                // 基准 434 GUI 比半宽 320 大 → 外圈有一部分碎片本来就在屏幕外（1.19.17 就是这样）。
                float r = s.radius * heldRMax
                        * (1F + s.wobble * Mth.sin(seconds * s.wobbleSpd + s.theta0));
                float ang = s.theta0 + s.dir * s.omega * seconds;
                idleX = coreX + s.eccX * heldRMax + Mth.cos(ang) * r;
                idleY = coreY + s.eccY * heldRMax + Mth.sin(ang) * r * s.squash;
                idleRot = s.angle0 + s.dir * s.spin * seconds * 57.29578F;
            }

            // 面板里的位置：严格格心（不规则感由每片自己的尺寸表达）
            int col = s.index % cols;
            int row = s.index / cols;
            boolean border = rect != null
                    && (col == 0 || row == 0 || col == cols - 1 || row == rows - 1);
            float tx = idleX;
            float ty = idleY;
            float snapL = Float.NaN;
            float snapR = Float.NaN;
            float snapT = Float.NaN;
            float snapB = Float.NaN;
            if (rect != null) {
                tx = rect[0] + (col + 0.5F) * cw;
                ty = rect[1] + (row + 0.5F) * ch;
                if (col == 0) {
                    snapL = rect[0];
                }
                if (col == cols - 1) {
                    snapR = rect[2];
                }
                if (row == 0) {
                    snapT = rect[1];
                }
                if (row == rows - 1) {
                    snapB = rect[3];
                }
            }

            float k;
            if (phase == Phase.CONVERGING) {
                long delay = (long) (LAYER_STAGGER_MS * s.layer + PER_SHARD_JITTER_MS * s.delay);
                long dur = CONVERGE_BASE_MS + (long) (CONVERGE_JITTER_MS * s.dur);
                k = easeInOutCubic(Mth.clamp((float) (now - phaseStart - delay) / (float) dur, 0F, 1F));
            } else if (merged) {
                k = 1F;
            } else if (phase == Phase.DISPERSE) {
                float stagger = 0.18F * s.dur + 0.10F * s.layer;
                float d = Mth.clamp((disperse - stagger) / Math.max(0.05F, 1F - stagger), 0F, 1F);
                k = lastProgress * (1F - easeOutCubic(d));
            } else {
                k = 0F;
            }

            float x = idleX;
            float y = idleY;
            if (k > 0.0005F) {
                float twist = Mth.sin((float) Math.PI * k) * CONVERGE_TWIST;
                float c = Mth.cos(twist);
                float sn = Mth.sin(twist);
                float ax = pivotX + (idleX - pivotX) * c - (idleY - pivotY) * sn;
                float ay = pivotY + (idleX - pivotX) * sn + (idleY - pivotY) * c;
                x = ax + (tx - ax) * k;
                y = ay + (ty - ay) * k;
            }

            float flat = rect == null ? 0F
                    : easeInOutCubic(Mth.clamp((k - FLATTEN_FROM) / Math.max(0.05F, 1F - FLATTEN_FROM), 0F, 1F));

            float alpha = LAYER_ALPHA[s.layer];
            if (k > 0F) {
                alpha *= lerp(1F, 1.10F, Mth.clamp(k * 1.5F, 0F, 1F));
                if (flat >= 0.999F) {
                    alpha *= 0.92F + flash;
                }
            }
            if (thinned) {
                // 平时隐身的那部分，聚合时快速淡入（不闪不跳）
                alpha *= Mth.clamp(k * 3.2F, 0F, 1F);
            }
            alpha *= master;
            if (alpha <= 0.004F) {
                continue;
            }
            alpha = Math.min(alpha, 1F);

            int cornerMask = 0;
            if (rect != null && flat > 0.01F) {
                if (col == 0 && row == 0) {
                    cornerMask |= 1;
                }
                if (col == cols - 1 && row == 0) {
                    cornerMask |= 2;
                }
                if (col == cols - 1 && row == rows - 1) {
                    cornerMask |= 4;
                }
                if (col == 0 && row == rows - 1) {
                    cornerMask |= 8;
                }
            }
            emitShard(bb, s, x, y, idleRot, flat, cw, ch,
                    alpha, border, flash, cornerMask, snapL, snapR, snapT, snapB);
        }

        BufferUploader.drawWithShader(bb.end());

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /** 本帧的碎片尺寸倍率（悬停环绕模式放大，外围公转 1.0）。 */
    private static float sSizeMul = 1.0F;

    /** 复用的 4 顶点缓冲：避免每帧每片 new float[4]（1500 片 ×60fps 的 GC 抖动）。 */
    private static final float[] PX = new float[4];
    private static final float[] PY = new float[4];
    private static final float[] QX = new float[4];
    private static final float[] QY = new float[4];
    private static final float[] AX = new float[4];
    private static final float[] AY = new float[4];

    /**
     * 画一片碎片：flat=0 是锐角玻璃碎片（细线框），flat=1 是摊平后的方板。
     * 两者都是 4 个顶点，逐点插值 —— 视觉上就是"碎片摊开拼进面板"。
     */
    private static void emitShard(BufferBuilder bb, Shard s, float cx, float cy, float rotDeg,
                                  float flat, float cellW, float cellH,
                                  float alpha, boolean border, float flash, int cornerMask,
                                  float snapL, float snapR, float snapT, float snapB) {
        float rad = (float) Math.toRadians(rotDeg);
        float cos = Mth.cos(rad);
        float sin = Mth.sin(rad);
        float rc = lerp(cos, 1F, flat);
        float rs = lerp(sin, 0F, flat);

        // 悬停环绕里只把"四边形碎片"放大到与圆形碎片同一档（那一圈是整体外观，不能一半大一半小）
        float radius = s.size * (s.quad ? sSizeMul : 1.0F) * guiComp();
        float stretch = 1.45F + s.poly * 1.85F;
        // 半宽/半高 ≥ 半格（grow ≥ 1）→ 每片必然完整覆盖自己那一格 → 并集恒等于整块面板
        float hw = cellW * 0.5F * s.growX;
        float hh = cellH * 0.5F * s.growY;
        for (int i = 0; i < 4; i++) {
            float a = (i / 4.0F) * TAU + s.poly * 1.7F + s.seedJ * 0.6F;
            float wob = 0.52F + 0.48F * Math.abs(Mth.sin(i * 1.9F + s.poly * 5.0F + s.tint * 3.0F));
            float rr = radius * wob;
            float lx = Mth.cos(a) * rr * stretch;
            float ly = Mth.sin(a) * rr;
            PX[i] = lx * rc - ly * rs;
            PY[i] = lx * rs + ly * rc;

            float cornerX = ((i == 0 || i == 3) ? -hw : hw);
            float cornerY = ((i == 0 || i == 1) ? -hh : hh);
            // 最外圈：朝外的边精确压在面板边界上
            if (cornerX < 0F && !Float.isNaN(snapL)) {
                cornerX = snapL - cx;
            }
            if (cornerX > 0F && !Float.isNaN(snapR)) {
                cornerX = snapR - cx;
            }
            if (cornerY < 0F && !Float.isNaN(snapT)) {
                cornerY = snapT - cy;
            }
            if (cornerY > 0F && !Float.isNaN(snapB)) {
                cornerY = snapB - cy;
            }
            // 四角碎片斜切出圆角（收边之后再切，切出来的斜角自然落在边界内侧）
            if (cornerMask != 0 && (cornerMask & (1 << i)) != 0) {
                cornerX *= 0.34F;
                cornerY *= 0.34F;
            }
            QX[i] = cornerX;
            QY[i] = cornerY;
        }

        for (int i = 0; i < 4; i++) {
            AX[i] = lerp(PX[i], QX[i], flat);
            AY[i] = lerp(PY[i], QY[i], flat);
        }

        // 碎片自身的半透明白填充（只有 1/12 是"实心碎片"）。
        // 注意：板面本体不在这里画 —— 逐片瓷砖填会因为重叠数不同而深浅不一（"小块小块"的根源），
        // 板面由 drawShards 里的 plateQuad() 整块画。
        // 七彩循环（用户："要改成七色的，赤橙黄绿青蓝紫七色，颜色变换速度快点"）——
        // 色相 = 时间 × 速度 + 每片自己的相位；hsvToRgb 内部取小数部分 → 自动环绕整个色轮。
        // 每片速度不同（s.poly）、起点不同（s.tint），所以同一时刻整片碎片就是七彩的，
        // 而每一片都在依次走过 赤→橙→黄→绿→青→蓝→紫。
        float tt = Util.getMillis() / 1000.0F;
        float hue = tt * (HUE_SPEED_LO + (HUE_SPEED_HI - HUE_SPEED_LO) * s.poly) + s.tint;
        int tc = hsvToRgb(hue, SAT_LO + (SAT_HI - SAT_LO) * s.poly, 1.0F);
        float cr = ((tc >> 16) & 0xFF) / 255.0F;
        float cg = ((tc >> 8) & 0xFF) / 255.0F;
        float cb = (tc & 0xFF) / 255.0F;

        float fa = alpha * (s.filled ? 0.14F : 0F);
        if (fa > 0.004F) {
            fan(bb, cx, cy, fa, cr, cg, cb);
        }

        if (flat > 0.995F) {
            // 已摊平：内部瓷砖一条线都不画；最外圈只画"朝外"的那条边
            // （四条边全画的话，瓷砖左右两条边会在面板里戳出一排小线头 = 用户说的"溢出来的边"）
            // 面板边框保持近白色 —— 那是面板的一部分，不跟着碎片的淡彩走
            if (!border) {
                return;
            }
            boolean onTop = !Float.isNaN(snapT);
            boolean onBottom = !Float.isNaN(snapB);
            boolean onLeft = !Float.isNaN(snapL);
            boolean onRight = !Float.isNaN(snapR);
            float ba = Math.min(1F, alpha * (BORDER_ALPHA + flash));
            float half = Math.max(0.30F, BORDER_WIDTH) * 0.5F;
            for (int i = 0; i < 4; i++) {
                boolean face = (i == 0 && onTop) || (i == 1 && onRight)
                        || (i == 2 && onBottom) || (i == 3 && onLeft);
                if (face) {
                    strokeEdge(bb, i, cx, cy, half, ba, 0.93F, 0.95F, 1.0F);
                }
            }
        } else {
            // 还没摊平：画完整轮廓（碎片本体：细线 + 自己的淡彩色）—— 线宽同样按界面尺寸补偿，
            // 不然大尺寸下碎片会变成又粗又胖的块。
            float half = Math.max(0.30F, s.edge * guiComp()) * 0.5F;
            for (int i = 0; i < 4; i++) {
                strokeEdge(bb, i, cx, cy, half, alpha, cr, cg, cb);
            }
        }
    }

    /** 画一条边（i → i+1）的细线：两条三角形拼成的矩形带。 */
    private static void strokeEdge(BufferBuilder bb, int i, float cx, float cy, float half,
                                   float alpha, float r, float g, float b) {
        int j = (i + 1) % 4;
        float ax = AX[i];
        float ay = AY[i];
        float bx = AX[j];
        float by = AY[j];
        float dx = bx - ax;
        float dy = by - ay;
        float len = Math.max(1.0E-4F, (float) Math.sqrt(dx * dx + dy * dy));
        float nx = -dy / len * half;
        float ny = dx / len * half;
        vertex(bb, cx + ax + nx, cy + ay + ny, alpha, r, g, b);
        vertex(bb, cx + bx + nx, cy + by + ny, alpha, r, g, b);
        vertex(bb, cx + bx - nx, cy + by - ny, alpha, r, g, b);
        vertex(bb, cx + ax + nx, cy + ay + ny, alpha, r, g, b);
        vertex(bb, cx + bx - nx, cy + by - ny, alpha, r, g, b);
        vertex(bb, cx + ax - nx, cy + ay - ny, alpha, r, g, b);
    }

    /**
     * 面板本体：<b>一整块</b>四角斜切的半透明板（八边形扇形）。
     * 只有整块画才能同时做到"颜色绝对均匀"与"半透明"。
     */
    private static void plateQuad(BufferBuilder bb, int[] rect, float cut, float a,
                                  float r, float g, float b) {
        float x0 = rect[0];
        float y0 = rect[1];
        float x1 = rect[2];
        float y1 = rect[3];
        float c = Math.max(0F, Math.min(cut, Math.min(x1 - x0, y1 - y0) * 0.4F));
        float[] px = {x0 + c, x1 - c, x1, x1, x1 - c, x0 + c, x0, x0};
        float[] py = {y0, y0, y0 + c, y1 - c, y1, y1, y1 - c, y0 + c};
        float mx = (x0 + x1) * 0.5F;
        float my = (y0 + y1) * 0.5F;
        for (int i = 0; i < 8; i++) {
            int j = (i + 1) % 8;
            vertex(bb, mx, my, a, r, g, b);
            vertex(bb, px[i], py[i], a, r, g, b);
            vertex(bb, px[j], py[j], a, r, g, b);
        }
    }

    /** 以中心为扇心，把 4 个角连成 4 个三角形（覆盖 = 该片当前的形状）。 */
    private static void fan(BufferBuilder bb, float cx, float cy, float a, float r, float g, float b) {
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            vertex(bb, cx, cy, a, r, g, b);
            vertex(bb, cx + AX[i], cy + AY[i], a, r, g, b);
            vertex(bb, cx + AX[j], cy + AY[j], a, r, g, b);
        }
    }

    private static void vertex(BufferBuilder bb, float x, float y, float a, float r, float g, float b) {
        bb.vertex(x, y, 0.0F).color(r, g, b, a).endVertex();
    }

    // ───────────────────────── 数据面板 ─────────────────────────

    private int[] panelRect(Minecraft mc, List<Component> lines, int width, float coreX, float coreY) {
        if (mc.font == null || lines.isEmpty()) {
            return null;
        }
        int padX = 18;
        int padY = 14;
        int maxW = Math.min((int) (width * 0.88F), 900);
        int contentW = 0;
        for (Component line : lines) {
            contentW = Math.max(contentW, mc.font.width(line));
        }
        if (cachedTitle != null) {
            contentW = Math.max(contentW, mc.font.width(cachedTitle));
        }
        int panelW = Math.min(maxW, contentW + padX * 2 + ROW_INDENT);
        int panelH = lines.size() * ROW_H + padY * 2 + 6;
        int x0 = (int) (coreX - panelW / 2.0F);
        int y0 = (int) (coreY - panelH / 2.0F);
        return new int[]{x0, y0, x0 + panelW, y0 + panelH};
    }

    private void drawPanelText(GuiGraphics graphics, Minecraft mc, List<Component> lines,
                               int[] rect, long now, float master) {
        if (mc.font == null || lines.isEmpty()) {
            return;
        }
        int y0 = rect[1];
        int textX = rect[0] + 18;
        int textY = y0 + 14;

        long panelT;
        if (phase == Phase.PANEL) {
            panelT = now - phaseStart;
        } else if (phase == Phase.FUSED) {
            panelT = 0L;
        } else if (phase == Phase.CONVERGING || phase == Phase.IDLE) {
            return;
        } else {
            panelT = (long) (PANEL_LEAD_MS + lines.size() * ROW_STAGGER_MS + ROW_REVEAL_MS + 500L);
        }
        float open = Mth.clamp((float) panelT / 210.0F, 0F, 1F);
        if (open <= 0.002F) {
            return;
        }
        float shrink = phase == Phase.DISPERSE
                ? Mth.clamp((float) (now - phaseStart) / 170.0F, 0F, 1F) : 0F;
        int w = rect[2] - rect[0];
        int halfW = (int) (w / 2.0F * Math.max(0F, open * (1F - easeInQuad(shrink))));
        if (halfW <= 1) {
            return;
        }
        int cx = (rect[0] + rect[2]) / 2;
        graphics.enableScissor(cx - halfW, y0 - 2, cx + halfW, rect[3] + 2);

        if (cachedTitle != null) {
            graphics.drawString(mc.font, cachedTitle, textX, textY,
                    withAlpha(styleColor(cachedTitle, 0xFFFFD98A), master), true);
            int ruleY = textY + 10;
            int ruleMax = Math.min(w - 36 - 6, mc.font.width(cachedTitle) + 40);
            int ruleW = (int) (ruleMax * easeOutQuint(Mth.clamp((float) panelT / 240.0F, 0F, 1F)));
            if (ruleW > 0) {
                graphics.fill(textX, ruleY, textX + ruleW, ruleY + 1, withAlpha(0x66FFFFFF, master));
            }
            textY += 15;
        }

        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            long rowStart = PANEL_LEAD_MS + (long) i * ROW_STAGGER_MS;
            float rt = Mth.clamp((float) (panelT - rowStart) / (float) ROW_REVEAL_MS, 0F, 1F);
            if (rt > 0.002F) {
                float e = easeOutCubic(rt);
                int lw = mc.font.width(line);
                int revealW = Math.max(1, (int) (lw * e));
                int color = withAlpha(styleColor(line, 0xFFE8E8F0), master);
                // 行首色标（照参考视频：每行前面一道竖色条，颜色取这行文字自己的样式色）
                graphics.fill(textX, textY - 1, textX + 2, textY + 8, color);
                graphics.enableScissor(rect[0] + 3, textY - 1,
                        Math.min(rect[2] - 3, textX + ROW_INDENT + revealW + 2), textY + 9);
                graphics.drawString(mc.font, line, textX + ROW_INDENT, textY, color, true);
                graphics.disableScissor();
                if (rt < 0.99F) {
                    int hx = textX + ROW_INDENT + revealW;
                    graphics.fill(hx, textY - 1, hx + 1, textY + 9, withAlpha(0x7AFFFFFF, master));
                }
            }
            textY += ROW_H;
        }
        graphics.disableScissor();
    }

    /** 取文字自身的样式色（原版 tooltip 每行都带颜色），没有就用 fallback。 */
    private static int styleColor(Component text, int fallback) {
        if (text != null) {
            var tc = text.getStyle().getColor();
            if (tc != null) {
                return 0xFF000000 | tc.getValue();
            }
        }
        return fallback;
    }

    private List<Component> panelLines(ItemStack heart) {
        if (heart.isEmpty()) {
            return cachedLines == null ? List.of() : cachedLines;
        }
        if (cachedLines != null && ItemStack.isSameItemSameTags(cachedStack, heart)) {
            return cachedLines;
        }
        cachedStack = heart.copy();
        List<Component> raw = heart.getTooltipLines(
                Minecraft.getInstance().player,
                net.minecraft.world.item.TooltipFlag.Default.NORMAL);
        List<Component> out = new ArrayList<>();
        if (!raw.isEmpty()) {
            cachedTitle = raw.get(0);
            for (int i = 1; i < raw.size(); i++) {
                Component c = raw.get(i);
                if (c.getString().isBlank()) {
                    continue;
                }
                out.add(c);
            }
        } else {
            cachedTitle = Component.literal(heart.getHoverName().getString());
        }
        cachedLines = out;
        return cachedLines;
    }

    private static int withAlpha(int rgb, float mul) {
        int a = (int) (((rgb >>> 24) & 0xFF) * Mth.clamp(mul, 0F, 1F));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    // ───────────────────────── 触发判定 ─────────────────────────

    /** 手持（主手/副手）—— 出碎片，也允许 Shift 出面板。 */
    public static ItemStack handHeart(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (isHeart(main)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        return isHeart(off) ? off : ItemStack.EMPTY;
    }

    /**
     * 饰品栏位（Curios）佩戴 —— <b>只出碎片，不出面板</b>。
     *
     * <p>用户 2026-09-23："不应该出现面板，但碎片应该向1一样在屏幕边上"。</p>
     */
    public static ItemStack wornHeart(LocalPlayer player) {
        if (!EquipTracker.isEquipped(player)) {
            return ItemStack.EMPTY;
        }
        var handler = top.theillusivec4.curios.api.CuriosApi
                .getCuriosInventory(player).resolve().orElse(null);
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        var res = handler.findFirstCurio(EternalHeartMod.ETERNAL_HEART.get());
        return res.isPresent() ? res.get().stack() : ItemStack.EMPTY;
    }

    /** 兼容旧调用：手持或佩戴，任意一个都算"有碎片"。 */
    public static ItemStack findActiveHeart(LocalPlayer player) {
        ItemStack hand = handHeart(player);
        return hand.isEmpty() ? wornHeart(player) : hand;
    }

    public static boolean isHeart(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == EternalHeartMod.ETERNAL_HEART.get();
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float easeInOutCubic(float t) {
        return t < 0.5F ? 4F * t * t * t : 1F - (float) Math.pow(-2F * t + 2F, 3F) / 2F;
    }

    private static float easeOutCubic(float t) {
        return 1F - (float) Math.pow(1F - t, 3F);
    }

    private static float easeOutQuint(float t) {
        return 1F - (float) Math.pow(1F - t, 5F);
    }

    private static float easeInQuad(float t) {
        return t * t;
    }
}
