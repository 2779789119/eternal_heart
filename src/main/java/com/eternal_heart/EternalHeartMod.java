package com.eternal_heart;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.eternal_heart.features.FeatureManager;

@Mod(EternalHeartMod.MODID)
public class EternalHeartMod {

    public static final String MODID = "eternal_heart";

    public static final DeferredRegister<com.mojang.serialization.Codec<? extends net.minecraftforge.common.loot.IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MODID);
    static {
        LOOT_MODIFIERS.register("heart_block_drops", () -> com.eternal_heart.loot.HeartBlockLootModifier.CODEC);
    }

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // ==================== 自定义效果注册 ====================

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MODID);

    /** 永怒（Eternal Fury）：按层提供攻击伤害加成，满层额外 +5% 伤害 */
    public static final RegistryObject<MobEffect> ETERNAL_FURY =
            MOB_EFFECTS.register("eternal_fury", EternalFuryEffect::new);

    // ==================== 音效注册 ====================

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);

    /** 时停发动音效 */
    public static final RegistryObject<SoundEvent> SHI_TING =
            SOUND_EVENTS.register("shi_ting",
                    () -> SoundEvent.createVariableRangeEvent(EternalHeartMod.id("shi_ting")));

    // ==================== 物品注册 ====================

    public static final RegistryObject<Item> ETERNAL_HEART = ITEMS.register("eternal_heart",
            () -> new EternalHeartItem(new Item.Properties().stacksTo(1).fireResistant().rarity(net.minecraft.world.item.Rarity.EPIC)));

    // ==================== 创造模式物品栏 ====================

    public static final RegistryObject<CreativeModeTab> TAB = CREATIVE_TABS.register("eternal_heart_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.eternal_heart"))
                    .icon(() -> ETERNAL_HEART.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(ETERNAL_HEART.get());
                    })
                    .build());

    public EternalHeartMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        CREATIVE_TABS.register(modBus);
        MOB_EFFECTS.register(modBus);
        LOOT_MODIFIERS.register(modBus);
        SOUND_EVENTS.register(modBus);

        modBus.addListener(this::addCreativeTab);

        // 注册配置文件
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, EternalHeartConfig.SPEC);

        // 初始化功能模块
        FeatureManager.registerFeatures();
        com.eternal_heart.network.ConfigNetwork.register();
        com.eternal_heart.network.TimeStopNetwork.register();

        MinecraftForge.EVENT_BUS.register(EternalHeartEvents.class);
    }

    private void addCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ETERNAL_HEART.get());
        }
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
