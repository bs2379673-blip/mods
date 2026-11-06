package com.starlevi.starscenes;

import com.starlevi.starscenes.entity.NpcEntity;
import com.starlevi.starscenes.net.Net;
import com.starlevi.starscenes.server.Commands;
import com.starlevi.starscenes.server.SceneLibrary;
import com.starlevi.starscenes.server.Uploads;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StarScenes: NPCs animados desde modelos de Blockbench (.bbmodel), para Fabric 1.21.11. El servidor
 * lee los modelos y skins de config/starscenes y se los manda a los jugadores.
 * Propiedad de Starlevi. Todos los derechos reservados.
 */
public final class StarScenes implements ModInitializer {
    public static final String MOD_ID = "starscenes";
    public static final Logger LOGGER = LoggerFactory.getLogger("StarScenes");

    private static final ResourceKey<EntityType<?>> NPC_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "npc"));
    public static final EntityType<NpcEntity> NPC = Registry.register(BuiltInRegistries.ENTITY_TYPE, NPC_KEY,
            EntityType.Builder.<NpcEntity>of(NpcEntity::new, MobCategory.MISC).sized(0.6f, 1.8f).clientTrackingRange(10).updateInterval(2)
                    .fireImmune().build(NPC_KEY));

    @Override
    public void onInitialize() {
        Net.register();
        CommandRegistrationCallback.EVENT.register((d, ctx, env) -> Commands.register(d));
        ServerLifecycleEvents.SERVER_STARTING.register(s -> SceneLibrary.scan());
        ServerTickEvents.END_SERVER_TICK.register(Uploads::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sender.sendPacket(SceneLibrary.index()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Uploads.left(handler.getPlayer()));
    }
}
