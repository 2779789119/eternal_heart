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

    // ==================== 机动系统 ====================
    public static final ForgeConfigSpec.BooleanValue JUMP_BOOST;
    public static final ForgeConfigSpec.BooleanValue STEP_ASSIST;
    public static final ForgeConfigSpec.BooleanValue SLOW_FALL_GLIDE;
    public static final ForgeConfigSpec.BooleanValue DOLPHINS_GRACE;
    public static final ForgeConfigSpec.BooleanValue LAVA_WALKING;
    public static final ForgeConfigSpec.BooleanValue WATER_WALKING;

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
    public static final ForgeConfigSpec.BooleanValue PIERCE_IMMUNITY;

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

    // ==================== 时停 ====================
    public static final ForgeConfigSpec.BooleanValue TIME_STOP;
    public static final ForgeConfigSpec.IntValue TIME_STOP_DURATION;
    public static final ForgeConfigSpec.IntValue TIME_STOP_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue TIME_STOP_RADIUS;
    public static final ForgeConfigSpec.BooleanValue TIME_STOP_AFFECT_PLAYERS;

    // ==================== 战斗生存增强 ====================
    public static final ForgeConfigSpec.BooleanValue KNOCKBACK_IMMUNITY;
    public static final ForgeConfigSpec.BooleanValue AUTO_POTION;
    public static final ForgeConfigSpec.DoubleValue AUTO_POTION_THRESHOLD;
    public static final ForgeConfigSpec.BooleanValue LIGHTNING_REFLECT;
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_REFLECT_CHANCE;
    public static final ForgeConfigSpec.BooleanValue KILL_CHAIN;
    public static final ForgeConfigSpec.DoubleValue KILL_CHAIN_RANGE;

    // ==================== 伤害光环 ====================
    public static final ForgeConfigSpec.BooleanValue DAMAGE_AURA_ENABLED;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_AURA_RANGE;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_AURA_DAMAGE;
    public static final ForgeConfigSpec.IntValue DAMAGE_AURA_TICK_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_AURA_BOSS_MULT;

    // ==================== 采集强化 ====================
    public static final ForgeConfigSpec.BooleanValue AUTO_SMELT;
    public static final ForgeConfigSpec.BooleanValue VEIN_MINER;
    public static final ForgeConfigSpec.IntValue VEIN_MAX_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue VEIN_USE_TAGS;
    public static final ForgeConfigSpec.IntValue FORTUNE_BONUS;
    public static final ForgeConfigSpec.IntValue LOOTING_BONUS;

    // ==================== 生存保障 ====================
    public static final ForgeConfigSpec.BooleanValue VOID_RESCUE;
    public static final ForgeConfigSpec.BooleanValue UNDYING_TOTEM;
    public static final ForgeConfigSpec.IntValue UNDYING_COOLDOWN;
    public static final ForgeConfigSpec.BooleanValue KILL_EXPLOSION;
    public static final ForgeConfigSpec.DoubleValue KILL_EXPLOSION_POWER;
    public static final ForgeConfigSpec.BooleanValue SOUL_BIND;
    public static final ForgeConfigSpec.BooleanValue ENDER_CHEST_REMOTE;
    public static final ForgeConfigSpec.BooleanValue AUTO_EXTINGUISH;
    public static final ForgeConfigSpec.DoubleValue KILL_HEAL_RATIO;

    // ==================== 环境适应 ====================
    public static final ForgeConfigSpec.DoubleValue MAGNET_RANGE;
    public static final ForgeConfigSpec.DoubleValue MAGNET_INSTANT_RANGE;
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

    // ==================== 永怒系统 ====================
    public static final ForgeConfigSpec.IntValue FURY_MAX_STACKS;
    public static final ForgeConfigSpec.IntValue FURY_DECAY_TICKS;
    public static final ForgeConfigSpec.DoubleValue FURY_DAMAGE_PER_STACK;
    public static final ForgeConfigSpec.DoubleValue FURY_LIFESTEAL;
    public static final ForgeConfigSpec.IntValue KILL_FURY_BONUS;
    public static final ForgeConfigSpec.DoubleValue LIFESTEAL_FOOD_RESTORE;
    public static final ForgeConfigSpec.DoubleValue LIFESTEAL_SATURATION_RESTORE;

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
    public static final ForgeConfigSpec.BooleanValue MAX_HEALTH_GUARD;
    public static final ForgeConfigSpec.BooleanValue LETHAL_GUARD;
    public static final ForgeConfigSpec.DoubleValue LETHAL_GUARD_RATIO;
    public static final ForgeConfigSpec.IntValue LETHAL_GUARD_COOLDOWN;
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

    // ==================== 渲染 ====================
    /** 碎片渲染总开关（默认开）。关闭后完全退回模组原版：不画碎片、提示框照常显示。 */
    public static final ForgeConfigSpec.BooleanValue SHARD_VEIL;

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
        BUILDER.pop();

        BUILDER.push("机动系统 (Mobility)");
        JUMP_BOOST = BUILDER.comment("跳跃增强 (+100%)").define("jumpBoost", true);
        STEP_ASSIST = BUILDER.comment("自动上坡 - 直接走上一格高方块").define("stepAssist", true);
        SLOW_FALL_GLIDE = BUILDER.comment("缓降滑翔 - 空中潜行时缓慢降落并可前进").define("slowFallGlide", true);
        DOLPHINS_GRACE = BUILDER.comment("海豚的恩惠 - 水中加速游泳").define("dolphinsGrace", true);
        LAVA_WALKING = BUILDER.comment("熔岩行走").define("lavaWalking", true);
        WATER_WALKING = BUILDER.comment("水上行走 - 站在水面，潜行允许下潜").define("waterWalking", true);
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
        PIERCE_DAMAGE_CAP = BUILDER.comment("突破限伤 - 最终结算阶段把伤害恢复为我方期望值：其它模组的单次伤害上限（限伤）会被突破。","注意：期望值是护甲结算前的数值，因此开启后同时会穿透目标护甲 / 抗性 / 吸收的减伤；只提升不压低，其它模组的合法增伤不受影响").define("pierceDamageCap", true);
        PIERCE_IMMUNITY = BUILDER.comment("突破免伤 - 目标用「取消伤害事件」完全免疫时改用真实伤害直接扣血（仅对非玩家目标生效；会无视 BOSS 无敌阶段与不死图腾）").define("pierceImmunity", false);
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
        KILL_CHAIN = BUILDER.comment("击杀连锁 - 击杀怪物时伤害周围同种生物").define("killChain", true);
        KILL_CHAIN_RANGE = BUILDER.comment("击杀连锁范围 (格；上限 512 为引擎安全值)").defineInRange("killChainRange", 8.0, -Double.MAX_VALUE, 512.0);
        BUILDER.pop();

        BUILDER.push("伤害光环 (Damage Aura)");
        DAMAGE_AURA_ENABLED = BUILDER.comment("伤害光环开关 (关闭后整体停用，范围设置保留)").define("damageAuraEnabled", true);
        DAMAGE_AURA_RANGE = BUILDER.comment("伤害光环范围 (格，0 = 关闭；上限 512 为引擎安全值)").defineInRange("damageAuraRange", 6.0, -Double.MAX_VALUE, 512.0);
        DAMAGE_AURA_DAMAGE = BUILDER.comment("伤害光环每次触发的伤害").defineInRange("damageAuraDamage", 4.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        DAMAGE_AURA_TICK_INTERVAL = BUILDER.comment("伤害光环触发间隔 (tick)").defineInRange("damageAuraTickInterval", 20, Integer.MIN_VALUE, Integer.MAX_VALUE);
        DAMAGE_AURA_BOSS_MULT = BUILDER.comment("伤害光环对Boss伤害倍率 (叠加到基础伤害)").defineInRange("damageAuraBossMult", 3.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("采集强化 (Harvesting)");
        AUTO_SMELT = BUILDER.comment("自动冶炼 - 挖矿直接掉落锭").define("autoSmelt", true);
        VEIN_MINER = BUILDER.comment("连锁挖掘 - 一键挖光同种矿脉").define("veinMiner", true);
        VEIN_MAX_BLOCKS = BUILDER.comment("连锁挖掘最大方块数 (上限 65536，再大等于让服务器长时间阻塞)").defineInRange("veinMaxBlocks", 64, Integer.MIN_VALUE, 65536);
        VEIN_USE_TAGS = BUILDER.comment("使用 forge:ores 标签识别矿石 (支持所有模组矿石)").define("veinUseTags", true);
        FORTUNE_BONUS = BUILDER.comment("矿石额外掉落补判次数 (每次 33% 概率复制一份，不是原版时运等级；上限 1000)").defineInRange("fortuneBonus", 3, Integer.MIN_VALUE, 1000);
        LOOTING_BONUS = BUILDER.comment("额外抢夺等级 (加到武器抢夺上；上限 1000)").defineInRange("lootingBonus", 3, Integer.MIN_VALUE, 1000);
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

        BUILDER.push("时停 (Time Stop)");
        TIME_STOP = BUILDER.comment("时停开关 (使用快捷键或命令触发，冻结范围内除自己以外的实体)").define("timeStop", true);
        TIME_STOP_DURATION = BUILDER.comment("时停持续时间 (秒)").defineInRange("timeStopDuration", 5, Integer.MIN_VALUE, Integer.MAX_VALUE);
        TIME_STOP_COOLDOWN = BUILDER.comment("时停冷却 (秒)").defineInRange("timeStopCooldown", 30, Integer.MIN_VALUE, Integer.MAX_VALUE);
        TIME_STOP_RADIUS = BUILDER.comment("时停半径 (格，0 = 整个维度)").defineInRange("timeStopRadius", 0.0, -Double.MAX_VALUE, 512.0);
        TIME_STOP_AFFECT_PLAYERS = BUILDER.comment("是否连其它玩家一起冻结 (自己永远不受影响)").define("timeStopAffectPlayers", false);
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
        KILL_HEAL_RATIO = BUILDER.comment("击杀时恢复最大生命值比例").defineInRange("killHealRatio", 0.2, -Double.MAX_VALUE, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("环境适应 (Adaptation)");
        MAGNET_RANGE = BUILDER.comment("物品磁铁范围 (格，0 = 关闭；上限 512 为引擎安全值)").defineInRange("magnetRange", 8.0, -Double.MAX_VALUE, 512.0);
        MAGNET_INSTANT_RANGE = BUILDER.comment("物品即时捡取范围 - 此范围内物品直接进入背包 (格；上限 512)").defineInRange("magnetInstantRange", 2.0, -Double.MAX_VALUE, 512.0);
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
        BUILDER.pop();

        BUILDER.push("永怒系统 (Eternal Fury)");
        FURY_MAX_STACKS = BUILDER.comment("永怒最大层数").defineInRange("maxStacks", 20, Integer.MIN_VALUE, Integer.MAX_VALUE);
        FURY_DECAY_TICKS = BUILDER.comment("永怒衰减间隔 (tick)").defineInRange("decayTicks", 60, Integer.MIN_VALUE, Integer.MAX_VALUE);
        FURY_DAMAGE_PER_STACK = BUILDER.comment("每层永怒伤害加成 (5 = 5%)").defineInRange("damagePerStack", 0.05, -Double.MAX_VALUE, Double.MAX_VALUE);
        FURY_LIFESTEAL = BUILDER.comment("满层永怒生命窃取比例").defineInRange("lifesteal", 0.10, -Double.MAX_VALUE, Double.MAX_VALUE);
        KILL_FURY_BONUS = BUILDER.comment("击杀额外永怒层数").defineInRange("killFuryBonus", 3, Integer.MIN_VALUE, Integer.MAX_VALUE);
        LIFESTEAL_FOOD_RESTORE = BUILDER.comment("生命窃取时恢复饱食度 (每次窃取)").defineInRange("lifestealFoodRestore", 1.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        LIFESTEAL_SATURATION_RESTORE = BUILDER.comment("生命窃取时恢复饱和度 (每次窃取)").defineInRange("lifestealSaturationRestore", 2.0, -Double.MAX_VALUE, Double.MAX_VALUE);
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
        MAX_HEALTH_GUARD = BUILDER.comment("生命上限保护 - 佩戴后最大生命值不会被削减（防止被偷取/封印生命上限）").define("maxHealthGuard", true);
        LETHAL_GUARD = BUILDER.comment("致死保护 - 单次致命伤害不会直接击杀，改为保留一定比例生命（防秒杀 / 防被收）").define("lethalGuard", true);
        LETHAL_GUARD_RATIO = BUILDER.comment("致死保护保留生命比例 (15 = 保留最大生命的 15%，最低保留 1 点；≥100 等于免疫致命伤)").defineInRange("lethalGuardRatio", 0.15, -Double.MAX_VALUE, Double.MAX_VALUE);
        LETHAL_GUARD_COOLDOWN = BUILDER.comment("致死保护冷却 (tick，冷却期间不再触发；0 = 无冷却)").defineInRange("lethalGuardCooldown", 600, Integer.MIN_VALUE, Integer.MAX_VALUE);
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
        BUILDER.pop();

        BUILDER.push("渲染 (Rendering)");
        SHARD_VEIL = BUILDER.comment(
                        "碎片渲染 - 永恒之心的碎片环绕特效（拿在手上 / 戴在饰品栏时出现的碎片动画与悬停聚合面板）。",
                        "关闭后完全恢复模组原版表现：碎片不再渲染，物品提示框照常直接显示",
                        "（背包里悬停即见，无需按住 Shift；世界中也不再弹出碎片面板）。")
                .define("shardVeil", true);
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
