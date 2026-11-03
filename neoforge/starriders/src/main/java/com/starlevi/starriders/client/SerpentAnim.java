package com.starlevi.starriders.client;

import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * La serpiente se mueve por codigo: el cuerpo sigue una curva que ondula (la onda viaja hacia la cola
 * a la misma velocidad con la que avanza, asi no "patina"), el cuello se levanta como una cobra y la
 * cabeza mira, saca la lengua y sisea. Todo queda centrado en el lomo donde va el jinete.
 */
public final class SerpentAnim {
    /** Distancia (metros del modelo) desde la cabeza hasta el asiento del jinete. */
    public static final float SEAT = 4.6f;
    /** Alto del eje del cuerpo en reposo (metros del modelo). */
    private static final float BASE_Y = 0.3f;
    private static final float DS = 0.08f;
    /** Largo de la onda del cuerpo (metros del modelo). */
    public static final float WAVE = 5.5f;

    public static final class Params {
        public float age, phase, move, run, hiss = -1, headYaw, headPitch;
    }

    private final SkinnedModel m;
    private final int head, mouth, tongue;
    private final int[] body;
    private final float[] bodyS;
    private final Matrix4f[] bindWorld;
    private final Vector3f[] bindPos;
    private final float headS;
    private final Quaternionf mouthParentRef, tongueParentRef;
    private final float[] cx, cy, cz, cyaw, cpitch;
    private final int steps;

    public SerpentAnim(SkinnedModel m) {
        this.m = m;
        this.head = m.bone("b_f_head_01");
        this.mouth = m.bone("b_f_mouse_01");
        this.tongue = m.bone("b_f_tongue_01");
        m.sample(0);
        m.compute();
        this.bindWorld = new Matrix4f[m.bones];
        this.bindPos = new Vector3f[m.bones];
        for (int b = 0; b < m.bones; b++) {
            this.bindWorld[b] = new Matrix4f(m.world(b));
            this.bindPos[b] = m.world(b).getTranslation(new Vector3f());
        }
        this.headS = 0;
        float headX = this.bindPos[this.head].x;
        java.util.List<Integer> list = new java.util.ArrayList<>();
        for (int i = 1; i <= 28; i++) {
            int b = m.bone(String.format("b_f_body_%02d", i));
            if (b >= 0) {
                list.add(b);
            }
        }
        this.body = list.stream().mapToInt(Integer::intValue).toArray();
        this.bodyS = new float[this.body.length];
        float maxS = 0;
        for (int i = 0; i < this.body.length; i++) {
            this.bodyS[i] = headX - this.bindPos[this.body[i]].x;
            maxS = Math.max(maxS, this.bodyS[i]);
        }
        this.steps = Mth.ceil((maxS + 1) / DS) + 2;
        this.cx = new float[this.steps];
        this.cy = new float[this.steps];
        this.cz = new float[this.steps];
        this.cyaw = new float[this.steps];
        this.cpitch = new float[this.steps];
        int hp = m.parent[this.mouth];
        this.mouthParentRef = m.world(hp).getUnnormalizedRotation(new Quaternionf()).normalize();
        this.tongueParentRef = this.tongue < 0 ? new Quaternionf()
                : m.world(m.parent[this.tongue]).getUnnormalizedRotation(new Quaternionf()).normalize();
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    public void apply(Params p) {
        float move = Mth.clamp(p.move, 0, 1), run = Mth.clamp(p.run, 0, 1), still = 1 - move;
        float t = p.age;
        // cuanto se levanta el cuello: alto quieto (cobra), mas bajo al avanzar, casi al ras corriendo
        float hiss = p.hiss >= 0 ? Mth.sin(Mth.clamp(p.hiss, 0, 1) * Mth.PI) : 0;
        float rise = 0.95f - move * 0.4f - run * 0.25f + hiss * 0.3f + Mth.sin(t * 0.05f) * 0.04f * still;
        float riseLen = 2.8f;
        // ondulacion: fuerte al moverse, suave respirando quieta
        float amp = 0.1f + move * 0.42f + run * 0.12f;
        float idleWave = t * 0.03f;
        // se integra la curva desde la cabeza hacia la cola
        float x = 0, y = 0, z = 0;
        for (int i = 0; i < this.steps; i++) {
            float s = i * DS;
            float a = amp * smooth(1.2f, 4.5f, s) * (1 - 0.35f * smooth(10f, 15f, s));
            float yaw = a * Mth.sin(Mth.TWO_PI * s / WAVE - p.phase - idleWave);
            // el cuello: vertical cerca de la cabeza, despues se acuesta
            float pitch = rise * (1 - smooth(riseLen * 0.35f, riseLen, s)) * smooth(0.0f, 0.5f, s);
            this.cx[i] = x;
            this.cy[i] = y;
            this.cz[i] = z;
            this.cyaw[i] = yaw;
            this.cpitch[i] = pitch;
            float cp = Mth.cos(pitch);
            x -= Mth.cos(yaw) * cp * DS;
            y -= Mth.sin(pitch) * DS;
            z += Mth.sin(yaw) * cp * DS;
        }
        // centra todo en el asiento: ahi queda en (0, BASE_Y, 0) mirando hacia +x
        int si = Math.min(this.steps - 1, Math.round(SEAT / DS));
        float sx = this.cx[si], sy = this.cy[si], sz = this.cz[si], syaw = this.cyaw[si];
        // la cola queda en el piso: la altura minima de la curva va a BASE_Y
        float floor = Float.MAX_VALUE;
        for (int i = si; i < this.steps; i++) {
            floor = Math.min(floor, this.cy[i]);
        }
        float lift = BASE_Y - Math.min(floor, sy);
        Quaternionf around = new Quaternionf().rotationY(-syaw);
        Vector3f tmp = new Vector3f();
        for (int k = 0; k < this.body.length; k++) {
            place(this.body[k], this.bodyS[k], sx, sy, sz, syaw, lift, around, tmp, 0, 0);
        }
        // cabeza: horizontal, mira hacia donde mira el jinete
        float hy = Mth.clamp(p.headYaw, -60, 60) * Mth.DEG_TO_RAD * 0.7f;
        float hp = Mth.clamp(p.headPitch, -30, 30) * Mth.DEG_TO_RAD * 0.5f;
        float sway = Mth.sin(t * 0.045f) * 0.12f * still;
        place(this.head, this.headS, sx, sy, sz, syaw, lift, around, tmp, -hy + sway, -hp - this.cpitch[0] + hiss * 0.35f);
        // boca y lengua: la lengua sale cada tanto; al sisear abre grande la boca
        float flickT = (t % 70f) / 70f;
        float flick = flickT < 0.18f ? Mth.sin(flickT / 0.18f * Mth.PI) : 0;
        m.sample(0);
        if (this.mouth >= 0) {
            float open = hiss * 0.75f + flick * 0.08f;
            turnLocal(this.mouth, this.mouthParentRef, 0, 0, 1, -open);
        }
        if (this.tongue >= 0) {
            turnLocal(this.tongue, this.tongueParentRef, 0, 0, 1, Mth.sin(t * 2.6f) * 0.25f * flick);
            turnLocal(this.tongue, this.tongueParentRef, 0, 1, 0, Mth.sin(t * 1.7f) * 0.2f * flick);
        }
        m.compute();
    }

    /** Pone un hueso sobre la curva, en la distancia s desde la cabeza. */
    private void place(int b, float s, float sx, float sy, float sz, float syaw, float lift, Quaternionf around, Vector3f tmp,
                       float extraYaw, float extraPitch) {
        float f = Mth.clamp(s / DS, 0, this.steps - 1.001f);
        int i = (int) f;
        float k = f - i;
        float px = Mth.lerp(k, this.cx[i], this.cx[i + 1]) - sx;
        float py = Mth.lerp(k, this.cy[i], this.cy[i + 1]) - sy;
        float pz = Mth.lerp(k, this.cz[i], this.cz[i + 1]) - sz;
        float yaw = Mth.lerp(k, this.cyaw[i], this.cyaw[i + 1]) - syaw + extraYaw;
        float pitch = Mth.lerp(k, this.cpitch[i], this.cpitch[i + 1]) + extraPitch;
        around.transform(tmp.set(px, py, pz));
        Vector3f bp = this.bindPos[b];
        // mundo = T(punto) * R(tangente) * T(-pos de reposo) * mundo de reposo
        Matrix4f w = this.m.forced[b] == null ? (this.m.forced[b] = new Matrix4f()) : this.m.forced[b];
        w.translation(tmp.x, tmp.y + sy + lift, tmp.z)
                .rotateY(yaw).rotateZ(pitch)
                .translate(-bp.x, -bp.y, -bp.z)
                .mul(this.bindWorld[b]);
    }

    private void turnLocal(int b, Quaternionf parentRef, float ax, float ay, float az, float angle) {
        if (angle == 0) {
            return;
        }
        Vector3f axis = parentRef.conjugate(new Quaternionf()).transform(new Vector3f(ax, ay, az));
        this.m.rot[b].premul(new Quaternionf().rotationAxis(angle, axis.normalize()));
    }
}
