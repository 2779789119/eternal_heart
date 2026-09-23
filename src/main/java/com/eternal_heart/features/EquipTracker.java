package com.eternal_heart.features;

import com.eternal_heart.EternalHeartMod;
import com.eternal_heart.core.PlayerScoped;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.UUID;

/**
 * 「永恒之心」佩戴状态跟踪器 —— 全项目唯一的装备状态判定入口。
 *
 * <p>解决两类问题：</p>
 *
 * <ol>
 *   <li><b>性能</b>：原来每个事件处理器都直接调用 CuriosApi 查询饰品栏
 *       （tick、hurt、attack、kill…… 一次玩家操作可能触发十几次查询）。
 *       现在服务端每 tick 只查询一次，其余全部走内存缓存。</li>
 *
 *   <li><b>抖断（关键）</b>：维度切换 / 死亡重生 / Curios 数据同步间隙可能出现
 *       1~2 tick 的「瞬时时查不到饰品」误判。原实现据此立即触发卸下清理
 *       （回收飞行能力、移除被动效果、重置永怒……），表现为
 *       <b>「飞着飞着突然掉下来」</b>等状态抖动。这里引入卸下确认防抖：
 *       服务端连续 {@link #UNEQUIP_CONFIRM_TICKS} tick 查不到才判定为真卸下。</li>
 * </ol>
 *
 * <p>端侧差异：客户端是表现层，直接返回实时查询结果（无防抖、无缓存）；
 * 服务端读取 {@link #serverTick} 维护的防抖状态。</p>
 *
 * <p>重写要点：状态表改用 fastutil 开放寻址哈希表（{@link PlayerScoped}），
 * 去掉 {@code ConcurrentHashMap} 的同步开销；状态机与边沿分发逻辑收敛到一个方法内，
 * 保证「每条边沿只触发一次」。</p>
 */
public final class EquipTracker {

    /** 服务端「已卸下」的确认窗口：连续多少 tick 查不到饰品才判定为真卸下（约 0.5 秒） */
    private static final int UNEQUIP_CONFIRM_TICKS = 10;

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 每个玩家一份状态（服务端 / 客户端各是一个独立 JVM 或同线程的独立实例） */
    private static final PlayerScoped<State> STATES = new PlayerScoped<>();

    private EquipTracker() {
    }

    private static final class State {
        /** 防抖后的判定状态 */
        boolean equipped;
        /** 连续未检测到饰品的 tick 计数（防抖计时） */
        int missingTicks;
    }

    /**
     * 实时查询 Curios 饰品栏（无缓存、无防抖）。仅跟踪器内部与配置重载等特殊场景使用。
     *
     * <p><b>关键：区分「未佩戴」与「数据不可用」</b>。</p>
     *
     * <p>{@code getCuriosInventory} 返回空表示 Curios 能力当前读不到（切换物品、维度切换、
     * 死亡重生、能力重建等时刻都可能短暂出现），而不是玩家摘下了饰品。
     * 历史实现把这两种情况都判为「未佩戴」，于是换物品/同步间隙会被当成摘饰品，
     * 触发卸载清理（回收飞行、停掉内置滑翔，表现为「一切换物品飞行就被重置」）。</p>
     *
     * <p>因此这里对「数据不可用」采取保守判定：视为仍然佩戴；只有
     * <b>Curios 数据可读且确实找不到饰品</b> 时才返回 false。</p>
     */
    public static boolean query(Player player) {
        var handler = CuriosApi.getCuriosInventory(player).resolve();
        if (handler.isEmpty()) return true; // 数据不可用 ≠ 未佩戴：保持现状，避免误触发卸载清理
        return handler.get().findFirstCurio(EternalHeartMod.ETERNAL_HEART.get()).isPresent();
    }

    /**
     * 服务端每 tick 调用：刷新佩戴状态、推进防抖计时，
     * 并在「装备 / 卸下」边沿到来时自动分发
     * {@link FeatureManager#dispatchEquip} / {@link FeatureManager#dispatchUnequip}（每条边沿仅一次）。
     *
     * @return 本 tick 的判定状态（true = 视为佩戴中，功能应继续运行）
     */
    public static boolean serverTick(Player player) {
        State state = STATES.compute(player.getUUID(), State::new);
        boolean present = query(player);

        if (present) {
            state.missingTicks = 0;
            if (!state.equipped) {
                state.equipped = true;
                FeatureManager.dispatchEquip(player);
            }
        } else if (state.equipped && ++state.missingTicks >= UNEQUIP_CONFIRM_TICKS) {
            // 卸下边沿：连续多次查不到才确认真卸下。
            // 防止维度切换 / 重生 / 数据同步间隙的瞬时误判触发卸载清理
            //（历史问题：飞行中途被误清理导致「飞一小段就掉下来」）。
            state.equipped = false;
            state.missingTicks = 0;
            LOGGER.debug("[永恒之心] {} 判定为已卸下（连续 {} tick 未检测到饰品），执行卸载清理",
                    player.getName().getString(), UNEQUIP_CONFIRM_TICKS);
            FeatureManager.dispatchUnequip(player);
        }
        return state.equipped;
    }

    /**
     * 客户端每 tick 调用：与 {@link #serverTick} 相同的防抖语义（但不分发装备/卸下边沿，
     * 客户端没有需要清理的服务端状态）。
     *
     * <p>表现层同样需要防抖：内置滑翔、飞行提示等功能直接依赖本判定，若短暂查询失败
     * 就立刻中断，「切换物品」这类操作会让滑翔瞬间消失（表现为飞行状态被重置）。</p>
     */
    public static boolean clientTick(Player player) {
        State state = STATES.compute(player.getUUID(), State::new);
        if (state.equipped) {
            if (query(player)) {
                state.missingTicks = 0;
            } else if (++state.missingTicks >= UNEQUIP_CONFIRM_TICKS) {
                state.equipped = false;
                state.missingTicks = 0;
            }
        } else if (query(player)) {
            state.equipped = true;
            state.missingTicks = 0;
        }
        return state.equipped;
    }

    /**
     * 读取佩戴判定状态（零查询开销，供所有事件处理器复用）。
     *
     * <p>两端都读取由 tick 维护的防抖缓存（客户端见 {@link #clientTick}，
     * 服务端见 {@link #serverTick}）；若从未 tick 过（例如登录瞬间而事件先于 tick 到达），
     * 补做一次实时查询并建立缓存。</p>
     */
    public static boolean isEquipped(Player player) {
        State state = STATES.get(player.getUUID());
        if (state == null) {
            state = new State();
            state.equipped = query(player);
            STATES.put(player.getUUID(), state);
        }
        return state.equipped;
    }

    /** 玩家退出时清理缓存，防止内存泄漏。 */
    public static void forget(UUID id) {
        STATES.remove(id);
    }

    /** 服务器停止时清空全部状态（下次启动从零开始）。 */
    public static void clear() {
        STATES.clear();
    }

    /** 当前被跟踪的玩家数量（诊断用）。 */
    public static int trackedPlayers() {
        return STATES.size();
    }
}
