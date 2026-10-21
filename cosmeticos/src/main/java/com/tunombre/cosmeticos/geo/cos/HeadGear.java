package com.tunombre.cosmeticos.geo.cos;

import com.tunombre.cosmeticos.geo.*;

/** aureola, corona de espinas y cuernos */
public final class HeadGear {
    private HeadGear() { }

    public static Cosm halo() {
        return new Cosm() {
            @Override public Bone bone() { return Bone.HEAD; }

            @Override
            public void build(Ctx c) {
                float bob = 0.03F * (float) Math.sin(c.t * 0.07F);
                V3 ctr = V3.of(0.0, 0.82 + bob, -0.02);
                float tx = 0.06F * (float) Math.sin(c.t * 0.05F) - c.vf * 0.8F, tz = 0.06F * (float) Math.cos(c.t * 0.043F) + c.vl * 0.8F;
                V3 ax = V3.X.rot(V3.Z, tz).rot(V3.X, tx), ay = V3.Z.rot(V3.Z, tz).rot(V3.X, tx);
                G.torus(c.sc.solid("halo"), ctr, ax, ay, 0.31F, 0.026F, 40, 8, 6F, 0xFFFFFFFF);
                G.torus(c.sc.solid("halo"), ctr, ax, ay, 0.31F, 0.026F, 40, 8, 6F, 0xFFFFFFFF);
                float pulse = 0.75F + 0.25F * (float) Math.sin(c.t * 0.1F);
                G.torus(c.sc.glow("halo_g"), ctr, ax, ay, 0.31F, 0.034F, 40, 8, 6F, Col.alpha(0xFFFFE08A, 0.55F * pulse));
                G.torus(c.sc.glow("halo_g"), ctr, ax, ay, 0.31F, 0.055F, 40, 8, 3F, Col.alpha(0xFFFFE08A, 0.18F * pulse));
                G.sprite(c.sc.glow("glow"), ctr, c.camR, c.camU, c.camF, 0.8F, 0.8F, 0, G.FULL, Col.alpha(0xFFFFE08A, 0.18F * pulse));
                for (int k = 0; k < 6; k++) {
                    float a = c.t * 0.035F + k * 1.047F;
                    V3 p = ctr.add(ax.mul((float) Math.cos(a) * 0.31F)).add(ay.mul((float) Math.sin(a) * 0.31F));
                    float tw = 0.5F + 0.5F * (float) Math.sin(c.t * 0.2F + k * 2F);
                    G.sprite(c.sc.glow("star"), p, c.camR, c.camU, c.camF, 0.09F * tw + 0.03F, 0.09F * tw + 0.03F, c.t * 0.03F, G.FULL, Col.alpha(0xFFFFF2B0, tw));
                }
            }
        };
    }

    public static Cosm thorns() {
        return new Cosm() {
            @Override public Bone bone() { return Bone.HEAD; }

            @Override
            public void build(Ctx c) {
                int seg = 44;
                float baseY = 0.5F;
                for (int strand = 0; strand < 3; strand++) {
                    V3[] pts = new V3[seg + 1];
                    for (int i = 0; i <= seg; i++) {
                        float a = 6.2832F * i / seg;
                        float tw = a * 5 + strand * 2.094F;
                        float r = 0.29F + 0.018F * (float) Math.cos(tw);
                        pts[i] = V3.of(Math.cos(a) * r, baseY + 0.02F * Math.sin(tw) + 0.01F * Math.sin(a * 2), Math.sin(a) * r);
                    }
                    G.tube(c.sc.solid("thorn"), pts, G.ffill(seg + 1, 0.016F), 6, 1F, 5F, 0F, G.fill(seg + 1, 0xFFFFFFFF));
                }
                for (int k = 0; k < 20; k++) {
                    float a = 6.2832F * k / 20 + Noise.hash(k, 4) * 0.15F;
                    float r = 0.3F;
                    V3 b = V3.of(Math.cos(a) * r, baseY + 0.02F * (Noise.hash(k, 5) - 0.5F), Math.sin(a) * r);
                    V3 out = V3.of(Math.cos(a), 0, Math.sin(a));
                    V3 dir = out.mul(0.6F).add(0, 0.8F * (Noise.hash(k, 6) - 0.2F) + 0.3F, 0).norm();
                    float len = 0.07F + 0.05F * Noise.hash(k, 7);
                    G.tube(c.sc.solid("thorn"), new V3[]{b, b.add(dir.mul(len * 0.5F)), b.add(dir.mul(len))}, new float[]{0.012F, 0.007F, 0.0015F}, 5, 1F, 6F, 0.5F, G.fill(3, 0xFFFFFFFF));
                }
                // gotas de sangre brillantes que caen
                for (int k = 0; k < 3; k++) {
                    float life = (c.t * 0.014F + k * 0.33F) % 1F;
                    float a = 1.0F + k * 2.1F;
                    V3 p = V3.of(Math.cos(a) * 0.3, baseY - life * life * 0.5F, Math.sin(a) * 0.3);
                    float al = 1F - life * life;
                    G.sprite(c.sc.glow("glow"), p, c.camR, c.camU, c.camF, 0.05F, 0.07F, 0, G.FULL, Col.alpha(0xFFFF2020, al));
                }
            }
        };
    }

    public static Cosm horns() {
        return new Cosm() {
            @Override public Bone bone() { return Bone.HEAD; }

            @Override
            public void build(Ctx c) {
                for (int side = -1; side <= 1; side += 2) {
                    horn(c, side, V3.of(0.16, 0.5, 0.06), V3.of(0.32, 0.85, -0.25), 0.62F, 0.052F, 0.30F, 0.07F);
                    horn(c, side, V3.of(0.27, 0.34, 0.08), V3.of(0.9, 0.2, -0.3), 0.22F, 0.03F, 0.28F, 0.0F);
                }
            }

            private void horn(Ctx c, int side, V3 root, V3 dir, float len, float r0, float curlP, float curlY) {
                int n = 14;
                Frame f = new Frame(V3.of(root.x * side, root.y, root.z), V3.of(dir.x * side, dir.y, dir.z), V3.Y);
                V3[] pts = new V3[n + 1];
                pts[0] = f.p;
                for (int i = 0; i < n; i++) {
                    float k = (i + 1F) / n;
                    f.turn(0F, curlP * 0.33F * (0.4F + k)).step(len / n);
                    pts[i + 1] = f.p;
                }
                float[] rad = G.taper(n + 1, r0, 0.004F, 0.75F);
                int[] cols = G.ramp(n + 1, Col.shade(0xFFFFFFFF, 0.7F), 0xFFFFFFFF);
                G.tube(c.sc.solid("horn"), pts, rad, 8, 1F, 3F, 0F, cols);
                float pulse = 0.5F + 0.5F * (float) Math.sin(c.t * 0.06F + side);
                G.tube(c.sc.glow("horn_g"), pts, scale(rad, 1.03F), 8, 1F, 3F, 0F, G.ramp(n + 1, Col.alpha(0xFFFF5A20, 0.0F), Col.alpha(0xFFFF7A30, 0.9F * pulse)));
                G.sprite(c.sc.glow("glow"), pts[n], c.camR, c.camU, c.camF, 0.12F, 0.12F, 0, G.FULL, Col.alpha(0xFFFF6A20, 0.5F * pulse));
            }

            private float[] scale(float[] r, float k) { float[] o = new float[r.length]; for (int i = 0; i < r.length; i++) o[i] = r[i] * k; return o; }
        };
    }
}
