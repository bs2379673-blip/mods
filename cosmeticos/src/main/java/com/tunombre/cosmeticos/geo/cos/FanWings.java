package com.tunombre.cosmeticos.geo.cos;

import com.tunombre.cosmeticos.geo.*;

/** alas en abanico: plumas (luz), lenguas de llamas (fuego) o esquirlas de cristal. El borde de ataque es una curva que se pliega o se abre. */
public final class FanWings implements Cosm {
    public enum Kind { FEATHER, FLAME, CRYSTAL }

    private final String id, tex, glowTex;
    private final Kind kind;
    private final float len, wid;
    private final int n;
    private final int tint;
    private final int glowCol;

    public FanWings(String id, Kind kind, String tex, String glowTex, float len, float wid, int n, int tint, int glowCol) {
        this.id = id; this.kind = kind; this.tex = tex; this.glowTex = glowTex; this.len = len; this.wid = wid; this.n = n; this.tint = tint; this.glowCol = glowCol;
    }

    @Override public Bone bone() { return Bone.CHEST; }

    // puntos de control del borde de ataque, relativos al hombro (x se multiplica por el lado)
    private static final V3[] SPREAD = {V3.of(0.38, 0.55, -0.12), V3.of(1.00, 0.82, -0.28), V3.of(1.45, 0.50, -0.38)};
    private static final V3[] FOLDED = {V3.of(0.10, 0.52, -0.06), V3.of(0.16, 0.80, -0.16), V3.of(0.12, 0.50, -0.32)};

    private static V3 bez(V3 a, V3 b, V3 c, V3 d, float t) {
        float u = 1 - t;
        return a.mul(u * u * u).add(b.mul(3 * u * u * t)).add(c.mul(3 * u * t * t)).add(d.mul(t * t * t));
    }

    private static V3 bezT(V3 a, V3 b, V3 c, V3 d, float t) {
        float u = 1 - t;
        return b.sub(a).mul(3 * u * u).add(c.sub(b).mul(6 * u * t)).add(d.sub(c).mul(3 * t * t));
    }

    @Override
    public void build(Ctx c) {
        Motion m = c.state(id, Motion::new);
        m.update(c, kind == Kind.FLAME ? 0.3F : 0.22F, 0.40F);
        for (int side = -1; side <= 1; side += 2) wing(c, m, side);
        // brillo en la raiz
        Mesh g = c.sc.glow("glow");
        for (int side = -1; side <= 1; side += 2)
            G.sprite(g, V3.of(side * 0.1, -0.2, -0.2), c.camR, c.camU, c.camF, 0.35F, 0.35F, 0F, G.FULL, Col.alpha(glowCol, 0.55F));
    }

    private void wing(Ctx c, Motion m, int side) {
        float u = m.open;
        V3 S = V3.of(side * 0.10, -0.20, -0.15);
        V3 c1 = lerpSide(FOLDED[0], SPREAD[0], u, side), c2 = lerpSide(FOLDED[1], SPREAD[1], u, side), c3 = lerpSide(FOLDED[2], SPREAD[2], u, side);
        V3 P0 = V3.ZERO;
        V3 down = V3.of(0, -1, -0.2).norm();
        float flapScale = 0.3F + 0.7F * u;
        int rows = kind == Kind.CRYSTAL ? 2 : kind == Kind.FLAME ? 2 : 3;
        for (int row = 0; row < rows; row++) {
            int cnt = n - row * (kind == Kind.FLAME ? 3 : 2);
            for (int i = 0; i < cnt; i++) {
                float s = (i + 0.5F) / cnt;
                if (row > 0) s = (i + 0.35F) / cnt * 0.97F;
                float jit = Noise.hash(i * 7 + row * 131, side + 9) - 0.5F;
                V3 T = bezT(P0, c1, c2, c3, s).norm();
                float phiS = (float) Math.toRadians(85 - 75 * s), phiF = (float) Math.toRadians(160 - 40 * s);
                float phi = Noise.lerp(phiF, phiS, u) + jit * 0.09F;
                V3 D = T.mul((float) Math.cos(phi)).add(down.mul((float) Math.sin(phi))).add(0, 0, -0.14F).norm();
                V3 root = S.add(bez(P0, c1, c2, c3, s));
                // el abanico entero gira alrededor del hombro; cada pluma con un retraso hacia la punta
                float ang = m.flap(side, s, 1.15F) * flapScale;
                root = S.add(root.sub(S).rot(V3.Z, ang));
                D = D.rot(V3.Z, ang);
                float lenS = len * (0.5F + 0.5F * (float) Math.sin(Math.PI / 2 * Math.min(1F, s * 1.15F)));
                if (s > 0.93F) lenS *= 0.75F;
                lenS *= (row == 0 ? 1F : row == 1 ? 0.58F : 0.34F) * (1F + jit * 0.16F);
                float w = wid * (row == 0 ? 1F : row == 1 ? 0.9F : 0.8F);
                V3 off = V3.of(0, 0, -0.018F * row);
                root = root.add(off);
                float flick = 0;
                if (kind == Kind.FLAME) { flick = Noise.n1(c.t * 0.35F + i * 3.1F + row * 9 + side * 20, 3); lenS *= 1F + 0.2F * flick; D = D.add(0, 0.12F * flick, 0).norm(); }
                float lag = (float) Math.sin(m.phase - s * 1.1F - 1.2F);
                V3 bendDir = V3.of(0, -lag * m.amp * 2.5F * u, -1).norm();
                float bend = (kind == Kind.FLAME ? 0.55F : kind == Kind.CRYSTAL ? 0.05F : 0.32F) + 0.12F * Math.abs(lag) * m.amp * 2F;
                int cb = Col.shade(tint, row == 0 ? 0.74F : row == 1 ? 0.88F : 1F), ct = tint;
                switch (kind) {
                    case FEATHER -> {
                        int variant = (int) (Noise.hash(i, row + 5) * 8) % 8;
                        float[] uv = row == 0 ? new float[]{variant / 8F, 0F, (variant + 1) / 8F, 0.75F} : new float[]{(variant % 4) / 4F, 0.75F, (variant % 4 + 1) / 4F, 1F};
                        G.blade(c.sc.solid(tex), root, D, T, lenS, w, bend, bendDir, 6, G.FLAT, uv, cb, ct, 0.012F);
                        if (glowTex != null && row < 2) G.blade(c.sc.glow(glowTex), root, D, T, lenS, w, bend, bendDir, 6, G.FLAT, uv, Col.alpha(glowCol, 0.0F + 0.55F), Col.alpha(glowCol, 0.85F), 0.012F);
                    }
                    case FLAME -> {
                        int variant = (int) (Noise.hash(i, row + 5) * 8) % 8;
                        float[] uv = {variant / 8F, 0F, (variant + 1) / 8F, 0.75F};
                        G.blade(c.sc.solid(tex), root, D, T, lenS, w * 1.15F, bend, bendDir, 6, G.FLAT, uv, Col.WHITE, Col.WHITE, 0.01F);
                        G.blade(c.sc.glow(glowTex), root, D, T, lenS * 1.03F, w * 1.25F, bend, bendDir, 6, G.FLAT, uv, Col.alpha(glowCol, 0.75F + 0.25F * flick), Col.alpha(glowCol, 0.9F), 0.01F);
                    }
                    case CRYSTAL -> {
                        float[] uv = G.FULL;
                        G.shard(c.sc.solid(tex), root, D, T, lenS * 0.82F, w * 0.62F, w * 0.34F, uv, Col.shade(tint, row == 0 ? 0.7F : 0.9F), tint);
                        G.shard(c.sc.glow(glowTex), root, D, T, lenS * 0.82F, w * 0.62F, w * 0.34F, uv, Col.alpha(glowCol, 0.35F), Col.alpha(glowCol, 0.8F));
                    }
                }
                // chispas en las puntas
                if (row == 0 && kind != Kind.FLAME && (i % 3 == 0)) {
                    V3 tip = root.add(D.mul(lenS));
                    float tw = 0.5F + 0.5F * (float) Math.sin(c.t * 0.13F + i * 2.3F + side);
                    if (kind == Kind.CRYSTAL || tw > 0.6F)
                        G.sprite(c.sc.glow("star"), tip, c.camR, c.camU, c.camF, 0.13F * tw + 0.04F, 0.13F * tw + 0.04F, c.t * 0.02F + i, G.FULL, Col.alpha(glowCol, 0.9F * tw));
                }
            }
        }
        if (kind == Kind.FLAME) embers(c, S, c1, c2, c3, side, u);
    }

    private void embers(Ctx c, V3 S, V3 c1, V3 c2, V3 c3, int side, float u) {
        Mesh g = c.sc.glow("spark");
        for (int k = 0; k < 9; k++) {
            float life = (c.t * 0.018F + k * 0.113F + (side > 0 ? 0.5F : 0F)) % 1F;
            float s = Noise.hash(k, 77 + side) ;
            V3 p = S.add(bez(V3.ZERO, c1, c2, c3, s)).add(side * 0.16F * life * (Noise.hash(k, 3) - 0.5F), -0.15F + life * 0.9F, -0.05F - 0.3F * Noise.hash(k, 4));
            p = p.add(0.05F * (float) Math.sin(c.t * 0.2F + k * 5), 0, 0);
            float a = (float) Math.sin(Math.PI * life);
            G.sprite(g, p, c.camR, c.camU, c.camF, 0.07F * a + 0.015F, 0.07F * a + 0.015F, 0, G.FULL, Col.alpha(glowCol, a));
        }
    }

    private static V3 lerpSide(V3 a, V3 b, float u, int side) {
        V3 l = a.lerp(b, u);
        return new V3(l.x * side, l.y, l.z);
    }
}
