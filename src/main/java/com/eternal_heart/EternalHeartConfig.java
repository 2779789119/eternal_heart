package com.eternal_heart;

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

    // ==================== 防御系统 ====================
    public static final ForgeConfigSpec.DoubleValue DAMAGE_REDUCTION;
    public static final ForgeConfigSpec.DoubleValue FALL_DAMAGE_RATIO;
    public static final ForgeConfigSpec.DoubleValue REFLECT_RATIO;
    public static final ForgeConfigSpec.BooleanValue FIRE_IMMUNE;
    public static final ForgeConfigSpec.BooleanValue DROWN_IMMUNE;
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
    public static final ForgeConfigSpec.BooleanValue CREATIVE_FLIGHT;
    public static final ForgeConfigSpec.BooleanValue JUMP_BOOST;
    public static final ForgeConfigSpec.BooleanValue STEP_ASSIST;
    public static final ForgeConfigSpec.BooleanValue SLOW_FALL_GLIDE;
    public static final ForgeConfigSpec.BooleanValue DOLPHINS_GRACE;
    public static final ForgeConfigSpec.DoubleValue WALL_CLIMB_SPEED;
    public static final ForgeConfigSpec.BooleanValue WALL_CLIMB;
    public static final ForgeConfigSpec.BooleanValue LAVA_WALKING;
    public static final ForgeConfigSpec.BooleanValue WATER_WALKING;

    // ==================== 冲刺 ====================
    public static final ForgeConfigSpec.BooleanValue DASH;
    public static final ForgeConfigSpec.DoubleValue DASH_FORCE;
    public static final ForgeConfigSpec.IntValue DASH_INVULN_TICKS;
    public static final ForgeConfigSpec.DoubleValue DASH_DAMAGE;
    public static final ForgeConfigSpec.IntValue DASH_COOLDOWN;

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

    // ==================== 护盾系统 ====================
    public static final ForgeConfigSpec.DoubleValue SHIELD_PER_SEC;
    public static final ForgeConfigSpec.DoubleValue SHIELD_MAX;
    public static final ForgeConfigSpec.DoubleValue SHIELD_BREAK_KNOCKBACK;

    // ==================== 速度爆发 ====================
    public static final ForgeConfigSpec.IntValue SPEED_BURST_DURATION;
    public static final ForgeConfigSpec.IntValue SPEED_BURST_LEVEL;

    // ==================== 战斗生存增强 ====================
    public static final ForgeConfigSpec.BooleanValue KNOCKBACK_IMMUNITY;
    public static final ForgeConfigSpec.BooleanValue AUTO_POTION;
    public static final ForgeConfigSpec.DoubleValue AUTO_POTION_THRESHOLD;
    public static final ForgeConfigSpec.BooleanValue LIGHTNING_REFLECT;
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_REFLECT_CHANCE;
    public static final ForgeConfigSpec.BooleanValue KILL_CHAIN;
    public static final ForgeConfigSpec.DoubleValue KILL_CHAIN_RANGE;

    // ==================== 伤害光环 ====================
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
    public static final ForgeConfigSpec.DoubleValue KILL_HEAL_RATIO;

    // ==================== 环境适应 ====================
    public static final ForgeConfigSpec.DoubleValue MAGNET_RANGE;
    public static final ForgeConfigSpec.DoubleValue MAGNET_INSTANT_RANGE;
    public static final ForgeConfigSpec.DoubleValue GLOW_RANGE;

    // ==================== 光环系统 ====================
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
    public static final ForgeConfigSpec.BooleanValue AUTO_OPEN_CHEST;
    public static final ForgeConfigSpec.DoubleValue AUTO_OPEN_CHEST_RANGE;

    // ==================== 机动便利 ====================
    public static final ForgeConfigSpec.BooleanValue AUTO_REFILL;
    public static final ForgeConfigSpec.BooleanValue INFINITE_BUCKET;
    public static final ForgeConfigSpec.BooleanValue INFINITE_BUCKET_LAVA;

    // ==================== 模组集成 ====================
    public static final ForgeConfigSpec.BooleanValue USE_FTB_ULTIMINE;
    public static final ForgeConfigSpec.EnumValue<FlightMode> FLIGHT_MODE;
    public static final ForgeConfigSpec.DoubleValue PEHKUI_SCALE;

    public static final ForgeConfigSpec SPEC;

    public enum FlightMode {
        CREATIVE,
        ELYTRA,
        OFF
    }

    static {
        BUILDER.push("属性系统 (Attributes)");
        ATTACK_DAMAGE = BUILDER.comment("攻击伤害 Total 乘数 (1.0=不变, 2.5=×2.5 即 +150%, 3.5=×3.5 即 +250%)").defineInRange("attackDamage", 2.5, 0.0, Double.MAX_VALUE);
        ATTACK_SPEED = BUILDER.comment("攻击速度 Total 乘数 (1.0=不变, 2.5=×2.5 即 +150%)").defineInRange("attackSpeed", 2.5, 0.0, Double.MAX_VALUE);
        MAX_HEALTH = BUILDER.comment("最大生命值增加量 (加法，原版上限 1024，安装 AttributeFix 后无上限)").defineInRange("maxHealth", 1004.0, 0.0, Double.MAX_VALUE);
        ARMOR = BUILDER.comment("护甲值增加量").defineInRange("armor", 12.0, 0.0, Double.MAX_VALUE);
        ARMOR_TOUGHNESS = BUILDER.comment("护甲韧性增加量").defineInRange("armorToughness", 8.0, 0.0, Double.MAX_VALUE);
        KNOCKBACK_RESIST = BUILDER.comment("击退抗性 (1.0 = 免疫击退)").defineInRange("knockbackResist", 1.0, 0.0, Double.MAX_VALUE);
        MOVEMENT_SPEED = BUILDER.comment("移动速度 Total 乘数 (0.5 = +50% 最终移速)").defineInRange("movementSpeed", 0.5, 0.0, Double.MAX_VALUE);
        LUCK = BUILDER.comment("幸运值增加量").defineInRange("luck", 5.0, 0.0, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("防御系统 (Defense)");
        DAMAGE_REDUCTION = BUILDER.comment("伤害减免比例 (0.5 = 50%)").defineInRange("damageReduction", 0.5, 0.0, Double.MAX_VALUE);
        FALL_DAMAGE_RATIO = BUILDER.comment("摔落伤害保留比例 (0 = 完全免疫)").defineInRange("fallDamageRatio", 0.0, 0.0, Double.MAX_VALUE);
        REFLECT_RATIO = BUILDER.comment("近战反伤比例 (0.2 = 20%)").defineInRange("reflectRatio", 0.2, 0.0, Double.MAX_VALUE);
        FIRE_IMMUNE = BUILDER.comment("是否免疫火焰伤害").define("fireImmune", true);
        DROWN_IMMUNE = BUILDER.comment("是否免疫溺水伤害").define("drownImmune", true);
        DEBUFF_CLEAR_INTERVAL = BUILDER.comment("清除负面效果间隔 (tick)").defineInRange("debuffClearInterval", 60, 0, Integer.MAX_VALUE);
        DEBUFF_BLACKLIST = BUILDER.comment("效果强制黑名单 - 即使标记为有益也会被清除","格式: [\"modid:effect_id\"]").defineList("debuffBlacklist", () -> List.of(), o -> o instanceof String s && s.contains(":"));
        DEBUFF_WHITELIST = BUILDER.comment("效果保护白名单 - 永远不被清除","格式: [\"modid:effect_id\"]").defineList("debuffWhitelist", () -> List.of(), o -> o instanceof String s && s.contains(":"));
        SUFFOCATE_IMMUNE = BUILDER.comment("是否免疫窒息伤害").define("suffocateImmune", true);
        CACTUS_IMMUNE = BUILDER.comment("是否免疫仙人掌伤害").define("cactusImmune", true);
        EXPLOSION_RESIST = BUILDER.comment("额外爆炸伤害减免 (0.8 = 80%，叠加到基础减伤)").defineInRange("explosionResist", 0.8, 0.0, Double.MAX_VALUE);
        DAMAGE_CAP_RATIO = BUILDER.comment("单次受伤上限 (0.3 = 不超过最大生命30%，0 = 关闭)").defineInRange("damageCapRatio", 0.3, 0.0, Double.MAX_VALUE);
        PROJECTILE_RESIST = BUILDER.comment("弹射物伤害额外减免 (0.5 = 50%，叠加到基础减伤)").defineInRange("projectileResist", 0.5, 0.0, Double.MAX_VALUE);
        MAGIC_RESIST = BUILDER.comment("魔法伤害额外减免 (0.3 = 30%，叠加到基础减伤)").defineInRange("magicResist", 0.3, 0.0, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("生命回复 (Regeneration)");
        TICK_REGEN = BUILDER.comment("每 tick 回复量 (2.0 = 40 HP/s)").defineInRange("tickRegen", 2.0, 0.0, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("被动药水效果 (Passive Potion Effects)");
        NIGHT_VISION = BUILDER.comment("永久夜视").define("nightVision", true);
        WATER_BREATHING = BUILDER.comment("永久水下呼吸").define("waterBreathing", true);
        HASTE_LEVEL = BUILDER.comment("急迫等级 (0=关闭, 1=速度+20%, 2=+40%)").defineInRange("hasteLevel", 1, 0, Integer.MAX_VALUE);
        FIRE_RESISTANCE_POTION = BUILDER.comment("永久抗火药水效果").define("fireResistancePotion", true);
        SATURATION = BUILDER.comment("永久饱和 (不消耗饥饿值)").define("saturation", true);
        PERMANENT_LUCK = BUILDER.comment("永久幸运药水效果").define("permanentLuck", true);
        BUILDER.pop();

        BUILDER.push("机动系统 (Mobility)");
        CREATIVE_FLIGHT = BUILDER.comment("创造飞行 - 双击跳跃起飞").define("creativeFlight", true);
        JUMP_BOOST = BUILDER.comment("跳跃增强 (+100%)").define("jumpBoost", true);
        STEP_ASSIST = BUILDER.comment("自动上坡 - 直接走上一格高方块").define("stepAssist", true);
        SLOW_FALL_GLIDE = BUILDER.comment("缓降滑翔 - 空中潜行时缓慢降落并可前进").define("slowFallGlide", true);
        DOLPHINS_GRACE = BUILDER.comment("海豚的恩惠 - 水中加速游泳").define("dolphinsGrace", true);
        WALL_CLIMB_SPEED = BUILDER.comment("爬墙速度 (0.08=类似蜘蛛，0.2=快速)").defineInRange("wallClimbSpeed", 0.15, 0.0, Double.MAX_VALUE);
        WALL_CLIMB = BUILDER.comment("爬墙 - 贴墙时缓慢上升").define("wallClimb", true);
        LAVA_WALKING = BUILDER.comment("熔岩行走").define("lavaWalking", true);
        WATER_WALKING = BUILDER.comment("水上行走 - 潜行时站在水面").define("waterWalking", true);
        BUILDER.pop();

        BUILDER.push("冲刺 (Dash)");
        DASH = BUILDER.comment("冲刺 - 双击任意移动键全向冲刺").define("dash", true);
        DASH_FORCE = BUILDER.comment("冲刺力度 (越大越远)").defineInRange("dashForce", 1.5, 0.0, Double.MAX_VALUE);
        DASH_INVULN_TICKS = BUILDER.comment("冲刺无敌帧数 (tick，0=无无敌)").defineInRange("dashInvulnTicks", 6, 0, Integer.MAX_VALUE);
        DASH_DAMAGE = BUILDER.comment("冲刺撞击敌人伤害 (0=关闭)").defineInRange("dashDamage", 10.0, 0.0, Double.MAX_VALUE);
        DASH_COOLDOWN = BUILDER.comment("冲刺冷却时间 (tick)").defineInRange("dashCooldown", 20, 0, Integer.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("战斗系统 (Combat)");
        DODGE_CHANCE = BUILDER.comment("闪避概率 (0.2 = 20%，0 = 关闭)").defineInRange("dodgeChance", 0.2, 0.0, Double.MAX_VALUE);
        REFLECT_PROJECTILE_CHANCE = BUILDER.comment("弹射物反射概率 (0.3 = 30%)").defineInRange("reflectProjectileChance", 0.3, 0.0, Double.MAX_VALUE);
        LOW_HP_THRESHOLD = BUILDER.comment("低血量狂暴阈值 (0.3 = 30%)").defineInRange("lowHpThreshold", 0.3, 0.0, Double.MAX_VALUE);
        LOW_HP_DAMAGE_MULT = BUILDER.comment("低血量时伤害倍率 (2.0 = +100%)").defineInRange("lowHpDamageMult", 2.0, 1.0, Double.MAX_VALUE);
        BOSS_DAMAGE_BONUS = BUILDER.comment("对 Boss 生物额外伤害倍率").defineInRange("bossDamageBonus", 0.5, 0.0, Double.MAX_VALUE);
        EXECUTE_THRESHOLD = BUILDER.comment("斩杀阈值 - 敌人血量低于此比例直接秒杀 (0.1 = 10%，0 = 关闭)").defineInRange("executeThreshold", 0.1, 0.0, Double.MAX_VALUE);
        COUNTER_DAMAGE_RATIO = BUILDER.comment("反伤比例 - 受伤时将伤害以此比例反弹 (0.25 = 25%)").defineInRange("counterDamageRatio", 0.25, 0.0, Double.MAX_VALUE);
        COUNTER_INVULN_RESET = BUILDER.comment("反伤时重置攻击者无敌帧 (使反伤立即生效)").define("counterInvulnReset", true);
        AOE_SPLASH_RATIO = BUILDER.comment("攻击命中时的AOE溅射伤害比例 (0.3 = 30%主伤害)").defineInRange("aoeSplashRatio", 0.3, 0.0, Double.MAX_VALUE);
        AOE_SPLASH_RANGE = BUILDER.comment("AOE溅射范围 (格，0=关闭)").defineInRange("aoeSplashRange", 4.0, 0.0, Double.MAX_VALUE);
        EXTRA_HIT_DAMAGE = BUILDER.comment("每击额外附加固定伤害 (0=关闭)").defineInRange("extraHitDamage", 2.0, 0.0, Double.MAX_VALUE);
        EXTRA_HIT_COOLDOWN = BUILDER.comment("额外伤害冷却 (tick，防止高频触发)").defineInRange("extraHitCooldown", 2, 0, Integer.MAX_VALUE);
        GLOW_DAMAGE_BONUS = BUILDER.comment("对发光生物的额外伤害倍率 (0.3 = +30%)").defineInRange("glowDamageBonus", 0.3, 0.0, Double.MAX_VALUE);
        BENEFICIAL_STRIP = BUILDER.comment("攻击时移除敌人有益效果").define("beneficialStrip", true);
        INFINITE_ARROWS = BUILDER.comment("射箭不消耗箭矢").define("infiniteArrows", true);
        IGNITE_CHANCE = BUILDER.comment("攻击点燃概率").defineInRange("igniteChance", 0.3, 0.0, Double.MAX_VALUE);
        IGNITE_DURATION = BUILDER.comment("点燃持续时间 (秒)").defineInRange("igniteDuration", 3, 0, Integer.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("护盾系统 (Shield)");
        SHIELD_PER_SEC = BUILDER.comment("满血时每秒吸收生命 (0 = 关闭)").defineInRange("shieldPerSec", 2.0, 0.0, Double.MAX_VALUE);
        SHIELD_MAX = BUILDER.comment("吸收护盾上限").defineInRange("shieldMax", 20.0, 0.0, Double.MAX_VALUE);
        SHIELD_BREAK_KNOCKBACK = BUILDER.comment("护盾被击破时击退周围怪物范围 (格，0=关闭)").defineInRange("shieldBreakKnockback", 6.0, 0.0, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("速度爆发 (Speed Burst)");
        SPEED_BURST_DURATION = BUILDER.comment("受伤后速度爆发持续 (tick，0=关闭)").defineInRange("speedBurstDuration", 100, 0, Integer.MAX_VALUE);
        SPEED_BURST_LEVEL = BUILDER.comment("速度爆发等级 (0=速度, 1=速度II...)").defineInRange("speedBurstLevel", 2, 0, Integer.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("战斗生存增强 (Enhanced Combat Survival)");
        KNOCKBACK_IMMUNITY = BUILDER.comment("击退免疫 (独立开关，与击退抗性属性独立)").define("knockbackImmunity", true);
        AUTO_POTION = BUILDER.comment("低血自动喝药水 - 生命低于阈值自动使用背包治疗药水").define("autoPotion", true);
        AUTO_POTION_THRESHOLD = BUILDER.comment("自动喝药触发血量比例 (0.3 = 30%)").defineInRange("autoPotionThreshold", 0.3, 0.0, Double.MAX_VALUE);
        LIGHTNING_REFLECT = BUILDER.comment("受伤时连锁闪电反击攻击者").define("lightningReflect", true);
        LIGHTNING_REFLECT_CHANCE = BUILDER.comment("闪电反击概率 (0.3 = 30%)").defineInRange("lightningReflectChance", 0.3, 0.0, Double.MAX_VALUE);
        KILL_CHAIN = BUILDER.comment("击杀连锁 - 击杀怪物时伤害周围同种生物").define("killChain", true);
        KILL_CHAIN_RANGE = BUILDER.comment("击杀连锁范围 (格)").defineInRange("killChainRange", 8.0, 0.0, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("伤害光环 (Damage Aura)");
        DAMAGE_AURA_RANGE = BUILDER.comment("伤害光环范围 (格，0 = 关闭)").defineInRange("damageAuraRange", 6.0, 0.0, Double.MAX_VALUE);
        DAMAGE_AURA_DAMAGE = BUILDER.comment("伤害光环每秒伤害").defineInRange("damageAuraDamage", 4.0, 0.0, Double.MAX_VALUE);
        DAMAGE_AURA_TICK_INTERVAL = BUILDER.comment("伤害光环触发间隔 (tick)").defineInRange("damageAuraTickInterval", 20, 1, Integer.MAX_VALUE);
        DAMAGE_AURA_BOSS_MULT = BUILDER.comment("伤害光环对Boss伤害倍率 (叠加到基础伤害)").defineInRange("damageAuraBossMult", 3.0, 1.0, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("采集强化 (Harvesting)");
        AUTO_SMELT = BUILDER.comment("自动冶炼 - 挖矿直接掉落锭").define("autoSmelt", true);
        VEIN_MINER = BUILDER.comment("连锁挖掘 - 一键挖光同种矿脉").define("veinMiner", true);
        VEIN_MAX_BLOCKS = BUILDER.comment("连锁挖掘最大方块数").defineInRange("veinMaxBlocks", 64, 1, Integer.MAX_VALUE);
        VEIN_USE_TAGS = BUILDER.comment("使用 forge:ores 标签识别矿石 (支持所有模组矿石)").define("veinUseTags", true);
        FORTUNE_BONUS = BUILDER.comment("额外时运等级 (加到工具时运上)").defineInRange("fortuneBonus", 3, 0, Integer.MAX_VALUE);
        LOOTING_BONUS = BUILDER.comment("额外抢夺等级 (加到武器抢夺上)").defineInRange("lootingBonus", 3, 0, Integer.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("生存保障 (Survival)");
        VOID_RESCUE = BUILDER.comment("虚空救援 - 掉入虚空时传送回顶部").define("voidRescue", true);
        UNDYING_TOTEM = BUILDER.comment("自动复活 - 死亡时触发类似不死图腾").define("undyingTotem", true);
        UNDYING_COOLDOWN = BUILDER.comment("复活冷却时间 (秒)").defineInRange("undyingCooldown", 300, 0, Integer.MAX_VALUE);
        KILL_EXPLOSION = BUILDER.comment("击杀时产生不破坏方块的范围爆炸").define("killExplosion", true);
        KILL_EXPLOSION_POWER = BUILDER.comment("击杀爆炸威力").defineInRange("killExplosionPower", 3.0, 0.0, Double.MAX_VALUE);
        SOUL_BIND = BUILDER.comment("灵魂绑定 - 死亡不掉落物品（软死亡：复活时传送到出生点）").define("soulBind", true);
        ENDER_CHEST_REMOTE = BUILDER.comment("便携末影箱 - 潜行+右键空气打开末影箱").define("enderChestRemote", true);
        KILL_HEAL_RATIO = BUILDER.comment("击杀时恢复最大生命值比例").defineInRange("killHealRatio", 0.2, 0.0, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("环境适应 (Adaptation)");
        MAGNET_RANGE = BUILDER.comment("物品磁铁范围 (格，0 = 关闭)").defineInRange("magnetRange", 8.0, 0.0, Double.MAX_VALUE);
        MAGNET_INSTANT_RANGE = BUILDER.comment("物品即时捡取范围 - 此范围内物品直接进入背包 (格)").defineInRange("magnetInstantRange", 2.0, 0.0, Double.MAX_VALUE);
        GLOW_RANGE = BUILDER.comment("生物发光侦测范围 (格，0 = 关闭)").defineInRange("glowRange", 24.0, 0.0, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("光环系统 (Auras)");
        WITHER_AURA_RANGE = BUILDER.comment("凋零光环范围 (格，0 = 关闭)").defineInRange("witherAuraRange", 8.0, 0.0, Double.MAX_VALUE);
        PEACE_AURA_RANGE = BUILDER.comment("和平光环范围 - 范围内不生成敌对生物 (格，0 = 关闭)").defineInRange("peaceAuraRange", 32.0, 0.0, Double.MAX_VALUE);
        GROWTH_AURA_RANGE = BUILDER.comment("生长光环范围 - 加速周围作物生长 (格，0 = 关闭)").defineInRange("growthAuraRange", 12.0, 0.0, Double.MAX_VALUE);
        GROWTH_AURA_CHANCE = BUILDER.comment("生长光环单次骨粉成功率 (0.5 = 50%)").defineInRange("growthAuraChance", 0.5, 0.0, 1.0);
        GROWTH_AURA_MAX_BLOCKS = BUILDER.comment("生长光环每周期最大催熟方块数").defineInRange("growthAuraMaxBlocks", 10, 1, Integer.MAX_VALUE);
        GROWTH_AUTO_REPLANT = BUILDER.comment("自动补种 - 收获成熟作物后自动重新种植").define("growthAutoReplant", true);
        BUILDER.pop();

        BUILDER.push("永怒系统 (Eternal Fury)");
        FURY_MAX_STACKS = BUILDER.comment("永怒最大层数").defineInRange("maxStacks", 20, 1, Integer.MAX_VALUE);
        FURY_DECAY_TICKS = BUILDER.comment("永怒衰减间隔 (tick)").defineInRange("decayTicks", 60, 1, Integer.MAX_VALUE);
        FURY_DAMAGE_PER_STACK = BUILDER.comment("每层永怒伤害加成 (0.05 = 5%)").defineInRange("damagePerStack", 0.05, 0.0, Double.MAX_VALUE);
        FURY_LIFESTEAL = BUILDER.comment("满层永怒生命窃取比例").defineInRange("lifesteal", 0.10, 0.0, Double.MAX_VALUE);
        KILL_FURY_BONUS = BUILDER.comment("击杀额外永怒层数").defineInRange("killFuryBonus", 3, 0, Integer.MAX_VALUE);
        LIFESTEAL_FOOD_RESTORE = BUILDER.comment("生命窃取时恢复饱食度 (每次窃取)").defineInRange("lifestealFoodRestore", 1.0, 0.0, Double.MAX_VALUE);
        LIFESTEAL_SATURATION_RESTORE = BUILDER.comment("生命窃取时恢复饱和度 (每次窃取)").defineInRange("lifestealSaturationRestore", 2.0, 0.0, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("经验 (Experience)");
        XP_MULTIPLIER = BUILDER.comment("经验获取倍率").defineInRange("xpMultiplier", 3.0, 1.0, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("探索便利 (Exploration)");
        AUTO_TORCH = BUILDER.comment("自动火把 - 暗处自动从背包放置火把").define("autoTorch", true);
        AUTO_TORCH_LIGHT_LEVEL = BUILDER.comment("自动火把触发光照等级 (低于此值放置)").defineInRange("autoTorchLightLevel", 5, 0, 15);
        AUTO_DOOR = BUILDER.comment("自动门 - 靠近门自动开关").define("autoDoor", true);
        ORE_HIGHLIGHT = BUILDER.comment("洞穴探险 - 地表以下给周围实体添加发光").define("oreHighlight", true);
        ORE_HIGHLIGHT_RANGE = BUILDER.comment("洞穴探险高亮范围 (格)").defineInRange("oreHighlightRange", 32.0, 0.0, Double.MAX_VALUE);
        AUTO_FISH = BUILDER.comment("自动钓鱼 - 手持鱼竿站在水边自动垂钓").define("autoFish", true);
        AUTO_FISH_INTERVAL = BUILDER.comment("自动钓鱼间隔 (tick，越短越快)").defineInRange("autoFishInterval", 100, 1, Integer.MAX_VALUE);
        AUTO_OPEN_CHEST = BUILDER.comment("宝箱磁铁 - 自动收集附近箱子中的物品").define("autoOpenChest", true);
        AUTO_OPEN_CHEST_RANGE = BUILDER.comment("宝箱磁铁范围 (格，0=关闭)").defineInRange("autoOpenChestRange", 8.0, 0.0, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("机动便利 (Mobility Convenience)");
        AUTO_REFILL = BUILDER.comment("自动补货 - 快捷栏物品用完自动从背包补充").define("autoRefill", true);
        INFINITE_BUCKET = BUILDER.comment("无限水/岩浆桶 - 用完的水桶自动补充").define("infiniteBucket", true);
        INFINITE_BUCKET_LAVA = BUILDER.comment("无限岩浆桶 - 副手空桶自动补充为岩浆桶（需开启无限水/岩浆桶）").define("infiniteBucketLava", true);
        BUILDER.pop();

        BUILDER.push("模组集成 (Mod Integrations)");
        USE_FTB_ULTIMINE = BUILDER.comment("使用 FTB Ultimine 替代自定义连锁挖掘（需安装 ftb-ultimine，功能更强、兼容更好）").define("useFtbUltimine", true);
        FLIGHT_MODE = BUILDER.comment("飞行模式：CREATIVE=创造飞行(悬停)，ELYTRA=Caelus鞘翅飞行(需加速，需 Caelus 模组)，OFF=关闭").defineEnum("flightMode", FlightMode.CREATIVE);
        PEHKUI_SCALE = BUILDER.comment("体型缩放倍率 (1.0=正常, 2.0=两倍大, 0.5=小型，需 Pehkui 模组，0=关闭)").defineInRange("pehkuiScale", 0.0, 0.0, 10.0);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event) { }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) { }
}
