package com.annis.npc.client.punchy;

import com.annis.npc.AnnisNpc;
import com.annis.npc.client.Activities;
import com.annis.npc.client.NpcModel;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.model.ModelPart;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.io.Reader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Animaciones de los paquetes de recursos de Punchy Studio (y de paquetes hechos para Annis):
 * assets/punchy_3rd_person/animations/*.json y assets/annisnpc/animations/*.json.
 * Cada una queda disponible como actividad "punchy:<nombre>" para NPC, aldeanos y jugadores,
 * sincronizada por el servidor como cualquier animacion de Annis.
 */
public final class PunchyClips {
    public static final String PREFIX = "punchy:";
    private static final Set<String> NAMESPACES = Set.of("punchy_3rd_person", "punchy_moves", "annisnpc");
    private static final Map<String, BedrockClip> CLIPS = new HashMap<>();
    /** De que paquete vino cada animacion (para preferir las retocadas al paquete base de Annis). */
    private static final Map<String, String> ORIGIN = new HashMap<>();

    private PunchyClips() {
    }

    public static void init() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.of(AnnisNpc.MOD_ID, "punchy_clips");
            }

            @Override
            public void reload(ResourceManager manager) {
                load(manager);
            }
        });
    }

    /** La animacion con ese nombre (sin el prefijo), o null. */
    public static synchronized BedrockClip get(String name) {
        return name == null ? null : CLIPS.get(name.toLowerCase(Locale.ROOT));
    }

    public static synchronized java.util.List<String> names() {
        return new java.util.ArrayList<>(CLIPS.keySet());
    }

    private static synchronized void load(ResourceManager manager) {
        for (String old : CLIPS.keySet()) {
            Activities.unregister(PREFIX + old);
        }
        CLIPS.clear();
        ORIGIN.clear();
        Map<Identifier, Resource> found = manager.findResources("animations", id -> id.getPath().endsWith(".json"));
        int files = 0;
        for (Map.Entry<Identifier, Resource> en : found.entrySet()) {
            if (!NAMESPACES.contains(en.getKey().getNamespace())) {
                continue;
            }
            try (Reader r = en.getValue().getReader()) {
                JsonElement root = JsonParser.parseReader(r);
                if (!root.isJsonObject()) {
                    continue;
                }
                files++;
                JsonObject o = root.getAsJsonObject();
                JsonObject anims = o.has("animations") && o.get("animations").isJsonObject() ? o.getAsJsonObject("animations")
                        : o.has("clips") && o.get("clips").isJsonObject() ? o.getAsJsonObject("clips") : o;
                for (Map.Entry<String, JsonElement> a : anims.entrySet()) {
                    if (!a.getValue().isJsonObject()) {
                        continue;
                    }
                    String name = a.getKey().toLowerCase(Locale.ROOT);
                    BedrockClip clip = BedrockClip.read(name, a.getValue().getAsJsonObject());
                    if (clip != null) {
                        String pack = String.valueOf(en.getValue().getPackId());
                        String prev = ORIGIN.get(name);
                        // si la misma animacion esta en el paquete base y en otro (retocada), gana la retocada
                        if (prev == null || isBasePack(prev) && !isBasePack(pack)) {
                            CLIPS.put(name, clip);
                            ORIGIN.put(name, pack);
                        }
                    }
                }
            } catch (Exception ex) {
                AnnisNpc.LOGGER.warn("No se pudo leer la animacion {}: {}", en.getKey(), ex.toString());
            }
        }
        for (Map.Entry<String, BedrockClip> c : CLIPS.entrySet()) {
            Activities.register(PREFIX + c.getKey(), custom(c.getValue()));
        }
        AnnisNpc.LOGGER.info("Animaciones de paquetes (Punchy / Annis): {} en {} archivos", CLIPS.size(), files);
    }

    private static boolean isBasePack(String packId) {
        String p = packId.toLowerCase(Locale.ROOT).replace(' ', '_');
        return p.contains("annis_animaciones");
    }

    /** La animacion como actividad de Annis (huesos del cuerpo y pose de cuerpo entero). */
    public static Activities.Custom custom(BedrockClip clip) {
        return new Activities.Custom() {
            @Override
            public void apply(NpcModel m, float t) {
                PunchyClips.apply(clip, m, t);
            }

            @Override
            public Activities.RootPose root(float t) {
                return PunchyClips.root(clip, t);
            }

            @Override
            public boolean springy() {
                // lo que se hizo en el editor se respeta tal cual
                return false;
            }
        };
    }

    private static final String[] BONES = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};

    /** t = ticks desde el inicio (reloj comun). */
    public static void apply(BedrockClip clip, NpcModel m, float t) {
        float secs = t / 20.0f;
        float time = clip.clipTime(secs);
        ModelPart[] parts = {m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
        float[] v = new float[3];
        for (int i = 0; i < BONES.length; i++) {
            BedrockClip.Bone b = clip.bones.get(BONES[i]);
            if (b == null) {
                continue;
            }
            ModelPart p = parts[i];
            if (b.rotation != null && b.rotation.sample(time, secs, v)) {
                p.pitch = (float) Math.toRadians(v[0]);
                p.yaw = (float) Math.toRadians(v[1]);
                p.roll = (float) Math.toRadians(v[2]);
            }
            if (b.position != null && b.position.sample(time, secs, v)) {
                // Bedrock: y hacia arriba; el modelo de Java: y hacia abajo
                p.pivotX += v[0];
                p.pivotY -= v[1];
                p.pivotZ += v[2];
            }
            // (la escala no se usa: el modelo del juego no la vuelve a 1 por su cuenta)
        }
    }

    /** Hueso "root": todo el cuerpo, girando alrededor de los pies. */
    public static Activities.RootPose root(BedrockClip clip, float t) {
        BedrockClip.Bone b = clip.bones.get("root");
        if (b == null) {
            return null;
        }
        float secs = t / 20.0f;
        float time = clip.clipTime(secs);
        float[] r = new float[3], p = new float[3];
        boolean hasR = b.rotation != null && b.rotation.sample(time, secs, r);
        boolean hasP = b.position != null && b.position.sample(time, secs, p);
        if (!hasR && !hasP) {
            return null;
        }
        // en el espacio del dibujo (antes de dar vuelta el modelo) X e Y cambian de signo
        float pitch = (float) -Math.toRadians(r[0]);
        float yaw = (float) -Math.toRadians(r[1]);
        float roll = (float) Math.toRadians(r[2]);
        Vector3f offset = new Vector3f(-p[0] / 16.0f, p[1] / 16.0f, p[2] / 16.0f);
        // Annis gira alrededor de la cadera (0.7); Punchy alrededor de los pies: se compensa
        Matrix3f m = new Matrix3f().rotateZ(roll).rotateY(yaw).rotateX(pitch);
        Vector3f h = new Vector3f(0, 0.7f, 0);
        Vector3f rh = m.transform(new Vector3f(h));
        Vector3f t1 = offset.sub(h).add(rh);
        return new Activities.RootPose(pitch, yaw, roll, -t1.z, t1.y, -t1.x);
    }
}
