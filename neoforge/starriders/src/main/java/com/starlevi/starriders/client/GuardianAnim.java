package com.starlevi.starriders.client;

import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Animaciones del Guardian del Bosque. Usa la caminata original del modelo (60 cuadros por segundo,
 * interpolada) y le suma, con transiciones suaves: respirar quieto, mirar, correr (inclinado y con mas
 * impulso), salto (encoge las patas), rugido (se yergue, abre los brazos y sacude la melena) y la
 * melena de raices que se mece sola.
 */
public final class GuardianAnim {
    /** Cuadro de la caminata que se usa como pose de quieto: las dos patas apoyadas. */
    public static final float IDLE_FRAME = 36f;
    /** Metros (del modelo) que avanza el cuerpo por cuadro de la caminata, para que las patas no patinen. */
    public static final float STRIDE_PER_FRAME = 0.00576f;

    public static final class Params {
        public float age, walkFrame, walkAmount, run, air, roar = -1, headYaw, headPitch, grapple;
    }

    private final SkinnedModel m;
    private final int handL, handR, spine, spine1, neck, head, clavL, clavR, armL, armR, foreL, foreR, thighL, thighR, calfL, calfR;
    private final int[] maneL, maneR;
    /** Rotacion del padre de cada hueso en la pose de quieto (para girar en ejes del modelo). */
    private final Quaternionf[] parentRef;
    /** Vertices de las patas (para apoyarlas en el piso). */
    private final int[] feet;

    public GuardianAnim(SkinnedModel m) {
        this.m = m;
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
        this.handL = m.bone("Bip001_L_Hand");
        this.handR = m.bone("Bip001_R_Hand");
        this.maneL = new int[]{m.bone("BN_L_TreeHKmane_0"), m.bone("BN_L_TreeHKmane_1"), m.bone("BN_L_TreeHKmane_2")};
        this.maneR = new int[]{m.bone("BN_R_TreeHKmane_0"), m.bone("BN_R_TreeHKmane_1"), m.bone("BN_R_TreeHKmane_2")};
        m.sample(IDLE_FRAME);
        m.compute();
        this.parentRef = new Quaternionf[m.bones];
        for (int b = 0; b < m.bones; b++) {
            int p = m.parent[b];
            this.parentRef[b] = p < 0 ? new Quaternionf() : m.world(p).getUnnormalizedRotation(new Quaternionf()).normalize();
        }
        this.feet = m.vertsOf("Bip001_L_Foot", "Bip001_R_Foot", "Bip001_L_Toe0", "Bip001_R_Toe0");
    }

    /**
     * Gira un hueso alrededor de un eje del modelo (x izquierda, y arriba, z adelante), pasado a los
     * ejes del padre con su rotacion en la pose de quieto.
     */
    private void turn(int b, float ax, float ay, float az, float angle) {
        if (b < 0 || angle == 0) {
            return;
        }
        Vector3f axis = new Vector3f(ax, ay, az);
        this.parentRef[b].conjugate(new Quaternionf()).transform(axis);
        this.m.rot[b].premul(new Quaternionf().rotationAxis(angle, axis.normalize()));
    }

    /** Cabeceo: positivo baja el frente (en patas y brazos, positivo los lleva hacia atras). */
    private void pitch(int b, float a) {
        turn(b, 1, 0, 0, a);
    }

    /** Giro: positivo hacia la izquierda del guardian. */
    private void yaw(int b, float a) {
        turn(b, 0, 1, 0, a);
    }

    /** Ladeo: positivo lleva hacia la izquierda (abre el brazo izquierdo, cierra el derecho). */
    private void roll(int b, float a) {
        turn(b, 0, 0, 1, a);
    }

    public void apply(Params p) {
        SkinnedModel m = this.m;
        m.sample(IDLE_FRAME);
        m.blend(p.walkFrame, smooth(Mth.clamp(p.walkAmount, 0, 1)));
        float t = p.age;
        float still = 1 - Mth.clamp(p.walkAmount, 0, 1);
        // respirar: el pecho sube y baja, los hombros acompanan
        float br = Mth.sin(t * 0.075f);
        pitch(this.spine1, -br * 0.03f * (0.5f + still));
        roll(this.clavL, br * 0.025f * still);
        roll(this.clavR, -br * 0.025f * still);
        // quieto: se mece apenas, mueve los brazos y mira alrededor de vez en cuando
        yaw(this.spine, Mth.sin(t * 0.027f) * 0.06f * still);
        pitch(this.armL, Mth.sin(t * 0.045f + 1) * 0.05f * still);
        pitch(this.armR, Mth.sin(t * 0.045f + 2.4f) * 0.05f * still);
        float glance = Mth.sin(t * 0.013f) * Mth.sin(t * 0.031f);
        yaw(this.neck, glance * 0.25f * still);
        // correr: inclinado hacia adelante, cabeza alta, brazos con mas impulso y el lomo ondulando
        float run = smooth(Mth.clamp(p.run, 0, 1)) * Mth.clamp(p.walkAmount, 0, 1);
        float cyc = p.walkFrame / m.frames * Mth.TWO_PI * 2;
        pitch(this.spine, run * (0.22f + Mth.sin(cyc) * 0.05f));
        pitch(this.spine1, run * 0.08f);
        pitch(this.neck, -run * 0.2f);
        pitch(this.armL, -Mth.sin(cyc) * run * 0.35f);
        pitch(this.armR, Mth.sin(cyc) * run * 0.35f);
        // en el aire: rodillas adelante, patas plegadas, brazos estirados al frente
        float air = smooth(Mth.clamp(p.air, 0, 1));
        pitch(this.thighL, -air * 0.6f);
        pitch(this.thighR, -air * 0.6f);
        pitch(this.calfL, air * 0.75f);
        pitch(this.calfR, air * 0.75f);
        pitch(this.armL, -air * 0.45f);
        pitch(this.armR, -air * 0.45f);
        pitch(this.spine1, -air * 0.1f);
        // rugido: se yergue, abre los brazos, alza la cabeza y sacude la melena
        float shake = 0;
        if (p.roar >= 0) {
            float r = p.roar;
            float up = smooth(Mth.clamp(r / 0.25f, 0, 1)) * (1 - smooth(Mth.clamp((r - 0.7f) / 0.3f, 0, 1)));
            pitch(this.spine, -up * 0.3f);
            pitch(this.spine1, -up * 0.2f);
            pitch(this.neck, -up * 0.25f);
            pitch(this.head, -up * 0.35f);
            roll(this.armL, up * 0.8f);
            roll(this.armR, -up * 0.8f);
            pitch(this.foreL, -up * 0.4f);
            pitch(this.foreR, -up * 0.4f);
            shake = up * Mth.sin(t * 1.9f);
            yaw(this.head, shake * 0.1f);
            yaw(this.neck, shake * 0.06f);
        }
        // raices: estira los brazos hacia arriba y adelante, el cuerpo se arquea hacia el enganche
        float gr = smooth(Mth.clamp(p.grapple, 0, 1));
        if (gr > 0) {
            pitch(this.armL, -1.5f * gr);
            pitch(this.armR, -1.5f * gr);
            pitch(this.foreL, -0.5f * gr);
            pitch(this.foreR, -0.5f * gr);
            pitch(this.spine, -0.25f * gr);
            pitch(this.neck, -0.2f * gr);
            pitch(this.thighL, -0.3f * gr);
            pitch(this.thighR, -0.2f * gr);
        }
        // mirar hacia donde mira el jinete / el jugador
        float hy = Mth.clamp(p.headYaw, -70, 70) * Mth.DEG_TO_RAD, hp = Mth.clamp(p.headPitch, -40, 40) * Mth.DEG_TO_RAD;
        yaw(this.neck, -hy * 0.4f);
        yaw(this.head, -hy * 0.5f);
        pitch(this.neck, hp * 0.3f);
        pitch(this.head, hp * 0.4f);
        // melena de raices: se mece sola, mas al moverse y al sacudirse; en el aire flota hacia arriba
        float amp = 0.05f + Mth.clamp(p.walkAmount, 0, 1) * 0.06f + run * 0.08f + Math.abs(shake) * 0.2f;
        for (int i = 0; i < 3; i++) {
            float w = Mth.sin(t * 0.09f - i * 0.7f);
            float w2 = Mth.sin(t * 0.071f - i * 0.6f + 1.3f);
            float lift = air * 0.12f + run * 0.06f;
            pitch(this.maneL[i], w * amp + lift);
            pitch(this.maneR[i], w2 * amp + lift);
            roll(this.maneL[i], w2 * amp * 0.5f + shake * 0.12f);
            roll(this.maneR[i], -w * amp * 0.5f - shake * 0.12f);
        }
        m.compute();
    }

    /** Altura de las patas (lo mas bajo de pies y dedos) en la pose ya calculada. */
    public float ground() {
        return this.m.minY(this.feet);
    }

    /** Hueso del lomo de donde va agarrado el jinete. */
    public int gripBone() {
        return this.spine1;
    }

    /** Posicion de la mano (en el modelo, ya calculado). */
    public org.joml.Vector3f hand(boolean left, org.joml.Vector3f out) {
        return this.m.world(left ? this.handL : this.handR).getTranslation(out);
    }

    private static float smooth(float x) {
        return x * x * (3 - 2 * x);
    }
}
