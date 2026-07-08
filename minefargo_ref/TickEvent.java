package com.inolia_zaicek.mine_fargo.Event;

import com.inolia_zaicek.mine_fargo.Config.MyGoConfig;
import com.inolia_zaicek.mine_fargo.Damage.MyGoDamageType;
import com.inolia_zaicek.mine_fargo.Item.AlexsCaves.AbyssalChasmSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.AlexsCaves.ForlornSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.AlexsCaves.MagneticSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.AlexsCaves.ToxicSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.Ars.AmethystGolemSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.Ars.ArchwoodSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.Ars.FirenandoSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.Botania.GaiaSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.Goety.Entity.*;
import com.inolia_zaicek.mine_fargo.Item.Goety.Item.LegionSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.IceAndFire.Entity.*;
import com.inolia_zaicek.mine_fargo.Item.L2.Complements.*;
import com.inolia_zaicek.mine_fargo.Item.L2.Hostility.AquaHostilitySoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.L2.Hostility.BodyHostilitySoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.L2.Hostility.UltraHostilitySoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.L2.Hostility.ZoneHostilitySoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.LegendaryMonsters.Entity.*;
import com.inolia_zaicek.mine_fargo.Item.LegendaryMonsters.Monsters.PosessedPaladinSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.MineCraft.Entity.AquaticSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.MineCraft.Nature.ForestSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.MineCraft.Nature.LavaSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.MineCraft.Nature.LushSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.MineCraft.Supernatural.EnchantedGoldenAppleSoulStoneItem;
import com.inolia_zaicek.mine_fargo.Item.MineCraft.Supernatural.*;
import com.inolia_zaicek.mine_fargo.Item.MineCraft.Supernatural.UndyingSoulStoneItem;
import com.inolia_zaicek.mine_fargo.MineFargo;
import com.inolia_zaicek.mine_fargo.Network.Packet.ClientToServerPacket;
import com.inolia_zaicek.mine_fargo.Network.TerraRayChannel;
import com.inolia_zaicek.mine_fargo.Register.Key.MyKeyMappingUtil;
import com.inolia_zaicek.mine_fargo.Register.MyGoItemRegister;
import com.inolia_zaicek.mine_fargo.Util.MyGoUtil;
import static com.inolia_zaicek.mine_fargo.Register.MyGoItemRegister.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

import static com.inolia_zaicek.mine_fargo.Event.HurtEvent.*;
import static com.inolia_zaicek.mine_fargo.Register.MyGoItemRegister.*;

public class TickEvent {
    public static final String zombie_scepter_soul_stone_cooldown_time = MineFargo.MODID + ":zombie_scepter_soul_stone_cooldown";
    public static final String fortification_soul_stone_cooldown_time = MineFargo.MODID + ":fortification_soul_stone_cooldown";
    public static final String zinc_soul_stone_time = MineFargo.MODID + ":zinc_soul_stone";
    public static final String cloud_golem_soul_stone_time = MineFargo.MODID + ":cloud_golem_soul_stone";
    public static final String frostbitten_golem_soul_stone_time = MineFargo.MODID + ":frostbitten_golem_soul_stone";
    public static final String ancient_guardian_soul_stone_time = MineFargo.MODID + ":ancient_guardian_soul_stone";
    public static final String shulker_mimic_soul_stone_time = MineFargo.MODID + ":shulker_mimic_soul_stone";
    public static final String vizier_soul_stone_dead_cooldown = MineFargo.MODID + ":vizier_soul_stone_dead";
    public static final UUID legionUUID = UUID.fromString("FFFD7997-C28B-EBC4-B124-98E0E5D86F2B");
    public static final UUID zone_hostility_soul_stoneUUID = UUID.fromString("1ED4BE55-8249-B0DE-ED52-AAB9CF44402B");
    public static final String redstone_monstrosity_soul_stone_cooldown = MineFargo.MODID + ":redstone_monstrosity_soul_stone";
    public static final String iaf_siren_soul_stone = MineFargo.MODID + ":iaf_siren_soul_stone";
    public static final String destroy_hostility_soul_stone = MineFargo.MODID + ":destroy_hostility_soul_stone";
    public static final String ultra_hostility_soul_stone = MineFargo.MODID + ":ultra_hostility_soul_stone";
    public static final String elementium_soul_stone = MineFargo.MODID + ":elementium_soul_stone";
    public static final String enchanted_golden_apple_soul_stone = MineFargo.MODID + ":enchanted_golden_apple_soul_stone";
    public static final String anchor_soul_stone_time = MineFargo.MODID + ":anchor_soul_stone_time";
    public static final String anchor_soul_stone_time_cooldown = MineFargo.MODID + ":anchor_soul_stone_time_cooldown";
    //锚点记录XYZ
    private static final ResourceLocation X = new ResourceLocation(MineFargo.MODID, "anchor_x");
    private static final ResourceLocation Y = new ResourceLocation(MineFargo.MODID, "anchor_y");
    private static final ResourceLocation Z = new ResourceLocation(MineFargo.MODID, "anchor_z");
    private static final ResourceLocation WORLD = new ResourceLocation(MineFargo.MODID, "anchor_dimension");
    //
    public static final String magnet_soul_stone = MineFargo.MODID + ":magnet_soul_stone";
    public static final String magnet_soul_stone_open = MineFargo.MODID + ":magnet_soul_stone_open";
    //杀戮能力
    public static final String boolean_kill_range_skill = MineFargo.MODID + ":boolean_kill_range_skill";
    public static final String boolean_kill_range_skill_open = MineFargo.MODID + ":boolean_kill_range_skill_open";

    public static final String anchor_soul_stone_open = MineFargo.MODID + ":anchor_soul_stone_open";
    public static final String projectile_tracking_capability = MineFargo.MODID + ":projectile_tracking_capability";
    public static final String projectile_tracking_capability_open = MineFargo.MODID + ":projectile_tracking_capability_open";

    public static final String forlorn_soul_stone_c_to_s = MineFargo.MODID + ":forlorn_soul_stone_c_to_s";
    public static final String boss_heal = MineFargo.MODID + ":boss_heal";
    public static final String drop_cooldown = MineFargo.MODID + ":drop_cooldown";

    public static final String fire_c_to_s = MineFargo.MODID + ":fire_c_to_s";
    public static final String fire_open = MineFargo.MODID + ":fire_open";
    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent event) {
        if(event.getEntity().getPersistentData().getInt(drop_cooldown)>0){
            event.getEntity().getPersistentData().putInt(drop_cooldown,
                    event.getEntity().getPersistentData().getInt(drop_cooldown)-1);
            }
        if (!event.getEntity().isAlive())
            return;
        LivingEntity livingEntity = event.getEntity();
        Set<Item> curios = MyGoUtil.getCuriosItems(livingEntity);
        //水中漂浮
        boolean water = false;
        //弹射物追踪能力开关【服务端
        if(!livingEntity.level().isClientSide()) {
            if (livingEntity.getPersistentData().getInt(projectile_tracking_capability) == 20) {
                livingEntity.level().playSound(null, livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.0F);
                livingEntity.getPersistentData().putInt(projectile_tracking_capability, 0);
                //小于50，设置100表示关闭
                if (!livingEntity.level().isClientSide()) {
                    if (livingEntity.getPersistentData().getInt(projectile_tracking_capability_open) <= 50) {
                        livingEntity.getPersistentData().putInt(projectile_tracking_capability_open, 75);
                    } else {
                        livingEntity.getPersistentData().putInt(projectile_tracking_capability_open, 25);
                    }
                }
            }
        }
        //客户端
        else{
            //客户端按下按钮——发包
            if(MyKeyMappingUtil.KEYMAPPING4.isDown() && livingEntity.getPersistentData().getInt(projectile_tracking_capability) == 0 && livingEntity instanceof Player){
                livingEntity.getPersistentData().putInt(projectile_tracking_capability, 20);
                TerraRayChannel.CHANNEL.sendToServer(new ClientToServerPacket(4));
            }
            //客户端计算内置冷却
            if(livingEntity.getPersistentData().getInt(projectile_tracking_capability)>0){
                livingEntity.getPersistentData().putInt(projectile_tracking_capability, livingEntity.getPersistentData().getInt(projectile_tracking_capability)-1);
            }
        }
        /// 是否开启着火
        if(livingEntity.level().isClientSide()) {
            //按键内置冷却【直接用磁铁的了
            if(livingEntity.getPersistentData().getInt(magnet_soul_stone)>0){
                livingEntity.getPersistentData().putInt(magnet_soul_stone,
                        livingEntity.getPersistentData().getInt(magnet_soul_stone)-1);
            }
            if (livingEntity.getPersistentData().getInt(magnet_soul_stone) == 0 && livingEntity instanceof Player
                    && MyKeyMappingUtil.KEYMAPPING5.isDown()) {
                livingEntity.getPersistentData().putInt(magnet_soul_stone,10);
                // (int)(MyGoConfig.anchor_soul_stone_cooldown.get()*40)
                //发包
                TerraRayChannel.CHANNEL.sendToServer(new ClientToServerPacket(6));
            }
        }else {
            //服务端
            if (livingEntity.getPersistentData().getInt(fire_c_to_s) == 20) {
                livingEntity.level().playSound(null, livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.0F);
                livingEntity.getPersistentData().putInt(fire_c_to_s, 0);
                //小于50，设置100表示关闭
                if (!livingEntity.level().isClientSide()) {
                    if (livingEntity.getPersistentData().getInt(fire_open) <= 50) {
                        livingEntity.getPersistentData().putInt(fire_open, 75);
                    } else {
                        livingEntity.getPersistentData().putInt(fire_open, 25);
                    }
                }
            }
        }
        ///锚定
        //按下按键，客户端发包，开始执行
        if(livingEntity.level().isClientSide()) {
            //锚定按键内置冷却
            if(livingEntity.getPersistentData().getInt(anchor_soul_stone_time_cooldown)>0){
                livingEntity.getPersistentData().putInt(anchor_soul_stone_time_cooldown,
                        livingEntity.getPersistentData().getInt(anchor_soul_stone_time_cooldown)-1);
            }
            if (livingEntity.getPersistentData().getInt(anchor_soul_stone_time_cooldown) == 0 && livingEntity instanceof Player
                    && MyGoUtil.hasSupernatural(curios,livingEntity, AnchorSoulStone.get()) && MyKeyMappingUtil.KEYMAPPING.isDown()) {
                livingEntity.getPersistentData().putInt(anchor_soul_stone_time_cooldown,(int)(MyGoConfig.anchor_soul_stone_cooldown.get()*40));
                // (int)(MyGoConfig.anchor_soul_stone_cooldown.get()*40)
                //发包
                TerraRayChannel.CHANNEL.sendToServer(new ClientToServerPacket(3));
            }
        }
        else {
            //服务端执行操作————为目标标记
            if (livingEntity.getPersistentData().getInt(anchor_soul_stone_open) == 20 && MyGoUtil.hasSupernatural(curios,livingEntity, AnchorSoulStone.get())
                    && livingEntity instanceof Player) {
                livingEntity.getPersistentData().putInt(anchor_soul_stone_open,0);
                livingEntity.level().playSound(null, livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.0F);

                var mobList = MyGoUtil.mobList((int) ((MyGoConfig.anchor_soul_stone_range.get() + 1) / 2), livingEntity);
                for (Mob mobs : mobList) {
                    BlockPos pos = mobs.getOnPos();
                    if (MyGoUtil.canAttack(mobs, livingEntity)) {
                        //记录坐标
                        mobs.getPersistentData().putDouble(X.toString(), pos.getX()); // 使用 ResourceLocation 的 toString() 方法作为 NBT 键
                        mobs.getPersistentData().putDouble(Y.toString(), pos.getY()); // 使用 ResourceLocation 的 toString() 方法作为 NBT 键
                        mobs.getPersistentData().putDouble(Z.toString(), pos.getZ()); // 使用 ResourceLocation 的 toString() 方法作为 NBT 键
                        mobs.getPersistentData().putString(WORLD.toString(), mobs.level().dimension().location().getPath()); // 使用 ResourceLocation 的 toString() 方法作为 NBT 键
                        //计时
                        mobs.getPersistentData().putInt(anchor_soul_stone_time, (int) (MyGoConfig.anchor_soul_stone_time.get() * 20 * 2));
                    }
                }
            }
        }
        if(livingEntity.getPersistentData().getInt(anchor_soul_stone_time)>0){
            livingEntity.getPersistentData().putInt(anchor_soul_stone_time,
                    livingEntity.getPersistentData().getInt(anchor_soul_stone_time)-1);
            //执行传送逻辑
            // 从 NBT 读取坐标和维度
            double savedX = livingEntity.getPersistentData().getDouble(X.toString());
            double savedY = livingEntity.getPersistentData().getDouble(Y.toString());
            double savedZ = livingEntity.getPersistentData().getDouble(Z.toString());
            String savedDimensionPath = livingEntity.getPersistentData().getString(WORLD.toString());
            // 将字符串维度路径转换为 Dimension.ResourceKey
            ResourceLocation dimensionResourceLocation = new ResourceLocation(savedDimensionPath);
            ServerLevel targetLevel = null;
            if (livingEntity.level().getServer() != null) { // 确保 server 不为 null————检查当前代码是否正在游戏服务器端运行
                targetLevel = livingEntity.level().getServer().getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, dimensionResourceLocation));
            }
            if (targetLevel != null) {
                livingEntity.teleportTo(
                        targetLevel,
                        savedX+0.5, savedY+1, savedZ+0.5,//XYZ不用变化，因为是直接记录目标位置的，而不是找方块
                        EnumSet.noneOf(RelativeMovement.class), // 明确指示为绝对传送
                        livingEntity.getYRot(), // 保持玩家的当前 YAW
                        livingEntity.getXRot()  // 保持玩家的当前 PITCH
                );
            }
        }
        //杀戮功能开关【服务端
        if(!livingEntity.level().isClientSide()) {
            if (livingEntity.getPersistentData().getInt(boolean_kill_range_skill) == 20 && livingEntity instanceof Player) {
                livingEntity.level().playSound(null, livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.0F);
                livingEntity.getPersistentData().putInt(boolean_kill_range_skill, 0);
                //小于50，设置100表示关闭
                if (!livingEntity.level().isClientSide()) {
                    if (livingEntity.getPersistentData().getInt(boolean_kill_range_skill_open) <= 50) {
                        livingEntity.getPersistentData().putInt(boolean_kill_range_skill_open, 75);
                    } else {
                        livingEntity.getPersistentData().putInt(boolean_kill_range_skill_open, 25);
                    }
                }
            }
        }
        //客户端
        else{
            //客户端按下按钮——发包
            if(MyKeyMappingUtil.KEYMAPPING3.isDown() && livingEntity.getPersistentData().getInt(boolean_kill_range_skill) == 0 ){
                livingEntity.getPersistentData().putInt(boolean_kill_range_skill, 20);
                TerraRayChannel.CHANNEL.sendToServer(new ClientToServerPacket(1));
            }
            //客户端计算内置冷却
            if(livingEntity.getPersistentData().getInt(boolean_kill_range_skill)>0){
                livingEntity.getPersistentData().putInt(boolean_kill_range_skill, livingEntity.getPersistentData().getInt(boolean_kill_range_skill)-1);
            }
        }
        //磁铁功能开关【服务端
        if(!livingEntity.level().isClientSide()) {
            if (livingEntity.getPersistentData().getInt(magnet_soul_stone) == 20) {
                livingEntity.level().playSound(null, livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.0F);
                livingEntity.getPersistentData().putInt(magnet_soul_stone, 0);
                //小于50，设置100表示关闭
                if (!livingEntity.level().isClientSide()) {
                    if (livingEntity.getPersistentData().getInt(magnet_soul_stone_open) <= 50) {
                        livingEntity.getPersistentData().putInt(magnet_soul_stone_open, 75);
                    } else {
                        livingEntity.getPersistentData().putInt(magnet_soul_stone_open, 25);
                    }
                }
            }
        }
        //客户端
        else{
            //客户端按下按钮——发包
            if(MyKeyMappingUtil.KEYMAPPING2.isDown() && livingEntity.getPersistentData().getInt(magnet_soul_stone) == 0 && livingEntity instanceof Player){
                livingEntity.getPersistentData().putInt(magnet_soul_stone, 20);
                TerraRayChannel.CHANNEL.sendToServer(new ClientToServerPacket(2));
            }
            //客户端计算内置冷却
            if(livingEntity.getPersistentData().getInt(magnet_soul_stone)>0){
                livingEntity.getPersistentData().putInt(magnet_soul_stone, livingEntity.getPersistentData().getInt(magnet_soul_stone)-1);
            }
        }
        //潮水
        if(MyGoUtil.hasSupernatural(curios,livingEntity, TheSeaSoulStone.get())){
            water=true;
            if(livingEntity.getRemainingFireTicks()>0) {
                livingEntity.setRemainingFireTicks(0);
            }
        }

        //以太压血
        /*
        if (ModList.get().isLoaded("enigmaticlegacy")) {
            if (MyGoUtil.hasEnigmaticLegacy(curios,livingEntity, EtheriumSoulStone.get())) {
                if (livingEntity.getHealth() / livingEntity.getMaxHealth() > MyGoConfig.etherium_soul_stone_hp.get()) {
                    livingEntity.setHealth((float) (livingEntity.getMaxHealth() * MyGoConfig.etherium_soul_stone_hp.get()));
                }
            }
            //深渊——凝视
            if (MyGoUtil.hasEnigmaticLegacy(curios,livingEntity, AbyssSoulStone.get())) {
                if(MyGoUtil.getNearestNonFollowerOnPath(livingEntity, MyGoConfig.gorgon_soul_stone_range.get())!=null){
                    LivingEntity mob = MyGoUtil.getNearestNonFollowerOnPath(livingEntity, MyGoConfig.gorgon_soul_stone_range.get());
                    MyGoUtil.addEffect(mob,MobEffects.MOVEMENT_SLOWDOWN,40,(int) (MyGoConfig.abyss_soul_stone_slow.get() - 1));
                    MyGoUtil.addEffect(mob,MobEffects.WEAKNESS,40,(int) (MyGoConfig.abyss_soul_stone_weakness.get() - 1));
                }
            }
        }

        //莱特兰拓展
        if (ModList.get().isLoaded("l2complements")) {
            if (MyGoUtil.hasL2Complements(curios,livingEntity, SculkiumComplementsSoulStone.get()) ) {
            }
        }
        if (ModList.get().isLoaded("meetyourfight")) {
            if (MyGoUtil.hasMeetFight(livingEntity, PhantoplasmSoulStone.get())) {
                var mobList = MyGoUtil.mobList(32, livingEntity);
                for (Mob mobs : mobList) {
                    if ( MyGoUtil.canAttack(mobs,livingEntity)){
                        MyGoUtil.addEffect(mobs,MobEffects.GLOWING,400,0);
                        MyGoUtil.addEffect(mobs,MobEffects.MOVEMENT_SLOWDOWN,400,1);
                    }
                }
            }
        }

         */
        if (ModList.get().isLoaded("alexscaves")) {
            if (MyGoUtil.hasAlexsCaves(curios,livingEntity, AbyssalChasmSoulStone.get())) {
                water = true;
            }
        }
        if (ModList.get().isLoaded("aquaculture")) {
            if (MyGoUtil.isCurioEquipped(livingEntity, MyGoItemRegister.NeptuniumSoulStone.get())) {
                water = true;
            }
        }
        if(water&&MyGoConfig.water.get()) {
            if (livingEntity.isEyeInFluidType((FluidType) ForgeMod.WATER_TYPE.get())&&
                    !livingEntity.isCrouching() && !livingEntity.jumping && !livingEntity.isSwimming()) {
                livingEntity.setDeltaMovement(Vec3.ZERO);
            }
        }
        if(livingEntity.getPersistentData().getInt(elementium_soul_stone)>0){
            livingEntity.getPersistentData().putInt(elementium_soul_stone,
                    livingEntity.getPersistentData().getInt(elementium_soul_stone)-1);
        }
        //莱特兰
        if (ModList.get().isLoaded("l2hostility")) {
            if(livingEntity.getPersistentData().getInt(zone_hostility_soul_stone)>0){
                livingEntity.getPersistentData().putInt(zone_hostility_soul_stone,
                        livingEntity.getPersistentData().getInt(zone_hostility_soul_stone)-1);
            }
            if(livingEntity.getPersistentData().getInt(destroy_hostility_soul_stone)>0){
                livingEntity.getPersistentData().putInt(destroy_hostility_soul_stone,
                        livingEntity.getPersistentData().getInt(destroy_hostility_soul_stone)-1);
            }
            if(livingEntity.getPersistentData().getInt(ultra_hostility_soul_stone)>0){
                livingEntity.getPersistentData().putInt(ultra_hostility_soul_stone,
                        livingEntity.getPersistentData().getInt(ultra_hostility_soul_stone)-1);
            }
        }
        //诡厄，军团
        if (ModList.get().isLoaded("goety")) {
            //随从减伤
            if (livingEntity instanceof OwnableEntity ownableEntity && ownableEntity.getOwner() != null) {
                LivingEntity owner = ownableEntity.getOwner();
                if (MyGoUtil.hasGoetyItem(curios,owner, LegionSoulStone.get())) {
                    //移速
                    if(owner.getAttributes().hasAttribute(Attributes.MOVEMENT_SPEED)) {
                        Optional.of(livingEntity)
                                .map(LivingEntity::getAttributes)
                                .filter(manager -> manager.hasAttribute(Attributes.MOVEMENT_SPEED))
                                .map(manager -> manager.getInstance(Attributes.MOVEMENT_SPEED))
                                .filter(instance -> instance.getModifier(legionUUID) == null)
                                .ifPresent(instance -> instance.addTransientModifier(
                                        new AttributeModifier(legionUUID, "legion_soul_stone",
                                                    owner.getAttributeValue(Attributes.MOVEMENT_SPEED)*MyGoConfig.legion_soul_stone_speed.get(),
                                                AttributeModifier.Operation.ADDITION)));
                    }
                    if(owner.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)) {
                        Optional.of(livingEntity)
                                .map(LivingEntity::getAttributes)
                                .filter(manager -> manager.hasAttribute(Attributes.ATTACK_DAMAGE))
                                .map(manager -> manager.getInstance(Attributes.ATTACK_DAMAGE))
                                .filter(instance -> instance.getModifier(legionUUID) == null)
                                .ifPresent(instance -> instance.addTransientModifier(
                                        new AttributeModifier(legionUUID, "legion_soul_stone",
                                                owner.getAttributeValue(Attributes.ATTACK_DAMAGE)*MyGoConfig.legion_soul_stone_atk.get(),
                                                AttributeModifier.Operation.ADDITION)));
                    }
                    if(owner.getAttributes().hasAttribute(Attributes.MAX_HEALTH)) {
                        Optional.of(livingEntity)
                                .map(LivingEntity::getAttributes)
                                .filter(manager -> manager.hasAttribute(Attributes.MAX_HEALTH))
                                .map(manager -> manager.getInstance(Attributes.MAX_HEALTH))
                                .filter(instance -> instance.getModifier(legionUUID) == null)
                                .ifPresent(instance -> instance.addTransientModifier(
                                        new AttributeModifier(legionUUID, "legion_soul_stone",
                                                owner.getAttributeValue(Attributes.MAX_HEALTH)*MyGoConfig.legion_soul_stone_hp.get(),
                                                AttributeModifier.Operation.ADDITION)));
                    }
                }else{
                    Optional.of(livingEntity)
                            .map(LivingEntity::getAttributes)
                            .filter(manager -> manager.hasAttribute(Attributes.MAX_HEALTH))
                            .map(manager -> manager.getInstance(Attributes.MAX_HEALTH))
                            .ifPresent(instance -> instance.removeModifier(legionUUID));
                    Optional.of(livingEntity)
                            .map(LivingEntity::getAttributes)
                            .filter(manager -> manager.hasAttribute(Attributes.ATTACK_DAMAGE))
                            .map(manager -> manager.getInstance(Attributes.ATTACK_DAMAGE))
                            .ifPresent(instance -> instance.removeModifier(legionUUID));
                    Optional.of(livingEntity)
                            .map(LivingEntity::getAttributes)
                            .filter(manager -> manager.hasAttribute(Attributes.MOVEMENT_SPEED))
                            .map(manager -> manager.getInstance(Attributes.MOVEMENT_SPEED))
                            .ifPresent(instance -> instance.removeModifier(legionUUID));
                }
            }
        }
        if (ModList.get().isLoaded("legendary_monsters")) {
            if(livingEntity.getPersistentData().getInt(cloud_golem_soul_stone_time)>0){
                livingEntity.getPersistentData().putInt(cloud_golem_soul_stone_time,
                        livingEntity.getPersistentData().getInt(cloud_golem_soul_stone_time)-1);
            }
            if(livingEntity.getPersistentData().getInt(frostbitten_golem_soul_stone_time)>0){
                livingEntity.getPersistentData().putInt(frostbitten_golem_soul_stone_time,
                        livingEntity.getPersistentData().getInt(frostbitten_golem_soul_stone_time)-1);
            }
            if(livingEntity.getPersistentData().getInt(ancient_guardian_soul_stone_time)>0){
                livingEntity.getPersistentData().putInt(ancient_guardian_soul_stone_time,
                        livingEntity.getPersistentData().getInt(ancient_guardian_soul_stone_time)-1);
            }
            if(livingEntity.getPersistentData().getInt(shulker_mimic_soul_stone_time)>0){
                livingEntity.getPersistentData().putInt(shulker_mimic_soul_stone_time,
                        livingEntity.getPersistentData().getInt(shulker_mimic_soul_stone_time)-1);
            }
        }
        if(livingEntity.getPersistentData().getInt(ender_guardian_soul_stone_cooldown_time)>0){
            livingEntity.getPersistentData().putInt(ender_guardian_soul_stone_cooldown_time,
                    livingEntity.getPersistentData().getInt(ender_guardian_soul_stone_cooldown_time)-1);
        }

        if(livingEntity.getPersistentData().getInt(maledictus_soul_stone_cooldown_time)>0){
            livingEntity.getPersistentData().putInt(maledictus_soul_stone_cooldown_time,
                    livingEntity.getPersistentData().getInt(maledictus_soul_stone_cooldown_time)-1);
        }
        if(livingEntity.getPersistentData().getInt(undying_soul_stone)>0){
            livingEntity.getPersistentData().putInt(undying_soul_stone,
                    livingEntity.getPersistentData().getInt(undying_soul_stone)-1);
        }
        if(livingEntity.getPersistentData().getInt(apostle_soul_stone_cooldown)>0){
            livingEntity.getPersistentData().putInt(apostle_soul_stone_cooldown,
                    livingEntity.getPersistentData().getInt(apostle_soul_stone_cooldown)-1);
        }
        if(livingEntity.getPersistentData().getInt(iaf_dragon_steel_time)>0){
            livingEntity.getPersistentData().putInt(iaf_dragon_steel_time,
                    livingEntity.getPersistentData().getInt(iaf_dragon_steel_time)-1);
        }
        if(livingEntity.getPersistentData().getInt(redstone_monstrosity_soul_stone_cooldown)>0){
            livingEntity.getPersistentData().putInt(redstone_monstrosity_soul_stone_cooldown,
                    livingEntity.getPersistentData().getInt(redstone_monstrosity_soul_stone_cooldown)-1);
        }
        if(livingEntity.getPersistentData().getInt(iaf_siren_soul_stone)>0){
            livingEntity.getPersistentData().putInt(iaf_siren_soul_stone,
                    livingEntity.getPersistentData().getInt(iaf_siren_soul_stone)-1);
        }
        //灵灾
        if (ModList.get().isLoaded("malum")) {
            /*
            if (MyGoUtil.hasMalum(curios,livingEntity, TaintedSoulStone.get()) && livingEntity.level().getGameTime() % 20L == 0) {
                livingEntity.heal((float) (livingEntity.getMaxHealth()*MyGoConfig.tainted_soul_stone_heal.get()));
            }
            if (MyGoUtil.hasMalum(curios,livingEntity, TotemicSoulStone.get()) && livingEntity.level().getGameTime() % 20L == 0) {
                livingEntity.addEffect(new MobEffectInstance(Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(
                        "malum", "ifrits_embrace"))),200,(int)(MyGoConfig.ifrits_embrace.get()-1)
                ));
                livingEntity.addEffect(new MobEffectInstance(Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(
                        "malum", "earthen_might"))),200,(int)(MyGoConfig.earthen_might.get()-1)
                ));
                livingEntity.addEffect(new MobEffectInstance(Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(
                        "malum", "anglers_lure"))),200,(int)(MyGoConfig.anglers_lure.get()-1)
                ));
                livingEntity.addEffect(new MobEffectInstance(Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(
                        "malum", "aethers_charm"))),200,(int)(MyGoConfig.aethers_charm.get()-1)
                ));
                livingEntity.addEffect(new MobEffectInstance(Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(
                        "malum", "miners_rage"))),200,(int)(MyGoConfig.miners_rage.get()-1)
                ));
                livingEntity.addEffect(new MobEffectInstance(Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(
                        "malum", "gaias_bulwark"))),200,(int)(MyGoConfig.gaias_bulwark.get()-1)
                ));
                livingEntity.addEffect(new MobEffectInstance(Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(
                        "malum", "poseidons_grasp"))),200,(int)(MyGoConfig.poseidons_grasp.get()-1)
                ));
                livingEntity.addEffect(new MobEffectInstance(Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(
                        "malum", "zephyrs_courage"))),200,(int)(MyGoConfig.zephyrs_courage.get()-1)
                ));
            }
             */
        }
        if (ModList.get().isLoaded("twilightforest")) {
            if(livingEntity.getPersistentData().getInt(zombie_scepter_soul_stone_cooldown_time)>0){
                livingEntity.getPersistentData().putInt(zombie_scepter_soul_stone_cooldown_time,
                        livingEntity.getPersistentData().getInt(zombie_scepter_soul_stone_cooldown_time)-1);
            }
            if(livingEntity.getPersistentData().getInt(fortification_soul_stone_cooldown_time)>0){
                livingEntity.getPersistentData().putInt(fortification_soul_stone_cooldown_time,
                        livingEntity.getPersistentData().getInt(fortification_soul_stone_cooldown_time)-1);
            }
            if(livingEntity.getPersistentData().getInt(ur_ghast_soul_stone_time)>0){
                livingEntity.getPersistentData().putInt(ur_ghast_soul_stone_time,
                        livingEntity.getPersistentData().getInt(ur_ghast_soul_stone_time)-1);
            }
            if(livingEntity.getPersistentData().getInt(alpha_yeti_soul_stone_cooldown_time)>0){
                livingEntity.getPersistentData().putInt(alpha_yeti_soul_stone_cooldown_time,
                        livingEntity.getPersistentData().getInt(alpha_yeti_soul_stone_cooldown_time)-1);
            }
            if(livingEntity.getPersistentData().getInt(snow_queen_soul_stone_cooldown_time)>0){
                livingEntity.getPersistentData().putInt(snow_queen_soul_stone_cooldown_time,
                        livingEntity.getPersistentData().getInt(snow_queen_soul_stone_cooldown_time)-1);
            }
            if(livingEntity.getPersistentData().getInt(zinc_soul_stone_time)>0){
                livingEntity.getPersistentData().putInt(zinc_soul_stone_time,
                        livingEntity.getPersistentData().getInt(zinc_soul_stone_time)-1);
            }
            if(livingEntity.getPersistentData().getInt(vizier_soul_stone_dead_cooldown)>0){
                livingEntity.getPersistentData().putInt(vizier_soul_stone_dead_cooldown,
                        livingEntity.getPersistentData().getInt(vizier_soul_stone_dead_cooldown)-1);
            }
        }
        if (livingEntity.level().getGameTime() % 10L == 0) {
            //新生魔艺部分
            if (ModList.get().isLoaded("ars_nouveau")) {
                int shielding = 0;
                //护盾等级分开计算
                if(MyGoUtil.hasArs(curios,livingEntity, AmethystGolemSoulStone.get())){
                    shielding+=(int)(MyGoConfig.amethyst_golem_soul_stone_level.get()*1);
                }
                if(MyGoUtil.hasArs(curios,livingEntity, ArchwoodSoulStone.get())){
                    shielding+=(int)(MyGoConfig.archwood_soul_stone_purple.get()*1);
                }
                if (shielding>0) {
                    livingEntity.addEffect(new MobEffectInstance(
                            Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("ars_nouveau", "shielding"))),
                            300, shielding-1, false, false, false));
                }
            }
        }

        //森林催熟
        if(livingEntity.level().getGameTime() % (20L*MyGoConfig.forest_soul_stone.get()) == 0
               &&  MyGoUtil.hasNature(curios,livingEntity, ForestSoulStone.get())&&livingEntity.isShiftKeyDown()) {
            Level world = livingEntity.level();
            Random random = new Random();
            float rx = random.nextFloat() * 5.0F - 3.0F;
            float rz = random.nextFloat() * 5.0F - 3.0F;
            float ry = random.nextFloat() * 2.0F - 2.0F;
            float ry0 = random.nextFloat() * 2.0F;
            if (world instanceof ServerLevel serverLevel) { // 如果是服务器端，安全地转换为 ServerLevel
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(), 1, 0.5D, 1.0D, 0.5D, 0.1D);
            } else { // 如果是客户端（ClientLevel），则在本地生成粒子
                world.addParticle(ParticleTypes.HAPPY_VILLAGER, livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(), 0.5D, 1.0D, 0.5D); // 参数可能需要调整
            }
            // **修复点2：骨粉效果只在服务器端执行**
            if (!world.isClientSide) { // 只有在服务器端才执行骨粉逻辑
                BlockPos posBelow = livingEntity.blockPosition().below();
                BlockState blockStateBelow = world.getBlockState(posBelow);
                Block below = blockStateBelow.getBlock();

                if (below == Blocks.GRASS_BLOCK) {
                    BlockPos pos = new BlockPos((int) ((float) livingEntity.blockPosition().getX() + rx), (int) ((float) livingEntity.blockPosition().getY() + ry), (int) ((float) livingEntity.blockPosition().getZ() + rz));
                    BlockState blockstate = world.getBlockState(pos);
                    Block var17 = blockstate.getBlock();
                    if (var17 instanceof BonemealableBlock) {
                        BonemealableBlock igrowable = (BonemealableBlock) var17;
                        // 确保 world 是 ServerLevel 才能调用 performBonemeal
                        if (igrowable.isValidBonemealTarget(world, pos, blockstate, false) // isClientSide 应该为 false 在服务端
                                && world instanceof ServerLevel serverWorld // 再次确认是 ServerLevel
                                && igrowable.isBonemealSuccess(world, world.random, pos, blockstate)) {
                            igrowable.performBonemeal(serverWorld, world.random, pos, blockstate);
                        }
                    }
                } else {
                    BlockPos pos = new BlockPos((int) ((float) livingEntity.blockPosition().getX() + rx), (int) ((float) livingEntity.blockPosition().getY() + ry0), (int) ((float) livingEntity.blockPosition().getZ() + rz));
                    BlockState blockstate = world.getBlockState(pos);
                    Block var22 = blockstate.getBlock();
                    if (var22 instanceof BonemealableBlock) {
                        BonemealableBlock igrowable = (BonemealableBlock) var22;
                        // 确保 world 是 ServerLevel 才能调用 performBonemeal
                        if (igrowable.isValidBonemealTarget(world, pos, blockstate, false) // isClientSide 应该为 false 在服务端
                                && world instanceof ServerLevel serverWorld // 再次确认是 ServerLevel
                                && igrowable.isBonemealSuccess(world, world.random, pos, blockstate)) {
                            igrowable.performBonemeal(serverWorld, world.random, pos, blockstate);
                        }
                    }
                }
            }
        }
    }
}
