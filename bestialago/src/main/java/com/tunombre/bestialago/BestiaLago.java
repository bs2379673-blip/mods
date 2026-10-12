package com.tunombre.bestialago;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Armatures;

import java.util.List;

/**
 * Bestia del Lago: un monstruo gigante e inmortal que vigila un lago. Va en el SERVIDOR y en los CLIENTES.
 * Requiere Epic Fight (la animacion del jugador sujetando las mandibulas).
 */
@Mod(BestiaLago.MODID)
public class BestiaLago {
    public static final String MODID = "bestialago";

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);

    public static final RegistryObject<EntityType<Bestia>> BESTIA = ENTITIES.register("bestia",
            () -> EntityType.Builder.<Bestia>of(Bestia::new, MobCategory.MISC)
                    .sized(Bestia.WIDTH, Bestia.HEIGHT)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .build(MODID + ":bestia"));

    public static final RegistryObject<Item> BESTIA_EGG = ITEMS.register("bestia_spawn_egg",
            () -> new ForgeSpawnEggItem(BESTIA, 0x1C221E, 0x7A2228, new Item.Properties()));

    public static final RegistryObject<SoundEvent> ROAR = sound("roar", 160F);
    public static final RegistryObject<SoundEvent> GROWL = sound("growl", 64F);
    public static final RegistryObject<SoundEvent> SPLASH = sound("splash", 128F);
    public static final RegistryObject<SoundEvent> BITE = sound("bite", 64F);
    public static final RegistryObject<SoundEvent> SWALLOW = sound("swallow", 48F);

    public static final ResourceKey<DamageType> SWALLOWED = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(MODID, "swallowed"));

    public static AnimationManager.AnimationAccessor<StaticAnimation> JAW_HOLD;

    // ---- configuracion (config/bestialago-common.toml) ----
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.DoubleValue PRESS = B.comment("Cuanto suma cada pulsacion del quick time event (0-100)").defineInRange("qte_pulsacion", 6.0, 0.5, 50.0);
    private static final ForgeConfigSpec.DoubleValue DECAY = B.comment("Cuanto baja la barra por tick (20 ticks = 1 s). Mas alto = mas dificil").defineInRange("qte_caida_por_tick", 1.1, 0.05, 10.0);
    private static final ForgeConfigSpec SPEC = B.build();
    public static float cfgPress = 6F, cfgDecay = 1.1F;

    private static RegistryObject<SoundEvent> sound(String name, float range) {
        return SOUNDS.register(name, () -> SoundEvent.createFixedRangeEvent(new ResourceLocation(MODID, name), range));
    }

    public BestiaLago() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(bus);
        ITEMS.register(bus);
        SOUNDS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
        bus.addListener(BestiaLago::onConfig);
        bus.addListener(BestiaLago::onAnimations);
        bus.addListener(BestiaLago::onAttributes);
        bus.addListener(BestiaLago::onCreativeTab);
        MinecraftForge.EVENT_BUS.addListener(BestiaLago::onMount);
        MinecraftForge.EVENT_BUS.addListener(BestiaLago::onCommands);
        BestiaNet.init();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.bestialago.client.ClientSetup.init(bus));
    }

    private static void onConfig(ModConfigEvent e) {
        if (e.getConfig().getSpec() == SPEC) {
            cfgPress = PRESS.get().floatValue();
            cfgDecay = DECAY.get().floatValue();
        }
    }

    private static void onAnimations(AnimationManager.AnimationRegistryEvent event) {
        event.newBuilder(MODID, b -> JAW_HOLD = b.nextAccessor("biped/player/jaw_hold", acc -> new StaticAnimation(0.15F, true, acc, Armatures.BIPED)));
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(BESTIA.get(), Bestia.createAttributes().build());
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(BESTIA_EGG);
    }

    /** nadie sale de la boca por su cuenta (shift) mientras la bestia lo tiene */
    private static void onMount(EntityMountEvent e) {
        if (e.isDismounting() && e.getEntityBeingMounted() instanceof Bestia b && !b.mayDismount(e.getEntityMounting())) e.setCanceled(true);
    }

    private static void onCommands(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("bestialago").requires(s -> s.hasPermission(2))
                .then(Commands.literal("radio").then(Commands.argument("bloques", IntegerArgumentType.integer(8, 512)).executes(c -> {
                    int r = IntegerArgumentType.getInteger(c, "bloques");
                    List<Bestia> l = near(c.getSource());
                    l.forEach(b -> b.setRadius(r));
                    c.getSource().sendSuccess(() -> Component.literal("Radio del lago = " + r + " bloques (" + l.size() + " bestia/s)"), true);
                    return l.size();
                })))
                .then(Commands.literal("casa").executes(c -> {
                    List<Bestia> l = near(c.getSource());
                    l.forEach(Bestia::setHomeHere);
                    c.getSource().sendSuccess(() -> Component.literal("Centro del lago = posicion actual de la bestia (" + l.size() + ")"), true);
                    return l.size();
                }))
                .then(Commands.literal("info").executes(c -> {
                    for (Bestia b : near(c.getSource())) {
                        c.getSource().sendSuccess(() -> Component.literal("Bestia en " + b.blockPosition().toShortString() + " | centro " +
                                (b.getHome() == null ? "?" : b.getHome().toShortString()) + " | radio " + b.getRadius() + " | estado " + b.getState()), false);
                    }
                    return 1;
                })));
    }

    private static List<Bestia> near(CommandSourceStack s) {
        return s.getLevel().getEntitiesOfClass(Bestia.class, new AABB(s.getPosition(), s.getPosition()).inflate(256));
    }
}
