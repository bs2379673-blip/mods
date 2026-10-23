package com.tunombre.cosmeticos.geo.cos;

import com.tunombre.cosmeticos.geo.*;

/**
 * Modo dios oscuro (solo administradores): alas negras enormes con filo rojo que en el suelo envuelven al personaje como una capa y en el
 * aire se abren hasta seis bloques de envergadura con aleteos lentos y pesados; y la guadaña gigante en la mano derecha (con la que se
 * engancha y se arrastra a otros jugadores).
 */
public final class God {
    private God() { }

    // ------------------------------------------------------------------ alas ----
    public static Cosm wings() {
        return new Cosm() {
            @Override public Bone bone() { return Bone.CHEST; }

            // borde de ataque relativo al hombro (x se multiplica por el lado)
            final V3[] spread = {V3.of(0.6, 0.85, -0.2), V3.of(1.9, 1.55, -0.45), V3.of(3.0, 0.9, -0.6)};
            final V3[] wrap = {V3.of(0.42, 0.48, 0.02), V3.of(0.62, 0.22, 0.42), V3.of(0.2, -0.05, 0.62)};

            private V3 bez(V3 a, V3 b, V3 c2, V3 d, float t) {
                float u = 1 - t;
                return a.mul(u * u * u).add(b.mul(3 * u * u * t)).add(c2.mul(3 * u * t * t)).add(d.mul(t * t * t));
            }

            private V3 bezT(V3 a, V3 b, V3 c2, V3 d, float t) {
                float u = 1 - t;
                return b.sub(a).mul(3 * u * u).add(c2.sub(b).mul(6 * u * t)).add(d.sub(c2).mul(3 * t * t));
            }

            @Override
            public void build(Ctx c) {
                Motion m = c.state("alas_dios", Motion::new);
                m.update(c, 0.0F, 0.17F);
                // en vuelo de admin (creativo) también se abren
                if (c.fly && !c.crouch) m.open += (1F - m.open) * (1F - (float) Math.exp(-c.dt * 0.15F));
                for (int side = -1; side <= 1; side += 2) wing(c, m, side);
                // sombra y brasas que se desprenden
                float u = m.open;
                for (int k = 0; k < 14; k++) {
                    float life = (c.t * 0.012F + k / 14F) % 1F;
                    int side = k % 2 == 0 ? 1 : -1;
                    float s = Noise.hash(k, 3);
                    V3 p = V3.of(side * (0.3F + 2.4F * s * u), -0.2F + 1.1F * s * u - life * 1.6F, -0.4F - 0.3F * Noise.hash(k, 4));
                    p = p.add(0.1F * (float) Math.sin(c.t * 0.05F + k), 0, 0);
                    float a = (float) Math.sin(Math.PI * life);
                    G.sprite(c.sc.solid("ash"), p, c.camR, c.camU, c.camF, 0.09F, 0.09F, c.t * 0.03F + k, G.frame(4, k), Col.alpha(0xFF0C0C0E, a));
                    if (k % 3 == 0) G.sprite(c.sc.glow("spark"), p.add(0, 0.2F, 0), c.camR, c.camU, c.camF, 0.06F, 0.06F, 0, G.FULL, Col.alpha(0xFFFF2030, a));
                }
                G.sprite(c.sc.glow("glow"), V3.of(0, -0.25F, -0.25F), c.camR, c.camU, c.camF, 1.2F + 2.4F * u, 1.2F + 1.2F * u, 0, G.FULL, Col.alpha(0xFF600008, 0.18F + 0.1F * u));
            }

            private void wing(Ctx c, Motion m, int side) {
                float u = m.open;
                V3 S = V3.of(side * 0.12, -0.18, -0.16);
                V3 c1 = mix(wrap[0], spread[0], u, side), c2 = mix(wrap[1], spread[1], u, side), c3 = mix(wrap[2], spread[2], u, side);
                V3 down = V3.of(0, -1, -0.15).norm();
                float flapScale = 0.2F + 0.8F * u;
                int[] counts = {20, 17, 13, 9};
                float[] lens = {2.4F, 1.45F, 0.85F, 0.5F};
                for (int row = 0; row < counts.length; row++) {
                    int cnt = counts[row];
                    for (int i = 0; i < cnt; i++) {
                        float s = (i + 0.5F) / cnt * (row == 0 ? 1F : 0.97F);
                        float jit = Noise.hash(i * 7 + row * 131, side + 9) - 0.5F;
                        V3 T = bezT(V3.ZERO, c1, c2, c3, s).norm();
                        // abiertas: las plumas apuntan hacia afuera y abajo; envolviendo: cuelgan hacia el suelo como una capa
                        float phiS = (float) Math.toRadians(80 - 72 * s), phiW = (float) Math.toRadians(170 - 70 * s);
                        float phi = Noise.lerp(phiW, phiS, u) + jit * 0.08F;
                        V3 out = V3.of(side * 0.25F * (1 - u), 0, 0.2F * (1 - u));
                        V3 D = T.mul((float) Math.cos(phi)).add(down.mul((float) Math.sin(phi))).add(out).add(0, 0, -0.1F * u).norm();
                        V3 root = S.add(bez(V3.ZERO, c1, c2, c3, s));
                        float ang = m.flap(side, s, 1.3F) * flapScale;
                        root = S.add(root.sub(S).rot(V3.Z, ang));
                        D = D.rot(V3.Z, ang);
                        float lenS = lens[row] * (0.45F + 0.55F * (float) Math.sin(Math.PI / 2 * Math.min(1F, s * 1.1F))) * (1F + jit * 0.14F);
                        if (s > 0.93F) lenS *= 0.8F;
                        // envolviendo, las plumas largas llegan casi al suelo
                        lenS *= Noise.lerp(0.78F, 1F, u);
                        float w = 0.3F - 0.03F * row;
                        root = root.add(0, 0, -0.022F * row);
                        float lag = (float) Math.sin(m.phase - s * 1.2F - 1.3F);
                        V3 bendDir = V3.of(0, -lag * m.amp * 2.5F * u, -1).norm();
                        float bend = 0.3F + 0.14F * Math.abs(lag) * m.amp * 2F;
                        int variant = (int) (Noise.hash(i, row + 5) * 8) % 8;
                        float[] uv = row < 2 ? new float[]{variant / 8F, 0F, (variant + 1) / 8F, 0.75F} : new float[]{(variant % 4) / 4F, 0.75F, (variant % 4 + 1) / 4F, 1F};
                        int base = Col.shade(0xFF26242A, row == 0 ? 0.7F : 0.85F + 0.05F * row);
                        G.blade(c.sc.solid("feather_dark"), root, D, T, lenS, w, bend, bendDir, 7, G.FLAT, uv, base, 0xFF3A363E, 0.016F);
                        float pulse = 0.55F + 0.45F * (float) Math.sin(c.t * 0.06F - s * 4F + row);
                        if (row < 3) G.blade(c.sc.glow("feather_dark_g"), root, D, T, lenS, w, bend, bendDir, 7, G.FLAT, uv,
                                Col.alpha(0xFFB0101C, 0.25F * pulse), Col.alpha(0xFFFF2A36, 0.85F * pulse), 0.016F);
                    }
                }
            }

            private V3 mix(V3 a, V3 b, float u, int side) { V3 l = a.lerp(b, u); return new V3(l.x * side, l.y, l.z); }
        };
    }

    // ------------------------------------------------------------------ guadaña ----
    public static Cosm scythe() {
        return new Cosm() {
            @Override public Bone bone() { return Bone.HAND_R; }

            @Override
            public void build(Ctx c) {
                float sway = 0.04F * (float) Math.sin(c.t * 0.05F);
                // el asta pasa por la mano: sube por detrás del hombro y baja hasta la hoja
                V3 top = V3.of(0.0F, 1.15F, -0.05F), bot = V3.of(0.0F + sway, -1.6F, 0.06F);
                int n = 10;
                V3[] pts = new V3[n];
                for (int i = 0; i < n; i++) pts[i] = top.lerp(bot, i / (n - 1F)).add(0.025F * (float) Math.sin(i * 0.9F), 0, 0);
                float[] rad = new float[n];
                for (int i = 0; i < n; i++) rad[i] = 0.034F + ((i == 0 || i == n - 1) ? 0.012F : 0F) + (i == 4 ? 0.008F : 0F);
                G.tube(c.sc.solid("scythe_metal"), pts, rad, 8, 1F, 1.1F, 0F, G.fill(n, 0xFFFFFFFF));
                float pulse = 0.55F + 0.45F * (float) Math.sin(c.t * 0.08F);
                G.tube(c.sc.glow("scythe_metal_g"), pts, scale(rad, 1.05F), 8, 1F, 1.1F, 0F, G.fill(n, Col.alpha(0xFFFF2A2A, pulse)));
                // pincho de arriba
                V3 dirUp = top.sub(bot).norm();
                G.tube(c.sc.solid("scythe_metal"), new V3[]{top, top.add(dirUp.mul(0.12F)), top.add(dirUp.mul(0.28F))}, new float[]{0.045F, 0.03F, 0.002F}, 6, 1F, 2F, 0F, G.fill(3, 0xFFFFFFFF));
                // hoja: sale de la punta de abajo hacia atrás y se curva hacia abajo, ancha y con filo rojo brillante
                V3 hub = bot;
                V3 bdir = V3.of(0, -0.15F, -1F).norm();
                Mesh bm = c.sc.solid("scythe_blade"), bg = c.sc.glow("scythe_blade_g");
                G.blade(bm, hub, bdir, V3.Y, 1.45F, 0.42F, 1.15F, V3.Y.neg(), 12, G.LANCE, new float[]{1F, 0F, 0F, 1F}, 0xFFFFFFFF, 0xFFFFFFFF, 0.03F);
                G.blade(bg, hub, bdir, V3.Y, 1.45F, 0.42F, 1.15F, V3.Y.neg(), 12, G.LANCE, new float[]{1F, 0F, 0F, 1F}, Col.alpha(0xFFFF2030, 0.6F * pulse + 0.3F), Col.alpha(0xFFFF4050, 0.95F), 0.03F);
                // contrafilo corto hacia delante
                G.blade(bm, hub, V3.of(0, 0.25F, 1F).norm(), V3.Y, 0.32F, 0.13F, 0.5F, V3.Y, 4, G.LANCE, new float[]{1F, 0F, 0F, 1F}, 0xFFFFFFFF, 0xFFFFFFFF, 0.02F);
                // tela rota que cuelga de la unión y flamea
                for (int k = 0; k < 2; k++) {
                    int tn = 8;
                    V3[] cl = new V3[tn];
                    float[] w = new float[tn];
                    int[] cols = new int[tn];
                    for (int i = 0; i < tn; i++) {
                        float f = i / (tn - 1F);
                        cl[i] = hub.add(0.02F * k, 0.08F - f * 0.55F, 0.05F + 0.02F * k).add(V3.of(c.vl, 0, c.vf).mul(-f * 3F)).add(0.06F * f * (float) Math.sin(c.t * 0.25F - f * 4F + k * 2), 0, 0.04F * f * (float) Math.cos(c.t * 0.2F + k));
                        w[i] = 0.09F * (1F - 0.5F * f);
                        cols[i] = 0xFFFFFFFF;
                    }
                    G.ribbon(c.sc.solid("cloth"), cl, w, c.camF, G.FULL, cols);
                }
                G.sprite(c.sc.glow("glow"), hub.add(bdir.mul(0.5F)).add(0, -0.25F, 0), c.camR, c.camU, c.camF, 0.9F, 0.9F, 0, G.FULL, Col.alpha(0xFFFF1020, 0.2F + 0.15F * pulse + (c.carrying ? 0.2F : 0F)));
                // gotas de energía que caen del filo
                for (int k = 0; k < 5; k++) {
                    float life = (c.t * 0.02F + k * 0.2F) % 1F;
                    V3 p = hub.add(bdir.mul(0.25F + 0.22F * k)).add(0, -0.3F - 0.05F * k - life * 0.6F, 0);
                    G.sprite(c.sc.glow("spark"), p, c.camR, c.camU, c.camF, 0.05F, 0.07F, 0, G.FULL, Col.alpha(0xFFFF2A2A, 1F - life));
                }
            }

            private float[] scale(float[] r, float k) { float[] o = new float[r.length]; for (int i = 0; i < r.length; i++) o[i] = r[i] * k; return o; }
        };
    }
}
