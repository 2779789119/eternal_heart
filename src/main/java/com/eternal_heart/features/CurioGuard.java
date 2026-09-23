package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.EternalHeartMod;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.core.PlayerScoped;
import com.eternal_heart.core.Ticker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.Collection;

/**
 * 饰品守护（Curio Guard）—— 让永恒之心「死不掉、也锁不住」。
 *
 * <h3>1. 死亡不掉落（{@code curioKeepOnDeath}）</h3>
 * <p>关键是<b>在死亡瞬间就把饰品从 Curios 槽位取出</b>（而不是等 Curios 掉落再从掉落物里
 * 删除）：槽位被清空后，无论 Curios（或其它模组）用哪条路径处理死亡掉落，都无物可掉，
 * 不存在竞态。</p>
 * <ol>
 *   <li>{@link LivingDeathEvent}（未被取消时）：快照物品 + 槽位（identifier / index），
 *       写入内存与玩家持久化数据（防「死亡到重生之间服务器重启」），随即清空该槽位；</li>
 *   <li>{@code LivingDropsEvent}（事件层在 Curios 之后调用 {@link #filterDrops}）：
 *       兜底删除任何被其它路径掉出的永恒之心；</li>
 *   <li>{@link #restoreOnRespawn}（{@code PlayerEvent.Clone}，在 Curios 复制数据之后）：
 *       按「原槽位 → 第一个可用空槽 → 背包 → 脚下」的顺序归还，绝不丢失。</li>
 * </ol>
 *
 * <h3>2. 无视绑定（{@code curioUnbind}）</h3>
 * <ul>
 *   <li>每 20 tick <b>检查</b>饰品上的绑定诅咒附魔（无论由哪个模组附上），
 *       首次发现时清除并打上 {@link #UNBOUND_TAG} 标记，之后不再重写槽位——
 *       重写会触发 Curios 全量重算与槽位变更事件，是「饰品效果每秒闪一下」的根源；</li>
 *   <li>物品自身 {@code canUnequip} 恒为 true（Curios 层允许随时取下），
 *       因此即使外部再次附上诅咒也不影响取下；</li>
 *   <li>命令 {@code /eternalheart curio unequip} 直接写槽位绕过一切锁定，作为最终兜底。</li>
 * </ul>
 *
 * <h3>3. 饰品防夺（{@code curioSeizeGuard}）</h3>
 * <p>对抗外部机制的强行取走（如诡厄巫法：启示录的亚波伦「末日终结」阶段夺取饰品）：
 * 监听 {@code CurioChangeEvent}，若饰品被移出且<b>玩家侧一无所获</b>（背包 / 光标 /
 * 附近掉落物都没有）则判定为被夺取，下一 tick 立即放回原槽位并重建属性加成；
 * 玩家主动卸下的路径不受影响。</p>
 */
public class CurioGuard implements IFeature {

    /** 持久化备份键（写入玩家 PersistentData，跨服务器重启有效）。 */
    private static final String BACKUP_KEY = "eternal_heart_curio_backup";
    private static final String BACKUP_STACK = "stack";
    private static final String BACKUP_IDENTIFIER = "identifier";
    private static final String BACKUP_INDEX = "index";

    /** 绑定诅咒附魔 ID（1.20.1 附魔表存储格式："id" 字段）。 */
    private static final String BINDING_CURSE_ID = "minecraft:binding_curse";

    /**
     * 物品标记：绑定诅咒已由我们清理过。
     *
     * <p>清理动作要重写饰品槽位，而重写会触发 Curios 对该饰品的<b>全量重算</b>与槽位变更事件；
     * 整合包里若有模组响应这些事件（重新施加效果等），就会表现为「饰品效果每秒被清除后重新添加」
     * 的闪烁。清理一次即打标，之后只读不写。诅咒对取下没有影响——
     * 「随时可取下」由物品自身的 {@code canUnequip = true} 保证。</p>
     */
    private static final String UNBOUND_TAG = "eternal_heart_unbound";

    private static final PlayerScoped<Backup> BACKUPS = new PlayerScoped<>();

    /** 被外部机制取走、等待下一 tick 收回的饰品（常态为空）。 */
    private static final PlayerScoped<Backup> SEIZED = new PlayerScoped<>();

    /** 饰品快照（死亡备份 / 被夺收回共用）：物品 + 原槽位（identifier / index）。 */
    private record Backup(ItemStack stack, String identifier, int index) {
    }

    /** Tab 补全 / 消息用的日志节流间隔（tick）：绑定诅咒检查频率。 */
    private static final int UNBIND_CHECK_INTERVAL = 20;

    @Override
    public String getName() {
        return "CurioGuard";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    // ============================================================
    //  1. 死亡不掉落
    // ============================================================

    @Override
    public void onPlayerDeath(Player player, LivingDeathEvent event) {
        if (event.isCanceled()) return;
        if (!ConfigValues.get(EternalHeartConfig.CURIO_KEEP_ON_DEATH)) return;

        ICuriosItemHandler handler = handlerOf(player);
        if (handler == null) return;
        SlotResult found = handler.findFirstCurio(EternalHeartMod.ETERNAL_HEART.get()).orElse(null);
        if (found == null) return;

        SlotContext context = found.slotContext();
        Backup backup = new Backup(found.stack().copy(), context.identifier(), context.index());
        BACKUPS.put(player.getUUID(), backup);
        writeBackup(player, backup);

        // 立即取出：Curios 随后的死亡掉落流程无物可掉（不存在竞态）
        clearSlot(handler, context.identifier(), context.index());
    }

    /**
     * 掉落过滤（事件层在 Curios 处理之后调用）：删除被其它路径掉出的永恒之心。
     *
     * <p>只有「死亡时确实佩戴并已备份」才移除——死亡时饰品在背包里的情况
     * 由背包保护（{@code keepInventory}）或原版掉落规则处理。</p>
     */
    public static void filterDrops(Player player, Collection<ItemEntity> drops) {
        if (!ConfigValues.get(EternalHeartConfig.CURIO_KEEP_ON_DEATH)) return;
        if (backupOf(player) == null) return;
        try {
            drops.removeIf(item -> item.getItem().is(EternalHeartMod.ETERNAL_HEART.get()));
        } catch (UnsupportedOperationException ignored) {
            // 集合不可变（某些模组包装过掉落表）：交由取出机制保证不会掉落
        }
    }

    /**
     * 重生数据复制阶段归还饰品（由事件层在 {@code PlayerEvent.Clone} 中调用，
     * 且必须在 Curios 复制数据之后执行）。
     *
     * @param wasDeath 是否为死亡重生（仅死亡重生才归还）
     */
    public static void restoreOnRespawn(Player oldPlayer, Player newPlayer, boolean wasDeath) {
        if (!wasDeath) return;

        Backup backup = BACKUPS.remove(oldPlayer.getUUID());
        if (backup == null) backup = readBackup(oldPlayer);
        if (backup == null) backup = readBackup(newPlayer);
        clearBackup(oldPlayer);
        clearBackup(newPlayer);
        if (backup == null) return;
        if (!ConfigValues.get(EternalHeartConfig.CURIO_KEEP_ON_DEATH)) return;

        // 饰品已经回到槽位（Curios keepCurios=ON 等实现自行保留了）→ 不重复归还
        if (EquipTracker.query(newPlayer)) return;
        // 避免重复归还：物品已由其它路径还到背包/槽位时按物品计数防重
        if (containsCurio(newPlayer)) return;

        ICuriosItemHandler handler = handlerOf(newPlayer);
        if (handler == null) {
            giveToInventory(newPlayer, backup.stack());
            return;
        }

        if (putBack(handler, backup)) {
            // 主动刷新属性修饰符：不依赖 Curios 是否在槽位变化时重算
            AttributeFeature.refresh(newPlayer);
            return;
        }
        // 兜底：背包（背包满则掉在脚下，绝不丢失）
        giveToInventory(newPlayer, backup.stack());
    }

    /** 优先放回原槽位，其次任意可放置的空槽。 */
    private static boolean putBack(ICuriosItemHandler handler, Backup backup) {
        if (backup.identifier() != null
                && putIntoSlot(handler, backup.identifier(), backup.index(), backup.stack())) {
            return true;
        }
        for (var entry : handler.getCurios().entrySet()) {
            IDynamicStackHandler stacks = entry.getValue().getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                if (putIntoSlot(handler, entry.getKey(), i, backup.stack())) return true;
            }
        }
        return false;
    }

    /**
     * 强制取下（命令入口）：直接写槽位，绕过 {@code canUnequip} 与任何模组的锁定。
     *
     * @return 是否取下成功（未佩戴时返回 false）
     */
    public static boolean forceUnequip(ServerPlayer player) {
        ICuriosItemHandler handler = handlerOf(player);
        if (handler == null) return false;
        SlotResult found = handler.findFirstCurio(EternalHeartMod.ETERNAL_HEART.get()).orElse(null);
        if (found == null) return false;

        SlotContext context = found.slotContext();
        ItemStack stack = found.stack().copy();
        clearSlot(handler, context.identifier(), context.index());
        giveToInventory(player, stack);
        return true;
    }

    // ============================================================
    //  3. 饰品防夺（对抗外部机制的强行取走，如亚波伦「末日终结」）
    // ============================================================

    /**
     * 槽位变更回调（由事件层的 {@code CurioChangeEvent} 调用）。
     *
     * <p>如何区分「被夺取」与「玩家主动卸下」：主动卸下时物品一定会出现在玩家侧
     * （背包 / 光标 / 附近掉落物）；被外部机制取走时玩家侧一无所获。
     * 只有后者才排队收回，因此不会妨碍正常卸下。</p>
     */
    public static void handleSlotLoss(Player player, String identifier, int index, ItemStack removed) {
        if (!ConfigValues.get(EternalHeartConfig.CURIO_SEIZE_GUARD)) return;
        if (player.level().isClientSide()) return;
        if (!removed.is(EternalHeartMod.ETERNAL_HEART.get())) return;
        // 死亡流程由「死亡不掉落」接管：isAlive 为 false 时不收回，避免与其冲突
        if (!player.isAlive()) return;
        if (playerHasItem(player, removed)) return; // 玩家主动卸下
        SEIZED.put(player.getUUID(), new Backup(removed.copy(), identifier, index));
    }

    /** 下一 tick 收回被夺取的饰品（优先原槽位，其次空槽，再次背包）并重建属性加成。 */
    private static void restoreSeized(Player player) {
        Backup seized = SEIZED.remove(player.getUUID());
        if (seized == null) return;
        if (!ConfigValues.get(EternalHeartConfig.CURIO_SEIZE_GUARD)) return;
        if (playerHasItem(player, seized.stack())) return; // 已由其它路径补回

        ICuriosItemHandler handler = handlerOf(player);
        boolean placed = handler != null && putBack(handler, seized);
        if (!placed) giveToInventory(player, seized.stack()); // 槽位不可用：进背包，绝不丢失
        AttributeFeature.refresh(player);
        player.displayClientMessage(Component.translatable("message.eternal_heart.seize_guard"), true);
    }

    /** 玩家侧（背包 / 手持 / 光标 / 附近掉落物）是否已持有该物品。 */
    private static boolean playerHasItem(Player player, ItemStack stack) {
        if (player.getInventory().contains(stack)) return true;
        if (player.containerMenu.getCarried().is(stack.getItem())) return true;
        return !player.level().getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(8.0),
                item -> item.getItem().is(stack.getItem())).isEmpty();
    }

    // ============================================================
    //  2. 无视绑定（清除绑定诅咒）
    // ============================================================

    @Override
    public void onPlayerTick(Player player, LivingTickEvent event) {
        // 每 tick：收回被外部机制取走的饰品（常态为空，零开销）
        restoreSeized(player);
        if (!ConfigValues.get(EternalHeartConfig.CURIO_UNBIND)) return;
        if (!Ticker.due(player.getUUID(), player.level().getGameTime(), UNBIND_CHECK_INTERVAL)) return;
        purgeBindingCurse(player);
    }

    /** 检查并清除饰品上的绑定诅咒（来自任何模组的附魔同样处理）。 */
    private static void purgeBindingCurse(Player player) {
        ICuriosItemHandler handler = handlerOf(player);
        if (handler == null) return;
        SlotResult found = handler.findFirstCurio(EternalHeartMod.ETERNAL_HEART.get()).orElse(null);
        if (found == null) return;

        ItemStack stack = found.stack();
        if (!EnchantmentHelper.hasBindingCurse(stack)) return;
        // 已清理过：不再反复重写槽位（重写会触发 Curios 全量重算与槽位变更事件，
        // 是「饰品效果每秒闪一下」的根源）。诅咒对取下无影响——canUnequip 恒为 true。
        if (stack.getTag() != null && stack.getTag().getBoolean(UNBOUND_TAG)) return;

        ItemStack cleaned = stack.copy();
        removeBindingCurse(cleaned);
        cleaned.getOrCreateTag().putBoolean(UNBOUND_TAG, true);
        // 重新写回槽位：NBT 变更通过槽位同步给客户端，并触发 Curios 的属性重算
        SlotContext context = found.slotContext();
        IDynamicStackHandler stacks = stacksOf(handler, context.identifier());
        if (stacks == null || context.index() < 0 || context.index() >= stacks.getSlots()) return;
        stacks.setStackInSlot(context.index(), cleaned);

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.eternal_heart.unbind"), true);
        }
    }

    /** 从附魔表中剔除绑定诅咒（1.20.1 附魔存储键：Enchantments / StoredEnchantments）。 */
    private static void removeBindingCurse(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        for (String key : new String[]{"Enchantments", "StoredEnchantments"}) {
            ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
            // 倒序删除：避免索引位移（不依赖 ListTag 的集合视图实现）
            for (int i = list.size() - 1; i >= 0; i--) {
                if (BINDING_CURSE_ID.equals(list.getCompound(i).getString("id"))) {
                    list.remove(i);
                }
            }
        }
    }

    // ============================================================
    //  内部工具
    // ============================================================

    private static ICuriosItemHandler handlerOf(Player player) {
        var handler = CuriosApi.getCuriosInventory(player).resolve();
        return handler.isEmpty() ? null : handler.get();
    }

    private static IDynamicStackHandler stacksOf(ICuriosItemHandler handler, String identifier) {
        ICurioStacksHandler stacksHandler = handler.getStacksHandler(identifier).orElse(null);
        return stacksHandler == null ? null : stacksHandler.getStacks();
    }

    /** 空槽位写入（校验槽位类型合法性），成功返回 true。 */
    private static boolean putIntoSlot(ICuriosItemHandler handler, String identifier, int index, ItemStack stack) {
        IDynamicStackHandler stacks = stacksOf(handler, identifier);
        if (stacks == null || index < 0 || index >= stacks.getSlots()) return false;
        if (!stacks.getStackInSlot(index).isEmpty()) return false;
        if (!stacks.isItemValid(index, stack)) return false;
        stacks.setStackInSlot(index, stack);
        return true;
    }

    private static void clearSlot(ICuriosItemHandler handler, String identifier, int index) {
        IDynamicStackHandler stacks = stacksOf(handler, identifier);
        if (stacks == null || index < 0 || index >= stacks.getSlots()) return;
        stacks.setStackInSlot(index, ItemStack.EMPTY);
    }

    private static void giveToInventory(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    /** 玩家是否已经以任何形式持有永恒之心（槽位 / 背包）。 */
    private static boolean containsCurio(Player player) {
        ICuriosItemHandler handler = handlerOf(player);
        if (handler != null && handler.findFirstCurio(EternalHeartMod.ETERNAL_HEART.get()).isPresent()) return true;
        return player.getInventory().contains(new ItemStack(EternalHeartMod.ETERNAL_HEART.get()));
    }

    private static Backup backupOf(Player player) {
        Backup backup = BACKUPS.get(player.getUUID());
        return backup != null ? backup : readBackup(player);
    }

    private static void writeBackup(Player player, Backup backup) {
        CompoundTag tag = new CompoundTag();
        tag.put(BACKUP_STACK, backup.stack().save(new CompoundTag()));
        if (backup.identifier() != null) tag.putString(BACKUP_IDENTIFIER, backup.identifier());
        tag.putInt(BACKUP_INDEX, backup.index());
        player.getPersistentData().put(BACKUP_KEY, tag);
    }

    private static Backup readBackup(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(BACKUP_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = data.getCompound(BACKUP_KEY);
        if (!tag.contains(BACKUP_STACK, Tag.TAG_COMPOUND)) return null;
        ItemStack stack = ItemStack.of(tag.getCompound(BACKUP_STACK));
        if (stack.isEmpty()) return null;
        String identifier = tag.contains(BACKUP_IDENTIFIER) ? tag.getString(BACKUP_IDENTIFIER) : null;
        return new Backup(stack, identifier, tag.getInt(BACKUP_INDEX));
    }

    private static void clearBackup(Player player) {
        player.getPersistentData().remove(BACKUP_KEY);
    }

    /** 玩家退出时清理内存备份（持久化备份保留，供重生归还兜底）。 */
    public static void forget(Player player) {
        BACKUPS.remove(player.getUUID());
    }
}
