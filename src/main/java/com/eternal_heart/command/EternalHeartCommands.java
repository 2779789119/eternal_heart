package com.eternal_heart.command;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.EternalHeartMod;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.core.Debuffs;
import com.eternal_heart.core.Numbers;
import com.eternal_heart.features.CurioGuard;
import com.eternal_heart.features.CustomAttributes;
import com.eternal_heart.features.CustomRecipes;
import com.eternal_heart.features.TimeStop;
import com.eternal_heart.integration.RevelationFixCompat;
import com.eternal_heart.network.ConfigNetwork;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 游戏内命令入口 —— 负面效果清除的黑 / 白名单快捷增删查清。
 *
 * <p>相比手改 toml 或打开面板，命令支持 Tab 补全全部已注册效果 ID（含其它模组），
 * 修改后立即保存并同步给在线玩家（与面板保存共用 {@link ConfigNetwork#publish()} 链路）。</p>
 *
 * <pre>
 * /eternalheart debuff list                        查看两个名单
 * /eternalheart debuff blacklist add &lt;effect&gt;      加入黑名单（强制清除）
 * /eternalheart debuff blacklist remove &lt;effect&gt;   移出黑名单
 * /eternalheart debuff blacklist clear             清空黑名单
 * /eternalheart debuff whitelist add|remove|clear  白名单（保护，永不清除）
 *
 * /eternalheart attribute list                     查看自定义属性
 * /eternalheart attribute set &lt;attribute&gt; &lt;value&gt; [operation]   设置（含其它模组属性）
 * /eternalheart attribute remove &lt;attribute&gt;         移除
 * /eternalheart attribute clear                    清空
 * </pre>
 */
@Mod.EventBusSubscriber(modid = EternalHeartMod.MODID)
public final class EternalHeartCommands {

    private static final String COMMON = "commands.eternal_heart.";
    private static final String PREFIX = COMMON + "debuff.";
    private static final String ATTR_PREFIX = COMMON + "attribute.";
    private static final String RECIPE_PREFIX = COMMON + "recipe.";
    private static final String TIMESTOP_PREFIX = COMMON + "timestop.";
    private static final String CURIO_PREFIX = COMMON + "curio.";
    private static final String COMPAT_PREFIX = COMMON + "compat.";
    private static final String BLACKLIST_FIELD = "DEBUFF_BLACKLIST";
    private static final String WHITELIST_FIELD = "DEBUFF_WHITELIST";
    private static final String ATTR_FIELD = "EXTRA_ATTRIBUTES";

    private EternalHeartCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("eternalheart")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("debuff")
                                .then(branch("blacklist", BLACKLIST_FIELD, EternalHeartConfig.DEBUFF_BLACKLIST))
                                .then(branch("whitelist", WHITELIST_FIELD, EternalHeartConfig.DEBUFF_WHITELIST))
                                .then(Commands.literal("check")
                                        .then(Commands.argument("effect", ResourceLocationArgument.id())
                                                .suggests(EternalHeartCommands::suggestEffects)
                                                .executes(ctx -> checkEffect(ctx.getSource(),
                                                        ResourceLocationArgument.getId(ctx, "effect")))))
                                .then(Commands.literal("list").executes(ctx -> showAll(ctx.getSource()))))
                        .then(attributeNode())
                        .then(curioNode())
                        .then(compatNode())
                        .then(Commands.literal("recipe")
                                .then(Commands.literal("reload")
                                        .executes(ctx -> reloadRecipes(ctx.getSource()))))
                        .then(Commands.literal("timestop")
                                .executes(ctx -> timeStop(ctx.getSource()))));
    }

    /**
     * 外部兼容子命令：维护其它模组公开配置里的兼容条目（当前：RevelationFix 的「天启」豁免名单）。
     *
     * <p>只读写对方公开的配置文件，不触碰其模组本体（ARR 合规，详见 {@link RevelationFixCompat}）。</p>
     */
    private static LiteralArgumentBuilder<CommandSourceStack> compatNode() {
        return Commands.literal("compat")
                .then(Commands.literal("revelationfix")
                        .executes(ctx -> compatRevelation(ctx.getSource(), null))
                        .then(Commands.literal("on").executes(ctx -> compatRevelation(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> compatRevelation(ctx.getSource(), false)))
                        .then(Commands.literal("auto")
                                .executes(ctx -> compatAutoDetect(ctx.getSource(), null))
                                .then(Commands.literal("on").executes(ctx -> compatAutoDetect(ctx.getSource(), true)))
                                .then(Commands.literal("off").executes(ctx -> compatAutoDetect(ctx.getSource(), false)))));
    }

    /** 同步 RevelationFix 豁免名单；带 on/off 参数时同时改写并保存本模组的开关。 */
    private static int compatRevelation(CommandSourceStack source, Boolean wanted) {
        boolean target = wanted != null ? wanted : EternalHeartConfig.REVELATIONFIX_WHITELIST.get();
        if (wanted != null) {
            try {
                ConfigValues.apply(Map.of("REVELATIONFIX_WHITELIST", wanted));
                if (EternalHeartConfig.CONFIG != null) EternalHeartConfig.CONFIG.save();
            } catch (RuntimeException e) {
                source.sendFailure(Component.translatable(COMMON + "failed", String.valueOf(e.getMessage())));
                return 0;
            }
            ConfigNetwork.publish();
        }
        // 自动检测条目本次不动（null），由扫描器接管
        RevelationFixCompat.Result result = RevelationFixCompat.sync(target,
                ConfigValues.get(EternalHeartConfig.REVELATIONFIX_EXTRA_ITEMS), null);
        Component message = Component.translatable(COMPAT_PREFIX + "revelationfix."
                + result.name().toLowerCase(Locale.ROOT));
        boolean ok = switch (result) {
            case ADDED, REMOVED, ALREADY -> true;
            default -> false;
        };
        if (ok) {
            // 总开关切换后，尽快让自动检测条目（若开启）跟随当前佩戴情况
            RevelationFixCompat.scheduleScan();
            source.sendSuccess(() -> message, true);
            return 1;
        }
        source.sendFailure(message);
        return 0;
    }

    /** 切换/查询「自动检测」开关；带 on/off 参数时立即改写、保存并强制重扫一次。 */
    private static int compatAutoDetect(CommandSourceStack source, Boolean wanted) {
        boolean target = wanted != null
                ? wanted
                : ConfigValues.get(EternalHeartConfig.REVELATIONFIX_AUTO_DETECT);
        if (wanted != null) {
            try {
                ConfigValues.apply(Map.of("REVELATIONFIX_AUTO_DETECT", wanted));
                if (EternalHeartConfig.CONFIG != null) EternalHeartConfig.CONFIG.save();
            } catch (RuntimeException e) {
                source.sendFailure(Component.translatable(COMMON + "failed", String.valueOf(e.getMessage())));
                return 0;
            }
            ConfigNetwork.publish();
        }
        // 强制重扫（跳过节流），让豁免名单立即与开关状态、佩戴情况对齐
        RevelationFixCompat.scanAndSync(source.getServer(), true);
        Component message = wanted != null
                ? Component.translatable(COMPAT_PREFIX + "revelationfix.auto_" + (target ? "enabled" : "disabled"))
                : Component.translatable(COMPAT_PREFIX + "revelationfix.auto_status",
                        Component.translatable(COMPAT_PREFIX + "revelationfix." + (target ? "on" : "off")));
        source.sendSuccess(() -> message, true);
        return 1;
    }

    /** 饰品强制取下子命令（绕过一切绑定 / 锁定，直接写 Curios 槽位）。 */
    private static LiteralArgumentBuilder<CommandSourceStack> curioNode() {
        return Commands.literal("curio")
                .then(Commands.literal("unequip")
                        .executes(ctx -> forceUnequip(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> forceUnequip(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))));
    }

    private static int forceUnequip(CommandSourceStack source, ServerPlayer player) {
        if (!CurioGuard.forceUnequip(player)) {
            source.sendFailure(Component.translatable(CURIO_PREFIX + "absent"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(CURIO_PREFIX + "unequipped", player.getName()), true);
        return 1;
    }

    /** 自定义属性子命令（支持任意已注册属性，含其它模组）。 */
    private static LiteralArgumentBuilder<CommandSourceStack> attributeNode() {
        return Commands.literal("attribute")
                .then(Commands.literal("list").executes(ctx -> showAttributes(ctx.getSource())))
                .then(Commands.literal("clear").executes(ctx -> apply(ctx.getSource(), ATTR_FIELD,
                        List.of(), Component.translatable(ATTR_PREFIX + "cleared"))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("attribute", ResourceLocationArgument.id())
                                .suggests(EternalHeartCommands::suggestAttributes)
                                .executes(ctx -> removeAttribute(ctx.getSource(),
                                        ResourceLocationArgument.getId(ctx, "attribute")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("attribute", ResourceLocationArgument.id())
                                .suggests(EternalHeartCommands::suggestAttributes)
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                        .executes(ctx -> setAttribute(ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "attribute"),
                                                DoubleArgumentType.getDouble(ctx, "value"), null))
                                        .then(Commands.argument("operation", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                        List.of("addition", "multiply_base", "multiply_total"), builder))
                                                .executes(ctx -> setAttribute(ctx.getSource(),
                                                        ResourceLocationArgument.getId(ctx, "attribute"),
                                                        DoubleArgumentType.getDouble(ctx, "value"),
                                                        StringArgumentType.getString(ctx, "operation")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> branch(
            String name, String field, ForgeConfigSpec.ConfigValue<List<? extends String>> value) {
        return Commands.literal(name)
                .then(Commands.literal("list").executes(ctx -> show(ctx.getSource(), name, value)))
                .then(Commands.literal("clear").executes(ctx -> apply(ctx.getSource(), field, List.of(),
                        Component.translatable(PREFIX + "cleared", Component.translatable(PREFIX + "name." + name)))))
                .then(Commands.literal("add")
                        .then(Commands.argument("effect", ResourceLocationArgument.id())
                                .suggests(EternalHeartCommands::suggestEffects)
                                .executes(ctx -> mutate(ctx.getSource(), name, field, value,
                                        ResourceLocationArgument.getId(ctx, "effect"), true))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("effect", ResourceLocationArgument.id())
                                .suggests(EternalHeartCommands::suggestEffects)
                                .executes(ctx -> mutate(ctx.getSource(), name, field, value,
                                        ResourceLocationArgument.getId(ctx, "effect"), false))));
    }

    /** Tab 补全：全部已注册效果（含其它模组）。 */
    private static CompletableFuture<Suggestions> suggestEffects(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(BuiltInRegistries.MOB_EFFECT.keySet(), builder);
    }

    /** 展示某个效果的拦截判定与来源（排查「为什么被 / 没被拦」）。 */
    private static int checkEffect(CommandSourceStack source, ResourceLocation id) {
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);
        if (effect == null) {
            source.sendFailure(Component.translatable(PREFIX + "unknown_effect", id.toString()));
            return 0;
        }
        boolean blocked = Debuffs.blocked(effect);
        Debuffs.Source from = Debuffs.source(effect);
        source.sendSuccess(() -> Component.translatable(COMMON + "debuff.check",
                id.toString(),
                Component.translatable(COMMON + "debuff.blocked." + (blocked ? "yes" : "no")),
                Component.translatable(COMMON + "debuff.source." + from.name().toLowerCase(Locale.ROOT))), false);
        return blocked ? 1 : 0;
    }

    private static int showAll(CommandSourceStack source) {
        show(source, "blacklist", EternalHeartConfig.DEBUFF_BLACKLIST);
        show(source, "whitelist", EternalHeartConfig.DEBUFF_WHITELIST);
        return 1;
    }

    private static int show(CommandSourceStack source, String name,
                            ForgeConfigSpec.ConfigValue<List<? extends String>> value) {
        List<? extends String> list = ConfigValues.get(value);
        Component display = Component.translatable(PREFIX + "name." + name);
        source.sendSuccess(() -> Component.translatable(
                PREFIX + (list.isEmpty() ? "empty" : "header"), display, list.size()), false);
        for (String id : list) {
            source.sendSuccess(() -> Component.literal(" - " + id), false);
        }
        return list.size();
    }

    /** 名单增删：加入 / 移出一个效果 ID（含存在性检查与反馈）。 */
    private static int mutate(CommandSourceStack source, String name, String field,
                              ForgeConfigSpec.ConfigValue<List<? extends String>> value,
                              ResourceLocation id, boolean adding) {
        List<String> list = new ArrayList<>(ConfigValues.get(value));
        String key = id.toString();
        Component display = Component.translatable(PREFIX + "name." + name);
        boolean present = list.contains(key);
        if (adding && present) {
            source.sendFailure(Component.translatable(PREFIX + "present", display, key));
            return 0;
        }
        if (!adding && !present) {
            source.sendFailure(Component.translatable(PREFIX + "absent", display, key));
            return 0;
        }
        if (adding) {
            list.add(key);
            list.sort(Comparator.naturalOrder());
        } else {
            list.remove(key);
        }
        return apply(source, field, list,
                Component.translatable(PREFIX + (adding ? "added" : "removed"), display, key));
    }

    // ============================================================
    //  自定义属性（可指向其它模组注册的属性）
    // ============================================================

    /** Tab 补全：全部已注册属性（含其它模组）。 */
    private static CompletableFuture<Suggestions> suggestAttributes(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(ForgeRegistries.ATTRIBUTES.getKeys(), builder);
    }

    private static int showAttributes(CommandSourceStack source) {
        List<? extends String> list = ConfigValues.get(EternalHeartConfig.EXTRA_ATTRIBUTES);
        source.sendSuccess(() -> Component.translatable(
                ATTR_PREFIX + (list.isEmpty() ? "empty" : "header"), list.size()), false);
        for (String line : list) {
            source.sendSuccess(() -> Component.literal(" - " + line), false);
        }
        return list.size();
    }

    private static int setAttribute(CommandSourceStack source, ResourceLocation id,
                                    double value, String operationName) {
        if (!ForgeRegistries.ATTRIBUTES.containsKey(id)) {
            source.sendFailure(Component.translatable(ATTR_PREFIX + "unknown", id.toString()));
            return 0;
        }
        if (!Double.isFinite(value)) {
            source.sendFailure(Component.translatable(COMMON + "failed", "non-finite value"));
            return 0;
        }
        AttributeModifier.Operation operation = operationName == null
                ? AttributeModifier.Operation.ADDITION : CustomAttributes.operation(operationName);
        if (operation == null) {
            source.sendFailure(Component.translatable(ATTR_PREFIX + "bad_operation", operationName));
            return 0;
        }

        List<String> list = new ArrayList<>(ConfigValues.get(EternalHeartConfig.EXTRA_ATTRIBUTES));
        list.removeIf(line -> line.startsWith(id + " "));
        list.add(CustomAttributes.format(id, value, operation));
        list.sort(Comparator.naturalOrder());
        return apply(source, ATTR_FIELD, list, Component.translatable(ATTR_PREFIX + "set",
                id.toString(), Numbers.format(value), CustomAttributes.operationName(operation)));
    }

    private static int removeAttribute(CommandSourceStack source, ResourceLocation id) {
        List<String> list = new ArrayList<>(ConfigValues.get(EternalHeartConfig.EXTRA_ATTRIBUTES));
        if (!list.removeIf(line -> line.startsWith(id + " "))) {
            source.sendFailure(Component.translatable(ATTR_PREFIX + "absent", id.toString()));
            return 0;
        }
        return apply(source, ATTR_FIELD, list,
                Component.translatable(ATTR_PREFIX + "removed", id.toString()));
    }

    // ============================================================
    //  通用写入（保存 + 同步 + 反馈）
    // ============================================================

    private static int apply(CommandSourceStack source, String field, List<String> list, Component success) {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put(field, List.copyOf(list));
        try {
            ConfigValues.apply(patch);
            if (EternalHeartConfig.CONFIG != null) EternalHeartConfig.CONFIG.save();
        } catch (RuntimeException e) {
            source.sendFailure(Component.translatable(COMMON + "failed", String.valueOf(e.getMessage())));
            return 0;
        }
        ConfigNetwork.publish();
        source.sendSuccess(() -> success, true);
        return 1;
    }

    // ============================================================
    //  自定义配方 / 时停
    // ============================================================

    /** 重新注入自定义配方：改完配置无需重启（数据包重载后同样会走这条链路）。 */
    private static int reloadRecipes(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        int added;
        try {
            added = CustomRecipes.inject(server);
        } catch (RuntimeException e) {
            source.sendFailure(Component.translatable(COMMON + "failed", String.valueOf(e.getMessage())));
            return 0;
        }
        int total = added;
        source.sendSuccess(() -> Component.translatable(RECIPE_PREFIX + "reloaded", total), true);
        return added;
    }

    /** 手动发动时停：与快捷键走完全相同的校验（开关 / 冷却 / 佩戴）。 */
    private static int timeStop(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.translatable(TIMESTOP_PREFIX + "player_only"));
            return 0;
        }
        Component failure = TimeStop.start(player);
        if (failure != null) {
            source.sendFailure(failure);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(TIMESTOP_PREFIX + "started"), true);
        return 1;
    }
}
