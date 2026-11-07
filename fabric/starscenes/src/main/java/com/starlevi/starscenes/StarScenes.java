package com.starlevi.starscenes;

import com.starlevi.starscenes.entity.NpcEntity;
import com.starlevi.starscenes.net.Net;
import com.starlevi.starscenes.server.Commands;
import com.starlevi.starscenes.server.SceneLibrary;
import com.starlevi.starscenes.server.Uploads;
import com.starlevi.starscenes.talk.Dialogues;
import com.starlevi.starscenes.talk.PlayerStore;
import com.starlevi.starscenes.talk.Quests;
import com.starlevi.starscenes.talk.Shops;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
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
        ServerLifecycleEvents.SERVER_STARTING.register(s -> {
            SceneLibrary.scan();
            PlayerStore.start(s);
            LOGGER.info("StarScenes: {}", loadTalk());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> PlayerStore.stop());
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            Uploads.tick(s);
            Dialogues.tick(s);
            Quests.tick(s);
            PlayerStore.tick(s);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register(Quests::killed);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sender.sendPacket(SceneLibrary.index()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Quests.dirty(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Uploads.left(handler.getPlayer());
            Dialogues.left(handler.getPlayer());
            Shops.close(handler.getPlayer());
            Quests.left(handler.getPlayer());
            PlayerStore.left(handler.getPlayer());
        });
    }

    /** Lee dialogos, misiones y tiendas; devuelve el resumen (con los errores, si hubo). */
    public static String loadTalk() {
        java.util.List<String> errors = new java.util.ArrayList<>();
        String msg = Dialogues.load(errors) + ", " + Quests.load(errors) + ", " + Shops.load(errors);
        if (!errors.isEmpty()) {
            msg += "\n§cErrores:§r\n" + String.join("\n", errors);
        }
        return msg;
    }
}
