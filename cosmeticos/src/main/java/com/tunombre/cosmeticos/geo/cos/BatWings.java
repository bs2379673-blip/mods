package com.tunombre.cosmeticos.geo.cos;

import com.tunombre.cosmeticos.geo.*;

/** alas membranosas: brazo + dedos con hueso y membrana tensa entre ellos (demonio / Mortifilia) */
public final class BatWings implements Cosm {
    private final String id, memTex, memGlow, boneTex;
    private final float scale;
    private final int fingers;
    private final int glowCol, tint;
    private final boolean flora;

    public BatWings(String id, String memTex, String memGlow, String boneTex, float scale, int fingers, int tint, int glowCol, boolean flora) {
        this.id = id; this.memTex = memTex; this.memGlow = memGlow; this.boneTex = boneTex; this.scale = scale; this.fingers = fingers; this.tint = tint; this.glowCol = glowCol; this.flora = flora;
    }

    @Override public Bone bone() { return Bone.CHEST; }

    @Override
    public void build(Ctx c) {
        Motion m = c.state(id, Motion::new);
        m.update(c, 0.12F, 0.34F);
        for (int side = -1; side <= 1; side += 2) wing(c, m, side);
    }

    private void wing(Ctx c, Motion m, int side) {
        float u = m.open;
        V3 S = V3.of(side * 0.12, -0.2, -0.14);
        // articulaciones (plegado -> abierto)
        V3 E = S.add(side * lerp(0.07F, 0.30F * scale, u), lerp(0.52F, 0.46F * scale, u), lerp(-0.14F, -0.20F, u));
        V3 W = E.add(side * lerp(0.03F, 0.60F * scale, u), lerp(0.20F, 0.26F * scale, u), lerp(-0.22F, -0.10F, u));
        float flapScale = 0.25F + 0.75F * u;
        float armAng = m.flap(side, 0F, 1.2F) * flapScale;
        E = S.add(E.sub(S).rot(V3.Z, armAng * 0.5F));
        W = S.add(W.sub(S).rot(V3.Z, armAng));
        int nf = fingers;
        V3[][] tipPts = new V3[nf][];
        int segs = 7;
        V3 body = V3.of(side * 0.07, -0.68, -0.12);
        for (int i = 0; i < nf; i++) {
            float f = i / (float) (nf - 1);
            // abierto: el dedo 0 sigue hacia fuera y arriba, el ultimo cae casi vertical hacia atras; plegado: todos cuelgan pegados al brazo
            V3 dOpen = V3.of(side * lerp(0.92F, 0.28F, f * f), lerp(0.30F, -0.88F, f), lerp(-0.28F, -0.34F, f)).norm();
            V3 dFold = V3.of(side * (0.05F + 0.05F * i), -0.93F, -0.30F - 0.08F * i).norm();
            V3 d = dFold.lerp(dOpen, u).norm();
            float L = scale * (1.12F - 0.22F * f + (i == 0 ? 0.0F : 0F)) * (0.72F + 0.28F * u);
            float delay = 1.2F + f * 0.5F;
            V3[] pts = new V3[segs + 1];
            for (int k = 0; k <= segs; k++) {
                float r = k / (float) segs;
                float ang = m.flap(side, 0.3F + r * 0.7F, delay) * flapScale;
                V3 pos = W.add(d.mul(L * r)).add(0, -0.10F * (float) Math.sin(Math.PI * r) * u - 0.06F * r * r, -0.12F * (float) Math.sin(Math.PI * r) * u);
                // la punta se retrasa respecto al brazo
                pos = W.add(pos.sub(W).rot(V3.Z, (ang - armAng) * 0.55F));
                pts[k] = pos;
            }
            tipPts[i] = pts;
        }
        // membranas entre dedos consecutivos y hasta el cuerpo
        for (int p = 0; p <= nf - 1; p++) {
            V3[] A = tipPts[p];
            V3[] B = p < nf - 1 ? tipPts[p + 1] : null;
            int rows = segs, cols = 4;
            V3[][] grid = new V3[rows + 1][cols + 1];
            for (int r = 0; r <= rows; r++) {
                float rr = r / (float) rows;
                V3 a = A[r];
                V3 b = B != null ? B[r] : W.lerp(body, rr);
                for (int cc = 0; cc <= cols; cc++) {
                    float cf = cc / (float) cols;
                    V3 pos = a.lerp(b, cf);
                    float sag = (B != null ? 0.30F : 0.18F) * (float) Math.sin(Math.PI * cf) * rr * rr;
                    pos = pos.add(W.sub(pos).mul(sag));
                    pos = pos.add(0, 0, -0.025F * (float) Math.sin(Math.PI * cf) * rr);
                    grid[r][cc] = pos;
                }
            }
            Mesh sm = c.sc.solid(memTex), gm = c.sc.glow(memGlow);
            float bright = p == nf - 1 ? 0.8F : 0.92F;
            for (int r = 0; r < rows; r++) {
                for (int cc = 0; cc < cols; cc++) {
                    V3 q0 = grid[r][cc], q1 = grid[r][cc + 1], q2 = grid[r + 1][cc + 1], q3 = grid[r + 1][cc];
                    V3 nr = q1.sub(q0).cross(q3.sub(q0)).norm();
                    float u0 = cc / (float) cols, u1 = (cc + 1) / (float) cols, v0 = 1F - r / (float) rows, v1 = 1F - (r + 1) / (float) rows;
                    int col = Col.shade(tint, bright * (0.85F + 0.15F * (float) Math.sin(r * 0.9 + p)));
                    sm.vert(q0, u0, v0, nr, col); sm.vert(q1, u1, v0, nr, col); sm.vert(q2, u1, v1, nr, col); sm.vert(q3, u0, v1, nr, col);
                    float pulse = 0.55F + 0.45F * (float) Math.sin(c.t * 0.07F + p * 1.3F - r * 0.5F);
                    int gc = Col.alpha(glowCol, pulse);
                    gm.vert(q0, u0, v0, nr, gc); gm.vert(q1, u1, v0, nr, gc); gm.vert(q2, u1, v1, nr, gc); gm.vert(q3, u0, v1, nr, gc);
                }
            }
        }
        // huesos
        Mesh bm = c.sc.solid(boneTex);
        V3[] arm = {S, S.lerp(E, 0.5F).add(0, 0.02F, -0.02F), E, E.lerp(W, 0.5F).add(0, 0.02F, -0.03F), W};
        G.tube(bm, arm, new float[]{0.045F, 0.04F, 0.035F, 0.03F, 0.026F}, 6, 1F, 2.5F, 0F, G.fill(5, tint));
        for (int i = 0; i < nf; i++) {
            float r0 = 0.024F - 0.002F * i;
            G.tube(bm, tipPts[i], G.taper(segs + 1, r0, 0.004F, 0.9F), 5, 1F, 3F, 0F, G.fill(segs + 1, Col.shade(tint, 0.85F)));
        }
        // garra del pulgar y brillo en las articulaciones
        V3 clawDir = V3.of(side * 0.5, 0.75, 0.25).norm();
        G.tube(bm, new V3[]{W.add(0, 0.02F, 0), W.add(clawDir.mul(0.07F)), W.add(clawDir.mul(0.15F)).add(0, 0, 0.03F)}, new float[]{0.03F, 0.02F, 0.003F}, 5, 1F, 3F, 0F, G.fill(3, tint));
        Mesh g = c.sc.glow("glow");
        G.sprite(g, W, c.camR, c.camU, c.camF, 0.2F, 0.2F, 0, G.FULL, Col.alpha(glowCol, 0.6F));
        for (int i = 0; i < nf; i++) G.sprite(g, tipPts[i][segs], c.camR, c.camU, c.camF, 0.13F, 0.13F, 0, G.FULL, Col.alpha(glowCol, 0.75F));
        if (flora) buds(c, E, W, side, u);
    }

    /** capullos que se abren en las articulaciones (Mortifilia) y esporas que caen */
    private void buds(Ctx c, V3 E, V3 W, int side, float u) {
        V3[] at = {E, W};
        for (int j = 0; j < 2; j++) {
            float open = 0.45F + 0.4F * (float) Math.sin(c.t * 0.05F + j * 2 + side);
            for (int k = 0; k < 5; k++) {
                float a = k * 1.2566F + c.t * 0.004F;
                V3 d = V3.of(Math.cos(a) * 0.5F * open, 0.8F, Math.sin(a) * 0.5F * open - 0.1F).norm();
                G.blade(c.sc.solid("petal_mort"), at[j].add(0, 0.02F, 0), d, V3.of(Math.sin(a), 0, -Math.cos(a)), 0.13F, 0.07F, 0.5F, V3.of(0, -1, 0), 3, G.FLAT, new float[]{k / 4F * 0.75F, 0F, k / 4F * 0.75F + 0.25F, 1F}, Col.shade(tint, 0.9F), Col.WHITE, 0.004F);
            }
            G.sprite(c.sc.glow("glow"), at[j].add(0, 0.09F, 0), c.camR, c.camU, c.camF, 0.1F, 0.1F, 0, G.FULL, Col.alpha(glowCol, 0.9F));
        }
        for (int k = 0; k < 5; k++) {
            float life = (c.t * 0.012F + k * 0.2F + (side > 0 ? 0.1F : 0F)) % 1F;
            V3 p = W.add(side * (0.2F + 0.6F * Noise.hash(k, 21)), -life * 0.9F, -0.1F + 0.1F * (float) Math.sin(c.t * 0.1F + k));
            float a = (float) Math.sin(Math.PI * life);
            G.sprite(c.sc.glow("spark"), p, c.camR, c.camU, c.camF, 0.05F * a + 0.01F, 0.05F * a + 0.01F, 0, G.FULL, Col.alpha(glowCol, a * 0.9F));
        }
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
