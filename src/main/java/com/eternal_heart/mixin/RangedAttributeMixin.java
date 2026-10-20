package com.eternal_heart.mixin;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 生命上限突破 —— 解除原版 {@code MAX_HEALTH} 的 1024 点硬上限。
 *
 * <p><b>为什么需要 Mixin</b>：原版 {@code Attributes.MAX_HEALTH} 是
 * {@code new RangedAttribute(..., 20.0, 1.0, 1024.0)}，所有写入（基础值、属性修饰符、
 * 装备加成）都要经过 {@link RangedAttribute#sanitizeValue(double)} 的
 * {@code Mth.clamp(value, 1.0, 1024.0)}。也就是说：</p>
 * <ul>
 *   <li>「最大生命值增加量」配置填 5000，实际生效上限永远只有 1024；</li>
 *   <li>过去只能建议玩家安装 AttributeFix —— 而它的实现正是同一处注入。</li>
 * </ul>
 *
 * <p>这里只对<b>最大生命值</b>这一条属性放开上限，其它 RangedAttribute
 * （护甲、移速、攻击距离等）保持原版夹取，避免顺带放开别的模组依赖的上限保护。</p>
 */
@Mixin(RangedAttribute.class)
public abstract class RangedAttributeMixin {

    /** MAX_HEALTH 的语言键（1.20.1 固定值，属性注册时使用）。 */
    private static final String MAX_HEALTH_KEY = "attribute.name.generic.max_health";

    @Inject(method = "sanitizeValue", at = @At("HEAD"), cancellable = true)
    private void eternalHeart$raiseMaxHealthCap(double value, CallbackInfoReturnable<Double> cir) {
        if (!ConfigValues.get(EternalHeartConfig.HEALTH_CAP_OVERRIDE)) return;
        if (!Double.isFinite(value)) return; // 非有限值交回原版夹取，避免血条算出 Infinity

        RangedAttribute self = (RangedAttribute) (Object) this;
        if (!MAX_HEALTH_KEY.equals(self.getDescriptionId())) return;

        // 只去掉上限：下限（原版 1.0）仍然保留，0 / 负数依旧被夹到 1
        cir.setReturnValue(Math.max(self.getMinValue(), value));
    }
}
