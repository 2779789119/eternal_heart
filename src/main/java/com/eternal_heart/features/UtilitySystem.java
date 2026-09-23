package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.core.PlayerScoped;
import com.eternal_heart.core.Scan;
import com.eternal_heart.core.Ticker;
import com.eternal_heart.integration.Integrations;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;
import net.minecraftforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 便利系统 —— 掉落物磁铁、发光、自动火把 / 门 / 钓鱼、补货、无限桶、连锁挖矿、掉落转化。
 *
 * <p>重写要点：</p>
 * <ul>
 *   <li><b>连锁挖矿零对象分配</b>：矿脉宽搜改用 fastutil 的 long 队列 / long 集合 +
 *       复用可变坐标查询（历史实现用 {@code LinkedList<BlockPos>} + {@code LinkedHashSet<BlockPos>}，
 *       每个节点都要装箱坐标并新建邻域 {@code BlockPos}，密集矿脉下一次连锁会产生上千次分配）；</li>
 *   <li><b>磁铁免开方</b>：以距离平方比较替代 {@code distanceTo}（每 tick 每个掉落物省一次 sqrt）；</li>
 *   <li><b>玩家状态统一管理</b>：待处理连锁与火把槽位缓存改由 {@link PlayerScoped} 承载
 *       （原为裸 {@code HashMap}，退出时只在部分路径清理）；</li>
 *   <li><b>周期任务相位错开</b>：自动门 / 高亮 / 钓鱼 / 补货统一走 {@link Ticker}，
 *       多人服务器上不再同一 tick 集中扫描；</li>
 *   <li>无限桶改为内存镜像 + 变更时才写 NBT（保留跨会话持久化语义）。</li>
 * </ul>
 */
public class UtilitySystem implements IFeature {

    private static final TagKey<Block> FORGE_ORES_TAG =
            BlockTags.create(new ResourceLocation("forge", "ores"));

    // ==================== 熔炼结果缓存 ====================

    private static final Map<Item, ItemStack> SMELTING_CACHE = new ConcurrentHashMap<>();
    private static final ItemStack NO_SMELT = ItemStack.EMPTY;

    // ==================== 连锁挖矿 ====================

    /** 26 邻域坐标增量（零分配宽搜用）。 */
    private static final int[] NEIGHBOR_DX = new int[26];
    private static final int[] NEIGHBOR_DY = new int[26];
    private static final int[] NEIGHBOR_DZ = new int[26];

    static {
        int index = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    NEIGHBOR_DX[index] = dx;
                    NEIGHBOR_DY[index] = dy;
                    NEIGHBOR_DZ[index] = dz;
                    index++;
                }
            }
        }
    }

    /** 每个玩家最多一条待处理连锁（破坏事件触发，延迟 2 tick 结算以让原版破坏流程完成）。 */
    private static final PlayerScoped<PendingVein> PENDING_VEINS = new PlayerScoped<>();

    /** 防止连锁破坏再触发连锁（destroyBlock 会再次发出 BreakEvent）。 */
    private static final ThreadLocal<Boolean> MINING = ThreadLocal.withInitial(() -> false);

    private record PendingVein(ServerLevel level, BlockPos origin, Block block,
                               List<BlockPos> neighbors, long tick, boolean activated) {
    }

    // ==================== 自动火把 ====================

    private static final PlayerScoped<Integer> TORCH_SLOT = new PlayerScoped<>();

    // ==================== 自动钓鱼 ====================

    private static final ResourceLocation FISH_TABLE = new ResourceLocation("minecraft", "gameplay/fishing/fish");
    private static final ResourceLocation JUNK_TABLE = new ResourceLocation("minecraft", "gameplay/fishing/junk");
    private static final ResourceLocation TREASURE_TABLE = new ResourceLocation("minecraft", "gameplay/fishing/treasure");

    private static final String WATER_CHECK_KEY = "eternal_heart_water_check";
    private static final String WATER_POS_KEY = "eternal_heart_water_pos";

    // ==================== 无限桶 ====================

    private static final String BUCKET_MAIN_KEY = "eternal_heart_last_bucket_main";
    private static final String BUCKET_OFF_KEY = "eternal_heart_last_bucket_off";
    private static final PlayerScoped<BucketMemory> BUCKETS = new PlayerScoped<>();

    /** 玩家上一 tick 手持的满桶类型（用于识别"刚倒出内容"的瞬间并补回）。 */
    private static final class BucketMemory {
        Item main;
        Item off;
        boolean loaded;
        boolean dirty;
    }

    @Override
    public String getName() {
        return "UtilitySystem";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    // ============================================================
    //  Tick
    // ============================================================

    @Override
    public void onPlayerTick(Player player, LivingTickEvent event) {
        long tick = player.level().getGameTime();
        UUID id = player.getUUID();

        applyMagnet(player);
        if (Ticker.due(id, tick, 20)) applyGlow(player);
        applyAutoTorch(player);
        if (Ticker.due(id, tick, 5)) applyAutoDoor(player);
        if (Ticker.due(id, tick, 10)) applyOreHighlight(player);
        if (ConfigValues.get(EternalHeartConfig.AUTO_FISH)) {
            int interval = ConfigValues.get(EternalHeartConfig.AUTO_FISH_INTERVAL);
            if (Ticker.due(id, tick, interval)) applyAutoFish(player, tick);
        }
        if (Ticker.due(id, tick, 20)) applyAutoRefill(player);
        applyInfiniteBucket(player, tick);
        finishVein(player, tick);
    }

    @Override
    public void onUnequip(Player player) {
        PendingVein pending = PENDING_VEINS.remove(player.getUUID());
        if (pending != null && pending.activated()) Integrations.deactivateUltimine(player);
        TORCH_SLOT.remove(player.getUUID());
        BUCKETS.remove(player.getUUID());
    }

    // ============================================================
    //  连锁挖矿
    // ============================================================

    @Override
    public void onBlockBreak(Player player, BlockEvent.BreakEvent event) {
        if (player.isCreative() || event.isCanceled() || !(player instanceof ServerPlayer)) return;

        // 熔炼 / 额外时运由战利品修饰器在破坏成功后处理。
        // 这里不取消事件、也不自行移除方块：那会绕过保护、耐久与采集等级检查。
        BlockState state = event.getState();
        if (MINING.get() || PENDING_VEINS.get(player.getUUID()) != null) return;
        if (!ConfigValues.get(EternalHeartConfig.VEIN_MINER) || player.isCrouching()) return;
        if (!isOreBlock(state)) return;

        List<BlockPos> neighbors = findVein(player.level(), state, event.getPos(),
                ConfigValues.get(EternalHeartConfig.VEIN_MAX_BLOCKS));
        boolean activated = ConfigValues.get(EternalHeartConfig.USE_FTB_ULTIMINE) && Integrations.activateUltimine(player);
        PENDING_VEINS.put(player.getUUID(), new PendingVein((ServerLevel) player.level(),
                event.getPos().immutable(), state.getBlock(), neighbors,
                player.level().getGameTime() + 2, activated));
    }

    /** 延迟结算：等待原版破坏流程完成，再决定是否需要内置连锁（FTB Ultimine 已处理则跳过）。 */
    private void finishVein(Player player, long tick) {
        PendingVein pending = PENDING_VEINS.get(player.getUUID());
        if (pending == null || tick < pending.tick()) return;
        PENDING_VEINS.remove(player.getUUID());

        if (pending.activated()) Integrations.deactivateUltimine(player);

        if (player.level() != pending.level() || player.isCreative() || !player.isAlive()) return;
        if (!ConfigValues.get(EternalHeartConfig.VEIN_MINER)) return;
        // 原方块仍然存在 = 破坏未成立（被保护 / 被取消）
        if (pending.level().getBlockState(pending.origin()).is(pending.block())) return;

        // Ultimine 已经处理了邻域方块，无需再自行破坏
        boolean handled = pending.activated() && pending.neighbors().stream()
                .anyMatch(pos -> !pending.level().getBlockState(pos).is(pending.block()));
        if (handled || !(player instanceof ServerPlayer serverPlayer)) return;

        MINING.set(true);
        try {
            for (BlockPos pos : pending.neighbors()) {
                if (!player.isAlive() || !EquipTracker.query(player)) break;
                if (!pending.level().hasChunkAt(pos)) continue;
                if (!pending.level().getBlockState(pos).is(pending.block())) continue;
                serverPlayer.gameMode.destroyBlock(pos);
            }
        } finally {
            MINING.remove();
        }
    }

    /**
     * 以 origin 为起点宽搜同种方块矿脉（26 邻域）。
     *
     * <p>全程使用 long 编码坐标与复用查询坐标：队列、访问集合、结果集都不产生坐标对象，
     * 仅在返回时把结果解码为不可变 {@link BlockPos}（数量不超过连锁上限）。</p>
     */
    private static List<BlockPos> findVein(Level level, BlockState originState, BlockPos origin, int maxBlocks) {
        Block target = originState.getBlock();
        // 配置下限已放开：maxBlocks 可能为负，用 long 计算避免 MIN_VALUE - 1 反向溢出成巨正值
        long rawLimit = Math.max(0L, (long) maxBlocks - 1);
        int limit = (int) Math.min(rawLimit, 65536); // 与历史实现一致：上限包含起点本身

        LongOpenHashSet visited = new LongOpenHashSet(Math.min(Math.max(limit, 16), 512));
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        LongArrayList vein = new LongArrayList();

        long start = origin.asLong();
        visited.add(start);
        queue.enqueue(start);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty() && vein.size() < limit) {
            long current = queue.dequeueLong();
            int x = BlockPos.getX(current);
            int y = BlockPos.getY(current);
            int z = BlockPos.getZ(current);

            for (int i = 0; i < 26 && vein.size() < limit; i++) {
                int nx = x + NEIGHBOR_DX[i];
                int ny = y + NEIGHBOR_DY[i];
                int nz = z + NEIGHBOR_DZ[i];
                long encoded = BlockPos.asLong(nx, ny, nz);
                if (!visited.add(encoded)) continue;

                cursor.set(nx, ny, nz);
                if (!level.hasChunkAt(cursor)) continue;
                if (level.getBlockState(cursor).getBlock() != target) continue;

                vein.add(encoded);
                queue.enqueue(encoded);
            }
        }

        List<BlockPos> result = new ArrayList<>(vein.size());
        for (int i = 0; i < vein.size(); i++) result.add(BlockPos.of(vein.getLong(i)));
        return result;
    }

    private static boolean isOreBlock(BlockState state) {
        if (ConfigValues.get(EternalHeartConfig.VEIN_USE_TAGS)) {
            return state.is(FORGE_ORES_TAG);
        }
        return state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)
                || state.is(Blocks.COPPER_ORE) || state.is(Blocks.DEEPSLATE_COPPER_ORE)
                || state.is(Blocks.GOLD_ORE) || state.is(Blocks.DEEPSLATE_GOLD_ORE)
                || state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)
                || state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE)
                || state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)
                || state.is(Blocks.LAPIS_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE)
                || state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE)
                || state.is(Blocks.NETHER_QUARTZ_ORE)
                || state.is(Blocks.NETHER_GOLD_ORE)
                || state.is(Blocks.ANCIENT_DEBRIS);
    }

    // ============================================================
    //  掉落转化（熔炼 / 额外时运）
    // ============================================================

    /** 对成功破坏产生的掉落物做转化；绝不在此移除方块（交由战利品修饰器处理）。 */
    public static List<ItemStack> processDrops(Player player, BlockState state, List<ItemStack> vanillaDrops) {
        boolean smelt = ConfigValues.get(EternalHeartConfig.AUTO_SMELT);
        int fortuneBonus = isOreBlock(state) ? ConfigValues.get(EternalHeartConfig.FORTUNE_BONUS) : 0;
        Level level = player.level();

        List<ItemStack> finalDrops = new ArrayList<>(vanillaDrops.size());
        for (ItemStack drop : vanillaDrops) {
            ItemStack processed = smelt ? smeltOrOriginal(level, drop) : drop.copy();
            if (processed.isEmpty()) continue;
            finalDrops.add(processed);

            // 额外时运：每级 33% 概率额外复制一份（近似原版时运的期望收益）
            for (int level_ = 0; level_ < fortuneBonus; level_++) {
                if (player.getRandom().nextFloat() < 0.33f) {
                    finalDrops.add(processed.copy());
                }
            }
        }
        return finalDrops;
    }

    /** 按熔炼配方转换掉落物；无配方或客户端时返回原物副本（保留原数量）。 */
    private static ItemStack smeltOrOriginal(Level level, ItemStack drop) {
        ItemStack result = findSmeltingResult(level, drop);
        if (result.isEmpty()) return drop.copy();
        result.setCount(result.getCount() * drop.getCount());
        return result;
    }

    /** 熔炼结果查询（Item → 结果 缓存，无配方缓存为空标记）。 */
    private static ItemStack findSmeltingResult(Level level, ItemStack input) {
        if (input.isEmpty()) return ItemStack.EMPTY;
        Item item = input.getItem();

        ItemStack cached = SMELTING_CACHE.get(item);
        if (cached != null) return cached.copy();
        if (!(level instanceof ServerLevel serverLevel)) return ItemStack.EMPTY;

        for (var recipe : serverLevel.getRecipeManager()
                .getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.SMELTING)) {
            if (recipe.getIngredients().get(0).test(input)) {
                ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
                SMELTING_CACHE.put(item, result.copy());
                return result;
            }
        }
        SMELTING_CACHE.put(item, NO_SMELT);
        return ItemStack.EMPTY;
    }

    /** 数据包重载后熔炼配方可能变化，清空缓存。 */
    public static void invalidateSmeltingCache() {
        SMELTING_CACHE.clear();
    }

    // ============================================================
    //  磁铁
    // ============================================================

    private void applyMagnet(Player player) {
        double pullRange = ConfigValues.get(EternalHeartConfig.MAGNET_RANGE);
        double instantRange = ConfigValues.get(EternalHeartConfig.MAGNET_INSTANT_RANGE);
        if (pullRange <= 0 && instantRange <= 0) return;

        double radius = Math.max(pullRange, instantRange);
        double instantSq = instantRange * instantRange;
        double pullSq = pullRange * pullRange;

        for (ItemEntity item : Scan.of(player.level(), player, radius, ItemEntity.class,
                entity -> !entity.hasPickUpDelay())) {
            double distanceSq = item.distanceToSqr(player);
            if (instantRange > 0 && distanceSq <= instantSq) {
                item.playerTouch(player);
            } else if (pullRange > 0 && distanceSq <= pullSq) {
                Vec3 pull = player.position().subtract(item.position()).normalize().scale(0.4);
                item.setDeltaMovement(item.getDeltaMovement().add(pull));
            }
        }
    }

    // ============================================================
    //  发光（自身周围 / 洞穴高亮）
    // ============================================================

    private void applyGlow(Player player) {
        double range = ConfigValues.get(EternalHeartConfig.GLOW_RANGE);
        if (range <= 0) return;
        glowAround(player, range);
    }

    private void applyOreHighlight(Player player) {
        if (!ConfigValues.get(EternalHeartConfig.ORE_HIGHLIGHT)) return;
        if (player.getY() > 64) return;
        double range = ConfigValues.get(EternalHeartConfig.ORE_HIGHLIGHT_RANGE);
        if (range <= 0) return;

        // 发光效果持续 400 tick，无需高频刷新
        glowAround(player, range);
        for (ItemEntity item : Scan.of(player.level(), player, range, ItemEntity.class, entity -> true)) {
            item.setGlowingTag(true);
        }
    }

    /** 让范围内尚未发光的生物发光（包含动物 / 村民等全部生物，与原实现一致）。 */
    private static void glowAround(Player player, double range) {
        for (LivingEntity entity : Scan.of(player.level(), player, range, LivingEntity.class,
                e -> e != player && e.isAlive() && !e.hasEffect(MobEffects.GLOWING))) {
            entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 400, 0, false, false));
        }
    }

    // ============================================================
    //  自动火把
    // ============================================================

    private void applyAutoTorch(Player player) {
        if (!ConfigValues.get(EternalHeartConfig.AUTO_TORCH)) return;
        if (!player.onGround()) return;

        Level level = player.level();
        BlockPos feetPos = player.blockPosition();
        if (!level.isEmptyBlock(feetPos)) return;
        if (level.getBrightness(LightLayer.BLOCK, feetPos) > ConfigValues.get(EternalHeartConfig.AUTO_TORCH_LIGHT_LEVEL)) return;
        if (!level.getBlockState(feetPos.below()).isSolid()) return;

        UUID id = player.getUUID();
        Inventory inventory = player.getInventory();

        // 优先复用上次的火把槽位（避免每次都从头扫描背包）
        Integer cached = TORCH_SLOT.get(id);
        if (cached != null && cached < inventory.getContainerSize()
                && inventory.getItem(cached).is(Items.TORCH)) {
            placeTorch(player, level, feetPos, inventory.getItem(cached), cached);
            return;
        }

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(Items.TORCH)) continue;
            placeTorch(player, level, feetPos, stack, slot);
            return;
        }
    }

    private void placeTorch(Player player, Level level, BlockPos pos, ItemStack torches, int slot) {
        level.setBlock(pos, Blocks.TORCH.defaultBlockState(), 3);
        if (!player.isCreative()) {
            torches.shrink(1);
        }
        if (torches.isEmpty()) {
            TORCH_SLOT.remove(player.getUUID());
        } else {
            TORCH_SLOT.put(player.getUUID(), slot);
        }
    }

    // ============================================================
    //  自动门
    // ============================================================

    private void applyAutoDoor(Player player) {
        if (!ConfigValues.get(EternalHeartConfig.AUTO_DOOR)) return;

        Level level = player.level();
        Vec3 playerPos = player.position();
        final int radius = 3;

        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(playerPos.x - radius, playerPos.y - 1, playerPos.z - radius),
                BlockPos.containing(playerPos.x + radius, playerPos.y + 2, playerPos.z + radius))) {

            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof DoorBlock door)) continue;
            if (state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;

            double dx = pos.getX() + 0.5 - playerPos.x;
            double dy = pos.getY() + 0.5 - playerPos.y;
            double dz = pos.getZ() + 0.5 - playerPos.z;
            double distanceSq = dx * dx + dy * dy + dz * dz;

            if (distanceSq <= 2.25) {
                if (!state.getValue(DoorBlock.OPEN)) {
                    door.setOpen(player, level, state, pos, true);
                }
            } else if (distanceSq > 6.25) {
                // 重新读取最新状态（玩家可能已手动关门）
                BlockState fresh = level.getBlockState(pos);
                if (fresh.getBlock() instanceof DoorBlock
                        && fresh.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                        && fresh.getValue(DoorBlock.OPEN)) {
                    door.setOpen(player, level, fresh, pos, false);
                }
            }
        }
    }

    // ============================================================
    //  自动钓鱼
    // ============================================================

    private void applyAutoFish(Player player, long tick) {
        if (!player.getMainHandItem().is(Items.FISHING_ROD)) return;

        Level level = player.level();
        BlockPos waterPos = findWater(player, level, tick);
        if (waterPos == null) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        float roll = player.getRandom().nextFloat();
        ResourceLocation tableKey = roll < 0.85f ? FISH_TABLE : roll < 0.95f ? JUNK_TABLE : TREASURE_TABLE;

        LootTable table = serverLevel.getServer().getLootData().getLootTable(tableKey);
        LootParams params = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(waterPos))
                .withParameter(LootContextParams.TOOL, player.getMainHandItem())
                .withLuck(player.getLuck())
                .create(LootContextParamSets.FISHING);

        for (ItemStack stack : table.getRandomItems(params)) {
            if (!player.addItem(stack)) {
                player.level().addFreshEntity(new ItemEntity(player.level(),
                        player.getX(), player.getY() + 1, player.getZ(), stack));
            }
        }
        player.getMainHandItem().hurtAndBreak(1, player, broken -> {});
    }

    /** 在水边缓存水源位置，避免每次都做 5×3×5 的立方体扫描。 */
    private static BlockPos findWater(Player player, Level level, long tick) {
        BlockPos feet = player.blockPosition();
        CompoundTag data = player.getPersistentData();
        long lastCheck = data.getLong(WATER_CHECK_KEY);

        if (tick - lastCheck <= 20 && data.contains(WATER_POS_KEY)) {
            return BlockPos.of(data.getLong(WATER_POS_KEY));
        }

        BlockPos found = null;
        for (int dx = -2; dx <= 2 && found == null; dx++) {
            for (int dz = -2; dz <= 2 && found == null; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos check = feet.offset(dx, dy, dz);
                    if (level.getFluidState(check).is(Fluids.WATER)) {
                        found = check;
                        break;
                    }
                }
            }
        }
        data.putLong(WATER_CHECK_KEY, tick);
        if (found != null) data.putLong(WATER_POS_KEY, found.asLong());
        return found;
    }

    // ============================================================
    //  自动补货
    // ============================================================

    private void applyAutoRefill(Player player) {
        if (!ConfigValues.get(EternalHeartConfig.AUTO_REFILL)) return;
        refillHotbar(player.getInventory());
    }

    /** 只搬运匹配且数量更多的堆，绝不改动空快捷栏格（空位不猜测物品）。 */
    public static void refillHotbar(Inventory inventory) {
        boolean changed = false;

        for (int hotbar = 0; hotbar < 9; hotbar++) {
            ItemStack hotbarStack = inventory.getItem(hotbar);
            if (hotbarStack.isEmpty()) continue;
            if (hotbarStack.getMaxStackSize() <= 1) continue;
            if (hotbarStack.getCount() > 1) continue;

            int mainEnd = Math.min(36, inventory.getContainerSize());
            int limit = Math.min(hotbarStack.getMaxStackSize(), inventory.getMaxStackSize());

            for (int slot = 9; slot < mainEnd && hotbarStack.getCount() < limit; slot++) {
                ItemStack source = inventory.getItem(slot);
                if (source.isEmpty() || !ItemStack.isSameItemSameTags(hotbarStack, source)) continue;
                int moved = Math.min(limit - hotbarStack.getCount(), source.getCount());
                hotbarStack.grow(moved);
                source.shrink(moved);
                if (source.isEmpty()) inventory.setItem(slot, ItemStack.EMPTY);
                changed |= moved > 0;
            }
        }
        if (changed) inventory.setChanged();
    }

    // ============================================================
    //  无限桶
    // ============================================================

    /**
     * 无限桶：仅当「上一 tick 手持满桶（水 / 熔岩），本 tick 变成空桶」时把空桶补回原类型。
     *
     * <p>空桶保持空桶、熔岩桶补回熔岩桶；是否允许无限熔岩由 INFINITE_BUCKET_LAVA 控制。
     * 状态存内存镜像，仅在变化时写回 NBT（保留跨会话持久化）。</p>
     */
    private void applyInfiniteBucket(Player player, long tick) {
        if (!ConfigValues.get(EternalHeartConfig.INFINITE_BUCKET)) return;
        if (player.level().isClientSide()) return;

        BucketMemory memory = bucketMemory(player);
        boolean lavaEnabled = ConfigValues.get(EternalHeartConfig.INFINITE_BUCKET_LAVA);

        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        if (mainHand.is(Items.BUCKET)) {
            Item refill = refill(memory.main, lavaEnabled);
            if (refill != null) {
                player.getInventory().setItem(player.getInventory().selected, new ItemStack(refill, 1));
            }
        }
        if (offHand.is(Items.BUCKET)) {
            Item refill = refill(memory.off, lavaEnabled);
            if (refill != null) {
                player.getInventory().offhand.set(0, new ItemStack(refill, 1));
            }
        }

        Item nextMain = fullBucketOf(mainHand);
        Item nextOff = fullBucketOf(offHand);
        if (nextMain != memory.main || nextOff != memory.off) {
            memory.main = nextMain;
            memory.off = nextOff;
            memory.dirty = true;
        }
        if (memory.dirty) {
            persistBuckets(player, memory);
            memory.dirty = false;
        }
    }

    private static BucketMemory bucketMemory(Player player) {
        BucketMemory memory = BUCKETS.compute(player.getUUID(), BucketMemory::new);
        if (memory.loaded) return memory;

        CompoundTag data = player.getPersistentData();
        memory.main = bucketFromName(data.getString(BUCKET_MAIN_KEY));
        memory.off = bucketFromName(data.getString(BUCKET_OFF_KEY));
        memory.loaded = true;
        return memory;
    }

    private static void persistBuckets(Player player, BucketMemory memory) {
        CompoundTag data = player.getPersistentData();
        data.putString(BUCKET_MAIN_KEY, nameOf(memory.main));
        data.putString(BUCKET_OFF_KEY, nameOf(memory.off));
    }

    private static Item fullBucketOf(ItemStack stack) {
        if (stack.is(Items.WATER_BUCKET)) return Items.WATER_BUCKET;
        if (stack.is(Items.LAVA_BUCKET)) return Items.LAVA_BUCKET;
        return null;
    }

    /** 依据上一 tick 的满桶类型决定补回什么；不允许或不需要补时返回 null。 */
    private static Item refill(Item previous, boolean lavaEnabled) {
        if (previous == Items.WATER_BUCKET) return Items.WATER_BUCKET;
        if (previous == Items.LAVA_BUCKET) return lavaEnabled ? Items.LAVA_BUCKET : null;
        return null;
    }

    private static Item bucketFromName(String name) {
        return switch (name) {
            case "water" -> Items.WATER_BUCKET;
            case "lava" -> Items.LAVA_BUCKET;
            default -> null;
        };
    }

    private static String nameOf(Item item) {
        if (item == Items.WATER_BUCKET) return "water";
        if (item == Items.LAVA_BUCKET) return "lava";
        return "none";
    }
}
