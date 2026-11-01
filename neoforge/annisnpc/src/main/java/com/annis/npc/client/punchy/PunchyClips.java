package com.annis.npc.client.punchy;

import com.annis.npc.AnnisNpc;
import com.annis.npc.client.Activities;
import com.annis.npc.client.NpcModel;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.annis.npc.compat.ResourceManagerHelper;
import com.annis.npc.compat.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
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
    /** Acciones -> animacion (assets/annisnpc/acciones.json de los paquetes activos). */
    private static final Map<String, String> ACTIONS = new HashMap<>();

    /** De que paquete vino cada animacion (para preferir las retocadas al paquete base de Annis). */
    private static final Map<String, String> ORIGIN = new HashMap<>();

    private PunchyClips() {
    }

    public static void init() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return ResourceLocation.fromNamespaceAndPath(AnnisNpc.MOD_ID, "punchy_clips");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
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
        ACTIONS.clear();
        // acciones: el paquete de mas arriba gana
        try {
            for (Resource res : manager.getResourceStack(ResourceLocation.fromNamespaceAndPath("annisnpc", "acciones.json"))) {
                try (Reader r = res.openAsReader()) {
                    JsonElement e = JsonParser.parseReader(r);
                    if (e.isJsonObject()) {
                        for (Map.Entry<String, JsonElement> a : e.getAsJsonObject().entrySet()) {
                            if (a.getValue().isJsonPrimitive()) {
                                String clip = a.getValue().getAsString().toLowerCase(Locale.ROOT);
                                if (clip.startsWith(PREFIX)) {
                                    clip = clip.substring(PREFIX.length());
                                }
                                ACTIONS.put(a.getKey().toLowerCase(Locale.ROOT), clip);
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            AnnisNpc.LOGGER.warn("No se pudo leer acciones.json: {}", ex.toString());
        }
        Map<ResourceLocation, Resource> found = manager.listResources("animations", id -> id.getPath().endsWith(".json"));
        int files = 0;
        for (Map.Entry<ResourceLocation, Resource> en : found.entrySet()) {
            if (!NAMESPACES.contains(en.getKey().getNamespace())) {
                continue;
            }
            try (Reader r = en.getValue().openAsReader()) {
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
                        String pack = String.valueOf(en.getValue().sourcePackId());
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
        AnnisNpc.LOGGER.info("Animaciones de paquetes (Punchy / Annis): {} en {} archivos; acciones asignadas: {}",
                CLIPS.size(), files, ACTIONS.size());
    }

    /** Animacion asignada a una accion ("sentarse", "pescar", "quieto", "caminar"...), o null. */
    public static synchronized BedrockClip action(String action) {
        if (action == null) {
            return null;
        }
        String clip = ACTIONS.get(action.toLowerCase(Locale.ROOT));
        return clip == null ? null : CLIPS.get(clip);
    }

    /** El clip asignado a la accion, o si no hay, el de Punchy Moves / Fluid & Foolish con ese nombre. */
    private static BedrockClip actionOr(String action, String punchy) {
        BedrockClip c = action(action);
        return c != null ? c : get(punchy);
    }

    /**
     * Caminar, correr, agacharse y quieto con las animaciones de Punchy Moves (y del paquete
     * Fluid &amp; Foolish si esta activo): para los NPC de Annis y los aldeanos de MCA. Como en
     * Punchy, se suman sobre la pose de descanso y el paso sigue la distancia recorrida.
     * Devuelve true si las uso (si no hay animaciones, queda la capa propia de Annis).
     */
    public static boolean locomotion(net.minecraft.client.model.HumanoidModel<?> m, boolean crouching, float limbAngle,
                                     float limbDistance, float ticks, boolean sprinting, float headYawRad, float headPitchRad) {
        BedrockClip idle = actionOr("quieto", "idle");
        boolean custom = action("caminar") != null;
        BedrockClip walk = actionOr("caminar", "normal_forward");
        BedrockClip walkAdd = custom ? null : get("nf_add");
        BedrockClip run = action("correr");
        BedrockClip runAdd = run == null ? get("sprint_add") : null;
        BedrockClip sneak = actionOr("agachado", "sneaking");
        if (idle == null && walk == null) {
            return false;
        }
        ModelPart[] parts = {m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
        float[][] rot = new float[6][3], pos = new float[6][3];
        // velocidad en bloques por tick (el juego guarda 4 veces eso, hasta 1)
        float speed = limbDistance / 4.0f;
        float amp = Math.min(1.2f, speed / 0.215f);
        float w = Math.min(1.0f, amp);
        float secs = ticks / 20.0f;
        if (idle != null && w < 1.0f) {
            add(idle, idle.clipTime(secs), secs, 1.0f - w, rot, pos);
        }
        if (w > 0.0f) {
            // un ciclo de paso cada 1.67 bloques, como Punchy
            float cycle = (limbAngle / 4.0f) / 1.67f;
            cycle -= (float) Math.floor(cycle);
            BedrockClip stride = sprinting && run != null ? run : walk;
            if (stride != null) {
                float t = cycle * stride.length;
                add(stride, t, t, w * Math.max(1.0f, amp), rot, pos);
            }
            if (walkAdd != null && stride == walk) {
                float t = cycle * walkAdd.length;
                add(walkAdd, t, t, w, rot, pos);
            }
            if (sprinting && runAdd != null) {
                float t = runAdd.length > 0 ? cycle * runAdd.length : 0;
                add(runAdd, t, t, w, rot, pos);
            }
        }
        if (crouching && sneak != null) {
            float t = sneak.length;
            add(sneak, t, t, 1.0f, rot, pos);
        }
        // golpeando, apuntando con arco/ballesta, comiendo...: los brazos y el torso quedan como los puso el juego
        boolean armsBusy = m.attackTime > 0.0f || busy(m.rightArmPose) || busy(m.leftArmPose);
        for (int i = 0; i < 6; i++) {
            if (armsBusy && i >= 1 && i <= 3) {
                continue;
            }
            ModelPart p = parts[i];
            net.minecraft.client.model.geom.PartPose init = p.getInitialPose();
            p.xRot = init.xRot + rot[i][0];
            p.yRot = init.yRot + rot[i][1];
            p.zRot = init.zRot + rot[i][2];
            p.x = init.x + pos[i][0];
            p.y = init.y - pos[i][1];
            p.z = init.z + pos[i][2];
        }
        m.head.xRot += headPitchRad;
        m.head.yRot += headYawRad;
        m.hat.copyFrom(m.head);
        return true;
    }

    private static boolean busy(net.minecraft.client.model.HumanoidModel.ArmPose pose) {
        return pose != null && pose != net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY
                && pose != net.minecraft.client.model.HumanoidModel.ArmPose.ITEM;
    }

    private static void add(BedrockClip clip, float time, float secs, float weight, float[][] rot, float[][] pos) {
        float[] r = new float[3], p = new float[3];
        for (int i = 0; i < BONES.length; i++) {
            int got = sample(clip, BONES[i], time, secs, r, p);
            if ((got & 1) != 0) {
                for (int c = 0; c < 3; c++) {
                    rot[i][c] += r[c] * weight;
                }
            }
            if ((got & 2) != 0) {
                for (int c = 0; c < 3; c++) {
                    pos[i][c] += p[c] * weight;
                }
            }
        }
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
        float[] r = new float[3], p = new float[3];
        for (int i = 0; i < BONES.length; i++) {
            int got = sample(clip, BONES[i], time, secs, r, p);
            ModelPart part = parts[i];
            if ((got & 1) != 0) {
                part.xRot = r[0];
                part.yRot = r[1];
                part.zRot = r[2];
            }
            if ((got & 2) != 0) {
                // Bedrock: y hacia arriba; el modelo de Java: y hacia abajo
                part.x += p[0];
                part.y -= p[1];
                part.z += p[2];
            }
            // (la escala no se usa: el modelo del juego no la vuelve a 1 por su cuenta)
        }
    }

    private static final Vector3f ZERO = new Vector3f();
    private static final Vector3f RIGHT_ARM_PIVOT = new Vector3f(-5, 2, 0);
    private static final Vector3f LEFT_ARM_PIVOT = new Vector3f(5, 2, 0);

    /**
     * Pose de un hueso: rotacion en radianes (rot) y desplazamiento en pixeles con Y hacia arriba
     * (pos). Devuelve 1 si hay rotacion, 2 si hay posicion (o ambas). Como Punchy Moves, la
     * "cintura" (waist) arrastra al torso, la cabeza y los brazos.
     */
    static int sample(BedrockClip clip, String bone, float time, float secs, float[] rot, float[] pos) {
        float[] v = new float[3];
        int got = 0;
        rot[0] = rot[1] = rot[2] = 0;
        pos[0] = pos[1] = pos[2] = 0;
        BedrockClip.Bone b = clip.bones.get(bone);
        if (b != null && b.rotation != null && b.rotation.sample(time, secs, v)) {
            rot[0] = v[0];
            rot[1] = v[1];
            rot[2] = v[2];
            got |= 1;
        }
        if (b != null && b.position != null && b.position.sample(time, secs, v)) {
            pos[0] = v[0];
            pos[1] = v[1];
            pos[2] = v[2];
            got |= 2;
        }
        BedrockClip.Bone w = bone.equals("body") || bone.equals("head") || bone.endsWith("_arm") ? clip.bones.get("waist") : null;
        if (w != null) {
            float[] wr = new float[3], wp = new float[3];
            boolean hasR = w.rotation != null && w.rotation.sample(time, secs, wr);
            boolean hasP = w.position != null && w.position.sample(time, secs, wp);
            if (hasR || hasP) {
                Quaternionf qw = new Quaternionf().rotationZYX((float) Math.toRadians(wr[2]), (float) Math.toRadians(wr[1]),
                        (float) Math.toRadians(wr[0]));
                Quaternionf q = new Quaternionf(qw).mul(new Quaternionf().rotationZYX((float) Math.toRadians(rot[2]),
                        (float) Math.toRadians(rot[1]), (float) Math.toRadians(rot[0])));
                Vector3f e = q.getEulerAnglesZYX(new Vector3f());
                rot[0] = (float) Math.toDegrees(e.x);
                rot[1] = (float) Math.toDegrees(e.y);
                rot[2] = (float) Math.toDegrees(e.z);
                Vector3f pivot = bone.equals("right_arm") ? RIGHT_ARM_PIVOT : bone.equals("left_arm") ? LEFT_ARM_PIVOT : ZERO;
                Vector3f moved = qw.transform(new Vector3f(pivot)).sub(pivot)
                        .add(qw.transform(new Vector3f(pos[0], pos[1], pos[2]))).add(wp[0], wp[1], wp[2]);
                pos[0] = moved.x;
                pos[1] = moved.y;
                pos[2] = moved.z;
                got |= 3;
            }
        }
        if ((got & 1) != 0) {
            rot[0] = (float) Math.toRadians(rot[0]);
            rot[1] = (float) Math.toRadians(rot[1]);
            rot[2] = (float) Math.toRadians(rot[2]);
        }
        return got;
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
