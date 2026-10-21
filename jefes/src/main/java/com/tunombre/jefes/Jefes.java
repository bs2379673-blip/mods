package com.tunombre.jefes;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Jefes: monstruos con modelo propio (convertido de un .cpmmodel) sin inteligencia: los maneja un administrador que "se convierte" en el.
 * Pasos, gruñidos, zarpazos, agarre con quick time events y screamer. Va en el SERVIDOR y en los CLIENTES.
 */
@Mod(Jefes.MODID)
public class Jefes {
    public static final String MODID = "jefes";
    public static final Logger LOG = com.mojang.logging.LogUtils.getLogger();

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);

    public static final RegistryObject<EntityType<JefeEntity>> JEFE = ENTITIES.register("jefe",
            () -> EntityType.Builder.<JefeEntity>of(JefeEntity::new, MobCategory.MISC)
                    .sized(1.5F, 5.4F).clientTrackingRange(12).updateInterval(1).fireImmune().build(MODID + ":jefe"));

    public static final RegistryObject<Item> MANDO = ITEMS.register("mando", () -> new MandoItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> INVOCADOR = ITEMS.register("invocador", () -> new InvocadorItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<SoundEvent> STEP = sound("step", 96F);
    public static final RegistryObject<SoundEvent> ROAR = sound("roar", 160F);
    public static final RegistryObject<SoundEvent> GROWL = sound("growl", 96F);
    public static final RegistryObject<SoundEvent> VOICE = sound("voice", 80F);
    public static final RegistryObject<SoundEvent> ATTACK = sound("attack", 64F);
    public static final RegistryObject<SoundEvent> HIT = sound("hit", 64F);
    public static final RegistryObject<SoundEvent> LAND = sound("land", 128F);
    public static final RegistryObject<SoundEvent> SNIFF = sound("sniff", 64F);
    public static final RegistryObject<SoundEvent> GRAB = sound("grab", 64F);
    public static final RegistryObject<SoundEvent> CHOMP = sound("chomp", 64F);
    public static final RegistryObject<SoundEvent> SCREAM = sound("scream", 48F);

    public static final ResourceKey<DamageType> ATRAPADO = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(MODID, "atrapado"));

    // ---- configuracion (config/jefes-common.toml) ----
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.DoubleValue QTE_TIME = B.comment("Multiplicador del tiempo de los quick time events (1.0 = normal, 2.0 = el doble de tiempo)").defineInRange("qte_tiempo", 1.0, 0.3, 4.0);
    private static final ForgeConfigSpec.DoubleValue DAMAGE = B.comment("Multiplicador del daño de los golpes de los jefes").defineInRange("dano", 1.0, 0.0, 20.0);
    private static final ForgeConfigSpec.BooleanValue ANNOUNCE = B.comment("Al liberar un jefe con /jefe liberar, anunciar su nombre con TacUI (/tacalert)").define("anunciar", true);
    private static final ForgeConfigSpec.BooleanValue AMBIENT = B.comment("Un jefe sin administrador gruñe de vez en cuando").define("gruñidos_solo", true);
    private static final ForgeConfigSpec.IntValue HOLD_LIMIT = B.comment("Segundos maximos que un jefe sostiene a alguien antes de darlo por perdido").defineInRange("agarre_maximo", 60, 10, 600);
    private static final ForgeConfigSpec CFG = B.build();

    public static double cfgQteTime() { return QTE_TIME.get(); }
    public static double cfgDamage() { return DAMAGE.get(); }
    public static boolean cfgAnnounce() { return ANNOUNCE.get(); }
    public static boolean cfgAmbient() { return AMBIENT.get(); }
    public static int cfgHoldLimit() { return HOLD_LIMIT.get(); }

    private static RegistryObject<SoundEvent> sound(String name, float range) {
        return SOUNDS.register(name, () -> SoundEvent.createFixedRangeEvent(new ResourceLocation(MODID, name), range));
    }

    public Jefes() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(bus);
        ITEMS.register(bus);
        SOUNDS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CFG);
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(JEFE.get(), JefeEntity.createAttributes().build()));
        bus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) { e.accept(MANDO); e.accept(INVOCADOR); }
        });
        Net.init();
        Qte.init();
        MinecraftForge.EVENT_BUS.addListener((ServerAboutToStartEvent e) -> { Bosses.loadServer(); QteDefs.install(); });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) for (Boss b : Bosses.SERVER.values()) Net.toPlayer(sp, Net.Defs.of(b));
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (!(e.getEntity() instanceof ServerPlayer sp)) return;
            Possession.onLogout(sp);
            if (sp.getVehicle() instanceof JefeEntity j) j.forceRelease(sp);
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> { if (e.phase == TickEvent.Phase.END) Possession.tick(); });
        MinecraftForge.EVENT_BUS.addListener((LivingDeathEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp && sp.getVehicle() instanceof JefeEntity j) j.forceRelease(sp);
        });
        MinecraftForge.EVENT_BUS.addListener((EntityMountEvent e) -> {
            if (!e.isDismounting() || !(e.getEntityBeingMounted() instanceof JefeEntity j)) return;
            if (e.getLevel().isClientSide) {
                if (e.getEntityMounting().isAlive() && j.isAlive() && !j.isRemoved() && DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> com.tunombre.jefes.client.ClientHooks.holding())) e.setCanceled(true);
            } else if (!j.mayDismount(e.getEntityMounting())) e.setCanceled(true);
        });
        MinecraftForge.EVENT_BUS.addListener(Jefes::commands);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.jefes.client.ClientSetup.init(bus));
    }

    // ------------------------------------------------------------------ utilidades ----
    public static void announce(MinecraftServer srv, Boss b) {
        if (srv == null) return;
        if (ModList.get().isLoaded("tacui")) {
            srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack().withSuppressedOutput(), "tacalert @a 5.0 rojo " + b.spec.title);
        } else {
            String t = b.spec.title.replace('|', ' ');
            srv.getPlayerList().broadcastSystemMessage(Component.literal("§4§l" + t), false);
        }
    }

    /** crea un jefe en esa posicion; null si el id no existe */
    public static JefeEntity spawn(net.minecraft.server.level.ServerLevel lv, String id, double x, double y, double z, float yaw) {
        Boss b = Bosses.SERVER.get(id.toLowerCase(Locale.ROOT));
        if (b == null) return null;
        JefeEntity j = JEFE.get().create(lv);
        if (j == null) return null;
        j.setBossId(b.id());
        j.moveTo(x, y, z, yaw, 0F);
        j.setYHeadRot(yaw);
        lv.addFreshEntity(j);
        return j;
    }

    private static JefeEntity nearest(CommandSourceStack s, boolean freeOnly) {
        AABB box = new AABB(s.getPosition(), s.getPosition()).inflate(128);
        return s.getLevel().getEntitiesOfClass(JefeEntity.class, box, j -> !freeOnly || j.getController() == null).stream()
                .min(Comparator.comparingDouble(j -> j.distanceToSqr(s.getPosition()))).orElse(null);
    }

    private static void commands(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        var ids = (com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack>) (c, b) -> SharedSuggestionProvider.suggest(Bosses.SERVER.keySet(), b);
        d.register(Commands.literal("jefe").requires(s -> s.hasPermission(2))
                .then(Commands.literal("lista").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("Jefes: " + String.join(", ", Bosses.SERVER.keySet())), false);
                    return Bosses.SERVER.size();
                }))
                .then(Commands.literal("invocar").then(Commands.argument("id", StringArgumentType.word()).suggests(ids).executes(c -> spawnCmd(c.getSource(), StringArgumentType.getString(c, "id"), false))))
                .then(Commands.literal("liberar").then(Commands.argument("id", StringArgumentType.word()).suggests(ids).executes(c -> spawnCmd(c.getSource(), StringArgumentType.getString(c, "id"), true))))
                .then(Commands.literal("controlar").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    JefeEntity j = nearest(c.getSource(), true);
                    if (j == null) { c.getSource().sendFailure(Component.literal("No hay ningun jefe libre cerca (128 bloques).")); return 0; }
                    return Possession.begin(p, j) ? 1 : 0;
                }))
                .then(Commands.literal("soltar").executes(c -> { Possession.end(c.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("quitar").executes(c -> {
                    AABB box = new AABB(c.getSource().getPosition(), c.getSource().getPosition()).inflate(128);
                    List<JefeEntity> l = c.getSource().getLevel().getEntitiesOfClass(JefeEntity.class, box);
                    l.forEach(j -> { j.forceReleaseAll(); j.discard(); });
                    c.getSource().sendSuccess(() -> Component.literal("Jefes quitados: " + l.size()), true);
                    return l.size();
                }))
                .then(Commands.literal("sonido").then(Commands.argument("cual", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("gruñido", "rugido", "voz", "olfateo"), b))
                        .executes(c -> {
                            JefeEntity j = nearest(c.getSource(), false);
                            if (j == null) return 0;
                            j.doAction(switch (StringArgumentType.getString(c, "cual")) { case "rugido" -> 4; case "voz" -> 9; case "olfateo" -> 6; default -> 8; });
                            return 1;
                        }))));
    }

    private static int spawnCmd(CommandSourceStack s, String id, boolean announce) {
        JefeEntity j = spawn(s.getLevel(), id, s.getPosition().x, s.getPosition().y, s.getPosition().z, s.getRotation().y);
        if (j == null) { s.sendFailure(Component.literal("No existe el jefe '" + id + "'. Usa /jefe lista")); return 0; }
        if (announce && cfgAnnounce()) announce(s.getServer(), Bosses.SERVER.get(j.getBossId()));
        s.sendSuccess(() -> Component.literal("Jefe '" + id + "' en " + j.blockPosition().toShortString() + ". /jefe controlar para manejarlo."), true);
        return 1;
    }
}
