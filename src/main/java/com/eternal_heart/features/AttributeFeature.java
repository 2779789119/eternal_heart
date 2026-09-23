package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.core.Ticker;
import com.google.common.collect.Multimap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import top.theillusivec4.curios.api.SlotContext;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 属性系统 —— 永恒之心提供的全部基础属性修饰符（伤害 / 攻速 / 生命 / 护甲 / 韧性 /
 * 击退抗性 / 移速 / 幸运）。
 *
 * <p>重写要点：<b>声明式属性表</b>。历史上这 8 条属性在「装备注册」与「配置热刷新」
 * 两处各写了一遍（共 16 段几乎相同的代码，改一个属性要改两处、极易漏改）；
 * 现在用一张 {@link Entry} 表描述「配置项 → 属性 → UUID → 名称 → 操作类型 → 偏移」，
 * 两个入口共用同一张表遍历。</p>
 *
 * <p>热刷新语义保持不变：用与注册完全相同的固定 UUID，先移除旧修饰符再按当前配置重建，
 * 因此「配置改动立即生效，无需卸下再佩戴」，且重复刷新不会叠加。</p>
 */
public class AttributeFeature implements IFeature {

    /**
     * 单条属性描述。
     *
     * @param source    配置项
     * @param attribute 目标属性（延迟解析：Forge 自注册属性必须等注册完成才能 get()）
     * @param id        固定 UUID（同一 UUID 不会重复叠加）
     * @param label     修饰符名称（调试可读）
     * @param operation 操作类型
     * @param offset    施加值 = 配置值 + 偏移（乘算类配置以「总倍率」表示，故偏移 -1）
     */
    private record Entry(ForgeConfigSpec.DoubleValue source, Supplier<Attribute> attribute, UUID id, String label,
                         AttributeModifier.Operation operation, double offset) {

        double amount() {
            return ConfigValues.get(source) + offset;
        }

        Attribute target() {
            return attribute.get();
        }
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry(EternalHeartConfig.ATTACK_DAMAGE, () -> Attributes.ATTACK_DAMAGE,
                    UUID.fromString("e1a00001-0000-0000-0000-000000000000"), "Eternal Heart Attack Damage",
                    AttributeModifier.Operation.MULTIPLY_TOTAL, -1.0),
            new Entry(EternalHeartConfig.ATTACK_SPEED, () -> Attributes.ATTACK_SPEED,
                    UUID.fromString("e1a00002-0000-0000-0000-000000000000"), "Eternal Heart Attack Speed",
                    AttributeModifier.Operation.MULTIPLY_TOTAL, -1.0),
            new Entry(EternalHeartConfig.MAX_HEALTH, () -> Attributes.MAX_HEALTH,
                    UUID.fromString("e1a00003-0000-0000-0000-000000000000"), "Eternal Heart Max Health",
                    AttributeModifier.Operation.ADDITION, 0.0),
            new Entry(EternalHeartConfig.ARMOR, () -> Attributes.ARMOR,
                    UUID.fromString("e1a00004-0000-0000-0000-000000000000"), "Eternal Heart Armor",
                    AttributeModifier.Operation.ADDITION, 0.0),
            new Entry(EternalHeartConfig.ARMOR_TOUGHNESS, () -> Attributes.ARMOR_TOUGHNESS,
                    UUID.fromString("e1a00005-0000-0000-0000-000000000000"), "Eternal Heart Armor Toughness",
                    AttributeModifier.Operation.ADDITION, 0.0),
            new Entry(EternalHeartConfig.KNOCKBACK_RESIST, () -> Attributes.KNOCKBACK_RESISTANCE,
                    UUID.fromString("e1a00006-0000-0000-0000-000000000000"), "Eternal Heart Knockback Resist",
                    AttributeModifier.Operation.ADDITION, 0.0),
            new Entry(EternalHeartConfig.MOVEMENT_SPEED, () -> Attributes.MOVEMENT_SPEED,
                    UUID.fromString("e1a00007-0000-0000-0000-000000000000"), "Eternal Heart Movement Speed",
                    AttributeModifier.Operation.MULTIPLY_TOTAL, 0.0),
            new Entry(EternalHeartConfig.LUCK, () -> Attributes.LUCK,
                    UUID.fromString("e1a00008-0000-0000-0000-000000000000"), "Eternal Heart Luck",
                    AttributeModifier.Operation.ADDITION, 0.0),
            // ---- 扩展属性 ----
            new Entry(EternalHeartConfig.SWIM_SPEED, () -> ForgeMod.SWIM_SPEED.get(),
                    UUID.fromString("e1a00009-0000-0000-0000-000000000000"), "Eternal Heart Swim Speed",
                    AttributeModifier.Operation.MULTIPLY_TOTAL, -1.0),
            new Entry(EternalHeartConfig.FLYING_SPEED, () -> Attributes.FLYING_SPEED,
                    UUID.fromString("e1a0000a-0000-0000-0000-000000000000"), "Eternal Heart Flying Speed",
                    AttributeModifier.Operation.MULTIPLY_TOTAL, -1.0),
            new Entry(EternalHeartConfig.ATTACK_KNOCKBACK, () -> Attributes.ATTACK_KNOCKBACK,
                    UUID.fromString("e1a0000b-0000-0000-0000-000000000000"), "Eternal Heart Attack Knockback",
                    AttributeModifier.Operation.ADDITION, 0.0),
            new Entry(EternalHeartConfig.ENTITY_REACH, () -> ForgeMod.ENTITY_REACH.get(),
                    UUID.fromString("e1a0000c-0000-0000-0000-000000000000"), "Eternal Heart Entity Reach",
                    AttributeModifier.Operation.ADDITION, 0.0),
            new Entry(EternalHeartConfig.BLOCK_REACH, () -> ForgeMod.BLOCK_REACH.get(),
                    UUID.fromString("e1a0000d-0000-0000-0000-000000000000"), "Eternal Heart Block Reach",
                    AttributeModifier.Operation.ADDITION, 0.0),
            new Entry(EternalHeartConfig.STEP_HEIGHT, () -> ForgeMod.STEP_HEIGHT_ADDITION.get(),
                    UUID.fromString("e1a0000e-0000-0000-0000-000000000000"), "Eternal Heart Step Height",
                    AttributeModifier.Operation.ADDITION, 0.0),
            new Entry(EternalHeartConfig.NAMETAG_DISTANCE, () -> ForgeMod.NAMETAG_DISTANCE.get(),
                    UUID.fromString("e1a0000f-0000-0000-0000-000000000000"), "Eternal Heart Nametag Distance",
                    AttributeModifier.Operation.ADDITION, 0.0));

    @Override
    public String getName() {
        return "AttributeFeature";
    }

    /** 属性修饰符校验间隔（tick）：对抗其它模组的「属性还原 / 清洗」类机制。 */
    private static final int VERIFY_INTERVAL = 40;

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * 低频校验我们的属性修饰符是否仍然存在。
     *
     * <p>某些 Boss / 机制会把玩家属性「还原到初始值」或直接清空修饰符
     * （典型：亚波伦「末日终结」阶段的属性还原）。发现缺失即整体重建，
     * 保证饰品加成持续生效；未佩戴时不介入。</p>
     */
    @Override
    public void onPlayerTick(Player player, LivingTickEvent event) {
        if (player.level().isClientSide()) return;
        if (!EquipTracker.isEquipped(player)) return;
        if (!Ticker.due(player.getUUID(), player.level().getGameTime(), VERIFY_INTERVAL)) return;
        verify(player);
    }

    /** 任一预期修饰符缺失即整体重建（重建幂等：先移除同 UUID 再添加）。 */
    private static void verify(Player player) {
        for (Entry entry : ENTRIES) {
            AttributeInstance instance = player.getAttribute(entry.target());
            if (instance == null) continue;
            boolean expected = entry.amount() != 0.0
                    || entry.operation() == AttributeModifier.Operation.ADDITION;
            if (expected && instance.getModifier(entry.id()) == null) {
                refresh(player);
                return;
            }
        }
        for (CustomAttributes.Spec spec : customSpecs()) {
            Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(spec.attribute());
            if (attribute == null) continue;
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null && instance.getModifier(CustomAttributes.uuidFor(spec.attribute())) == null) {
                refresh(player);
                return;
            }
        }
    }

    /** 自定义属性修饰符的显示名（调试可读）。 */
    private static final String CUSTOM_LABEL = "Eternal Heart Custom Attribute";

    @Override
    public void addAttributeModifiers(SlotContext slotContext, UUID uuid, ItemStack stack,
                                      Multimap<Attribute, AttributeModifier> map) {
        for (Entry entry : ENTRIES) {
            map.put(entry.target(),
                    new AttributeModifier(entry.id(), entry.label(), entry.amount(), entry.operation()));
        }
        // 自定义属性（可指向其它模组的属性）：只登记实体确实拥有实例的属性，
        // 避免给不支持的实体挂上不存在的属性而引发异常
        var entity = slotContext.entity();
        for (CustomAttributes.Spec spec : customSpecs()) {
            Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(spec.attribute());
            if (attribute == null || entity == null || entity.getAttribute(attribute) == null) continue;
            map.put(attribute, modifier(spec));
        }
    }

    /** 配置热刷新：用固定 UUID 重建全部属性修饰符（无需重新佩戴）。 */
    public static void refresh(Player player) {
        for (Entry entry : ENTRIES) {
            apply(player, entry);
        }
        applyCustom(player);
    }

    /** 刷新所有在线且已装备永恒之心的玩家。 */
    public static void refreshAllOnline() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (EquipTracker.query(player)) {
                refresh(player);
            }
        }
    }

    private static void apply(Player player, Entry entry) {
        AttributeInstance instance = player.getAttribute(entry.target());
        if (instance == null) return;
        instance.removeModifier(entry.id());

        double amount = entry.amount();
        // 零值乘算修饰符没有意义，跳过（加算修饰符保持登记以维持既有行为）
        if (amount != 0.0 || entry.operation() == AttributeModifier.Operation.ADDITION) {
            instance.addTransientModifier(
                    new AttributeModifier(entry.id(), entry.label(), amount, entry.operation()));
        }
    }

    // ============================================================
    //  自定义属性（可指向其它模组注册的属性）
    // ============================================================

    /** 读取并解析自定义属性配置（低频：装备 / 配置保存 / 命令修改时）。 */
    private static List<CustomAttributes.Spec> customSpecs() {
        return CustomAttributes.parse(ConfigValues.get(EternalHeartConfig.EXTRA_ATTRIBUTES));
    }

    private static AttributeModifier modifier(CustomAttributes.Spec spec) {
        return new AttributeModifier(CustomAttributes.uuidFor(spec.attribute()), CUSTOM_LABEL,
                spec.value(), spec.operation());
    }

    /**
     * 应用自定义属性：先按「全注册表 × 确定性 UUID」清除上一次的修饰符
     * （配置里被删掉的旧条目同样能移除），再按当前配置重建。
     *
     * <p>遍历属性注册表只发生在装备 / 配置保存这类低频路径，代价可忽略；
     * 换来的是<b>无状态</b>——不需要记录每个玩家上一次应用过哪些属性。</p>
     */
    private static void applyCustom(Player player) {
        for (var attributeEntry : ForgeRegistries.ATTRIBUTES.getEntries()) {
            ResourceLocation id = attributeEntry.getKey().location();
            AttributeInstance instance = player.getAttribute(attributeEntry.getValue());
            if (instance != null) instance.removeModifier(CustomAttributes.uuidFor(id));
        }
        for (CustomAttributes.Spec spec : customSpecs()) {
            Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(spec.attribute());
            if (attribute == null) continue;
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) continue;
            instance.addTransientModifier(modifier(spec));
        }
    }
}
