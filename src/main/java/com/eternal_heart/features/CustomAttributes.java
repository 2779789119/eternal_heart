package com.eternal_heart.features;

import com.eternal_heart.core.Numbers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 自定义属性条目 —— 「为任意已注册属性（含其它模组）添加修饰符」的解析与序列化。
 *
 * <p>配置里每项一条文本，空格分隔：</p>
 * <pre>
 *   &lt;属性ID&gt; &lt;数值&gt; [操作类型]
 *   例： "pehkui:base_scale 0.25 multiply_total"
 *        "apotheosis:crit_damage 15 addition"
 * </pre>
 *
 * <p>数值语义即 {@link AttributeModifier} 的 amount，<b>不做总倍率换算</b>：
 * 其它模组属性的量纲千差万别，直接填修饰符原始值才可预期。
 * 操作类型：{@code addition}（默认）/ {@code multiply_base} / {@code multiply_total}。</p>
 */
public final class CustomAttributes {

    /** 操作类型的固定顺序（界面按此顺序循环切换）。 */
    public static final List<AttributeModifier.Operation> OPERATIONS = List.of(
            AttributeModifier.Operation.ADDITION,
            AttributeModifier.Operation.MULTIPLY_BASE,
            AttributeModifier.Operation.MULTIPLY_TOTAL);

    /** 单条自定义属性（已校验：属性已注册、数值有限）。 */
    public record Spec(ResourceLocation attribute, double value, AttributeModifier.Operation operation) {
    }

    private CustomAttributes() {
    }

    /** 解析全部条目：非法行（属性未注册 / 数值不可读）静默跳过，坏配置不阻塞其余属性。 */
    public static List<Spec> parse(List<? extends String> raw) {
        List<Spec> specs = new ArrayList<>(raw.size());
        for (String line : raw) {
            Spec spec = parseLine(line);
            if (spec != null) specs.add(spec);
        }
        return specs;
    }

    /** 解析单行；非法返回 null。 */
    public static Spec parseLine(String line) {
        if (line == null) return null;
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 2) return null;

        ResourceLocation id = ResourceLocation.tryParse(parts[0]);
        if (id == null || !ForgeRegistries.ATTRIBUTES.containsKey(id)) return null;

        double value;
        try {
            value = Double.parseDouble(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (!Double.isFinite(value)) return null;

        AttributeModifier.Operation operation = parts.length >= 3
                ? operation(parts[2]) : AttributeModifier.Operation.ADDITION;
        if (operation == null) return null;
        return new Spec(id, value, operation);
    }

    /** 序列化回配置文本（界面 / 命令写入时使用）。 */
    public static String format(ResourceLocation attribute, double value, AttributeModifier.Operation operation) {
        return attribute + " " + Numbers.format(value) + " " + operationName(operation);
    }

    public static String operationName(AttributeModifier.Operation operation) {
        return operation.name().toLowerCase(Locale.ROOT);
    }

    /** 解析操作类型名（兼容简写）；非法返回 null。 */
    public static AttributeModifier.Operation operation(String name) {
        if (name == null) return null;
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "addition", "add" -> AttributeModifier.Operation.ADDITION;
            case "multiply_base", "base" -> AttributeModifier.Operation.MULTIPLY_BASE;
            case "multiply_total", "total" -> AttributeModifier.Operation.MULTIPLY_TOTAL;
            default -> null;
        };
    }

    /**
     * 每条自定义属性一个确定性 UUID：由属性 ID 派生，
     * 因此同一属性永不重复叠加，热刷新也能精确移除「上一次配置里的条目」。
     */
    public static UUID uuidFor(ResourceLocation attribute) {
        return UUID.nameUUIDFromBytes(("eternal_heart:custom_attribute:" + attribute)
                .getBytes(StandardCharsets.UTF_8));
    }

    /** 属性本地化名键（用于界面 / 提示显示）。 */
    public static String displayNameKey(ResourceLocation id) {
        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(id);
        return attribute == null ? id.toString() : attribute.getDescriptionId();
    }
}
