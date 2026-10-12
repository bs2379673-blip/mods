package com.tunombre.bestialago;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Animacion procedural de la Bestia (la misma en cliente y servidor) y posicion de la boca en el mundo.
 * GENERADO por export_java.py desde model.py: las formulas son las mismas que las de las vistas previas.
 */
public final class BestiaAnim {
    public static final float SCALE = 3.5F;
    public static final float CENTER_H = 1.6F;
    public static final int COUNT = 16;
    public static final int BODY = 0;
    public static final int NECK = 1;
    public static final int HEAD = 2;
    public static final int JAW = 3;
    public static final int BARBEL1_R = 4;
    public static final int BARBEL2_R = 5;
    public static final int BARBEL1_L = 6;
    public static final int BARBEL2_L = 7;
    public static final int TAIL1 = 8;
    public static final int TAIL2 = 9;
    public static final int TAIL3 = 10;
    public static final int TAIL4 = 11;
    public static final int FIN_R = 12;
    public static final int RFIN_R = 13;
    public static final int FIN_L = 14;
    public static final int RFIN_L = 15;

    private BestiaAnim() { }

    private static void set(float[] r, int p, float x, float y, float z) {
        r[p * 3] = x; r[p * 3 + 1] = y; r[p * 3 + 2] = z;
    }

    /** rotaciones (x, y, z) de cada parte. ph fase de nado, t ticks, swim 0..1, jaw 0..1, shake 0..1, turn, lift 0..1 */
    public static void compute(float[] r, float ph, float t, float swim, float jaw, float shake, float turn, float lift) {
        java.util.Arrays.fill(r, 0F);
        float amp = 0.35F + 0.65F * swim;
        set(r, BODY, 0, 0.04F * amp * Mth.sin(ph), 0);
        float headX = -jaw * 0.22F - lift * 0.45F;
        set(r, NECK, -jaw * 0.10F - lift * 0.35F, -0.05F * amp * Mth.sin(ph + 0.6F) + 0.25F * turn, 0);
        set(r, HEAD, headX, -0.06F * amp * Mth.sin(ph + 1.0F) + 0.25F * turn + shake * 0.14F * Mth.sin(t * 1.7F), shake * 0.10F * Mth.sin(t * 1.3F + 1.0F));
        int[] tails = {TAIL1, TAIL2, TAIL3, TAIL4};
        for (int i = 0; i < 4; i++) {
            int k = i + 1;
            set(r, tails[i], 0.03F * Mth.sin(ph * 0.5F - k), (0.09F + 0.07F * k) * amp * Mth.sin(ph - 0.9F * k) - turn * 0.45F * k / 4F, 0);
        }
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            int fin = sgn > 0 ? FIN_R : FIN_L, rfin = sgn > 0 ? RFIN_R : RFIN_L;
            set(r, fin, 0, sgn * (0.45F + 0.35F * swim + 0.30F * (1 - 0.5F * swim) * Mth.sin(ph * 0.5F + 0.8F)), sgn * (0.35F * (1 - swim) * Mth.sin(ph * 0.5F) + 0.15F));
            set(r, rfin, 0, sgn * (0.55F + 0.3F * swim + 0.25F * Mth.sin(ph * 0.5F + 2.2F)), sgn * (0.2F * Mth.sin(ph * 0.5F + 1.4F) + 0.1F));
            int b1 = sgn > 0 ? BARBEL1_R : BARBEL1_L, b2 = sgn > 0 ? BARBEL2_R : BARBEL2_L;
            set(r, b1, 0.55F + 0.25F * swim + 0.18F * Mth.sin(ph * 0.7F + sgn) - headX * 0.8F, 0, sgn * (0.25F + 0.12F * Mth.sin(ph * 0.6F + 2 * sgn)));
            set(r, b2, 0.35F + 0.25F * Mth.sin(ph * 0.7F + sgn - 1.2F), 0, 0);
        }
        set(r, JAW, jaw * 0.95F + shake * 0.06F * Mth.sin(t * 3.1F), 0, 0);
    }

    private static final float[] PIV_NECK = {0.0F, -1.0F, -11.0F};
    private static final float[] PIV_HEAD = {0.0F, -1.0F, -7.0F};
    private static final float[] PIV_JAW = {0.0F, 1.0F, 0.0F};

    /** matriz del cuerpo del modelo en el mundo (relativa a la posicion de la entidad), igual que el renderer */
    public static Matrix4f rootMatrix(float yawDeg, float pitchDeg) {
        return new Matrix4f().translate(0, CENTER_H, 0)
                .rotateY((float) Math.toRadians(180F - yawDeg))
                .rotateX((float) Math.toRadians(-pitchDeg))
                .scale(-SCALE, -SCALE, SCALE);
    }

    private static Matrix4f apply(Matrix4f m, float[] piv, float[] r, int p) {
        return m.translate(piv[0] / 16F, piv[1] / 16F, piv[2] / 16F).rotateZYX(r[p * 3 + 2], r[p * 3 + 1], r[p * 3]);
    }

    /** punto local (en pixeles) de la MANDIBULA inferior -> desplazamiento en el mundo respecto a la entidad */
    public static Vec3 jawPoint(float[] r, float yawDeg, float pitchDeg, float lx, float ly, float lz) {
        Matrix4f m = rootMatrix(yawDeg, pitchDeg).rotateZYX(r[BODY * 3 + 2], r[BODY * 3 + 1], r[BODY * 3]);
        apply(m, PIV_NECK, r, NECK);
        apply(m, PIV_HEAD, r, HEAD);
        apply(m, PIV_JAW, r, JAW);
        Vector3f v = m.transformPosition(new Vector3f(lx / 16F, ly / 16F, lz / 16F));
        return new Vec3(v.x, v.y, v.z);
    }
}
