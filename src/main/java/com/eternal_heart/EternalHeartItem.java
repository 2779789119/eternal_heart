package com.eternal_heart;

import com.eternal_heart.features.FeatureManager;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import com.eternal_heart.integration.Integrations;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class EternalHeartItem extends Item implements ICurioItem {

    public EternalHeartItem(Properties properties) {
        super(properties);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, UUID uuid, ItemStack stack) {
        return FeatureManager.buildAttributeModifiers(slotContext, uuid, stack);
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {

        // 风味引言
        tooltip.add(Component.literal("◆ '无论凡人或不朽，都承认你的神性' ◆")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.empty());

        // 暴击/永怒机制
        tooltip.add(Component.literal("暴击率固定为 50%")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("暴击时 +10%，满 100% 后每击获得 10% 生命窃取")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("并获得 +5% 伤害与 +5% 伤害减免")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("受击时重置（MC 适配：" + EternalHeartConfig.FURY_MAX_STACKS.get() + " 层永怒叠层系统）")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.empty());

        // "此外还拥有"
        tooltip.add(Component.literal("此外还拥有：")
                .withStyle(ChatFormatting.DARK_GRAY));

        // --- 属性 ---
        double atkDmgPct = (EternalHeartConfig.ATTACK_DAMAGE.get() - 1.0) * 100;
        double atkSpdPct = (EternalHeartConfig.ATTACK_SPEED.get() - 1.0) * 100;
        tooltip.add(Component.literal(String.format("+%.0f%% 全部伤害  |  +%.0f%% 攻击速度", atkDmgPct, atkSpdPct))
                .withStyle(ChatFormatting.RED));

        double effHealth = Math.min(EternalHeartConfig.MAX_HEALTH.get(), Integrations.getEffectiveMaxHealthCap() - 20.0);
        String healthCapText = Integrations.isAttributeFixLoaded() ? " (AttributeFix: 无上限)" : " (MC 上限 1024)";
        tooltip.add(Component.literal(String.format("+%.0f 最大生命值" + healthCapText + "  +%.0f 护甲  +%.0f 护甲韧性",
                        effHealth,
                        EternalHeartConfig.ARMOR.get(),
                        EternalHeartConfig.ARMOR_TOUGHNESS.get()))
                .withStyle(ChatFormatting.RED));

        // --- 防御 ---
        StringBuilder defense = new StringBuilder();
        double kb = EternalHeartConfig.KNOCKBACK_RESIST.get();
        if (kb >= 1.0) defense.append("免疫击退  |  ");
        int dmgRedPct = (int)(EternalHeartConfig.DAMAGE_REDUCTION.get() * 100);
        defense.append(dmgRedPct).append("% 伤害减免");

        double cap = EternalHeartConfig.DAMAGE_CAP_RATIO.get();
        if (cap > 0) defense.append("  |  单次受伤≤").append((int)(cap * 100)).append("%最大生命");

        tooltip.add(Component.literal(defense.toString()).withStyle(ChatFormatting.GRAY));

        // --- 速度 ---
        double spdPct = EternalHeartConfig.MOVEMENT_SPEED.get() * 100;
        tooltip.add(Component.literal(String.format("+%.0f%% 移动速度  +%.0f 幸运", spdPct, EternalHeartConfig.LUCK.get()))
                .withStyle(ChatFormatting.AQUA));

        // --- 回复 ---
        double hpPerSec = EternalHeartConfig.TICK_REGEN.get() * 20;
        tooltip.add(Component.literal(String.format("被动生命回复（%.0f HP/s）", hpPerSec))
                .withStyle(ChatFormatting.GREEN));

        // --- 负面清除 ---
        int clearSec = EternalHeartConfig.DEBUFF_CLEAR_INTERVAL.get() / 20;
        tooltip.add(Component.literal(String.format("每 %d 秒清除负面效果", clearSec))
                .withStyle(ChatFormatting.YELLOW));

        // --- 免疫 ---
        StringBuilder immunity = new StringBuilder();
        if (EternalHeartConfig.FIRE_IMMUNE.get()) immunity.append("火焰");
        double fallPct = EternalHeartConfig.FALL_DAMAGE_RATIO.get();
        if (fallPct <= 0) immunity.append(immunity.isEmpty() ? "摔落" : " / 摔落");
        else immunity.append(immunity.isEmpty() ? "" : " / ").append((int)((1 - fallPct) * 100)).append("%摔落减免");
        if (EternalHeartConfig.DROWN_IMMUNE.get())
            immunity.append(immunity.isEmpty() ? "溺水" : " / 溺水");
        if (EternalHeartConfig.SUFFOCATE_IMMUNE.get())
            immunity.append(immunity.isEmpty() ? "窒息" : " / 窒息");
        double reflectPct = EternalHeartConfig.REFLECT_RATIO.get() * 100;
        immunity.append("  |  反伤 ").append((int)reflectPct).append("%");
        double expResist = EternalHeartConfig.EXPLOSION_RESIST.get();
        if (expResist > 0) immunity.append("  |  ").append((int)(expResist * 100)).append("%爆炸减免");
        tooltip.add(Component.literal(immunity.toString()).withStyle(ChatFormatting.LIGHT_PURPLE));

        tooltip.add(Component.empty());

        // --- 被动效果 ---
        StringBuilder passive = new StringBuilder();
        if (EternalHeartConfig.NIGHT_VISION.get()) passive.append("夜视");
        if (EternalHeartConfig.WATER_BREATHING.get())
            passive.append(passive.isEmpty() ? "水下呼吸" : " / 水下呼吸");
        if (EternalHeartConfig.HASTE_LEVEL.get() > 0)
            passive.append(passive.isEmpty() ? "" : " / ").append("急迫 ").append(EternalHeartConfig.HASTE_LEVEL.get());
        if (EternalHeartConfig.FIRE_RESISTANCE_POTION.get())
            passive.append(passive.isEmpty() ? "抗火" : " / 抗火");
        if (EternalHeartConfig.SATURATION.get())
            passive.append(passive.isEmpty() ? "饱和" : " / 饱和");
        tooltip.add(Component.literal(passive.toString()).withStyle(ChatFormatting.AQUA));

        // --- 战斗 ---
        StringBuilder combat = new StringBuilder();
        int dodgePct = (int)(EternalHeartConfig.DODGE_CHANCE.get() * 100);
        int refProjPct = (int)(EternalHeartConfig.REFLECT_PROJECTILE_CHANCE.get() * 100);
        combat.append("闪避 ").append(dodgePct).append("%  |  弹射反射 ").append(refProjPct).append("%");
        double auraRange = EternalHeartConfig.DAMAGE_AURA_RANGE.get();
        if (auraRange > 0) combat.append("  |  伤害光环(").append((int)auraRange).append("格)");
        double lowHp = EternalHeartConfig.LOW_HP_THRESHOLD.get();
        double lowHpMult = EternalHeartConfig.LOW_HP_DAMAGE_MULT.get();
        if (lowHp > 0) combat.append("  |  低血(").append((int)(lowHp * 100)).append("%)狂怒×").append((int)(lowHpMult * 100)).append("%");
        double bossBonus = EternalHeartConfig.BOSS_DAMAGE_BONUS.get();
        if (bossBonus > 0) combat.append("  |  Boss增伤+").append((int)(bossBonus * 100)).append("%");
        double exec = EternalHeartConfig.EXECUTE_THRESHOLD.get();
        if (exec > 0) combat.append("  |  斩杀 ").append((int)(exec * 100)).append("%");
        double ignite = EternalHeartConfig.IGNITE_CHANCE.get();
        if (ignite > 0) combat.append("  |  点燃 ").append((int)(ignite * 100)).append("%");
        tooltip.add(Component.literal(combat.toString()).withStyle(ChatFormatting.RED));

        // --- 护盾 ---
        StringBuilder shield = new StringBuilder();
        double sps = EternalHeartConfig.SHIELD_PER_SEC.get();
        double smax = EternalHeartConfig.SHIELD_MAX.get();
        if (sps > 0) shield.append("满血护盾自动充能(").append((int)smax).append("上限)");
        int burstDur = EternalHeartConfig.SPEED_BURST_DURATION.get();
        if (burstDur > 0)
            shield.append(shield.isEmpty() ? "" : "  |  ").append("受伤速度爆发(").append(burstDur / 20).append("秒)");
        tooltip.add(Component.literal(shield.toString()).withStyle(ChatFormatting.YELLOW));

        // --- 采集 ---
        StringBuilder harvest = new StringBuilder();
        if (EternalHeartConfig.AUTO_SMELT.get()) harvest.append("自动冶炼");
        if (EternalHeartConfig.VEIN_MINER.get())
            harvest.append(harvest.isEmpty() ? "连锁挖掘" : "  |  连锁挖掘(" + EternalHeartConfig.VEIN_MAX_BLOCKS.get() + "块)");
        int fortune = EternalHeartConfig.FORTUNE_BONUS.get();
        int looting = EternalHeartConfig.LOOTING_BONUS.get();
        if (fortune > 0) harvest.append(harvest.isEmpty() ? "" : "  |  ").append("时运+").append(fortune);
        if (looting > 0) harvest.append(harvest.isEmpty() ? "" : "  |  ").append("抢夺+").append(looting);
        tooltip.add(Component.literal(harvest.toString()).withStyle(ChatFormatting.YELLOW));

        // --- 生存 ---
        StringBuilder survive = new StringBuilder();
        if (EternalHeartConfig.UNDYING_TOTEM.get())
            survive.append("不死图腾(").append(EternalHeartConfig.UNDYING_COOLDOWN.get()).append("秒冷却)");
        if (EternalHeartConfig.VOID_RESCUE.get())
            survive.append(survive.isEmpty() ? "虚空救援" : " | 虚空救援");
        if (EternalHeartConfig.SOUL_BIND.get())
            survive.append(survive.isEmpty() ? "灵魂绑定" : " | 灵魂绑定");
        if (EternalHeartConfig.KILL_EXPLOSION.get())
            survive.append(survive.isEmpty() ? "击杀爆炸" : " | 击杀爆炸");
        double killHeal = EternalHeartConfig.KILL_HEAL_RATIO.get();
        if (killHeal > 0)
            survive.append(survive.isEmpty() ? "" : " | ").append("击杀恢复").append((int)(killHeal * 100)).append("%血量");
        if (EternalHeartConfig.INFINITE_ARROWS.get())
            survive.append(survive.isEmpty() ? "无限箭矢" : " | 无限箭矢");
        if (EternalHeartConfig.ENDER_CHEST_REMOTE.get())
            survive.append(survive.isEmpty() ? "便携末影箱" : " | 便携末影箱");
        tooltip.add(Component.literal(survive.toString()).withStyle(ChatFormatting.GREEN));

        // --- 适应 ---
        StringBuilder adapt = new StringBuilder();
        double magRange = EternalHeartConfig.MAGNET_RANGE.get();
        if (magRange > 0) adapt.append("物品磁铁(").append((int)magRange).append("格)");
        double glowRange = EternalHeartConfig.GLOW_RANGE.get();
        if (glowRange > 0) adapt.append(adapt.isEmpty() ? "" : "  |  ").append("生物侦测(").append((int)glowRange).append("格)");
        if (EternalHeartConfig.LAVA_WALKING.get())
            adapt.append(adapt.isEmpty() ? "熔岩行走" : "  |  熔岩行走");
        if (EternalHeartConfig.WATER_WALKING.get())
            adapt.append(adapt.isEmpty() ? "水上行走" : "  |  水上行走");
        if (EternalHeartConfig.WALL_CLIMB.get())
            adapt.append(adapt.isEmpty() ? "爬墙" : "  |  爬墙");
        tooltip.add(Component.literal(adapt.toString()).withStyle(ChatFormatting.LIGHT_PURPLE));

        // --- 机动 ---
        StringBuilder mobility = new StringBuilder();
        if (EternalHeartConfig.CREATIVE_FLIGHT.get()) mobility.append("创造飞行");
        if (EternalHeartConfig.JUMP_BOOST.get())
            mobility.append(mobility.isEmpty() ? "跳跃强化" : "  |  跳跃强化");
        if (EternalHeartConfig.STEP_ASSIST.get())
            mobility.append(mobility.isEmpty() ? "自动上坡" : "  |  自动上坡");
        if (EternalHeartConfig.SLOW_FALL_GLIDE.get())
            mobility.append(mobility.isEmpty() ? "缓降滑翔" : "  |  缓降滑翔");
        if (EternalHeartConfig.DOLPHINS_GRACE.get())
            mobility.append(mobility.isEmpty() ? "海豚的恩惠" : "  |  海豚的恩惠");
        tooltip.add(Component.literal(mobility.toString()).withStyle(ChatFormatting.AQUA));

        // --- 光环 ---
        StringBuilder aura = new StringBuilder();
        double witherR = EternalHeartConfig.WITHER_AURA_RANGE.get();
        if (witherR > 0) aura.append("凋零光环(").append((int)witherR).append("格)");
        double peaceR = EternalHeartConfig.PEACE_AURA_RANGE.get();
        if (peaceR > 0) aura.append(aura.isEmpty() ? "" : "  |  ").append("和平光环(").append((int)peaceR).append("格)");
        double growthR = EternalHeartConfig.GROWTH_AURA_RANGE.get();
        if (growthR > 0) aura.append(aura.isEmpty() ? "" : "  |  ").append("生长光环(").append((int)growthR).append("格)");
        if (!aura.isEmpty())
            tooltip.add(Component.literal(aura.toString()).withStyle(ChatFormatting.DARK_PURPLE));

        // --- 战斗生存增强 ---
        StringBuilder combatPlus = new StringBuilder();
        if (EternalHeartConfig.KNOCKBACK_IMMUNITY.get())
            combatPlus.append("击退免疫");
        if (EternalHeartConfig.AUTO_POTION.get()) {
            int potPct = (int)(EternalHeartConfig.AUTO_POTION_THRESHOLD.get() * 100);
            combatPlus.append(combatPlus.isEmpty() ? "" : "  |  ").append("低血(").append(potPct).append("%)自动喝药");
        }
        if (EternalHeartConfig.LIGHTNING_REFLECT.get()) {
            int lrPct = (int)(EternalHeartConfig.LIGHTNING_REFLECT_CHANCE.get() * 100);
            combatPlus.append(combatPlus.isEmpty() ? "" : "  |  ").append("受伤闪电反击(").append(lrPct).append("%)");
        }
        if (EternalHeartConfig.KILL_CHAIN.get()) {
            int kcRange = EternalHeartConfig.KILL_CHAIN_RANGE.get().intValue();
            combatPlus.append(combatPlus.isEmpty() ? "" : "  |  ").append("击杀连锁(").append(kcRange).append("格)");
        }
        if (EternalHeartConfig.PERMANENT_LUCK.get())
            combatPlus.append(combatPlus.isEmpty() ? "永久幸运" : "  |  永久幸运");
        if (!combatPlus.isEmpty())
            tooltip.add(Component.literal(combatPlus.toString()).withStyle(ChatFormatting.DARK_RED));

        // --- 探索便利 ---
        StringBuilder explore = new StringBuilder();
        if (EternalHeartConfig.AUTO_TORCH.get())
            explore.append("自动火把(光照<").append(EternalHeartConfig.AUTO_TORCH_LIGHT_LEVEL.get()).append(")");
        if (EternalHeartConfig.AUTO_DOOR.get())
            explore.append(explore.isEmpty() ? "自动开关门" : "  |  自动开关门");
        if (EternalHeartConfig.ORE_HIGHLIGHT.get())
            explore.append(explore.isEmpty() ? "洞穴高亮" : "  |  洞穴高亮(").append(EternalHeartConfig.ORE_HIGHLIGHT_RANGE.get().intValue()).append("格)");
        if (EternalHeartConfig.AUTO_FISH.get())
            explore.append(explore.isEmpty() ? "自动钓鱼" : "  |  自动钓鱼");
        double chestR = EternalHeartConfig.AUTO_OPEN_CHEST_RANGE.get();
        if (chestR > 0)
            explore.append(explore.isEmpty() ? "" : "  |  ").append("宝箱磁铁(").append((int)chestR).append("格)");
        if (!explore.isEmpty())
            tooltip.add(Component.literal(explore.toString()).withStyle(ChatFormatting.AQUA));

        // --- 机动便利 ---
        StringBuilder conv = new StringBuilder();
        if (EternalHeartConfig.DASH.get())
            conv.append("冲刺(双击W,×").append(String.format("%.1f", EternalHeartConfig.DASH_FORCE.get())).append(")");
        if (EternalHeartConfig.AUTO_REFILL.get())
            conv.append(conv.isEmpty() ? "自动补货" : "  |  自动补货");
        if (EternalHeartConfig.INFINITE_BUCKET.get())
            conv.append(conv.isEmpty() ? "无限水/岩浆桶" : "  |  无限水/岩浆桶");
        if (!conv.isEmpty())
            tooltip.add(Component.literal(conv.toString()).withStyle(ChatFormatting.YELLOW));

        // --- 结尾 ---
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("—— 泰拉瑞亚 Fargo's Soul of Eternity 完整还原 ——")
                .withStyle(ChatFormatting.GOLD).withStyle(ChatFormatting.ITALIC));
        double xpMult = EternalHeartConfig.XP_MULTIPLIER.get();
        tooltip.add(Component.literal(String.format("经验获取 ×%.1f", xpMult))
                .withStyle(ChatFormatting.GREEN));

        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    protected String getTooltipItemName() {
        return BuiltInRegistries.ITEM.getKey(this).getPath();
    }
}
