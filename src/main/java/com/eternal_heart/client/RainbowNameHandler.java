package com.eternal_heart.client;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.EternalHeartMod;
import com.eternal_heart.config.ConfigValues;
import com.mojang.datafixers.util.Either;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 「永恒之心」提示框的炫彩文字 —— 名字与全部说明行都按彩虹色逐字渐变，并随时间流动。
 *
 * <p><b>为什么接管提示框，而不是覆写 {@code Item#getName}</b>：{@code ItemStack} 会把第一次算出的
 * 名字缓存进 {@code hoverName} 字段，覆写只能得到<b>静态</b>颜色，流动不起来。
 * 而提示框每帧都会重建组件（{@link RenderTooltipEvent.GatherComponents} 每帧触发），
 * 在这里逐帧替换才真的会动。</p>
 *
 * <p>替换范围：提示框里所有纯文本行（物品名、风味文本、属性说明、其它模组追加的
 * 「栏位」等），空行与非文本 TooltipComponent（图片等）原样保留。
 * 每行的装饰（斜体 / 加粗 / 下划线）沿用原组件样式，只替换颜色。</p>
 */
@Mod.EventBusSubscriber(modid = EternalHeartMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RainbowNameHandler {

    /**
     * 每个字符推进的色相（0~1 为一圈）。
     * 取 0.018 表示约 56 个字符走完一整圈彩虹：一行几十个字就能看到完整的七彩过渡，
     * 相邻两行之间又保持连续，不会每行开头都重新从红色开始。
     */
    private static final float HUE_PER_CHAR = 0.018F;

    private RainbowNameHandler() {
    }

    @SubscribeEvent
    public static void onGatherTooltipComponents(RenderTooltipEvent.GatherComponents event) {
        if (!ConfigValues.get(EternalHeartConfig.RAINBOW_NAME)) return;

        ItemStack stack = event.getItemStack();
        if (!stack.is(EternalHeartMod.ETERNAL_HEART.get())) return;

        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        if (elements.isEmpty()) return;

        float speed = ConfigValues.get(EternalHeartConfig.RAINBOW_NAME_SPEED).floatValue();
        float drift = Util.getMillis() / 1000.0F * speed;   // 秒 × 圈/秒 = 已经流过的圈数
        // 跨行累计的字符序号：让彩虹在整段提示框里连续铺开（含行首缩进）
        int[] cursor = {0};

        for (int i = 0; i < elements.size(); i++) {
            FormattedText line = elements.get(i).left().orElse(null);
            if (line == null) continue;                 // 图片等非文本元素不动
            String plain = line.getString();
            if (plain.isEmpty()) continue;              // 空行保留，避免把间隔吃掉

            // 保留原有斜体 / 加粗等装饰，只把颜色换成彩虹
            Style base = line instanceof Component component ? component.getStyle() : Style.EMPTY;
            elements.set(i, Either.left(rainbow(plain, base, drift, cursor)));
        }
    }

    /** 把一行纯文本拆成「每字一色」的彩虹组件。 */
    private static MutableComponent rainbow(String text, Style base, float drift, int[] cursor) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                result.append(Component.literal("\n").withStyle(base));
                continue;
            }
            int rgb = RainbowPalette.hueToRgb(drift + cursor[0]++ * HUE_PER_CHAR);
            result.append(Component.literal(String.valueOf(c))
                    .withStyle(base.withColor(TextColor.fromRgb(rgb))));
        }
        return result;
    }
}
