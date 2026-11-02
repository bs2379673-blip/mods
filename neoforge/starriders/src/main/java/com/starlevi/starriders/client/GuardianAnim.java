package com.starlevi.starriders.client;

import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Animaciones del Guardian del Bosque. El modelo trae una caminata en bucle; encima se suman:
 * respirar quieto, mirar, correr (inclinado y con mas impulso), salto (encoge las patas), rugido
 * (se para, abre los brazos y sacude la melena) y la melena de raices que se mece sola.
 */
public final class GuardianAnim {
    /** Cuadro (de la caminata) que se usa como pose de quieto: las dos patas apoyadas. */
    public static final float IDLE_FRAME = 18f;
    /** Metros (del modelo) que avanza por cuadro de la caminata, para que las patas no patinen. */
    public static final float STRIDE_PER_FRAME = 0.0109f;

    public static final class Params {
        public float age, walkFrame, walkAmount, run, air, roar = -1, headYaw, headPitch, tame;
    }

    private final SkinnedModel m;
    private final int root, pelvis, spine, spine1, neck, head, clavL, clavR, armL, armR, foreL, foreR, thighL, thighR, calfL, calfR;
    private final int[] maneL, maneR;
    /** Rotacion del padre de cada hueso en la pose de quieto (para girar en ejes del mundo del modelo). */
    private final Quaternionf[] parentRef;

    public GuardianAnim(SkinnedModel m) {
        this.m = m;
        this.root = m.bone("Bip001");
        this.pelvis = m.bone("Bip001_Pelvis");
        this.spine = m.bone("Bip001_Spine");
        this.spine1 = m.bone("Bip001_Spine1");
        this.neck = m.bone("Bip001_Neck");
        this.head = m.bone("Bip001_Head");
        this.clavL = m.bone("Bip001_L_Clavicle");
        this.clavR = m.bone("Bip001_R_Clavicle");
        this.armL = m.bone("Bip001_L_UpperArm");
        this.armR = m.bone("Bip001_R_UpperArm");
        this.foreL = m.bone("Bip001_L_Forearm");
        this.foreR = m.bone("Bip001_R_Forearm");
        this.thighL = m.bone("Bip001_L_Thigh");
        this.thighR = m.bone("Bip001_R_Thigh");
        this.calfL = m.bone("Bip001_L_Calf");
        this.calfR = m.bone("Bip001_R_Calf");
        this.maneL = new int[]{m.bone("BN_L_TreeHKmane_0"), m.bone("BN_L_TreeHKmane_1"), m.bone("BN_L_TreeHKmane_2")};
        this.maneR = new int[]{m.bone("BN_R_TreeHKmane_0"), m.bone("BN_R_TreeHKmane_1"), m.bone("BN_R_TreeHKmane_2")};
        m.sample(IDLE_FRAME);
        m.compute();
        this.parentRef = new Quaternionf[m.bones];
        for (int b = 0; b < m.bones; b++) {
            int p = m.parent[b];
            this.parentRef[b] = p < 0 ? new Quaternionf() : m.world(p).getUnnormalizedRotation(new Quaternionf()).normalize();
        }
    }

    /**
     * Gira un hueso alrededor de un eje del espacio del modelo (x izquierda, y arriba, z adelante).
     * Se pasa a los ejes del padre con su rotacion en la pose de quieto.
     */
    private void turn(int b, float ax, float ay, float az, float angle) {
        if (b < 0 || angle == 0) {
            return;
        }
        Vector3f axis = new Vector3f(ax, ay, az);
        this.parentRef[b].conjugate(new Quaternionf()).transform(axis);
        this.m.rot[b].premul(new Quaternionf().rotationAxis(angle, axis.normalize()));
    }

    /** Cabeceo (positivo = hacia abajo/adelante). */
    private void pitch(int b, float a) {
        turn(b, 1, 0, 0, a);
    }

    private void yaw(int b, float a) {
        turn(b, 0, 1, 0, a);
    }

    private void roll(int b, float a) {
        turn(b, 0, 0, 1, a);
    }

    public void apply(Params p) {
        SkinnedModel m = this.m;
        m.sample(IDLE_FRAME);
        m.blend(p.walkFrame, p.walkAmount);
        float t = p.age;
        float still = 1 - p.walkAmount;
        // respirar
        float br = Mth.sin(t * 0.07f);
        pitch(this.spine1, br * 0.035f * (0.4f + still));
        pitch(this.clavL, -br * 0.04f * still);
        pitch(this.clavR, -br * 0.04f * still);
        // quieto: se mece apenas y cada tanto mueve los brazos
        yaw(this.spine, Mth.sin(t * 0.031f) * 0.05f * still);
        pitch(this.armL, Mth.sin(t * 0.05f + 1) * 0.06f * still);
        pitch(this.armR, Mth.sin(t * 0.05f + 2.4f) * 0.06f * still);
        // correr: se inclina, rebota y mueve mas los brazos
        float run = p.run * p.walkAmount;
        float cyc = p.walkFrame / m.frames * Mth.TWO_PI * 2;
        pitch(this.root, run * 0.28f);
        pitch(this.spine, run * 0.12f + Mth.sin(cyc) * run * 0.06f);
        pitch(this.neck, -run * 0.25f);
        pitch(this.armL, Mth.sin(cyc) * run * 0.35f);
        pitch(this.armR, -Mth.sin(cyc) * run * 0.35f);
        // en el aire: encoge las patas y estira los brazos hacia adelante
        float air = p.air;
        pitch(this.thighL, air * 0.45f);
        pitch(this.thighR, air * 0.45f);
        pitch(this.calfL, air * 0.5f);
        pitch(this.calfR, air * 0.5f);
        pitch(this.armL, air * 0.55f);
        pitch(this.armR, air * 0.55f);
        pitch(this.spine1, -air * 0.12f);
        // rugido: se yergue, levanta los brazos, alza la cabeza y sacude la melena
        float shake = 0;
        if (p.roar >= 0) {
            float r = p.roar;
            float up = Mth.sin(Math.min(1, r * 3f) * Mth.HALF_PI) * (1 - smooth(Mth.clamp((r - 0.7f) / 0.3f, 0, 1)));
            pitch(this.spine, -up * 0.3f);
            pitch(this.spine1, -up * 0.25f);
            pitch(this.neck, -up * 0.3f);
            pitch(this.head, -up * 0.45f);
            pitch(this.armL, up * 0.8f);
            pitch(this.armR, up * 0.8f);
            shake = up * Mth.sin(t * 2.2f);
            yaw(this.head, shake * 0.12f);
        }
        // mirar hacia donde mira el jinete / el jugador
        float hy = Mth.clamp(p.headYaw, -60, 60) * Mth.DEG_TO_RAD, hp = Mth.clamp(p.headPitch, -40, 40) * Mth.DEG_TO_RAD;
        yaw(this.neck, -hy * 0.4f);
        yaw(this.head, -hy * 0.6f);
        pitch(this.neck, hp * 0.35f);
        pitch(this.head, hp * 0.5f);
        // melena de raices: se mece sola, mas al moverse y al sacudirse
        float amp = 0.06f + p.walkAmount * 0.08f + run * 0.1f + Math.abs(shake) * 0.25f + air * 0.15f;
        for (int i = 0; i < 3; i++) {
            float w = Mth.sin(t * 0.11f - i * 0.8f);
            float w2 = Mth.sin(t * 0.083f - i * 0.6f + 1.3f);
            pitch(this.maneL[i], w * amp - air * 0.12f);
            pitch(this.maneR[i], w2 * amp - air * 0.12f);
            roll(this.maneL[i], w2 * amp * 0.6f + shake * 0.15f);
            roll(this.maneR[i], -w * amp * 0.6f - shake * 0.15f);
        }
        m.compute();
    }

    private static float smooth(float x) {
        return x * x * (3 - 2 * x);
    }

    /** Altura de los pies (y minimo de la piel) en la pose actual: para apoyarlo en el piso. */
    public static float minY(SkinnedModel m) {
        return m.minSkinnedY();
    }

    public Matrix4f world(int b) {
        return this.m.world(b);
    }
}
