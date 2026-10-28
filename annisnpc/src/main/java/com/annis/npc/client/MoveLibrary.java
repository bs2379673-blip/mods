package com.annis.npc.client;

import com.annis.npc.AnnisNpc;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.ModelPart;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Movimientos grabados (config/annisnpc/moves/*.json): la pose de cada hueso tick a tick.
 * Se reproducen con interpolacion Catmull-Rom para que queden suaves entre ticks.
 */
public final class MoveLibrary {
    /** Orden de huesos en cada frame; cada hueso son 6 valores: pitch, yaw, roll, pivotX, pivotY, pivotZ. */
    public static final int PARTS = 6;
    public static final int VALUES = PARTS * 6;

    public record Move(float[][] frames, boolean loop) {
    }

    private static final Map<String, Move> CACHE = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();
    private static List<String> listCache;

    private MoveLibrary() {
    }

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("annisnpc").resolve("moves");
    }

    public static boolean has(String name) {
        return get(name) != null;
    }

    public static Move get(String name) {
        if (name == null || name.isEmpty() || !name.matches("[a-z0-9_-]+")) {
            return null;
        }
        Move cached = CACHE.get(name);
        if (cached != null) {
            return cached;
        }
        if (MISSING.contains(name)) {
            return null;
        }
        Path file = dir().resolve(name + ".json");
        if (!Files.isRegularFile(file)) {
            MISSING.add(name);
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            boolean loop = !root.has("loop") || root.get("loop").getAsBoolean();
            JsonArray arr = root.getAsJsonArray("frames");
            List<float[]> frames = new ArrayList<>();
            for (JsonElement el : arr) {
                JsonArray f = el.getAsJsonArray();
                if (f.size() < VALUES) {
                    continue;
                }
                float[] v = new float[VALUES];
                for (int i = 0; i < VALUES; i++) {
                    v[i] = f.get(i).getAsFloat();
                }
                frames.add(v);
            }
            if (frames.isEmpty()) {
                MISSING.add(name);
                return null;
            }
            Move move = new Move(frames.toArray(new float[0][]), loop);
            CACHE.put(name, move);
            return move;
        } catch (Exception e) {
            AnnisNpc.LOGGER.warn("No se pudo leer el movimiento {}", file, e);
            MISSING.add(name);
            return null;
        }
    }

    public static List<String> list() {
        if (listCache != null) {
            return listCache;
        }
        List<String> names = new ArrayList<>();
        Path dir = dir();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> stream = Files.list(dir)) {
                stream.map(p -> p.getFileName().toString())
                        .filter(n -> n.endsWith(".json"))
                        .map(n -> n.substring(0, n.length() - 5).toLowerCase(Locale.ROOT))
                        .filter(n -> n.matches("[a-z0-9_-]+"))
                        .sorted()
                        .forEach(names::add);
            } catch (Exception e) {
                AnnisNpc.LOGGER.warn("No se pudo listar los movimientos", e);
            }
        }
        listCache = names;
        return names;
    }

    public static void clear() {
        CACHE.clear();
        MISSING.clear();
        listCache = null;
    }

    private static float catmull(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    /** Pone en el modelo la pose del movimiento en el tiempo t (ticks, con decimales). */
    public static void apply(Move move, NpcModel m, float t) {
        float[][] fr = move.frames();
        int n = fr.length;
        float ft;
        if (move.loop()) {
            ft = ((t % n) + n) % n;
        } else {
            ft = Math.max(0.0f, Math.min(t, n - 1));
        }
        int i1 = (int) Math.floor(ft);
        float f = ft - i1;
        int i0 = idx(i1 - 1, n, move.loop());
        int i2 = idx(i1 + 1, n, move.loop());
        int i3 = idx(i1 + 2, n, move.loop());
        i1 = idx(i1, n, move.loop());
        ModelPart[] parts = {m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
        for (int p = 0; p < PARTS; p++) {
            int b = p * 6;
            float[] v = new float[6];
            for (int k = 0; k < 6; k++) {
                v[k] = catmull(fr[i0][b + k], fr[i1][b + k], fr[i2][b + k], fr[i3][b + k], f);
            }
            ModelPart part = parts[p];
            part.pitch = v[0];
            part.yaw = v[1];
            part.roll = v[2];
            part.pivotX = v[3];
            part.pivotY = v[4];
            part.pivotZ = v[5];
        }
    }

    private static int idx(int i, int n, boolean loop) {
        if (loop) {
            return ((i % n) + n) % n;
        }
        return Math.max(0, Math.min(n - 1, i));
    }
}
