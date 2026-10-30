package com.annis.npc.client;

import com.annis.npc.AnnisNpc;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

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
    private static CameraType previousPerspective;

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
    public static boolean start(String rawName, int seconds, boolean loopMove, Player targetPlayer) {
        Minecraft mc = Minecraft.getInstance();
        String clean = sanitize(rawName);
        if (clean.isEmpty() || mc.player == null || active()) {
            return false;
        }
        name = clean;
        loop = loopMove;
        target = targetPlayer == null ? mc.player.getUUID() : targetPlayer.getUUID();
        countdown = 60;
        maxTicks = Math.max(1, Math.min(120, seconds)) * 20;
        recordedTicks = 0;
        last = null;
        FRAMES.clear();
        if (target.equals(mc.player.getUUID())) {
            previousPerspective = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else {
            previousPerspective = null;
        }
        return true;
    }

    public static void tick() {
        if (!active()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            cancel();
            return;
        }
        if (countdown > 0) {
            if (countdown % 20 == 0) {
                mc.player.displayClientMessage(Component.literal("Grabando \"" + name + "\" en " + countdown / 20 + "..."), true);
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
            mc.player.displayClientMessage(Component.literal("● REC " + name + "  " + recordedTicks / 20 + "/" + maxTicks / 20 + " s"), true);
        }
        if (recordedTicks >= maxTicks) {
            stop();
        }
    }

    /** Llamado desde la capa de render del jugador, ya con la pose final (EMF incluido). */
    public static void capture(Player entity, PlayerModel<?> model) {
        if (!capturing() || capturedThisTick || !entity.getUUID().equals(target)) {
            return;
        }
        ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        float[] v = new float[MoveLibrary.VALUES];
        for (int p = 0; p < parts.length; p++) {
            ModelPart part = parts[p];
            int b = p * 6;
            v[b] = part.xRot;
            v[b + 1] = part.yRot;
            v[b + 2] = part.zRot;
            v[b + 3] = part.x;
            v[b + 4] = part.y;
            v[b + 5] = part.z;
        }
        FRAMES.add(v);
        last = v;
        capturedThisTick = true;
    }

    public static void stop() {
        if (!active()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        String saved = name;
        restorePerspective();
        name = null;
        if (FRAMES.isEmpty()) {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("No se capturo nada (el jugador tiene que verse en pantalla)"), false);
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
                mc.player.displayClientMessage(Component.literal("Movimiento \"" + saved + "\" guardado (" + FRAMES.size() + " ticks). Ya aparece en la lista de emotes."), false);
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
            Minecraft.getInstance().options.setCameraType(previousPerspective);
            previousPerspective = null;
        }
    }
}
