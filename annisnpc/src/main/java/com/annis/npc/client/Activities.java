package com.annis.npc.client;

import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.Locale;

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

    private Activities() {
    }

    public static boolean has(String name) {
        return name != null && NAMES.contains(name.toLowerCase(Locale.ROOT));
    }

    private static void rot(net.minecraft.client.model.ModelPart p, float pitch, float yaw, float roll) {
        p.pitch = pitch;
        p.yaw = yaw;
        p.roll = roll;
    }

    private static void down(NpcModel m, float amount) {
        m.head.pivotY += amount;
        m.body.pivotY += amount;
        m.leftArm.pivotY += amount;
        m.rightArm.pivotY += amount;
        m.leftLeg.pivotY += amount;
        m.rightLeg.pivotY += amount;
    }

    private static float smooth(float x) {
        x = MathHelper.clamp(x, 0.0f, 1.0f);
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
        return MathHelper.sin(PI * (float) Math.pow(phase, 1.6));
    }

    /** Mueve juntos cabeza, torso y brazos (asi los accesorios de cabeza y manos siguen encajando). */
    private static void upper(NpcModel m, float dx, float dy, float dz) {
        m.head.pivotX += dx;
        m.body.pivotX += dx;
        m.rightArm.pivotX += dx;
        m.leftArm.pivotX += dx;
        m.head.pivotY += dy;
        m.body.pivotY += dy;
        m.rightArm.pivotY += dy;
        m.leftArm.pivotY += dy;
        m.head.pivotZ += dz;
        m.body.pivotZ += dz;
        m.rightArm.pivotZ += dz;
        m.leftArm.pivotZ += dz;
    }

    /** Reverencia como la del agacharse vanilla, con peso w (0..1). */
    private static void bow(NpcModel m, float w) {
        m.body.pitch += 0.5f * w;
        m.body.pivotY += 3.2f * w;
        m.head.pivotY += 4.2f * w;
        m.head.pitch += 0.35f * w;
        m.rightArm.pivotY += 3.2f * w;
        m.leftArm.pivotY += 3.2f * w;
        m.rightLeg.pivotZ += 4.0f * w;
        m.leftLeg.pivotZ += 4.0f * w;
        m.rightLeg.pivotY += 0.2f * w;
        m.leftLeg.pivotY += 0.2f * w;
    }

    /** Brazos del tambor: baquetas alternas que golpean el parche (geometria calculada con el tambor y las baquetas del conjunto). */
    private static void drumArms(NpcModel m, float t) {
        final float beat = 8.0f;
        float cyc = t / (2.0f * beat);
        float amp = 0.55f * (1.0f + 0.25f * MathHelper.sin(t * 2.0f * PI / (8.0f * beat)));
        float sr = stroke(frac(cyc));
        float sl = stroke(frac(cyc + 0.5f));
        rot(m.rightArm, -1.25f - amp * sr, -0.6f + 0.05f * sr, 0.0f);
        rot(m.leftArm, -1.25f - amp * sl, 0.6f - 0.05f * sl, 0.0f);
        float hit = 1.0f - Math.abs(MathHelper.sin(PI * t / beat));
        hit = hit * hit;
        upper(m, 0.0f, 0.35f * hit, 0.0f);
        m.head.pitch = 0.12f + 0.06f * hit;
        m.head.yaw = MathHelper.sin(t * 0.04f) * 0.15f;
        m.body.roll += MathHelper.sin(t * PI / (2.0f * beat)) * 0.03f;
    }

    /** Pone la pose de la actividad en el modelo. t = ticks con parte decimal. */
    public static void apply(String name, NpcModel m, float t) {
        MoveLibrary.Move move = MoveLibrary.get(name);
        if (move != null) {
            MoveLibrary.apply(move, m, t);
            return;
        }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "saludar" -> {
                float wave = MathHelper.sin(t * 0.45f);
                rot(m.rightArm, -0.15f, 0.0f, 2.45f + wave * 0.3f);
                m.leftArm.roll = -0.12f;
                m.head.roll += wave * 0.03f;
                m.body.roll += 0.03f;
            }
            case "charlar" -> {
                rot(m.rightArm, -0.9f + MathHelper.sin(t * 0.13f) * 0.35f, -0.4f, 0.25f + MathHelper.sin(t * 0.21f) * 0.15f);
                rot(m.leftArm, -0.7f + MathHelper.sin(t * 0.17f + 1.0f) * 0.3f, 0.4f, -0.2f);
                m.head.pitch += MathHelper.sin(t * 0.2f) * 0.06f;
                m.head.yaw += MathHelper.sin(t * 0.045f) * 0.12f;
                m.body.yaw += MathHelper.sin(t * 0.05f) * 0.08f;
            }
            case "pescar" -> {
                float pull = (float) Math.pow(Math.max(0.0f, MathHelper.sin(t * 0.07f + 1.0f)), 10);
                float sway = MathHelper.sin(t * 0.05f) * 0.03f;
                rot(m.rightArm, -1.2f - pull * 0.5f, -0.3f, 0.1f);
                rot(m.leftArm, -1.1f - pull * 0.5f, 0.5f, -0.1f);
                m.head.pitch = 0.35f + sway;
                m.body.pitch += -pull * 0.15f;
                m.body.roll += sway;
            }
            case "mercado" -> {
                float look = MathHelper.sin(t * 0.04f);
                rot(m.rightArm, -1.1f + MathHelper.sin(t * 0.1f) * 0.08f, -0.2f, 0.35f);
                rot(m.leftArm, -1.6f, 0.9f, -0.1f);
                m.head.yaw += look * 0.35f;
                m.head.pitch += 0.05f;
                m.body.yaw += look * 0.1f;
                m.body.roll += MathHelper.sin(t * 0.03f) * 0.03f;
            }
            case "combate" -> {
                float p = (t / 22.0f) % 1.0f;
                float pitch;
                float strike = 0.0f;
                if (p < 0.5f) {
                    pitch = MathHelper.lerp(MathHelper.sin(p / 0.5f * PI / 2), -1.2f, -2.9f);
                } else if (p < 0.62f) {
                    float k = (p - 0.5f) / 0.12f;
                    pitch = MathHelper.lerp(k, -2.9f, -0.6f);
                    strike = MathHelper.sin(k * PI);
                } else {
                    float k = (p - 0.62f) / 0.38f;
                    pitch = MathHelper.lerp(k * k * (3 - 2 * k), -0.6f, -1.2f);
                }
                float bounce = MathHelper.sin(t * 0.3f) * 0.5f;
                rot(m.rightArm, pitch, -0.2f, 0.15f);
                rot(m.leftArm, -1.4f, 0.5f, -0.1f);
                m.body.pitch += 0.15f + strike * 0.2f;
                m.body.yaw += -0.3f + strike * 0.5f;
                m.head.yaw += 0.25f;
                rot(m.rightLeg, -0.35f, 0.0f, 0.12f);
                rot(m.leftLeg, 0.4f, 0.0f, -0.12f);
                m.body.pivotY += bounce;
                m.head.pivotY += bounce;
                m.leftArm.pivotY += bounce;
                m.rightArm.pivotY += bounce;
            }
            case "sentarse" -> {
                down(m, 10.0f);
                rot(m.rightLeg, -PI / 2, 0.1f, 0.0f);
                rot(m.leftLeg, -PI / 2, -0.1f, 0.0f);
                rot(m.rightArm, -0.9f, -0.15f, 0.1f);
                rot(m.leftArm, -0.9f, 0.15f, -0.1f);
                m.body.pitch += 0.04f;
            }
            case "bailar" -> {
                float s = MathHelper.sin(t * 0.3f);
                float bob = Math.abs(s) * 1.2f;
                m.body.roll += s * 0.1f;
                m.body.yaw += MathHelper.sin(t * 0.15f) * 0.3f;
                rot(m.rightArm, MathHelper.sin(t * 0.15f) * 0.3f, 0.0f, 2.2f + s * 0.5f);
                rot(m.leftArm, MathHelper.cos(t * 0.15f) * 0.3f, 0.0f, -2.2f + s * 0.5f);
                rot(m.rightLeg, s * 0.4f, 0.0f, 0.05f);
                rot(m.leftLeg, -s * 0.4f, 0.0f, -0.05f);
                m.head.roll -= s * 0.1f;
                m.body.pivotY += bob;
                m.head.pivotY += bob;
                m.leftArm.pivotY += bob;
                m.rightArm.pivotY += bob;
            }
            case "pensar" -> {
                rot(m.rightArm, -1.9f, 1.0f, 0.0f);
                rot(m.leftArm, -0.9f, 0.8f, -0.1f);
                m.head.pitch += 0.2f;
                m.head.yaw += MathHelper.sin(t * 0.03f) * 0.2f;
                m.body.roll += MathHelper.sin(t * 0.02f) * 0.03f;
            }
            case "aplaudir" -> {
                float clap = 0.5f + 0.5f * MathHelper.sin(t * 0.9f);
                float spread = 0.15f + clap * 0.55f;
                rot(m.rightArm, -1.4f, spread * 0.6f, 0.0f);
                rot(m.leftArm, -1.4f, -spread * 0.6f, 0.0f);
                m.head.pitch += 0.05f;
                m.body.pitch += clap * 0.03f;
            }
            case "tocar_tambor" -> {
                drumArms(m, t);
                // golpecito del pie cada dos tiempos
                float tap = (float) Math.pow(Math.max(0.0f, MathHelper.sin(PI * t / 16.0f)), 6);
                rot(m.rightLeg, -0.12f * tap, 0.05f, 0.03f);
                rot(m.leftLeg, 0.0f, -0.05f, -0.03f);
            }
            case "desfile_tambor" -> {
                drumArms(m, t);
                float step = MathHelper.sin(2.0f * PI * t / 32.0f);
                // pasos cortos: con el tambor delante las rodillas no pueden subir mucho
                rot(m.rightLeg, -0.42f * (float) Math.pow(Math.max(0.0f, step), 1.5), 0.0f, 0.06f);
                rot(m.leftLeg, -0.42f * (float) Math.pow(Math.max(0.0f, -step), 1.5), 0.0f, -0.06f);
                upper(m, 0.0f, -0.45f * Math.abs(step), 0.0f);
            }
            case "tocar_trompeta" -> {
                float phrase = (float) Math.pow(Math.max(0.0f, MathHelper.sin(t * 0.045f)), 2);
                float lift = -0.14f * phrase;
                float sway = MathHelper.sin(t * 0.05f);
                rot(m.head, -0.15f + lift, 0.0f, sway * 0.04f);
                rot(m.rightArm, -2.1f + lift * 1.1f, -0.74f, 0.03f * MathHelper.sin(t * 1.3f) * (1.0f - phrase));
                rot(m.leftArm, -1.86f + lift * 1.1f, 0.68f, 0.0f);
                upper(m, sway * 0.7f, MathHelper.sin(t * 0.3f) * 0.25f, 0.0f);
                m.body.roll += sway * 0.03f;
                m.body.pitch += lift * 0.3f;
                float tap = (float) Math.pow(Math.max(0.0f, MathHelper.sin(PI * t / 10.0f)), 4);
                rot(m.leftLeg, -0.15f * tap, -0.05f, -0.03f - sway * 0.02f);
                rot(m.rightLeg, 0.0f, 0.05f, 0.03f - sway * 0.02f);
            }
            case "saludo_sombrero" -> {
                float c = ((t % 70.0f) + 70.0f) % 70.0f;
                float raise = env(c, 0, 12, 52, 64);
                float b = env(c, 14, 26, 38, 50);
                m.rightArm.pitch = MathHelper.lerp(raise, m.rightArm.pitch, -2.66f);
                m.rightArm.yaw = MathHelper.lerp(raise, m.rightArm.yaw, -0.76f);
                m.rightArm.roll = MathHelper.lerp(raise, m.rightArm.roll, 0.0f);
                m.leftArm.pitch = MathHelper.lerp(b, m.leftArm.pitch, 0.35f);
                m.leftArm.roll = MathHelper.lerp(b, m.leftArm.roll, -0.12f);
                bow(m, b);
            }
            case "presentar" -> {
                float c = ((t % 100.0f) + 100.0f) % 100.0f;
                float open = env(c, 5, 30, 70, 90);
                float b = env(c, 72, 80, 84, 92) * 0.5f;
                rot(m.rightArm,
                        MathHelper.lerp(open, -0.3f, -1.45f),
                        MathHelper.lerp(open, -0.2f, 0.35f) + MathHelper.sin(c * 0.15f) * 0.08f * open,
                        MathHelper.lerp(open, 0.1f, 0.5f));
                rot(m.leftArm, -0.25f, 0.2f, -0.75f); // mano en la cintura
                m.head.yaw = MathHelper.lerp(open, m.head.yaw, 0.45f);
                m.head.pitch = MathHelper.lerp(open, m.head.pitch, -0.08f);
                m.body.pitch -= 0.05f * open;
                m.body.yaw += 0.12f * open;
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
                float pitch = MathHelper.lerp(f, a[0], n[0]) + 0.12f * ictus;
                float yaw = MathHelper.lerp(f, a[1], n[1]);
                rot(m.rightArm, pitch, yaw, 0.05f);
                rot(m.leftArm, -0.9f + 0.35f * MathHelper.sin(t * 0.025f), 0.25f, -0.1f);
                m.head.pitch += (i == 0 ? 0.06f : 0.02f) * ictus;
                m.head.yaw += yaw * 0.2f;
                m.body.yaw += yaw * 0.1f;
            }
            case "marchar" -> {
                float step = MathHelper.sin(2.0f * PI * t / 24.0f);
                rot(m.rightLeg, -0.85f * (float) Math.pow(Math.max(0.0f, step), 1.3), 0.0f, 0.02f);
                rot(m.leftLeg, -0.85f * (float) Math.pow(Math.max(0.0f, -step), 1.3), 0.0f, -0.02f);
                rot(m.rightArm, 0.55f * step, 0.0f, 0.06f);
                rot(m.leftArm, -0.55f * step, 0.0f, -0.06f);
                upper(m, 0.0f, -0.7f * Math.abs(step), 0.0f);
                m.head.pitch = -0.05f;
            }
            default -> {
            }
        }
    }
}
