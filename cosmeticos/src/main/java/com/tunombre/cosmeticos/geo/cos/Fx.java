package com.tunombre.cosmeticos.geo.cos;

import com.tunombre.cosmeticos.geo.*;

/** auras y efectos alrededor del cuerpo (origen en la cadera: pies en y = -0.75, cabeza hasta y = 1.25) */
public final class Fx {
    private Fx() { }

    private abstract static class Aura implements Cosm {
        @Override public Bone bone() { return Bone.ROOT; }
    }

    private static float h(int a, int b) { return Noise.hash(a, b); }

    private static V3 wind(Ctx c, float k) { return V3.of(-c.vl * k, -c.vy * k * 0.5F, -c.vf * k); }

    // ---------- fuego ----------
    public static Cosm fire() {
        return new Aura() {
            @Override
            public void build(Ctx c) {
                Mesh g = c.sc.glow("flame_sheet"), sm = c.sc.glow("spark");
                int n = 30;
                for (int k = 0; k < n; k++) {
                    float life = (c.t * (0.020F + 0.01F * h(k, 1)) + h(k, 2)) % 1F;
                    float a = h(k, 3) * 6.2832F + life * 1.6F * (h(k, 4) - 0.5F) * 2;
                    float r = (0.26F + 0.16F * h(k, 5)) * (1F - 0.45F * life);
                    float y = -0.78F + life * (1.55F + 0.5F * h(k, 6));
                    V3 p = V3.of(Math.cos(a) * r, y, Math.sin(a) * r).add(wind(c, 3.5F * life));
                    float s = (0.2F + 0.28F * h(k, 7)) * (float) Math.pow(Math.sin(Math.PI * Math.min(1, life * 1.05F)), 0.6F);
                    int col = Col.mix(0xFFFFFFA0, 0xFFFF2A00, life);
                    float flick = 0.8F + 0.2F * (float) Math.sin(c.t * 0.7F + k);
                    G.sprite(g, p, c.camR, c.camU, c.camF, s * 0.8F, s * 1.25F * flick, (h(k, 8) - 0.5F) * 0.5F, G.frame(4, (int) (c.t * 0.45F) + k), Col.alpha(col, 0.55F * (1F - life * 0.5F)));
                }
                for (int k = 0; k < 18; k++) {
                    float life = (c.t * 0.03F + h(k, 11) * 3F) % 1F;
                    float a = h(k, 12) * 6.2832F;
                    V3 p = V3.of(Math.cos(a) * (0.3 + 0.25 * life), -0.7 + life * 2.2F, Math.sin(a) * (0.3 + 0.25 * life)).add(0.06F * (float) Math.sin(c.t * 0.15F + k * 3), 0, 0).add(wind(c, 5F * life));
                    float al = (float) Math.sin(Math.PI * life);
                    G.sprite(sm, p, c.camR, c.camU, c.camF, 0.04F + 0.03F * h(k, 13), 0.04F + 0.03F * h(k, 13), 0, G.FULL, Col.alpha(0xFFFFB040, al));
                }
                G.sprite(c.sc.glow("glow"), V3.of(0, -0.2, 0), c.camR, c.camU, c.camF, 1.5F, 2.2F, 0, G.FULL, Col.alpha(0xFFFF5010, 0.16F + 0.04F * (float) Math.sin(c.t * 0.3F)));
            }
        };
    }

    // ---------- oscura ----------
    public static Cosm dark() {
        return new Aura() {
            @Override
            public void build(Ctx c) {
                Mesh sm = c.sc.solid("smoke_sheet");
                for (int k = 0; k < 16; k++) {
                    float life = (c.t * (0.008F + 0.006F * h(k, 1)) + h(k, 2)) % 1F;
                    float a = h(k, 3) * 6.2832F + life * 2.5F;
                    float r = 0.3F + 0.25F * life;
                    V3 p = V3.of(Math.cos(a) * r, -0.75F + life * 1.9F, Math.sin(a) * r).add(wind(c, 4F * life));
                    float s = 0.35F + 0.45F * life * (0.6F + 0.4F * h(k, 5));
                    float al = (float) Math.sin(Math.PI * life) * 0.55F;
                    G.sprite(sm, p, c.camR, c.camU, c.camF, s, s, h(k, 6) * 6F + c.t * 0.01F, G.frame(4, k), Col.alpha(0xFF1A1030, al));
                }
                // cintas de sombra que giran alrededor
                for (int j = 0; j < 3; j++) {
                    int n = 16;
                    V3[] pts = new V3[n];
                    float[] w = new float[n];
                    int[] cols = new int[n];
                    for (int i = 0; i < n; i++) {
                        float f = i / (n - 1F);
                        float a = c.t * 0.03F * (1 + 0.3F * j) + j * 2.1F - f * 2.4F;
                        float y = -0.7F + 1.5F * (0.5F + 0.5F * (float) Math.sin(c.t * 0.02F + j * 2 - f * 3F));
                        float r = 0.5F + 0.08F * (float) Math.sin(f * 6 + c.t * 0.05F);
                        pts[i] = V3.of(Math.cos(a) * r, y, Math.sin(a) * r).add(wind(c, 3F * f));
                        w[i] = 0.2F * (float) Math.sin(Math.PI * f);
                        cols[i] = Col.alpha(0xFF2A1450, 0.55F * (float) Math.sin(Math.PI * f));
                    }
                    G.ribbon(c.sc.solid("wisp"), pts, w, c.camF, G.FULL, cols);
                }
                for (int k = 0; k < 5; k++) {
                    float life = (c.t * 0.012F + k * 0.2F) % 1F;
                    float a = k * 1.9F;
                    G.sprite(c.sc.glow("glow"), V3.of(Math.cos(a) * 0.35, -0.75F + life * 0.4F, Math.sin(a) * 0.35), c.camR, c.camU, c.camF, 0.2F, 0.2F, 0, G.FULL, Col.alpha(0xFF8A40FF, (float) Math.sin(Math.PI * life) * 0.5F));
                }
            }
        };
    }

    // ---------- orbes ----------
    public static Cosm orbs() {
        return new Aura() {
            @Override
            public void build(Ctx c) {
                int n = 5;
                for (int k = 0; k < n; k++) {
                    float spd = 0.045F + 0.012F * k;
                    float tilt = 0.5F + k * 0.55F;
                    V3 u1 = V3.X.rot(V3.Z, tilt * 0.6F), u2 = V3.Z.rot(V3.X, tilt * 0.5F);
                    float R = 0.62F + 0.06F * k;
                    int tn = 14;
                    V3[] pts = new V3[tn];
                    float[] w = new float[tn];
                    int[] cols = new int[tn];
                    int base = Col.hsv(0.52F + 0.05F * k, 0.55F, 1F, 1F);
                    for (int i = 0; i < tn; i++) {
                        float f = i / (tn - 1F);
                        float a = (c.t - f * 9F) * spd + k * 1.256F;
                        pts[i] = V3.of(0, 0.15F + 0.25F * (float) Math.sin(k), 0).add(u1.mul((float) Math.cos(a) * R)).add(u2.mul((float) Math.sin(a) * R));
                        w[i] = 0.07F * (1F - f);
                        cols[i] = Col.alpha(base, 0.75F * (1F - f));
                    }
                    G.ribbon(c.sc.glow("trail"), reverse(pts), reverse(w), c.camF, G.FULL, reverseI(cols));
                    V3 p = pts[0];
                    G.sprite(c.sc.solid("orb"), p, c.camR, c.camU, c.camF, 0.12F, 0.12F, 0, G.FULL, Col.WHITE);
                    G.sprite(c.sc.glow("glow"), p, c.camR, c.camU, c.camF, 0.4F, 0.4F, 0, G.FULL, Col.alpha(base, 0.7F));
                }
            }
        };
    }

    private static V3[] reverse(V3[] a) { V3[] r = new V3[a.length]; for (int i = 0; i < a.length; i++) r[i] = a[a.length - 1 - i]; return r; }
    private static float[] reverse(float[] a) { float[] r = new float[a.length]; for (int i = 0; i < a.length; i++) r[i] = a[a.length - 1 - i]; return r; }
    private static int[] reverseI(int[] a) { int[] r = new int[a.length]; for (int i = 0; i < a.length; i++) r[i] = a[a.length - 1 - i]; return r; }

    // ---------- anillos arcanos ----------
    public static Cosm rings() {
        return new Aura() {
            @Override
            public void build(Ctx c) {
                Mesh g = c.sc.glow("rune_ring");
                float[][] spec = {{-0.72F, 0.95F, 0.018F}, {0.05F, 0.72F, -0.026F}, {0.75F, 0.55F, 0.034F}};
                for (int i = 0; i < spec.length; i++) {
                    float phi = c.t * spec[i][2];
                    V3 ax = V3.of(Math.cos(phi), 0, Math.sin(phi)).mul(spec[i][1]), ay = V3.of(-Math.sin(phi), 0, Math.cos(phi)).mul(spec[i][1]);
                    float y = spec[i][0] + 0.03F * (float) Math.sin(c.t * 0.05F + i);
                    G.plane(g, V3.of(0, y, 0), ax, ay, G.FULL, Col.alpha(0xFFB080FF, 0.85F));
                    G.plane(g, V3.of(0, y, 0), ay, ax, G.FULL, Col.alpha(0xFFB080FF, 0.0F));
                }
                // sigilo vertical detras de la espalda
                float ph = -c.t * 0.02F;
                V3 ax = V3.of(Math.cos(ph), Math.sin(ph), 0).mul(0.85F), ay = V3.of(-Math.sin(ph), Math.cos(ph), 0).mul(0.85F);
                G.plane(c.sc.glow("rune_ring2"), V3.of(0, 0.25F, -0.5F), ax, ay, G.FULL, Col.alpha(0xFFC8A0FF, 0.9F));
                for (int k = 0; k < 8; k++) {
                    float life = (c.t * 0.01F + k * 0.125F) % 1F;
                    float a = k * 0.785F + c.t * 0.02F;
                    G.sprite(c.sc.glow("spark"), V3.of(Math.cos(a) * 0.8F, -0.75F + life * 1.8F, Math.sin(a) * 0.8F), c.camR, c.camU, c.camF, 0.06F, 0.06F, 0, G.FULL, Col.alpha(0xFFC8A0FF, (float) Math.sin(Math.PI * life)));
                }
            }
        };
    }

    // ---------- ojos ----------
    public static Cosm eyes() {
        return new Aura() {
            @Override
            public void build(Ctx c) {
                int n = 5;
                for (int k = 0; k < n; k++) {
                    float a = c.t * 0.025F + k * 6.2832F / n;
                    float R = 0.7F + 0.08F * (float) Math.sin(c.t * 0.04F + k);
                    float y = 0.25F + 0.55F * (float) Math.sin(c.t * 0.03F + k * 1.7F);
                    V3 p = V3.of(Math.cos(a) * R, y, Math.sin(a) * R);
                    float blink = (c.t * 0.01F + k * 0.37F) % 1F;
                    float lid = blink > 0.94F ? 0.25F : 1F;
                    V3 ez = c.camF.neg();
                    G.sphere(c.sc.solid("eye"), p, c.camR, c.camU.mul(lid), ez, 0.075F, 8, 14, Col.WHITE);
                    G.sprite(c.sc.glow("glow"), p, c.camR, c.camU, c.camF, 0.3F, 0.3F, 0, G.FULL, Col.alpha(0xFFFF2020, 0.45F));
                    // venitas rojas que cuelgan
                    V3[] t = new V3[5];
                    for (int i = 0; i < 5; i++) t[i] = p.add(0.02F * (float) Math.sin(c.t * 0.1F + i + k), -0.03F * i, 0);
                    G.ribbon(c.sc.glow("trail"), t, new float[]{0.03F, 0.025F, 0.02F, 0.012F, 0.004F}, c.camF, G.FULL, G.ramp(5, Col.alpha(0xFFFF3030, 0.7F), Col.alpha(0xFFFF3030, 0F)));
                }
            }
        };
    }

    // ---------- estrellas ----------
    public static Cosm stars() {
        return new Aura() {
            @Override
            public void build(Ctx c) {
                for (int k = 0; k < 18; k++) {
                    float a = c.t * (0.012F + 0.01F * h(k, 1)) * (k % 2 == 0 ? 1 : -1) + h(k, 2) * 6.2832F;
                    float R = 0.5F + 0.45F * h(k, 3);
                    float y = -0.75F + 2.0F * h(k, 4) + 0.08F * (float) Math.sin(c.t * 0.05F + k);
                    V3 p = V3.of(Math.cos(a) * R, y, Math.sin(a) * R);
                    float tw = 0.5F + 0.5F * (float) Math.sin(c.t * (0.1F + 0.1F * h(k, 5)) + k * 3F);
                    float s = (0.08F + 0.16F * h(k, 6)) * (0.35F + 0.65F * tw);
                    int col = Col.mix(0xFFFFF4B0, 0xFFFFFFFF, h(k, 7));
                    G.sprite(c.sc.glow("star"), p, c.camR, c.camU, c.camF, s, s, c.t * 0.02F * (h(k, 8) - 0.5F) * 3, G.FULL, Col.alpha(col, 0.35F + 0.65F * tw));
                    G.sprite(c.sc.glow("glow"), p, c.camR, c.camU, c.camF, s * 1.6F, s * 1.6F, 0, G.FULL, Col.alpha(col, 0.18F * tw));
                }
            }
        };
    }

    // ---------- rayos ----------
    public static Cosm lightning() {
        return new Aura() {
            @Override
            public void build(Ctx c) {
                int step = (int) (c.t / 2.5F);
                for (int b = 0; b < 4; b++) {
                    int seed = step * 31 + b * 7 + (int) (c.seed * 1000);
                    if (h(seed, 1) < 0.35F) continue;
                    float a0 = h(seed, 2) * 6.2832F, y0 = -0.5F + 1.4F * h(seed, 3);
                    V3 from = V3.of(Math.cos(a0) * 0.28, y0, Math.sin(a0) * 0.28);
                    float a1 = a0 + (h(seed, 4) - 0.5F) * 1.2F, y1 = y0 + (h(seed, 5) - 0.5F) * 1.2F, R = 0.9F + 0.5F * h(seed, 6);
                    V3 to = V3.of(Math.cos(a1) * R, y1, Math.sin(a1) * R);
                    bolt(c, from, to, seed, 0.07F);
                    if (h(seed, 8) > 0.5F) {
                        V3 mid = from.lerp(to, 0.5F);
                        V3 to2 = mid.add((h(seed, 9) - 0.5F) * 0.8F, (h(seed, 10) - 0.5F) * 0.8F, (h(seed, 11) - 0.5F) * 0.8F);
                        bolt(c, mid, to2, seed + 5, 0.045F);
                    }
                    G.sprite(c.sc.glow("glow"), to, c.camR, c.camU, c.camF, 0.3F, 0.3F, 0, G.FULL, Col.alpha(0xFF80D8FF, 0.8F));
                }
                G.sprite(c.sc.glow("glow"), V3.of(0, 0.2F, 0), c.camR, c.camU, c.camF, 1.3F, 1.8F, 0, G.FULL, Col.alpha(0xFF60C0FF, 0.07F + 0.06F * (h(step, 1))));
            }

            private void bolt(Ctx c, V3 from, V3 to, int seed, float width) {
                int n = 10;
                V3[] pts = new V3[n];
                float[] w = new float[n];
                int[] cols = new int[n];
                V3 d = to.sub(from);
                for (int i = 0; i < n; i++) {
                    float f = i / (n - 1F);
                    float j = (i == 0 || i == n - 1) ? 0 : 1;
                    pts[i] = from.add(d.mul(f)).add(c.camR.mul((h(seed, 20 + i) - 0.5F) * 0.22F * j)).add(c.camU.mul((h(seed, 40 + i) - 0.5F) * 0.22F * j));
                    w[i] = width * (1F - 0.6F * f);
                    cols[i] = Col.alpha(0xFFC8F0FF, 1F);
                }
                G.ribbon(c.sc.glow("bolt"), pts, w, c.camF, G.FULL, cols);
                float[] w2 = new float[n];
                for (int i = 0; i < n; i++) w2[i] = w[i] * 2.8F;
                G.ribbon(c.sc.glow("trail"), pts, w2, c.camF, G.FULL, G.fill(n, Col.alpha(0xFF50B0FF, 0.45F)));
            }
        };
    }

    // ---------- mariposas ----------
    public static Cosm butterflies() {
        return new Aura() {
            @Override
            public void build(Ctx c) {
                int n = 6;
                for (int k = 0; k < n; k++) {
                    float f1 = 0.011F + 0.005F * h(k, 1), f2 = 0.017F + 0.006F * h(k, 2);
                    float ph = h(k, 3) * 6.2832F;
                    V3 p = V3.of(Math.cos(c.t * f1 + ph) * 0.75F, 0.2F + 0.65F * (float) Math.sin(c.t * f2 + ph * 2), Math.sin(c.t * f1 * 1.3F + ph) * 0.75F);
                    V3 p2 = V3.of(Math.cos((c.t + 2) * f1 + ph) * 0.75F, 0.2F + 0.65F * (float) Math.sin((c.t + 2) * f2 + ph * 2), Math.sin((c.t + 2) * f1 * 1.3F + ph) * 0.75F);
                    V3 tan = p2.sub(p).norm();
                    V3 side = tan.cross(V3.Y);
                    side = side.len() < 1e-4F ? V3.X : side.norm();
                    V3 up = side.cross(tan).norm();
                    float flap = (float) Math.sin(c.t * 0.9F + k * 2F) * 0.95F + 0.1F;
                    float size = 0.11F;
                    Mesh g = c.sc.glow("bfly_small");
                    for (int sd = -1; sd <= 1; sd += 2) {
                        V3 wo = side.mul(sd * (float) Math.cos(flap)).add(up.mul((float) Math.sin(flap))).norm();
                        V3 a = p, b = p.add(wo.mul(size * 2F)), cc = b.add(tan.mul(size * 1.6F)), d = p.add(tan.mul(size * 1.6F));
                        V3 nr = wo.cross(tan).norm();
                        float u0 = sd > 0 ? 0.5F : 0.5F, u1 = sd > 0 ? 1F : 0F;
                        int col = Col.alpha(Col.hsv(0.45F + 0.08F * h(k, 5), 0.5F, 1F, 1F), 0.95F);
                        g.vert(a, u0, 1F, nr, col); g.vert(b, u1, 1F, nr, col); g.vert(cc, u1, 0F, nr, col); g.vert(d, u0, 0F, nr, col);
                    }
                    G.sprite(c.sc.glow("glow"), p, c.camR, c.camU, c.camF, 0.28F, 0.28F, 0, G.FULL, Col.alpha(0xFF80FFE0, 0.35F));
                    // estela de polvo
                    for (int i = 1; i < 4; i++) {
                        float tt = c.t - i * 3F;
                        V3 q = V3.of(Math.cos(tt * f1 + ph) * 0.75F, 0.2F + 0.65F * (float) Math.sin(tt * f2 + ph * 2), Math.sin(tt * f1 * 1.3F + ph) * 0.75F);
                        G.sprite(c.sc.glow("spark"), q, c.camR, c.camU, c.camF, 0.05F, 0.05F, 0, G.FULL, Col.alpha(0xFF90FFE8, 0.6F - 0.15F * i));
                    }
                }
            }
        };
    }

    // ---------- luciernagas ----------
    public static Cosm fireflies() {
        return new Aura() {
            @Override
            public void build(Ctx c) {
                for (int k = 0; k < 22; k++) {
                    float t = c.t * 0.012F;
                    V3 p = V3.of(Noise.n1(t * (1F + h(k, 1)) + k * 10F, 11) * 0.95F, -0.2F + Noise.n1(t * (0.8F + h(k, 2)) + k * 7F, 12) * 1.0F, Noise.n1(t * (1F + h(k, 3)) + k * 13F, 13) * 0.95F);
                    float pulse = 0.5F + 0.5F * (float) Math.sin(c.t * (0.06F + 0.06F * h(k, 4)) + k * 5F);
                    pulse = pulse * pulse;
                    float s = 0.05F + 0.07F * h(k, 5);
                    G.sprite(c.sc.glow("glow"), p, c.camR, c.camU, c.camF, s * 4F, s * 4F, 0, G.FULL, Col.alpha(0xFFC8FF50, 0.45F * pulse));
                    G.sprite(c.sc.glow("spark"), p, c.camR, c.camU, c.camF, s, s, 0, G.FULL, Col.alpha(0xFFF6FFB0, 0.4F + 0.6F * pulse));
                }
            }
        };
    }
}
