package com.starlevi.starframes;

import com.starlevi.starframes.block.ScreenBlock;
import com.starlevi.starframes.block.ScreenBlockEntity;
import com.starlevi.starframes.channel.Channels;
import com.starlevi.starframes.item.RemoteItem;
import com.starlevi.starframes.net.Net;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StarFrames: pantallas de imagen y video para Fabric 1.21.11 (con WaterMedia), vinculadas y
 * sincronizadas: todas las pantallas de un canal muestran lo mismo en el mismo segundo.
 * Propiedad de Starlevi. Todos los derechos reservados.
 */
public final class StarFrames implements ModInitializer {
    public static final String MOD_ID = "starframes";
    public static final Logger LOGGER = LoggerFactory.getLogger("StarFrames");

    private static Identifier id(String p) {
        return Identifier.fromNamespaceAndPath(MOD_ID, p);
    }

    private static final ResourceKey<Block> SCREEN_KEY = ResourceKey.create(Registries.BLOCK, id("pantalla"));
    public static final Block SCREEN = Registry.register(BuiltInRegistries.BLOCK, SCREEN_KEY,
            new ScreenBlock(BlockBehaviour.Properties.of().setId(SCREEN_KEY).strength(1.0f).noOcclusion().sound(SoundType.METAL)
                    .lightLevel(s -> 3)));
    public static final Item SCREEN_ITEM = Registry.register(BuiltInRegistries.ITEM, id("pantalla"),
            new BlockItem(SCREEN, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id("pantalla"))).useBlockDescriptionPrefix()));
    public static final Item REMOTE = Registry.register(BuiltInRegistries.ITEM, id("control_remoto"),
            new RemoteItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id("control_remoto"))).stacksTo(1)));
    public static final BlockEntityType<ScreenBlockEntity> SCREEN_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("pantalla"),
            FabricBlockEntityTypeBuilder.create(ScreenBlockEntity::new, SCREEN).build());

    @Override
    public void onInitialize() {
        Net.register();
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(e -> {
            e.accept(SCREEN_ITEM);
            e.accept(REMOTE);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(Channels::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> Channels.stop());
        ServerTickEvents.END_SERVER_TICK.register(s -> Channels.tick());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> Channels.join(handler.getPlayer())));
        LOGGER.info("StarFrames listo");
    }
}
