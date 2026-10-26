package com.annis.npc.client;

import com.annis.npc.AnnisNpc;
import com.annis.npc.ModEntities;
import com.annis.npc.entity.AnnisNpcEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class AnnisNpcClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.NPC, NpcRenderer::new);

        // crear las carpetas para que sea facil encontrarlas
        try {
            Files.createDirectories(SkinManager.dir());
            for (Path dir : EmoteLibrary.dirs()) {
                Files.createDirectories(dir);
            }
        } catch (Exception e) {
            AnnisNpc.LOGGER.warn("No se pudieron crear las carpetas de skins y emotes", e);
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) {
                return;
            }
            for (Entity entity : client.world.getEntities()) {
                if (entity instanceof AnnisNpcEntity npc) {
                    NpcAnimState.of(npc).tick(npc);
                }
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("npcreload").executes(ctx -> {
                SkinManager.clear();
                EmoteLibrary.clear();
                ctx.getSource().sendFeedback(Text.literal("Skins y emotes recargados"));
                return 1;
            }));
            dispatcher.register(ClientCommandManager.literal("npcskins").executes(ctx -> {
                List<String> names = SkinManager.list();
                ctx.getSource().sendFeedback(Text.literal(names.isEmpty()
                        ? "No hay skins. Pone PNG de 64x64 en " + SkinManager.dir()
                        : "Skins: " + String.join(", ", names)));
                return names.size();
            }));
            dispatcher.register(ClientCommandManager.literal("npcemotes").executes(ctx -> {
                List<String> names = EmoteLibrary.list();
                ctx.getSource().sendFeedback(Text.literal(names.isEmpty()
                        ? "No hay emotes (.json). Carpetas: " + EmoteLibrary.dirs()
                        : "Emotes: " + String.join(", ", names)));
                return names.size();
            }));
        });
    }
}
