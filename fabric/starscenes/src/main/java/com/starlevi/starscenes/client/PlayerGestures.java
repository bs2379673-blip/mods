package com.starlevi.starscenes.client;

import com.starlevi.starscenes.model.BBModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

import java.util.HashMap;
import java.util.Map;

/**
 * Gestos en los jugadores (abrazo, beso, chocar los cinco, de la mano...), sacados de las animaciones
 * del modelo humano de StarScenes y puestos encima de la animacion normal del jugador: solo mueven los
 * huesos que el gesto tiene animados, entrando y saliendo suave.
 */
public final class PlayerGestures {
    private static final float BLEND = 0.25f;

    private static final class G {
        BBModel.Anim anim;
        float start, until;
    }

    private static final Map<Integer, G> ACTIVE = new HashMap<>();
    private static float[] buf = new float[0];

    private PlayerGestures() {
    }

    private static float now() {
        return System.nanoTime() / 1e9f;
    }

    private static BBModel human() {
        ClientScenes.Ready r = ClientScenes.model("humano", ClientScenes.playerSources);
        return r == null ? null : r.model;
    }

    /** ticks 0 corta el gesto (saliendo suave). */
    public static void play(int entity, String anim, int ticks) {
        float t = now();
        G g = ACTIVE.get(entity);
        if (ticks <= 0 || anim == null || anim.isEmpty()) {
            if (g != null) {
                g.until = Math.min(g.until, t + BLEND);
            }
            return;
        }
        BBModel m = human();
        BBModel.Anim an = m == null ? null : m.anims.get(anim);
        if (an == null) {
            return;
        }
        if (g != null && g.anim == an && g.until > t && an.loops()) {
            g.until = t + ticks / 20f; // el mismo gesto en ronda: se alarga
            return;
        }
        g = new G();
        g.anim = an;
        g.start = t;
        g.until = an.loops() ? t + ticks / 20f : t + Math.min(an.length, ticks / 20f);
        ACTIVE.put(entity, g);
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static boolean active(int entity) {
        return ACTIVE.containsKey(entity);
    }

    /** Lo llama el modelo del jugador al final de su animacion. */
    public static void apply(int entity, HumanoidModel<?> model) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        G g = ACTIVE.get(entity);
        if (g == null) {
            return;
        }
        float t = now();
        if (t >= g.until) {
            ACTIVE.remove(entity);
            return;
        }
        BBModel m = human();
        if (m == null) {
            return;
        }
        float w = Math.min(1, Math.min((t - g.start) / BLEND, (g.until - t) / BLEND));
        if (w <= 0) {
            return;
        }
        w = w * w * (3 - 2 * w);
        int n = m.bones.size() * 9;
        if (buf.length < n) {
            buf = new float[n];
        }
        float at = g.anim.loops() ? (t - g.start) % g.anim.length : Math.min(t - g.start, g.anim.length);
        m.sample(g.anim, at, buf);
        float lift = 0;
        for (int i = 0; i < m.bones.size(); i++) {
            String name = m.bones.get(i).name;
            int o = i * 9;
            if (g.anim.moves(i) && name.equals("player")) {
                lift = buf[o + 4] * w;
            }
            if (!g.anim.rotates(i)) {
                continue;
            }
            ModelPart p = switch (name) {
                case "head" -> model.head;
                case "body" -> model.body;
                case "right_arm" -> model.rightArm;
                case "left_arm" -> model.leftArm;
                case "right_leg" -> model.rightLeg;
                case "left_leg" -> model.leftLeg;
                default -> null;
            };
            if (p == null) {
                continue;
            }
            float rx = (float) Math.toRadians(buf[o]), ry = (float) Math.toRadians(buf[o + 1]), rz = (float) Math.toRadians(buf[o + 2]);
            if (p == model.head) {
                // sigue mirando a donde mira; el gesto se suma
                rx += p.xRot;
                ry += p.yRot;
            }
            p.xRot += (rx - p.xRot) * w;
            p.yRot += (ry - p.yRot) * w;
            p.zRot += (rz - p.zRot) * w;
        }
        if (lift != 0) {
            for (ModelPart p : new ModelPart[]{model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg}) {
                p.y -= lift;
            }
        }
    }
}
