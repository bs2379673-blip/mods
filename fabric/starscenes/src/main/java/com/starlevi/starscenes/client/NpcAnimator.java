package com.starlevi.starscenes.client;

import com.starlevi.starscenes.entity.NpcEntity;
import com.starlevi.starscenes.model.BBModel;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lo que hace un NPC en este cliente: reposa (idle), cuando te acercas hace "te vio" (seeplayer), al
 * tocarlo hace su interaccion (interact) y vuelve a reposar. Pasa de una animacion a otra mezclando,
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

    /** Pide una animacion ("interact", "seeplayer", "idle" o un nombre exacto); se hace en el proximo cuadro. */
    void trigger(String kind) {
        this.pending = kind;
    }

    void update(NpcEntity npc, BBModel m, float now, Player local, float bodyYaw) {
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
        if (this.cur == null) {
            play(find(npc, "idle"), now, false);
        }
        // te vio: al entrar en su radio
        double dist = local == null ? 1e9 : Math.sqrt(local.distanceToSqr(npc));
        float r = npc.radius() * npc.scale();
        if (!this.near && dist < r) {
            this.near = true;
            if (isKind(this.cur, "idle")) {
                play(find(npc, "seeplayer"), now, true);
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
            }
        }
        // las de una vez, al terminar, vuelven a reposar
        if (this.cur != null && "once".equals(this.cur.loop) && now - this.curStart >= this.cur.length) {
            play(find(npc, "idle"), now, false);
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
        if (npc.look() && local != null && dist < r + 4 && m.headBone >= 0) {
            float s = npc.scale() / 16f;
            double hy = npc.getY() + m.bones.get(m.headBone).origin[1] * s;
            Vector3f d = new Vector3f((float) (local.getX() - npc.getX()), (float) (local.getEyeY() - hy), (float) (local.getZ() - npc.getZ()));
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

    private static boolean isKind(BBModel.Anim an, String kind) {
        return an == null || an.name.toLowerCase(Locale.ROOT).startsWith(kind);
    }

    /** idle1, seeplayer2... segun el estilo del NPC; si no hay ese numero, la primera de ese tipo. */
    private BBModel.Anim find(NpcEntity npc, String kind) {
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
        int style = npc.style();
        for (BBModel.Anim an : list) {
            if (an.name.toLowerCase(Locale.ROOT).replaceAll("[^0-9]", "").equals(String.valueOf(style))) {
                return an;
            }
        }
        return list.get(style == 0 ? Math.floorMod(npc.getId(), list.size()) : 0);
    }
}
