package com.eternal_heart.features;

import com.eternal_heart.core.PlayerScoped;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * 吸收护盾记账 —— 吸收值（Absorption）是玩家身上的共享标量，金苹果、其它模组都会往里加。
 * 这里只记录「我们贡献了多少」，卸下饰品时先花掉自己的份额，再归还剩余的外部份额。
 *
 * <p>重写要点：历史实现每次 {@code reconcile} 都读写 3~4 次 NBT（满血充能期间每 tick 都会发生）。
 * 现在改为内存记账，<strong>仅在数值真正变化时</strong>落盘，充能稳态期的 NBT 操作降为 0；
 * 数值仍持久化，跨重启后卸下依然能准确归还。</p>
 */
public final class OwnedShield {

    private static final String OWNED = "eternal_heart_shield_owned";
    private static final String OBSERVED = "eternal_heart_shield_observed";

    private static final PlayerScoped<Account> ACCOUNTS = new PlayerScoped<>();

    private OwnedShield() {
    }

    /** 单玩家的护盾账目（内存镜像 + 已落盘值，用于跳过无变更写入）。 */
    private static final class Account {
        float owned;
        float observed;
        boolean initialized;
        boolean written;
        float writtenOwned;
        float writtenObserved;
    }

    private static Account account(Player player) {
        Account cached = ACCOUNTS.get(player.getUUID());
        if (cached != null) return cached;

        Account account = new Account();
        CompoundTag data = player.getPersistentData();
        if (data.contains(OWNED)) {
            account.owned = data.getFloat(OWNED);
            account.observed = data.getFloat(OBSERVED);
            account.initialized = true;
        }
        ACCOUNTS.put(player.getUUID(), account);
        return account;
    }

    /**
     * 对账：根据当前吸收值推断我们的份额还剩多少（吸收值被消耗时优先扣我们的部分）。
     *
     * @return 当前属于我们的吸收量
     */
    public static float reconcile(Player player) {
        Account account = account(player);
        float current = Math.max(0, player.getAbsorptionAmount());
        float owned = account.owned;
        if (account.initialized) {
            owned -= Math.max(0, account.observed - current);
            account.owned = Math.max(0, Math.min(owned, current));
        } else {
            // 首次对账：存量吸收值全部视为外部来源
            account.owned = 0;
        }
        account.observed = current;
        account.initialized = true;
        persist(player, account);
        return account.owned;
    }

    /** 充能：向吸收值追加我们的份额（不超过上限）。 */
    public static void charge(Player player, float perTick, float cap) {
        Account account = account(player);
        float owned = reconcile(player);
        float current = player.getAbsorptionAmount();
        float added = Math.max(0, Math.min(perTick, cap - current));
        if (added <= 0) return;

        player.setAbsorptionAmount(current + added);
        float total = player.getAbsorptionAmount();
        account.owned = owned + Math.max(0, total - current);
        account.observed = total;
        account.initialized = true;
        persist(player, account);
    }

    /** 卸下：移除我们的剩余份额，外部吸收值保持不变。 */
    public static void release(Player player) {
        float owned = reconcile(player);
        player.setAbsorptionAmount(Math.max(0, player.getAbsorptionAmount() - owned));

        Account account = account(player);
        account.owned = 0;
        account.observed = player.getAbsorptionAmount();
        account.initialized = true;
        account.written = false;

        CompoundTag data = player.getPersistentData();
        data.remove(OWNED);
        data.remove(OBSERVED);
    }

    /** 玩家退出时清理内存账目（数据已持久化）。 */
    public static void forget(Player player) {
        ACCOUNTS.remove(player.getUUID());
    }

    private static void persist(Player player, Account account) {
        if (account.written && account.writtenOwned == account.owned && account.writtenObserved == account.observed) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        data.putFloat(OWNED, account.owned);
        data.putFloat(OBSERVED, account.observed);
        account.written = true;
        account.writtenOwned = account.owned;
        account.writtenObserved = account.observed;
    }
}
