package com.annis.npc.client;

import com.annis.npc.AnnisNpc;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Graba la pose real del modelo de un jugador (con Fresh Animations/EMF, emotes de Emotecraft o lo que
 * tenga encima) tick a tick y la guarda como movimiento para los NPC.
 */
public final class MoveRecorder {
    private static String name;
    private static boolean loop;
    private static UUID target;
    private static int countdown;
    private static int maxTicks;
    private static int recordedTicks;
    private static boolean capturedThisTick;
    private static float[] last;
    private static final List<float[]> FRAMES = new ArrayList<>();
    private static Perspective previousPerspective;

    private MoveRecorder() {
    }

    public static boolean active() {
        return name != null;
    }

    public static boolean capturing() {
        return name != null && countdown <= 0;
    }

    public static String sanitize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replaceAll("[^a-z0-9_-]", "");
    }

    /** Empieza a grabar (tras 3 s de cuenta atras). targetPlayer null = uno mismo. */
    public static boolean start(String rawName, int seconds, boolean loopMove, PlayerEntity targetPlayer) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String clean = sanitize(rawName);
        if (clean.isEmpty() || mc.player == null || active()) {
            return false;
        }
        name = clean;
        loop = loopMove;
        target = targetPlayer == null ? mc.player.getUuid() : targetPlayer.getUuid();
        countdown = 60;
        maxTicks = Math.max(1, Math.min(120, seconds)) * 20;
        recordedTicks = 0;
        last = null;
        FRAMES.clear();
        if (target.equals(mc.player.getUuid())) {
            previousPerspective = mc.options.getPerspective();
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        } else {
            previousPerspective = null;
        }
        return true;
    }

    public static void tick() {
        if (!active()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            cancel();
            return;
        }
        if (countdown > 0) {
            if (countdown % 20 == 0) {
                mc.player.sendMessage(Text.literal("Grabando \"" + name + "\" en " + countdown / 20 + "..."), true);
            }
            countdown--;
            return;
        }
        // si este tick no se dibujo el jugador, repetir el ultimo frame para no perder el ritmo
        if (!capturedThisTick && last != null) {
            FRAMES.add(last.clone());
        }
        capturedThisTick = false;
        recordedTicks++;
        if (recordedTicks % 20 == 0) {
            mc.player.sendMessage(Text.literal("● REC " + name + "  " + recordedTicks / 20 + "/" + maxTicks / 20 + " s"), true);
        }
        if (recordedTicks >= maxTicks) {
            stop();
        }
    }

    /** Llamado desde la capa de render del jugador, ya con la pose final (EMF incluido). */
    public static void capture(PlayerEntity entity, PlayerEntityModel<?> model) {
        if (!capturing() || capturedThisTick || !entity.getUuid().equals(target)) {
            return;
        }
        ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        float[] v = new float[MoveLibrary.VALUES];
        for (int p = 0; p < parts.length; p++) {
            ModelPart part = parts[p];
            int b = p * 6;
            v[b] = part.pitch;
            v[b + 1] = part.yaw;
            v[b + 2] = part.roll;
            v[b + 3] = part.pivotX;
            v[b + 4] = part.pivotY;
            v[b + 5] = part.pivotZ;
        }
        FRAMES.add(v);
        last = v;
        capturedThisTick = true;
    }

    public static void stop() {
        if (!active()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        String saved = name;
        restorePerspective();
        name = null;
        if (FRAMES.isEmpty()) {
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("No se capturo nada (el jugador tiene que verse en pantalla)"), false);
            }
            return;
        }
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.addProperty("name", saved);
            root.addProperty("loop", loop);
            root.addProperty("fps", 20);
            JsonArray frames = new JsonArray();
            for (float[] f : FRAMES) {
                JsonArray a = new JsonArray();
                for (float x : f) {
                    a.add(Math.round(x * 10000f) / 10000f);
                }
                frames.add(a);
            }
            root.add("frames", frames);
            Path dir = MoveLibrary.dir();
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(saved + ".json"), new GsonBuilder().create().toJson(root));
            MoveLibrary.clear();
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("Movimiento \"" + saved + "\" guardado (" + FRAMES.size() + " ticks). Ya aparece en la lista de emotes."), false);
            }
        } catch (Exception e) {
            AnnisNpc.LOGGER.warn("No se pudo guardar el movimiento", e);
        }
        FRAMES.clear();
    }

    public static void cancel() {
        restorePerspective();
        name = null;
        FRAMES.clear();
    }

    private static void restorePerspective() {
        if (previousPerspective != null) {
            MinecraftClient.getInstance().options.setPerspective(previousPerspective);
            previousPerspective = null;
        }
    }
}
