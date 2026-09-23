package com.eternal_heart.core;

import java.util.UUID;

/**
 * 周期任务门控 —— 统一「每 N tick 执行一次」的判定，并把不同玩家的任务相位错开。
 *
 * <p>历史实现散落着大量 {@code tick % interval == 0}：单人游玩没有问题，
 * 但多人服务器上所有玩家的重任务（光环方块扫描、自动门扫描、掉落物高亮……）
 * 会在<strong>同一个 tick</strong> 同时执行，形成周期性的 tick 时间尖峰。
 * 这里用玩家 UUID 派生出稳定的相位偏移，使每个玩家的周期任务落在不同 tick 上，
 * 把尖峰摊平到整个周期内。</p>
 *
 * <p>对单个玩家而言语义仍是「每 N tick 一次」，仅起始相位不同。</p>
 */
public final class Ticker {

    private Ticker() {
    }

    /**
     * 本 tick 是否应执行周期任务。
     *
     * @param id     任务归属（玩家 UUID），用于派生稳定相位
     * @param tick   当前游戏时间（{@code level.getGameTime()}）
     * @param period 周期（tick）；{@code <= 1} 表示每 tick 执行
     */
    public static boolean due(UUID id, long tick, int period) {
        if (period <= 1) return true;
        return Math.floorMod(tick + Math.floorMod(id.hashCode(), period), period) == 0;
    }

    /** 周期性任务的相位值（同一玩家同一周期恒定），调试与筛选时可读性更好。 */
    public static int phase(UUID id, int period) {
        return period <= 1 ? 0 : Math.floorMod(id.hashCode(), period);
    }
}
