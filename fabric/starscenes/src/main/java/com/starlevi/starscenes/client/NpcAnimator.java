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
    /** La animacion de base (reposar / caminar / correr), con mezcla al cambiar. */
    private BBModel.Anim base, basePrev;
    private float baseStart, basePrevStart, baseBlend = -100;
    /** El gesto de encima (saludar, hablar, reir...): solo mueve los huesos que tiene animados. */
    private BBModel.Anim gest;
    private float gestStart, gestRefresh, gestEnd = Float.MAX_VALUE;
    private boolean near;
    private float yaw, pitch;
    private float[] a, b, g, out;
    final List<Matrix4f> mats = new ArrayList<>();
    private String pending;
    /**
     * Para el modelo de EMF: los huesos que mueve el gesto de ahora, con su giro en grados (como en
     * Blockbench) y cuanto pesa (0..1, para entrar y salir suave). Vacio si no hay gesto.
     */
    final java.util.Map<String, float[]> overlay = new java.util.HashMap<>();

    /** Pide una animacion ("interact", "seeplayer", "idle" o un nombre exacto); se hace en el proximo cuadro. */
    void trigger(String kind) {
        this.pending = kind;
    }

    private final ChestSpring chest = new ChestSpring();
    private int chestBone = -2;

    /** El rebote del pecho de este cuadro (para el modelo de EMF). */
    float chestBounce;

    private float speed;
    private double lastX = Double.NaN, lastZ;
    private int lastTick = -1;

    void update(Entity e, SceneActor npc, BBModel m, float now, Player local, float bodyYaw) {
        if (m != this.model) {
            this.model = m;
            this.chestBone = -2;
            int n = m.bones.size();
            this.a = new float[n * 9];
            this.b = new float[n * 9];
            this.g = new float[n * 9];
            this.out = new float[n * 9];
            this.mats.clear();
            for (int i = 0; i < n; i++) {
                this.mats.add(new Matrix4f());
            }
            this.base = this.basePrev = this.gest = null;
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
        // base: reposar / caminar / correr segun se mueva
        String kind = base(npc);
        BBModel.Anim want = find(npc, kind);
        if (want != this.base) {
            this.basePrev = this.base;
            this.basePrevStart = this.baseStart;
            this.base = want;
            this.baseStart = now;
            this.baseBlend = this.basePrev == null ? -100 : now;
        }
        // te vio: al entrar en su radio
        double dist = local == null ? 1e9 : Math.sqrt(local.distanceToSqr(e));
        float r = npc.sceneRadius() * npc.sceneScale();
        if (!this.near && dist < r) {
            this.near = true;
            if (this.gest == null && kind.equals("idle")) {
                start(find(npc, "seeplayer"), now);
            }
        } else if (this.near && dist > r + 1.5) {
            this.near = false;
        }
        if (this.pending != null) {
            String k = this.pending;
            this.pending = null;
            BBModel.Anim an = m.anims.containsKey(k) ? m.anims.get(k) : find(npc, k);
            if (an == null || isBase(an)) {
                // "idle": se corta el gesto
                if (this.gest != null && this.gestEnd > now) {
                    this.gestEnd = now;
                }
            } else if (an == this.gest && an.loops() && this.gestEnd > now) {
                // el mismo gesto en ronda: sigue sin empezar de nuevo
                this.gestRefresh = now;
            } else {
                start(an, now);
            }
        }
        // el gesto termina: los de una vez al final; los de ronda a los 4 s (o dos vueltas) sin refrescar
        if (this.gest != null && this.gestEnd == Float.MAX_VALUE) {
            if (!this.gest.loops()) {
                this.gestEnd = this.gestStart + this.gest.length;
            } else if (now - this.gestRefresh >= Math.max(4f, this.gest.length * 2)) {
                this.gestEnd = now + BLEND;
            }
        }
        float w = 0;
        if (this.gest != null) {
            w = Math.min(1, (now - this.gestStart) / BLEND);
            if (this.gestEnd != Float.MAX_VALUE) {
                w = Math.min(w, (this.gestEnd - now) / BLEND);
            }
            if (w <= 0 && now > this.gestStart + BLEND) {
                this.gest = null;
                w = 0;
            }
        }
        // pose: la base (mezclando si cambio) y el gesto encima, solo en sus huesos
        m.sample(this.base, time(this.base, now - this.baseStart), this.b);
        float f = (now - this.baseBlend) / BLEND;
        if (f < 1 && this.basePrev != null) {
            m.sample(this.basePrev, time(this.basePrev, now - this.basePrevStart), this.a);
            f = f * f * (3 - 2 * f);
            BBModel.blend(this.a, this.b, f, this.out);
        } else {
            System.arraycopy(this.b, 0, this.out, 0, this.out.length);
        }
        this.overlay.clear();
        if (this.gest != null && w > 0) {
            float gt = time(this.gest, now - this.gestStart);
            m.sample(this.gest, gt, this.g);
            float sw = w * w * (3 - 2 * w);
            for (int i = 0; i < m.bones.size(); i++) {
                int o = i * 9;
                if (this.gest.rotates(i)) {
                    for (int c = 0; c < 3; c++) {
                        this.out[o + c] += (this.g[o + c] - this.out[o + c]) * sw;
                    }
                    this.overlay.put(m.bones.get(i).name, new float[]{this.g[o], this.g[o + 1], this.g[o + 2], sw});
                }
                if (this.gest.moves(i)) {
                    for (int c = 3; c < 6; c++) {
                        this.out[o + c] += (this.g[o + c] - this.out[o + c]) * sw;
                    }
                }
            }
        }
        // pecho con rebote (si el modelo tiene el hueso "chest")
        if (this.chestBone == -2 || this.chestBone >= m.bones.size()) {
            this.chestBone = -1;
            for (int i = 0; i < m.bones.size(); i++) {
                if (m.bones.get(i).name.equals("chest")) {
                    this.chestBone = i;
                }
            }
        }
        this.chestBounce = this.chest.update(e, now, this.speed);
        if (this.chestBone >= 0) {
            this.out[this.chestBone * 9] += (float) Math.toDegrees(this.chestBounce);
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
    }

    private void start(BBModel.Anim an, float now) {
        if (an == null) {
            return;
        }
        this.gest = an;
        this.gestStart = now;
        this.gestRefresh = now;
        this.gestEnd = Float.MAX_VALUE;
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
