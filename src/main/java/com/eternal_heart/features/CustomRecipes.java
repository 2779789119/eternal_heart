package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.EternalHeartMod;
import com.eternal_heart.config.ConfigValues;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义配方 —— 在配置里直接写配方 JSON，注入服务器的 {@link RecipeManager}。
 *
 * <p>为什么用 JSON 而不是自造语法：配方种类极多（有序/无序合成、熔炼、锻造、
 * 以及各模组自定义的 recipe type），直接复用原版解析器 {@link RecipeManager#fromJson}
 * 就能<b>天然支持全部现有配方类型</b>，包括其它模组注册的 type 与序列化器。</p>
 *
 * <h3>注入时机</h3>
 * <ol>
 *   <li>{@link ServerStartedEvent}：服务器启动完成、数据包已加载之后注入一次；</li>
 *   <li>{@link AddReloadListenerEvent}：{@code /reload} 时重新注入（数据包重载会清空
 *       非数据包来源的配方，必须补回）；</li>
 *   <li>命令 {@code /eternalheart recipe reload}：改完配置立即生效，无需重启。</li>
 * </ol>
 *
 * <p>注入后把完整配方表用 {@link ClientboundUpdateRecipesPacket} 广播给在线玩家，
 * 否则配方书 / JEI 之类仍然看不到新增配方。</p>
 */
@Mod.EventBusSubscriber(modid = EternalHeartMod.MODID)
public final class CustomRecipes {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 自定义配方的 ID 命名空间前缀（与数据包配方不会冲突）。 */
    private static final String ID_PREFIX = "eternal_heart_custom_";

    private CustomRecipes() {
    }

    // ============================================================
    //  注入
    // ============================================================

    /** 把配置里的配方追加到服务器的配方表，并同步给在线玩家。返回成功注入的条数。 */
    public static int inject(MinecraftServer server) {
        RecipeManager manager = server.getRecipeManager();
        // 先剔除本模组上次注入的自定义配方：否则「修改 / 删除配置后 reload」时，
        // 旧的自定义配方会残留在配方表里（replaceRecipes 不会清理本次列表之外的同名前缀项）。
        List<Recipe<?>> recipes = new ArrayList<>();
        for (Recipe<?> recipe : manager.getRecipes()) {
            if (!isOwned(recipe)) recipes.add(recipe);
        }
        int before = recipes.size();

        List<? extends String> definitions = ConfigValues.get(EternalHeartConfig.CUSTOM_RECIPES);
        for (int index = 0; index < definitions.size(); index++) {
            Recipe<?> recipe = parse(index, definitions.get(index));
            if (recipe != null) recipes.add(recipe);
        }

        int added = recipes.size() - before;
        manager.replaceRecipes(recipes);
        broadcast(server, recipes);
        LOGGER.info("[永恒之心] 自定义配方注入完成：配置 {} 条，成功 {} 条，配方表共 {} 条",
                definitions.size(), added, recipes.size());
        return added;
    }

    /** 是否为本模组注入的自定义配方（按 ID 前缀识别）。 */
    private static boolean isOwned(Recipe<?> recipe) {
        ResourceLocation id = recipe.getId();
        return id.getNamespace().equals(EternalHeartMod.MODID)
                && id.getPath().startsWith(ID_PREFIX);
    }

    /**
     * 解析单条配置。
     *
     * <p>支持简化语法与原始 JSON 两种写法（见 {@link RecipeText}）——
     * 常用配方一行就能写，复杂配方仍可贴原版 JSON。</p>
     *
     * <p>解析失败只记录日志并跳过 —— 一条写错不应该拖垮整份配置，
     * 更不该让服务器起不来。</p>
     */
    private static Recipe<?> parse(int index, String definition) {
        try {
            JsonObject object = RecipeText.parse(definition);
            if (object == null) {
                LOGGER.warn("[永恒之心] 自定义配方 #{} 无法识别，已忽略（写法见配置注释）：{}", index, definition);
                return null;
            }
            return RecipeManager.fromJson(EternalHeartMod.id(ID_PREFIX + index), object);
        } catch (RuntimeException e) {
            LOGGER.warn("[永恒之心] 自定义配方 #{} 解析失败，已忽略：{}", index, e.toString());
            return null;
        }
    }

    /** 广播完整配方表（配方书 / JEI 依赖该包刷新）。 */
    private static void broadcast(MinecraftServer server, List<Recipe<?>> recipes) {
        ClientboundUpdateRecipesPacket packet = new ClientboundUpdateRecipesPacket(recipes);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }

    // ============================================================
    //  事件：启动与数据包重载
    // ============================================================

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        inject(event.getServer());
    }

    /**
     * {@code /reload}：数据包重载会把配方表整体替换，自定义配方必须重新注入。
     *
     * <p>本监听器由 {@code addListener} 追加在 vanilla 的 {@link RecipeManager} 之后，
     * 但重载本身在异步线程执行，因此注入动作回到服务器主线程再执行，
     * 保证读取到的是本次重载完成的配方表。</p>
     */
    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.execute(() -> {
            // 重载完成后才安排到这里：此刻拿到的是新配方表
            if (ServerLifecycleHooks.getCurrentServer() == server) inject(server);
        });
    }
}
