package com.annis.npc.client;

import com.annis.npc.AnnisNpc;
import com.annis.npc.ModEntities;
import com.annis.npc.entity.AnnisNpcEntity;
import com.annis.npc.net.OpenDialogPayload;
import com.annis.npc.net.OpenEditorPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class AnnisNpcClient implements ClientModInitializer {
    private static KeyBinding openEditorKey;

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

        // el servidor pide abrir el editor (varita)
        ClientPlayNetworking.registerGlobalReceiver(OpenEditorPayload.ID, (payload, context) ->
                context.client().setScreen(new NpcEditorScreen(payload.entityId())));

        ClientPlayNetworking.registerGlobalReceiver(OpenDialogPayload.ID, (payload, context) ->
                context.client().setScreen(new NpcDialogScreen(payload.entityId())));

        // tecla para editar el NPC apuntado o el mas cercano
        openEditorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.annisnpc.editor", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.annisnpc"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) {
                return;
            }
            for (Entity entity : client.world.getEntities()) {
                if (entity instanceof AnnisNpcEntity npc) {
                    NpcAnimState.of(npc).tick(npc);
                    FiguraBridge.sync(npc);
                }
            }
            while (openEditorKey.wasPressed()) {
                openEditorForNearest(client);
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("npcmenu").executes(ctx -> {
                return openEditorForNearest(MinecraftClient.getInstance()) ? 1 : 0;
            }));
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

    /** Abre el editor del NPC al que apuntas; si no hay, el mas cercano (hasta 6 bloques). */
    private static boolean openEditorForNearest(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return false;
        }
        AnnisNpcEntity target = null;
        if (client.targetedEntity instanceof AnnisNpcEntity npc) {
            target = npc;
        } else {
            double best = 36.0;
            for (Entity entity : client.world.getEntities()) {
                if (entity instanceof AnnisNpcEntity npc) {
                    double d = client.player.squaredDistanceTo(npc);
                    if (d < best) {
                        best = d;
                        target = npc;
                    }
                }
            }
        }
        if (target == null) {
            client.player.sendMessage(Text.literal("No hay ningun NPC cerca"), true);
            return false;
        }
        client.setScreen(new NpcEditorScreen(target.getId()));
        return true;
    }
}
