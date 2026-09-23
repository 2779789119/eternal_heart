package com.eternal_heart;

import com.eternal_heart.config.ConfigValues;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * 永怒（Eternal Fury）自定义效果
 *
 * 通过属性修饰符（MULTIPLY_TOTAL）提供攻击伤害加成。效果等级（amplifier + 1）即等于
 * 当前永怒层数；重写 getAttributeModifierValue 使修饰符 amount 按层数放大，从而
 * 「每层 +FURY_DAMAGE_PER_STACK 伤害」天然成立，并走原版伤害计算管线
 * （自动尊重护甲、附魔、抗性等）。
 *
 * 满怒额外 +5% 伤害由 CombatSystem 在伤害计算处统一处理（与 DefenseSystem 中
 * 满怒 -5% 减伤保持对称，逻辑集中、便于阅读）。
 */
public class EternalFuryEffect extends MobEffect {

    /** 固定 UUID，确保属性修饰符稳定，同一 UUID 不会重复叠加 */
    private static final UUID FURY_DAMAGE_UUID =
            UUID.fromString("f2a3c1d4-5e6b-7f80-91a2-b3c4d5e6f7a8");

    public EternalFuryEffect() {
        // 有益效果，橙红色（呼应「永怒」火焰意象）
        super(MobEffectCategory.BENEFICIAL, 0xFF5A1F);

        // Registration must not read config before it loads; the override reads the live value.
        // 本 Forge 版本唯一的 addAttributeModifier 重载为 (Attribute, String, double, Operation)，
        // 其内部会将第二个参数 name 当作 UUID 解析（UUID.fromString(name)）。因此必须传入合法 UUID 字符串，
        // 不能传普通名称；amount 通过第三个参数 double 传入。
        this.addAttributeModifier(Attributes.ATTACK_DAMAGE,
                FURY_DAMAGE_UUID.toString(),
                0.05,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public double getAttributeModifierValue(int pAmplifier, AttributeModifier pModifier) {
        // 每层永怒提供 FURY_DAMAGE_PER_STACK 攻击伤害加成：
        // 实际 amount = 当前配置 * 层数（支持服务端配置热刷新）
        return ConfigValues.get(EternalHeartConfig.FURY_DAMAGE_PER_STACK) * (double) (pAmplifier + 1);
    }
}
