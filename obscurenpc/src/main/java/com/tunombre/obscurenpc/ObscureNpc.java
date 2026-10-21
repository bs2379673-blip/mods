package com.tunombre.obscurenpc;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.forgeevent.EntityPatchRegistryEvent;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.world.entity.ai.attribute.EpicFightAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Obscure NPC: NPCs vivos para el servidor. Dos motores (mismo comportamiento): obscurenpc:npc se anima con los huesos de
 * Epic Fight y obscurenpc:npc_vanilla con los huesos normales de Minecraft. Se crean con el Creador, se editan con el Editor,
 * hablan en burbuja/chat con gestos y responden preguntas libres con una IA por contexto (sin internet).
 */
@Mod(ObscureNpc.MODID)
public class ObscureNpc {
    public static final String MODID = "obscurenpc";

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<EntityType<Actor>> NPC_EF = ENTITIES.register("npc", () -> EntityType.Builder.<Actor>of(Actor::new, MobCategory.MISC)
            .sized(0.6F, 1.8F).clientTrackingRange(10).updateInterval(3).build(MODID + ":npc"));
    public static final RegistryObject<EntityType<Actor>> NPC_VANILLA = ENTITIES.register("npc_vanilla", () -> EntityType.Builder.<Actor>of(Actor::new, MobCategory.MISC)
            .sized(0.6F, 1.8F).clientTrackingRange(10).updateInterval(3).build(MODID + ":npc_vanilla"));

    public static final RegistryObject<Item> CREATOR = ITEMS.register("npc_creator", () -> new NpcItems.CreatorItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> EDITOR = ITEMS.register("npc_editor", () -> new NpcItems.EditorItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Block> SPAWNER = BLOCKS.register("npc_spawner", () -> new SpawnerBlock(Block.Properties.of().mapColor(MapColor.COLOR_PURPLE)
            .strength(3.0F, 3600000.0F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Item> SPAWNER_ITEM = ITEMS.register("npc_spawner", () -> new net.minecraft.world.item.BlockItem(SPAWNER.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<SpawnerEntity>> SPAWNER_BE = BLOCK_ENTITIES.register("npc_spawner",
            () -> BlockEntityType.Builder.of(SpawnerEntity::new, SPAWNER.get()).build(null));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("obscurenpc", () -> CreativeModeTab.builder()
            .title(Component.literal("Obscure NPC"))
            .icon(() -> new ItemStack(CREATOR.get()))
            .displayItems((params, out) -> {
                out.accept(CREATOR.get());
                out.accept(EDITOR.get());
                out.accept(SPAWNER_ITEM.get());
            }).build());

    public ObscureNpc() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(bus);
        ITEMS.register(bus);
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        TABS.register(bus);
        bus.addListener(ObscureNpc::onAttributes);
        bus.addListener(ObscureNpc::onAttributesModify);
        bus.addListener(ObscureNpc::onPatches);
        bus.addListener(ObscureNpc::onAnimations);
        bus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(() -> Armatures.registerEntityTypeArmature(NPC_EF.get(), Armatures.BIPED)));
        Net.init();
        MinecraftForge.EVENT_BUS.register(Cmd.class);
        MinecraftForge.EVENT_BUS.register(ObscureNpc.class);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.obscurenpc.client.ClientSetup.init(bus));
    }

    /** crea un NPC del motor pedido con esa configuracion (sin colocarlo en el mundo) */
    public static Actor create(ServerLevel level, String motor, NpcData data) {
        EntityType<Actor> t = "normal".equalsIgnoreCase(motor) || "vanilla".equalsIgnoreCase(motor) ? NPC_VANILLA.get() : NPC_EF.get();
        Actor a = t.create(level);
        if (a != null) a.setData(data);
        return a;
    }

    // ------------------------------------------------------------------ registro de Epic Fight ----
    private static void onAttributes(EntityAttributeCreationEvent e) {
        e.put(NPC_EF.get(), Actor.createAttributes().build());
        e.put(NPC_VANILLA.get(), Actor.createAttributes().build());
    }

    /** los atributos que Epic Fight da a los humanoides (sin ellos su parche falla al leerlos) */
    private static void onAttributesModify(EntityAttributeModificationEvent event) {
        EntityType<Actor> t = NPC_EF.get();
        event.add(t, EpicFightAttributes.WEIGHT.get());
        event.add(t, EpicFightAttributes.ARMOR_NEGATION.get());
        event.add(t, EpicFightAttributes.IMPACT.get(), 1.0D);
        event.add(t, EpicFightAttributes.MAX_STRIKES.get());
        event.add(t, EpicFightAttributes.STUN_ARMOR.get());
        event.add(t, EpicFightAttributes.OFFHAND_ATTACK_SPEED.get());
        event.add(t, EpicFightAttributes.OFFHAND_MAX_STRIKES.get());
        event.add(t, EpicFightAttributes.OFFHAND_ARMOR_NEGATION.get());
        event.add(t, EpicFightAttributes.OFFHAND_IMPACT.get());
    }

    private static void onPatches(EntityPatchRegistryEvent event) {
        event.getTypeEntry().put(NPC_EF.get(), e -> ActorPatch::new);
    }

    private static void onAnimations(AnimationManager.AnimationRegistryEvent event) {
        event.newBuilder(MODID, ObscureNpc::buildAnimations);
    }

    private static void buildAnimations(AnimationManager.AnimationBuilder b) {
        for (String n : Gestures.ONESHOT) Gestures.MINE.put(n, anim(b, n, false));
        for (String n : Gestures.LOOPS) Gestures.MINE.put(n, anim(b, n, true));
    }

    private static AnimationManager.AnimationAccessor<StaticAnimation> anim(AnimationManager.AnimationBuilder b, String name, boolean loop) {
        return b.nextAccessor("biped/npc/" + name, acc -> new StaticAnimation(loop ? 0.25F : 0.2F, loop, acc, Armatures.BIPED));
    }

    // ------------------------------------------------------------------ eventos del servidor ----
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent e) {
        installDefaults();
        Dialogs.reload();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent e) {
        Dialogs.endAll();
        Chatter.clear();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Dialogs.tick(e.getServer());
        Chatter.serverTick(e.getServer());
    }

    @SubscribeEvent
    public static void onChat(net.minecraftforge.event.ServerChatEvent e) { Dialogs.onChat(e); }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { Dialogs.end(e.getEntity().getUUID()); }

    /** deja ejemplos listos en config/obscurenpc la primera vez */
    private static void installDefaults() {
        try {
            Path base = State.dir();
            for (String f : new String[]{"dialogos/ejemplo.json", "contexto/ejemplo.txt", "rutinas/ejemplo.json", "LEEME.txt"}) {
                Path dst = base.resolve(f);
                if (Files.exists(dst)) continue;
                Files.createDirectories(dst.getParent());
                try (var in = ObscureNpc.class.getResourceAsStream("/defaults/" + f)) {
                    if (in != null) Files.write(dst, in.readAllBytes());
                }
            }
        } catch (IOException ignored) { }
    }
}
