package com.tunombre.obscurearcade;

import com.cotii.cubosu.api.CubOsuServerApi;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import yesman.epicfight.api.animation.AnimationManager;

/**
 * Obscure Arcade: una maquina recreativa (osu!mania con ranking y premios) y una consola de duelo donde dos jugadores se pelean
 * con animaciones de Epic Fight segun las notas que aciertan o fallan. Incluye /fnf para convertir canciones de Friday Night Funkin
 * en archivos .osz que lee CubOsu!.
 */
@Mod(ObscureArcade.MODID)
public class ObscureArcade {
    public static final String MODID = "obscurearcade";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Block> ARCADE = BLOCKS.register("arcade_machine", () -> new CabinetBlock(Block.Properties.of().mapColor(MapColor.COLOR_BLUE)
            .strength(3.0F, 3600000.0F).sound(SoundType.METAL).noOcclusion(), false));
    public static final RegistryObject<Block> VERSUS = BLOCKS.register("versus_console", () -> new CabinetBlock(Block.Properties.of().mapColor(MapColor.COLOR_RED)
            .strength(3.0F, 3600000.0F).sound(SoundType.METAL).noOcclusion(), true));
    public static final RegistryObject<Item> ARCADE_ITEM = ITEMS.register("arcade_machine", () -> new BlockItem(ARCADE.get(), new Item.Properties()));
    public static final RegistryObject<Item> VERSUS_ITEM = ITEMS.register("versus_console", () -> new BlockItem(VERSUS.get(), new Item.Properties()));
    public static final RegistryObject<Item> WAND = ITEMS.register("arcade_wand", () -> new ArcadeWand(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TOKEN = ITEMS.register("arcade_token", () -> new Item(new Item.Properties()));

    public static final RegistryObject<BlockEntityType<ArcadeEntity>> ARCADE_BE = BLOCK_ENTITIES.register("arcade_machine",
            () -> BlockEntityType.Builder.of(ArcadeEntity::new, ARCADE.get()).build(null));
    public static final RegistryObject<BlockEntityType<VersusEntity>> VERSUS_BE = BLOCK_ENTITIES.register("versus_console",
            () -> BlockEntityType.Builder.of(VersusEntity::new, VERSUS.get()).build(null));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("obscurearcade", () -> CreativeModeTab.builder()
            .title(Component.literal("Obscure Arcade"))
            .icon(() -> new ItemStack(ARCADE_ITEM.get()))
            .displayItems((params, out) -> {
                out.accept(ARCADE_ITEM.get());
                out.accept(VERSUS_ITEM.get());
                out.accept(WAND.get());
                out.accept(TOKEN.get());
            }).build());

    private static boolean listening;

    public ObscureArcade() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        TABS.register(bus);
        bus.addListener((AnimationManager.AnimationRegistryEvent e) -> Anims.register(e));
        Net.init();
        MinecraftForge.EVENT_BUS.register(ObscureArcade.class);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.obscurearcade.client.ClientSetup.init());
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) { Cmd.register(e.getDispatcher()); }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent e) {
        Styles.installDefaults();
        Styles.reload();
        if (!listening) {
            listening = true;
            try { CubOsuServerApi.addListener(new Results()); } catch (RuntimeException ex) { listening = false; }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent e) {
        Duels.clear();
        Arcade.clear();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END) Duels.tick(e.getServer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        Duels.abort(e.getEntity().getUUID());
        Arcade.forget(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        Duels.abort(e.getEntity().getUUID());
        Arcade.forget(e.getEntity().getUUID());
    }

    /** en pleno duelo nadie sale herido: los golpes de Epic Fight son solo visuales */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer v && Duels.inDuel(v.getUUID())) e.setCanceled(true);
        else if (e.getSource().getEntity() instanceof ServerPlayer a && Duels.inDuel(a.getUUID())) e.setCanceled(true);
    }
}
