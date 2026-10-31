package com.eternal_heart;

import com.eternal_heart.integration.RevelationFixCompat;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

@Mod.EventBusSubscriber(modid = EternalHeartMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class EternalHeartConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ==================== 属性系统 ====================
    public static final ForgeConfigSpec.DoubleValue ATTACK_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue ATTACK_SPEED;
    public static final ForgeConfigSpec.DoubleValue MAX_HEALTH;
    public static final ForgeConfigSpec.DoubleValue ARMOR;
    public static final ForgeConfigSpec.DoubleValue ARMOR_TOUGHNESS;
    public static final ForgeConfigSpec.DoubleValue KNOCKBACK_RESIST;
    public static final ForgeConfigSpec.DoubleValue MOVEMENT_SPEED;
    public static final ForgeConfigSpec.DoubleValue LUCK;

    // ==================== 扩展属性 ====================
    public static final ForgeConfigSpec.DoubleValue SWIM_SPEED;
    public static final ForgeConfigSpec.DoubleValue FLYING_SPEED;
    public static final ForgeConfigSpec.DoubleValue ATTACK_KNOCKBACK;
    public static final ForgeConfigSpec.DoubleValue ENTITY_REACH;
    public static final ForgeConfigSpec.DoubleValue BLOCK_REACH;
    public static final ForgeConfigSpec.DoubleValue STEP_HEIGHT;
    public static final ForgeConfigSpec.DoubleValue NAMETAG_DISTANCE;

    /** 自定义属性：为任意已注册属性（含其它模组）添加修饰符，格式见配置注释。 */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXTRA_ATTRIBUTES;

    /** 生命上限突破：解除原版 1024 点生命上限（不再依赖 AttributeFix）。 */
    public static final ForgeConfigSpec.BooleanValue HEALTH_CAP_OVERRIDE;

    // ==================== 防御系统 ====================
    public static final ForgeConfigSpec.DoubleValue DAMAGE_REDUCTION;
    public static final ForgeConfigSpec.DoubleValue FALL_DAMAGE_RATIO;
    public static final ForgeConfigSpec.DoubleValue REFLECT_RATIO;
    public static final ForgeConfigSpec.BooleanValue FIRE_IMMUNE;
    public static final ForgeConfigSpec.BooleanValue DROWN_IMMUNE;
    public static final ForgeConfigSpec.BooleanValue DEBUFF_GUARD;
    public static final ForgeConfigSpec.IntValue DEBUFF_CLEAR_INTERVAL;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DEBUFF_BLACKLIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DEBUFF_WHITELIST;
    public static final ForgeConfigSpec.BooleanValue SUFFOCATE_IMMUNE;
    public static final ForgeConfigSpec.BooleanValue CACTUS_IMMUNE;
    /** 环境伤害免疫：浆果丛 / 石笋 / 细雪冻结 / 挤压 / 雷击 / 鞘翅撞墙。 */
    public static final ForgeConfigSpec.BooleanValue ENVIRONMENT_IMMUNE;
    public static final ForgeConfigSpec.DoubleValue EXPLOSION_RESIST;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_CAP_RATIO;
    public static final ForgeConfigSpec.DoubleValue PROJECTILE_RESIST;
    public static final ForgeConfigSpec.DoubleValue MAGIC_RESIST;

    // ==================== 生命回复 ====================
    public static final ForgeConfigSpec.DoubleValue TICK_REGEN;

    // ==================== 被动效果 ====================
    public static final ForgeConfigSpec.BooleanValue NIGHT_VISION;
    public static final ForgeConfigSpec.BooleanValue WATER_BREATHING;
    public static final ForgeConfigSpec.IntValue HASTE_LEVEL;
    public static final ForgeConfigSpec.BooleanValue FIRE_RESISTANCE_POTION;
    public static final ForgeConfigSpec.BooleanValue SATURATION;
    public static final ForgeConfigSpec.BooleanValue PERMANENT_LUCK;
    public static final ForgeConfigSpec.IntValue NIGHT_VISION_LEVEL;
    public static final ForgeConfigSpec.IntValue WATER_BREATHING_LEVEL;
    public static final ForgeConfigSpec.IntValue SATURATION_LEVEL;
    public static final ForgeConfigSpec.IntValue PERMANENT_LUCK_LEVEL;
    /** 被动效果无限时长：效果永不到期（原版 -1 时长语义），不再依赖周期性刷新。 */
    public static final ForgeConfigSpec.BooleanValue PASSIVE_EFFECT_INFINITE;
    /** 氧气锁定：佩戴时氧气值不下降。 */
    public static final ForgeConfigSpec.BooleanValue AIR_SUPPLY_LOCK;
    /** 饥饿锁定：佩戴时饥饿值与饱食度不会下降。 */
    public static final ForgeConfigSpec.BooleanValue HUNGER_LOCK;
    /** 附加被动效果：任意已注册药水效果（含其它模组），格式 "modid:effect_id|等级|时长tick"。 */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PASSIVE_EXTRA_EFFECTS;

    // ==================== 机动系统 ====================
    public static final ForgeConfigSpec.BooleanValue JUMP_BOOST;
    public static final ForgeConfigSpec.BooleanValue STEP_ASSIST;
    public static final ForgeConfigSpec.BooleanValue SLOW_FALL_GLIDE;
    public static final ForgeConfigSpec.BooleanValue DOLPHINS_GRACE;
    public static final ForgeConfigSpec.BooleanValue LAVA_WALKING;
    public static final ForgeConfigSpec.BooleanValue WATER_WALKING;
    public static final ForgeConfigSpec.IntValue JUMP_BOOST_LEVEL;
    public static final ForgeConfigSpec.IntValue DOLPHINS_GRACE_LEVEL;
    public static final ForgeConfigSpec.IntValue SLOW_FALL_GLIDE_LEVEL;
    // ---- 内置滑翔参数（未安装 Caelus 时使用）----
    public static final ForgeConfigSpec.DoubleValue GLIDE_START_FALL_SPEED;
    public static final ForgeConfigSpec.DoubleValue GLIDE_FALL_DAMPING;
    public static final ForgeConfigSpec.DoubleValue GLIDE_MAX_FALL_SPEED;
    public static final ForgeConfigSpec.DoubleValue GLIDE_ACCELERATION;
    public static final ForgeConfigSpec.DoubleValue GLIDE_MAX_HORIZONTAL_SPEED;

    // ==================== 战斗系统 ====================
    public static final ForgeConfigSpec.DoubleValue DODGE_CHANCE;
    public static final ForgeConfigSpec.DoubleValue REFLECT_PROJECTILE_CHANCE;
    public static final ForgeConfigSpec.DoubleValue LOW_HP_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue LOW_HP_DAMAGE_MULT;
    public static final ForgeConfigSpec.DoubleValue BOSS_DAMAGE_BONUS;
    public static final ForgeConfigSpec.DoubleValue EXECUTE_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue COUNTER_DAMAGE_RATIO;
    public static final ForgeConfigSpec.BooleanValue COUNTER_INVULN_RESET;
    public static final ForgeConfigSpec.DoubleValue AOE_SPLASH_RATIO;
    public static final ForgeConfigSpec.DoubleValue AOE_SPLASH_RANGE;
    public static final ForgeConfigSpec.DoubleValue EXTRA_HIT_DAMAGE;
    public static final ForgeConfigSpec.IntValue EXTRA_HIT_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue GLOW_DAMAGE_BONUS;
    public static final ForgeConfigSpec.BooleanValue BENEFICIAL_STRIP;
    public static final ForgeConfigSpec.BooleanValue INFINITE_ARROWS;
    public static final ForgeConfigSpec.DoubleValue IGNITE_CHANCE;
    public static final ForgeConfigSpec.IntValue IGNITE_DURATION;
    public static final ForgeConfigSpec.BooleanValue PIERCE_DAMAGE_CAP;
    /** 破限·锁血重试：补足被目标锁住时，最多在后续多少 tick 内继续尝试打进这笔伤害。 */
    public static final ForgeConfigSpec.IntValue PIERCE_RETRY_TICKS;
    /** 溅射不再要求目标与被击目标同种生物（范围内敌对生物全部命中）。 */
    public static final ForgeConfigSpec.BooleanValue AOE_SPLASH_ALL_TYPES;
    public static final ForgeConfigSpec.DoubleValue KILL_CHAIN_RATIO;
    /** 处决增伤：目标生命比例低于阈值后，按缺失生命线性放大伤害。 */
    public static final ForgeConfigSpec.DoubleValue EXECUTE_DAMAGE_BONUS;
    public static final ForgeConfigSpec.DoubleValue EXECUTE_DAMAGE_THRESHOLD;
    /** 溢出转移：单次伤害超过目标剩余生命时，把溢出部分打向附近敌人。 */
    public static final ForgeConfigSpec.DoubleValue OVERFLOW_RATIO;
    public static final ForgeConfigSpec.IntValue OVERFLOW_MAX_TARGETS;
    /** 级联伤害深度上限（0 = 次级伤害不再产生新的次级伤害）。 */
    public static final ForgeConfigSpec.IntValue CASCADE_DEPTH_LIMIT;
    /** 无视无敌帧：我方造成的伤害不再被目标的受击无敌帧削减。 */
    public static final ForgeConfigSpec.BooleanValue IGNORE_INVULNERABLE_TIME;

    // ==================== 攻击压制 ====================
    /** 攻击压制总开关：命中后被攻击者无法回血 / 增益被清除。 */
    public static final ForgeConfigSpec.BooleanValue SUPPRESSION_ENABLED;
    /** 被攻击者无法回血（禁疗）。 */
    public static final ForgeConfigSpec.BooleanValue SUPPRESSION_ANTI_HEAL;
    /** 禁疗持续时间（秒，每次命中刷新）。 */
    public static final ForgeConfigSpec.IntValue SUPPRESSION_DURATION;
    /** 命中时清除被攻击者的有益效果。 */
    public static final ForgeConfigSpec.BooleanValue SUPPRESSION_STRIP_BENEFITS;
    /** 是否同样压制被攻击的玩家（PvP）。 */
    public static final ForgeConfigSpec.BooleanValue SUPPRESSION_AFFECT_PLAYERS;
    /** 压制期间清零并禁止获得吸收值（金苹果的黄心挡不住禁疗）。 */
    public static final ForgeConfigSpec.BooleanValue SUPPRESSION_STRIP_ABSORPTION;
    /** 压制期间禁止获得治疗类效果（再生 / 吸收 / 生命提升 / 瞬间治疗）。 */
    public static final ForgeConfigSpec.BooleanValue SUPPRESSION_STRIP_HEALING_EFFECTS;

    // ==================== 护盾系统 ====================
    public static final ForgeConfigSpec.DoubleValue SHIELD_PER_SEC;
    public static final ForgeConfigSpec.DoubleValue SHIELD_MAX;
    public static final ForgeConfigSpec.DoubleValue SHIELD_BREAK_KNOCKBACK;

    // ==================== 速度爆发 ====================
    public static final ForgeConfigSpec.IntValue SPEED_BURST_DURATION;
    public static final ForgeConfigSpec.IntValue SPEED_BURST_LEVEL;

    // ==================== 冷却缩减 ====================
    public static final ForgeConfigSpec.DoubleValue COOLDOWN_RATIO;

    // ==================== 自定义配方 ====================
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CUSTOM_RECIPES;

    /** 自定义仪式配方：Goety（诡厄巫法）暗黑祭坛仪式，格式见配置注释。 */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CUSTOM_RITUALS;

    // ==================== 时停 ====================
    public static final ForgeConfigSpec.BooleanValue TIME_STOP;
    public static final ForgeConfigSpec.IntValue TIME_STOP_DURATION;
    public static final ForgeConfigSpec.IntValue TIME_STOP_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue TIME_STOP_RADIUS;
    public static final ForgeConfigSpec.BooleanValue TIME_STOP_AFFECT_PLAYERS;
    /** 时停冻结方块实体（熔炉 / 漏斗 / 刷怪笼等机器逻辑一并停止）。 */
    public static final ForgeConfigSpec.BooleanValue TIME_STOP_FREEZE_BLOCK_ENTITIES;

    // ==================== 战斗生存增强 ====================
    public static final ForgeConfigSpec.BooleanValue KNOCKBACK_IMMUNITY;
    public static final ForgeConfigSpec.BooleanValue AUTO_POTION;
    public static final ForgeConfigSpec.DoubleValue AUTO_POTION_THRESHOLD;
    public static final ForgeConfigSpec.BooleanValue LIGHTNING_REFLECT;
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_REFLECT_CHANCE;
    /** 反射闪电对攻击者造成的伤害（0 = 纯视觉）。 */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_REFLECT_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue KILL_CHAIN;
    public static final ForgeConfigSpec.DoubleValue KILL_CHAIN_RANGE;

    // ==================== 伤害光环 ====================
    public static final ForgeConfigSpec.BooleanValue DAMAGE_AURA_ENABLED;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_AURA_RANGE;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_AURA_DAMAGE;
    public static final ForgeConfigSpec.IntValue DAMAGE_AURA_TICK_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_AURA_BOSS_MULT;
    /** 光环伤害是否走完整战斗管线（增伤 / 斩杀 / 处决 / 溢出 / 限伤穿透）。 */
    public static final ForgeConfigSpec.BooleanValue AURA_USE_COMBAT_PIPELINE;

    // ==================== 采集强化 ====================
    public static final ForgeConfigSpec.BooleanValue AUTO_SMELT;
    public static final ForgeConfigSpec.BooleanValue INSTANT_MINE;
    public static final ForgeConfigSpec.BooleanValue INSTANT_MINE_UNBREAKABLE;
    public static final ForgeConfigSpec.BooleanValue VEIN_MINER;
    public static final ForgeConfigSpec.IntValue VEIN_MAX_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue VEIN_USE_TAGS;
    /** 连锁挖掘按矿脉同族标签连通（铁矿 ↔ 深板岩铁矿、模组同族矿石连成一片）。 */
    public static final ForgeConfigSpec.BooleanValue VEIN_MATCH_TAGS;
    public static final ForgeConfigSpec.IntValue FORTUNE_BONUS;
    public static final ForgeConfigSpec.IntValue LOOTING_BONUS;

    // ==================== 饰品融合 ====================
    public static final ForgeConfigSpec.BooleanValue FUSION_ENABLED;
    public static final ForgeConfigSpec.IntValue FUSION_LIMIT;

    // ==================== 生存保障 ====================
    public static final ForgeConfigSpec.BooleanValue VOID_RESCUE;
    public static final ForgeConfigSpec.BooleanValue UNDYING_TOTEM;
    public static final ForgeConfigSpec.IntValue UNDYING_COOLDOWN;
    public static final ForgeConfigSpec.BooleanValue KILL_EXPLOSION;
    public static final ForgeConfigSpec.DoubleValue KILL_EXPLOSION_POWER;
    public static final ForgeConfigSpec.BooleanValue SOUL_BIND;
    public static final ForgeConfigSpec.BooleanValue ENDER_CHEST_REMOTE;
    public static final ForgeConfigSpec.BooleanValue AUTO_EXTINGUISH;
    /** 秒吃食物：佩戴时进食几乎瞬间完成（使用时长压缩到 1 tick）。 */
    public static final ForgeConfigSpec.BooleanValue INSTANT_EAT;
    /** 耐久补满：周期性把背包（含盔甲 / 副手）中已损耗的耐久补回满值。 */
    public static final ForgeConfigSpec.BooleanValue DURABILITY_REFILL;
    public static final ForgeConfigSpec.IntValue DURABILITY_REFILL_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue KILL_HEAL_RATIO;
    /** 工具不掉耐久：佩戴时手持物品的耐久消耗被完全跳过。 */
    public static final ForgeConfigSpec.BooleanValue ITEM_UNBREAKABLE;

    // ==================== 环境适应 ====================
    public static final ForgeConfigSpec.BooleanValue MAGNET_ENABLED;
    public static final ForgeConfigSpec.DoubleValue MAGNET_RANGE;
    public static final ForgeConfigSpec.DoubleValue MAGNET_INSTANT_RANGE;
    /** 磁铁同时吸附经验球。 */
    public static final ForgeConfigSpec.BooleanValue MAGNET_PULL_XP;
    public static final ForgeConfigSpec.BooleanValue GLOW_ENABLED;
    public static final ForgeConfigSpec.DoubleValue GLOW_RANGE;

    // ==================== 光环系统 ====================
    public static final ForgeConfigSpec.BooleanValue WITHER_AURA_ENABLED;
    public static final ForgeConfigSpec.BooleanValue PEACE_AURA_ENABLED;
    public static final ForgeConfigSpec.BooleanValue GROWTH_AURA_ENABLED;
    public static final ForgeConfigSpec.DoubleValue WITHER_AURA_RANGE;
    public static final ForgeConfigSpec.DoubleValue PEACE_AURA_RANGE;
    public static final ForgeConfigSpec.DoubleValue GROWTH_AURA_RANGE;
    public static final ForgeConfigSpec.DoubleValue GROWTH_AURA_CHANCE;
    public static final ForgeConfigSpec.IntValue GROWTH_AURA_MAX_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue GROWTH_AUTO_REPLANT;
    /** 凋零光环持续时间 (tick)。 */
    public static final ForgeConfigSpec.IntValue WITHER_AURA_DURATION;
    /** 凋零光环等级 (0 = I 级)。 */
    public static final ForgeConfigSpec.IntValue WITHER_AURA_LEVEL;
    /** 凋零光环/伤害光环合并触发间隔 (tick)。 */
    public static final ForgeConfigSpec.IntValue WITHER_AURA_INTERVAL;
    /** 生长光环垂直扫描范围 (±格)。 */
    public static final ForgeConfigSpec.IntValue GROWTH_VERTICAL;
    /** 和平光环周期清理：直接把范围内的敌对生物移除（不只拦截新加入）。 */
    public static final ForgeConfigSpec.BooleanValue PEACE_AURA_CLEAN;
    public static final ForgeConfigSpec.IntValue PEACE_AURA_CLEAN_INTERVAL;

    // ==================== 永怒系统 ====================
    public static final ForgeConfigSpec.IntValue FURY_MAX_STACKS;
    public static final ForgeConfigSpec.IntValue FURY_DECAY_TICKS;
    public static final ForgeConfigSpec.DoubleValue FURY_DAMAGE_PER_STACK;
    public static final ForgeConfigSpec.DoubleValue FURY_LIFESTEAL;
    public static final ForgeConfigSpec.IntValue KILL_FURY_BONUS;
    public static final ForgeConfigSpec.DoubleValue LIFESTEAL_FOOD_RESTORE;
    public static final ForgeConfigSpec.DoubleValue LIFESTEAL_SATURATION_RESTORE;
    /** 非原版暴击的补判概率 (50 = 50%)。 */
    public static final ForgeConfigSpec.DoubleValue FURY_CRIT_CHANCE;
    /** 暴击额外伤害加成 (10 = +10%)。 */
    public static final ForgeConfigSpec.DoubleValue FURY_CRIT_DAMAGE_BONUS;
    /** 强制暴击的基础倍率 (1.5 = 原版暴击倍率)。 */
    public static final ForgeConfigSpec.DoubleValue FURY_CRIT_MULTIPLIER;

    // ==================== 经验 ====================
    public static final ForgeConfigSpec.DoubleValue XP_MULTIPLIER;

    // ==================== 探索便利 ====================
    public static final ForgeConfigSpec.BooleanValue AUTO_TORCH;
    public static final ForgeConfigSpec.IntValue AUTO_TORCH_LIGHT_LEVEL;
    public static final ForgeConfigSpec.BooleanValue AUTO_DOOR;
    public static final ForgeConfigSpec.BooleanValue ORE_HIGHLIGHT;
    public static final ForgeConfigSpec.DoubleValue ORE_HIGHLIGHT_RANGE;
    public static final ForgeConfigSpec.BooleanValue AUTO_FISH;
    public static final ForgeConfigSpec.IntValue AUTO_FISH_INTERVAL;

    // ==================== 机动便利 ====================
    public static final ForgeConfigSpec.BooleanValue AUTO_REFILL;
    public static final ForgeConfigSpec.BooleanValue INFINITE_BUCKET;
    public static final ForgeConfigSpec.BooleanValue INFINITE_BUCKET_LAVA;

    // ==================== 防护机制 (Aegis) ====================
    public static final ForgeConfigSpec.BooleanValue HEALTH_GUARD;
    public static final ForgeConfigSpec.BooleanValue HEALTH_GUARD_HARD;
    public static final ForgeConfigSpec.BooleanValue MAX_HEALTH_GUARD;
    public static final ForgeConfigSpec.BooleanValue LETHAL_GUARD;
    public static final ForgeConfigSpec.BooleanValue DEATH_GUARD;
    public static final ForgeConfigSpec.DoubleValue LETHAL_GUARD_RATIO;
    public static final ForgeConfigSpec.IntValue LETHAL_GUARD_COOLDOWN;
    /** 濒死无敌窗口（秒）：致死保护 / 死亡兜底触发后的短时间内完全免疫伤害。 */
    public static final ForgeConfigSpec.IntValue LETHAL_GUARD_IMMUNE;
    /** 强制击杀兜底：让 /kill 这类「无视无敌」的伤害同样受致死保护约束。 */
    public static final ForgeConfigSpec.BooleanValue KILL_GUARD;
    /** 虚空拦截：掉出世界时取消虚空伤害并传送回最后一次安全落点。 */
    public static final ForgeConfigSpec.BooleanValue VOID_GUARD;
    public static final ForgeConfigSpec.BooleanValue KEEP_INVENTORY;
    public static final ForgeConfigSpec.BooleanValue KEEP_EXPERIENCE;
    public static final ForgeConfigSpec.BooleanValue ITEM_GUARD;
    public static final ForgeConfigSpec.IntValue ITEM_GUARD_TICKS;
    public static final ForgeConfigSpec.BooleanValue CURIO_KEEP_ON_DEATH;
    public static final ForgeConfigSpec.BooleanValue CURIO_UNBIND;
    public static final ForgeConfigSpec.BooleanValue CURIO_SEIZE_GUARD;

    // ==================== 模组集成 ====================
    public static final ForgeConfigSpec.BooleanValue USE_FTB_ULTIMINE;
    public static final ForgeConfigSpec.EnumValue<FlightMode> FLIGHT_MODE;
    public static final ForgeConfigSpec.BooleanValue REVELATIONFIX_WHITELIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> REVELATIONFIX_EXTRA_ITEMS;
    public static final ForgeConfigSpec.BooleanValue REVELATIONFIX_AUTO_DETECT;
    public static final ForgeConfigSpec.BooleanValue REVELATIONFIX_GUARD_EQUIPMENT;
    public static final ForgeConfigSpec.BooleanValue REVELATIONFIX_GUARD_INVENTORY;
    public static final ForgeConfigSpec.BooleanValue CURIOS_SLOT_EXPANSION_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CURIOS_SLOT_EXPANSION;

    // ==================== 渲染 ====================

    // ==================== 头顶光环 ====================
    /** 头顶光环：佩戴「永恒之心」时在玩家头顶绘制一圈悬浮光环（纯客户端表现，不影响数值）。 */
    public static final ForgeConfigSpec.BooleanValue HALO_ENABLED;
    public static final ForgeConfigSpec.DoubleValue HALO_RADIUS;
    public static final ForgeConfigSpec.DoubleValue HALO_HEIGHT;
    public static final ForgeConfigSpec.DoubleValue HALO_THICKNESS;
    public static final ForgeConfigSpec.IntValue HALO_COLOR;
    public static final ForgeConfigSpec.DoubleValue HALO_SPIN_SPEED;
    /** 彩虹光环：颜色沿环周向铺成彩虹并随时间流动（开启后「光环颜色」被忽略）。 */
    public static final ForgeConfigSpec.BooleanValue HALO_RAINBOW;
    /** 彩虹流动速度（圈/秒，0 = 只保留静态彩虹不流动）。 */
    public static final ForgeConfigSpec.DoubleValue HALO_RAINBOW_SPEED;
    /** 彩虹色带条数：整数圈。0 = 整环同色、只随时间变色；1 = 一周铺满一整圈色相。 */
    public static final ForgeConfigSpec.IntValue HALO_RAINBOW_BANDS;

    // ==================== 环绕光球 ====================
    /** 光球模式：把头顶圆环换成环绕玩家中点的若干光球。 */
    public static final ForgeConfigSpec.BooleanValue HALO_ORBS;
    public static final ForgeConfigSpec.IntValue HALO_ORB_COUNT;
    /** 光球轨道半径：每个球到玩家中点（轨道球心）的水平距离，与圆环样式的「光环半径」互相独立。 */
    public static final ForgeConfigSpec.DoubleValue HALO_ORB_RADIUS;
    public static final ForgeConfigSpec.DoubleValue HALO_ORB_SIZE;
    public static final ForgeConfigSpec.DoubleValue HALO_ORB_HEIGHT;
    public static final ForgeConfigSpec.DoubleValue HALO_ORB_TILT;
    public static final ForgeConfigSpec.IntValue HALO_ORB_TRAIL;

    /** 炫彩提示框：名字与全部说明文字按彩虹色逐字渐变并流动。 */
    public static final ForgeConfigSpec.BooleanValue RAINBOW_NAME;
    /** 炫彩文字的色相流动速度（圈/秒）。 */
    public static final ForgeConfigSpec.DoubleValue RAINBOW_NAME_SPEED;

    public static final ForgeConfigSpec SPEC;

    /** 配置加载后保存的 ModConfig 引用，供配置面板持久化（保存回 toml）使用 */
    public static net.minecraftforge.fml.config.ModConfig CONFIG;

    public enum FlightMode {
        CREATIVE,
        ELYTRA,
        OFF
    }

    static {
        BUILDER.push("属性系统 (Attributes)");
        ATTACK_DAMAGE = BUILDER.comment("攻击伤害 Total 乘数 (1.0=不变, 2.5=×2.5 即 +150%, 3.5=×3.5 即 +250%)","上限已放开：可填任意大数值").defineInRange("attackDamage", 2.5, -Double.MAX_VALUE, Double.MAX_VALUE);
        ATTACK_SPEED = BUILDER.comment("攻击速度 Total 乘数 (1.0=不变, 2.5=×2.5 即 +150%)").defineInRange("attackSpeed", 2.5, -Double.MAX_VALUE, Double.MAX_VALUE);
        MAX_HEALTH = BUILDER.comment("最大生命值增加量 (加法，原版上限 1024，安装 AttributeFix 后无上限)").defineInRange("maxHealth", 1004.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        ARMOR = BUILDER.comment("护甲值增加量").defineInRange("armor", 12.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        ARMOR_TOUGHNESS = BUILDER.comment("护甲韧性增加量").defineInRange("armorToughness", 8.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        KNOCKBACK_RESIST = BUILDER.comment("击退抗性 (100 = 免疫击退，可超过 100)").defineInRange("knockbackResist", 1.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        MOVEMENT_SPEED = BUILDER.comment("移动速度最终增量比例 (50 = +50%，即总倍率 1.5)").defineInRange("movementSpeed", 0.5, -Double.MAX_VALUE, Double.MAX_VALUE);
        LUCK = BUILDER.comment("幸运值增加量").defineInRange("luck", 5.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("扩展属性 (Extended Attributes)");
        SWIM_SPEED = BUILDER.comment("游泳速度总倍率 (1.0=不变, 1.5=×1.5；影响水中游速)").defineInRange("swimSpeed", 1.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        FLYING_SPEED = BUILDER.comment("飞行速度总倍率 (1.0=不变；影响创造飞行与鞘翅滑翔)").defineInRange("flyingSpeed", 1.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        ATTACK_KNOCKBACK = BUILDER.comment("攻击击退加成 (附加，0=不变；命中时把目标击退得更远)").defineInRange("attackKnockback", 0.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        ENTITY_REACH = BUILDER.comment("实体交互距离加成 (格，附加；影响攻击与对实体交互的距离)").defineInRange("entityReach", 0.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        BLOCK_REACH = BUILDER.comment("方块交互距离加成 (格，附加；影响挖掘与放置距离)").defineInRange("blockReach", 0.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        STEP_HEIGHT = BUILDER.comment("台阶高度加成 (格，附加；与「自动上坡」功能独立)").defineInRange("stepHeight", 0.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        NAMETAG_DISTANCE = BUILDER.comment("名牌可见距离加成 (格，附加)").defineInRange("nametagDistance", 0.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        EXTRA_ATTRIBUTES = BUILDER.comment(
                        "自定义属性 - 为任意已注册属性（含其它模组）添加修饰符",
                        "格式: \"<属性ID> <数值> [操作类型]\"（空格分隔）",
                        "操作类型: addition(默认) / multiply_base / multiply_total",
                        "示例: \"pehkui:base_scale 0.25 multiply_total\"、\"apotheosis:crit_damage 15 addition\"",
                        "面板内点「编辑」可视化选择；未注册的属性会在加载时被忽略")
                // 元素校验放宽到「任意字符串」：非法条目由解析层忽略，
                // 避免手改 toml 写错一个字符就让整份配置加载失败
                .defineList("extraAttributes", () -> List.of(), o -> o instanceof String);
        HEALTH_CAP_OVERRIDE = BUILDER.comment(
                        "生命上限突破 - 解除原版 1024 点生命上限（原版 MAX_HEALTH 属性被硬夹在 1024，",
                        "超出部分会被丢弃；开启后不再需要 AttributeFix 也能把最大生命堆到任意值）")
                .define("healthCapOverride", true);
        BUILDER.pop();

        BUILDER.push("防御系统 (Defense)");
        DAMAGE_REDUCTION = BUILDER.comment("伤害减免比例 (50 = 50%，可超过 100；伤害不会因此变成治疗)").defineInRange("damageReduction", 0.5, -Double.MAX_VALUE, Double.MAX_VALUE);
        FALL_DAMAGE_RATIO = BUILDER.comment("摔落伤害保留比例 (0 = 完全免疫)").defineInRange("fallDamageRatio", 0.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        REFLECT_RATIO = BUILDER.comment("近战反伤比例 (20 = 20%)").defineInRange("reflectRatio", 0.2, -Double.MAX_VALUE, Double.MAX_VALUE);
        FIRE_IMMUNE = BUILDER.comment("是否免疫火焰伤害").define("fireImmune", true);
        DROWN_IMMUNE = BUILDER.comment("是否免疫溺水伤害").define("drownImmune", true);
        DEBUFF_GUARD = BUILDER.comment("负面效果拦截 - 阻止负面药水效果施加，并按间隔清除已有负面效果（黑/白名单在此生效）").define("debuffGuard", true);
        DEBUFF_CLEAR_INTERVAL = BUILDER.comment("清除负面效果间隔 (tick；0 或负 = 只拦截新施加的效果，不做周期清扫)").defineInRange("debuffClearInterval", 60, Integer.MIN_VALUE, Integer.MAX_VALUE);
        DEBUFF_BLACKLIST = BUILDER.comment("效果强制黑名单 - 即使标记为有益也会被清除","格式: [\"modid:effect_id\"]").defineList("debuffBlacklist", () -> List.of(), o -> o instanceof String s && s.contains(":"));
        DEBUFF_WHITELIST = BUILDER.comment("效果保护白名单 - 永远不被清除","格式: [\"modid:effect_id\"]").defineList("debuffWhitelist", () -> List.of(), o -> o instanceof String s && s.contains(":"));
        SUFFOCATE_IMMUNE = BUILDER.comment("是否免疫窒息伤害").define("suffocateImmune", true);
        CACTUS_IMMUNE = BUILDER.comment("是否免疫仙人掌伤害").define("cactusImmune", true);
        ENVIRONMENT_IMMUNE = BUILDER.comment(
                        "环境伤害免疫 - 免疫浆果丛 / 石笋 / 细雪冻结 / 挤压 / 雷击 / 鞘翅撞墙这类「没有攻击者」的伤害",
                        "不含火焰类（由「免疫火焰」单管）与虚空（由「虚空拦截」单管）")
                .define("environmentImmune", true);
        EXPLOSION_RESIST = BUILDER.comment("额外爆炸伤害减免 (80 = 80%，叠加到基础减伤)").defineInRange("explosionResist", 0.8, -Double.MAX_VALUE, Double.MAX_VALUE);
        DAMAGE_CAP_RATIO = BUILDER.comment("单次受伤上限 (30 = 不超过最大生命30%，0 = 关闭)").defineInRange("damageCapRatio", 0.3, -Double.MAX_VALUE, Double.MAX_VALUE);
        PROJECTILE_RESIST = BUILDER.comment("弹射物伤害额外减免 (50 = 50%，叠加到基础减伤)").defineInRange("projectileResist", 0.5, -Double.MAX_VALUE, Double.MAX_VALUE);
        MAGIC_RESIST = BUILDER.comment("魔法伤害额外减免 (30 = 30%，叠加到基础减伤)").defineInRange("magicResist", 0.3, -Double.MAX_VALUE, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("生命回复 (Regeneration)");
        TICK_REGEN = BUILDER.comment("每 tick 回复量 (2.0 = 40 HP/s)").defineInRange("tickRegen", 2.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("被动药水效果 (Passive Potion Effects)");
        NIGHT_VISION = BUILDER.comment("永久夜视").define("nightVision", true);
        WATER_BREATHING = BUILDER.comment("永久水下呼吸").define("waterBreathing", true);
        HASTE_LEVEL = BUILDER.comment("急迫等级 (0=关闭, 1=速度+20%, 2=+40%)").defineInRange("hasteLevel", 1, Integer.MIN_VALUE, Integer.MAX_VALUE);
        FIRE_RESISTANCE_POTION = BUILDER.comment("永久抗火药水效果").define("fireResistancePotion", true);
        SATURATION = BUILDER.comment("永久饱和 (不消耗饥饿值)").define("saturation", true);
        PERMANENT_LUCK = BUILDER.comment("永久幸运药水效果").define("permanentLuck", true);
        NIGHT_VISION_LEVEL = BUILDER.comment("夜视等级 (0 = I 级, 1 = II 级…)").defineInRange("nightVisionLevel", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
        WATER_BREATHING_LEVEL = BUILDER.comment("水下呼吸等级 (0 = I 级, 1 = II 级…)").defineInRange("waterBreathingLevel", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
        SATURATION_LEVEL = BUILDER.comment("饱和等级 (0 = I 级, 1 = II 级…)").defineInRange("saturationLevel", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
        PERMANENT_LUCK_LEVEL = BUILDER.comment("幸运等级 (0 = I 级, 1 = II 级…)").defineInRange("permanentLuckLevel", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
        PASSIVE_EFFECT_INFINITE = BUILDER.comment(
                        "被动效果无限时长 - 我方维持的被动药水效果使用原版无限时长（永不到期），",
                        "不再依赖周期性刷新，也不会被「效果时长削减」类机制中断；关闭则回到固定时长 + 自动刷新")
                .define("passiveEffectInfinite", true);
        AIR_SUPPLY_LOCK = BUILDER.comment("氧气锁定 - 佩戴时氧气值不会下降（等同永久水下呼吸，但不受效果清除影响）").define("airSupplyLock", true);
        HUNGER_LOCK = BUILDER.comment("饥饿锁定 - 佩戴时饥饿值与饱食度不再下降（不管多高强度的消耗都保持满值）").define("hungerLock", true);
        PASSIVE_EXTRA_EFFECTS = BUILDER.comment(
                        "附加被动效果 - 从全部已注册药水效果里自由选择（含其它模组的效果）",
                        "格式: \"modid:effect_id|等级|时长tick\"（等级从 1 开始 = I 级；时长 -1 = 永久）",
                        "省略后两段时默认 I 级、永久。示例: \"minecraft:strength|2|600\" = 力量 II、30 秒到期自动续",
                        "面板内点「编辑」可视化选择；未注册的效果 ID 会在加载时被忽略",
                        "注意：被负面效果拦截（HARMFUL 分类 / 黑名单）的效果不会生效，需先加入白名单")
                .defineList("passiveExtraEffects", () -> List.of(),
                        o -> o instanceof String s && !s.isBlank() && s.length() <= 128);
        BUILDER.pop();

        BUILDER.push("机动系统 (Mobility)");
        JUMP_BOOST = BUILDER.comment("跳跃增强 (+100%)").define("jumpBoost", true);
        STEP_ASSIST = BUILDER.comment("自动上坡 - 直接走上一格高方块").define("stepAssist", true);
        SLOW_FALL_GLIDE = BUILDER.comment("缓降滑翔 - 空中潜行时缓慢降落并可前进").define("slowFallGlide", true);
        DOLPHINS_GRACE = BUILDER.comment("海豚的恩惠 - 水中加速游泳").define("dolphinsGrace", true);
        LAVA_WALKING = BUILDER.comment("熔岩行走").define("lavaWalking", true);
        WATER_WALKING = BUILDER.comment("水上行走 - 站在水面，潜行允许下潜").define("waterWalking", true);
        JUMP_BOOST_LEVEL = BUILDER.comment("跳跃提升等级 (0 = 跳跃提升 I，即 +100%；1 = II…)").defineInRange("jumpBoostLevel", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
        DOLPHINS_GRACE_LEVEL = BUILDER.comment("海豚的恩惠等级 (0 = I 级…)").defineInRange("dolphinsGraceLevel", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
        SLOW_FALL_GLIDE_LEVEL = BUILDER.comment("缓降滑翔的效果等级 (0 = 缓降 I…)").defineInRange("slowFallGlideLevel", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
        GLIDE_START_FALL_SPEED = BUILDER.comment("内置滑翔·起滑阈值 - 下落速度低于此值（自由落体）时自动进入滑翔 (格/tick，默认 -0.6)")
                .defineInRange("glideStartFallSpeed", -0.6, -Double.MAX_VALUE, Double.MAX_VALUE);
        GLIDE_FALL_DAMPING = BUILDER.comment("内置滑翔·下落阻尼 - 每 tick 下落速度乘以该系数，越小平滑得越快 (默认 0.7)")
                .defineInRange("glideFallDamping", 0.7, -Double.MAX_VALUE, Double.MAX_VALUE);
        GLIDE_MAX_FALL_SPEED = BUILDER.comment("内置滑翔·最大下落速度 (格/tick，默认 -0.22；填 0 可悬浮不落)")
                .defineInRange("glideMaxFallSpeed", -0.22, -Double.MAX_VALUE, Double.MAX_VALUE);
        GLIDE_ACCELERATION = BUILDER.comment("内置滑翔·沿视角加速度 (格/tick²，默认 0.06)")
                .defineInRange("glideAcceleration", 0.06, -Double.MAX_VALUE, Double.MAX_VALUE);
        GLIDE_MAX_HORIZONTAL_SPEED = BUILDER.comment("内置滑翔·水平速度上限 (格/tick，默认 1.1；≤0 = 不限制，可无限加速)")
                .defineInRange("glideMaxHorizontalSpeed", 1.1, -Double.MAX_VALUE, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("战斗系统 (Combat)");
        DODGE_CHANCE = BUILDER.comment("闪避概率 (20 = 20%，0 = 关闭；≥100 为必定闪避)").defineInRange("dodgeChance", 0.2, -Double.MAX_VALUE, Double.MAX_VALUE);
        REFLECT_PROJECTILE_CHANCE = BUILDER.comment("弹射物反射概率 (30 = 30%)").defineInRange("reflectProjectileChance", 0.3, -Double.MAX_VALUE, Double.MAX_VALUE);
        LOW_HP_THRESHOLD = BUILDER.comment("低血量狂暴阈值 (30 = 30%)").defineInRange("lowHpThreshold", 0.3, -Double.MAX_VALUE, Double.MAX_VALUE);
        LOW_HP_DAMAGE_MULT = BUILDER.comment("低血量时伤害倍率 (2.0 = +100%)").defineInRange("lowHpDamageMult", 2.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        BOSS_DAMAGE_BONUS = BUILDER.comment("对 Boss 生物额外伤害倍率").defineInRange("bossDamageBonus", 0.5, -Double.MAX_VALUE, Double.MAX_VALUE);
        EXECUTE_THRESHOLD = BUILDER.comment("斩杀阈值 - 敌人血量低于此比例直接秒杀 (10 = 10%，0 = 关闭)").defineInRange("executeThreshold", 0.1, -Double.MAX_VALUE, Double.MAX_VALUE);
        COUNTER_DAMAGE_RATIO = BUILDER.comment("反伤比例 - 受伤时将伤害以此比例反弹 (25 = 25%)").defineInRange("counterDamageRatio", 0.25, -Double.MAX_VALUE, Double.MAX_VALUE);
        COUNTER_INVULN_RESET = BUILDER.comment("反伤时重置攻击者无敌帧 (使反伤立即生效)").define("counterInvulnReset", true);
        AOE_SPLASH_RATIO = BUILDER.comment("攻击命中时的AOE溅射伤害比例 (30 = 30%主伤害)").defineInRange("aoeSplashRatio", 0.3, -Double.MAX_VALUE, Double.MAX_VALUE);
        AOE_SPLASH_RANGE = BUILDER.comment("AOE溅射范围 (格，0=关闭；上限 512 为引擎安全值)").defineInRange("aoeSplashRange", 4.0, -Double.MAX_VALUE, 512.0);
        EXTRA_HIT_DAMAGE = BUILDER.comment("每击额外附加固定伤害 (0=关闭)").defineInRange("extraHitDamage", 2.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        EXTRA_HIT_COOLDOWN = BUILDER.comment("额外伤害冷却 (tick，防止高频触发)").defineInRange("extraHitCooldown", 2, Integer.MIN_VALUE, Integer.MAX_VALUE);
        GLOW_DAMAGE_BONUS = BUILDER.comment("对发光生物的额外伤害倍率 (30 = +30%)").defineInRange("glowDamageBonus", 0.3, -Double.MAX_VALUE, Double.MAX_VALUE);
        BENEFICIAL_STRIP = BUILDER.comment("攻击时移除敌人有益效果").define("beneficialStrip", true);
        INFINITE_ARROWS = BUILDER.comment("射箭不消耗箭矢").define("infiniteArrows", true);
        IGNITE_CHANCE = BUILDER.comment("攻击点燃概率").defineInRange("igniteChance", 0.3, -Double.MAX_VALUE, Double.MAX_VALUE);
        IGNITE_DURATION = BUILDER.comment("点燃持续时间 (秒)").defineInRange("igniteDuration", 3, Integer.MIN_VALUE, Integer.MAX_VALUE);
        PIERCE_DAMAGE_CAP = BUILDER.comment(
                        "突破限伤 / 免伤（总开关）- 开启后我方伤害不受任何单次伤害上限 / 免伤影响：",
                        "事件内恢复 + 结算后核对补足，覆盖三类限伤：",
                        "1) 限伤写在目标 hurt() 里（伤害事件之前就压完）；2) 在本事件返回之后再夹一次；",
                        "3) 目标 hurt() 直接 return false（无敌帧 / 阶段免伤），伤害事件根本不触发。",
                        "关闭 = 完全原版行为、零介入（不记录、不核对、不干预）。",
                        "注意：期望值是护甲结算前的数值，因此开启后同时会穿透目标护甲 / 抗性 / 吸收的减伤；",
                        "只提升不压低，其它模组的合法增伤不受影响；仅对非玩家目标生效")
                .define("pierceDamageCap", true);
        PIERCE_RETRY_TICKS = BUILDER.comment(
                        "破限·锁血重试 (tick) - 补足被目标锁血 / 阶段无敌挡住时，在后续多少 tick 内继续尝试",
                        "此前这种目标会把这一笔伤害直接吞掉（只补一次，写不进去就作废）；0 = 不重试")
                .defineInRange("pierceRetryTicks", 3, 0, 40);
        AOE_SPLASH_ALL_TYPES = BUILDER.comment(
                        "溅射·不分种类 - 开启后溅射命中范围内全部敌对生物；",
                        "关闭则退回旧行为（只打与被击目标同种生物）").define("aoeSplashAllTypes", true);
        KILL_CHAIN_RATIO = BUILDER.comment("击杀连锁伤害比例 - 以被击杀者最大生命值计算 (50 = 50%)").defineInRange("killChainRatio", 0.5, -Double.MAX_VALUE, Double.MAX_VALUE);
        EXECUTE_DAMAGE_BONUS = BUILDER.comment(
                        "处决增伤 - 目标生命比例低于阈值后的最大额外伤害倍率 (50 = 最多 +50%)",
                        "按缺失生命线性增长：血量恰好在阈值处 ×1.0，趋近 0 时趋近上限；0 = 关闭",
                        "与上面的「斩杀」互补：斩杀负责收割最后 10%，这里负责 10%~阈值之间的过渡段")
                .defineInRange("executeDamageBonus", 0.5, -Double.MAX_VALUE, Double.MAX_VALUE);
        EXECUTE_DAMAGE_THRESHOLD = BUILDER.comment("处决增伤起始阈值 (50 = 目标血量低于 50% 开始增伤；0 = 关闭)")
                .defineInRange("executeDamageThreshold", 0.5, -Double.MAX_VALUE, Double.MAX_VALUE);
        OVERFLOW_RATIO = BUILDER.comment(
                        "溢出转移 - 单次伤害超过目标剩余生命时，把溢出部分按此比例打向附近敌人 (30 = 30%溢出量)",
                        "只对普通命中生效（斩杀本身已经秒杀，不再溢出）；0 = 关闭")
                .defineInRange("overflowRatio", 0.3, -Double.MAX_VALUE, Double.MAX_VALUE);
        OVERFLOW_MAX_TARGETS = BUILDER.comment("溢出转移的目标数量上限 (0 = 关闭；按距离由近到远选取)")
                .defineInRange("overflowMaxTargets", 3, 0, 64);
        CASCADE_DEPTH_LIMIT = BUILDER.comment(
                        "级联伤害深度上限 - 溅射 / 连锁产生的伤害是否允许再产生溅射 / 连锁：",
                        "0 = 不允许（旧行为，最安全）；1 = 允许一层；上限 8 防止怪群无限递归把主线程卡死")
                .defineInRange("cascadeDepthLimit", 1, 0, 8);
        IGNORE_INVULNERABLE_TIME = BUILDER.comment(
                        "无视无敌帧 - 我方造成的每次打击都不被目标的受击无敌帧（10 tick）削减或吞掉，",
                        "光环、溅射、反伤、连击类能力因此能全额结算（对玩家自身受击无效）")
                .define("ignoreInvulnerableTime", true);
        BUILDER.pop();

        BUILDER.push("攻击压制 (Suppression)");
        SUPPRESSION_ENABLED = BUILDER.comment(
                        "攻击压制 - 总开关：命中后被攻击者无法回血、增益被清除",
                        "关闭后下面两项都不再生效（不影响「剥离增益效果」开关）")
                .define("suppressionEnabled", true);
        SUPPRESSION_ANTI_HEAL = BUILDER.comment(
                        "被攻击者禁疗 - 命中的目标在持续时间内无法回血",
                        "拦截范围：生命回复 / 再生药水 / 治疗药水与金苹果 / 自然恢复 / 命令与其它模组的直接加血",
                        "对「效果免疫」的生物同样生效（不会因为对方免疫药水效果就失效）")
                .define("suppressionAntiHeal", true);
        SUPPRESSION_DURATION = BUILDER.comment("禁疗持续时间 (秒，每次命中刷新；0 = 不施加禁疗)")
                .defineInRange("suppressionDuration", 5, Integer.MIN_VALUE, 1000000);
        SUPPRESSION_STRIP_BENEFITS = BUILDER.comment("被攻击者清除增益 - 每次命中移除目标身上全部有益效果")
                .define("suppressionStripBenefits", true);
        SUPPRESSION_AFFECT_PLAYERS = BUILDER.comment(
                        "对玩家生效 - 是否同样压制被攻击的玩家",
                        "默认关闭：联机里若双方都佩戴，会互相禁疗到谁都回不了血")
                .define("suppressionAffectPlayers", false);
        SUPPRESSION_STRIP_ABSORPTION = BUILDER.comment(
                        "压制吸收值 - 命中时清零目标的吸收值（黄心），且压制期间无法再获得",
                        "此前禁疗只拦 heal / setHealth，金苹果给吸收值这条等价回血路径完全没被覆盖")
                .define("suppressionStripAbsorption", true);
        SUPPRESSION_STRIP_HEALING_EFFECTS = BUILDER.comment(
                        "压制治疗类效果 - 压制期间目标无法获得再生 / 吸收 / 生命提升 / 瞬间治疗",
                        "从源头挡掉：已经挂着这类效果的目标仍会被上面的「清除增益」剥掉")
                .define("suppressionStripHealingEffects", true);
        BUILDER.pop();

        BUILDER.push("护盾系统 (Shield)");
        SHIELD_PER_SEC = BUILDER.comment("满血时每秒吸收生命 (0 = 关闭)").defineInRange("shieldPerSec", 2.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        SHIELD_MAX = BUILDER.comment("吸收护盾上限").defineInRange("shieldMax", 20.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        SHIELD_BREAK_KNOCKBACK = BUILDER.comment("护盾被击破时击退周围怪物范围 (格，0=关闭；上限 512 为引擎安全值)").defineInRange("shieldBreakKnockback", 6.0, -Double.MAX_VALUE, 512.0);
        BUILDER.pop();

        BUILDER.push("速度爆发 (Speed Burst)");
        SPEED_BURST_DURATION = BUILDER.comment("受伤后速度爆发持续 (tick，0=关闭)").defineInRange("speedBurstDuration", 100, Integer.MIN_VALUE, Integer.MAX_VALUE);
        SPEED_BURST_LEVEL = BUILDER.comment("速度爆发等级 (0=速度, 1=速度II...)").defineInRange("speedBurstLevel", 2, Integer.MIN_VALUE, Integer.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("战斗生存增强 (Enhanced Combat Survival)");
        KNOCKBACK_IMMUNITY = BUILDER.comment("击退免疫 (独立开关，与击退抗性属性独立)").define("knockbackImmunity", true);
        AUTO_POTION = BUILDER.comment("低血自动喝药水 - 生命低于阈值自动使用背包治疗药水").define("autoPotion", true);
        AUTO_POTION_THRESHOLD = BUILDER.comment("自动喝药触发血量比例 (30 = 30%)").defineInRange("autoPotionThreshold", 0.3, -Double.MAX_VALUE, Double.MAX_VALUE);
        LIGHTNING_REFLECT = BUILDER.comment("受伤时连锁闪电反击攻击者").define("lightningReflect", true);
        LIGHTNING_REFLECT_CHANCE = BUILDER.comment("闪电反击概率 (30 = 30%)").defineInRange("lightningReflectChance", 0.3, -Double.MAX_VALUE, Double.MAX_VALUE);
        LIGHTNING_REFLECT_DAMAGE = BUILDER.comment(
                        "落雷反射伤害 - 反射闪电对攻击者造成的伤害（0 = 只播放闪电动画，不造成伤害）",
                        "闪电一律为「视觉闪电」：不点燃方块、不伤害周围其它实体，只结算这一笔伤害")
                .defineInRange("lightningReflectDamage", 5.0, 0.0, Double.MAX_VALUE);
        KILL_CHAIN = BUILDER.comment("击杀连锁 - 击杀怪物时伤害周围同种生物").define("killChain", true);
        KILL_CHAIN_RANGE = BUILDER.comment("击杀连锁范围 (格；上限 512 为引擎安全值)").defineInRange("killChainRange", 8.0, -Double.MAX_VALUE, 512.0);
        BUILDER.pop();

        BUILDER.push("伤害光环 (Damage Aura)");
        DAMAGE_AURA_ENABLED = BUILDER.comment("伤害光环开关 (关闭后整体停用，范围设置保留)").define("damageAuraEnabled", true);
        DAMAGE_AURA_RANGE = BUILDER.comment("伤害光环范围 (格，0 = 关闭；上限 512 为引擎安全值)").defineInRange("damageAuraRange", 6.0, -Double.MAX_VALUE, 512.0);
        DAMAGE_AURA_DAMAGE = BUILDER.comment("伤害光环每次触发的伤害").defineInRange("damageAuraDamage", 4.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        DAMAGE_AURA_TICK_INTERVAL = BUILDER.comment("伤害光环触发间隔 (tick)").defineInRange("damageAuraTickInterval", 20, Integer.MIN_VALUE, Integer.MAX_VALUE);
        DAMAGE_AURA_BOSS_MULT = BUILDER.comment("伤害光环对Boss伤害倍率 (叠加到基础伤害)").defineInRange("damageAuraBossMult", 3.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        AURA_USE_COMBAT_PIPELINE = BUILDER.comment(
                        "光环走战斗管线 - 光环伤害改为经过与近战相同的处理：",
                        "Boss 增伤 / 低血狂暴 / 满怒 +5% / 处决增伤 / 时停增伤 / 溢出转移 / 溅射 / 限伤穿透都会生效",
                        "关闭则退回旧行为（固定数值直接 hurt，只有「对 Boss 倍率」一个加成）")
                .define("auraUseCombatPipeline", true);
        BUILDER.pop();

        BUILDER.push("采集强化 (Harvesting)");
        AUTO_SMELT = BUILDER.comment("自动冶炼 - 挖矿直接掉落锭").define("autoSmelt", true);
        INSTANT_MINE = BUILDER.comment("瞬挖 - 佩戴后任意方块只需 1 tick 即可挖穿，工具判定恒为正确工具（空手 / 工具不对也能正常掉落）").define("instantMine", true);
        INSTANT_MINE_UNBREAKABLE = BUILDER.comment("瞬挖·破不可破坏方块 - 允许挖穿硬度 -1 的方块（基岩 / 屏障 / 命令方块 / 结构方块等）；关闭则这些方块恢复不可破坏。出生点保护与领地限制仍然生效").define("instantMineUnbreakable", true);
        VEIN_MINER = BUILDER.comment("连锁挖掘 - 一键挖光同种矿脉").define("veinMiner", true);
        VEIN_MAX_BLOCKS = BUILDER.comment("连锁挖掘最大方块数 (上限 65536，再大等于让服务器长时间阻塞)").defineInRange("veinMaxBlocks", 64, Integer.MIN_VALUE, 65536);
        VEIN_USE_TAGS = BUILDER.comment("使用 forge:ores 标签识别矿石 (支持所有模组矿石)").define("veinUseTags", true);
        VEIN_MATCH_TAGS = BUILDER.comment(
                        "连锁挖掘·同族连通 - 按矿石子标签（forge:ores/iron 等）判断连通，",
                        "使铁矿与深板岩铁矿、模组同族矿石连成一片；关闭则退回「必须是同一种方块」")
                .define("veinMatchTags", true);
        FORTUNE_BONUS = BUILDER.comment("矿石额外掉落补判次数 (每次 33% 概率复制一份，不是原版时运等级；上限 1000)").defineInRange("fortuneBonus", 3, Integer.MIN_VALUE, 1000);
        LOOTING_BONUS = BUILDER.comment("额外抢夺等级 (加到武器抢夺上；上限 1000)").defineInRange("lootingBonus", 3, Integer.MIN_VALUE, 1000);
        BUILDER.pop();

        BUILDER.push("饰品融合 (Fusion)");
        FUSION_ENABLED = BUILDER.comment("饰品融合 - 允许在工作台把永恒之心与其它饰品合成，继承其属性与 Curios 回调能力（攻击/生命等属性、每 tick 效果、装备时效果、时运/抢夺等）").define("fusionEnabled", true);
        FUSION_LIMIT = BUILDER.comment("饰品融合上限 - 永恒之心最多融合多少个饰品 (0 = 禁止融合，默认 32，无上限)",
                        "实际上限只受物品 NBT 大小与装备时的属性收集开销约束")
                .defineInRange("fusionLimit", 32, 0, Integer.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("自定义配方 (Custom Recipes)");
        CUSTOM_RECIPES = BUILDER.comment(
                        "自定义配方 - 支持简化语法，一行一条",
                        "  无序合成: shapeless: <产出>[ x数量] = <材料>[ x数量] [+ <材料>…]",
                        "  有序合成: shaped: <产出>[ x数量] = <第1行> / <第2行> / <第3行>   （空格分格，_ 表示空位）",
                        "  熔炼之类: smelting: <产出> <- <材料>                        （blasting / smoking 同理）",
                        "  原版 JSON: {…}                                            （复杂配方仍可直接写 JSON）",
                        "示例: 'shaped: diamond_pickaxe = diamond diamond diamond / _ stick _ / _ stick _'",
                        "物品 ID 可省略 minecraft: 前缀；# 开头表示标签（如 #minecraft:planks）",
                        "无法识别的条目会被忽略并在日志中记录，不影响其余配方",
                        "配方在服务器启动时自动注入；改动后可执行 /eternalheart recipe reload 立即生效")
                .defineList("customRecipes", () -> List.of(), o -> o instanceof String);
        BUILDER.pop();

        BUILDER.push("自定义仪式配方 (Custom Rituals)");
        CUSTOM_RITUALS = BUILDER.comment(
                        "自定义仪式配方 - 诡厄巫法（Goety）暗黑祭坛仪式，一行一条，键值对形式（分号分隔）",
                        "  ritual: result=<产出>[ xN]; activation=<激活物>; ingredients=<材料>[ xN] [+ <材料>…]",
                        "          [; type=<goety:craft|summon|convert|enchant|…>] [; craft=<流派>] [; soul=<灵魂>] [; time=<秒>]",
                        "          [; summon=<实体ID>] [; summon_life=<tick>] [; convert=<实体tag>] [; convert_into=<实体ID>]",
                        "          [; enchant=<附魔ID>] [; xp=<经验等级>] [; research=<研究>] [; group=<分组>]",
                        "          [; sacrifice=<需献祭的实体tag>] [; sacrifice_name=<献祭显示名>]  （献祭为空则不要求）",
                        "  默认: type=goety:craft、craft 空、soul=0、time=30；非合成类（召唤/转换等）省略 result 时自动填占位物品",
                        "  材料与激活物 ID 可省略 minecraft: 前缀；# 开头表示标签（如 #forge:gems/prismarine）",
                        "  原版 JSON: {…}                                     （复杂仪式仍可直接粘贴 Goety 的完整 JSON）",
                        "示例: 'ritual: result=goety:abyss_crown; activation=goety:wild_crown; ingredients=4x #forge:dusts/prismarine + 4x #forge:gems/prismarine; craft=deep; soul=1; time=10'",
                        "该功能软依赖 Goety：未安装时条目会被忽略；安装后随服务器启动自动注入",
                        "改动后可执行 /eternalheart recipe reload 立即生效")
                .defineList("customRituals", () -> List.of(), o -> o instanceof String);
        BUILDER.pop();

        BUILDER.push("时停 (Time Stop)");
        TIME_STOP = BUILDER.comment("时停开关 (使用快捷键或命令触发，冻结范围内除自己以外的实体)").define("timeStop", true);
        TIME_STOP_DURATION = BUILDER.comment("时停持续时间 (秒)").defineInRange("timeStopDuration", 5, Integer.MIN_VALUE, Integer.MAX_VALUE);
        TIME_STOP_COOLDOWN = BUILDER.comment("时停冷却 (秒)").defineInRange("timeStopCooldown", 30, Integer.MIN_VALUE, Integer.MAX_VALUE);
        TIME_STOP_RADIUS = BUILDER.comment("时停半径 (格，0 = 整个维度)").defineInRange("timeStopRadius", 0.0, -Double.MAX_VALUE, 512.0);
        TIME_STOP_AFFECT_PLAYERS = BUILDER.comment("是否连其它玩家一起冻结 (自己永远不受影响)").define("timeStopAffectPlayers", false);
        TIME_STOP_FREEZE_BLOCK_ENTITIES = BUILDER.comment(
                        "时停·冻结方块实体 - 熔炉 / 漏斗 / 刷怪笼 / 各类机器在时停期间一并停止工作",
                        "（旧行为只冻结实体移动，方块的机器逻辑照常运行）")
                .define("timeStopFreezeBlockEntities", true);
        BUILDER.pop();

        BUILDER.push("冷却缩减 (Cooldown)");
        COOLDOWN_RATIO = BUILDER.comment(
                        "物品 / 装备技能冷却时长倍率 (1.0=不变, 0.5=减半, 0=无冷却)",
                        "作用于所有使用原版物品冷却的模组（例如灾变 Cataclysm 的武器技能）",
                        "值域完全放开：负数按无冷却处理，大于 1 会延长冷却")
                .defineInRange("cooldownRatio", 1.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("生存保障 (Survival)");
        VOID_RESCUE = BUILDER.comment("虚空救援 - 掉入虚空时传送回顶部").define("voidRescue", true);
        UNDYING_TOTEM = BUILDER.comment("自动复活 - 死亡时触发类似不死图腾").define("undyingTotem", true);
        UNDYING_COOLDOWN = BUILDER.comment("复活冷却时间 (秒)").defineInRange("undyingCooldown", 300, Integer.MIN_VALUE, Integer.MAX_VALUE);
        KILL_EXPLOSION = BUILDER.comment("击杀时产生不破坏方块的范围爆炸").define("killExplosion", true);
        KILL_EXPLOSION_POWER = BUILDER.comment("击杀爆炸威力 (上限 256，原版 TNT 为 4)").defineInRange("killExplosionPower", 3.0, -Double.MAX_VALUE, 256.0);
        SOUL_BIND = BUILDER.comment("灵魂绑定 - 死亡不掉落物品（软死亡：复活时传送到出生点）").define("soulBind", true);
        ENDER_CHEST_REMOTE = BUILDER.comment("便携末影箱 - 潜行+右键空气打开末影箱").define("enderChestRemote", true);
        AUTO_EXTINGUISH = BUILDER.comment("自动灭火 - 着火时立即熄灭；药水抗火由独立开关控制").define("autoExtinguish", true);
        INSTANT_EAT = BUILDER.comment(
                        "秒吃食物 - 佩戴时进食几乎瞬间完成（使用时长压缩到 1 tick，约 0.05 秒）",
                        "进食期间不再有持续减速；药水 / 弓 / 盾牌等其它使用类物品不受影响")
                .define("instantEat", true);
        DURABILITY_REFILL = BUILDER.comment(
                        "耐久补满 - 佩戴时定期把背包里已损耗的物品耐久直接补回满值",
                        "（覆盖主背包 / 盔甲 / 副手；与「工具不掉耐久」互补：那个只阻止新损耗，",
                        "这里负责把佩戴前已经磨掉的耐久补回来，两者可独立开关）")
                .define("durabilityRefill", true);
        DURABILITY_REFILL_INTERVAL = BUILDER.comment("耐久补满间隔 (秒，0 = 每 tick 检查)")
                .defineInRange("durabilityRefillInterval", 1, Integer.MIN_VALUE, Integer.MAX_VALUE);
        KILL_HEAL_RATIO = BUILDER.comment("击杀时恢复最大生命值比例").defineInRange("killHealRatio", 0.2, -Double.MAX_VALUE, Double.MAX_VALUE);
        ITEM_UNBREAKABLE = BUILDER.comment(
                        "工具不掉耐久 - 佩戴时手持物品的耐久消耗被完全跳过",
                        "（挖掘 / 攻击 / 自动钓鱼 / 连锁挖矿都不再磨损，附魔与属性照常生效）")
                .define("itemUnbreakable", true);
        BUILDER.pop();

        BUILDER.push("环境适应 (Adaptation)");
        MAGNET_ENABLED = BUILDER.comment("物品磁铁开关 (关闭后「物品磁铁范围」与「磁铁瞬吸范围」一并停用，范围设置保留)").define("magnetEnabled", true);
        MAGNET_RANGE = BUILDER.comment("物品磁铁范围 (格，0 = 关闭；上限 512 为引擎安全值)").defineInRange("magnetRange", 8.0, -Double.MAX_VALUE, 512.0);
        MAGNET_INSTANT_RANGE = BUILDER.comment("物品即时捡取范围 - 此范围内物品直接进入背包 (格；上限 512)").defineInRange("magnetInstantRange", 2.0, -Double.MAX_VALUE, 512.0);
        MAGNET_PULL_XP = BUILDER.comment("磁铁吸附经验球 - 经验球与物品共用同一吸附范围（即时捡取范围内直接吸收）").define("magnetPullXp", true);
        GLOW_ENABLED = BUILDER.comment("生物发光侦测开关 (关闭后「生物发光侦测范围」停用，范围设置保留)").define("glowEnabled", true);
        GLOW_RANGE = BUILDER.comment("生物发光侦测范围 (格，0 = 关闭；上限 512 为引擎安全值)").defineInRange("glowRange", 24.0, -Double.MAX_VALUE, 512.0);
        BUILDER.pop();

        BUILDER.push("光环系统 (Auras)");
        WITHER_AURA_ENABLED = BUILDER.comment("凋零光环开关 (关闭后整体停用，范围设置保留)").define("witherAuraEnabled", true);
        PEACE_AURA_ENABLED = BUILDER.comment("和平光环开关 (关闭后整体停用，范围设置保留)").define("peaceAuraEnabled", true);
        GROWTH_AURA_ENABLED = BUILDER.comment("生长光环开关 (关闭后整体停用，范围设置保留)").define("growthAuraEnabled", true);
        WITHER_AURA_RANGE = BUILDER.comment("凋零光环范围 (格，0 = 关闭；上限 512 为引擎安全值)").defineInRange("witherAuraRange", 8.0, -Double.MAX_VALUE, 512.0);
        PEACE_AURA_RANGE = BUILDER.comment("和平光环范围 - 阻止敌对生物加入世界，包括载入和生成 (格，0 = 关闭；上限 512)").defineInRange("peaceAuraRange", 32.0, -Double.MAX_VALUE, 512.0);
        GROWTH_AURA_RANGE = BUILDER.comment("生长光环范围 - 加速周围作物生长 (格，0 = 关闭；上限 64，每周期要遍历 (2r+1)²×7 个方块)").defineInRange("growthAuraRange", 12.0, -Double.MAX_VALUE, 64.0);
        GROWTH_AURA_CHANCE = BUILDER.comment("生长光环单次骨粉成功率 (50 = 50%，100 = 必定尝试)").defineInRange("growthAuraChance", 0.5, -Double.MAX_VALUE, Double.MAX_VALUE);
        GROWTH_AURA_MAX_BLOCKS = BUILDER.comment("生长光环每周期最大催熟方块数 (上限 4096)").defineInRange("growthAuraMaxBlocks", 10, Integer.MIN_VALUE, 4096);
        GROWTH_AUTO_REPLANT = BUILDER.comment("自动补种 - 收获成熟作物后自动重新种植").define("growthAutoReplant", true);
        WITHER_AURA_DURATION = BUILDER.comment("凋零光环·持续时间 (tick，默认 60)").defineInRange("witherAuraDuration", 60, Integer.MIN_VALUE, Integer.MAX_VALUE);
        WITHER_AURA_LEVEL = BUILDER.comment("凋零光环·等级 (0 = 凋零 I，1 = 凋零 II…)").defineInRange("witherAuraLevel", 1, Integer.MIN_VALUE, Integer.MAX_VALUE);
        WITHER_AURA_INTERVAL = BUILDER.comment("凋零光环·触发间隔 (tick，默认 20；越小越频繁)").defineInRange("witherAuraInterval", 20, Integer.MIN_VALUE, Integer.MAX_VALUE);
        GROWTH_VERTICAL = BUILDER.comment("生长光环·垂直扫描范围 (±格)").defineInRange("growthVertical", 3, 0, 64);
        PEACE_AURA_CLEAN = BUILDER.comment(
                        "和平光环·周期清理 - 不只拦截新加入的敌对生物，",
                        "还定期把范围内的敌对生物直接移除（不留尸体、不掉落）")
                .define("peaceAuraClean", true);
        PEACE_AURA_CLEAN_INTERVAL = BUILDER.comment("和平光环·清理间隔 (tick)").defineInRange("peaceAuraCleanInterval", 40, 1, Integer.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("永怒系统 (Eternal Fury)");
        FURY_MAX_STACKS = BUILDER.comment("永怒最大层数").defineInRange("maxStacks", 20, Integer.MIN_VALUE, Integer.MAX_VALUE);
        FURY_DECAY_TICKS = BUILDER.comment("永怒衰减间隔 (tick)").defineInRange("decayTicks", 60, Integer.MIN_VALUE, Integer.MAX_VALUE);
        FURY_DAMAGE_PER_STACK = BUILDER.comment("每层永怒伤害加成 (5 = 5%)").defineInRange("damagePerStack", 0.05, -Double.MAX_VALUE, Double.MAX_VALUE);
        FURY_LIFESTEAL = BUILDER.comment("满层永怒生命窃取比例").defineInRange("lifesteal", 0.10, -Double.MAX_VALUE, Double.MAX_VALUE);
        KILL_FURY_BONUS = BUILDER.comment("击杀额外永怒层数").defineInRange("killFuryBonus", 3, Integer.MIN_VALUE, Integer.MAX_VALUE);
        LIFESTEAL_FOOD_RESTORE = BUILDER.comment("生命窃取时恢复饱食度 (每次窃取)").defineInRange("lifestealFoodRestore", 1.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        LIFESTEAL_SATURATION_RESTORE = BUILDER.comment("生命窃取时恢复饱和度 (每次窃取)").defineInRange("lifestealSaturationRestore", 2.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        FURY_CRIT_CHANCE = BUILDER.comment("非原版暴击补判概率 (50 = 50%，0 = 关闭；原版暴击条件与判定不变)")
                .defineInRange("furyCritChance", 0.5, -Double.MAX_VALUE, Double.MAX_VALUE);
        FURY_CRIT_DAMAGE_BONUS = BUILDER.comment("暴击额外伤害加成 (10 = +10%，叠加在原版 1.5 倍之上)")
                .defineInRange("furyCritDamageBonus", 0.10, -Double.MAX_VALUE, Double.MAX_VALUE);
        FURY_CRIT_MULTIPLIER = BUILDER.comment("强制暴击的基础倍率 (1.5 = 原版暴击倍率；调高即「必爆 + 高倍暴击」)")
                .defineInRange("furyCritMultiplier", 1.5, -Double.MAX_VALUE, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("经验 (Experience)");
        XP_MULTIPLIER = BUILDER.comment("生物死亡经验倍率").defineInRange("xpMultiplier", 3.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("探索便利 (Exploration)");
        AUTO_TORCH = BUILDER.comment("自动火把 - 暗处自动从背包放置火把").define("autoTorch", true);
        AUTO_TORCH_LIGHT_LEVEL = BUILDER.comment("自动火把触发光照等级 (低于此值放置；>15 等于永不放置)").defineInRange("autoTorchLightLevel", 5, Integer.MIN_VALUE, Integer.MAX_VALUE);
        AUTO_DOOR = BUILDER.comment("自动门 - 靠近门自动开关").define("autoDoor", true);
        ORE_HIGHLIGHT = BUILDER.comment("洞穴探险 - 地表以下给周围实体添加发光").define("oreHighlight", true);
        ORE_HIGHLIGHT_RANGE = BUILDER.comment("洞穴探险高亮范围 (格；上限 512 为引擎安全值)").defineInRange("oreHighlightRange", 32.0, -Double.MAX_VALUE, 512.0);
        AUTO_FISH = BUILDER.comment("自动钓鱼 - 手持鱼竿站在水边自动垂钓").define("autoFish", true);
        AUTO_FISH_INTERVAL = BUILDER.comment("自动钓鱼间隔 (tick，越短越快)").defineInRange("autoFishInterval", 100, Integer.MIN_VALUE, Integer.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("机动便利 (Mobility Convenience)");
        AUTO_REFILL = BUILDER.comment("自动补货 - 快捷栏剩 1 个时合并背包中相同 NBT 的物品").define("autoRefill", true);
        INFINITE_BUCKET = BUILDER.comment("无限水/岩浆桶 - 用完的水桶自动补充").define("infiniteBucket", true);
        INFINITE_BUCKET_LAVA = BUILDER.comment("无限岩浆桶 - 主手或副手倒出岩浆后补回（需开启无限水/岩浆桶）").define("infiniteBucketLava", true);
        BUILDER.pop();

        BUILDER.push("防护机制 (Aegis)");
        HEALTH_GUARD = BUILDER.comment("血量防篡改 - 拦截非伤害途径的生命值削减并立即回滚（防止被其它模组/机制强行改血）").define("healthGuard", true);
        HEALTH_GUARD_HARD = BUILDER.comment("血量硬锁 - 在 setHealth 层直接拒绝未经伤害管线的降血（零窗口，不再依赖每 tick 回滚；关闭则退回审计+回滚模式）").define("healthGuardHard", true);
        MAX_HEALTH_GUARD = BUILDER.comment("生命上限保护 - 佩戴后最大生命值不会被削减（防止被偷取/封印生命上限）").define("maxHealthGuard", true);
        LETHAL_GUARD = BUILDER.comment("致死保护 - 单次致命伤害不会直接击杀，改为保留一定比例生命（防秒杀 / 防被收）").define("lethalGuard", true);
        DEATH_GUARD = BUILDER.comment("死亡兜底 - 致死保护没拦住时（直接 die / 绕过伤害管线）在死亡结算阶段强行取消死亡，保留致死保护比例的生命；与致死保护共享冷却").define("deathGuard", true);
        LETHAL_GUARD_RATIO = BUILDER.comment("致死保护保留生命比例 (15 = 保留最大生命的 15%，最低保留 1 点；≥100 等于免疫致命伤)").defineInRange("lethalGuardRatio", 0.15, -Double.MAX_VALUE, Double.MAX_VALUE);
        LETHAL_GUARD_COOLDOWN = BUILDER.comment("致死保护冷却 (tick，冷却期间不再触发；0 = 无冷却)").defineInRange("lethalGuardCooldown", 600, Integer.MIN_VALUE, Integer.MAX_VALUE);
        LETHAL_GUARD_IMMUNE = BUILDER.comment(
                        "濒死无敌窗口 (秒) - 致死保护 / 死亡兜底触发后的一段时间内完全免疫伤害（图腾式保护，避免刚被救回就被下一次攻击打死）",
                        "0 = 关闭（只回血，不给无敌窗口）")
                .defineInRange("lethalGuardImmune", 2, Integer.MIN_VALUE, Integer.MAX_VALUE);
        KILL_GUARD = BUILDER.comment(
                        "强制击杀兜底 - 让 /kill、generic_kill 这类「无视无敌」的强制击杀同样走致死保护与死亡兜底",
                        "打开后即使被 /kill 也会保留致死保护比例的生命；关闭则保持原版（一击必死）",
                        "虚空伤害不受此开关影响（由下面的「虚空拦截」专管，否则会永远卡在虚空里）")
                .define("killGuard", true);
        VOID_GUARD = BUILDER.comment(
                        "虚空拦截 - 掉出世界时拦下虚空伤害，并把玩家传送回最后一次安全落点",
                        "（未记录到安全落点时抬到建筑上限，与生存系统的虚空救援一致）")
                .define("voidGuard", true);
        KEEP_INVENTORY = BUILDER.comment("死亡保留物品 - 死亡时物品栏不掉落，重生后原样归还（防止掉落物被收走）").define("keepInventory", true);
        KEEP_EXPERIENCE = BUILDER.comment("死亡保留经验 - 死亡时经验不掉落，重生后原样归还").define("keepExperience", true);
        ITEM_GUARD = BUILDER.comment("掉落物保护 - 自己丢出的物品在保护时长内只有自己能拾取（防止被他人或收集类机制收走）").define("itemGuard", true);
        ITEM_GUARD_TICKS = BUILDER.comment("掉落物保护时长 (tick)").defineInRange("itemGuardTicks", 200, Integer.MIN_VALUE, Integer.MAX_VALUE);
        CURIO_KEEP_ON_DEATH = BUILDER.comment("饰品死亡不掉落 - 死亡瞬间收回饰品并在重生后原样归还（独立于背包掉落规则）").define("curioKeepOnDeath", true);
        CURIO_UNBIND = BUILDER.comment("无视绑定 - 允许随时取下饰品：自动清除绑定诅咒附魔，忽略其它模组的绑定限制").define("curioUnbind", true);
        CURIO_SEIZE_GUARD = BUILDER.comment("饰品防夺 - 饰品被外部机制强行取走（如亚波伦末日终结）时立即收回，并重建属性加成").define("curioSeizeGuard", true);
        BUILDER.pop();

        BUILDER.push("模组集成 (Mod Integrations)");
        USE_FTB_ULTIMINE = BUILDER.comment("使用 FTB Ultimine 替代自定义连锁挖掘（需安装 ftb-ultimine，功能更强、兼容更好）").define("useFtbUltimine", true);
        FLIGHT_MODE = BUILDER.comment("飞行模式（唯一控制源）：CREATIVE=创造飞行(双击空格起飞/悬停)，ELYTRA=鞘翅滑翔(安装了 Caelus 用真鞘翅；未安装则用内置滑翔：自由落体自动缓降+沿视角前进，潜行取消)，OFF=关闭飞行").defineEnum("flightMode", FlightMode.CREATIVE);
        REVELATIONFIX_WHITELIST = BUILDER.comment(
                        "诡厄巫法：启示录兼容 - 把永恒之心写入 RevelationFix 的「天启」豁免名单，",
                        "下界亚波伦「末日终结」阶段不会夺取（其公开配置 config/revelationfix/revelationfix-common.toml 的 whitelistItems）",
                        "只做幂等的追加/移除（不触碰其模组本体）；修改后可能需要重启一次游戏生效")
                .define("revelationfixWhitelist", true);
        REVELATIONFIX_EXTRA_ITEMS = BUILDER.comment(
                        "自定义饰品防收 - 除永恒之心本身外，额外写入 RevelationFix「天启」豁免名单的物品 ID 列表",
                        "（下界亚波伦「末日终结」阶段不会夺取这些物品；通常填其它饰品，如 minecraft:totem_of_undying、curios:ring 等）",
                        "ID 可省略 minecraft: 前缀；留空则只豁免永恒之心本身",
                        "关闭上面的「启示录兼容」开关时会连同这些条目一并移除；修改后可能需要重启一次游戏生效")
                .defineList("revelationfixExtraItems", () -> List.of(), o -> o instanceof String);
        REVELATIONFIX_AUTO_DETECT = BUILDER.comment(
                        "自动检测佩戴饰品 - 开启后定期扫描所有在线玩家佩戴中的饰品（Curios 饰品栏），",
                        "把佩戴中的物品 ID 一并写入 RevelationFix 的「天启」豁免名单：",
                        "玩家戴上新饰品即自动保护，饰品被卸下且无人佩戴时自动移除",
                        "（依赖上面的「启示录兼容」总开关与「自定义饰品防收」列表，三者合并生效）")
                .define("revelationfixAutoDetect", true);
        REVELATIONFIX_GUARD_EQUIPMENT = BUILDER.comment(
                        "装备防收 - 开启后定期扫描所有在线玩家「穿在身上的装备」：",
                        "头盔 / 胸甲 / 护腿 / 靴子 + 主手 / 副手，把物品 ID 写入 RevelationFix「天启」豁免名单",
                        "（脱下或换下后自动移除；需打开上面的「启示录兼容」总开关才生效）")
                .define("revelationfixGuardEquipment", false);
        REVELATIONFIX_GUARD_INVENTORY = BUILDER.comment(
                        "物品防收 - 开启后定期扫描所有在线玩家背包（含快捷栏）里的物品，",
                        "把物品 ID 写入 RevelationFix「天启」豁免名单",
                        "（物品用完 / 移出背包后自动移除；需打开上面的「启示录兼容」总开关才生效）")
                .define("revelationfixGuardInventory", false);
        CURIOS_SLOT_EXPANSION_ENABLED = BUILDER.comment(
                        "Curios 饰品槽位扩容 - 总开关：按下面的列表给整合包内的饰品槽位加格子",
                        "（点开列表可自动读取当前整合包里全部已注册的饰品槽位，选中槽位再填数量）")
                .define("curiosSlotExpansionEnabled", true);
        CURIOS_SLOT_EXPANSION = BUILDER.comment(
                        "饰品槽位扩容列表 - 每项格式「槽位ID|总数量」，如 ring|10、eternal_heart|3",
                        "数量 = 该槽位扩容后的总格数（不是增量），编辑框允许填到整数极限 2147483647",
                        "（出于内存与界面安全，每个槽位实际最多生效 4096 格，超出部分按 4096 处理；改完约 2 秒内自动生效，无需重启）")
                .defineList("curiosSlotExpansion", () -> List.of(), o -> o instanceof String);
        BUILDER.pop();

        BUILDER.push("渲染 (Rendering)");
        HALO_ENABLED = BUILDER.comment(
                        "头顶光环 - 佩戴「永恒之心」时在头顶绘制一圈悬浮光环（第三人称 / 其他玩家均可见）。",
                        "纯客户端表现，不影响任何数值；关闭后立即消失。")
                .define("haloEnabled", true);
        HALO_RADIUS = BUILDER.comment("光环半径 (格，0.1 ~ 3.0；0.6 约为头顶大半身位)")
                .defineInRange("haloRadius", 0.6, 0.05, 3.0);
        HALO_HEIGHT = BUILDER.comment(
                        "光环离脚底的高度 (格，玩家身高 1.8；填负值可把光环压到肩膀 / 头部位置)",
                        "0 = 与脚底同高；圆环样式一般配合较大的半径使用")
                .defineInRange("haloHeight", 0.0, -1.0, 4.0);
        HALO_THICKNESS = BUILDER.comment("光环圆环的粗细 (格，0.02 ~ 0.4；越大越像实心圆环)")
                .defineInRange("haloThickness", 0.06, 0.01, 0.4);
        HALO_COLOR = BUILDER.comment(
                        "光环颜色 - RGB 十进制整数（十六进制转十进制即可，例如金色 0xFFD54A = 16766282）。",
                        "面板里直接填十进制数值；16711680=红、65280=绿、255=蓝、16777215=白")
                .defineInRange("haloColor", 0xFFD54A, 0, 0xFFFFFF);
        HALO_SPIN_SPEED = BUILDER.comment("光环自转速度 (度/秒，0 = 静止不旋转)")
                .defineInRange("haloSpinSpeed", 45.0, -720.0, 720.0);
        HALO_RAINBOW = BUILDER.comment(
                        "彩虹光环 - 颜色沿光环周向铺成彩虹并缓缓流动（多彩变换），",
                        "相当于在光环上放了一条永远转不完的彩虹带。",
                        "开启后「光环颜色」被忽略；关掉则回到固定单色。")
                .define("haloRainbow", true);
        HALO_RAINBOW_SPEED = BUILDER.comment(
                        "彩虹流动速度 (圈/秒，0 = 只保留静态彩虹不流动；负值反向流动)",
                        "注意这是「颜色沿环流动」，与上面的「自转速度」是两回事，可各调各的")
                .defineInRange("haloRainbowSpeed", 0.15, -5.0, 5.0);
        HALO_RAINBOW_BANDS = BUILDER.comment(
                        "彩虹色带条数（整数圈）：0 = 整环同色、颜色只随时间整体变化；",
                        "1 = 光环一周正好铺满一整圈色相（红橙黄绿蓝紫各占一段，推荐）；",
                        "2 / 3 = 一周铺两圈 / 三圈彩虹，色带更密；",
                        "必须是整数 —— 填小数会让色环首尾接不上，出现一条明显的颜色硬边；",
                        "条数越多每段跨度越大，超过 4 条开始能看出色阶，1 ~ 2 条最顺眼")
                .defineInRange("haloRainbowBands", 1, 0, 12);
        HALO_ORBS = BUILDER.comment(
                        "光球模式 - 把头顶的圆环光环换成「环绕光球」：若干发光球均匀分布在一个",
                        "以玩家中点为球心的圆轨道上整体自转（默认 5 个）。",
                        "颜色 / 彩虹 / 自转速度与圆环样式共用；半径、高度、倾角、大小各自独立。",
                        "关闭本项即回到圆环样式，此时「光环高度」「光环粗细」等才会重新生效。")
                .define("haloOrbs", true);
        HALO_ORB_COUNT = BUILDER.comment("光球数量 (1 ~ 8，均匀分布在同一条轨道上)")
                .defineInRange("haloOrbCount", 5, 1, 8);
        HALO_ORB_RADIUS = BUILDER.comment(
                        "光球轨道半径 (格) —— 每个球到轨道球心（玩家中点）的水平距离。",
                        "0.6 约为头顶大半身位；想让球离身体更远 / 更贴身就调这里。",
                        "与圆环样式的「光环半径」互相独立，改这里不会影响圆环。")
                .defineInRange("haloOrbRadius", 0.6, 0.05, 3.0);
        HALO_ORB_SIZE = BUILDER.comment("单个光球的半径 (格；光球外还有一层约 1.8 倍大的柔光)")
                .defineInRange("haloOrbSize", 0.1, 0.04, 0.4);
        HALO_ORB_HEIGHT = BUILDER.comment(
                        "光球轨道的球心高度 (相对脚底，格) —— 就是「以玩家中点为球心」的那个球心。",
                        "玩家身高 1.8，0.9 即身体正中，1.2 略高于身体正中；",
                        "想让球环绕腰部填 0.9 左右，环绕头部填 1.5 以上")
                .defineInRange("haloOrbHeight", 1.2, -1.0, 4.0);
        HALO_ORB_TILT = BUILDER.comment(
                        "轨道倾斜角 (度，0 = 水平环绕，正负决定往哪一侧倾斜)",
                        "倾斜后从侧面看球会前后错落，更接近「环绕身体」的观感")
                .defineInRange("haloOrbTilt", -40.0, -90.0, 90.0);
        HALO_ORB_TRAIL = BUILDER.comment(
                        "光球拖影 - 每个球身后跟随几级渐小渐淡的光痕 (0 = 关闭；球越多 / 越大，拖影越显眼)。",
                        "拖影方向自动跟随自转方向；自转速度为 0 时拖影与球重合，看不出多余的东西。")
                .defineInRange("haloOrbTrail", 5, 0, 8);
        RAINBOW_NAME = BUILDER.comment(
                        "炫彩提示框 - 「永恒之心」提示框里的物品名与全部说明文字按彩虹色逐字渐变，并随时间流动。",
                        "纯客户端显示效果：不改动物品名本身，也不会出现在聊天 / 掉落物标签里。")
                .define("rainbowName", true);
        RAINBOW_NAME_SPEED = BUILDER.comment(
                        "炫彩文字的色相流动速度 (圈/秒，0 = 静止彩虹不再流动；负值反向流动)",
                        "每个字符推进固定色相步长，整段提示框连成一条流动的彩虹")
                .defineInRange("rainbowNameSpeed", 0.25, -5.0, 5.0);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getModId().equals(EternalHeartMod.MODID)) {
            CONFIG = event.getConfig();
            // 按开关同步 RevelationFix 的「天启」豁免名单（合规说明见 RevelationFixCompat）；
            // 自动检测条目传 null 表示本次不动（服务器启动后由扫描器接管）
            RevelationFixCompat.sync(REVELATIONFIX_WHITELIST.get(), REVELATIONFIX_EXTRA_ITEMS.get(), null);
        }
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        if (!event.getConfig().getModId().equals(EternalHeartMod.MODID)) return;
        // 配置重载后，刷新在线玩家的永恒之心属性加成，避免「改配置必须重新佩戴才生效」。
        // 排到服务器主线程执行，防止在网络/UI 线程直接修改玩家属性。
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(com.eternal_heart.network.ConfigNetwork::publish);
        }
    }
}
