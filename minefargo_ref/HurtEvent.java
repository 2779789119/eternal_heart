package com.inolia_zaicek.mine_fargo.Event;
import com.inolia_zaicek.mine_fargo.Config.MyGoConfig;
import com.inolia_zaicek.mine_fargo.Damage.MyGoDamageType;
import com.inolia_zaicek.mine_fargo.Item.AlexsCaves.ForlornSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.AlexsCaves.PrimitiveSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.AlexsCaves.ToxicSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.Cataclysm.*;
import com.inolia_zaicek.mine_fargo.Item.Create.BrassSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.Create.SturdySheetSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.Goety.Entity.*;
import com.inolia_zaicek.mine_fargo.Item.Goety.Item.EscortSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.Goety.Item.GoetyDarkSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.Goety.Item.OrderAboutSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.IceAndFire.Dragon.*;
import com.inolia_zaicek.mine_fargo.Item.IceAndFire.Entity.HippocampusSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.IceAndFire.Entity.TrollSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.L2.Complements.*;
import com.inolia_zaicek.mine_fargo.Item.L2.Hostility.*;
import com.inolia_zaicek.mine_fargo.Item.LegendaryMonsters.Entity.LavaEaterSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.LegendaryMonsters.Entity.*;
import com.inolia_zaicek.mine_fargo.Item.LegendaryMonsters.Monsters.*;
import com.inolia_zaicek.mine_fargo.Item.MineCraft.Entity.*;
import com.inolia_zaicek.mine_fargo.Item.MineCraft.Nature.EnderSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.MineCraft.Nature.LavaSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.MineCraft.Nature.NetherSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.MineCraft.Nature.*;
import com.inolia_zaicek.mine_fargo.Item.SonsOfSins.*;
import com.inolia_zaicek.mine_fargo.Item.Twilight.*;
import com.inolia_zaicek.mine_fargo.Item.Twilight.TwilightForest.FieryIronSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.Twilight.TwilightForest.FluffyCloudSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.Twilight.TwilightForest.QuestRamSoulStoneItem;
import com.inolia_zaicek.mine_fargo.MineFargo;
import com.inolia_zaicek.mine_fargo.Register.MyGoItemRegister;
import com.inolia_zaicek.mine_fargo.Util.MyGoEntityHelper;
import com.inolia_zaicek.mine_fargo.Util.MyGoUtil;
import static com.inolia_zaicek.mine_fargo.Register.MyGoItemRegister.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.commons.lang3.tuple.Triple;

import java.util.*;

import static com.inolia_zaicek.mine_fargo.Event.AlexsCaves.ACTickEvent.*;
import static com.inolia_zaicek.mine_fargo.Event.DeathAndCloneEvent.*;
import static com.inolia_zaicek.mine_fargo.Event.TickEvent.*;
import static com.inolia_zaicek.mine_fargo.Register.MyGoItemRegister.*;
import static net.minecraft.tags.DamageTypeTags.*;

public class HurtEvent {
    public static final List<Triple<MobEffect, Integer, Boolean>> EFFECTS = new ArrayList<>();
    public static void initEffects() {
        //Effect, duration, scale duration instead of amplifier (for like Fire Resistance)
        EFFECTS.add(Triple.of(MobEffects.MOVEMENT_SPEED, 60*20, false));
        EFFECTS.add(Triple.of(MobEffects.DIG_SPEED, 60*20, false));
        EFFECTS.add(Triple.of(MobEffects.DAMAGE_BOOST, 60*20, false));
        EFFECTS.add(Triple.of(MobEffects.REGENERATION, 10*20, false));
        EFFECTS.add(Triple.of(MobEffects.DAMAGE_RESISTANCE, 60*20, false));
        EFFECTS.add(Triple.of(MobEffects.FIRE_RESISTANCE, 60*20, true));
        EFFECTS.add(Triple.of(MobEffects.WATER_BREATHING, 60*20, true));
        EFFECTS.add(Triple.of(MobEffects.INVISIBILITY, 60*20, true));
        EFFECTS.add(Triple.of(MobEffects.ABSORPTION, 60*20, false));
        EFFECTS.add(Triple.of(MobEffects.LUCK, 60*20, false));
    }
    static {initEffects();}
    public static final UUID uuid1 = UUID.fromString("1718C426-F3F8-50B7-7F97-4C83C4537EF9");
    public static final UUID uuid2 = UUID.fromString("FB637D10-4240-B77D-B699-C9E50DF0EBFA");
    public static final String ender_guardian_soul_stone_cooldown_time = MineFargo.MODID + ":ender_guardian_soul_stone";
    public static final String maledictus_soul_stone_cooldown_time = MineFargo.MODID + ":maledictus_soul_stone";
    public static final String ur_ghast_soul_stone_time = MineFargo.MODID + ":ur_ghast_soul_stone";
    public static final String alpha_yeti_soul_stone_cooldown_time = MineFargo.MODID + ":alpha_yeti_soul_stone";
    public static final String snow_queen_soul_stone_cooldown_time = MineFargo.MODID + ":snow_queen_soul_stone";
    public static final String apostle_soul_stone_cooldown = MineFargo.MODID + ":apostle_soul_stone";
    public static final String iaf_dragon_steel_time = MineFargo.MODID + ":iaf_dragon_steel_time";
    public static final String zone_hostility_soul_stone = MineFargo.MODID + ":zone_hostility_soul_stone";
    public static final String undying_soul_stone = MineFargo.MODID + ":undying_soul_stone";
    // 数据结构，保存每个实体的适应信息（伤害类型及次数，顺序保存）
    public static final Map<LivingEntity, HurtEvent.Data> ENTITY_ADAPT_DATA = Collections.synchronizedMap(new WeakHashMap<>());

    @SubscribeEvent
    public static void hurt(LivingHurtEvent event) {
        LivingEntity attacked = event.getEntity();
        Random random = new Random();
        if (attacked != null) {
            Set<Item> attackedCurios = MyGoUtil.getCuriosItems(attacked);
            boolean canceled = false;
            double baseDamage = event.getAmount();
            //减伤采用乘算，保证稀释
            double number = 1;
            double overNumber = 1;
            float fixedNumber = 0;
            double invulnerableTime = 1;
            //限伤【最大生命值*该数额】
            double xianShang = 1;
            //莱特兰
            if (ModList.get().isLoaded("l2hostility")) {
                //禁域随从减伤
                if (attacked instanceof OwnableEntity ownableEntity && ownableEntity.getOwner() != null) {
                    Set<Item> ownerCurios = MyGoUtil.getCuriosItems(ownableEntity.getOwner());
                    if (MyGoUtil.hasL2Hostility(ownerCurios,ownableEntity.getOwner(), AquaHostilitySoulStone.get())) {
                        number *= 1 - MyGoConfig.aqua_hostility_soul_stone_own_armor.get();
                    }
                }
                /*
                //适应
                if (MyGoUtil.hasL2Hostility(attackedCurios,attacked, BodyHostilitySoulStone.get()) ) {
                    // 获得伤害来源的唯一标识（类似MsgId）
                    String id = event.getSource().getMsgId();
                    // 获取或创建该实体的适应数据
                    HurtEvent.Data data = ENTITY_ADAPT_DATA.computeIfAbsent(attacked, k -> new HurtEvent.Data());
                    synchronized (data) {
                        if (data.memory.contains(id)) {
                            // 如果之前打过这个伤害类型，放到最前面
                            data.memory.remove(id);
                            data.memory.add(0, id);
                            // 适应次数加1
                            int count = data.adaption.compute(id, (k, old) -> old == null ? 1 : old + 1);
                            // 计算减伤系数【计算 MyGoConfig.body_hostility_soul_stone_adaptive_re.get() 的 (count - 1) 次方】
                            double factor = Math.pow(MyGoConfig.body_hostility_soul_stone_adaptive_re.get(), count - 1);
                            // 调整事件伤害，实现伤害减免，注意要乘以原伤害【factor就《1】
                            //float newDamage = (float) (event.getAmount() * factor);
                            overNumber *= Math.max(1 - MyGoConfig.body_hostility_soul_stone_adaptive_max_re.get(), factor);
                        } else {
                            // 第一次收到这个伤害类型，加入记忆
                            data.memory.add(0, id);
                            data.adaption.put(id, 1);
                            // 超出最大记忆数时移除最旧的记忆及对应记录
                            if (data.memory.size() > (int) (MyGoConfig.body_hostility_soul_stone_adaptive_number.get() * 1)) {
                                String old = data.memory.remove(data.memory.size() - 1);
                                data.adaption.remove(old);
                            }
                        }
                    }
                }
                */
            }
            //迎战
            /*
            if (ModList.get().isLoaded("meetyourfight")) {
                //赌注
                if (MyGoUtil.hasMeetFight(attacked, AnteSoulStone.get()) ) {
                    double chance = MyGoConfig.ante_soul_stone_base_chance.get();
                    if(attacked.getAttributes().hasAttribute(Attributes.LUCK)) {
                        float luck = (float) attacked.getAttributeValue(Attributes.LUCK);
                        if (luck >= 0 && chance < 0.5)
                            chance = (1.0 + luck) / ((1.0 / MyGoConfig.ante_soul_stone_base_chance.get()) + 2 * luck);
                        else if (luck < 0)
                            chance = 1.0 / ((1.0 / MyGoConfig.ante_soul_stone_base_chance.get()) - 3 * luck);
                    }
                    if (random.nextDouble() <= chance) {
                        number *= 1 - MyGoConfig.ante_soul_stone_resi.get();
                    }
                }
            }
             */
            //法伤减免
            if (ModList.get().isLoaded("goety")) {
                if (MyGoUtil.hasGoetyEntity(attackedCurios,attacked, ApostleSoulStone.get())) {
                    //限伤设置：无法超过
                    xianShang = Math.min(1, MyGoConfig.apostle_soul_stone_damage_restriction.get());
                }
            }
            //机械动力
            if (ModList.get().isLoaded("create")) {
                if (MyGoUtil.hasCreate(attackedCurios,attacked, SturdySheetSoulStone.get())) {
                    fixedNumber -= MyGoConfig.sturdy_sheet_soul_stone_damage.get();
                }
                //机械动力伤害
                if (event.getSource().type().msgId().equals(new ResourceLocation("create", "crush"))
                        || event.getSource().type().msgId().equals(new ResourceLocation("create", "cuckoo_surprise"))
                        || event.getSource().type().msgId().equals(new ResourceLocation("create", "fan_fire"))
                        || event.getSource().type().msgId().equals(new ResourceLocation("create", "fan_lava"))
                        || event.getSource().type().msgId().equals(new ResourceLocation("create", "mechanical_drill"))
                        || event.getSource().type().msgId().equals(new ResourceLocation("create", "mechanical_roller"))
                        || event.getSource().type().msgId().equals(new ResourceLocation("create", "mechanical_saw"))
                        || event.getSource().type().msgId().equals(new ResourceLocation("create", "potato_cannon"))
                        || event.getSource().type().msgId().equals(new ResourceLocation("create", "run_over"))
                ) {
                    if (MyGoUtil.hasCreate(attackedCurios,attacked, BrassSoulStone.get())) {
                        number *= 1 - MyGoConfig.brass_soul_stone_damage.get();
                    }
                }
            }
            //灵灾
            if (ModList.get().isLoaded("malum")) {
                /*
                //镰锋
                if(MyGoUtil.hasMalum(attackedCurios,attacked, ScytheSoulStone.get()) ) {
                    if (attacked.hasEffect(Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("malum", "wicked_intent"))))) {
                        //邪恶意图
                        MobEffectInstance effect1 = attacked.getEffect(
                                Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("malum", "wicked_intent")))
                        );
                        if (effect1 != null) {
                            MyGoEntityHelper.amplifyEffect(effect1, attacked, (int) (MyGoConfig.scythe_soul_stone_lvl.get() * 1), (int) (MyGoConfig.scythe_soul_stone_max_lvl.get() - 1));
                            MyGoEntityHelper.extendEffect(effect1, attacked, (int) (MyGoConfig.scythe_soul_stone_time.get() * 20), (int) (MyGoConfig.scythe_soul_stone_time.get() * 20));
                        }
                    }else{
                        attacked.addEffect(new MobEffectInstance(Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("malum", "wicked_intent")))
                                ,(int)(MyGoConfig.scythe_soul_stone_time.get()*20),(int)(MyGoConfig.scythe_soul_stone_lvl.get()-1)
                        ));
                    }
                }
                //污秽魂石buff
                if (MyGoUtil.hasMalum(attackedCurios,attacked, TaintedSoulStone.get())&&random.nextInt(100)<MyGoConfig.tainted_soul_stone_buff_chance.get()*100) {
                    attacked.addEffect(new MobEffectInstance(
                            Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("malum", "reactive_shielding")))
                            ,(int)(MyGoConfig.tainted_soul_stone_buff_time.get()*20),(int)(MyGoConfig.tainted_soul_stone_buff_lvl.get()-1)
                    ));
                }
                if (MyGoUtil.hasMalum(attackedCurios,attacked, VoidTabletSoulStone.get())){
                    //静默
                    if(event.getSource().getEntity() instanceof LivingEntity attacker) {
                        attacker.addEffect(new MobEffectInstance(Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(
                                new ResourceLocation("malum", "silenced")))
                                , (int) (MyGoConfig.void_tablet_soul_stone_hit_time.get() * 20), (int)(MyGoConfig.void_tablet_soul_stone_hit_lvl.get()-1) ));
                        if (!EntityType.getKey(attacker.getType()).toString().equals("eeeabsmobs:immortal") && !attacker.hasEffect(
                                Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(
                                        "malum", "silenced")))
                        )) {
                            //没buff强上
                            var map = attacker.getActiveEffectsMap();
                            map.put(
                                    Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(
                                            "malum", "silenced")))
                                    , new MobEffectInstance(Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(
                                            new ResourceLocation("malum", "silenced")))
                                            , (int) (MyGoConfig.void_tablet_soul_stone_hit_time.get() * 20), (int)(MyGoConfig.void_tablet_soul_stone_hit_lvl.get()-1) ));
                        }
                        //有buff
                        if(attacker.hasEffect(Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("malum", "silenced"))))){
                            MobEffectInstance effect = attacker.getEffect(
                                    Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("malum", "silenced")))
                            );
                            if (effect != null) {
                            MyGoEntityHelper.amplifyEffect(effect,attacker,(int)(MyGoConfig.void_tablet_soul_stone_hit_lvl.get()*1),(int)(MyGoConfig.void_tablet_soul_stone_hit_lvl_max.get()-1));
                            MyGoEntityHelper.extendEffect(effect,attacker,(int) (MyGoConfig.void_tablet_soul_stone_hit_time.get() * 20),(int)(MyGoConfig.void_tablet_soul_stone_hit_time_max.get()*20));
                            }
                        }
                    }
                    if(event.getSource().is(IS_FIRE) || attacked.getRemainingFireTicks()>0 ){
                        number *= 1-MyGoConfig.void_tablet_soul_stone_hit.get();
                    }
                }
                 */
            }
            //诡厄
            if (ModList.get().isLoaded("goety")) {
                //随从减伤
                if (attacked instanceof OwnableEntity ownableEntity && ownableEntity.getOwner() != null) {
                    LivingEntity owner = ownableEntity.getOwner();
                    Set<Item> ownerCurios = MyGoUtil.getCuriosItems(owner);
                    if (MyGoUtil.hasGoetyItem(ownerCurios,owner, EscortSoulStone.get())) {
                        number *= 1 - MyGoConfig.escort_soul_stone.get();
                    }
                }
                if (MyGoUtil.hasGoetyItem(attackedCurios,attacked, GoetyDarkSoulStone.get())) {
                    if (event.getSource().is(IS_FIRE) || event.getSource().is(IS_EXPLOSION)) {
                        number *= 1 - MyGoConfig.goety_dark_soul_stone_fire.get();
                    } else if (event.getSource().is(WITCH_RESISTANT_TO)) {
                        number *= 1 - MyGoConfig.goety_dark_soul_stone_magic.get();
                    }
                }
            }
            if (invulnerableTime != 1) {
                double finishTime = Math.max(0, invulnerableTime);
                attacked.invulnerableTime = (int) (attacked.invulnerableTime * finishTime);
            }
            //结算
            //冰火
            /*
            if (ModList.get().isLoaded("iceandfire")) {
                if (event.getSource().getEntity() instanceof LivingEntity attacker) {
                    if (EntityType.getKey(attacker.getType()).toString().equals("iceandfire:fire_dragon")
                            || EntityType.getKey(attacker.getType()).toString().equals("iceandfire:ice_dragon")
                            || EntityType.getKey(attacker.getType()).toString().equals("iceandfire:lightning_dragon_blood")) {
                        if (MyGoUtil.hasIAFDragon(attackedCurios,attacked, DragonBoneSoulStone.get())) {
                            fixedNumber -= MyGoConfig.dragon_bone_soul_stone_fixed.get();
                        }
                        double dragonDamageDown = 0;
                        //龙钢减伤
                        if (MyGoUtil.hasIAFDragon(attackedCurios,attacked, FireDragonSteelSoulStone.get())) {
                            dragonDamageDown += MyGoConfig.fire_dragon_steel_soul_stone_dragon.get();
                        }
                        if (MyGoUtil.hasIAFDragon(attackedCurios,attacked, IceDragonSteelSoulStone.get())) {
                            dragonDamageDown += MyGoConfig.ice_dragon_steel_soul_stone_dragon.get();
                        }
                        if (MyGoUtil.hasIAFDragon(attackedCurios,attacked, LightningDragonSteelSoulStone.get())) {
                            dragonDamageDown += MyGoConfig.lightning_dragon_steel_soul_stone_dragon.get();
                        }
                        if (dragonDamageDown > 0) {
                            overNumber *= 1 - dragonDamageDown;
                        }
                    }
                }
            }
            if (ModList.get().isLoaded("enigmaticlegacy")) {
                if (MyGoUtil.hasEnigmaticLegacy(attackedCurios,attacked, EtheriumSoulStone.get())) {
                    number *= 1 - MyGoConfig.etherium_soul_stone_damage.get();
                }
            }
             */
            if (ModList.get().isLoaded("sons_of_sins")) {
                if (MyGoUtil.hasSonsOfSins(attackedCurios,attacked, GreedSinsSoulStone.get()) && event.getSource().is(DamageTypes.IN_WALL)) {
                    number *= 1 - MyGoConfig.greed_sin_soul_stone_down.get();
                }
                if (MyGoUtil.hasSonsOfSins(attackedCurios,attacked, LustSinsSoulStone.get()) && event.getSource().is(IS_PROJECTILE)) {
                    number *= 1 - MyGoConfig.lust_sin_soul_stone_down.get();
                }
            }
            if (baseDamage + fixedNumber <= 0 || number <= 0 || overNumber <= 0 || ((baseDamage * number + fixedNumber) * overNumber) <= 0) {
                canceled = true;
            }
            if (canceled) {
                event.setCanceled(true);
            } else {
                float damage = (float) Math.max(0, (baseDamage * number + fixedNumber) * overNumber);
                //限伤不为1且伤害大于限伤数额
                if (xianShang < 1 && damage > (attacked.getMaxHealth() * xianShang)) {
                    damage = (float) (attacked.getMaxHealth() * xianShang);
                }
                if (damage <= 0) {
                    event.setCanceled(true);
                } else {
                    //反伤
                    if (event.getSource().getEntity() instanceof LivingEntity attacker) {
                        //莱特兰
                        if (ModList.get().isLoaded("l2hostility")) {
                            //疆域反伤
                            if (MyGoUtil.hasL2Hostility(attackedCurios,attacked, ZoneHostilitySoulStone.get()) && attacker.getPersistentData().getInt(zone_hostility_soul_stone) == 0) {
                                attacker.getPersistentData().putInt(zone_hostility_soul_stone, (int) (0.05 * 20 * 2));
                                attacker.invulnerableTime = 0;
                                attacker.hurt(MyGoDamageType.hasSource(attacked.level(), MyGoDamageType.TickMagicDamage, attacked),
                                        (float) (damage * MyGoConfig.zone_hostility_soul_stone_damage.get()));
                            }
                        }
                        if (ModList.get().isLoaded("legendary_monsters")) {
                            if (MyGoUtil.hasLegendaryMonsters(attackedCurios,attacked, AncientGuardianSoulStone.get()) && attacked.getPersistentData().getInt(ancient_guardian_soul_stone_time) == 0 && attacker instanceof LivingEntity) {
                                attacked.getPersistentData().putInt(ancient_guardian_soul_stone_time, (int) (MyGoConfig.ancient_guardian_soul_stone_cooldown.get() * 20 * 2));
                                var DamageType = MyGoDamageType.hasSource(attacked.level(), MyGoDamageType.TRUEDAMAGE, attacked);
                                attacker.invulnerableTime = 0;
                                attacker.hurt(DamageType, (float) (MyGoConfig.ancient_guardian_soul_stone_fixed_damage.get() +
                                        damage * MyGoConfig.ancient_guardian_soul_stone_damage.get()));
                                if (random.nextInt(100) <= MyGoConfig.ancient_guardian_soul_stone_chance.get() * 100) {
                                    attacker.addEffect(new MobEffectInstance(
                                            Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("legendary_monsters", "bleeding")))
                                            , (int) (MyGoConfig.ancient_guardian_soul_stone_time.get() * 20), 0));
                                    if (!EntityType.getKey(attacker.getType()).toString().equals("eeeabsmobs:immortal") &&  !attacker.hasEffect(
                                            Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("legendary_monsters", "bleeding")))
                                    )) {
                                        var map = attacker.getActiveEffectsMap();
                                        map.put(
                                                Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("legendary_monsters", "bleeding")))
                                                , new MobEffectInstance(Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("legendary_monsters", "bleeding"))), (int) (MyGoConfig.ancient_guardian_soul_stone_time.get() * 20), 0));
                                    }
                                }
                            }
                        }
                    }
                    /*
                    //迎战
                    if (ModList.get().isLoaded("meetyourfight")) {
                        if (MyGoUtil.hasMeetFight(attacked, FossilSoulStone.get())) {
                            double treshold = attacked.getMaxHealth() * MyGoConfig.fossil_soul_stone_hp.get();
                            if (damage > treshold) {
                                damage=(float) ((damage - treshold) * MyGoConfig.fossil_soul_stone_resi.get() + treshold);
                            }
                        }
                    }
                    //结束
                    event.setAmount(damage);
                     */
                }
            }
        }
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacked != null) {
            Set<Item> attackerCurios = MyGoUtil.getCuriosItems(attacker);
            //普通乘区内，150%是+0.5，即+0.5
            double number = 1;
            double overNumber = 1;
            double fixedNumber = 0;
            float time = 1;
            double fireTime = 0;
            var map = attacked.getActiveEffectsMap();
            //随从锁定
            boolean orderOwnable = false;
            //灵灾
            if (ModList.get().isLoaded("malum")) {
                /*
                //污秽魂石增伤
                if (MyGoUtil.hasMalum(attackerCurios, attacker, TaintedSoulStone.get())) {
                    final RandomSource randomSource = attacker.getRandom();
                    number += Mth.nextDouble(randomSource,
                            MyGoConfig.tainted_soul_stone_min_damage.get(), MyGoConfig.tainted_soul_stone_max_damage.get());
                    if (random.nextFloat() < MyGoConfig.tainted_soul_stone_damage_up_chance.get()) {
                        number += MyGoConfig.tainted_soul_stone_damage_up_number.get();
                    }
                }
                //饥馁魂石【暴食
                if (MyGoUtil.hasMalum(attackerCurios, attacker, StarvedSoulStone.get()) && (attacked.getHealth() / attacked.getMaxHealth() >= MyGoConfig.starved_soul_stone_hp.get())) {
                    if (attacker.hasEffect(
                            Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("malum", "gluttony")))
                    )) {
                        int buffLevel = Objects.requireNonNull(attacker.getEffect(Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(
                                new ResourceLocation("malum", "gluttony"))))).getAmplifier();
                        int buffTime = Objects.requireNonNull(attacker.getEffect(Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(
                                new ResourceLocation("malum", "gluttony"))))).getDuration();
                        attacker.addEffect(new MobEffectInstance(
                                Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("malum", "gluttony")))
                                , (int) (Math.min((MyGoConfig.starved_soul_stone_max_time.get() * 20), (MyGoConfig.starved_soul_stone_time.get() * 20 + buffTime))),
                                (int) (Math.min((MyGoConfig.starved_soul_stone_max_lvl.get() - 1), (MyGoConfig.starved_soul_stone_lvl.get() + buffLevel)))
                        ));
                    } else {
                        attacker.addEffect(new MobEffectInstance(
                                Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("malum", "gluttony")))
                                , (int) (MyGoConfig.starved_soul_stone_time.get() * 20), (int) (MyGoConfig.starved_soul_stone_lvl.get() - 1)
                        ));
                    }
                }
                //奥能
                if (MyGoUtil.hasMalum(attackerCurios, attacker, ArcanaSoulStone.get()) && (attacked.getHealth() / attacked.getMaxHealth() >= MyGoConfig.arcana_soul_stone_hp.get())) {
                    //奥术回响
                    if (attacker.hasEffect(Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("malum", "echoing_arcana"))))) {
                        MobEffectInstance effect1 = attacker.getEffect(
                                Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("malum", "echoing_arcana")))
                        );
                        if (effect1 != null) {
                            MyGoEntityHelper.amplifyEffect(effect1, attacker, (int) (MyGoConfig.arcana_soul_stone_lvl.get() * 1), (int) (MyGoConfig.arcana_soul_stone_lvl_max.get() - 1));
                            MyGoEntityHelper.extendEffect(effect1, attacker, (int) (MyGoConfig.arcana_soul_stone_time.get() * 20), (int) (MyGoConfig.arcana_soul_stone_time_max.get() * 20));
                        }
                    } else {
                        attacker.addEffect(new MobEffectInstance(Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("malum", "echoing_arcana")))
                                , (int) (MyGoConfig.arcana_soul_stone_time.get() * 20), (int) (MyGoConfig.arcana_soul_stone_lvl.get() - 1)
                        ));
                    }
                    //瘤态增生
                    if (attacker.hasEffect(Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("malum", "cancerous_growth"))))) {
                        MobEffectInstance effect2 = attacker.getEffect(
                                Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("malum", "cancerous_growth")))
                        );
                        if (effect2 != null) {
                            MyGoEntityHelper.amplifyEffect(effect2, attacker, (int) (MyGoConfig.arcana_soul_stone_lvl.get() * 1), (int) (MyGoConfig.arcana_soul_stone_lvl_max.get() - 1));
                            MyGoEntityHelper.extendEffect(effect2, attacker, (int) (MyGoConfig.arcana_soul_stone_time.get() * 20), (int) (MyGoConfig.arcana_soul_stone_time_max.get() * 20));
                        }
                    } else {
                        attacker.addEffect(new MobEffectInstance(Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("malum", "cancerous_growth")))
                                , (int) (MyGoConfig.arcana_soul_stone_time.get() * 20), (int) (MyGoConfig.arcana_soul_stone_lvl.get() - 1)
                        ));
                    }
                }
                 */
            }
            //莱特兰
            if (ModList.get().isLoaded("l2hostility")) {
                //禁域随从增伤
                if (attacker instanceof OwnableEntity ownableEntity && ownableEntity.getOwner() != null) {
                    Set<Item> ownerCurios = MyGoUtil.getCuriosItems(ownableEntity.getOwner());
                    if (MyGoUtil.hasL2Hostility(ownerCurios, ownableEntity.getOwner(), AquaHostilitySoulStone.get())) {
                        number += MyGoConfig.aqua_hostility_soul_stone_own_damage.get();
                    }
                }
                /*
                //腐蚀
                if (MyGoUtil.hasL2Hostility(attackerCurios, attacker, CorrosionHostilitySoulStone.get()) && attacked != null) {
                    int corrosionTime = (int) (20 * MyGoConfig.corrosion_hostility_soul_stone_time.get());
                    //虚弱
                    MyGoUtil.addEffect(attacked,MobEffects.WEAKNESS,corrosionTime,
                            (int) (MyGoConfig.corrosion_hostility_soul_stone_weakness.get() - 1) );
                    //缓慢
                    MyGoUtil.addEffect(attacked,MobEffects.MOVEMENT_SLOWDOWN,corrosionTime,
                            (int) (MyGoConfig.corrosion_hostility_soul_stone_slowness.get() - 1) );
                    //中毒
                    MyGoUtil.addEffect(attacked,MobEffects.POISON,corrosionTime,
                            (int) (MyGoConfig.corrosion_hostility_soul_stone_poison.get() - 1) );
                    //凋零
                    MyGoUtil.addEffect(attacked,MobEffects.WITHER,corrosionTime,
                            (int) (MyGoConfig.corrosion_hostility_soul_stone_wither.get() - 1) );
                    //魂火
                    MyGoUtil.addEffect(attacked,
                            Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("l2complements", "flame")))
                            ,corrosionTime, (int) (MyGoConfig.corrosion_hostility_soul_stone_flame.get() - 1) );

                    //寒流
                    MyGoUtil.addEffect(attacked,
                            Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("l2complements", "frozen")))
                            ,corrosionTime, (int) (MyGoConfig.corrosion_hostility_soul_stone_flame.get() - 1) );
                    //重力
                    if(MyGoConfig.gravity.get()) {
                        MyGoUtil.addEffect(attacked,
                                Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("l2complements", "gravity")))
                                , corrosionTime, (int) (MyGoConfig.corrosion_hostility_soul_stone_flame.get() - 1));
                    }
                    //诅咒
                    MyGoUtil.addEffect(attacked,
                            Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("l2complements", "curse")))
                            ,corrosionTime, (int) (MyGoConfig.corrosion_hostility_soul_stone_flame.get() - 1) );
                }
                */
            }
            //七罪
            if (ModList.get().isLoaded("sons_of_sins")) {
                /*
                if (MyGoUtil.hasSonsOfSins(attackerCurios, attacker, EnvySinsSoulStone.get())) {
                    number += MyGoConfig.envy_sin_soul_stone_atk.get();
                }
                if (MyGoUtil.hasSonsOfSins(attackerCurios, attacker, GluttonySinsSoulStone.get())) {
                    int killNumber = attacker.getPersistentData().getInt(gluttony_sin_soul_stone);
                    number += Math.min(MyGoConfig.gluttony_sin_soul_stone_max.get(), killNumber * MyGoConfig.gluttony_sin_soul_stone_kill.get());
                }
                if (MyGoUtil.hasSonsOfSins(attackerCurios, attacker, PrideSinsSoulStone.get())) {
                    if (attacker.getHealth() / attacker.getMaxHealth() > attacked.getHealth() / attacked.getMaxHealth()) {
                        number += MyGoConfig.pride_sin_soul_stone.get();
                    }
                }
                if (MyGoUtil.hasSonsOfSins(attackerCurios, attacker, SlothSinsSoulStone.get())) {
                    MyGoUtil.addEffect(attacked,MobEffects.MOVEMENT_SLOWDOWN,(int) (MyGoConfig.sloth_sin_soul_stone_time.get() * 20),
                            (int) (MyGoConfig.sloth_sin_soul_stone_lvl.get() - 1));
                }
                if (MyGoUtil.hasSonsOfSins(attackerCurios, attacker, WrathSinsSoulStone.get())) {
                    number += MyGoConfig.wrath_sin_soul_stone_damage.get();
                }
                if (MyGoUtil.hasSonsOfSins(attackerCurios, attacker, LustSinsSoulStone.get())) {
                    overArmorPenetration += MyGoConfig.lust_sin_soul_stone_armor_p.get();
                }
                 */
            }
            //冰火
            if (ModList.get().isLoaded("iceandfire")) {
                /*
                if (MyGoUtil.hasIAFEntity(attackerCurios, attacker, HippocampusSoulStone.get())) {
                    MyGoUtil.addEffect(attacked,MobEffects.MOVEMENT_SLOWDOWN,(int) (MyGoConfig.hippocampus_soul_stone_time.get() * 20),
                            (int) (MyGoConfig.hippocampus_soul_stone_lvl1.get() - 1));
                    MyGoUtil.addEffect(attacked,MobEffects.CONFUSION,(int) (MyGoConfig.hippocampus_soul_stone_time.get() * 20),
                            (int) (MyGoConfig.hippocampus_soul_stone_lvl1.get() - 1));
                }
                //龙血
                if (MyGoUtil.hasIAFDragon(attackerCurios, attacker, FireDragonBloodSoulStone.get())) {
                    fireTime += MyGoConfig.fire_dragon_blood_soul_stone_time.get() * 20;
                    if (attacked.getRemainingFireTicks() > 0) {
                        number += MyGoConfig.fire_dragon_blood_soul_stone_damage.get();
                    }
                    if (EntityType.getKey(attacked.getType()).toString().equals("iceandfire:ice_dragon")) {
                        number += MyGoConfig.fire_dragon_blood_soul_stone_dragon.get();
                    }
                }
                if (MyGoUtil.hasIAFDragon(attackerCurios, attacker, DragonBoneSoulStone.get())) {
                    if (EntityType.getKey(attacked.getType()).toString().equals("iceandfire:fire_dragon")
                            || EntityType.getKey(attacked.getType()).toString().equals("iceandfire:ice_dragon")
                            || EntityType.getKey(attacked.getType()).toString().equals("iceandfire:lightning_dragon_blood")) {
                        number += MyGoConfig.dragon_bone_soul_stone_damage.get();
                    }
                    overArmorPenetration += MyGoConfig.dragon_bone_soul_stone_armor_p.get();
                }
                if (MyGoUtil.hasIAFDragon(attackerCurios, attacker, IceDragonBloodSoulStone.get())) {
                    if (EntityType.getKey(attacked.getType()).toString().equals("iceandfire:fire_dragon")) {
                        number += MyGoConfig.fire_dragon_blood_soul_stone_dragon.get();
                    }
                    MyGoUtil.addEffect(attacked,MobEffects.MOVEMENT_SLOWDOWN,(int) (MyGoConfig.ice_dragon_blood_soul_stone_time.get() * 20),
                            (int) (MyGoConfig.ice_dragon_blood_soul_stone_ms_lvl.get() - 1));
                    MyGoUtil.addEffect(attacked,MobEffects.DIG_SLOWDOWN,(int) (MyGoConfig.ice_dragon_blood_soul_stone_time.get() * 20),
                            (int) (MyGoConfig.ice_dragon_blood_soul_stone_dig_lvl.get() - 1));
                }
                if (MyGoUtil.hasIAFDragon(attackerCurios, attacker, LightningDragonBloodSoulStone.get())) {
                    if (EntityType.getKey(attacked.getType()).toString().equals("iceandfire:ice_dragon")
                            || EntityType.getKey(attacked.getType()).toString().equals("iceandfire:fire_dragon")) {
                        number += MyGoConfig.fire_dragon_blood_soul_stone_dragon.get();
                    }
                }
                 */
            }
            if (ModList.get().isLoaded("aquaculture")) {
                if (MyGoUtil.isCurioEquipped(attacker, MyGoItemRegister.NeptuniumSoulStone.get())
                        && (attacker.isUnderWater() || attacker.isInWater() || attacker.isInWaterOrRain() || attacker.isInWaterRainOrBubble())) {
                    number += MyGoConfig.neptunium_soul_stone_damage.get();
                }
            }
            //诡厄
            if (ModList.get().isLoaded("goety")) {
                //随从
                if (attacker instanceof OwnableEntity ownableEntity && ownableEntity.getOwner() != null) {
                    LivingEntity owner = ownableEntity.getOwner();
                    Set<Item> ownerCurios = MyGoUtil.getCuriosItems(owner);
                    if (MyGoUtil.hasGoetyItem(ownerCurios, owner, OrderAboutSoulStone.get())) {
                        number += MyGoConfig.order_about_soul_stone_damage.get();
                    }
                }
            }
                /*
            //迎战
            if (ModList.get().isLoaded("meetyourfight")) {
                //暮光
                if (MyGoUtil.hasMeetFight(attacker, VioletSoulStone.get())) {
                    number += MyGoConfig.violet_soul_stone_damage.get();
                }
                //赌注
                if (MyGoUtil.hasMeetFight(attacker, AnteSoulStone.get()) && attacker.getAttributes().hasAttribute(Attributes.LUCK)) {
                    double chanceDamage = MyGoConfig.ante_soul_stone_base_chance.get();
                    //幸运计算
                    if (attacker.getAttributes().hasAttribute(Attributes.LUCK)) {
                        float luck = (float) attacker.getAttributeValue(Attributes.LUCK);
                        if (luck >= 0)
                            chanceDamage = (1.0 + luck) / ((1.0 / MyGoConfig.ante_soul_stone_base_chance.get()) + luck);
                        else chanceDamage = 1.0 / ((1.0 / MyGoConfig.ante_soul_stone_base_chance.get()) - 3 * luck);
                    }
                    //增伤
                    if (random.nextDouble() <= chanceDamage) {
                        number += MyGoConfig.ante_soul_stone_damage.get();
                    }
                    /// buff部分
                    double chance = MyGoConfig.ante_soul_stone_base_chance.get() * 2;
                    if (attacker.getAttributes().hasAttribute(Attributes.LUCK)) {
                        float luck = (float) attacker.getAttributeValue(Attributes.LUCK);
                        if (luck >= 0)
                            chance = (1.0 + 0.5 * luck) / ((1.0 / MyGoConfig.ante_soul_stone_base_chance.get() * 2) + 0.5 * luck);
                        else chance = 1.0 / ((1.0 / MyGoConfig.ante_soul_stone_base_chance.get() * 2) - luck);
                    }
                    int effectLevel = -1;
                    if (attacker.level().getRandom().nextDouble() <= chance) {
                        effectLevel = 0;
                        for (int i = 0; i < 2; i++) {
                            chance *= 0.5;
                            if (attacker.level().getRandom().nextDouble() <= chance) effectLevel++;
                            else break;
                        }
                    }
                    if (effectLevel >= 0) {
                        Triple<MobEffect, Integer, Boolean> triple = EFFECTS.get(attacker.level().getRandom().nextInt(EFFECTS.size()));
                        int duration = triple.getRight() ? triple.getMiddle() * (1 + effectLevel) : triple.getMiddle();
                        int potency = triple.getRight() ? 0 : effectLevel;
                        attacker.addEffect(new MobEffectInstance(triple.getLeft(), duration, potency, false, false, true));
                    }
                }
            }

                 */
            //结算部分
            float damage = (float) ((event.getAmount() * number + fixedNumber) * overNumber);
            //吸血【最终数值
            double heal = 0;
            /*
            //神秘遗物
            if (ModList.get().isLoaded("enigmaticlegacy")) {
                if (MyGoUtil.hasEnigmaticLegacy(attackerCurios, attacker, AbyssSoulStone.get())) {
                    heal += MyGoConfig.abyss_soul_stone_heal.get() * damage;
                }
            }
             */
            //暮色
            if (ModList.get().isLoaded("twilightforest")) {
                if (MyGoUtil.hasTwilightLich(attackerCurios, attacker, LifedrainSoulStone.get())) {
                    heal += damage * MyGoConfig.lifedrain_soul_stone_heal.get();
                    if (attacker instanceof Player player) {
                        float saturation = player.getFoodData().getSaturationLevel();
                        player.getFoodData().setSaturation((float) Math.min(saturation + damage * MyGoConfig.lifedrain_soul_stone_sat.get(), 20));
                        int food = player.getFoodData().getFoodLevel();
                        player.getFoodData().setFoodLevel((int) Math.min(food + damage * MyGoConfig.lifedrain_soul_stone_food.get(), 20));
                    }
                }
                var DamageType = MyGoDamageType.hasSource(attacker.level(), DamageTypes.FREEZE, attacker);
                if (MyGoUtil.hasTwilight(attackerCurios, attacker, AlphaYetiSoulStone.get()) && attacker.getPersistentData().getInt(alpha_yeti_soul_stone_cooldown_time) == 0) {
                    attacker.getPersistentData().putInt(alpha_yeti_soul_stone_cooldown_time, (int) (MyGoConfig.alpha_yeti_soul_stone_cooldown.get() * 20 * 2));
                    var mobList = MyGoUtil.mobList((int) ((MyGoConfig.alpha_yeti_soul_stone_range.get() + 1) / 2), attacker);
                    for (Mob mobs : mobList) {
                        if (MyGoUtil.canAttack(mobs, attacker)) {
                            mobs.invulnerableTime = 0;
                            mobs.hurt(DamageType, (float) (damage * MyGoConfig.alpha_yeti_soul_stone_damage.get()));
                        }
                    }
                }
                if (MyGoUtil.hasTwilight(attackerCurios, attacker, SnowQueenSoulStone.get()) && attacker.getPersistentData().getInt(snow_queen_soul_stone_cooldown_time) == 0) {
                    attacker.getPersistentData().putInt(snow_queen_soul_stone_cooldown_time, (int) (MyGoConfig.snow_queen_soul_stone_cooldown.get() * 20 * 2));
                    var mobList = MyGoUtil.mobList((int) ((MyGoConfig.snow_queen_soul_stone_range.get() + 1) / 2), attacked);
                    for (Mob mobs : mobList) {
                        if (MyGoUtil.canAttack(mobs, attacker)) {
                            mobs.invulnerableTime = 0;
                            mobs.hurt(DamageType, (float) (damage * MyGoConfig.snow_queen_soul_stone_damage.get()));
                        }
                    }
                }
            }
            if (ModList.get().isLoaded("legendary_monsters")) {
                if (MyGoUtil.hasLegendaryMonsters(attackerCurios, attacker, CloudGolemSoulStone.get()) && attacker.getPersistentData().getInt(cloud_golem_soul_stone_time) == 0 && attacker instanceof LivingEntity) {
                    attacker.getPersistentData().putInt(cloud_golem_soul_stone_time, (int) (MyGoConfig.cloud_golem_soul_stone_cooldown.get() * 20 * 2));
                    var DamageType = MyGoDamageType.hasSource(attacker.level(), DamageTypes.LIGHTNING_BOLT, attacker);
                    attacked.invulnerableTime = 0;
                    attacked.hurt(DamageType, (float) (damage * MyGoConfig.snow_queen_soul_stone_damage.get()));
                }
                if (MyGoUtil.hasLegendaryMonsters(attackerCurios, attacker, FrostbittenGolemSoulStone.get()) && attacker.getPersistentData().getInt(frostbitten_golem_soul_stone_time) == 0) {
                    var DamageType = MyGoDamageType.hasSource(attacker.level(), DamageTypes.FREEZE, attacker);
                    attacker.getPersistentData().putInt(frostbitten_golem_soul_stone_time, (int) (MyGoConfig.frostbitten_golem_soul_stone_cooldown.get() * 20 * 2));
                    var mobList = MyGoUtil.mobList((int) ((MyGoConfig.frostbitten_golem_soul_stone_range.get() + 1) / 2), attacker);
                    for (Mob mobs : mobList) {
                        if (MyGoUtil.canAttack(mobs, attacker)) {
                            mobs.invulnerableTime = 0;
                            mobs.hurt(DamageType, (float) (damage * MyGoConfig.frostbitten_golem_soul_stone_damage.get()));
                            MyGoUtil.addEffect(mobs,MobEffects.MOVEMENT_SLOWDOWN,(int) (MyGoConfig.frostbitten_golem_soul_stone_time.get() * 20), (int) (MyGoConfig.frostbitten_golem_soul_stone_level.get() - 1) );
                        }
                    }
                }
            }
            //可着火且目标时间小于最大着火时间
            if (fireTime > 0 && attacked.getRemainingFireTicks() < fireTime) {
                attacked.setRemainingFireTicks((int) fireTime);
            }
            //暮色炽铁烤火结算
            if (ModList.get().isLoaded("twilightforest")) {
                if (MyGoUtil.hasTwilightForest(attackerCurios, attacker, FieryIronSoulStone.get()) && attacked.getRemainingFireTicks() > 0) {
                    heal += (attacked.getRemainingFireTicks() * MyGoConfig.fiery_iron_soul_stone_heal.get()) / 20;
                }
            }
            //莱特兰结算部分
            if (ModList.get().isLoaded("l2hostility")) {
                if (MyGoUtil.hasL2Hostility(attackerCurios, attacker, DestroyHostilitySoulStone.get()) && attacked != null) {
                    List<MobEffect> toRemove = new ArrayList<>();
                    for (MobEffectInstance effect : attacked.getActiveEffects()) {
                        if (effect.getEffect().getCategory().equals(MobEffectCategory.BENEFICIAL) && effect.getEffect() != Objects.requireNonNull(
                                ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("l2hostility", "antibuild")))) {
                            toRemove.add(effect.getEffect());
                        }
                    }
                    for (MobEffect effect : toRemove) {
                        attacked.removeEffect(effect);
                    }
                    if (attacker.getPersistentData().getInt(destroy_hostility_soul_stone) == 0) {
                        attacker.getPersistentData().putInt(destroy_hostility_soul_stone, (int) (0.05 * 20 * 2));
                        //连锁
                        var mobList = MyGoUtil.mobList((int) ((MyGoConfig.destroy_hostility_soul_stone_range.get() + 1) / 2), attacked);
                        for (Mob mobs : mobList) {
                            if (mobs != null && mobs != attacked && mobs != attacker) {
                                //如果这些范围里有人和伤害源一样
                                if (mobs.getType().toString() == attacked.getType().toString() && mobs != attacked && mobs != attacker) {
                                    mobs.invulnerableTime = 0;
                                    mobs.hurt(MyGoDamageType.hasSource(attacker.level(), MyGoDamageType.TRUEDAMAGE, attacker), (float) (damage * MyGoConfig.destroy_hostility_soul_stone_damage.get()));
                                }
                            }
                        }
                    }
                }
            }
            if (ModList.get().isLoaded("alexscaves")) {
                if (MyGoUtil.hasAlexsCaves(attackerCurios, attacker, PrimitiveSoulStone.get()) && attacker.getPersistentData().getInt(primitive_soul_stone_damage) == 0) {
                    attacker.getPersistentData().putInt(primitive_soul_stone_damage, (int) (MyGoConfig.primitive_soul_stone_damage_cooldown.get() * 20 * 2));
                    attacked.invulnerableTime = 0;
                    attacked.hurt(MyGoDamageType.hasSource(attacker.level(), DamageTypes.LAVA, attacker),
                            (float) (damage * MyGoConfig.primitive_soul_stone_damage.get()));
                }
                if (MyGoUtil.hasAlexsCaves(attackerCurios, attacker, PrimitiveSoulStone.get()) && attacker.getPersistentData().getInt(primitive_soul_stone_buff) == 0) {
                    attacker.getPersistentData().putInt(primitive_soul_stone_buff, (int) (MyGoConfig.primitive_soul_stone_cooldown.get() * 20 * 2));

                    MyGoUtil.addEffect(attacked,
                            Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("alexscaves", "stunned")))
                            ,(int) (MyGoConfig.primitive_soul_stone_time.get() * 20),0);
                }

                if (MyGoUtil.hasAlexsCaves(attackerCurios, attacker, ForlornSoulStone.get()) && attacker.getPersistentData().getInt(forlorn_soul_stone) == 0) {
                    attacker.getPersistentData().putInt(forlorn_soul_stone, (int) (MyGoConfig.forlorn_soul_stone_cooldown.get() * 20 * 2));
                    attacked.invulnerableTime = 0;
                    attacked.hurt(MyGoDamageType.hasSource(attacker.level(), DamageTypes.MAGIC, attacker),
                            (float) (damage * MyGoConfig.forlorn_soul_stone_damage.get()));
                }
            }
            //附伤
            if (attacker.getPersistentData().getInt(iaf_dragon_steel_time) == 0) {
                attacker.getPersistentData().putInt(iaf_dragon_steel_time, (int) (0.05 * 20 * 2));
                //迎战
                if (ModList.get().isLoaded("meetyourfight")) {
                    if (MyGoUtil.hasMeetFight(attacker, FossilSoulStone.get())) {
                        var mobList = MyGoUtil.mobList(3, attacked);
                        for (Mob mobs : mobList) {
                            if(MyGoUtil.canAttack(mobs,attacker)) {
                                mobs.invulnerableTime = 0;
                                mobs.hurt(MyGoDamageType.hasSource(attacker.level(), DamageTypes.LAVA, attacker),
                                        (float) (damage * MyGoConfig.fossil_soul_stone_boom.get()));
                            }
                        }
                    }
                }
                //冰火
                if (ModList.get().isLoaded("iceandfire")) {
                    //雷龙血 附伤
                    if (MyGoUtil.hasIAFDragon(attackerCurios, attacker, LightningDragonBloodSoulStone.get())) {
                        attacked.invulnerableTime = 0;
                        attacked.hurt(MyGoDamageType.hasSource(attacker.level(), DamageTypes.LIGHTNING_BOLT, attacker), (float) (damage * MyGoConfig.lightning_dragon_blood_soul_stone_damage.get()));
                    }
                    //三龙钢
                    if (MyGoUtil.hasIAFDragon(attackerCurios, attacker, FireDragonSteelSoulStone.get())) {
                        attacked.invulnerableTime = 0;
                        attacked.hurt(MyGoDamageType.hasSource(attacker.level(), DamageTypes.LAVA, attacker), (float) (damage * MyGoConfig.fire_dragon_steel_soul_stone_damage.get()));
                    }
                    if (MyGoUtil.hasIAFDragon(attackerCurios, attacker, IceDragonSteelSoulStone.get())) {
                        attacked.invulnerableTime = 0;
                        attacked.hurt(MyGoDamageType.hasSource(attacker.level(), DamageTypes.FREEZE, attacker), (float) (damage * MyGoConfig.ice_dragon_steel_soul_stone_damage.get()));
                    }
                    if (MyGoUtil.hasIAFDragon(attackerCurios, attacker, LightningDragonSteelSoulStone.get())) {
                        attacked.invulnerableTime = 0;
                        attacked.hurt(MyGoDamageType.hasSource(attacker.level(), DamageTypes.LIGHTNING_BOLT, attacker), (float) (damage * MyGoConfig.lightning_dragon_steel_soul_stone_damage.get()));
                    }
                }
            }
            if (heal > 0) {
                attacker.heal((float) heal);
            }
            if (orderOwnable) {
                List<Mob> mobList = MyGoUtil.mobList(22, attacker);
                for (Mob mobs : mobList) {
                    //周围有随从、随从的主人是玩家、可以攻击目标————集体转火
                    if (mobs instanceof OwnableEntity ownableEntity && ownableEntity.getOwner() instanceof Player player && mobs.canAttack(attacked)) {
                        if (!mobs.level().isClientSide()) {
                            mobs.setTarget(attacked);
                        }
                    }
                }
            }
            if (time != 1) {
                float finalTime = Math.max(0, time);//保证为0及以上
                attacked.invulnerableTime = (int) (attacked.invulnerableTime * finalTime);
            }
            //结束
            event.setAmount(damage);
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        Optional.of(event.getEntity())
                .map(LivingEntity::getAttributes)
                .filter(manager -> manager.hasAttribute(Attributes.ARMOR))
                .map(manager -> manager.getInstance(Attributes.ARMOR))
                .ifPresent(instance -> instance.removeModifier(uuid1));
        Optional.of(event.getEntity())
                .map(LivingEntity::getAttributes)
                .filter(manager -> manager.hasAttribute(Attributes.ARMOR))
                .map(manager -> manager.getInstance(Attributes.ARMOR))
                .ifPresent(instance -> instance.removeModifier(uuid2));
    }

    //适应代码使用
    // 内部数据类，记录内存（顺序），和适应次数
    public static class Data {
        public final ArrayList<String> memory = new ArrayList<>();
        public final HashMap<String, Integer> adaption = new HashMap<>();
    }
}