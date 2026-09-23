package com.eternal_heart.integration;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * RevelationFix（诡厄巫法：启示录内嵌的兼容层）「天启」豁免名单接入。
 *
 * <p>下界亚波伦「末日终结」阶段会夺取非豁免玩家的饰品；RevelationFix 为此提供了
 * <b>公开配置项</b>：{@code config/revelationfix/revelationfix-common.toml} →
 * {@code ["The Apocalypse"].whitelistItems}（注释原文："A list of items(curios!) won't be
 * banned when The Nether Apollyon in the phase 'The Apocalypse'"）。</p>
 *
 * <p><b>合规边界</b>：RevelationFix 为 ARR 协议——本类严格限定在「作者开放的配置接口」范围内：
 * <ul>
 *   <li>不修改、不反编译、不反射其模组本体（jar / 类 / 内部状态一律不触碰）；</li>
 *   <li>只对该配置文件做<b>幂等</b>读写：追加或移除我们写入的条目（永恒之心本身 +
 *       用户配置的额外物品 + 自动检测到的佩戴饰品），其它条目与注释原样保留；</li>
 *   <li>等价于整合包作者在配置里手工增删几行。</li>
 * </ul></p>
 */
public final class RevelationFixCompat {

    /** 目标模组 ID（其配置目录名即 modid）。 */
    private static final String MOD_ID = "revelationfix";
    private static final String CONFIG_FILE = "revelationfix/revelationfix-common.toml";
    /** 配置键路径（分组名含空格，NightConfig 以 '.' 分隔路径）。 */
    private static final String ITEMS_PATH = "The Apocalypse.whitelistItems";
    /** 要维护的条目（我们自己的物品）。 */
    private static final String ENTRY = "eternal_heart:eternal_heart";

    /** 自动检测的例行扫描间隔（tick，100 = 5 秒）。 */
    private static final long SCAN_INTERVAL = 100L;

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 上次写入名单的自动检测条目（用于在饰品卸下 / 开关关闭时精确移除）。 */
    private static Set<String> lastAuto = Set.of();
    /** 事件驱动的「尽快重扫」标志（饰品变更 / 玩家上下线时置位）。 */
    private static boolean scanPending = false;
    private static long lastScanTick = Long.MIN_VALUE;
    private static boolean lastWanted = false;
    private static boolean lastAutoDetect = false;

    private RevelationFixCompat() {
    }

    /** 同步结果（命令反馈 / 日志共用）。 */
    public enum Result {
        /** 未安装 RevelationFix。 */
        MOD_ABSENT,
        /** 配置文件尚未生成（模组从未启动过）。 */
        FILE_ABSENT,
        /** 配置文件缺少目标键（版本不匹配）。 */
        KEY_ABSENT,
        /** 已是期望状态（幂等命中）。 */
        ALREADY,
        /** 已写入豁免名单。 */
        ADDED,
        /** 已从豁免名单移除。 */
        REMOVED,
        /** 读写失败（详见日志）。 */
        FAILED
    }

    /**
     * 按期望状态同步豁免名单。
     *
     * @param wanted     true = 确保条目在名单中；false = 移除我们写入的所有条目
     * @param extraItems 额外要维护的物品 ID（除永恒之心本身外；可为空）
     * @param autoItems  自动检测到的佩戴饰品 ID 集合（{@code null} = 本次不动自动条目；
     *                   空列表 = 清空自动条目，如自动检测被关闭时）
     * @return 同步结果
     */
    public static Result sync(boolean wanted, List<? extends String> extraItems, List<? extends String> autoItems) {
        if (!ModList.get().isLoaded(MOD_ID)) return Result.MOD_ABSENT;
        Path file = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE);
        if (!Files.exists(file)) return Result.FILE_ABSENT;

        CommentedFileConfig config = CommentedFileConfig.builder(file)
                .sync().preserveInsertionOrder().build();
        try {
            config.load();
            Object raw = config.get(ITEMS_PATH);
            if (!(raw instanceof List<?> existing)) return Result.KEY_ABSENT;

            List<String> list = new ArrayList<>(existing.size());
            for (Object element : existing) list.add(String.valueOf(element));

            // 固定 + 用户自定义条目：始终作为一个整体维护
            List<String> owned = new ArrayList<>();
            owned.add(ENTRY);
            if (extraItems != null) {
                for (String extra : extraItems) {
                    if (extra != null && !extra.isBlank() && !owned.contains(extra)) owned.add(extra);
                }
            }

            // 本次要维护的自动条目（null = 保持现状不动）
            Set<String> auto = autoItems == null ? null : new TreeSet<>();
            if (autoItems != null) {
                for (String autoItem : autoItems) {
                    if (autoItem != null && !autoItem.isBlank()) auto.add(autoItem);
                }
            }

            boolean changed = false;
            if (wanted) {
                for (String entry : owned) {
                    if (!list.contains(entry)) {
                        list.add(entry);
                        changed = true;
                    }
                }
                if (auto != null) {
                    for (String entry : auto) {
                        if (!list.contains(entry)) {
                            list.add(entry);
                            changed = true;
                        }
                    }
                    // 上次自动写入、现在已无人佩戴的条目：移除（固定/自定义条目除外）
                    for (String stale : lastAuto) {
                        if (!auto.contains(stale) && !owned.contains(stale) && list.remove(stale)) {
                            changed = true;
                        }
                    }
                }
            } else {
                for (String entry : owned) {
                    if (list.remove(entry)) changed = true;
                }
                for (String stale : lastAuto) {
                    if (list.remove(stale)) changed = true;
                }
            }

            if (auto != null) lastAuto = auto;

            if (!changed) return Result.ALREADY;

            config.set(ITEMS_PATH, list);
            config.save();

            Result result = wanted ? Result.ADDED : Result.REMOVED;
            LOGGER.info("[永恒之心] RevelationFix 天启豁免名单已更新（{}，名单共 {} 个条目）", result, list.size());
            if (wanted) {
                LOGGER.info("[永恒之心] 若本次启动 RevelationFix 的配置早于本模组加载，请重启一次游戏使其生效");
            }
            return result;
        } catch (Exception e) {
            LOGGER.warn("[永恒之心] 读写 RevelationFix 豁免名单失败（不影响其它功能）", e);
            return Result.FAILED;
        } finally {
            config.close();
        }
    }

    /**
     * 事件驱动：尽快重扫一次（下一服务端 tick 由 {@link #scanAndSync(MinecraftServer)} 执行）。
     *
     * <p>在 CurioChangeEvent（佩戴/卸下）、玩家登录/登出时调用，让名单尽快跟随
     * 实际佩戴情况，而不是等例行扫描周期。</p>
     */
    public static void scheduleScan() {
        scanPending = true;
    }

    /** 例行入口：带节流的扫描 + 同步（由服务端 tick 调用）。 */
    public static void scanAndSync(MinecraftServer server) {
        scanAndSync(server, false);
    }

    /**
     * 扫描所有在线玩家佩戴中的 Curios 饰品，与当前豁免名单做差量同步。
     *
     * @param force true = 跳过节流与「无变化」判断（命令切换开关后立即生效）
     */
    public static void scanAndSync(MinecraftServer server, boolean force) {
        long tick = server.getTickCount();
        if (!force && !scanPending && tick - lastScanTick < SCAN_INTERVAL) return;
        scanPending = false;
        lastScanTick = tick;

        boolean wanted = ConfigValues.get(EternalHeartConfig.REVELATIONFIX_WHITELIST);
        boolean autoDetect = ConfigValues.get(EternalHeartConfig.REVELATIONFIX_AUTO_DETECT);
        Set<String> detected = autoDetect ? scanWornCurios(server) : Set.of();

        // 开关状态与佩戴集合都没变 → 不动配置文件
        if (!force && wanted == lastWanted && autoDetect == lastAutoDetect && detected.equals(lastAuto)) return;
        lastWanted = wanted;
        lastAutoDetect = autoDetect;

        List<? extends String> extras = ConfigValues.get(EternalHeartConfig.REVELATIONFIX_EXTRA_ITEMS);
        if (!wanted) {
            // 总开关关闭：清掉我们写入的一切（含残留自动条目）
            sync(false, extras, List.of());
        } else if (autoDetect) {
            sync(true, extras, new ArrayList<>(detected));
        } else {
            // 自动检测关闭：固定 + 自定义条目照常维护，自动条目清空
            sync(true, extras, List.of());
        }
    }

    /** 收集所有在线玩家佩戴中的 Curios 饰品 ID 集合。 */
    private static Set<String> scanWornCurios(MinecraftServer server) {
        Set<String> worn = new TreeSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            var handler = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
            if (handler == null) continue;
            for (var stacksHandler : handler.getCurios().values()) {
                var stacks = stacksHandler.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (id != null) worn.add(id.toString());
                }
            }
        }
        return worn;
    }
}
