package com.annis.npc.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.util.Mth;

/**
 * Actividades integradas (no necesitan archivos): poses y movimientos en bucle estilo "escena".
 * Se escriben sobre el modelo y NpcAnimState las mezcla suavemente con la capa procedural.
 */
public final class Activities {
    public static final List<String> NAMES = List.of(
            "saludar", "charlar", "pescar", "mercado", "combate",
            "sentarse", "bailar", "pensar", "aplaudir",
            "tocar_tambor", "desfile_tambor", "tocar_trompeta", "saludo_sombrero",
            "presentar", "dirigir", "marchar");

    private static final float PI = (float) Math.PI;

    /**
     * Pose del cuerpo entero (tumbarse, ser llevado en brazos...): giros en radianes alrededor de la
     * cadera y desplazamiento en bloques (forward = hacia donde mira, up = arriba, left = su izquierda).
     */
    public record RootPose(float pitch, float yaw, float roll, float forward, float up, float left) {
        public RootPose scaled(float w) {
            return new RootPose(pitch * w, yaw * w, roll * w, forward * w, up * w, left * w);
        }
    }

    /** Animacion registrada por otro mod (por ejemplo Annis Interacciones). */
    public interface Custom {
        void apply(NpcModel m, float t);

        default RootPose root(float t) {
            return null;
        }

        /** Suavizado con inercia (resortes) por encima de la animacion: mas vivo, menos rigido. */
        default boolean springy() {
            return true;
        }

        /** false = esta parte (0 cabeza, 1 torso, 2-3 brazos, 4-5 piernas) pasa tal cual, sin inercia. */
        default boolean springPart(int i) {
            return true;
        }
    }

    private static final Map<String, Custom> CUSTOM = new LinkedHashMap<>();
    private static net.minecraft.world.entity.LivingEntity current;

    /** Entidad que se esta animando ahora mismo (para animaciones que miran al companero). */
    public static net.minecraft.world.entity.LivingEntity current() {
        return current;
    }

    public static void setCurrent(net.minecraft.world.entity.LivingEntity e) {
        current = e;
    }

    private Activities() {
    }

    /** API: registrar una animacion procedural con nombre (se puede usar en NPC, aldeanos y jugadores). */
    public static synchronized void register(String name, Custom custom) {
        CUSTOM.put(name.toLowerCase(Locale.ROOT), custom);
    }

    public static synchronized void unregister(String name) {
        CUSTOM.remove(name.toLowerCase(Locale.ROOT));
    }

    /** Integradas + registradas por otros mods. */
    public static synchronized List<String> names() {
        List<String> all = new ArrayList<>(NAMES);
        all.addAll(CUSTOM.keySet());
        return all;
    }

    public static boolean has(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        return NAMES.contains(n) || CUSTOM.containsKey(n);
    }

    /** true si la animacion pide suavizado con inercia. */
    public static boolean springy(String name) {
        if (com.annis.npc.client.punchy.PunchyClips.action(name) != null) {
            return false;
        }
        Custom c = name == null ? null : CUSTOM.get(name.toLowerCase(Locale.ROOT));
        return c != null && c.springy();
    }

    /** true si la parte i de la animacion se suaviza con inercia. */
    public static boolean springPart(String name, int i) {
        Custom c = name == null ? null : CUSTOM.get(name.toLowerCase(Locale.ROOT));
        return c == null || c.springPart(i);
    }

    /** Pose de cuerpo entero de la animacion (o null si no mueve el cuerpo entero). */
    public static RootPose root(String name, float t) {
        com.annis.npc.client.punchy.BedrockClip mapped = com.annis.npc.client.punchy.PunchyClips.action(name);
        if (mapped != null) {
            return com.annis.npc.client.punchy.PunchyClips.root(mapped, t);
        }
        Custom c = name == null ? null : CUSTOM.get(name.toLowerCase(Locale.ROOT));
        return c == null ? null : c.root(t);
    }

    private static void rot(net.minecraft.client.model.geom.ModelPart p, float pitch, float yaw, float roll) {
        p.xRot = pitch;
        p.yRot = yaw;
        p.zRot = roll;
    }

    private static void down(NpcModel m, float amount) {
        m.head.y += amount;
        m.body.y += amount;
        m.leftArm.y += amount;
        m.rightArm.y += amount;
        m.leftLeg.y += amount;
        m.rightLeg.y += amount;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    /** 0 antes de a, sube suave hasta b, 1 hasta c, baja suave hasta d, 0 despues. */
    private static float env(float t, float a, float b, float c, float d) {
        if (t <= a || t >= d) {
            return 0.0f;
        }
        if (t < b) {
            return smooth((t - a) / (b - a));
        }
        if (t <= c) {
            return 1.0f;
        }
        return 1.0f - smooth((t - c) / (d - c));
    }

    /** Golpe: sube rapido despues del impacto y cae rapido al siguiente (0 = impacto). */
    private static float stroke(float phase) {
        return Mth.sin(PI * (float) Math.pow(phase, 1.6));
    }

    /** Mueve juntos cabeza, torso y brazos (asi los accesorios de cabeza y manos siguen encajando). */
    private static void upper(NpcModel m, float dx, float dy, float dz) {
        m.head.x += dx;
        m.body.x += dx;
        m.rightArm.x += dx;
        m.leftArm.x += dx;
        m.head.y += dy;
        m.body.y += dy;
        m.rightArm.y += dy;
        m.leftArm.y += dy;
        m.head.z += dz;
        m.body.z += dz;
        m.rightArm.z += dz;
        m.leftArm.z += dz;
    }

    /** Reverencia como la del agacharse vanilla, con peso w (0..1). */
    private static void bow(NpcModel m, float w) {
        m.body.xRot += 0.5f * w;
        m.body.y += 3.2f * w;
        m.head.y += 4.2f * w;
        m.head.xRot += 0.35f * w;
        m.rightArm.y += 3.2f * w;
        m.leftArm.y += 3.2f * w;
        m.rightLeg.z += 4.0f * w;
        m.leftLeg.z += 4.0f * w;
        m.rightLeg.y += 0.2f * w;
        m.leftLeg.y += 0.2f * w;
    }

    /** Brazos del tambor: baquetas alternas que golpean el parche (geometria calculada con el tambor y las baquetas del conjunto). */
    private static void drumArms(NpcModel m, float t) {
        final float beat = 8.0f;
        float cyc = t / (2.0f * beat);
        float amp = 0.55f * (1.0f + 0.25f * Mth.sin(t * 2.0f * PI / (8.0f * beat)));
        float sr = stroke(frac(cyc));
        float sl = stroke(frac(cyc + 0.5f));
        rot(m.rightArm, -1.25f - amp * sr, -0.6f + 0.05f * sr, 0.0f);
        rot(m.leftArm, -1.25f - amp * sl, 0.6f - 0.05f * sl, 0.0f);
        float hit = 1.0f - Math.abs(Mth.sin(PI * t / beat));
        hit = hit * hit;
        upper(m, 0.0f, 0.35f * hit, 0.0f);
        m.head.xRot = 0.12f + 0.06f * hit;
        m.head.yRot = Mth.sin(t * 0.04f) * 0.15f;
        m.body.zRot += Mth.sin(t * PI / (2.0f * beat)) * 0.03f;
    }

    /** Pone la pose de la actividad en el modelo. t = ticks con parte decimal. */
    public static void apply(String name, NpcModel m, float t) {
        // una animacion de Punchy Studio asignada a esta accion (acciones.json del paquete) manda
        com.annis.npc.client.punchy.BedrockClip mapped = com.annis.npc.client.punchy.PunchyClips.action(name);
        if (mapped != null) {
            com.annis.npc.client.punchy.PunchyClips.apply(mapped, m, t);
            return;
        }
        MoveLibrary.Move move = MoveLibrary.get(name);
        if (move != null) {
            MoveLibrary.apply(move, m, t);
            return;
        }
        Custom custom = CUSTOM.get(name.toLowerCase(Locale.ROOT));
        if (custom != null) {
            custom.apply(m, t);
            return;
        }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "saludar" -> {
                float wave = Mth.sin(t * 0.45f);
                rot(m.rightArm, -0.15f, 0.0f, 2.45f + wave * 0.3f);
                m.leftArm.zRot = -0.12f;
                m.head.zRot += wave * 0.03f;
                m.body.zRot += 0.03f;
            }
            case "charlar" -> {
                rot(m.rightArm, -0.9f + Mth.sin(t * 0.13f) * 0.35f, -0.4f, 0.25f + Mth.sin(t * 0.21f) * 0.15f);
                rot(m.leftArm, -0.7f + Mth.sin(t * 0.17f + 1.0f) * 0.3f, 0.4f, -0.2f);
                m.head.xRot += Mth.sin(t * 0.2f) * 0.06f;
                m.head.yRot += Mth.sin(t * 0.045f) * 0.12f;
                m.body.yRot += Mth.sin(t * 0.05f) * 0.08f;
            }
            case "pescar" -> {
                float pull = (float) Math.pow(Math.max(0.0f, Mth.sin(t * 0.07f + 1.0f)), 10);
                float sway = Mth.sin(t * 0.05f) * 0.03f;
                rot(m.rightArm, -1.2f - pull * 0.5f, -0.3f, 0.1f);
                rot(m.leftArm, -1.1f - pull * 0.5f, 0.5f, -0.1f);
                m.head.xRot = 0.35f + sway;
                m.body.xRot += -pull * 0.15f;
                m.body.zRot += sway;
            }
            case "mercado" -> {
                float look = Mth.sin(t * 0.04f);
                rot(m.rightArm, -1.1f + Mth.sin(t * 0.1f) * 0.08f, -0.2f, 0.35f);
                rot(m.leftArm, -1.6f, 0.9f, -0.1f);
                m.head.yRot += look * 0.35f;
                m.head.xRot += 0.05f;
                m.body.yRot += look * 0.1f;
                m.body.zRot += Mth.sin(t * 0.03f) * 0.03f;
            }
            case "combate" -> {
                float p = (t / 22.0f) % 1.0f;
                float pitch;
                float strike = 0.0f;
                if (p < 0.5f) {
                    pitch = Mth.lerp(Mth.sin(p / 0.5f * PI / 2), -1.2f, -2.9f);
                } else if (p < 0.62f) {
                    float k = (p - 0.5f) / 0.12f;
                    pitch = Mth.lerp(k, -2.9f, -0.6f);
                    strike = Mth.sin(k * PI);
                } else {
                    float k = (p - 0.62f) / 0.38f;
                    pitch = Mth.lerp(k * k * (3 - 2 * k), -0.6f, -1.2f);
                }
                float bounce = Mth.sin(t * 0.3f) * 0.5f;
                rot(m.rightArm, pitch, -0.2f, 0.15f);
                rot(m.leftArm, -1.4f, 0.5f, -0.1f);
                m.body.xRot += 0.15f + strike * 0.2f;
                m.body.yRot += -0.3f + strike * 0.5f;
                m.head.yRot += 0.25f;
                rot(m.rightLeg, -0.35f, 0.0f, 0.12f);
                rot(m.leftLeg, 0.4f, 0.0f, -0.12f);
                m.body.y += bounce;
                m.head.y += bounce;
                m.leftArm.y += bounce;
                m.rightArm.y += bounce;
            }
            case "sentarse" -> {
                down(m, 10.0f);
                rot(m.rightLeg, -PI / 2, 0.1f, 0.0f);
                rot(m.leftLeg, -PI / 2, -0.1f, 0.0f);
                rot(m.rightArm, -0.9f, -0.15f, 0.1f);
                rot(m.leftArm, -0.9f, 0.15f, -0.1f);
                m.body.xRot += 0.04f;
            }
            case "bailar" -> {
                float s = Mth.sin(t * 0.3f);
                float bob = Math.abs(s) * 1.2f;
                m.body.zRot += s * 0.1f;
                m.body.yRot += Mth.sin(t * 0.15f) * 0.3f;
                rot(m.rightArm, Mth.sin(t * 0.15f) * 0.3f, 0.0f, 2.2f + s * 0.5f);
                rot(m.leftArm, Mth.cos(t * 0.15f) * 0.3f, 0.0f, -2.2f + s * 0.5f);
                rot(m.rightLeg, s * 0.4f, 0.0f, 0.05f);
                rot(m.leftLeg, -s * 0.4f, 0.0f, -0.05f);
                m.head.zRot -= s * 0.1f;
                m.body.y += bob;
                m.head.y += bob;
                m.leftArm.y += bob;
                m.rightArm.y += bob;
            }
            case "pensar" -> {
                rot(m.rightArm, -1.9f, 1.0f, 0.0f);
                rot(m.leftArm, -0.9f, 0.8f, -0.1f);
                m.head.xRot += 0.2f;
                m.head.yRot += Mth.sin(t * 0.03f) * 0.2f;
                m.body.zRot += Mth.sin(t * 0.02f) * 0.03f;
            }
            case "aplaudir" -> {
                float clap = 0.5f + 0.5f * Mth.sin(t * 0.9f);
                float spread = 0.15f + clap * 0.55f;
                rot(m.rightArm, -1.4f, spread * 0.6f, 0.0f);
                rot(m.leftArm, -1.4f, -spread * 0.6f, 0.0f);
                m.head.xRot += 0.05f;
                m.body.xRot += clap * 0.03f;
            }
            case "tocar_tambor" -> {
                drumArms(m, t);
                // golpecito del pie cada dos tiempos
                float tap = (float) Math.pow(Math.max(0.0f, Mth.sin(PI * t / 16.0f)), 6);
                rot(m.rightLeg, -0.12f * tap, 0.05f, 0.03f);
                rot(m.leftLeg, 0.0f, -0.05f, -0.03f);
            }
            case "desfile_tambor" -> {
                drumArms(m, t);
                float step = Mth.sin(2.0f * PI * t / 32.0f);
                // pasos cortos: con el tambor delante las rodillas no pueden subir mucho
                rot(m.rightLeg, -0.42f * (float) Math.pow(Math.max(0.0f, step), 1.5), 0.0f, 0.06f);
                rot(m.leftLeg, -0.42f * (float) Math.pow(Math.max(0.0f, -step), 1.5), 0.0f, -0.06f);
                upper(m, 0.0f, -0.45f * Math.abs(step), 0.0f);
            }
            case "tocar_trompeta" -> {
                float phrase = (float) Math.pow(Math.max(0.0f, Mth.sin(t * 0.045f)), 2);
                float lift = -0.14f * phrase;
                float sway = Mth.sin(t * 0.05f);
                rot(m.head, -0.15f + lift, 0.0f, sway * 0.04f);
                rot(m.rightArm, -2.1f + lift * 1.1f, -0.74f, 0.03f * Mth.sin(t * 1.3f) * (1.0f - phrase));
                rot(m.leftArm, -1.86f + lift * 1.1f, 0.68f, 0.0f);
                upper(m, sway * 0.7f, Mth.sin(t * 0.3f) * 0.25f, 0.0f);
                m.body.zRot += sway * 0.03f;
                m.body.xRot += lift * 0.3f;
                float tap = (float) Math.pow(Math.max(0.0f, Mth.sin(PI * t / 10.0f)), 4);
                rot(m.leftLeg, -0.15f * tap, -0.05f, -0.03f - sway * 0.02f);
                rot(m.rightLeg, 0.0f, 0.05f, 0.03f - sway * 0.02f);
            }
            case "saludo_sombrero" -> {
                float c = ((t % 70.0f) + 70.0f) % 70.0f;
                float raise = env(c, 0, 12, 52, 64);
                float b = env(c, 14, 26, 38, 50);
                m.rightArm.xRot = Mth.lerp(raise, m.rightArm.xRot, -2.66f);
                m.rightArm.yRot = Mth.lerp(raise, m.rightArm.yRot, -0.76f);
                m.rightArm.zRot = Mth.lerp(raise, m.rightArm.zRot, 0.0f);
                m.leftArm.xRot = Mth.lerp(b, m.leftArm.xRot, 0.35f);
                m.leftArm.zRot = Mth.lerp(b, m.leftArm.zRot, -0.12f);
                bow(m, b);
            }
            case "presentar" -> {
                float c = ((t % 100.0f) + 100.0f) % 100.0f;
                float open = env(c, 5, 30, 70, 90);
                float b = env(c, 72, 80, 84, 92) * 0.5f;
                rot(m.rightArm,
                        Mth.lerp(open, -0.3f, -1.45f),
                        Mth.lerp(open, -0.2f, 0.35f) + Mth.sin(c * 0.15f) * 0.08f * open,
                        Mth.lerp(open, 0.1f, 0.5f));
                rot(m.leftArm, -0.25f, 0.2f, -0.75f); // mano en la cintura
                m.head.yRot = Mth.lerp(open, m.head.yRot, 0.45f);
                m.head.xRot = Mth.lerp(open, m.head.xRot, -0.08f);
                m.body.xRot -= 0.05f * open;
                m.body.yRot += 0.12f * open;
                bow(m, b);
            }
            case "dirigir" -> {
                final float beat = 11.0f;
                float[][] pts = {{-0.8f, -0.15f}, {-1.15f, -0.55f}, {-1.15f, 0.35f}, {-1.75f, -0.1f}};
                float bt = t / beat;
                int i = Math.floorMod((int) Math.floor(bt), 4);
                float f = smooth(frac(bt));
                float[] a = pts[i];
                float[] n = pts[(i + 1) % 4];
                float ictus = (float) Math.pow(1.0f - frac(bt), 3);
                float pitch = Mth.lerp(f, a[0], n[0]) + 0.12f * ictus;
                float yaw = Mth.lerp(f, a[1], n[1]);
                rot(m.rightArm, pitch, yaw, 0.05f);
                rot(m.leftArm, -0.9f + 0.35f * Mth.sin(t * 0.025f), 0.25f, -0.1f);
                m.head.xRot += (i == 0 ? 0.06f : 0.02f) * ictus;
                m.head.yRot += yaw * 0.2f;
                m.body.yRot += yaw * 0.1f;
            }
            case "marchar" -> {
                float step = Mth.sin(2.0f * PI * t / 24.0f);
                rot(m.rightLeg, -0.85f * (float) Math.pow(Math.max(0.0f, step), 1.3), 0.0f, 0.02f);
                rot(m.leftLeg, -0.85f * (float) Math.pow(Math.max(0.0f, -step), 1.3), 0.0f, -0.02f);
                rot(m.rightArm, 0.55f * step, 0.0f, 0.06f);
                rot(m.leftArm, -0.55f * step, 0.0f, -0.06f);
                upper(m, 0.0f, -0.7f * Math.abs(step), 0.0f);
                m.head.xRot = -0.05f;
            }
            default -> {
            }
        }
    }
}
