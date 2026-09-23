package com.eternal_heart.core;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.function.Predicate;

/**
 * 实体查询工具 —— 全项目「扫描附近实体」与「Boss 判定」的唯一入口。
 *
 * <p>统一了 AABB 构造、敌对生物谓词复用与排除逻辑，避免每个模块各自
 * {@code new AABB} + 重复分配 lambda；同时集中承担「查询范围」这一最容易写错的细节
 * （垂直与水平范围不一致时请使用 {@link #box(Entity, double, double, double)}）。</p>
 */
public final class Scan {

    /** 敌对生物谓词 —— 静态常量，零分配复用。 */
    public static final Predicate<LivingEntity> HOSTILE = entity -> entity instanceof Enemy;

    /** Forge 通用 Boss 标签。 */
    @SuppressWarnings("unchecked")
    public static final TagKey<EntityType<?>> BOSS_TAG =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("forge", "bosses"));

    /** 高血量兜底阈值：未打 Boss 标签但生命上限不低于该值的敌对生物视为 Boss。 */
    private static final float BOSS_HEALTH_THRESHOLD = 100f;

    private Scan() {
    }

    /** 目标是否应视作 Boss（显式标签，或高血量敌对生物）。 */
    public static boolean isBoss(LivingEntity entity) {
        return entity.getType().is(BOSS_TAG)
                || (entity instanceof Enemy && entity.getMaxHealth() >= BOSS_HEALTH_THRESHOLD);
    }

    /** 以实体为中心、各方向等距膨胀的查询盒。 */
    public static AABB box(Entity center, double range) {
        return center.getBoundingBox().inflate(range);
    }

    /** 以实体为中心、水平与垂直可分别指定的查询盒（用于收窄高度扫描范围）。 */
    public static AABB box(Entity center, double horizontal, double verticalDown, double verticalUp) {
        AABB bounds = center.getBoundingBox();
        return new AABB(
                bounds.minX - horizontal, bounds.minY - verticalDown, bounds.minZ - horizontal,
                bounds.maxX + horizontal, bounds.maxY + verticalUp, bounds.maxZ + horizontal);
    }

    /** 附近的敌对生物（Range 半径，包含全部方向）。 */
    public static List<LivingEntity> hostiles(Level level, Entity center, double range) {
        return level.getEntitiesOfClass(LivingEntity.class, box(center, range), HOSTILE);
    }

    /** 附近的敌对生物，附加自定义过滤条件。 */
    public static List<LivingEntity> hostiles(Level level, Entity center, double range,
                                              Predicate<? super LivingEntity> filter) {
        return level.getEntitiesOfClass(LivingEntity.class, box(center, range), HOSTILE.and(filter));
    }

    /** 附近指定类型的实体。 */
    public static <T extends Entity> List<T> of(Level level, Entity center, double range,
                                                Class<T> type, Predicate<? super T> filter) {
        return level.getEntitiesOfClass(type, box(center, range), filter);
    }

    /** 玩家是否在给定实体的范围内（和平光环等"查玩家"场景使用）。 */
    public static boolean anyPlayer(Level level, Entity center, double range, Predicate<Player> filter) {
        return !level.getEntitiesOfClass(Player.class, box(center, range), filter).isEmpty();
    }
}
