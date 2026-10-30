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
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class AnnisNpcClient implements ClientModInitializer {
    private static KeyMapping openEditorKey;

    @Override
    public void onInitializeClient() {
        com.annis.npc.client.punchy.PunchyClips.init();
        EntityRendererRegistry.register(ModEntities.NPC, NpcRenderer::new);

        // crear las carpetas para que sea facil encontrarlas
        try {
            Files.createDirectories(SkinManager.dir());
            Files.createDirectories(MoveLibrary.dir());
            Files.createDirectories(com.annis.npc.client.importer.MotionImporter.dir());
            for (Path dir : EmoteLibrary.dirs()) {
                Files.createDirectories(dir);
            }
        } catch (Exception e) {
            AnnisNpc.LOGGER.warn("No se pudieron crear las carpetas de skins y emotes", e);
        }

        // capa invisible en el jugador para el grabador de movimientos
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityRenderer instanceof PlayerRenderer playerRenderer) {
                helper.register(new RecorderFeature(playerRenderer));
            }
        });

        ClientLinks.init();

        // el servidor pide abrir el editor (varita)
        ClientPlayNetworking.registerGlobalReceiver(OpenEditorPayload.ID, (payload, context) ->
                context.client().setScreen(new NpcEditorScreen(payload.entityId())));

        ClientPlayNetworking.registerGlobalReceiver(OpenDialogPayload.ID, (payload, context) ->
                context.client().setScreen(new NpcDialogScreen(payload.entityId())));

        // tecla para editar el NPC apuntado o el mas cercano
        openEditorKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.annisnpc.editor", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.annisnpc"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) {
                MoveRecorder.cancel();
                return;
            }
            MoveRecorder.tick();
            ClientLinks.tick(client);
            for (Entity entity : client.level.entitiesForRendering()) {
                if (entity instanceof AnnisNpcEntity npc) {
                    NpcAnimState.of(npc).tick(npc);
                    FiguraBridge.sync(npc);
                }
            }
            while (openEditorKey.consumeClick()) {
                openEditorForNearest(client);
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("npcrecord")
                    .then(ClientCommandManager.literal("stop").executes(ctx -> {
                        MoveRecorder.stop();
                        return 1;
                    }))
                    .then(ClientCommandManager.argument("nombre", StringArgumentType.word())
                            .executes(ctx -> record(ctx, 8, null))
                            .then(ClientCommandManager.argument("segundos", IntegerArgumentType.integer(1, 120))
                                    .executes(ctx -> record(ctx, IntegerArgumentType.getInteger(ctx, "segundos"), null))
                                    .then(ClientCommandManager.argument("jugador", StringArgumentType.word())
                                            .executes(ctx -> record(ctx, IntegerArgumentType.getInteger(ctx, "segundos"),
                                                    StringArgumentType.getString(ctx, "jugador")))))));
            dispatcher.register(ClientCommandManager.literal("npcimport")
                    .executes(ctx -> {
                        List<String> files = com.annis.npc.client.importer.MotionImporter.list();
                        ctx.getSource().sendFeedback(Component.literal(files.isEmpty()
                                ? "Pon archivos .bvh o .vmd en " + com.annis.npc.client.importer.MotionImporter.dir()
                                : "Archivos: " + String.join(", ", files)));
                        return files.size();
                    })
                    .then(ClientCommandManager.argument("archivo", StringArgumentType.string())
                            .executes(ctx -> importCmd(ctx, null))
                            .then(ClientCommandManager.argument("nombre", StringArgumentType.word())
                                    .executes(ctx -> importCmd(ctx, StringArgumentType.getString(ctx, "nombre"))))));
            dispatcher.register(ClientCommandManager.literal("npcmenu").executes(ctx -> {
                return openEditorForNearest(Minecraft.getInstance()) ? 1 : 0;
            }));
            dispatcher.register(ClientCommandManager.literal("npcreload").executes(ctx -> {
                SkinManager.clear();
                EmoteLibrary.clear();
                MoveLibrary.clear();
                ctx.getSource().sendFeedback(Component.literal("Skins, emotes y movimientos recargados"));
                return 1;
            }));
            dispatcher.register(ClientCommandManager.literal("npcskins").executes(ctx -> {
                List<String> names = SkinManager.list();
                ctx.getSource().sendFeedback(Component.literal(names.isEmpty()
                        ? "No hay skins. Pone PNG de 64x64 en " + SkinManager.dir()
                        : "Skins: " + String.join(", ", names)));
                return names.size();
            }));
            dispatcher.register(ClientCommandManager.literal("npcemotes").executes(ctx -> {
                List<String> names = EmoteLibrary.list();
                ctx.getSource().sendFeedback(Component.literal(names.isEmpty()
                        ? "No hay emotes (.json). Carpetas: " + EmoteLibrary.dirs()
                        : "Emotes: " + String.join(", ", names)));
                return names.size();
            }));
        });
    }

    /** /npcimport "archivo" [nombre]: convierte un BVH o VMD en segundo plano. */
    private static int importCmd(CommandContext<FabricClientCommandSource> ctx, String name) {
        String file = StringArgumentType.getString(ctx, "archivo");
        ctx.getSource().sendFeedback(Component.literal("Importando " + file + "..."));
        Thread t = new Thread(() -> {
            String msg;
            try {
                msg = "Baile " + com.annis.npc.client.importer.MotionImporter.importFile(file, name, true);
            } catch (Exception e) {
                msg = "No se pudo importar: " + e.getMessage();
                AnnisNpc.LOGGER.warn("Error importando {}", file, e);
            }
            final String m = msg;
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(Component.literal(m), false);
                }
            });
        }, "annisnpc-import");
        t.setDaemon(true);
        t.start();
        return 1;
    }

    /** /npcrecord nombre [segundos] [jugador]: graba a uno mismo o a otro jugador visible. */
    private static int record(CommandContext<FabricClientCommandSource> ctx, int seconds, String playerName) {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer target = null;
        if (playerName != null && mc.level != null) {
            for (AbstractClientPlayer p : mc.level.players()) {
                if (p.getGameProfile().getName().equalsIgnoreCase(playerName)) {
                    target = p;
                }
            }
            if (target == null) {
                ctx.getSource().sendError(Component.literal("No veo a ningun jugador llamado " + playerName));
                return 0;
            }
        }
        String name = StringArgumentType.getString(ctx, "nombre");
        if (!MoveRecorder.start(name, seconds, true, target)) {
            ctx.getSource().sendError(Component.literal("No se pudo empezar (ya hay una grabacion o el nombre no vale)"));
            return 0;
        }
        ctx.getSource().sendFeedback(Component.literal("Grabacion en 3 s. /npcrecord stop para terminar antes."));
        return 1;
    }

    /** Abre el editor del NPC al que apuntas; si no hay, el mas cercano (hasta 6 bloques). */
    private static boolean openEditorForNearest(Minecraft client) {
        if (client.player == null || client.level == null) {
            return false;
        }
        AnnisNpcEntity target = null;
        if (client.crosshairPickEntity instanceof AnnisNpcEntity npc) {
            target = npc;
        } else {
            double best = 36.0;
            for (Entity entity : client.level.entitiesForRendering()) {
                if (entity instanceof AnnisNpcEntity npc) {
                    double d = client.player.distanceToSqr(npc);
                    if (d < best) {
                        best = d;
                        target = npc;
                    }
                }
            }
        }
        if (target == null) {
            client.player.displayClientMessage(Component.literal("No hay ningun NPC cerca"), true);
            return false;
        }
        client.setScreen(new NpcEditorScreen(target.getId()));
        return true;
    }
}
