package com.starlevi.starriders.client;

import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * La serpiente mistica: usa las animaciones que trae el modelo (se enrosca girando y se levanta,
 * quieta erguida sobre su enroscado, se estira hacia arriba) mezcladas suave entre si, y le suma por
 * codigo el vaiven al flotar, mirar, sacar la lengua y sisear.
 */
public final class SerpentAnim {
    /** Cuadros (de la animacion guardada, 25 por segundo) de cada parte. */
    public static final float RISE_A = 4, RISE_B = 88, IDLE_A = 88, IDLE_B = 130, REAR_A = 158, REAR_B = 228;
    /** Huesos sobre los que van sentados los pasajeros (el primero es el del dueno). */
    public static final String[] SEAT_BONES = {"b_f_body_13", "b_f_body_11", "b_f_body_16", "b_f_body_22", "b_f_body_25", "b_f_body_19"};

    public static final class Params {
        /** ticks; move 0..1; run 0..1; rise 0..1 mientras se levanta (o -1); glide 0..1. */
        public float age, move, run, rise = -1, riseWeight, glide, hiss = -1, headYaw, headPitch;
    }

    private final SkinnedModel m;
    private final int com, head, mouth, tongue;
    private final int[] seats;
    /** Huesos del cuerpo, de la cabeza a la cola (son hermanos: cada uno se mueve por su cuenta). */
    private final int[] body;
    private final Quaternionf[] parentRef;

    public SerpentAnim(SkinnedModel m) {
        this.m = m;
        this.com = m.bone("b_com_01");
        this.head = m.bone("b_f_head_01");
        this.mouth = m.bone("b_f_mouse_01");
        this.tongue = m.bone("b_f_tongue_01");
        java.util.List<Integer> bl = new java.util.ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            int b = m.bone(String.format("b_f_body_%02d", i));
            if (b >= 0) {
                bl.add(b);
            }
        }
        this.body = bl.stream().mapToInt(Integer::intValue).toArray();
        this.seats = new int[SEAT_BONES.length];
        for (int i = 0; i < SEAT_BONES.length; i++) {
            this.seats[i] = m.bone(SEAT_BONES[i]);
        }
        m.sample(IDLE_A + 20);
        m.compute();
        this.parentRef = new Quaternionf[m.bones];
        for (int b = 0; b < m.bones; b++) {
            int p = m.parent[b];
            this.parentRef[b] = p < 0 ? new Quaternionf() : m.world(p).getUnnormalizedRotation(new Quaternionf()).normalize();
        }
    }

    private static float pingPong(float t, float a, float b) {
        float len = b - a;
        float x = t % (2 * len);
        if (x < 0) {
            x += 2 * len;
        }
        // ida y vuelta con frenado suave en los extremos
        float k = x < len ? x / len : 2 - x / len;
        return a + len * (0.5f - 0.5f * Mth.cos(k * Mth.PI));
    }

    private void turn(int b, float ax, float ay, float az, float angle) {
        if (b < 0 || angle == 0) {
            return;
        }
        Vector3f axis = this.parentRef[b].conjugate(new Quaternionf()).transform(new Vector3f(ax, ay, az));
        this.m.rot[b].premul(new Quaternionf().rotationAxis(angle, axis.normalize()));
    }

    public void apply(Params p) {
        SkinnedModel m = this.m;
        float t = p.age;
        // quieta erguida, respirando (va y vuelve sobre su propio movimiento)
        m.sample(pingPong(t * 0.55f, IDLE_A, IDLE_B));
        // planeando: se estira hacia arriba, ondulando lento
        if (p.glide > 0) {
            m.blend(pingPong(t * 0.4f, REAR_A + 10, REAR_B - 6), smooth(Mth.clamp(p.glide, 0, 1)) * 0.85f);
        }
        // levitando: se enrosca girando y se levanta (la animacion del modelo)
        if (p.rise >= 0 && p.riseWeight > 0) {
            m.blend(Mth.lerp(Mth.clamp(p.rise, 0, 1), RISE_A, RISE_B), smooth(Mth.clamp(p.riseWeight, 0, 1)));
        }
        // flotando al avanzar: se mece de lado a lado y se inclina un poco hacia adelante
        float move = Mth.clamp(p.move, 0, 1), run = Mth.clamp(p.run, 0, 1);
        turn(this.com, 0, 1, 0, Mth.sin(t * 0.12f) * 0.1f * move);
        turn(this.com, 1, 0, 0, Mth.sin(t * 0.12f + 1) * 0.06f * move);
        turn(this.com, 0, 0, 1, -0.12f * move - 0.1f * run);
        // avanzando: una onda corre de la cabeza a la cola (de costado y un poco de arriba abajo), como si
        // nadara en el aire; corriendo es mas rapida y mas ancha; la cabeza cabecea con cada ola
        float wave = Mth.clamp(move * (1 - p.riseWeight), 0, 1);
        if (wave > 0.01f) {
            float speed = 0.22f + run * 0.12f, amp = (0.24f + run * 0.1f) * smooth(wave);
            int n = this.body.length;
            for (int i = 0; i < n; i++) {
                int b = this.body[i];
                float ph = t * speed - i * 0.42f;
                // la cola se mueve mas que el cuello; la cabeza poco (sigue mirando adelante)
                float along = 0.35f + 0.65f * Math.min(1, i / 8f);
                m.tr[b].add(0, Mth.cos(ph * 0.5f) * amp * 0.35f * along, Mth.sin(ph) * amp * along);
                turn(b, 0, 1, 0, Mth.cos(ph) * amp * 0.9f * along);
            }
            float ph0 = t * speed;
            m.tr[this.head].add(0, Mth.cos(ph0 * 0.5f) * amp * 0.35f * 0.35f, Mth.sin(ph0) * amp * 0.35f);
            turn(this.head, 0, 0, 1, Mth.sin(ph0 - 0.8f) * 0.07f * smooth(wave));
            turn(this.head, 0, 1, 0, -Mth.cos(ph0) * amp * 0.6f);
        }
        // mirar
        float hy = Mth.clamp(p.headYaw, -60, 60) * Mth.DEG_TO_RAD * 0.6f;
        float hp = Mth.clamp(p.headPitch, -35, 35) * Mth.DEG_TO_RAD * 0.5f;
        turn(this.head, 0, 1, 0, -hy);
        turn(this.head, 0, 0, 1, -hp);
        // sisear: levanta la cabeza y abre la boca grande
        float hiss = p.hiss >= 0 ? Mth.sin(Mth.clamp(p.hiss, 0, 1) * Mth.PI) : 0;
        turn(this.head, 0, 0, 1, hiss * 0.3f);
        float flickT = (t % 70f) / 70f;
        float flick = flickT < 0.18f ? Mth.sin(flickT / 0.18f * Mth.PI) : 0;
        if (this.mouth >= 0) {
            turn(this.mouth, 0, 0, 1, -(hiss * 0.7f + flick * 0.06f));
        }
        if (this.tongue >= 0) {
            turn(this.tongue, 0, 0, 1, Mth.sin(t * 2.6f) * 0.25f * flick);
            turn(this.tongue, 0, 1, 0, Mth.sin(t * 1.7f) * 0.2f * flick);
        }
        m.compute();
    }

    /** Hueso del asiento n. */
    public int seatBone(int n) {
        return this.seats[Math.min(n, this.seats.length - 1)];
    }

    /** Posicion (modelo, metros) arriba del hueso del asiento n. */
    public Vector3f seat(int n, Vector3f out) {
        int b = this.seats[Math.min(n, this.seats.length - 1)];
        return this.m.world(b).getTranslation(out).add(0, 0.33f, 0);
    }

    private static float smooth(float x) {
        return x * x * (3 - 2 * x);
    }
}
