package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.integration.Integrations;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;
import net.minecraftforge.event.level.BlockEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UtilitySystem implements IFeature {

    private static final TagKey<Block> FORGE_ORES_TAG =
            BlockTags.create(new ResourceLocation("forge", "ores"));

    private static final Map<Item, ItemStack> SMELTING_CACHE = new ConcurrentHashMap<>();
    private static final ItemStack NO_SMELT = ItemStack.EMPTY;

    private static final Map<UUID, Integer> TORCH_SLOT_CACHE = new HashMap<>();

    private static final BlockPos[] VEIN_NEIGHBORS;
    static {
        List<BlockPos> offsets = new ArrayList<>(26);
        for (int x = -1; x <= 1; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++)
                    if (x != 0 || y != 0 || z != 0)
                        offsets.add(new BlockPos(x, y, z));
        VEIN_NEIGHBORS = offsets.toArray(new BlockPos[0]);
    }

    private static final ResourceLocation FISH_TABLE = new ResourceLocation("minecraft", "gameplay/fishing/fish");
    private static final ResourceLocation JUNK_TABLE = new ResourceLocation("minecraft", "gameplay/fishing/junk");
    private static final ResourceLocation TREASURE_TABLE = new ResourceLocation("minecraft", "gameplay/fishing/treasure");

    private static final String WATER_CHECK_KEY = "eternal_heart_water_check";
    private static final String WATER_POS_KEY = "eternal_heart_water_pos";

    @Override
    public String getName() {
        return "UtilitySystem";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void onPlayerTick(Player player, LivingTickEvent event) {
        long tick = player.level().getGameTime();

        applyMagnet(player);
        applyGlow(player, tick);
        applyAutoTorch(player);
        applyAutoDoor(player, tick);
        applyOreHighlight(player);
        applyAutoFish(player, tick);
        applyAutoOpenChest(player, tick);
        applyAutoRefill(player, tick);
        applyInfiniteBucket(player);
    }

    @Override
    public void onBlockBreak(Player player, BlockEvent.BreakEvent event) {
        if (player.isCreative()) return;

        BlockState state = event.getState();
        BlockPos pos = event.getPos();

        if (EternalHeartConfig.VEIN_MINER.get() && !player.isCrouching()) {
            if (isOreBlock(state)) {
                if (EternalHeartConfig.USE_FTB_ULTIMINE.get() && Integrations.isFtbUltimineLoaded()) {
                    Integrations.activateUltimine(player);
                } else {
                    veinMine(event, player, state, pos);
                }
            }
        }

        boolean smelt = EternalHeartConfig.AUTO_SMELT.get();
        int fortuneBonus = EternalHeartConfig.FORTUNE_BONUS.get();
        if (smelt || fortuneBonus > 0) {
            event.setCanceled(true);
            processBlockDrops(event, player, state, pos, smelt, fortuneBonus);
        }
    }

    private void applyMagnet(Player player) {
        double range = EternalHeartConfig.MAGNET_RANGE.get();
        double instantRange = EternalHeartConfig.MAGNET_INSTANT_RANGE.get();
        if (range <= 0 && instantRange <= 0) return;

        AABB area = player.getBoundingBox().inflate(Math.max(range, instantRange));
        List<ItemEntity> items = player.level().getEntitiesOfClass(ItemEntity.class, area,
                e -> !e.hasPickUpDelay());
        for (ItemEntity item : items) {
            double dist = item.distanceTo(player);
            if (dist <= instantRange) {
                item.playerTouch(player);
                continue;
            }
            if (range > 0 && dist <= range) {
                Vec3 pull = player.position().subtract(item.position()).normalize().scale(0.4);
                item.setDeltaMovement(item.getDeltaMovement().add(pull));
            }
        }
    }

    private void applyGlow(Player player, long tick) {
        double range = EternalHeartConfig.GLOW_RANGE.get();
        if (range <= 0) return;
        if (tick % 20 != 0) return;

        AABB area = player.getBoundingBox().inflate(range);
        List<LivingEntity> entities = player.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e != player && e.isAlive() && !e.hasEffect(MobEffects.GLOWING));
        for (LivingEntity e : entities) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 400, 0, false, false));
        }
    }

    private boolean isOreBlock(BlockState state) {
        if (EternalHeartConfig.VEIN_USE_TAGS.get()) {
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

    private void veinMine(BlockEvent.BreakEvent event, Player player, BlockState state, BlockPos origin) {
        int maxBlocks = EternalHeartConfig.VEIN_MAX_BLOCKS.get();
        Set<BlockPos> vein = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(origin);
        vein.add(origin);

        Block targetBlock = state.getBlock();
        Level level = player.level();

        while (!queue.isEmpty() && vein.size() < maxBlocks) {
            BlockPos cur = queue.poll();
            for (BlockPos offset : VEIN_NEIGHBORS) {
                if (vein.size() >= maxBlocks) break;
                BlockPos neighbor = cur.offset(offset);
                if (!vein.contains(neighbor) && level.getBlockState(neighbor).getBlock() == targetBlock) {
                    vein.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        for (BlockPos p : vein) {
            if (p.equals(origin) && !event.isCanceled()) {
                level.removeBlock(p, false);
            } else {
                level.destroyBlock(p, true, player);
            }
        }
    }

    private void processBlockDrops(BlockEvent.BreakEvent event, Player player,
                                   BlockState state, BlockPos pos, boolean smelt, int fortuneBonus) {
        Level level = player.level();
        ItemStack tool = player.getMainHandItem();

        List<ItemStack> drops = Block.getDrops(state, (ServerLevel) level,
                pos, level.getBlockEntity(pos), player, tool);

        List<ItemStack> finalDrops = new ArrayList<>();

        for (ItemStack drop : drops) {
            ItemStack processed = drop.copy();

            if (smelt) {
                Item item = processed.getItem();
                ItemStack cached = SMELTING_CACHE.get(item);

                if (cached == null) {
                    var recipes = ((ServerLevel) level).getRecipeManager()
                            .getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.SMELTING);
                    boolean found = false;
                    for (var recipe : recipes) {
                        if (recipe.getIngredients().get(0).test(processed)) {
                            ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
                            SMELTING_CACHE.put(item, result.copy());
                            result.setCount(result.getCount() * processed.getCount());
                            processed = result;
                            found = true;
                            break;
                        }
                    }
                    if (!found) SMELTING_CACHE.put(item, NO_SMELT);
                } else if (cached != NO_SMELT) {
                    ItemStack result = cached.copy();
                    result.setCount(result.getCount() * processed.getCount());
                    processed = result;
                }
            }
            finalDrops.add(processed);

            if (fortuneBonus > 0) {
                for (int i = 0; i < fortuneBonus; i++) {
                    if (player.getRandom().nextFloat() < 0.33f) {
                        finalDrops.add(processed.copy());
                    }
                }
            }
        }

        level.removeBlock(pos, false);
        for (ItemStack drop : finalDrops) {
            if (!drop.isEmpty()) Block.popResource(level, pos, drop);
        }
    }

    private void applyAutoTorch(Player player) {
        if (!EternalHeartConfig.AUTO_TORCH.get()) return;
        BlockPos feetPos = player.blockPosition();
        Level level = player.level();
        if (!player.onGround() || !level.isEmptyBlock(feetPos)) return;
        if (level.getBrightness(LightLayer.BLOCK, feetPos) > EternalHeartConfig.AUTO_TORCH_LIGHT_LEVEL.get()) return;
        if (!level.getBlockState(feetPos.below()).isSolid()) return;

        UUID uuid = player.getUUID();
        Integer cachedSlot = TORCH_SLOT_CACHE.get(uuid);

        if (cachedSlot != null && cachedSlot < player.getInventory().getContainerSize()) {
            ItemStack cachedStack = player.getInventory().getItem(cachedSlot);
            if (cachedStack.is(Items.TORCH)) {
                level.setBlock(feetPos, Blocks.TORCH.defaultBlockState(), 3);
                if (!player.isCreative()) {
                    cachedStack.shrink(1);
                    if (cachedStack.isEmpty()) TORCH_SLOT_CACHE.remove(uuid);
                }
                return;
            }
        }

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.TORCH)) {
                level.setBlock(feetPos, Blocks.TORCH.defaultBlockState(), 3);
                if (!player.isCreative()) {
                    stack.shrink(1);
                    if (!stack.isEmpty()) TORCH_SLOT_CACHE.put(uuid, i);
                    else TORCH_SLOT_CACHE.remove(uuid);
                } else {
                    TORCH_SLOT_CACHE.put(uuid, i);
                }
                return;
            }
        }
    }

    private void applyAutoDoor(Player player, long tick) {
        if (!EternalHeartConfig.AUTO_DOOR.get()) return;
        if (tick % 5 != 0) return;

        Level level = player.level();
        Vec3 playerPos = player.position();
        int r = 3;

        for (BlockPos p : BlockPos.betweenClosed(
                BlockPos.containing(playerPos.x - r, playerPos.y - 1, playerPos.z - r),
                BlockPos.containing(playerPos.x + r, playerPos.y + 2, playerPos.z + r))) {

            BlockState state = level.getBlockState(p);
            if (!(state.getBlock() instanceof DoorBlock door)) continue;
            if (state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;

            double dx = p.getX() + 0.5 - playerPos.x;
            double dy = p.getY() + 0.5 - playerPos.y;
            double dz = p.getZ() + 0.5 - playerPos.z;
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq <= 2.25) {
                if (!state.getValue(DoorBlock.OPEN)) {
                    door.setOpen(player, level, state, p, true);
                }
            } else if (distSq > 6.25) {
                BlockState fresh = level.getBlockState(p);
                if (fresh.getBlock() instanceof DoorBlock
                        && fresh.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                        && fresh.getValue(DoorBlock.OPEN)) {
                    door.setOpen(player, level, fresh, p, false);
                }
            }
        }
    }

    private void applyOreHighlight(Player player) {
        if (!EternalHeartConfig.ORE_HIGHLIGHT.get()) return;
        if (player.getY() > 64) return;

        double range = EternalHeartConfig.ORE_HIGHLIGHT_RANGE.get();
        if (range <= 0) return;
        AABB area = player.getBoundingBox().inflate(range);
        List<LivingEntity> entities = player.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e != player && e.isAlive() && !e.hasEffect(MobEffects.GLOWING));
        for (LivingEntity e : entities) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 400, 0, false, false));
        }
        List<ItemEntity> items = player.level().getEntitiesOfClass(ItemEntity.class, area);
        for (ItemEntity item : items) {
            item.setGlowingTag(true);
        }
    }

    private void applyAutoFish(Player player, long tick) {
        if (!EternalHeartConfig.AUTO_FISH.get()) return;
        int interval = EternalHeartConfig.AUTO_FISH_INTERVAL.get();
        if (tick % interval != 0) return;
        if (!player.getMainHandItem().is(Items.FISHING_ROD)) return;

        Level level = player.level();
        BlockPos pos = player.blockPosition();

        CompoundTag pd = player.getPersistentData();
        long lastCheck = pd.getLong(WATER_CHECK_KEY);
        BlockPos waterPos;
        if (tick - lastCheck <= 20 && pd.contains(WATER_POS_KEY)) {
            waterPos = BlockPos.of(pd.getLong(WATER_POS_KEY));
        } else {
            BlockPos found = null;
            for (int dx = -2; dx <= 2 && found == null; dx++)
                for (int dz = -2; dz <= 2 && found == null; dz++)
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos check = pos.offset(dx, dy, dz);
                        if (level.getFluidState(check).is(Fluids.WATER)) {
                            found = check;
                        }
                    }
            if (found == null) {
                pd.putLong(WATER_CHECK_KEY, tick);
                return;
            }
            waterPos = found;
            pd.putLong(WATER_CHECK_KEY, tick);
            pd.putLong(WATER_POS_KEY, waterPos.asLong());
        }

        if (level instanceof ServerLevel sl) {
            float rnd = player.getRandom().nextFloat();
            ResourceLocation tableKey = rnd < 0.85f ? FISH_TABLE :
                    rnd < 0.95f ? JUNK_TABLE : TREASURE_TABLE;

            LootTable table = sl.getServer().getLootData().getLootTable(tableKey);
            LootParams params = new LootParams.Builder(sl)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(waterPos))
                    .withParameter(LootContextParams.TOOL, player.getMainHandItem())
                    .withLuck(player.getLuck())
                    .create(LootContextParamSets.FISHING);

            List<ItemStack> loot = table.getRandomItems(params);
            for (ItemStack stack : loot) {
                if (!player.addItem(stack)) {
                    player.level().addFreshEntity(new ItemEntity(player.level(),
                            player.getX(), player.getY() + 1, player.getZ(), stack));
                }
            }
            player.getMainHandItem().hurtAndBreak(1, player, p -> {});
        }
    }

    private void applyAutoOpenChest(Player player, long tick) {
        if (!EternalHeartConfig.AUTO_OPEN_CHEST.get()) return;
        double range = EternalHeartConfig.AUTO_OPEN_CHEST_RANGE.get();
        if (range <= 0) return;
        if (tick % 40 != 0) return;

        Level level = player.level();
        BlockPos playerPos = player.blockPosition();
        int r = (int) range;

        for (BlockPos p : BlockPos.betweenClosed(
                playerPos.offset(-r, -r, -r), playerPos.offset(r, r, r))) {
            BlockEntity be = level.getBlockEntity(p);
            if (!(be instanceof Container container)) continue;
            if (container.isEmpty()) continue;

            int taken = 0;
            for (int i = 0; i < container.getContainerSize() && taken < 5; i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty()) {
                    ItemStack copy = stack.copy();
                    if (player.getInventory().add(copy)) {
                        container.setItem(i, ItemStack.EMPTY);
                        taken++;
                    }
                }
            }
        }
    }

    private void applyAutoRefill(Player player, long tick) {
        if (!EternalHeartConfig.AUTO_REFILL.get()) return;
        if (tick % 20 != 0) return;

        for (int hotbar = 0; hotbar < 9; hotbar++) {
            ItemStack hotbarStack = player.getInventory().getItem(hotbar);

            if (hotbarStack.isEmpty() || hotbarStack.getCount() <= 0) continue;
            if (hotbarStack.getMaxStackSize() <= 1) continue;
            if (hotbarStack.getCount() > Math.min(1, hotbarStack.getMaxStackSize() / 4)) continue;

            int mainEnd = Math.min(36, player.getInventory().getContainerSize());
            Item bestMatch = hotbarStack.getItem();

            for (int inv = 9; inv < mainEnd; inv++) {
                ItemStack invStack = player.getInventory().getItem(inv);
                if (invStack.isEmpty()) continue;
                if (invStack.getItem() == bestMatch) {
                    if (invStack.getCount() <= hotbarStack.getCount()) continue;

                    player.getInventory().setItem(hotbar, invStack.copy());
                    player.getInventory().setItem(inv, ItemStack.EMPTY);
                    break;
                }
            }
        }
    }

    private void applyInfiniteBucket(Player player) {
        if (!EternalHeartConfig.INFINITE_BUCKET.get()) return;

        boolean lavaEnabled = EternalHeartConfig.INFINITE_BUCKET_LAVA.get();

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.is(Items.BUCKET)) {
            player.getInventory().setItem(player.getInventory().selected,
                    new ItemStack(Items.WATER_BUCKET, 1));
        }

        ItemStack offHand = player.getOffhandItem();
        if (offHand.is(Items.BUCKET)) {
            player.getInventory().offhand.set(0, new ItemStack(
                    lavaEnabled ? Items.LAVA_BUCKET : Items.WATER_BUCKET, 1));
        }
    }
}
