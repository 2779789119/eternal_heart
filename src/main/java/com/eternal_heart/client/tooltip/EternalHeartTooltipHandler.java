package com.eternal_heart.client.tooltip;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.EternalHeartMod;
import com.eternal_heart.client.RainbowPalette;
import com.eternal_heart.config.ConfigValues;
import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 永恒之心提示框的总装：固定头部 + 分隔线 + 正文"窗口切片" + 逐字炫彩。
 *
 * 版式（和参考图一致）：
 * - 最上面固定钉着"头部"：物品名、别的模组加的槽位行、◆祷文◆，滚轮怎么滚都不动；
 * - 头部下面固定一条分隔线（{@link SeparatorTooltip}，颜色和边框彩虹同步流动）；
 * - 滚轮翻阅的只是线以下的正文，行号提示也只数正文的行。
 *
 * 炫彩做法：不再使用富文本"样式段"，而是把每一行换成自定义组件，
 * 渲染时逐字调用 Font.drawInBatch 直接上色（和浮动 flavor 行同一机制）。
 * 每次绘制传下去的段落只含 1 个字符，样式段数天生极低，
 * 文字再多也不会触发整合包字体库的段数上限崩溃，渐变却保持逐字平滑。
 *
 * 滚动做法：展开时每帧只把"放得进屏幕的那几行正文"装进提示框，
 * 行号窗口由滚轮移动（见 TooltipScrollHandler）。
 * 提示框因此永远不超过屏幕，任何屏幕/窗口尺寸下滚轮都能翻阅。
 */
@Mod.EventBusSubscriber(modid = "eternal_heart", bus = Mod.EventBusSubscriber.Bus.FORGE, value = {Dist.CLIENT})
public final class EternalHeartTooltipHandler {
    /** 每个字符推进的色相（和最早版本一致，逐字渐变最顺滑的参数）。 */
    private static final float HUE_PER_CHAR = 0.018f;
    /** 提示框换行宽度（按字体"测量值"折行）。整合包的字体库实际画出来的字
     *  比测量值宽一成多，所以测量值要留出余量，画出来才不会撑破边框。 */
    private static final int WRAP_WIDTH = 220;
    /** 提示框固定宽度（像素）：文字短时不缩窄，文字长时（字体库把字画宽）也不撑破。 */
    private static final int FIXED_WIDTH = 264;
    /** 原版提示框行距：行高 10 + 行间隔 2。 */
    private static final int LINE_PITCH = 12;
    /** 分隔线一行占的高度：线高 5 + 行间隔 2。 */
    private static final int SEPARATOR_PITCH = 7;
    /** 留给边框内边距、行号提示等的纵向余量（像素）。 */
    private static final int VERTICAL_MARGIN = 32;

    private EternalHeartTooltipHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void gather(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !stack.is(EternalHeartMod.ETERNAL_HEART.get())) {
            return;
        }
        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        if (elements.isEmpty()) {
            return;
        }
        // 先把别的模组塞进来的"接管组件"清干净（收起/展开两条路都要清）。
        stripForeignControllers(elements);
        if (elements.isEmpty()) {
            return;
        }
        boolean rainbow = ((Boolean)ConfigValues.get(EternalHeartConfig.RAINBOW_NAME)).booleanValue();
        if (!EternalHeartTooltipState.expanded()) {
            TooltipScrollHandler.clear();
            collapsed(elements, rainbow);
        } else {
            windowed(stack, elements, rainbow);
        }
        // 塞一个"只占宽不占高"的占位组件：原版框宽取所有组件最大值，
        // 这样提示框宽度恒定，不随文字长短伸缩，也永远装得下画出来的字。
        elements.add(Either.right(new MinWidthTooltip(FIXED_WIDTH)));
    }

    /**
     * 清掉别的模组往提示框里塞的"接管组件"。
     *
     * 整合包里的提示框美化模组（Obscure Tooltips）是靠一个 mixin 往每件物品的提示框里
     * 插一个它自己的标记组件（StackBuffer），它的渲染器一旦在组件列表里找到这个标记，
     * 就把整份提示框接过去自己画——于是就有了那条固定的横线和它自己的花边框。
     * 我们只保留"原版自己的"和"我们自己的"组件，这个标记一消失，它就找不到接管点，
     * 提示框交回原版流程绘制——它画的那条位置随内容乱跑的横线也随之消失。
     * 交回来之后版式由我们自己定：固定头部下面由 {@link SeparatorTooltip}
     * 画一条端端正正的分隔线（见 {@link ClientSeparatorTooltip}），
     * 外框则由 {@link EternalHeartTooltipFrame} 在原版的配色事件里
     * 接管绘制，画一圈流动的炫彩边框。
     */
    private static void stripForeignControllers(List<Either<FormattedText, TooltipComponent>> elements) {
        elements.removeIf(entry -> {
            TooltipComponent component = entry.right().orElse(null);
            return component != null && !isLocalComponent(component);
        });
    }

    /** 这个图片类组件是原版自己的、或者我们自己的吗？（第三方模组的接管组件返回 false） */
    private static boolean isLocalComponent(TooltipComponent component) {
        for (Class<?> type = component.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            String name = type.getName();
            if (name.startsWith("net.minecraft.") || name.startsWith("com.eternal_heart.")) {
                return true;
            }
        }
        return false;
    }

    /** 没按 Shift：固定头部（物品名 + 槽位行 + ◆祷文◆）+ 分隔线 + 灰字提示。 */
    private static void collapsed(List<Either<FormattedText, TooltipComponent>> elements, boolean rainbow) {
        Either<FormattedText, TooltipComponent> head = elements.remove(0);
        List<Component> slots = extractSlotLines(elements);
        Font font = Minecraft.getInstance().font;
        float drift = drift();
        int[] cursor = new int[]{0};

        elements.clear();
        appendStyledLine(elements, head, font, drift, cursor, rainbow);
        for (Component slot : slots) {
            appendStyledLine(elements, Either.left(slot), font, drift, cursor, rainbow);
        }
        elements.add(Either.right(new FloatingLineTooltip(Component.translatable("tooltip.eternal_heart.flavor"))));
        elements.add(Either.right(new SeparatorTooltip(FIXED_WIDTH)));
        elements.add(Either.left(Component.translatable("tooltip.eternal_heart.expand_hint")
            .withStyle(ChatFormatting.DARK_GRAY)));
    }

    /**
     * 按住 Shift：固定头部 + 分隔线钉在最上面，
     * 线以下的正文按滚轮窗口切片——滚轮翻的永远不含头部。
     */
    private static void windowed(ItemStack stack, List<Either<FormattedText, TooltipComponent>> elements, boolean rainbow) {
        Font font = Minecraft.getInstance().font;
        float drift = rainbow ? drift() : 0.0f;
        int[] cursor = new int[]{0};

        // 1) 分成"固定头部"和"可滚动正文"两组可视行：
        //    第一行（物品名）和别的模组加的槽位行进头部，其余全进正文；
        //    顺手算好逐字颜色（隐藏的也算，保证色相跟内容不跟窗口跑）。
        List<VisualLine> header = new ArrayList<>();
        List<VisualLine> body = new ArrayList<>();
        int index = 0;
        for (Either<FormattedText, TooltipComponent> entry : elements) {
            boolean first = index++ == 0;
            FormattedText text = entry.left().orElse(null);
            if (text == null) {
                TooltipComponent other = entry.right().orElse(null);
                body.add(other != null ? VisualLine.foreign(other) : VisualLine.blank());
                continue;
            }
            boolean pinned = first || isSlotLine(text.getString());
            Style base = text instanceof Component ? ((Component)text).getStyle() : Style.EMPTY;
            String plain = text.getString();
            if (plain.isEmpty()) {
                (pinned ? header : body).add(VisualLine.blank());
                continue;
            }
            for (FormattedCharSequence slice : font.split(text, WRAP_WIDTH)) {
                String piece = plainOf(slice);
                if (rainbow) {
                    (pinned ? header : body).add(VisualLine.rainbow(piece, base, colors(piece, drift, cursor)));
                } else {
                    cursor[0] += piece.codePointCount(0, piece.length());
                    (pinned ? header : body).add(VisualLine.plain(piece, base));
                }
            }
        }
        header.add(VisualLine.foreign(new FloatingLineTooltip(Component.translatable("tooltip.eternal_heart.flavor"))));

        // 2) 按屏幕高度算正文窗口行数：先扣掉固定头部和分隔线占的像素
        int guiHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int headerPixels = 0;
        for (VisualLine line : header) {
            headerPixels += (line.component != null ? 11 : 10) + 2;
        }
        int windowLines = Math.max(3, (guiHeight - VERTICAL_MARGIN - headerPixels - SEPARATOR_PITCH) / LINE_PITCH);
        int total = body.size();
        boolean scrollable = total > windowLines;

        // 3) 重装：头部 → 分隔线 → 正文窗口（放不下就切窗口 + 底部加行号提示，只数正文的行）
        List<Either<FormattedText, TooltipComponent>> rebuilt = new ArrayList<>();
        for (VisualLine line : header) {
            append(rebuilt, line, rainbow);
        }
        rebuilt.add(Either.right(new SeparatorTooltip(FIXED_WIDTH)));
        if (!scrollable) {
            TooltipScrollHandler.clear();
            for (VisualLine line : body) {
                append(rebuilt, line, rainbow);
            }
        } else {
            int contentSlots = windowLines - 1;
            TooltipScrollHandler.present(stack, total, contentSlots);
            int start = Math.max(0, Math.min(TooltipScrollHandler.start(), total - contentSlots));
            int end = Math.min(total, start + contentSlots);
            for (int i = start; i < end; ++i) {
                append(rebuilt, body.get(i), rainbow);
            }
            rebuilt.add(Either.left(Component.translatable("tooltip.eternal_heart.scroll_info",
                start + 1, end, total).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
        }

        elements.clear();
        elements.addAll(rebuilt);
    }

    /**
     * 判断是不是别的模组往提示框里加的"槽位"说明行（如"槽位：永恒之心"）。
     * 它跟着物品名一起钉在头部，不参与滚动。
     */
    private static boolean isSlotLine(String plain) {
        if (plain.isEmpty()) {
            return false;
        }
        return plain.contains("槽位") || plain.toLowerCase(java.util.Locale.ROOT).contains("slot");
    }

    /** 把槽位行从元素列表里摘出来（保持原顺序），剩下的留在列表里当正文。 */
    private static List<Component> extractSlotLines(List<Either<FormattedText, TooltipComponent>> elements) {
        List<Component> slots = new ArrayList<>();
        elements.removeIf(entry -> {
            FormattedText text = entry.left().orElse(null);
            if (!(text instanceof Component component) || !isSlotLine(component.getString())) {
                return false;
            }
            slots.add(component);
            return true;
        });
        return slots;
    }

    /** 单行文本进提示框：炫彩开着就折行逐字上色，关着就原样保留。 */
    private static void appendStyledLine(List<Either<FormattedText, TooltipComponent>> out,
        Either<FormattedText, TooltipComponent> entry, Font font, float drift, int[] cursor, boolean rainbow) {
        FormattedText text = entry.left().orElse(null);
        if (rainbow && text != null && !text.getString().isEmpty()) {
            Style base = text instanceof Component ? ((Component)text).getStyle() : Style.EMPTY;
            for (FormattedCharSequence slice : font.split(text, WRAP_WIDTH)) {
                String piece = plainOf(slice);
                if (piece.isEmpty()) {
                    continue;
                }
                out.add(Either.right(new RainbowLineTooltip(piece, colors(piece, drift, cursor), base)));
            }
            return;
        }
        out.add(entry);
    }

    private static void append(List<Either<FormattedText, TooltipComponent>> out, VisualLine line, boolean rainbow) {
        if (line.component != null) {
            out.add(Either.right(line.component));
            return;
        }
        if (line.text.isEmpty()) {
            out.add(Either.left(Component.empty()));
            return;
        }
        if (rainbow && line.colors != null) {
            out.add(Either.right(new RainbowLineTooltip(line.text, line.colors, line.base)));
        } else {
            out.add(Either.left(Component.literal(line.text).withStyle(line.base)));
        }
    }

    /** 从折行结果里取出纯文本（原版 FormattedCharSequence 没有 getString，得遍历一遍）。 */
    private static String plainOf(FormattedCharSequence sequence) {
        StringBuilder builder = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            builder.appendCodePoint(codePoint);
            return true;
        });
        return builder.toString();
    }

    /** 逐字颜色：色相 = 时间漂移 + 全局字序，和最早版本的算法一致。 */
    private static int[] colors(String text, float drift, int[] cursor) {
        int n = text.codePointCount(0, text.length());
        int[] out = new int[n];
        for (int i = 0; i < n; ++i) {
            out[i] = 0xFF000000 | RainbowPalette.hueToRgb(drift + (float)cursor[0] * HUE_PER_CHAR);
            ++cursor[0];
        }
        return out;
    }

    private static float drift() {
        float speed = ((Double)ConfigValues.get(EternalHeartConfig.RAINBOW_NAME_SPEED)).floatValue();
        return (float)Util.getMillis() / 1000.0f * speed;
    }

    /** 一条"可视行"的中转数据。 */
    private static final class VisualLine {
        final String text;
        final Style base;
        final int[] colors;
        final TooltipComponent component;

        private VisualLine(String text, Style base, int[] colors, TooltipComponent component) {
            this.text = text;
            this.base = base;
            this.colors = colors;
            this.component = component;
        }

        static VisualLine blank() {
            return new VisualLine("", Style.EMPTY, null, null);
        }

        static VisualLine plain(String text, Style base) {
            return new VisualLine(text, base, null, null);
        }

        static VisualLine rainbow(String text, Style base, int[] colors) {
            return new VisualLine(text, base, colors, null);
        }

        static VisualLine foreign(TooltipComponent component) {
            return new VisualLine("", Style.EMPTY, null, component);
        }
    }
}
