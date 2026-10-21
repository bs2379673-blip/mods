package com.tunombre.cosmeticos.geo.cos;

import com.tunombre.cosmeticos.geo.*;

/** flores de la Mortifilia, enredaderas y la flor de la cabeza */
public final class Flora {
    private Flora() { }

    private static final int PETAL = 0xFFFFFFFF;

    /** una flor completa: sepalos, dos coronas de petalos que se abren y se cierran, estambres con luz */
    static void flower(Ctx c, V3 p, V3 axis, float size, float open, float seed, int glowCol) {
        axis = axis.norm();
        V3 e1 = axis.cross(Math.abs(axis.y) < 0.9F ? V3.Y : V3.X).norm();
        V3 e2 = axis.cross(e1).norm();
        Mesh sm = c.sc.solid("petal_mort");
        int rings = 2;
        for (int ring = 0; ring < rings; ring++) {
            int cnt = ring == 0 ? 7 : 5;
            float len = (ring == 0 ? 0.23F : 0.15F) * size;
            float wid = (ring == 0 ? 0.14F : 0.10F) * size;
            for (int k = 0; k < cnt; k++) {
                float a = 6.2832F * (k + (ring == 1 ? 0.5F : 0F)) / cnt + seed * 3F;
                V3 rad = e1.mul((float) Math.cos(a)).add(e2.mul((float) Math.sin(a)));
                V3 tan = e1.mul((float) -Math.sin(a)).add(e2.mul((float) Math.cos(a)));
                float o = ring == 0 ? open : open * 0.75F + 0.1F;
                V3 dir = axis.mul(1F - o * (ring == 0 ? 0.85F : 0.6F)).add(rad.mul(o * (ring == 0 ? 1.1F : 0.7F))).norm();
                int variant = (k + ring * 2) & 3;
                float shade = ring == 0 ? 0.82F : 1F;
                G.blade(sm, p.add(axis.mul(0.005F * ring)), dir, tan, len, wid, ring == 0 ? 0.35F + 0.5F * (1 - open) : 0.55F, ring == 0 ? rad.neg() : axis.neg(), 4, G.FLAT,
                        new float[]{variant * 0.25F, 0F, variant * 0.25F + 0.25F, 1F}, Col.shade(PETAL, shade * 0.75F), Col.shade(PETAL, shade), 0.006F * size);
            }
        }
        // estambres
        for (int k = 0; k < 6; k++) {
            float a = k * 1.047F + seed;
            V3 d = axis.add(e1.mul((float) Math.cos(a) * 0.35F * (0.4F + open))).add(e2.mul((float) Math.sin(a) * 0.35F * (0.4F + open))).norm();
            V3 tip = p.add(d.mul(0.12F * size));
            G.tube(c.sc.solid("stem"), new V3[]{p, p.lerp(tip, 0.5F), tip}, new float[]{0.006F * size, 0.005F * size, 0.003F * size}, 4, 1F, 2F, 0F, G.fill(3, 0xFFFFFFFF));
            G.sprite(c.sc.glow("glow"), tip, c.camR, c.camU, c.camF, 0.06F * size, 0.06F * size, 0, G.FULL, Col.alpha(glowCol, 0.9F));
        }
        float pulse = 0.55F + 0.45F * (float) Math.sin(c.t * 0.06F + seed * 9F);
        G.sprite(c.sc.glow("glow"), p.add(axis.mul(0.04F * size)), c.camR, c.camU, c.camF, 0.3F * size, 0.3F * size, 0, G.FULL, Col.alpha(glowCol, 0.35F * pulse));
    }

    /** flores repartidas por el cuerpo, abriendose y cerrandose lentamente */
    public static Cosm bodyFlowers() {
        return new Cosm() {
            @Override public Bone bone() { return Bone.CHEST; }

            @Override
            public void build(Ctx c) {
                // posicion, eje (hacia donde mira), tamano
                float[][] spec = {
                        {0, -0.22F, -0.145F, 0, 0.2F, -1, 1.25F}, {0.2F, -0.38F, -0.145F, 0.4F, 0.1F, -1, 0.95F}, {-0.2F, -0.38F, -0.145F, -0.4F, 0.1F, -1, 0.95F},
                        {0, -0.58F, -0.145F, 0, -0.1F, -1, 1.0F}, {0.17F, -0.72F, -0.145F, 0.4F, -0.2F, -1, 0.8F}, {-0.17F, -0.72F, -0.145F, -0.4F, -0.2F, -1, 0.8F},
                        {0.28F, 0.0F, 0.0F, 1, 0.9F, 0.1F, 1.0F}, {-0.28F, 0.0F, 0.0F, -1, 0.9F, 0.1F, 1.0F},
                        {0.3F, -0.3F, 0.05F, 1, 0.1F, 0.4F, 0.75F}, {-0.3F, -0.3F, 0.05F, -1, 0.1F, 0.4F, 0.75F},
                        {0.12F, -0.2F, 0.14F, 0.2F, 0.1F, 1, 0.7F}, {-0.12F, -0.45F, 0.14F, -0.2F, 0, 1, 0.65F}};
                for (int i = 0; i < spec.length; i++) {
                    float[] s = spec[i];
                    float seed = Noise.hash(i, 31);
                    float open = 0.5F + 0.5F * (float) Math.sin(c.t * 0.03F + seed * 6.28F);
                    float sway = 0.08F * (float) Math.sin(c.t * 0.05F + seed * 9) + c.vf * 0.8F;
                    V3 axis = V3.of(s[3], s[4], s[5]).norm().add(sway * 0.6F, 0.04F * (float) Math.sin(c.t * 0.04F + i), 0).norm();
                    V3 pos = V3.of(s[0], s[1], s[2]);
                    // tallo corto desde el cuerpo
                    V3 top = pos.add(axis.mul(0.07F * s[6]));
                    G.tube(c.sc.solid("stem"), new V3[]{pos.sub(axis.mul(0.02F)), pos.lerp(top, 0.5F).add(0, 0.01F, 0), top}, new float[]{0.016F * s[6], 0.012F * s[6], 0.01F * s[6]}, 6, 1F, 3F, 0F, G.fill(3, 0xFFFFFFFF));
                    flower(c, top, axis, s[6], 0.35F + 0.65F * open, seed, 0xFFE070FF);
                }
                // esporas
                for (int k = 0; k < 10; k++) {
                    float life = (c.t * 0.008F + k * 0.1F) % 1F;
                    float ang = k * 2.4F + c.t * 0.01F;
                    V3 p = V3.of(Math.cos(ang) * (0.38 + 0.1 * life), -0.7 + life * 1.2, Math.sin(ang) * (0.3 + 0.1 * life));
                    float a = (float) Math.sin(Math.PI * life);
                    G.sprite(c.sc.glow("spark"), p, c.camR, c.camU, c.camF, 0.05F * a + 0.01F, 0.05F * a + 0.01F, 0, G.FULL, Col.alpha(0xFFE9A0FF, a * 0.8F));
                }
            }
        };
    }

    /** enredaderas que trepan en espiral por el torso, con hojas, florecitas y puntas colgando */
    public static Cosm vines() {
        return new Cosm() {
            @Override public Bone bone() { return Bone.CHEST; }

            @Override
            public void build(Ctx c) {
                int nv = 3;
                int steps = 30;
                for (int v = 0; v < nv; v++) {
                    float a0 = v * 6.2832F / nv + 0.4F;
                    float turns = 1.35F + 0.15F * v;
                    float grow = 0.84F + 0.16F * (float) Math.sin(c.t * 0.012F + v * 2);
                    int n = (int) (steps * grow);
                    V3[] pts = new V3[n + 1];
                    float[] rad = new float[n + 1];
                    for (int i = 0; i <= n; i++) {
                        float t = i / (float) steps;
                        float a = a0 + turns * 6.2832F * t + 0.06F * (float) Math.sin(c.t * 0.04F + i * 0.5F + v);
                        float y = -0.80F + 0.92F * t;
                        float rx = 0.33F + 0.015F * (float) Math.sin(i * 1.3F + v), rz = 0.21F + 0.015F * (float) Math.sin(i * 1.7F);
                        pts[i] = V3.of(Math.cos(a) * rx, y, Math.sin(a) * rz);
                        rad[i] = 0.02F * (1F - 0.65F * t);
                    }
                    G.tube(c.sc.solid("vine"), pts, rad, 6, 1F, 3F, 0F, G.fill(n + 1, 0xFFFFFFFF));
                    for (int i = 2; i < n; i += 3) {
                        V3 out = V3.of(pts[i].x, 0, pts[i].z).norm();
                        float flut = 0.2F * (float) Math.sin(c.t * 0.07F + i + v);
                        V3 dir = out.mul(0.8F).add(0, 0.55F + flut, 0).norm().add(0, 0, 0).norm();
                        V3 side = out.cross(V3.Y).norm();
                        G.blade(c.sc.solid("leaf"), pts[i], dir, side, 0.14F + 0.04F * Noise.hash(i, v), 0.085F, 0.35F + flut, V3.of(0, -1, 0), 3, G.FLAT, new float[]{(i % 2) * 0.5F, 0F, (i % 2) * 0.5F + 0.5F, 1F}, Col.shade(0xFFFFFFFF, 0.8F), 0xFFFFFFFF, 0.006F);
                        if (i % 9 == 2) flower(c, pts[i].add(out.mul(0.03F)), out.add(0, 0.2F, 0), 0.5F, 0.6F + 0.4F * (float) Math.sin(c.t * 0.04F + i), Noise.hash(i, v + 3), 0xFFE070FF);
                    }
                    // punta que cuelga y se balancea
                    V3 last = pts[n];
                    V3[] tip = new V3[6];
                    for (int k = 0; k < 6; k++) tip[k] = last.add(0.03F * k * (float) Math.sin(c.t * 0.09F + v), -0.04F * k, 0.02F * k * (float) Math.cos(c.t * 0.07F));
                    G.tube(c.sc.solid("vine"), tip, G.taper(6, rad[n], 0.004F, 1F), 5, 1F, 3F, 0F, G.fill(6, 0xFFFFFFFF));
                }
            }
        };
    }

    /** flor grande en un lado de la cabeza con hojas y un zarcillo que cuelga */
    public static Cosm headFlower() {
        return new Cosm() {
            @Override public Bone bone() { return Bone.HEAD; }

            @Override
            public void build(Ctx c) {
                V3 base = V3.of(0.2, 0.43, 0.02);
                float open = 0.55F + 0.45F * (float) Math.sin(c.t * 0.035F);
                V3 axis = V3.of(0.65F + 0.05F * (float) Math.sin(c.t * 0.05F), 0.7F, 0.15F).norm();
                V3 top = base.add(axis.mul(0.1F));
                G.tube(c.sc.solid("stem"), new V3[]{base.sub(axis.mul(0.03F)), base.lerp(top, 0.5F), top}, new float[]{0.03F, 0.025F, 0.02F}, 6, 1F, 3F, 0F, G.fill(3, 0xFFFFFFFF));
                flower(c, top, axis, 2.1F, 0.3F + 0.7F * open, 0.37F, 0xFFE070FF);
                // hojas
                for (int k = 0; k < 4; k++) {
                    float a = k * 1.9F + 0.5F;
                    V3 dir = V3.of(Math.cos(a) * 0.8F + 0.4F, 0.1F + 0.3F * Math.sin(a), Math.sin(a) * 0.7F).norm();
                    G.blade(c.sc.solid("leaf"), base, dir, dir.cross(V3.Y).norm(), 0.2F, 0.11F, 0.5F + 0.1F * (float) Math.sin(c.t * 0.06F + k), V3.of(0, -1, 0), 3, G.FLAT,
                            new float[]{(k % 2) * 0.5F, 0F, (k % 2) * 0.5F + 0.5F, 1F}, Col.shade(0xFFFFFFFF, 0.8F), 0xFFFFFFFF, 0.006F);
                }
                // zarcillo colgante con flor pequena
                V3[] pts = new V3[9];
                for (int k = 0; k < 9; k++) {
                    float f = k / 8F;
                    pts[k] = base.add(0.07F * f + 0.03F * (float) Math.sin(c.t * 0.07F + k * 0.6F) * f, -0.55F * f * f - 0.02F * f, 0.1F * f + 0.03F * (float) Math.cos(c.t * 0.06F + k) * f);
                }
                G.tube(c.sc.solid("vine"), pts, G.taper(9, 0.012F, 0.004F, 1F), 5, 1F, 3F, 0F, G.fill(9, 0xFFFFFFFF));
                flower(c, pts[8], V3.of(0.1, -0.7, 0.2), 0.55F, 0.65F, 0.81F, 0xFFE070FF);
            }
        };
    }
}
