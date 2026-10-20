package com.tunombre.cosmeticos.client;

import com.tunombre.cosmeticos.client.Draw.V;

import static com.tunombre.cosmeticos.client.Draw.*;

/** los dibujos de cada cosmetico (indice = posicion en Cosmetics.ALL) */
public final class Styles {
    private Styles() { }

    private static final float PI = (float) Math.PI;

    private static float s(double x) { return (float) Math.sin(x); }
    private static float c(double x) { return (float) Math.cos(x); }
    private static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263; h = (h ^ (h >>> 13)) * 1274126177; h ^= h >>> 16;
        return (h & 0xFFFF) / 65535F;
    }

    public static void draw(String id, Draw d) {
        switch (id) {
            case "alas_luz" -> featherWings(d, 9, 1.0F, 0.12F, argb(1F, 1F, 1F, 0.95F), argb(1F, 0.86F, 0.45F, 0.95F), argb(1F, 0.85F, 0.4F, 0.35F), false, false);
            case "alas_cristal" -> featherWings(d, 8, 1.05F, 0.07F, argb(0.7F, 1F, 1F, 0.9F), argb(0.2F, 0.7F, 1F, 0.9F), argb(0.3F, 0.9F, 1F, 0.55F), true, true);
            case "alas_fuego" -> featherWings(d, 9, 1.05F, 0.11F, argb(1F, 0.85F, 0.2F, 0.95F), argb(1F, 0.2F, 0F, 0.7F), argb(1F, 0.5F, 0.05F, 0.5F), false, false, true);
            case "alas_mortifilia" -> membraneWings(d, 5, 1.2F, argb(0.28F, 0.05F, 0.4F, 0.9F), argb(0.85F, 0.25F, 0.95F, 0.75F), argb(0.9F, 0.4F, 1F, 0.85F), argb(0.08F, 0.02F, 0.12F, 1F));
            case "alas_demonio" -> membraneWings(d, 5, 1.45F, argb(0.25F, 0.02F, 0.02F, 0.95F), argb(0.8F, 0.08F, 0.05F, 0.9F), argb(1F, 0.25F, 0.05F, 0.8F), argb(0.06F, 0.01F, 0.01F, 1F));
            case "alas_mariposa" -> butterflyWings(d);
            case "tentaculos_slender" -> slender(d);
            case "tentaculos_mortifilia" -> mortiTentacles(d);
            case "cola_demonio" -> tail(d);
            case "flores_mortifilia" -> bodyFlowers(d);
            case "enredadera" -> vines(d);
            case "flor_cabeza" -> headFlower(d);
            case "aureola" -> halo(d);
            case "corona_espinas" -> thorns(d);
            case "cuernos_demonio" -> horns(d);
            case "aura_fuego" -> fireAura(d);
            case "aura_oscura" -> darkAura(d);
            case "orbitas" -> orbs(d);
            case "anillos_arcanos" -> rings(d);
            case "ojos_flotantes" -> eyes(d);
            case "estrellas" -> stars(d);
            case "rayos" -> lightning(d);
            case "mariposas_luz" -> butterflies(d);
            case "luciernagas" -> fireflies(d);
            default -> { }
        }
    }

    // =============== ALAS ===============

    private static void featherWings(Draw d, int n, float len, float wid, int cBase, int cTip, int cGlow, boolean shimmer, boolean crystal) {
        featherWings(d, n, len, wid, cBase, cTip, cGlow, shimmer, crystal, false);
    }

    private static void featherWings(Draw d, int n, float len, float wid, int cBase, int cTip, int cGlow, boolean shimmer, boolean crystal, boolean fire) {
        float flap = d.flapV;
        float phi = d.glide ? 0.2F : 0.5F + 0.32F * flap;
        for (int side = -1; side <= 1; side += 2) {
            V root = d.L(side * 0.1F, 1.38F, -0.17F);
            for (int layer = 0; layer < 2; layer++) {
                for (int i = 0; i < n; i++) {
                    float f = i / (float) (n - 1);
                    float a = (float) Math.toRadians(-30 + 130 * f);
                    float ph = phi + layer * 0.12F;
                    float lx = side * c(a) * c(ph), ly = s(a), lz = -c(a) * s(ph);
                    V dir = d.D(lx, ly, lz).norm();
                    V nrm = d.D(s(ph), 0, side * c(ph));
                    V sd = dir.cross(nrm).norm();
                    float l = len * (layer == 0 ? 1F : 0.62F) * (0.62F + 0.38F * s(f * PI * 0.85F + 0.25F));
                    if (fire) l *= 1F + 0.14F * s(d.t * 0.6F + i * 1.9F + layer);
                    int tip = cTip, base = cBase;
                    if (shimmer) tip = hsv(0.5F + 0.1F * s(d.t * 0.08F + f * 3F), 0.65F, 1F, 0.9F);
                    if (fire) { tip = mix(argb(1F, 0.15F, 0F, 0.6F), argb(1F, 0.8F, 0.1F, 0.9F), 0.5F + 0.5F * s(d.t * 0.5F + i)); }
                    float w = wid * (crystal ? 0.55F : 1F) * (layer == 0 ? 1F : 0.8F);
                    d.leaf(false, root, dir, sd, l, w, base, tip);
                    d.leaf(true, root, dir, sd, l * 1.04F, w * 1.5F, alpha(cGlow, 0.05F), cGlow);
                    if (layer == 0 && (crystal || fire || i % 3 == 0)) d.glow(root.add(dir.mul(l)), 0.07F + 0.03F * s(d.t * 0.2F + i), alpha(tip, 0.8F));
                }
            }
            d.glow(root, 0.12F, alpha(cGlow, 0.5F));
        }
    }

    private static void membraneWings(Draw d, int n, float len, int cMem, int cEdge, int cGlow, int cBone) {
        float flap = d.flapV;
        float phi = d.glide ? 0.15F : 0.55F + 0.4F * flap;
        for (int side = -1; side <= 1; side += 2) {
            V root = d.L(side * 0.1F, 1.35F, -0.17F);
            V[] tip = new V[n]; V[] elbow = new V[n];
            for (int i = 0; i < n; i++) {
                float f = i / (float) (n - 1);
                float a = (float) Math.toRadians(95 - 120 * f);
                float ph = phi + f * 0.25F * (1 + flap * 0.4F);
                float l = len * (i == 0 ? 0.85F : 1F - 0.4F * Math.abs(f - 0.35F));
                V dir = d.D(side * c(a) * c(ph), s(a), -c(a) * s(ph)).norm();
                tip[i] = root.add(dir.mul(l));
                elbow[i] = root.add(dir.mul(l * 0.42F)).add(d.D(0, 0.05F * (n - i), 0));
            }
            for (int i = 0; i < n - 1; i++) {
                V mid = tip[i].add(tip[i + 1]).mul(0.5F);
                mid = root.add(mid.sub(root).mul(0.78F));
                d.tri(false, root, tip[i], mid, cMem, cEdge, cEdge);
                d.tri(false, root, mid, tip[i + 1], cMem, cEdge, cEdge);
                d.tri(true, root, tip[i], mid, alpha(cGlow, 0.0F), alpha(cGlow, 0.18F), alpha(cGlow, 0.1F));
                d.tri(true, root, mid, tip[i + 1], alpha(cGlow, 0.0F), alpha(cGlow, 0.1F), alpha(cGlow, 0.18F));
            }
            for (int i = 0; i < n; i++) {
                V mid = elbow[i];
                V[] p = {root, mid, tip[i]};
                d.strip(false, p, new float[]{0.045F, 0.03F, 0.012F}, new int[]{cBone, cBone, cBone});
                d.strip(true, p, new float[]{0.03F, 0.02F, 0.01F}, new int[]{alpha(cGlow, 0.5F), alpha(cGlow, 0.7F), alpha(cGlow, 0.9F)});
                d.glow(tip[i], 0.06F + 0.02F * s(d.t * 0.2F + i), alpha(cGlow, 0.9F));
            }
        }
    }

    private static void butterflyWings(Draw d) {
        float flap = d.flapV;
        float phi = d.glide ? 0.3F : 0.55F + 0.45F * (0.5F + 0.5F * s(d.t * (d.air ? 0.5F : 0.16F)));
        for (int side = -1; side <= 1; side += 2) {
            V root = d.L(side * 0.08F, 1.28F, -0.16F);
            for (int part = 0; part < 2; part++) {
                int seg = 12;
                V prev = null; int prevC = 0;
                for (int k = 0; k <= seg; k++) {
                    float u = k / (float) seg;
                    float a = part == 0 ? (float) Math.toRadians(10 + 95 * u) : (float) Math.toRadians(-75 + 85 * u);
                    float rho = (part == 0 ? 1F : 0.7F) * (0.55F + 0.45F * s(u * PI));
                    float ph = phi + (part == 1 ? 0.12F : 0F);
                    V dir = d.D(side * c(a) * c(ph), s(a), -c(a) * s(ph)).norm();
                    V p = root.add(dir.mul(rho * 0.95F));
                    int col = hsv(0.82F + 0.2F * u + part * 0.08F + d.t * 0.002F, 0.55F, 1F, 0.8F);
                    if (prev != null) {
                        d.tri(false, root, prev, p, argb(1F, 1F, 1F, 0.9F), prevC, col);
                        d.tri(true, root, prev, p, alpha(col, 0F), alpha(col, 0.35F), alpha(col, 0.35F));
                    }
                    d.glow(p, 0.045F, alpha(col, 0.55F));
                    prev = p; prevC = col;
                }
            }
        }
    }

    // =============== TENTACULOS ===============

    /** un tentaculo con ondulacion; devuelve la punta */
    private static V tentacle(Draw d, V rootL, V dirL, float len, int segs, float thick, float phase, float amp, float droop, int c0, int c1, boolean glowTip, int glowCol, float whip) {
        V root = d.L(rootL.x(), rootL.y(), rootL.z());
        V dir = d.D(dirL.x(), dirL.y(), dirL.z()).norm();
        V p1 = dir.cross(v(0, 1, 0));
        p1 = p1.len() < 0.1F ? v(1, 0, 0) : p1.norm();
        V p2 = dir.cross(p1).norm();
        V[] pts = new V[segs + 1]; float[] w = new float[segs + 1]; int[] col = new int[segs + 1];
        for (int k = 0; k <= segs; k++) {
            float u = k / (float) segs;
            float a1 = amp * u * s(d.t * 0.07F * whip + phase + u * 4F);
            float a2 = amp * u * c(d.t * 0.05F * whip + phase * 1.3F + u * 3.3F);
            V p = root.add(dir.mul(len * u)).add(p1.mul(a1)).add(p2.mul(a2));
            p = p.add(v(0, -droop * u * u, 0)).add(v(-d.vx * u * u * 5F, 0, -d.vz * u * u * 5F));
            pts[k] = p;
            w[k] = thick * (1F - u * 0.92F) + 0.004F;
            col[k] = mix(c0, c1, u);
        }
        d.strip(false, pts, w, col);
        if (glowTip) d.glow(pts[segs], 0.09F, glowCol);
        return pts[segs];
    }

    private static void slender(Draw d) {
        int dark = argb(0.03F, 0.03F, 0.04F, 1F), sheen = argb(0.12F, 0.1F, 0.16F, 1F);
        float[][] cfg = {
                {0.18F, 1.25F, -0.2F, 0.9F, 0.5F, -0.7F}, {-0.18F, 1.25F, -0.2F, -0.9F, 0.5F, -0.7F},
                {0.2F, 0.95F, -0.2F, 0.95F, -0.1F, -0.6F}, {-0.2F, 0.95F, -0.2F, -0.95F, -0.1F, -0.6F},
                {0.1F, 1.4F, -0.2F, 0.3F, 1F, -0.6F}, {-0.1F, 1.4F, -0.2F, -0.3F, 1F, -0.6F}};
        for (int i = 0; i < cfg.length; i++) {
            float[] q = cfg[i];
            tentacle(d, v(q[0], q[1], q[2]), v(q[3], q[4], q[5]), 1.9F, 14, 0.055F, i * 1.7F, 0.38F, 0.2F, dark, sheen, false, 0, 0.7F);
        }
    }

    private static void mortiTentacles(Draw d) {
        int c0 = argb(0.25F, 0.04F, 0.35F, 0.95F), c1 = argb(0.95F, 0.4F, 0.95F, 0.9F);
        for (int i = 0; i < 6; i++) {
            float a = i * PI / 3F + 0.3F;
            V tip = tentacle(d, v(s(a) * 0.15F, 1.1F + (i % 2) * 0.25F, -0.2F), v(s(a), 0.35F + (i % 3) * 0.3F, -0.8F), 1.5F, 12, 0.05F, i * 2.1F, 0.35F, 0.1F, c0, c1, true, argb(1F, 0.5F, 1F, 0.8F), 0.9F);
            // bulbos brillantes a lo largo
            V root = d.L(s(a) * 0.15F, 1.1F + (i % 2) * 0.25F, -0.2F);
            d.glow(root.add(tip.sub(root).mul(0.5F)), 0.045F, argb(0.9F, 0.4F, 1F, 0.6F));
        }
    }

    private static void tail(Draw d) {
        V tip = tentacle(d, v(0, 0.85F, -0.16F), v(0, -0.1F, -1F), 1.0F, 12, 0.07F, 0.4F, 0.3F, 0.45F, argb(0.5F, 0.03F, 0.03F, 1F), argb(0.9F, 0.1F, 0.08F, 1F), false, 0, 1.2F);
        V root = d.L(0, 0.85F, -0.16F);
        V dir = tip.sub(root).norm();
        d.leafAuto(false, tip, dir, 0.2F, 0.1F, argb(0.9F, 0.1F, 0.08F, 1F), argb(1F, 0.3F, 0.1F, 1F));
        d.glow(tip.add(dir.mul(0.12F)), 0.09F, argb(1F, 0.25F, 0.05F, 0.6F));
    }

    // =============== FLORES ===============

    private static final int P_OUT0 = argb(0.28F, 0.04F, 0.45F, 0.95F), P_OUT1 = argb(0.85F, 0.35F, 1F, 0.95F);
    private static final int P_IN0 = argb(0.6F, 0.2F, 0.7F, 0.95F), P_IN1 = argb(1F, 0.8F, 1F, 0.95F);

    /** flor de Mortifilia: c = centro en el mundo, n = hacia donde abre, bloom 0 (capullo) .. 1 (abierta) */
    private static void flower(Draw d, V c, V n, float size, float bloom, int petals, float rot, int out0, int out1, int in0, int in1) {
        n = n.norm();
        V u = n.cross(v(0, 1, 0));
        u = u.len() < 0.1F ? v(1, 0, 0) : u.norm();
        V w = n.cross(u).norm();
        for (int ring = 0; ring < 2; ring++) {
            int cnt = ring == 0 ? petals : petals - 1;
            float sz = size * (ring == 0 ? 1F : 0.62F);
            for (int p = 0; p < cnt; p++) {
                float a = rot + (float) (Math.PI * 2 * p / cnt) + ring * 0.4F;
                V radial = u.mul(c(a)).add(w.mul(s(a)));
                float open = 0.25F + 0.75F * bloom;
                V dir = radial.mul(open * (ring == 0 ? 1F : 0.6F)).add(n.mul(1.1F - 0.55F * bloom)).norm();
                V side = dir.cross(n);
                side = side.len() < 1e-4F ? u : side.norm();
                d.leaf(false, c, dir, side, sz, sz * 0.4F, ring == 0 ? out0 : in0, ring == 0 ? out1 : in1);
                d.leaf(true, c, dir, side, sz * 1.05F, sz * 0.5F, alpha(out1, 0.0F), alpha(out1, 0.28F));
            }
        }
        d.glow(c.add(n.mul(size * 0.25F)), size * (0.35F + 0.4F * bloom), argb(1F, 0.85F, 1F, 0.55F * (0.4F + 0.6F * bloom)));
    }

    private static void mflower(Draw d, V c, V n, float size, float bloom) { flower(d, c, n, size, bloom, 7, 0.2F, P_OUT0, P_OUT1, P_IN0, P_IN1); }

    private static void bodyFlowers(Draw d) {
        float[][] at = {
                {0.34F, 1.5F, 0F, 1F, 0.6F, 0F, 0.14F}, {-0.34F, 1.5F, 0F, -1F, 0.6F, 0F, 0.12F},
                {0F, 1.2F, 0.15F, 0F, 0.25F, 1F, 0.12F}, {0F, 1.15F, -0.15F, 0F, 0.3F, -1F, 0.16F},
                {0.28F, 0.85F, -0.05F, 1F, 0F, -0.3F, 0.11F}, {-0.28F, 0.5F, 0F, -1F, 0F, 0.2F, 0.1F},
                {0.27F, 1.85F, 0F, 1F, 0.5F, 0F, 0.1F}, {-0.27F, 1.9F, 0.02F, -1F, 0.4F, 0F, 0.09F},
                {0.4F, 1.05F, 0.02F, 1F, 0.1F, 0.3F, 0.08F}, {0.15F, 0.55F, 0.13F, 0F, 0.1F, 1F, 0.07F},
                {-0.4F, 1.2F, -0.02F, -1F, 0.3F, 0F, 0.07F}, {0.1F, 1.7F, -0.2F, 0F, 0.6F, -1F, 0.08F}};
        for (int i = 0; i < at.length; i++) {
            float[] q = at[i];
            float bloom = 0.55F + 0.45F * s(d.t * 0.03F + i * 1.7F);
            float sway = 1F + 0.04F * s(d.t * 0.08F + i);
            V c = d.L(q[0], q[1], q[2]);
            V n = d.D(q[3], q[4], q[5]);
            // tallito corto desde el cuerpo
            d.strip(false, new V[]{c.sub(n.norm().mul(0.03F)), c.add(n.norm().mul(0.04F * sway))}, new float[]{0.03F, 0.02F}, new int[]{argb(0.1F, 0.4F, 0.15F, 1F), argb(0.2F, 0.6F, 0.2F, 1F)});
            mflower(d, c.add(n.norm().mul(0.05F)), n, q[6], bloom);
        }
    }

    private static void vines(Draw d) {
        int g0 = argb(0.1F, 0.4F, 0.12F, 1F), g1 = argb(0.3F, 0.75F, 0.25F, 1F);
        for (int vi = 0; vi < 2; vi++) {
            int n = 22;
            V[] p = new V[n]; float[] w = new float[n]; int[] col = new int[n];
            for (int k = 0; k < n; k++) {
                float u = k / (float) (n - 1);
                float y = 0.1F + 1.85F * u;
                float th = y * 4.6F + vi * PI + s(d.t * 0.02F) * 0.2F;
                float r = (y > 0.75F ? 0.31F : 0.22F) + 0.025F * s(d.t * 0.08F + y * 6F);
                p[k] = d.L(c(th) * r, y, s(th) * r);
                w[k] = 0.035F; col[k] = mix(g0, g1, 0.5F + 0.5F * s(k * 0.9F));
                if (k % 3 == 1) {
                    V out = d.D(c(th), 0.5F, s(th)).norm();
                    d.leafAuto(false, p[k], out, 0.13F, 0.05F, argb(0.12F, 0.5F, 0.15F, 1F), argb(0.35F, 0.85F, 0.3F, 1F));
                }
                if (k % 7 == 4) mflower(d, p[k].add(d.D(c(th), 0, s(th)).mul(0.03F)), d.D(c(th), 0.3F, s(th)), 0.08F, 0.6F + 0.4F * s(d.t * 0.04F + k));
            }
            d.strip(false, p, w, col);
        }
    }

    private static void headFlower(Draw d) {
        V c = d.L(0F, 2.1F, -0.02F);
        float bloom = 0.65F + 0.35F * s(d.t * 0.02F);
        d.strip(false, new V[]{d.L(0, 1.98F, 0), c}, new float[]{0.05F, 0.035F}, new int[]{argb(0.1F, 0.4F, 0.15F, 1F), argb(0.2F, 0.6F, 0.2F, 1F)});
        flower(d, c, d.D(0F, 1F, 0.12F), 0.36F, bloom, 9, d.t * 0.003F, P_OUT0, P_OUT1, P_IN0, P_IN1);
        // esporas
        for (int i = 0; i < 6; i++) {
            float ph = (d.t * 0.012F + i / 6F) % 1F;
            float a = i * 2.4F + d.t * 0.02F;
            d.glow(c.add(d.D(c(a) * 0.18F * ph, 0.1F + ph * 0.55F, s(a) * 0.18F * ph)), 0.035F, argb(0.95F, 0.6F, 1F, (1F - ph) * 0.8F));
        }
    }

    // =============== CABEZA ===============

    private static V[] circle(Draw d, V center, V u, V w, float r, int n, boolean closed) {
        V[] p = new V[n + (closed ? 1 : 0)];
        for (int i = 0; i < p.length; i++) { float a = (float) (Math.PI * 2 * i / n); p[i] = center.add(u.mul(c(a) * r)).add(w.mul(s(a) * r)); }
        return p;
    }

    private static void ringStrip(Draw d, V[] p, float w, int col, boolean glow) {
        float[] ws = new float[p.length]; int[] cs = new int[p.length];
        java.util.Arrays.fill(ws, w); java.util.Arrays.fill(cs, col);
        d.strip(glow, p, ws, cs);
    }

    private static void halo(Draw d) {
        float y = 2.3F + 0.03F * s(d.t * 0.1F);
        V ctr = d.L(0, y, -0.03F);
        V u = d.D(1, 0, 0), w = d.D(0, 0.15F, 1).norm();
        V[] p = circle(d, ctr, u, w, 0.3F, 36, true);
        ringStrip(d, p, 0.045F, argb(1F, 0.85F, 0.35F, 1F), false);
        ringStrip(d, p, 0.14F, argb(1F, 0.8F, 0.3F, 0.35F), true);
        for (int i = 0; i < 5; i++) {
            float a = d.t * 0.05F + i * 1.26F;
            d.star(ctr.add(u.mul(c(a) * 0.3F)).add(w.mul(s(a) * 0.3F)), 0.07F + 0.03F * s(d.t * 0.3F + i), argb(1F, 0.95F, 0.7F, 0.9F));
        }
    }

    private static void thorns(Draw d) {
        V ctr = d.L(0, 2.0F, 0);
        V[] p = circle(d, ctr, d.D(1, 0, 0), d.D(0, 0, 1), 0.27F, 24, true);
        ringStrip(d, p, 0.035F, argb(0.2F, 0.05F, 0.05F, 1F), false);
        int n = 14;
        for (int i = 0; i < n; i++) {
            float a = (float) (Math.PI * 2 * i / n);
            V base = ctr.add(d.D(c(a) * 0.27F, 0, s(a) * 0.27F));
            V dir = d.D(c(a) * 0.4F, 1F, s(a) * 0.4F).norm();
            float l = 0.11F + 0.07F * ((i % 2)) + 0.03F * hash(i, 3);
            d.leafAuto(false, base, dir, l, 0.022F, argb(0.3F, 0.05F, 0.05F, 1F), argb(0.9F, 0.15F, 0.1F, 1F));
            d.glow(base.add(dir.mul(l)), 0.025F, argb(1F, 0.2F, 0.1F, 0.7F));
        }
    }

    private static void horns(Draw d) {
        for (int side = -1; side <= 1; side += 2) {
            int n = 10;
            V[] p = new V[n]; float[] w = new float[n]; int[] col = new int[n];
            for (int k = 0; k < n; k++) {
                float u = k / (float) (n - 1);
                p[k] = d.L(side * (0.12F + 0.2F * s(u * 1.3F)), 2.03F + 0.4F * u, 0.0F + 0.15F * s(u * 2F));
                w[k] = 0.075F * (1F - u) + 0.008F;
                col[k] = mix(argb(0.06F, 0.02F, 0.02F, 1F), argb(0.75F, 0.15F, 0.08F, 1F), u * u);
            }
            d.strip(false, p, w, col);
            d.glow(p[n - 1], 0.06F, argb(1F, 0.2F, 0.05F, 0.7F));
        }
    }

    // =============== AURAS ===============

    private static void fireAura(Draw d) {
        int n = 26;
        for (int i = 0; i < n; i++) {
            float ph = (d.t * 0.022F + i / (float) n) % 1F;
            float a = i * 2.4F + d.t * 0.01F;
            float r = 0.4F + 0.15F * s(i * 1.3F);
            V p = d.L(c(a) * r * (1F - 0.3F * ph), ph * 2.1F, s(a) * r * (1F - 0.3F * ph));
            float size = 0.17F * (float) Math.pow(1F - ph, 0.8F) + 0.02F;
            int col = mix(argb(1F, 0.95F, 0.3F, 0.9F), argb(0.9F, 0.1F, 0F, 0.0F), ph);
            d.leafAuto(true, p, v(0, 1, 0), size * 2.6F, size, col, alpha(col, 0F));
            d.glow(p, size * 0.8F, alpha(col, 0.5F));
        }
    }

    private static void darkAura(Draw d) {
        int n = 20;
        for (int i = 0; i < n; i++) {
            float ph = (d.t * 0.014F + i / (float) n) % 1F;
            float a = i * 2.4F - d.t * 0.012F;
            float r = 0.35F + 0.2F * ph + 0.1F * s(i * 1.7F);
            V p = d.L(c(a) * r, 0.05F + ph * 2.0F, s(a) * r);
            float size = 0.06F + 0.16F * ph;
            int col = argb(0.08F, 0.03F, 0.12F, 0.55F * (1F - ph));
            d.disc(false, p, size, size, col, alpha(col, 0F), 8);
            if (i % 4 == 0) d.glow(p, size * 0.4F, argb(0.6F, 0.2F, 1F, 0.5F * (1F - ph)));
        }
    }

    private static void orbs(Draw d) {
        for (int i = 0; i < 5; i++) {
            float sp = 0.06F * (1F + i * 0.3F);
            float rad = 0.7F + 0.12F * (i % 2);
            float h = 0.35F + 0.36F * i + 0.12F * s(d.t * 0.05F + i);
            int col = hsv(0.55F + i * 0.07F, 0.6F, 1F, 0.9F);
            V[] tr = new V[12]; float[] w = new float[12]; int[] cc = new int[12];
            for (int k = 0; k < 12; k++) {
                float a = d.t * sp + i * 1.26F - k * 0.09F;
                tr[k] = d.L(c(a) * rad, h, s(a) * rad);
                w[k] = 0.07F * (1F - k / 12F); cc[k] = alpha(col, 0.7F * (1F - k / 12F));
            }
            d.strip(true, tr, w, cc);
            d.glow(tr[0], 0.15F, alpha(col, 0.8F));
            d.disc(true, tr[0], 0.04F, 0.04F, argb(1F, 1F, 1F, 1F), argb(1F, 1F, 1F, 0.3F), 8);
        }
    }

    private static void rings(Draw d) {
        for (int k = 0; k < 3; k++) {
            float a1 = d.t * 0.035F * (k + 1) * (k % 2 == 0 ? 1 : -1) + k * 1.1F;
            float tilt = 0.6F + k * 0.5F;
            V u = d.D(c(a1), 0, s(a1)).norm();
            V w = d.D(-s(a1) * s(tilt), c(tilt), c(a1) * s(tilt)).norm();
            V ctr = d.L(0, 1.0F, 0);
            V[] p = circle(d, ctr, u, w, 0.78F + k * 0.04F, 40, true);
            int col = hsv(0.72F + k * 0.08F, 0.6F, 1F, 0.9F);
            ringStrip(d, p, 0.025F, col, true);
            ringStrip(d, p, 0.07F, alpha(col, 0.25F), true);
            for (int r = 0; r < 4; r++) {
                float a = d.t * 0.03F * (k + 1) + r * PI / 2F;
                d.star(ctr.add(u.mul(c(a) * (0.78F + k * 0.04F))).add(w.mul(s(a) * (0.78F + k * 0.04F))), 0.06F, alpha(col, 0.9F));
            }
        }
    }

    private static void eyes(Draw d) {
        for (int i = 0; i < 6; i++) {
            float a = d.t * 0.012F + i * 1.047F;
            float r = 0.95F + 0.12F * s(i * 2F + d.t * 0.02F);
            V c = d.L(c(a) * r, 0.7F + 0.28F * i * 0.7F + 0.1F * s(d.t * 0.04F + i), s(a) * r);
            float blink = s(d.t * 0.025F + i * 2F) > 0.96F ? 0.12F : 1F;
            float sz = 0.1F;
            d.glow(c, sz * 2.2F, argb(1F, 0.1F, 0.05F, 0.5F));
            d.disc(false, c, sz * 1.1F, sz * 0.7F * blink, argb(0.95F, 0.92F, 0.9F, 1F), argb(0.8F, 0.75F, 0.72F, 1F), 14);
            d.disc(false, c, sz * 0.5F, sz * 0.5F * blink, argb(1F, 0.1F, 0.05F, 1F), argb(0.6F, 0F, 0F, 1F), 12);
            d.disc(false, c, sz * 0.12F, sz * 0.42F * blink, argb(0F, 0F, 0F, 1F), argb(0F, 0F, 0F, 1F), 8);
        }
    }

    private static void stars(Draw d) {
        for (int i = 0; i < 14; i++) {
            float a = d.t * (0.02F + 0.004F * (i % 4)) * (i % 2 == 0 ? 1 : -1) + i * 0.9F;
            float r = 0.5F + 0.6F * hash(i, 1);
            V p = d.L(c(a) * r, 0.1F + 2.0F * hash(i, 2) + 0.1F * s(d.t * 0.05F + i), s(a) * r);
            float tw = 0.5F + 0.5F * s(d.t * 0.18F + i * 3F);
            d.star(p, 0.05F + 0.06F * tw, hsv(0.12F + 0.1F * hash(i, 4), 0.35F, 1F, 0.4F + 0.6F * tw));
        }
    }

    private static void lightning(Draw d) {
        int seed = (int) (d.t / 3F);
        for (int i = 0; i < 5; i++) {
            if (hash(seed, i) < 0.35F) continue;
            float a = hash(seed, i + 10) * PI * 2F, el = hash(seed, i + 20) * 1.6F - 0.3F;
            float r = 0.9F + 0.5F * hash(seed, i + 30);
            V from = d.L(i % 2 == 0 ? 0.3F : -0.3F, 1.4F, 0F);
            V to = d.L(c(a) * r, 1.0F + el * 0.8F, s(a) * r);
            int n = 7;
            V[] p = new V[n]; float[] w = new float[n]; float[] w2 = new float[n]; int[] c1 = new int[n]; int[] c2 = new int[n];
            V dir = to.sub(from);
            V pp = dir.cross(from.norm()).norm();
            for (int k = 0; k < n; k++) {
                float u = k / (float) (n - 1);
                float j = (k == 0 || k == n - 1) ? 0F : (hash(seed * 7 + i, k) - 0.5F) * 0.35F;
                float j2 = (k == 0 || k == n - 1) ? 0F : (hash(seed * 7 + i, k + 50) - 0.5F) * 0.35F;
                p[k] = from.add(dir.mul(u)).add(pp.mul(j)).add(v(0, j2, 0));
                w[k] = 0.02F; w2[k] = 0.11F;
                c1[k] = argb(1F, 1F, 1F, 1F); c2[k] = argb(0.3F, 0.7F, 1F, 0.35F);
            }
            d.strip(true, p, w2, c2);
            d.strip(true, p, w, c1);
            d.glow(p[n - 1], 0.12F, argb(0.5F, 0.85F, 1F, 0.7F));
        }
    }

    private static void smallButterfly(Draw d, V c, V f, float sz, float phase, int c1, int c2) {
        V up = v(0, 1, 0);
        V lat = up.cross(f);
        lat = lat.len() < 1e-4F ? v(1, 0, 0) : lat.norm();
        float fl = s(d.t * 0.6F + phase);
        float ang = 0.15F + 1.0F * (0.5F + 0.5F * fl);
        for (int side = -1; side <= 1; side += 2) {
            V W = lat.mul(side * c(ang)).add(up.mul(s(ang))).mul(sz);
            V p1 = c.add(W).add(f.mul(sz * 0.7F)), p2 = c.add(W.mul(1.25F)), p3 = c.add(W).sub(f.mul(sz * 0.7F));
            d.quad(true, c, p1, p2, p3, alpha(c1, 0.9F), alpha(c2, 0.8F), alpha(c2, 0.8F), alpha(c2, 0.8F));
            d.quad(false, c, p1, p2, p3, alpha(c1, 0.55F), alpha(c2, 0.4F), alpha(c2, 0.4F), alpha(c2, 0.4F));
        }
        d.glow(c, sz * 0.8F, alpha(c1, 0.6F));
    }

    private static void butterflies(Draw d) {
        for (int i = 0; i < 6; i++) {
            float sp = 0.03F * (0.7F + 0.1F * i);
            float a = d.t * sp + i * 1.05F;
            float r = 0.75F + 0.25F * s(i * 1.9F + d.t * 0.01F);
            float h = 0.5F + 1.4F * hash(i, 5) + 0.15F * s(d.t * 0.07F + i);
            V p = d.L(c(a) * r, h, s(a) * r);
            V f = d.D(-s(a), 0, c(a)).norm();
            smallButterfly(d, p, f, 0.1F, i * 1.3F, hsv(0.45F + 0.05F * i, 0.4F, 1F, 1F), hsv(0.55F + 0.05F * i, 0.7F, 1F, 1F));
        }
    }

    private static void fireflies(Draw d) {
        for (int i = 0; i < 14; i++) {
            float a = d.t * 0.011F * (1F + hash(i, 6)) + i * 1.9F;
            float b = d.t * 0.017F * (1F + hash(i, 7)) + i * 0.7F;
            V p = d.L(s(a) * (0.5F + 0.5F * hash(i, 8)), 0.3F + 1.5F * (0.5F + 0.5F * s(b)), c(a * 1.3F + i) * (0.5F + 0.5F * hash(i, 9)));
            float pulse = 0.35F + 0.65F * (0.5F + 0.5F * s(d.t * 0.12F + i * 2.2F));
            d.glow(p, 0.09F, argb(0.8F, 1F, 0.3F, 0.7F * pulse));
            d.disc(true, p, 0.02F, 0.02F, argb(1F, 1F, 0.8F, pulse), argb(1F, 1F, 0.6F, 0F), 6);
        }
    }
}
