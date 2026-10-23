package com.tunombre.cosmeticos.geo.cos;

import com.tunombre.cosmeticos.geo.*;

/**
 * Cosméticos nuevos: auras (6-7, sangre, glitch, espíritus, pétalos, cenizas, cadenas, nube de tormenta, luz divina) y de cabeza
 * (aureola rota, tercer ojo, corona de Mortifilia). Las auras van en el hueso ROOT: siguen la posición y el giro del jugador pero no las
 * inclinaciones de las animaciones, así no se desfasan con Epic Fight. Origen en la cadera: pies en y = -0.75, cabeza hasta y ≈ 1.1.
 */
public final class Extra {
    private Extra() { }

    private abstract static class Aura implements Cosm {
        @Override public Bone bone() { return Bone.ROOT; }
    }

    private abstract static class Head implements Cosm {
        @Override public Bone bone() { return Bone.HEAD; }
    }

    private static float h(int a, int b) { return Noise.hash(a, b); }

    private static V3 wind(Ctx c, float k) { return V3.of(-c.vl * k, -c.vy * k * 0.5F, -c.vf * k); }

    private static float pop(float life) {
        // aparece con un rebote (pasa de tamaño y vuelve) y se apaga al final
        float in = Math.min(1F, life / 0.18F);
        float over = in < 1F ? (float) Math.sin(in * Math.PI * 0.5F) * 1.25F : 1F + 0.25F * (float) Math.exp(-(life - 0.18F) * 18F) * (float) Math.cos((life - 0.18F) * 40F);
        float out = life > 0.75F ? 1F - (life - 0.75F) / 0.25F : 1F;
        return Math.max(0F, over * out);
    }

    // ------------------------------------------------------------------ 6 7 ----
    public static Cosm sixSeven() {
        return new Aura() {
            @Override
            public void build(Ctx c) {
                Mesh g = c.sc.glow("num67");
                // los dos números grandes a la altura de las manos, subiendo y bajando alternados (el gesto del meme)
                float beat = c.t * 0.32F;
                for (int s = -1; s <= 1; s += 2) {
                    float y = 0.35F + 0.2F * (float) Math.sin(beat + (s > 0 ? Math.PI : 0));
                    V3 p = V3.of(s * 0.62F, y, 0.18F);
                    int col = Col.hsv(0.12F + 0.05F * (float) Math.sin(c.t * 0.03F + s), 0.55F, 1F, 1F);
                    float sc = 0.42F + 0.05F * (float) Math.sin(beat * 2F);
                    G.sprite(g, p, c.camR, c.camU, c.camF, sc, sc, 0.15F * (float) Math.sin(beat + s), G.cell(4, 1, s < 0 ? 0 : 1, 0), Col.alpha(col, 0.95F));
                    G.sprite(c.sc.glow("glow"), p, c.camR, c.camU, c.camF, sc * 1.9F, sc * 1.9F, 0, G.FULL, Col.alpha(col, 0.28F));
                }
                // un montón de 6 y 7 que salen del jugador, suben girando y explotan en destellos
                int n = 22;
                for (int k = 0; k < n; k++) {
                    float spd = 0.010F + 0.008F * h(k, 1);
                    float life = (c.t * spd + h(k, 2)) % 1F;
                    int cycle = (int) Math.floor(c.t * spd + h(k, 2));
                    float a = h(k * 31 + cycle, 3) * 6.2832F;
                    float r = 0.35F + 0.65F * h(k * 17 + cycle, 4);
                    float y = -0.6F + 0.4F * h(k * 13 + cycle, 5) + life * (1.6F + 0.8F * h(k, 6));
                    V3 p = V3.of(Math.cos(a + life * 1.4F) * r, y, Math.sin(a + life * 1.4F) * r).add(wind(c, 3F * life));
                    float s = (0.16F + 0.18F * h(k * 7 + cycle, 7)) * pop(life);
                    if (s <= 0.01F) continue;
                    int which = h(k * 3 + cycle, 8) < 0.12F ? 2 : (k + cycle) % 2;
                    int col = Col.hsv(0.92F + 0.24F * h(k * 5 + cycle, 9), 0.6F, 1F, 1F);   // de rosa a dorado
                    G.sprite(g, p, c.camR, c.camU, c.camF, which == 2 ? s * 1.5F : s, s, (h(k, 10) - 0.5F) * 1.2F * life + 0.3F * (float) Math.sin(c.t * 0.2F + k), G.cell(4, 1, which, 0), Col.alpha(col, 0.95F));
                    if (life > 0.72F) {
                        float f = (life - 0.72F) / 0.28F;
                        G.sprite(g, p, c.camR, c.camU, c.camF, s * (1F + 2.5F * f), s * (1F + 2.5F * f), f * 2F, G.cell(4, 1, 3, 0), Col.alpha(col, 1F - f));
                    }
                }
                // "67" gigante que late sobre la cabeza
                float big = 0.5F + 0.5F * (float) Math.sin(c.t * 0.16F);
                V3 top = V3.of(0, 1.55F + 0.06F * (float) Math.sin(c.t * 0.11F), 0);
                int gold = Col.mix(0xFFFFD040, 0xFFFF60C0, 0.5F + 0.5F * (float) Math.sin(c.t * 0.05F));
                G.sprite(g, top, c.camR, c.camU, c.camF, 0.75F + 0.12F * big, 0.5F + 0.08F * big, 0.06F * (float) Math.sin(c.t * 0.13F), G.cell(4, 1, 2, 0), Col.alpha(gold, 0.95F));
                G.sprite(c.sc.glow("glow"), top, c.camR, c.camU, c.camF, 1.4F, 1F, 0, G.FULL, Col.alpha(gold, 0.25F + 0.15F * big));
                // aura de fondo
                G.sprite(c.sc.glow("glow"), V3.of(0, 0.15F, 0), c.camR, c.camU, c.camF, 1.9F, 2.6F, 0, G.FULL, Col.alpha(0xFFFFB040, 0.12F + 0.05F * big));
                for (int k = 0; k < 12; k++) {
                    float life = (c.t * 0.025F + h(k, 21)) % 1F;
                    float a = h(k, 22) * 6.2832F + c.t * 0.02F;
                    V3 p = V3.of(Math.cos(a) * 0.55F, -0.7F + life * 2.1F, Math.sin(a) * 0.55F);
                    G.sprite(c.sc.glow("star"), p, c.camR, c.camU, c.camF, 0.09F, 0.09F, c.t * 0.05F, G.FULL, Col.alpha(0xFFFFF0A0, (float) Math.sin(Math.PI * life)));
                }
            }
        };
    }

    // ------------------------------------------------------------------ sangre ----
    public static Cosm blood() {
        return new Aura() {
            @Override
            public void build(Ctx c) {
                Mesh sm = c.sc.solid("smoke_sheet");
                for (int k = 0; k < 12; k++) {
                    float life = (c.t * (0.007F + 0.005F * h(k, 1)) + h(k, 2)) % 1F;
                    float a = h(k, 3) * 6.2832F + life * 1.5F;
                    float r = 0.28F + 0.3F * life;
                    V3 p = V3.of(Math.cos(a) * r, -0.7F + life * 1.2F, Math.sin(a) * r).add(wind(c, 3F * life));
                    float s = 0.35F + 0.5F * life;
                    G.sprite(sm, p, c.camR, c.camU, c.camF, s, s, h(k, 6) * 6F, G.frame(4, k), Col.alpha(0xFF5A0008, (float) Math.sin(Math.PI * life) * 0.5F));
                }
                // gotas que caen del cuerpo
                Mesh g = c.sc.glow("glow");
                for (int k = 0; k < 14; k++) {
                    float life = (c.t * (0.03F + 0.02F * h(k, 11)) + h(k, 12)) % 1F;
                    float a = h(k, 13) * 6.2832F;
                    float r = 0.18F + 0.1F * h(k, 14);
                    float y0 = 0.9F * h(k, 15);
                    V3 p = V3.of(Math.cos(a) * r, y0 - life * life * (y0 + 0.75F), Math.sin(a) * r);
                    G.sprite(g, p, c.camR, c.camU, c.camF, 0.045F, 0.075F + 0.05F * life, 0, G.FULL, Col.alpha(0xFFC00010, 0.95F));
                }
                // charco que late bajo los pies
                float pul = 0.85F + 0.15F * (float) Math.sin(c.t * 0.05F);
                G.plane(c.sc.solid("glow"), V3.of(0, -0.735F, 0), V3.X.mul(0.75F * pul), V3.Z.mul(0.75F * pul), G.FULL, Col.alpha(0xFF6A0006, 0.85F));
                G.plane(c.sc.glow("rune_ring"), V3.of(0, -0.73F, 0), V3.of(Math.cos(c.t * 0.01F), 0, Math.sin(c.t * 0.01F)).mul(0.8F),
                        V3.of(-Math.sin(c.t * 0.01F), 0, Math.cos(c.t * 0.01F)).mul(0.8F), G.FULL, Col.alpha(0xFFFF1020, 0.35F));
            }
        };
    }

    // ------------------------------------------------------------------ glitch ----
    public static Cosm glitch() {
        return new Aura() {
            @Override
            public void build(Ctx c) {
                int step = (int) (c.t / 1.6F);
                Mesh g = c.sc.glow("glitch");
                for (int k = 0; k < 26; k++) {
                    int sd = step * 131 + k * 7 + (int) (c.seed * 997);
                    if (h(sd, 1) < 0.45F) continue;
                    float a = h(sd, 2) * 6.2832F, r = 0.3F + 0.35F * h(sd, 3), y = -0.7F + 1.9F * h(sd, 4);
                    V3 p = V3.of(Math.cos(a) * r, y, Math.sin(a) * r);
                    float w = 0.04F + 0.28F * h(sd, 5) * h(sd, 5), hh = 0.02F + 0.09F * h(sd, 6);
                    V3 off = c.camR.mul(0.025F);
                    G.sprite(g, p.add(off), c.camR, c.camU, c.camF, w, hh, 0, G.FULL, Col.alpha(0xFFFF2060, 0.75F));
                    G.sprite(g, p.sub(off), c.camR, c.camU, c.camF, w, hh, 0, G.FULL, Col.alpha(0xFF20E8FF, 0.75F));
                    if (h(sd, 7) > 0.8F) G.sprite(g, p, c.camR, c.camU, c.camF, w * 0.6F, hh * 0.6F, 0, G.FULL, Col.alpha(0xFFFFFFFF, 0.9F));
                }
                // barridos horizontales que cruzan el cuerpo de vez en cuando
                if (h(step, 9) > 0.7F) {
                    float y = -0.6F + 1.8F * h(step, 10);
                    G.sprite(g, V3.of(0, y, 0), c.camR, c.camU, c.camF, 1.3F, 0.025F, 0, G.FULL, Col.alpha(0xFFFFFFFF, 0.55F));
                    G.sprite(g, V3.of(0, y + 0.05F, 0), c.camR, c.camU, c.camF, 0.9F, 0.012F, 0, G.FULL, Col.alpha(0xFF20E8FF, 0.6F));
                }
            }
        };
    }

    // ------------------------------------------------------------------ espíritus ----
    public static Cosm spirits() {
        return new Aura() {
            @Override
            public void build(Ctx c) {
                int n = 5;
                for (int k = 0; k < n; k++) {
                    float sp = 0.016F + 0.006F * h(k, 1);
                    float tt = c.t * sp + k * 6.2832F / n;
                    float R = 0.85F + 0.12F * (float) Math.sin(c.t * 0.02F + k);
                    float y = 0.1F + 0.55F * (float) Math.sin(c.t * 0.013F + k * 1.9F);
                    V3 p = V3.of(Math.cos(tt) * R, y, Math.sin(tt) * R);
                    int col = Col.hsv(0.5F + 0.04F * h(k, 3), 0.25F, 1F, 1F);
                    float fl = 0.75F + 0.25F * (float) Math.sin(c.t * 0.3F + k * 4);
                    G.sprite(c.sc.glow("ghost"), p, c.camR, c.camU, c.camF, 0.38F, 0.38F, 0.25F * (float) Math.sin(c.t * 0.05F + k), G.FULL, Col.alpha(col, 0.6F * fl));
                    G.sprite(c.sc.glow("glow"), p, c.camR, c.camU, c.camF, 0.7F, 0.7F, 0, G.FULL, Col.alpha(col, 0.18F));
                    // estela de bruma detrás
                    int tn = 10;
                    V3[] pts = new V3[tn];
                    float[] w = new float[tn];
                    int[] cols = new int[tn];
                    for (int i = 0; i < tn; i++) {
                        float f = i / (tn - 1F);
                        float t2 = (c.t - f * 14F) * sp + k * 6.2832F / n;
                        float y2 = 0.1F + 0.55F * (float) Math.sin((c.t - f * 14F) * 0.013F + k * 1.9F) - 0.08F;
                        pts[i] = V3.of(Math.cos(t2) * R, y2, Math.sin(t2) * R);
                        w[i] = 0.2F * (1F - f);
                        cols[i] = Col.alpha(col, 0.35F * (1F - f));
                    }
                    G.ribbon(c.sc.glow("trail"), pts, w, c.camF, G.FULL, cols);
                }
            }
        };
    }

    // ------------------------------------------------------------------ pétalos de Mortifilia ----
    public static Cosm petals() {
        return new Aura() {
            @Override
            public void build(Ctx c) {
                Mesh m = c.sc.solid("petal_mort");
                for (int k = 0; k < 26; k++) {
                    float life = (c.t * (0.006F + 0.004F * h(k, 1)) + h(k, 2)) % 1F;
                    float a = h(k, 3) * 6.2832F + life * 5F;
                    float r = 0.4F + 0.45F * h(k, 4) + 0.15F * life;
                    float y = 1.4F - life * 2.2F;
                    V3 p = V3.of(Math.cos(a) * r, y, Math.sin(a) * r).add(wind(c, 4F * life));
                    float spin = c.t * (0.05F + 0.05F * h(k, 5)) + k;
                    V3 dir = V3.of(Math.cos(spin), 0.4F * Math.sin(spin * 1.3F), Math.sin(spin)).norm();
                    V3 side = dir.cross(V3.Y).norm();
                    float al = Math.min(1F, Math.min(life * 6F, (1F - life) * 6F));
                    int col = Col.alpha(Col.shade(0xFFFFFFFF, 0.85F + 0.15F * h(k, 6)), al);
                    G.blade(m, p, dir, side, 0.12F, 0.07F, 0.6F, V3.Y.neg(), 2, G.FLAT, new float[]{(k % 4) * 0.25F, 0F, (k % 4) * 0.25F + 0.25F, 1F}, col, col, 0.004F);
                }
                for (int k = 0; k < 10; k++) {
                    float life = (c.t * 0.02F + h(k, 31)) % 1F;
                    float a = h(k, 32) * 6.2832F;
                    V3 p = V3.of(Math.cos(a) * 0.5F, -0.7F + life * 1.6F, Math.sin(a) * 0.5F);
                    G.sprite(c.sc.glow("spark"), p, c.camR, c.camU, c.camF, 0.05F, 0.05F, 0, G.FULL, Col.alpha(0xFFE080FF, (float) Math.sin(Math.PI * life)));
                }
            }
        };
    }

    // ------------------------------------------------------------------ cenizas y brasas ----
    public static Cosm ashes() {
        return new Aura() {
            @Override
            public void build(Ctx c) {
                Mesh a = c.sc.solid("ash");
                for (int k = 0; k < 30; k++) {
                    float life = (c.t * (0.005F + 0.004F * h(k, 1)) + h(k, 2)) % 1F;
                    float x = (h(k, 3) - 0.5F) * 1.8F + 0.15F * (float) Math.sin(c.t * 0.03F + k);
                    float z = (h(k, 4) - 0.5F) * 1.8F + 0.15F * (float) Math.cos(c.t * 0.027F + k);
                    V3 p = V3.of(x, 1.5F - life * 2.3F, z).add(wind(c, 3F));
                    float al = Math.min(1F, Math.min(life * 5F, (1F - life) * 5F));
                    float s = 0.05F + 0.05F * h(k, 5);
                    G.sprite(a, p, c.camR, c.camU, c.camF, s, s, c.t * 0.04F * (h(k, 6) - 0.5F) * 4, G.frame(4, k), Col.alpha(0xFF4A4646, al * 0.9F));
                }
                Mesh g = c.sc.glow("spark");
                for (int k = 0; k < 16; k++) {
                    float life = (c.t * (0.022F + 0.015F * h(k, 11)) + h(k, 12)) % 1F;
                    float an = h(k, 13) * 6.2832F;
                    V3 p = V3.of(Math.cos(an) * 0.3F, -0.7F + life * 2.2F, Math.sin(an) * 0.3F).add(0.08F * (float) Math.sin(c.t * 0.2F + k * 3), 0, 0.08F * (float) Math.cos(c.t * 0.17F + k)).add(wind(c, 5F * life));
                    float al = (float) Math.sin(Math.PI * life);
                    G.sprite(g, p, c.camR, c.camU, c.camF, 0.05F, 0.05F, 0, G.FULL, Col.alpha(Col.mix(0xFFFFD080, 0xFFFF3000, life), al));
                }
                Mesh sm = c.sc.solid("smoke_sheet");
                for (int k = 0; k < 6; k++) {
                    float life = (c.t * 0.006F + k / 6F) % 1F;
                    float an = k * 1.05F + c.t * 0.004F;
                    V3 p = V3.of(Math.cos(an) * 0.35F, -0.65F + life * 1.6F, Math.sin(an) * 0.35F).add(wind(c, 3F * life));
                    G.sprite(sm, p, c.camR, c.camU, c.camF, 0.5F + 0.5F * life, 0.5F + 0.5F * life, k, G.frame(4, k), Col.alpha(0xFF201C1C, 0.35F * (float) Math.sin(Math.PI * life)));
                }
            }
        };
    }

    // ------------------------------------------------------------------ cadenas espectrales ----
    public static Cosm chains() {
        return new Aura() {
            @Override
            public void build(Ctx c) {
                float[][] rings = {{-0.35F, 0.7F, 0.45F, 0.010F}, {0.25F, 0.62F, -0.5F, -0.013F}, {0.75F, 0.55F, 0.35F, 0.016F}};
                Mesh m = c.sc.solid("chain"), g = c.sc.glow("chain");
                for (int ri = 0; ri < rings.length; ri++) {
                    float[] r = rings[ri];
                    V3 ax = V3.X.rot(V3.Z, r[2]), az = V3.Z.rot(V3.X, r[2] * 0.6F);
                    int links = 34;
                    float rot = c.t * r[3];
                    for (int i = 0; i < links; i++) {
                        float a = rot + 6.2832F * i / links;
                        V3 rad = ax.mul((float) Math.cos(a)).add(az.mul((float) Math.sin(a)));
                        V3 tan = ax.mul((float) -Math.sin(a)).add(az.mul((float) Math.cos(a))).norm();
                        V3 ctr = V3.of(0, r[0] + 0.03F * (float) Math.sin(c.t * 0.04F + ri), 0).add(rad.mul(r[1]));
                        V3 wdir = (i % 2 == 0) ? rad.norm() : tan.cross(rad).norm();
                        V3 hy = tan.mul(0.07F), wx = wdir.mul(0.04F);
                        G.plane(m, ctr, wx, hy, G.FULL, 0xFFB8C0D0);
                        G.plane(g, ctr, wx.mul(1.15F), hy.mul(1.1F), G.FULL, Col.alpha(0xFF80A8FF, 0.25F + 0.2F * (float) Math.sin(c.t * 0.1F - i * 0.4F)));
                    }
                }
                // un candado que cuelga y gira
                V3 lock = V3.of(0.6F * (float) Math.cos(c.t * 0.01F), -0.25F, 0.6F * (float) Math.sin(c.t * 0.01F));
                G.sprite(c.sc.glow("glow"), lock, c.camR, c.camU, c.camF, 0.35F, 0.35F, 0, G.FULL, Col.alpha(0xFF80A8FF, 0.3F));
            }
        };
    }

    // ------------------------------------------------------------------ nube de tormenta personal ----
    public static Cosm storm() {
        return new Aura() {
            @Override
            public void build(Ctx c) {
                V3 base = V3.of(0, 1.62F + 0.04F * (float) Math.sin(c.t * 0.03F), 0);
                Mesh sm = c.sc.solid("smoke_sheet");
                for (int k = 0; k < 14; k++) {
                    float a = h(k, 1) * 6.2832F, r = 0.15F + 0.45F * h(k, 2);
                    V3 p = base.add(V3.of(Math.cos(a) * r, 0.12F * (h(k, 3) - 0.5F), Math.sin(a) * r * 0.8F));
                    float s = 0.45F + 0.25F * h(k, 4) + 0.04F * (float) Math.sin(c.t * 0.05F + k);
                    G.sprite(sm, p, c.camR, c.camU, c.camF, s, s * 0.8F, c.t * 0.004F + k, G.frame(4, k), Col.alpha(Col.shade(0xFF505868, 0.7F + 0.3F * h(k, 5)), 0.92F));
                }
                // lluvia
                for (int k = 0; k < 18; k++) {
                    float life = (c.t * (0.07F + 0.03F * h(k, 11)) + h(k, 12)) % 1F;
                    float a = h(k, 13) * 6.2832F, r = 0.45F * h(k, 14);
                    V3 top = base.add(V3.of(Math.cos(a) * r, -0.1F - life * 2.25F, Math.sin(a) * r));
                    if (top.y < -0.74F) continue;
                    V3[] pts = {top, top.add(0, -0.12F, 0)};
                    G.ribbon(c.sc.glow("trail"), pts, new float[]{0.012F, 0.006F}, c.camF, G.FULL, new int[]{Col.alpha(0xFF9AC8FF, 0.7F), Col.alpha(0xFF9AC8FF, 0.2F)});
                }
                // relámpago de vez en cuando
                int step = (int) (c.t / 3F);
                if (h(step + (int) (c.seed * 500), 7) > 0.82F) {
                    int sd = step * 17;
                    V3 from = base.add((h(sd, 1) - 0.5F) * 0.4F, -0.1F, (h(sd, 2) - 0.5F) * 0.3F);
                    V3 to = V3.of((h(sd, 3) - 0.5F) * 0.6F, -0.74F, (h(sd, 4) - 0.5F) * 0.6F);
                    int n = 9;
                    V3[] pts = new V3[n];
                    float[] w = new float[n];
                    for (int i = 0; i < n; i++) {
                        float f = i / (n - 1F), j = (i == 0 || i == n - 1) ? 0 : 1;
                        pts[i] = from.lerp(to, f).add(c.camR.mul((h(sd, 20 + i) - 0.5F) * 0.25F * j));
                        w[i] = 0.05F * (1F - 0.5F * f);
                    }
                    G.ribbon(c.sc.glow("bolt"), pts, w, c.camF, G.FULL, G.fill(n, 0xFFE8F4FF));
                    G.sprite(c.sc.glow("glow"), base, c.camR, c.camU, c.camF, 1.6F, 1.0F, 0, G.FULL, Col.alpha(0xFFB0D8FF, 0.5F));
                }
            }
        };
    }

    // ------------------------------------------------------------------ luz divina ----
    public static Cosm divine() {
        return new Aura() {
            @Override
            public void build(Ctx c) {
                V3 ctr = V3.of(0, 1.0F, -0.32F);
                int n = 16;
                for (int k = 0; k < n; k++) {
                    float a = c.t * 0.004F + 6.2832F * k / n;
                    float len = 0.9F + 0.45F * (0.5F + 0.5F * (float) Math.sin(c.t * 0.03F + k * 1.7F));
                    V3 d = V3.of(Math.cos(a), Math.sin(a), 0);
                    V3 side = V3.of(-Math.sin(a), Math.cos(a), 0);
                    V3 mid = ctr.add(d.mul(len * 0.5F));
                    float w = 0.06F + 0.04F * h(k, 1);
                    G.plane(c.sc.glow("ray"), mid, side.mul(w), d.mul(len * 0.5F).neg(), G.FULL, Col.alpha(0xFFFFE6A0, 0.5F));
                }
                G.sprite(c.sc.glow("glow"), ctr, c.camR, c.camU, c.camF, 1.2F, 1.2F, 0, G.FULL, Col.alpha(0xFFFFF0C0, 0.45F));
                G.torus(c.sc.glow("halo_g"), ctr, V3.X, V3.Y, 0.42F, 0.02F, 40, 6, 6F, Col.alpha(0xFFFFE08A, 0.7F));
                for (int k = 0; k < 14; k++) {
                    float life = (c.t * 0.01F + h(k, 5)) % 1F;
                    V3 p = V3.of((h(k, 6) - 0.5F) * 1.4F, -0.7F + life * 2.4F, (h(k, 7) - 0.5F) * 1.0F);
                    G.sprite(c.sc.glow("spark"), p, c.camR, c.camU, c.camF, 0.035F, 0.035F, 0, G.FULL, Col.alpha(0xFFFFF4C8, (float) Math.sin(Math.PI * life)));
                }
            }
        };
    }

    // ------------------------------------------------------------------ cabeza ----
    public static Cosm brokenHalo() {
        return new Head() {
            @Override
            public void build(Ctx c) {
                V3 ctr = V3.of(0, 0.86F + 0.025F * (float) Math.sin(c.t * 0.06F), -0.03F);
                float tilt = 0.22F + 0.05F * (float) Math.sin(c.t * 0.03F);
                V3 ax = V3.X.rot(V3.Z, tilt), ay = V3.Z.rot(V3.Z, tilt);
                float[][] arcs = {{0.0F, 1.6F}, {1.85F, 3.3F}, {3.55F, 5.2F}, {5.45F, 6.05F}};
                for (int i = 0; i < arcs.length; i++) {
                    float drift = 0.02F * (float) Math.sin(c.t * 0.05F + i * 2F);
                    int seg = 14;
                    V3[] pts = new V3[seg + 1];
                    for (int s = 0; s <= seg; s++) {
                        float a = arcs[i][0] + (arcs[i][1] - arcs[i][0]) * s / seg;
                        pts[s] = ctr.add(ax.mul((float) Math.cos(a) * (0.31F + drift))).add(ay.mul((float) Math.sin(a) * (0.31F + drift))).add(0, drift, 0);
                    }
                    G.tube(c.sc.solid("halo"), pts, G.ffill(seg + 1, 0.024F), 6, 1F, 6F, 0F, G.fill(seg + 1, 0xFF6A6060));
                    G.tube(c.sc.glow("halo_g"), pts, G.ffill(seg + 1, 0.034F), 6, 1F, 6F, 0F, G.fill(seg + 1, Col.alpha(0xFFFF2A2A, 0.45F + 0.3F * (float) Math.sin(c.t * 0.12F + i))));
                }
                // esquirlas que se desprenden de las grietas
                for (int k = 0; k < 6; k++) {
                    float life = (c.t * 0.012F + k / 6F) % 1F;
                    float a = 1.72F + k * 1.03F;
                    V3 p = ctr.add(ax.mul((float) Math.cos(a) * 0.31F)).add(ay.mul((float) Math.sin(a) * 0.31F)).add(0, -life * 0.35F, 0);
                    G.sprite(c.sc.glow("spark"), p, c.camR, c.camU, c.camF, 0.05F, 0.05F, c.t * 0.1F, G.FULL, Col.alpha(0xFFFF4040, 1F - life));
                }
            }
        };
    }

    public static Cosm thirdEye() {
        return new Head() {
            @Override
            public void build(Ctx c) {
                V3 at = V3.of(0, 0.37F, 0.262F);
                float blink = (c.t * 0.006F + c.seed) % 1F;
                float lid = blink > 0.96F ? 0.15F : 1F;
                // mira un poco hacia donde está la cámara, con temblor
                V3 look = c.camF.neg().lerp(V3.Z, 0.55F).norm();
                V3 r = look.cross(V3.Y).norm().neg(), u = r.cross(look).norm().neg();
                G.sphere(c.sc.solid("eye"), at, r, u.mul(lid * 0.75F), look, 0.06F, 8, 12, Col.WHITE);
                G.sprite(c.sc.glow("glow"), at.add(0, 0, 0.02F), c.camR, c.camU, c.camF, 0.25F, 0.25F, 0, G.FULL, Col.alpha(0xFFB040FF, 0.45F + 0.2F * (float) Math.sin(c.t * 0.08F)));
                // venas que se abren desde el ojo
                for (int k = 0; k < 6; k++) {
                    float a = k * 1.047F + 0.3F;
                    V3[] pts = new V3[4];
                    for (int i = 0; i < 4; i++) pts[i] = at.add((float) Math.cos(a) * (0.06F + 0.03F * i), (float) Math.sin(a) * (0.05F + 0.025F * i), 0.002F);
                    G.ribbon(c.sc.glow("trail"), pts, new float[]{0.012F, 0.009F, 0.006F, 0.002F}, V3.Z.neg(), G.FULL, G.ramp(4, Col.alpha(0xFFB040FF, 0.8F), Col.alpha(0xFFB040FF, 0F)));
                }
            }
        };
    }

    public static Cosm mortCrown() {
        return new Head() {
            @Override
            public void build(Ctx c) {
                float y = 0.6F + 0.02F * (float) Math.sin(c.t * 0.05F);
                int n = 7;
                for (int k = 0; k < n; k++) {
                    float a = c.t * 0.008F + 6.2832F * k / n;
                    V3 b = V3.of(Math.cos(a) * 0.3F, y, Math.sin(a) * 0.3F);
                    float open = 0.55F + 0.35F * (float) Math.sin(c.t * 0.04F + k * 1.3F);
                    V3 up = V3.of(Math.cos(a) * 0.25F, 1F, Math.sin(a) * 0.25F).norm();
                    V3 s = up.cross(V3.of(Math.cos(a), 0, Math.sin(a))).norm(), u2 = s.cross(up).norm();
                    for (int j = 0; j < 5; j++) {
                        float pa = j * 1.2566F;
                        V3 dir = up.add(s.mul((float) Math.cos(pa) * open)).add(u2.mul((float) Math.sin(pa) * open)).norm();
                        V3 side = s.mul((float) -Math.sin(pa)).add(u2.mul((float) Math.cos(pa))).norm();
                        G.blade(c.sc.solid("petal_mort"), b, dir, side, 0.11F, 0.065F, 0.5F, up.neg(), 3, G.FLAT, new float[]{(j % 4) * 0.25F, 0F, (j % 4) * 0.25F + 0.25F, 1F}, 0xFFE0D0E8, 0xFFFFFFFF, 0.003F);
                    }
                    G.sprite(c.sc.glow("glow"), b.add(up.mul(0.03F)), c.camR, c.camU, c.camF, 0.12F, 0.12F, 0, G.FULL, Col.alpha(0xFFD060FF, 0.7F));
                }
                // tallo que une las flores
                int seg = 42;
                V3[] pts = new V3[seg + 1];
                for (int i = 0; i <= seg; i++) {
                    float a = 6.2832F * i / seg;
                    pts[i] = V3.of(Math.cos(a) * 0.3F, y - 0.02F + 0.012F * (float) Math.sin(a * 7), Math.sin(a) * 0.3F);
                }
                G.tube(c.sc.solid("vine"), pts, G.ffill(seg + 1, 0.012F), 5, 1F, 5F, 0F, G.fill(seg + 1, 0xFFFFFFFF));
                for (int k = 0; k < 5; k++) {
                    float life = (c.t * 0.015F + k * 0.2F) % 1F;
                    float a = k * 1.25F;
                    G.sprite(c.sc.glow("spark"), V3.of(Math.cos(a) * 0.32F, y + life * 0.4F, Math.sin(a) * 0.32F), c.camR, c.camU, c.camF, 0.04F, 0.04F, 0, G.FULL, Col.alpha(0xFFE080FF, (float) Math.sin(Math.PI * life)));
                }
            }
        };
    }
}
