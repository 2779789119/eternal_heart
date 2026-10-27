package com.eternal_heart.client.tooltip;

import com.eternal_heart.EternalHeartMod;
import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 收起状态下的提示框内容。
 *
 * <p>不按 Shift 时，永恒之心只保留「物品名 + 一句会浮动呼吸的箴言」；按 Shift 则完全不干预，
 * 由 {@code EternalHeartItem#appendHoverText} 输出完整列表。</p>
 *
 * <p>用 {@link EventPriority#LOWEST} 是为了在所有其它模组（属性、饰品、遗物突变等）都往
 * elements 里加完行之后再动手，否则先清后加等于没清。</p>
 */
@Mod.EventBusSubscriber(modid = EternalHeartMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class EternalHeartTooltipHandler {

    private EternalHeartTooltipHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void gather(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !stack.is(EternalHeartMod.ETERNAL_HEART.get())) return;
        if (EternalHeartTooltipState.expanded()) return;

        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        if (elements.isEmpty()) return;
        // 第 0 行是物品名，保留；其余（数值、融合列表、其它模组追加的行）全部收起
        while (elements.size() > 1) elements.remove(elements.size() - 1);
        elements.add(Either.right(new FloatingLineTooltip(
                Component.translatable("tooltip.eternal_heart.flavor"))));
        // 不提示的话玩家根本不知道这里有内容可看
        elements.add(Either.left(Component.translatable("tooltip.eternal_heart.expand_hint")
                .withStyle(ChatFormatting.DARK_GRAY)));
    }
}
