package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.EternalHeartMod;
import com.eternal_heart.config.ConfigValues;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraftforge.common.MinecraftForge;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.event.CurioAttributeModifierEvent;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * 饰品融合 —— 让永恒之心继承其它饰品的能力。
 *
 * <h3>存什么</h3>
 * <p>融合时把原饰品的<b>完整 ItemStack NBT</b> 存进永恒之心的
 * {@code eternal_heart_fused} 列表（每项还记录它可用的 Curios 槽位类型）。
 * 原饰品被合成消耗，能力由永恒之心在装备期间代为调用。</p>
 *
 * <h3>继承什么</h3>
 * <ul>
 *   <li>属性修饰符（攻击 / 生命 / 护甲 / 移速……）—— 重新分配 UUID，避免与
 *       原饰品同时佩戴时冲突；</li>
 *   <li>{@code curioTick} / {@code onEquip} / {@code onUnequip} —— 每 tick 效果、
 *       装备时上 buff、卸下时清理；</li>
 *   <li>时运 / 抢夺 / 猪灵中立 / 细雪行走 / 末影人面具等返回值型能力。</li>
 * </ul>
 *
 * <p><b>继承不了</b>：能力写在 Forge 事件里、靠 {@code CuriosApi.findFirstCurio(自己的物品)}
 * 判断是否佩戴的模组。那类实现必须让原物品真的在槽位里才生效，本方案不覆盖。</p>
 *
 * <p><b>逐个开关</b>：每条融合记录带一个 {@code enabled} 标记（缺省 = 生效），
 * 玩家可以在「融合饰品开关」界面里逐条关闭。关闭的条目仍留在 NBT 里、提示框照常列出，
 * 只是不再转发任何能力；所有转发入口统一读 {@link #activeFused}，因此判定是单点的。</p>
 *
 * <p>所有对第三方饰品的调用都包了异常保护：某个饰品在 tick 里抛错不应该拖垮整个玩家 tick。</p>
 */
public final class HeartFusion {

    private static final String KEY = "eternal_heart_fused";
    private static final String STACK = "stack";
    private static final String SLOT = "slot";
    /** 单条融合记录是否生效（缺省 true，只有玩家手动关闭时才写入 false）。 */
    private static final String ENABLED = "enabled";

    /** 提示框最多列出多少个已融合饰品（其余折叠）。 */
    private static final int MAX_TOOLTIP_LINES = 8;

    private HeartFusion() {
    }

    /**
     * 一条融合记录。
     *
     * @param index   它在 NBT 列表里的原始下标（用于生成稳定的属性 UUID / 界面回写）
     * @param stack   原饰品（完整 NBT）
     * @param slot    原饰品可用的 Curios 槽位类型
     * @param enabled 玩家是否开启这条能力
     */
    public record Fused(int index, ItemStack stack, String slot, boolean enabled) {
    }

    // ============================================================
    //  读写 / 合成
    // ============================================================

    /** 该物品是否可以融合（带 Curios 能力）。 */
    public static boolean isCurio(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof ICurioItem) return true;
        return CuriosApi.getCurio(stack).isPresent();
    }

    /** 融合列表（返回的是 NBT 里的原始 ListTag，可直接修改后写回）。 */
    public static ListTag entries(ItemStack heart) {
        CompoundTag tag = heart.getTag();
        return tag == null ? new ListTag() : tag.getList(KEY, Tag.TAG_COMPOUND);
    }

    public static int count(ItemStack heart) {
        return entries(heart).size();
    }

    /** 当前是否还能继续融合（功能开启且未达上限）。 */
    public static boolean canFuse(ItemStack heart) {
        return ConfigValues.get(EternalHeartConfig.FUSION_ENABLED)
                && count(heart) < ConfigValues.get(EternalHeartConfig.FUSION_LIMIT);
    }

    /**
     * 把 {@code curio} 融合进 {@code heart}（直接修改 heart 的 NBT）。
     *
     * @return 是否成功（已满 / 功能关闭 / 非法物品时 false）
     */
    public static boolean fuse(ItemStack heart, ItemStack curio) {
        if (heart.isEmpty() || curio.isEmpty()) return false;
        if (!heart.is(EternalHeartMod.ETERNAL_HEART.get())) return false;
        if (curio.is(EternalHeartMod.ETERNAL_HEART.get())) return false; // 禁止自我嵌套
        if (!isCurio(curio)) return false;
        if (!canFuse(heart)) return false;

        ItemStack stored = curio.copy();
        stored.setCount(1);

        CompoundTag entry = new CompoundTag();
        entry.put(STACK, stored.save(new CompoundTag()));
        String slot = preferredSlot(stored);
        if (!slot.isEmpty()) entry.putString(SLOT, slot);

        ListTag list = entries(heart);
        list.add(entry);
        heart.getOrCreateTag().put(KEY, list);
        return true;
    }

    /** 解析融合列表（跳过损坏条目；原始下标保留在 {@link Fused#index()} 里）。 */
    public static List<Fused> fused(ItemStack heart) {
        ListTag list = entries(heart);
        if (list.isEmpty()) return List.of();

        List<Fused> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.contains(STACK, Tag.TAG_COMPOUND)) continue;
            ItemStack stack = ItemStack.of(entry.getCompound(STACK));
            if (stack.isEmpty() || stack.is(EternalHeartMod.ETERNAL_HEART.get())) continue;
            String slot = entry.contains(SLOT, Tag.TAG_STRING) ? entry.getString(SLOT) : "";
            result.add(new Fused(i, stack, slot, isEnabled(entry)));
        }
        return result;
    }

    /** 单条记录是否生效：只有显式写入 {@code enabled=false} 时才算关闭（旧存档没有该字段 → 生效）。 */
    private static boolean isEnabled(CompoundTag entry) {
        return !entry.contains(ENABLED, Tag.TAG_BYTE) || entry.getBoolean(ENABLED);
    }

    /**
     * 参与能力转发的融合列表：剔除被玩家关掉的条目。
     *
     * <p>所有转发入口都必须走这里（而不是 {@link #fused}），逐个开关才是单点生效的；
     * {@link #fused} 保留给提示框 / 开关界面 / 诊断命令等「只看不生效」的地方。</p>
     */
    public static List<Fused> activeFused(ItemStack heart) {
        List<Fused> all = fused(heart);
        if (all.isEmpty()) return all;
        List<Fused> active = new ArrayList<>(all.size());
        for (Fused fused : all) {
            if (fused.enabled()) active.add(fused);
        }
        return active;
    }

    /** 按 NBT 下标取一条融合记录（找不到返回 null）。 */
    public static Fused entry(ItemStack heart, int index) {
        for (Fused fused : fused(heart)) {
            if (fused.index() == index) return fused;
        }
        return null;
    }

    /**
     * 写入单条记录的开关状态（只改 NBT，不做能力同步）。
     *
     * <p>客户端也调用它：服务端改完后由回执包让客户端把自己的那份 NBT 对齐，
     * 提示框 / 客户端查询才不会停留在旧状态。</p>
     *
     * @return 下标无效 / 条目损坏时 false
     */
    public static boolean setEnabled(ItemStack heart, int index, boolean enabled) {
        if (heart.isEmpty()) return false;
        ListTag list = entries(heart);
        if (index < 0 || index >= list.size()) return false;
        CompoundTag entry = list.getCompound(index);
        if (!entry.contains(STACK, Tag.TAG_COMPOUND)) return false;
        entry.putBoolean(ENABLED, enabled);
        heart.getOrCreateTag().put(KEY, list);
        return true;
    }

    /** 原饰品可用的第一个 Curios 槽位类型（排序后取第一个，保证两端一致）。 */
    private static String preferredSlot(ItemStack stack) {
        Set<String> tags = CuriosApi.getCuriosHelper().getCurioTags(stack.getItem());
        if (tags.isEmpty()) return "";
        return new TreeSet<>(tags).iterator().next();
    }

    // ============================================================
    //  能力转发
    // ============================================================

    /** 把融合饰品的属性修饰符合并进目标的 Multimap（UUID 全部重新分配）。 */
    public static void mergeAttributeModifiers(Multimap<Attribute, AttributeModifier> target,
                                               SlotContext context, ItemStack heart) {
        for (Fused fused : activeFused(heart)) {
            SlotContext ctx = contextFor(context, fused);
            Multimap<Attribute, AttributeModifier> modifiers;
            try {
                modifiers = collect(fused, ctx);
            } catch (Exception | LinkageError ignored) {
                continue; // 第三方饰品的属性实现出错：跳过这一条，不影响永恒之心自身
            }

            int sub = 0;
            for (var entry : modifiers.entries()) {
                AttributeModifier modifier = entry.getValue();
                if (modifier == null || !Double.isFinite(modifier.getAmount())) continue;
                target.put(entry.getKey(), new AttributeModifier(
                        entryUuid(fused.index(), ++sub),
                        modifier.getName(), modifier.getAmount(), modifier.getOperation()));
            }
        }
    }

    /**
     * 解析一件融合饰品的属性修饰符。
     *
     * <p>两条来源都取：</p>
     * <ol>
     *   <li>物品自己的 {@code ICurioItem#getAttributeModifiers}（或 Curios 能力包装器）；</li>
     *   <li>{@link CurioAttributeModifierEvent} —— 不少模组不重写方法，而是在这个事件里
     *       判断 {@code event.getItemStack()} 再追加属性；必须为融合饰品补发一次，
     *       否则这类饰品的属性永远继承不过来。</li>
     * </ol>
     */
    private static Multimap<Attribute, AttributeModifier> collect(Fused fused, SlotContext ctx) {
        UUID baseUuid = entryUuid(fused.index(), 0);
        Multimap<Attribute, AttributeModifier> base;
        Item item = fused.stack().getItem();
        if (item instanceof ICurioItem curioItem) {
            base = curioItem.getAttributeModifiers(ctx, baseUuid, fused.stack());
        } else {
            base = curioOf(fused.stack())
                    .map(curio -> curio.getAttributeModifiers(ctx, baseUuid))
                    .orElseGet(ImmutableMultimap::of);
        }
        if (base == null) base = ImmutableMultimap.of();

        CurioAttributeModifierEvent event =
                new CurioAttributeModifierEvent(fused.stack(), ctx, baseUuid, base);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getModifiers();
    }

    public static void tick(SlotContext context, ItemStack heart) {
        for (Fused fused : activeFused(heart)) {
            SlotContext ctx = contextFor(context, fused);
            try {
                ItemStack stack = fused.stack();
                if (stack.getItem() instanceof ICurioItem item) item.curioTick(ctx, stack);
                else curioOf(stack).ifPresent(curio -> curio.curioTick(ctx));
            } catch (Exception | LinkageError ignored) {
                // 第三方饰品的 tick 抛错不能拖垮玩家 tick
            }
        }
    }

    public static void onEquip(SlotContext context, ItemStack heart) {
        dispatchEquip(context, heart, true, activeFused(heart));
    }

    public static void onUnequip(SlotContext context, ItemStack heart) {
        dispatchEquip(context, heart, false, activeFused(heart));
    }

    /**
     * 逐个转发装备 / 卸下回调。
     *
     * <p>显式传 {@code entries} 而不是在内部读开关：关闭某条能力时需要<b>越过开关</b>
     * 给它补发一次 {@code onUnequip}（见 {@link #setEntryEnabled}），
     * 否则融合饰品在 {@code onEquip} 里挂上的 buff 会一直留着。</p>
     */
    private static void dispatchEquip(SlotContext context, ItemStack heart, boolean equip, List<Fused> entries) {
        for (Fused fused : entries) {
            SlotContext ctx = contextFor(context, fused);
            try {
                ItemStack stack = fused.stack();
                if (stack.getItem() instanceof ICurioItem item) {
                    if (equip) item.onEquip(ctx, ItemStack.EMPTY, stack);
                    else item.onUnequip(ctx, ItemStack.EMPTY, stack);
                } else {
                    curioOf(stack).ifPresent(curio -> {
                        if (equip) curio.onEquip(ctx, ItemStack.EMPTY);
                        else curio.onUnequip(ctx, ItemStack.EMPTY);
                    });
                }
            } catch (Exception | LinkageError ignored) {
                // 忽略单个饰品
            }
        }
    }

    // ============================================================
    //  逐个开关（服务端）
    // ============================================================

    /**
     * 切换某条融合记录是否生效（服务端权威入口）。
     *
     * <p>为什么不能只改 NBT：<b>属性修饰符是 Curios 在装备瞬间收集的</b>，之后不会因为
     * NBT 变化重算 —— 关掉条目必须按固定的融合 UUID 把它从玩家属性上摘掉、打开时再补回。
     * tick / 返回值型能力本来就每 tick 读 {@link #activeFused}，天然即时生效；
     * 装备 / 卸下回调是边沿事件，所以切换时补发一次 {@code onUnequip} / {@code onEquip}，
     * 避免「关掉后 buff 残留」「打开后 buff 不再挂上」。</p>
     *
     * @param index   NBT 列表里的原始下标
     * @param enabled 目标状态
     * @return 是否应用成功（找不到永恒之心 / 下标无效时 false）
     */
    public static boolean setEntryEnabled(ServerPlayer player, int index, boolean enabled) {
        SlotResult found = CuriosApi.getCuriosHelper()
                .findFirstCurio(player, EternalHeartMod.ETERNAL_HEART.get()).orElse(null);
        boolean equipped = found != null;
        ItemStack heart;
        SlotContext base;
        if (equipped) {
            heart = found.stack();
            base = found.slotContext();
        } else if (player.getMainHandItem().is(EternalHeartMod.ETERNAL_HEART.get())) {
            // 主手也允许：刚合成完就能调，不必先戴上
            heart = player.getMainHandItem();
            base = new SlotContext("eternal_heart", player, 0, false, true);
        } else {
            return false;
        }

        if (!setEnabled(heart, index, enabled)) return false;
        // 拿在手上时融合能力本来就不生效（tick / 属性都只在佩戴时转发），
        // 这里只改记录；否则会给「只是拿在手里的心」凭空上属性 / buff
        if (!equipped) return true;

        Fused fused = entry(heart, index);
        if (fused == null) return false;

        syncEntryAttributes(player, base, fused, fused.enabled());
        dispatchEquip(base, heart, fused.enabled(), List.of(fused));
        return true;
    }

    /**
     * 把单条融合饰品的属性修饰符对齐到玩家身上（幂等）。
     *
     * <p>UUID 与 {@link #mergeAttributeModifiers} 生成的完全一致，因此这里补回 / 移除的
     * 就是 Curios 已经挂上的那批；重复调用不会叠加。移除只按 UUID 精确匹配，
     * 不会误伤玩家自己戴着的同名饰品。</p>
     */
    private static void syncEntryAttributes(Player player, SlotContext base, Fused fused, boolean active) {
        SlotContext ctx = contextFor(base, fused);
        try {
            int sub = 0;
            for (var entry : collect(fused, ctx).entries()) {
                AttributeModifier modifier = entry.getValue();
                if (modifier == null || !Double.isFinite(modifier.getAmount())) continue;
                AttributeInstance instance = player.getAttribute(entry.getKey());
                UUID id = entryUuid(fused.index(), ++sub);
                if (instance == null) continue;
                if (active) {
                    if (instance.getModifier(id) == null) {
                        instance.addTransientModifier(new AttributeModifier(id,
                                modifier.getName(), modifier.getAmount(), modifier.getOperation()));
                    }
                } else {
                    instance.removeModifier(id);
                }
            }
        } catch (Exception | LinkageError ignored) {
            // 第三方饰品的属性实现出错：这条能力没能切干净，但玩家 tick 不受影响
        }
    }

    // ---- 返回值型能力 -------------------------------------------------

    public static int fortune(SlotContext context, LootContext loot, ItemStack heart) {
        int total = 0;
        for (Fused fused : activeFused(heart)) {
            SlotContext ctx = contextFor(context, fused);
            try {
                ItemStack stack = fused.stack();
                if (stack.getItem() instanceof ICurioItem item) {
                    total += item.getFortuneLevel(ctx, loot, stack);
                } else {
                    total += curioOf(stack).map(curio -> curio.getFortuneLevel(ctx, loot)).orElse(0);
                }
            } catch (Exception | LinkageError ignored) {
                // 忽略单个饰品
            }
        }
        return total;
    }

    public static int looting(SlotContext context, DamageSource source, LivingEntity target,
                              int baseLooting, ItemStack heart) {
        int total = 0;
        for (Fused fused : activeFused(heart)) {
            SlotContext ctx = contextFor(context, fused);
            try {
                ItemStack stack = fused.stack();
                if (stack.getItem() instanceof ICurioItem item) {
                    total += item.getLootingLevel(ctx, source, target, baseLooting, stack);
                } else {
                    total += curioOf(stack)
                            .map(curio -> curio.getLootingLevel(ctx, source, target, baseLooting))
                            .orElse(0);
                }
            } catch (Exception | LinkageError ignored) {
                // 忽略单个饰品
            }
        }
        return total;
    }

    public static boolean makesPiglinsNeutral(SlotContext context, ItemStack heart) {
        for (Fused fused : activeFused(heart)) {
            SlotContext ctx = contextFor(context, fused);
            try {
                ItemStack stack = fused.stack();
                boolean value = stack.getItem() instanceof ICurioItem item
                        ? item.makesPiglinsNeutral(ctx, stack)
                        : curioOf(stack).map(curio -> curio.makesPiglinsNeutral(ctx)).orElse(false);
                if (value) return true;
            } catch (Exception | LinkageError ignored) {
                // 忽略单个饰品
            }
        }
        return false;
    }

    public static boolean canWalkOnPowderedSnow(SlotContext context, ItemStack heart) {
        for (Fused fused : activeFused(heart)) {
            SlotContext ctx = contextFor(context, fused);
            try {
                ItemStack stack = fused.stack();
                boolean value = stack.getItem() instanceof ICurioItem item
                        ? item.canWalkOnPowderedSnow(ctx, stack)
                        : curioOf(stack).map(curio -> curio.canWalkOnPowderedSnow(ctx)).orElse(false);
                if (value) return true;
            } catch (Exception | LinkageError ignored) {
                // 忽略单个饰品
            }
        }
        return false;
    }

    public static boolean isEnderMask(SlotContext context, EnderMan enderMan, ItemStack heart) {
        for (Fused fused : activeFused(heart)) {
            SlotContext ctx = contextFor(context, fused);
            try {
                ItemStack stack = fused.stack();
                boolean value = stack.getItem() instanceof ICurioItem item
                        ? item.isEnderMask(ctx, enderMan, stack)
                        : curioOf(stack).map(curio -> curio.isEnderMask(ctx, enderMan)).orElse(false);
                if (value) return true;
            } catch (Exception | LinkageError ignored) {
                // 忽略单个饰品
            }
        }
        return false;
    }

    // ============================================================
    //  诊断（/eternalheart fusion）
    // ============================================================

    /** 这件融合饰品是否解析出 Curios 能力。 */
    public static boolean debugCapability(Fused fused) {
        try {
            return curioOf(fused.stack()).isPresent();
        } catch (Exception | LinkageError ignored) {
            return false;
        }
    }

    /** 解析出的属性修饰符条数（-1 = 解析异常）。 */
    public static int debugModifierCount(Fused fused, SlotContext base) {
        try {
            int count = 0;
            for (var entry : collect(fused, contextFor(base, fused)).entries()) {
                AttributeModifier modifier = entry.getValue();
                if (modifier != null && Double.isFinite(modifier.getAmount())) count++;
            }
            return count;
        } catch (Exception | LinkageError ignored) {
            return -1;
        }
    }

    /** 这些属性修饰符里有多少条已经真正挂在玩家属性上。 */
    public static int debugAppliedCount(Fused fused, SlotContext base, Player player) {
        try {
            int applied = 0;
            int sub = 0;
            for (var entry : collect(fused, contextFor(base, fused)).entries()) {
                AttributeModifier modifier = entry.getValue();
                if (modifier == null || !Double.isFinite(modifier.getAmount())) continue;
                AttributeInstance instance = player.getAttribute(entry.getKey());
                if (instance != null && instance.getModifier(entryUuid(fused.index(), ++sub)) != null) applied++;
            }
            return applied;
        } catch (Exception | LinkageError ignored) {
            return -1;
        }
    }

    // ============================================================
    //  提示框
    // ============================================================

    /** 融合相关的提示行（未融合且功能开启时给一条合成提示）。 */
    public static List<Component> tooltip(ItemStack heart) {
        List<Fused> fused = fused(heart);
        List<Component> lines = new ArrayList<>();

        if (fused.isEmpty()) {
            if (ConfigValues.get(EternalHeartConfig.FUSION_ENABLED)) {
                lines.add(Component.translatable("tooltip.eternal_heart.fusion_hint")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            return lines;
        }

        lines.add(Component.translatable("tooltip.eternal_heart.fused_header", fused.size())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        int shown = Math.min(fused.size(), MAX_TOOLTIP_LINES);
        for (int i = 0; i < shown; i++) {
            Fused entry = fused.get(i);
            MutableComponent line = Component.literal(" · ").append(entry.stack().getHoverName());
            if (entry.enabled()) {
                lines.add(line.withStyle(ChatFormatting.GRAY));
            } else {
                // 关掉的条目仍然列出（玩家自己关的，需要随时看得见），只是标灰 + 后缀
                lines.add(line.copy().append(Component.translatable("tooltip.eternal_heart.fused_entry_off")
                        .withStyle(ChatFormatting.DARK_RED)).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        if (fused.size() > shown) {
            lines.add(Component.translatable("tooltip.eternal_heart.fused_more", fused.size() - shown)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    // ============================================================
    //  内部工具
    // ============================================================

    private static SlotContext contextFor(SlotContext base, Fused fused) {
        String identifier = fused.slot().isEmpty() ? base.identifier() : fused.slot();
        return new SlotContext(identifier, base.entity(), 0, false, true);
    }

    /**
     * 虚拟「已装备」上下文：融合饰品并没有真的占用槽位，但查询方需要一个
     * 看起来合法的 {@link SlotContext}（槽位类型 + 佩戴者）。
     */
    public static SlotContext virtualContext(LivingEntity wearer, Fused fused) {
        String identifier = fused.slot().isEmpty() ? "eternal_heart" : fused.slot();
        return new SlotContext(identifier, wearer, 0, false, true);
    }

    /**
     * 属性 UUID：由「融合条目在 NBT 里的下标 + 该条目内第几个修饰符」确定。
     *
     * <p>不能用随机 UUID：合成配方会在客户端与服务端各算一次产物，随机值会让两边 NBT 不一致；
     * 也不能沿用原饰品的 UUID：与原饰品同时佩戴时同一个 UUID 会互相覆盖。</p>
     */
    private static UUID entryUuid(int index, int sub) {
        return UUID.nameUUIDFromBytes(
                ("eternal_heart:fused:" + index + ":" + sub).getBytes(StandardCharsets.UTF_8));
    }

    private static java.util.Optional<ICurio> curioOf(ItemStack stack) {
        return CuriosApi.getCurio(stack).resolve();
    }
}
