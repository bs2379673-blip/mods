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
            "sentarse", "bailar", "pensar", "aplaudir");

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

    /** Pone la pose de la actividad en el modelo. t = ticks con parte decimal. */
    public static void apply(String name, NpcModel m, float t) {
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
            default -> {
            }
        }
    }
}
