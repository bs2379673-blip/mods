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
    /** Formato articulado (v3): 16 valores mas = brazo/pierna (pitch, yaw, roll, doblez) x 4. */
    public static final int JOINT_VALUES = VALUES + 16;
    /** Formato con espalda (v4): pecho (pitch, yaw, roll) + cintura relativa al pecho (pitch, yaw, roll). */
    public static final int SPINE_VALUES = JOINT_VALUES + 6;

    /** yaw: giro del cuerpo entero por frame (radianes, positivo = hacia su izquierda), puede ser null. */
    public record Move(float[][] frames, boolean loop, float[] yaw) {
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
                int count = f.size() >= SPINE_VALUES ? SPINE_VALUES : f.size() >= JOINT_VALUES ? JOINT_VALUES : VALUES;
                float[] v = new float[count];
                for (int i = 0; i < count; i++) {
                    v[i] = f.get(i).getAsFloat();
                }
                frames.add(v);
            }
            if (frames.isEmpty()) {
                MISSING.add(name);
                return null;
            }
            float[] yaw = null;
            if (root.has("yaw")) {
                JsonArray ya = root.getAsJsonArray("yaw");
                if (ya.size() >= frames.size()) {
                    yaw = new float[frames.size()];
                    for (int i = 0; i < yaw.length; i++) {
                        yaw[i] = ya.get(i).getAsFloat();
                    }
                }
            }
            Move move = new Move(frames.toArray(new float[0][]), loop, yaw);
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
        boolean joints = m.jointed && fr[i1].length >= JOINT_VALUES && fr[i0].length >= JOINT_VALUES
                && fr[i2].length >= JOINT_VALUES && fr[i3].length >= JOINT_VALUES;
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
        if (joints) {
            // brazos y piernas: direccion del tramo de arriba + doblez de codo/rodilla
            ModelPart[] limbs = {m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
            ModelPart[] lowers = {m.rightForearm, m.leftForearm, m.rightShin, m.leftShin};
            for (int l = 0; l < 4; l++) {
                int b = VALUES + l * 4;
                float[] v = new float[4];
                for (int k = 0; k < 4; k++) {
                    v[k] = catmull(fr[i0][b + k], fr[i1][b + k], fr[i2][b + k], fr[i3][b + k], f);
                }
                limbs[l].pitch = v[0];
                limbs[l].yaw = v[1];
                limbs[l].roll = v[2];
                lowers[l].pitch = v[3];
                lowers[l].yaw = 0.0f;
                lowers[l].roll = 0.0f;
            }
            boolean spine = fr[i0].length >= SPINE_VALUES && fr[i1].length >= SPINE_VALUES
                    && fr[i2].length >= SPINE_VALUES && fr[i3].length >= SPINE_VALUES;
            if (spine) {
                float[] v = new float[6];
                for (int k = 0; k < 6; k++) {
                    int b = JOINT_VALUES + k;
                    v[k] = catmull(fr[i0][b], fr[i1][b], fr[i2][b], fr[i3][b], f);
                }
                applySpine(m, v);
            }
        }
    }

    private static float[] mat(float pitch, float yaw, float roll) {
        // R = Rz(roll) * Ry(yaw) * Rx(pitch), por filas
        float cx = (float) Math.cos(pitch), sx = (float) Math.sin(pitch);
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float cz = (float) Math.cos(roll), sz = (float) Math.sin(roll);
        return new float[]{
                cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx,
                sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx,
                -sy, cy * sx, cy * cx};
    }

    private static float[] mul(float[] m, float x, float y, float z) {
        return new float[]{m[0] * x + m[1] * y + m[2] * z, m[3] * x + m[4] * y + m[5] * z, m[6] * x + m[7] * y + m[8] * z};
    }

    /**
     * Espalda: el pecho (body) y la cintura (hijo) toman su giro, y la parte de arriba entera
     * (cabeza, torso y brazos) se desplaza para que la cintura acabe justo encima de las piernas.
     * Asi la cadera queda fija y el pecho se dobla sobre ella como una persona de verdad.
     */
    private static void applySpine(NpcModel m, float[] v) {
        float bob = m.head.pivotY; // los frames guardan el rebote de cadera en el pivote de la cabeza
        m.body.pitch = v[0];
        m.body.yaw = v[1];
        m.body.roll = v[2];
        m.waist.pitch = v[3];
        m.waist.yaw = v[4];
        m.waist.roll = v[5];
        float[] rc = mat(v[0], v[1], v[2]);
        float[] rw = mat(v[3], v[4], v[5]);
        float[] low = mul(rw, 0.0f, JointModel.SPLIT, 0.0f);
        float[] bottom = mul(rc, low[0], JointModel.SPLIT + low[1], low[2]);
        float px = m.body.pivotX + bottom[0];
        float py = m.body.pivotY + bottom[1];
        float pz = m.body.pivotZ + bottom[2];
        float hipY = 12.0f + bob;
        float dx = -px;
        float dy = hipY - py;
        float dz = -pz;
        // hombros pegados al pecho girado (si el pecho se tuerce o se inclina, los brazos van con el)
        float[] offR = {m.rightArm.pivotX - m.body.pivotX, m.rightArm.pivotY - m.body.pivotY, m.rightArm.pivotZ - m.body.pivotZ};
        float[] offL = {m.leftArm.pivotX - m.body.pivotX, m.leftArm.pivotY - m.body.pivotY, m.leftArm.pivotZ - m.body.pivotZ};
        m.body.pivotX += dx;
        m.body.pivotY += dy;
        m.body.pivotZ += dz;
        m.head.pivotX = m.body.pivotX;
        m.head.pivotY = m.body.pivotY;
        m.head.pivotZ = m.body.pivotZ;
        float[] r = mul(rc, offR[0], offR[1], offR[2]);
        m.rightArm.pivotX = m.body.pivotX + r[0];
        m.rightArm.pivotY = m.body.pivotY + r[1];
        m.rightArm.pivotZ = m.body.pivotZ + r[2];
        float[] l = mul(rc, offL[0], offL[1], offL[2]);
        m.leftArm.pivotX = m.body.pivotX + l[0];
        m.leftArm.pivotY = m.body.pivotY + l[1];
        m.leftArm.pivotZ = m.body.pivotZ + l[2];
        // las piernas bajan con la cadera (las rodillas dobladas acortan la pierna)
        m.rightLeg.pivotY = hipY;
        m.leftLeg.pivotY = hipY;
    }

    /** Giro del cuerpo entero en el tiempo t (0 si el movimiento no tiene). */
    public static float yawAt(Move move, float t) {
        float[] y = move.yaw();
        if (y == null || y.length == 0) {
            return 0.0f;
        }
        int n = y.length;
        float ft = move.loop() ? ((t % n) + n) % n : Math.max(0.0f, Math.min(t, n - 1));
        int i0 = (int) Math.floor(ft);
        int i1 = idx(i0 + 1, n, move.loop());
        float f = ft - i0;
        float a = y[idx(i0, n, move.loop())];
        float b = y[i1];
        if (move.loop() && i1 == 0) {
            b = a; // al dar la vuelta al bucle no interpolar el salto
        }
        return a + (b - a) * f;
    }

    private static int idx(int i, int n, boolean loop) {
        if (loop) {
            return ((i % n) + n) % n;
        }
        return Math.max(0, Math.min(n - 1, i));
    }
}
