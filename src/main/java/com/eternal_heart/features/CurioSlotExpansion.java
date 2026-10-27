/*
 * 永恒之心 - Curios 饰品槽位扩容（服务端生效逻辑）
 *
 * 用 Curios 官方的「永久槽位修饰符」接口（ICuriosItemHandler#addPermanentSlotModifier），
 * 只影响我们配置的槽位、只作用于玩家，随存档持久化（重启/重进不闪缩），
 * 不干扰其它模组给同一槽位加的格子（修饰符按 UUID 各算各的）。
 * 每个槽位用固定 UUID（由槽位 ID 哈希而来），重复添加即覆盖，天然幂等；
 * 配置里删掉某槽位后，对应修饰符会被移除并自动收回多出的格子。
 */
package com.eternal_heart.features;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

public final class CurioSlotExpansion {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long INTERVAL = 40L;
    private static final String MODIFIER_NAME = "eternal_heart_slot_expansion";
    /** 配置里允许填到 2147483647（游戏整数极限），但真实格子要占内存与界面：超过此上限按此上限生效，防止崩服 */
    private static final int HARD_CAP = 4096;
    private static volatile boolean pending = true;
    private static volatile long lastTick = -100L;
    private static volatile String lastSignature = "";
    private static volatile int lastPlayerCount = -1;

    private CurioSlotExpansion() {
    }

    public static void schedule() {
        pending = true;
        lastSignature = "";
        lastPlayerCount = -1;
    }

    public static void tick(MinecraftServer server) {
        long tick = server.getTickCount();
        if (!pending && tick - lastTick < INTERVAL) {
            return;
        }
        pending = false;
        lastTick = tick;
        List<? extends String> raw = ConfigValues.get(EternalHeartConfig.CURIOS_SLOT_EXPANSION);
        boolean enabled = (Boolean)ConfigValues.get(EternalHeartConfig.CURIOS_SLOT_EXPANSION_ENABLED);
        Map<String, Integer> target = enabled ? parse(raw) : Map.of();
        int players = server.getPlayerList().getPlayers().size();
        String signature = target.toString();
        if (signature.equals(lastSignature) && players == lastPlayerCount) {
            return;
        }
        lastSignature = signature;
        lastPlayerCount = players;
        boolean retry = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!apply(player, target)) {
                retry = true;
            }
        }
        if (retry) {
            pending = true;
        }
    }

    private static Map<String, Integer> parse(List<? extends String> raw) {
        LinkedHashMap<String, Integer> target = new LinkedHashMap<String, Integer>();
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) continue;
            int sep = entry.lastIndexOf('|');
            if (sep <= 0 || sep == entry.length() - 1) continue;
            String slot = entry.substring(0, sep).trim();
            try {
                long amount = Long.parseLong(entry.substring(sep + 1).trim());
                if (amount < 1L) continue;
                if (amount > (long)HARD_CAP) amount = HARD_CAP;
                target.put(slot, (int)amount);
            }
            catch (NumberFormatException e) {
                LOGGER.warn("[永恒之心] 槽位扩容配置项无法解析，已忽略：{}", entry);
            }
        }
        return target;
    }

    public static void apply(ServerPlayer player) {
        boolean enabled = (Boolean)ConfigValues.get(EternalHeartConfig.CURIOS_SLOT_EXPANSION_ENABLED);
        List<? extends String> raw = ConfigValues.get(EternalHeartConfig.CURIOS_SLOT_EXPANSION);
        apply(player, enabled ? parse(raw) : Map.<String, Integer>of());
    }

    private static boolean apply(ServerPlayer player, Map<String, Integer> target) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory((LivingEntity)player).resolve().orElse(null);
        if (handler == null) {
            return false;
        }
        for (String slot : handler.getCurios().keySet()) {
            if (target.containsKey(slot)) continue;
            handler.removeSlotModifier(slot, uuidFor(slot));
        }
        for (Map.Entry<String, Integer> entry : target.entrySet()) {
            String slot = entry.getKey();
            ISlotType type = CuriosApi.getSlot(slot).orElse(null);
            if (type == null) {
                continue;
            }
            handler.removeSlotModifier(slot, uuidFor(slot));
            int amount = entry.getValue() - type.getSize();
            if (amount == 0) {
                continue;
            }
            handler.addPermanentSlotModifier(slot, uuidFor(slot), MODIFIER_NAME, (double)amount, AttributeModifier.Operation.ADDITION);
        }
        return true;
    }

    private static UUID uuidFor(String slot) {
        return UUID.nameUUIDFromBytes(("eternal_heart:slot_expansion:" + slot).getBytes(StandardCharsets.UTF_8));
    }
}
