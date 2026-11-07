package com.starlevi.starscenes.client;

import com.starlevi.starscenes.api.SceneActor;
import com.starlevi.starscenes.model.BBModel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lo que hace un actor en este cliente: reposa (idle), camina o corre (walk/run, si se mueve y el
 * modelo las tiene), cuando te acercas hace "te vio" (seeplayer), al tocarlo hace su interaccion
 * (interact), al hablar "talk", y vuelve a reposar. Pasa de una animacion a otra mezclando,
 * sin saltos, y gira la cabeza hacia vos.
 */
final class NpcAnimator {
    private static final float BLEND = 0.25f;

    private BBModel model;
    private BBModel.Anim cur, prev;
    private float curStart, prevStart, blendStart = -100;
    private boolean near;
    private float yaw, pitch;
    private float[] a, b, out;
    final List<Matrix4f> mats = new ArrayList<>();
    private String pending;
    private float[] ov;
    /**
     * Para el modelo de EMF: los huesos que mueve el gesto de ahora (saludar, reir...), con su giro en
     * grados (como en Blockbench) y cuanto pesa (0..1, para entrar y salir suave). Vacio si no hay gesto.
     */
    final java.util.Map<String, float[]> overlay = new java.util.HashMap<>();

    /** Pide una animacion ("interact", "seeplayer", "idle" o un nombre exacto); se hace en el proximo cuadro. */
    void trigger(String kind) {
        this.pending = kind;
    }

    private float speed;
    private boolean triggered;
    private double lastX = Double.NaN, lastZ;
    private int lastTick = -1;

    void update(Entity e, SceneActor npc, BBModel m, float now, Player local, float bodyYaw) {
        if (m != this.model) {
            this.model = m;
            int n = m.bones.size();
            this.a = new float[n * 9];
            this.b = new float[n * 9];
            this.out = new float[n * 9];
            this.mats.clear();
            for (int i = 0; i < n; i++) {
                this.mats.add(new Matrix4f());
            }
            this.cur = null;
            this.prev = null;
        }
        // que tan rapido se mueve (bloques por tick, suavizado)
        if (e.tickCount != this.lastTick) {
            if (!Double.isNaN(this.lastX)) {
                double dx = e.getX() - this.lastX, dz = e.getZ() - this.lastZ;
                float v = (float) Math.sqrt(dx * dx + dz * dz) / Math.max(1, e.tickCount - this.lastTick);
                this.speed += (Math.min(v, 1f) - this.speed) * 0.5f;
            }
            this.lastX = e.getX();
            this.lastZ = e.getZ();
            this.lastTick = e.tickCount;
        }
        String base = base(npc);
        if (this.cur == null) {
            play(find(npc, base), now, false);
        }
        // te vio: al entrar en su radio
        double dist = local == null ? 1e9 : Math.sqrt(local.distanceToSqr(e));
        float r = npc.sceneRadius() * npc.sceneScale();
        if (!this.near && dist < r) {
            this.near = true;
            if (isKind(this.cur, "idle") && base.equals("idle")) {
                BBModel.Anim see = find(npc, "seeplayer");
                if (see != null) {
                    play(see, now, true);
                    this.triggered = true;
                }
            }
        } else if (this.near && dist > r + 1.5) {
            this.near = false;
        }
        if (this.pending != null) {
            String k = this.pending;
            this.pending = null;
            BBModel.Anim an = m.anims.containsKey(k) ? m.anims.get(k) : find(npc, k);
            if (an != null) {
                play(an, now, true);
                this.triggered = !isBase(an);
            }
        }
        // las de una vez, al terminar, vuelven a reposar (o a caminar)
        boolean loopedEnough = this.triggered && this.cur != null && !"once".equals(this.cur.loop)
                && now - this.curStart >= Math.max(4f, this.cur.length * 2);
        if (this.cur != null && ("once".equals(this.cur.loop) && now - this.curStart >= this.cur.length || loopedEnough)) {
            this.triggered = false;
            play(find(npc, base), now, false);
        } else if (this.cur == null || isBase(this.cur)) {
            // reposar / caminar / correr segun se mueva
            BBModel.Anim want = find(npc, base);
            if (want != null && want != this.cur) {
                play(want, now, false);
            }
        }
        m.sample(this.cur, time(this.cur, now - this.curStart), this.b);
        float f = (now - this.blendStart) / BLEND;
        if (f < 1 && this.prev != this.cur) {
            m.sample(this.prev, time(this.prev, now - this.prevStart), this.a);
            f = f * f * (3 - 2 * f);
            BBModel.blend(this.a, this.b, f, this.out);
        } else {
            System.arraycopy(this.b, 0, this.out, 0, this.out.length);
        }
        // cabeza hacia el jugador
        float wantYaw = 0, wantPitch = 0;
        if (npc.sceneLooks() && local != null && dist < r + 4 && m.headBone >= 0) {
            float s = npc.sceneScale() / 16f;
            double hy = e.getY() + m.bones.get(m.headBone).origin[1] * s;
            Vector3f d = new Vector3f((float) (local.getX() - e.getX()), (float) (local.getEyeY() - hy), (float) (local.getZ() - e.getZ()));
            d.rotateY((float) -Math.toRadians(180 - bodyYaw));
            float ang = (float) Math.atan2(d.x, -d.z);
            if (Math.abs(ang) < Math.toRadians(100)) {
                wantYaw = (float) -Math.max(-Math.toRadians(70), Math.min(Math.toRadians(70), ang));
                float h = (float) Math.sqrt(d.x * d.x + d.z * d.z);
                wantPitch = (float) Math.max(-Math.toRadians(35), Math.min(Math.toRadians(35), Math.atan2(d.y, h)));
            }
        }
        this.yaw += (wantYaw - this.yaw) * 0.15f;
        this.pitch += (wantPitch - this.pitch) * 0.15f;
        Matrix4f[] arr = this.mats.toArray(new Matrix4f[0]);
        m.matrices(this.out, arr, this.yaw, this.pitch);
        // el gesto (si hay) para el modelo de EMF
        this.overlay.clear();
        BBModel.Anim g = null;
        float w = 0, gt = 0;
        if (this.cur != null && !isBase(this.cur)) {
            g = this.cur;
            gt = time(g, now - this.curStart);
            w = Math.min(1, (now - this.curStart) / BLEND);
            if (!g.loops()) {
                w = Math.min(w, Math.max(0, (g.length - gt) / BLEND));
            }
        } else if (this.prev != null && !isBase(this.prev) && now - this.blendStart < BLEND) {
            g = this.prev;
            gt = time(g, now - this.prevStart);
            w = 1 - (now - this.blendStart) / BLEND;
        }
        if (g != null && w > 0) {
            if (this.ov == null || this.ov.length != this.out.length) {
                this.ov = new float[this.out.length];
            }
            m.sample(g, gt, this.ov);
            w = w * w * (3 - 2 * w);
            for (int i = 0; i < m.bones.size(); i++) {
                if (g.rotates(i)) {
                    this.overlay.put(m.bones.get(i).name, new float[]{this.ov[i * 9], this.ov[i * 9 + 1], this.ov[i * 9 + 2], w});
                }
            }
        }
    }

    private void play(BBModel.Anim an, float now, boolean restart) {
        if (an == this.cur && !restart) {
            return;
        }
        this.prev = this.cur;
        this.prevStart = this.curStart;
        this.cur = an;
        this.curStart = now;
        this.blendStart = now;
    }

    private static float time(BBModel.Anim an, float t) {
        if (an == null || an.length <= 0) {
            return 0;
        }
        if (an.loops()) {
            return t % an.length;
        }
        return Math.min(t, an.length);
    }

    /** idle, walk o run segun la velocidad (si el modelo tiene esas animaciones). */
    private String base(SceneActor npc) {
        float v = this.speed / Math.max(0.2f, npc.sceneScale());
        if (v > 0.2f && find(npc, "run") != null) {
            return "run";
        }
        if (v > 0.015f && find(npc, "walk") != null) {
            return "walk";
        }
        return "idle";
    }

    private static boolean isBase(BBModel.Anim an) {
        return isKind(an, "idle") || isKind(an, "walk") || isKind(an, "run");
    }

    private static boolean isKind(BBModel.Anim an, String kind) {
        return an == null || an.name.toLowerCase(Locale.ROOT).startsWith(kind);
    }

    /** idle1, seeplayer2... segun el estilo del NPC; si no hay ese numero, la primera de ese tipo. */
    private final java.util.Map<String, BBModel.Anim> found = new java.util.HashMap<>();
    private int foundStyle = Integer.MIN_VALUE;
    private BBModel foundModel;

    private BBModel.Anim find(SceneActor npc, String kind) {
        if (this.foundModel != this.model || this.foundStyle != npc.sceneStyle()) {
            this.found.clear();
            this.foundModel = this.model;
            this.foundStyle = npc.sceneStyle();
        }
        if (this.found.containsKey(kind)) {
            return this.found.get(kind);
        }
        BBModel.Anim an = search(npc, kind);
        this.found.put(kind, an);
        return an;
    }

    private BBModel.Anim search(SceneActor npc, String kind) {
        List<BBModel.Anim> list = new ArrayList<>();
        for (BBModel.Anim an : this.model.anims.values()) {
            String n = an.name.toLowerCase(Locale.ROOT);
            if (n.equals(kind) || n.startsWith(kind) && n.substring(kind.length()).matches("[_\\-]?\\d+")) {
                list.add(an);
            }
        }
        if (list.isEmpty()) {
            return null;
        }
        int style = npc.sceneStyle();
        for (BBModel.Anim an : list) {
            if (an.name.toLowerCase(Locale.ROOT).replaceAll("[^0-9]", "").equals(String.valueOf(style))) {
                return an;
            }
        }
        return list.get(style == 0 ? Math.floorMod(npc.hashCode(), list.size()) : 0);
    }
}
