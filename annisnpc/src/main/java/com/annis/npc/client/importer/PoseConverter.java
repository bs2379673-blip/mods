package com.annis.npc.client.importer;

import java.util.ArrayList;
import java.util.List;

/**
 * Convierte poses de un esqueleto cualquiera a los 6 huesos del modelo de Minecraft
 * (direccion hombro->muneca, cadera->tobillo, inclinacion del torso, mirada de la cabeza),
 * mas el giro del cuerpo entero, el rebote de cadera y el balanceo. Resultado a 20 fps.
 */
final class PoseConverter {
    /** Resultado: frames de 36 valores (formato de MoveLibrary) y giro del cuerpo por frame. */
    record Result(List<float[]> frames, float[] yaw) {
    }

    private PoseConverter() {
    }

    /**
     * @param poses     poses en orden
     * @param fps       fotogramas por segundo de origen
     * @param frontSign +1 si "delante" = izquierda x arriba (sistemas diestros, BVH), -1 en MMD
     * @param headLocal direccion "delante" en el sistema local de la cabeza, o null para tomarla del primer frame
     */
    static Result convert(List<Pose> poses, double fps, double frontSign, double[] headLocal) {
        int n = poses.size();
        Pose p0 = poses.get(0);
        double[] hips0 = V.mid(p0.pos.get("legL"), p0.pos.get("legR"));
        double[] up0 = V.sub(p0.pos.get("head"), hips0);
        int ax = 0;
        for (int i = 1; i < 3; i++) {
            if (Math.abs(up0[i]) > Math.abs(up0[ax])) {
                ax = i;
            }
        }
        double[] u = V.v(0, 0, 0);
        u[ax] = up0[ax] > 0 ? 1 : -1;
        final double[] U = u;

        double[][] lf0 = frame(p0, U);
        double sign = frontSign;
        if (sign == 0) {
            sign = 1;
            double[] toe = p0.pos.get("toeL");
            if (toe != null && V.dot(horiz(V.sub(toe, p0.pos.get("footL")), U), lf0[1]) < 0) {
                sign = -1;
            }
        }
        double[] L0 = lf0[0];
        double[] F0 = V.mul(lf0[1], sign);
        double legLen = V.len(V.sub(p0.pos.get("legL"), p0.pos.get("footL")));
        double scale = 12.0 / Math.max(1e-6, legLen);
        double h0 = V.dot(hips0, U);
        double[] headFwdLocal = headLocal != null ? headLocal : V.mv(V.transpose(p0.headRot), F0);

        double[][] raw = new double[n][32];
        double[] yaw = new double[n];
        for (int i = 0; i < n; i++) {
            Pose p = poses.get(i);
            double[][] lf = frame(p, U);
            double[] L = lf[0];
            double[] F = V.mul(lf[1], sign);
            double[] r = raw[i];
            double[] head = mc(V.mv(p.headRot, headFwdLocal), L, U, F);
            head = V.norm(head);
            r[0] = clamp(Math.asin(clamp(head[1], -1, 1)), -0.9, 0.9);
            r[1] = Math.atan2(-head[0], -head[2]);
            double[] hips = V.mid(p.pos.get("legL"), p.pos.get("legR"));
            double[] neck = p.pos.get("neck") != null ? p.pos.get("neck") : p.pos.get("head");
            double[] d = V.norm(mc(V.sub(hips, neck), L, U, F));
            r[2] = Math.asin(clamp(d[2], -1, 1));
            double[] sl = mc(V.sub(p.pos.get("armL"), p.pos.get("armR")), L, U, F);
            r[3] = Math.atan2(-sl[2], sl[0]);
            r[4] = Math.atan2(-d[0], d[1]);
            limb(p, "armR", "handR", L, U, F, r, 5);
            limb(p, "armL", "handL", L, U, F, r, 7);
            limb(p, "legR", "footR", L, U, F, r, 9);
            limb(p, "legL", "footL", L, U, F, r, 11);
            r[13] = (h0 - V.dot(hips, U)) * scale;
            double[] feet = V.mid(p.pos.get("footL"), p.pos.get("footR"));
            double[] sway = V.mul(mc(V.sub(hips, feet), L, U, F), scale);
            r[14] = sway[0];
            r[15] = sway[2];
            yaw[i] = Math.atan2(V.dot(F, L0), V.dot(F, F0));
            // modelo articulado: tramo de arriba + doblez (si hay codos/rodillas en el esqueleto)
            joint(p, "armR", "elbowR", "handR", false, L, U, F, r, 16);
            joint(p, "armL", "elbowL", "handL", false, L, U, F, r, 20);
            joint(p, "legR", "kneeR", "footR", true, L, U, F, r, 24);
            joint(p, "legL", "kneeL", "footL", true, L, U, F, r, 28);
        }
        // desenrollar angulos para que no salten de +pi a -pi
        for (int c = 0; c < 32; c++) {
            if (c >= 13 && c < 16) {
                continue; // rebote y balanceo no son angulos
            }
            for (int i = 1; i < n; i++) {
                raw[i][c] = unwrap(raw[i - 1][c], raw[i][c]);
            }
        }
        for (int i = 1; i < n; i++) {
            yaw[i] = unwrap(yaw[i - 1], yaw[i]);
        }

        // remuestrear a 20 fps con un suavizado ligero (quita temblores de la captura)
        double duration = (n - 1) / fps;
        int out = Math.max(1, (int) Math.floor(duration * 20.0) + 1);
        List<float[]> frames = new ArrayList<>(out);
        float[] yawOut = new float[out];
        for (int k = 0; k < out; k++) {
            double src = k / 20.0 * fps;
            double[] v = new double[32];
            double y = 0;
            double wsum = 0;
            for (int o = -1; o <= 1; o++) {
                double w = o == 0 ? 2 : 1;
                double s = clamp(src + o * fps / 40.0, 0, n - 1);
                int i0 = (int) Math.floor(s);
                int i1 = Math.min(n - 1, i0 + 1);
                double f = s - i0;
                for (int c = 0; c < 32; c++) {
                    v[c] += w * (raw[i0][c] + (raw[i1][c] - raw[i0][c]) * f);
                }
                y += w * (yaw[i0] + (yaw[i1] - yaw[i0]) * f);
                wsum += w;
            }
            for (int c = 0; c < 32; c++) {
                v[c] /= wsum;
            }
            y /= wsum;
            float bob = (float) clamp(v[13], -3, 6);
            float sx = (float) clamp(v[14], -2.5, 2.5);
            float sz = (float) clamp(v[15], -2.5, 2.5);
            frames.add(new float[]{
                    (float) v[0], (float) v[1], 0f, sx, bob, sz,
                    (float) v[2], (float) v[3], (float) v[4], sx, bob, sz,
                    (float) v[5], 0f, (float) v[6], -5f + sx, 2f + bob, sz,
                    (float) v[7], 0f, (float) v[8], 5f + sx, 2f + bob, sz,
                    (float) v[9], 0f, (float) v[10], -1.9f, 12f, 0.1f,
                    (float) v[11], 0f, (float) v[12], 1.9f, 12f, 0.1f,
                    (float) v[16], (float) v[17], (float) v[18], (float) v[19],
                    (float) v[20], (float) v[21], (float) v[22], (float) v[23],
                    (float) v[24], (float) v[25], (float) v[26], (float) v[27],
                    (float) v[28], (float) v[29], (float) v[30], (float) v[31]});
            yawOut[k] = (float) y;
        }
        return new Result(frames, yawOut);
    }

    private static void limb(Pose p, String from, String to, double[] L, double[] U, double[] F, double[] r, int at) {
        double[] d = V.norm(mc(V.sub(p.pos.get(to), p.pos.get(from)), L, U, F));
        r[at] = Math.asin(clamp(d[2], -1, 1));
        r[at + 1] = Math.atan2(-d[0], d[1]);
    }

    /**
     * Tramo de arriba (pitch, yaw, roll de ModelPart) girado para que la bisagra doble en el plano del
     * codo/rodilla, mas el angulo de doblez (codo hacia delante negativo, rodilla hacia atras positivo).
     */
    private static void joint(Pose p, String a, String b, String c, boolean knee,
                              double[] L, double[] U, double[] F, double[] r, int at) {
        double[] pa = p.pos.get(a);
        double[] pb = p.pos.get(b);
        double[] pc = p.pos.get(c);
        if (pb == null) {
            // sin codo/rodilla en el esqueleto: miembro recto como en el modelo rigido
            pb = V.mid(pa, pc);
        }
        double[] d1 = V.norm(mc(V.sub(pb, pa), L, U, F));
        double[] d2 = V.norm(mc(V.sub(pc, pb), L, U, F));
        double ca = clamp(V.dot(d1, d2), -1, 1);
        double al = Math.acos(ca);
        double[] back = V.v(0, 0, 1);
        double[] zc = V.sub(back, V.mul(d1, V.dot(back, d1)));
        if (V.len(zc) < 1e-3) {
            double[] upv = V.v(0, -1, 0);
            zc = V.sub(upv, V.mul(d1, V.dot(upv, d1)));
        }
        zc = V.norm(zc);
        double[] zp = V.sub(d2, V.mul(d1, ca));
        double[] z;
        if (V.len(zp) > 1e-6) {
            double[] plane = V.mul(V.norm(zp), knee ? 1 : -1);
            double t = clamp((al - 0.15) / 0.30, 0, 1);
            double w = t * t * (3 - 2 * t);
            z = V.add(V.mul(plane, w), V.mul(zc, 1 - w));
            if (V.len(z) < 1e-3) {
                z = plane;
            }
        } else {
            z = zc;
        }
        z = V.norm(V.sub(z, V.mul(d1, V.dot(z, d1))));
        double[] x = V.cross(d1, z);
        r[at] = Math.atan2(d1[2], z[2]);
        r[at + 1] = Math.asin(clamp(-x[2], -1, 1));
        r[at + 2] = Math.atan2(x[1], x[0]);
        r[at + 3] = knee ? al : -al;
    }

    private static double[][] frame(Pose p, double[] U) {
        double[] L = horiz(V.sub(p.pos.get("legL"), p.pos.get("legR")), U);
        return new double[][]{L, V.cross(L, U)};
    }

    private static double[] horiz(double[] v, double[] U) {
        return V.norm(V.sub(v, V.mul(U, V.dot(v, U))));
    }

    /** Del sistema del personaje (izquierda, arriba, delante) al del modelo de Minecraft (x izq, y abajo, z atras). */
    private static double[] mc(double[] v, double[] L, double[] U, double[] F) {
        return V.v(V.dot(v, L), -V.dot(v, U), -V.dot(v, F));
    }

    private static double unwrap(double prev, double cur) {
        double d = cur - prev;
        while (d > Math.PI) {
            d -= 2 * Math.PI;
        }
        while (d < -Math.PI) {
            d += 2 * Math.PI;
        }
        return prev + d;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
