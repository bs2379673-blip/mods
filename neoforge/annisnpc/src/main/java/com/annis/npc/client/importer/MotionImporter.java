package com.annis.npc.client.importer;

import com.annis.npc.client.MoveLibrary;
import com.annis.npc.client.MoveRecorder;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.annis.npc.compat.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Importa bailes y capturas de movimiento a movimientos de NPC:
 * BVH (Mixamo, Blender, Rokoko, CMU...) y MMD (.vmd). Los archivos van en config/annisnpc/import/.
 */
public final class MotionImporter {
    /** Limite para no crear archivos enormes: 10 minutos a 20 fps. */
    private static final int MAX_FRAMES = 20 * 60 * 10;

    private MotionImporter() {
    }

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("annisnpc").resolve("import");
    }

    public static List<String> list() {
        List<String> out = new ArrayList<>();
        Path dir = dir();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                s.map(p -> p.getFileName().toString())
                        .filter(n -> {
                            String l = n.toLowerCase(Locale.ROOT);
                            return l.endsWith(".bvh") || l.endsWith(".vmd");
                        })
                        .sorted()
                        .forEach(out::add);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    /** Nombre de movimiento a partir del nombre de archivo. */
    public static String defaultName(String file) {
        String base = file.replaceAll("\\.[^.]+$", "");
        String clean = MoveRecorder.sanitize(base);
        return clean.isEmpty() ? "baile_" + Integer.toHexString(file.hashCode() & 0xffff) : clean;
    }

    /** Convierte el archivo y guarda el movimiento. Devuelve un resumen para el chat. */
    public static String importFile(String fileName, String moveName, boolean loop) throws Exception {
        Path file = dir().resolve(fileName).normalize();
        if (!file.startsWith(dir()) || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("No existe " + fileName + " en " + dir());
        }
        String name = MoveRecorder.sanitize(moveName == null || moveName.isEmpty() ? defaultName(fileName) : moveName);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Nombre no valido");
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        List<Pose> poses = new ArrayList<>();
        PoseConverter.Result result;
        if (lower.endsWith(".bvh")) {
            BvhReader r = BvhReader.read(file);
            for (int i = 0; i < r.frames(); i++) {
                poses.add(r.pose(i));
            }
            if (poses.size() < 2) {
                throw new IllegalArgumentException("El BVH no tiene fotogramas");
            }
            result = PoseConverter.convert(poses, 1.0 / r.frameTime, 0, null);
        } else if (lower.endsWith(".vmd")) {
            VmdReader r = VmdReader.read(file);
            // MMD va a 30 fps
            for (int f = 0; f <= r.lastFrame; f++) {
                poses.add(r.pose(f));
            }
            if (poses.size() < 2) {
                throw new IllegalArgumentException("El .vmd no tiene fotogramas");
            }
            result = PoseConverter.convert(poses, 30.0, -1, new double[]{0, 0, -1});
        } else {
            throw new IllegalArgumentException("Formato no soportado (usa .bvh o .vmd)");
        }

        List<float[]> frames = result.frames();
        int count = Math.min(frames.size(), MAX_FRAMES);
        JsonObject root = new JsonObject();
        root.addProperty("version", 2);
        root.addProperty("name", name);
        root.addProperty("loop", loop);
        root.addProperty("fps", 20);
        root.addProperty("source", fileName);
        JsonArray arr = new JsonArray();
        JsonArray yaw = new JsonArray();
        for (int i = 0; i < count; i++) {
            JsonArray a = new JsonArray();
            for (float x : frames.get(i)) {
                a.add(Math.round(x * 10000f) / 10000f);
            }
            arr.add(a);
            yaw.add(Math.round(result.yaw()[i] * 10000f) / 10000f);
        }
        root.add("frames", arr);
        root.add("yaw", yaw);
        Files.createDirectories(MoveLibrary.dir());
        Files.writeString(MoveLibrary.dir().resolve(name + ".json"), new GsonBuilder().create().toJson(root));
        MoveLibrary.clear();
        float turns = Math.abs(result.yaw()[count - 1] - result.yaw()[0]) / (float) (2 * Math.PI);
        return String.format(Locale.ROOT, "\"%s\" importado: %.1f s%s", name, count / 20.0,
                turns > 0.2f ? String.format(Locale.ROOT, ", %.1f vueltas", turns) : "");
    }
}
