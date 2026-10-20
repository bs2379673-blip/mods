package com.tunombre.infectados;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tunombre.infectados.mob.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
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
import yesman.epicfight.api.forgeevent.EntityPatchRegistryEvent;
import yesman.epicfight.world.capabilities.entitypatch.Factions;
import yesman.epicfight.world.entity.ai.attribute.EpicFightAttributes;

import java.util.function.Supplier;

/**
 * INFECTADOS: aldeanos de las plagas y el de la motosierra (Resident Evil 4) + Smoker, Boomer y Witch (Left 4 Dead).
 * Va en el SERVIDOR y en los CLIENTES. Requiere Epic Fight.
 * - Aldeanos y motosierra: solo en las zonas que crea un admin (/infectados zona ...).
 * - Smoker y Boomer: aparicion natural de noche/oscuridad (como cualquier monstruo).
 * - Witch: solo a mano (/summon infectados:witch o su huevo).
 */
@Mod(Infectados.MODID)
public class Infectados {
    public static final String MODID = "infectados";

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);

    public static final RegistryObject<EntityType<Ganado>> GANADO = mob("ganado", Ganado::new, 0.6F, 1.95F);
    public static final RegistryObject<EntityType<Motosierra>> MOTOSIERRA = mob("motosierra", Motosierra::new, 1.0F, 2.75F);
    public static final RegistryObject<EntityType<Smoker>> SMOKER = mob("smoker", Smoker::new, 0.6F, 2.05F);
    public static final RegistryObject<EntityType<Boomer>> BOOMER = mob("boomer", Boomer::new, 1.0F, 2.1F);
    public static final RegistryObject<EntityType<Witch>> WITCH = mob("witch", Witch::new, 0.6F, 1.85F);

    static {
        egg("ganado", GANADO, 0x6B4E36, 0xA01818);
        egg("motosierra", MOTOSIERRA, 0xB09464, 0x7A1010);
        egg("smoker", SMOKER, 0x3A3C40, 0xA8824E);
        egg("boomer", BOOMER, 0xB2A876, 0x6E8C3C);
        egg("witch", WITCH, 0xE6E4DC, 0xC01010);
    }

    public static final RegistryObject<SoundEvent> CHAINSAW_LOOP = SOUNDS.register("motosierra.loop", () -> SoundEvent.createFixedRangeEvent(new ResourceLocation(MODID, "motosierra.loop"), 40F));

    public static final ResourceKey<DamageType> DECAPITATED = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(MODID, "decapitated"));

    // ---- configuracion (config/infectados-common.toml) ----
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.DoubleValue PRESS_G = B.comment("QTE aldeano: cuanto suma cada pulsacion (0-100)").defineInRange("qte_aldeano_pulsacion", 7.0, 0.5, 50);
    private static final ForgeConfigSpec.DoubleValue DECAY_G = B.comment("QTE aldeano: cuanto baja por tick").defineInRange("qte_aldeano_caida", 0.9, 0.05, 10);
    private static final ForgeConfigSpec.DoubleValue PRESS_M = B.comment("QTE motosierra: cuanto suma cada pulsacion").defineInRange("qte_motosierra_pulsacion", 5.0, 0.5, 50);
    private static final ForgeConfigSpec.DoubleValue DECAY_M = B.comment("QTE motosierra: cuanto baja por tick (si llega a 0 te decapita)").defineInRange("qte_motosierra_caida", 1.2, 0.05, 10);
    private static final ForgeConfigSpec.DoubleValue PLAGA = B.comment("Probabilidad de que salga la plaga al reventar la cabeza (0-1)").defineInRange("probabilidad_plaga", 0.4, 0, 1);
    private static final ForgeConfigSpec.IntValue DODGE = B.comment("Estocada de la horca: ticks para pulsar la tecla de esquiva (20 = 1 s, maximo 14)").defineInRange("esquiva_ticks", 11, 3, 14);
    private static final ForgeConfigSpec.DoubleValue IMPALE = B.comment("Dano del empalamiento con la horca").defineInRange("dano_empalar", 14.0, 1, 100);
    private static final ForgeConfigSpec.IntValue ZONE_TICKS = B.comment("Cada cuantos ticks se intenta poblar las zonas (20 = 1 s)").defineInRange("zonas_intervalo", 100, 20, 6000);
    private static final ForgeConfigSpec.IntValue BOSS = B.comment("Rareza de la motosierra en zonas con motosierra: 1 de cada N intentos").defineInRange("motosierra_rareza", 30, 1, 10000);
    private static final ForgeConfigSpec.ConfigValue<String> EXT_G = B.comment("Nombre del quick time guardado (mod QuickTime Events) para el agarre del aldeano. Si existe y el jugador tiene ese mod, reemplaza al quick time propio. Vacio = siempre el propio.").define("qte_externo_aldeano", "agarre_aldeano");
    private static final ForgeConfigSpec.ConfigValue<String> EXT_M = B.comment("Igual, para el agarre de la motosierra").define("qte_externo_motosierra", "agarre_motosierra");
    private static final ForgeConfigSpec SPEC = B.build();
    private static float pressG = 7, decayG = 0.9F, pressM = 5, decayM = 1.2F, plaga = 0.4F;
    private static int zoneTicks = 100, boss = 30, dodge = 11;
    private static float impale = 14;

    public static float cfgPress(int kind) { return kind == 1 ? pressM : pressG; }
    public static float cfgDecay(int kind) { return kind == 1 ? decayM : decayG; }
    public static String cfgExternalQte(int kind) { return (kind == 1 ? EXT_M : EXT_G).get(); }
    public static float cfgPlagaChance() { return plaga; }
    public static int cfgBossRarity() { return boss; }
    public static int cfgDodgeTicks() { return dodge; }
    public static float cfgImpale() { return impale; }

    private static <T extends Monster> RegistryObject<EntityType<T>> mob(String name, EntityType.EntityFactory<T> f, float w, float h) {
        return ENTITIES.register(name, () -> EntityType.Builder.of(f, MobCategory.MONSTER).sized(w, h).clientTrackingRange(10).build(MODID + ":" + name));
    }

    private static void egg(String name, Supplier<? extends EntityType<? extends net.minecraft.world.entity.Mob>> type, int a, int b) {
        ITEMS.register(name + "_spawn_egg", () -> new ForgeSpawnEggItem(type, a, b, new Item.Properties()));
    }

    /** (cliente) el servidor nos tiene agarrados con QTE: el cliente no debe bajarse solo */
    public static volatile boolean clientHeld;

    public Infectados() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        Voice.register();
        ENTITIES.register(bus);
        ITEMS.register(bus);
        SOUNDS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
        bus.addListener(Infectados::onConfig);
        bus.addListener((AnimationManager.AnimationRegistryEvent e) -> e.newBuilder(MODID, Anims::build));
        bus.addListener(Infectados::onAttributes);
        bus.addListener(Infectados::onAttributesModify);
        bus.addListener(Infectados::onPatches);
        bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(() -> {
            // Epic Fight necesita saber que armadura usa cada tipo de mob; sin esto se cae al crearlos
            for (var t : java.util.List.of(GANADO.get(), MOTOSIERRA.get(), SMOKER.get(), BOOMER.get(), WITCH.get()))
                yesman.epicfight.gameasset.Armatures.registerEntityTypeArmature(t, yesman.epicfight.gameasset.Armatures.BIPED);
        }));
        bus.addListener(Infectados::onSpawnPlacement);
        bus.addListener(Infectados::onCreativeTab);
        MinecraftForge.EVENT_BUS.addListener(Infectados::onMount);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.living.LivingDeathEvent e) -> {
            // si muere por otra causa mientras lo agarran, el infectado lo suelta y se limpia (si lo mata el propio agarre, ya se encarga el)
            if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p && p.getVehicle() instanceof InfMob m && !(e.getSource().getEntity() == m)) m.forceRelease(p);
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p && p.getVehicle() instanceof InfMob m) m.forceRelease(p);
        });
        MinecraftForge.EVENT_BUS.addListener(Infectados::onCommands);
        MinecraftForge.EVENT_BUS.addListener(Infectados::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(Infectados::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(Infectados::onGameEvent);
        InfNet.init();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.infectados.client.ClientSetup.init(bus));
    }

    private static void onConfig(ModConfigEvent e) {
        if (e.getConfig().getSpec() != SPEC) return;
        pressG = PRESS_G.get().floatValue();
        decayG = DECAY_G.get().floatValue();
        pressM = PRESS_M.get().floatValue();
        decayM = DECAY_M.get().floatValue();
        plaga = PLAGA.get().floatValue();
        zoneTicks = ZONE_TICKS.get();
        boss = BOSS.get();
        dodge = DODGE.get();
        impale = IMPALE.get().floatValue();
    }

    private static void onAttributes(EntityAttributeCreationEvent e) {
        e.put(GANADO.get(), Ganado.createAttributes().build());
        e.put(MOTOSIERRA.get(), Motosierra.createAttributes().build());
        e.put(SMOKER.get(), Smoker.createAttributes().build());
        e.put(BOOMER.get(), Boomer.createAttributes().build());
        e.put(WITCH.get(), Witch.createAttributes().build());
    }

    /** los atributos que Epic Fight espera en un humanoide */
    private static void onAttributesModify(EntityAttributeModificationEvent e) {
        for (EntityType<? extends Monster> t : new EntityType[]{GANADO.get(), MOTOSIERRA.get(), SMOKER.get(), BOOMER.get(), WITCH.get()}) {
            e.add(t, EpicFightAttributes.WEIGHT.get());
            e.add(t, EpicFightAttributes.ARMOR_NEGATION.get());
            e.add(t, EpicFightAttributes.IMPACT.get(), 1.0D);
            e.add(t, EpicFightAttributes.MAX_STRIKES.get());
            e.add(t, EpicFightAttributes.STUN_ARMOR.get(), t == MOTOSIERRA.get() ? 12.0D : t == WITCH.get() ? 8.0D : 0.0D);
            e.add(t, EpicFightAttributes.OFFHAND_ATTACK_SPEED.get());
            e.add(t, EpicFightAttributes.OFFHAND_MAX_STRIKES.get());
            e.add(t, EpicFightAttributes.OFFHAND_ARMOR_NEGATION.get());
            e.add(t, EpicFightAttributes.OFFHAND_IMPACT.get());
        }
    }

    private static void onPatches(EntityPatchRegistryEvent e) {
        e.getTypeEntry().put(GANADO.get(), x -> () -> new InfPatch(Factions.VILLAGER));
        e.getTypeEntry().put(MOTOSIERRA.get(), x -> () -> new InfPatch(Factions.VILLAGER));
        e.getTypeEntry().put(SMOKER.get(), x -> () -> new InfPatch(Factions.UNDEAD));
        e.getTypeEntry().put(BOOMER.get(), x -> () -> new InfPatch(Factions.UNDEAD));
        e.getTypeEntry().put(WITCH.get(), x -> () -> new InfPatch(Factions.UNDEAD));
    }

    private static void onSpawnPlacement(SpawnPlacementRegisterEvent e) {
        e.register(SMOKER.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        e.register(BOOMER.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.SPAWN_EGGS) ITEMS.getEntries().forEach(i -> e.accept(i.get()));
    }

    private static void onMount(EntityMountEvent e) {
        if (!e.isDismounting() || !(e.getEntityBeingMounted() instanceof InfMob m)) return;
        if (e.getLevel().isClientSide) {
            if (clientHeld && m.isAlive() && !m.isRemoved() && e.getEntityMounting().isAlive()) e.setCanceled(true);
        } else if (!m.mayDismount(e.getEntityMounting())) e.setCanceled(true);
    }

    private static int zoneTimer;

    private static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (++zoneTimer >= zoneTicks) {
            zoneTimer = 0;
            Zones.tick(e.getServer());
        }
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && !e.player.level().isClientSide) Boomer.tickBile(e.player);
    }

    /** ruidos para la Witch: disparos, explosiones, bloques rotos, golpes */
    private static void onGameEvent(VanillaGameEvent e) {
        if (e.getLevel().isClientSide) return;
        GameEvent g = e.getVanillaEvent();
        float amount;
        if (g == GameEvent.PROJECTILE_SHOOT) amount = 45F;
        else if (g == GameEvent.EXPLODE) amount = 120F;
        else if (g == GameEvent.ENTITY_DAMAGE) amount = 20F;
        else if (g == GameEvent.BLOCK_DESTROY || g == GameEvent.BLOCK_PLACE) amount = 6F;
        else return;
        Vec3 p = e.getEventPosition();
        for (Witch w : e.getLevel().getEntitiesOfClass(Witch.class, new AABB(p, p).inflate(22))) {
            if (e.getCause() != w) w.noise(p, e.getCause(), amount);
        }
    }

    // ------------------------------------------------------------------ comandos ----
    private static void onCommands(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("infectados").requires(s -> s.hasPermission(2))
                .then(Commands.literal("zona")
                        .then(Commands.literal("crear").then(Commands.argument("nombre", StringArgumentType.word()).then(Commands.argument("radio", IntegerArgumentType.integer(4, 512))
                                .executes(c -> crear(c.getSource(), StringArgumentType.getString(c, "nombre"), IntegerArgumentType.getInteger(c, "radio"), 10))
                                .then(Commands.argument("max", IntegerArgumentType.integer(1, 100))
                                        .executes(c -> crear(c.getSource(), StringArgumentType.getString(c, "nombre"), IntegerArgumentType.getInteger(c, "radio"), IntegerArgumentType.getInteger(c, "max")))))))
                        .then(Commands.literal("borrar").then(Commands.argument("nombre", StringArgumentType.word()).executes(c -> {
                            Zones z = Zones.get(c.getSource().getServer());
                            boolean ok = z.zones.remove(StringArgumentType.getString(c, "nombre")) != null;
                            z.setDirty();
                            c.getSource().sendSuccess(() -> Component.literal(ok ? "Zona borrada" : "No existe esa zona"), true);
                            return ok ? 1 : 0;
                        })))
                        .then(Commands.literal("lista").executes(c -> {
                            Zones z = Zones.get(c.getSource().getServer());
                            if (z.zones.isEmpty()) c.getSource().sendSuccess(() -> Component.literal("No hay zonas. /infectados zona crear <nombre> <radio> [max]"), false);
                            z.zones.values().forEach(zo -> c.getSource().sendSuccess(() -> Component.literal(zo.describe()), false));
                            return z.zones.size();
                        }))
                        .then(Commands.literal("max").then(Commands.argument("nombre", StringArgumentType.word()).then(Commands.argument("n", IntegerArgumentType.integer(1, 100)).executes(c ->
                                edit(c.getSource(), StringArgumentType.getString(c, "nombre"), zo -> zo.max = IntegerArgumentType.getInteger(c, "n"))))))
                        .then(Commands.literal("motosierra").then(Commands.argument("nombre", StringArgumentType.word()).then(Commands.argument("si", BoolArgumentType.bool()).executes(c ->
                                edit(c.getSource(), StringArgumentType.getString(c, "nombre"), zo -> zo.boss = BoolArgumentType.getBool(c, "si"))))))
                        .then(Commands.literal("activa").then(Commands.argument("nombre", StringArgumentType.word()).then(Commands.argument("si", BoolArgumentType.bool()).executes(c ->
                                edit(c.getSource(), StringArgumentType.getString(c, "nombre"), zo -> zo.active = BoolArgumentType.getBool(c, "si"))))))));
    }

    private static int crear(CommandSourceStack s, String name, int radius, int max) {
        Zones z = Zones.get(s.getServer());
        Zones.Zone zo = new Zones.Zone();
        zo.name = name;
        zo.dim = s.getLevel().dimension();
        zo.center = BlockPos.containing(s.getPosition());
        zo.radius = radius;
        zo.max = max;
        z.zones.put(name, zo);
        z.setDirty();
        s.sendSuccess(() -> Component.literal("Zona creada: " + zo.describe()), true);
        return 1;
    }

    private static int edit(CommandSourceStack s, String name, java.util.function.Consumer<Zones.Zone> f) {
        Zones z = Zones.get(s.getServer());
        Zones.Zone zo = z.zones.get(name);
        if (zo == null) {
            s.sendFailure(Component.literal("No existe la zona " + name));
            return 0;
        }
        f.accept(zo);
        z.setDirty();
        s.sendSuccess(() -> Component.literal(zo.describe()), true);
        return 1;
    }
}
