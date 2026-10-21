package com.tunombre.cosmeticos.geo.cos;

import com.tunombre.cosmeticos.geo.*;

/** tentaculos y cola: cadenas de segmentos con muelles que reaccionan al movimiento, cuelgan de la espalda (o la cadera) */
public final class Tentacles implements Cosm {
    /** raiz (un lado, se refleja), direccion inicial, longitud, curvatura en cabeceo (negativo = se dobla hacia abajo), curvatura lateral, fase */
    public record Spec(V3 root, V3 dir, float len, float curlP, float curlY, float phase) { }

    private final String id, tex, glowTex;
    private final Spec[] specs;
    private final boolean mirror;
    private final int segs;
    private final float r0, wiggle;
    private final int tint, glowCol;
    private final int tip; // 0 nada, 1 flor, 2 pala (cola)
    private final Bone bone;
    private final float gravity;

    public Tentacles(String id, Bone bone, String tex, String glowTex, Spec[] specs, boolean mirror, int segs, float r0, float wiggle, int tint, int glowCol, int tip, float gravity) {
        this.id = id; this.bone = bone; this.tex = tex; this.glowTex = glowTex; this.specs = specs; this.mirror = mirror; this.segs = segs; this.r0 = r0;
        this.wiggle = wiggle; this.tint = tint; this.glowCol = glowCol; this.tip = tip; this.gravity = gravity;
    }

    @Override public Bone bone() { return bone; }

    private static final class State {
        Chain[] ch;
        float ext = 1F;
        V3 drag = V3.ZERO;
    }

    @Override
    public void build(Ctx c) {
        State st = c.state(id, State::new);
        int total = specs.length * (mirror ? 2 : 1);
        if (st.ch == null) { st.ch = new Chain[total]; for (int i = 0; i < total; i++) { st.ch[i] = new Chain(segs); st.ch[i].lim = 0.2F; } }
        float target = c.crouch ? 0.5F : c.glide ? 1.1F : 1F;
        st.ext += (target - st.ext) * (1F - (float) Math.exp(-c.dt * 0.12F));
        // arrastre: opuesto a la velocidad del cuerpo + inercia al girar + gravedad
        V3 want = V3.of(-c.vl * 5.5F - c.turnAcc * 0.03F, -c.vy * 4F - gravity, -c.vf * 5.5F);
        st.drag = st.drag.lerp(want, 1F - (float) Math.exp(-c.dt * 0.25F));
        int idx = 0;
        for (int sd = (mirror ? -1 : 1); sd <= 1; sd += 2) {
            for (Spec sp : specs) { one(c, st, st.ch[idx], sp, sd, idx); idx++; }
            if (!mirror) break;
        }
        Mesh gm = glowTex != null ? c.sc.glow("glow") : null;
    }

    private void one(Ctx c, State st, Chain ch, Spec sp, int side, int index) {
        V3 root = new V3(sp.root().x * side, sp.root().y, sp.root().z);
        V3 d0 = new V3(sp.dir().x * side, sp.dir().y, sp.dir().z).norm();
        float segLen = sp.len() * st.ext / segs;
        // 1) marco con el estado actual para saber hacia donde empuja el arrastre en cada segmento
        float[] tx = new float[segs], tz = new float[segs];
        Frame f = new Frame(root, d0, V3.Y);
        for (int i = 0; i < segs; i++) {
            float w = (i + 1F) / segs;
            float wave = wiggle * (0.3F + w) * (float) Math.sin(c.t * 0.075F - i * 0.55F + sp.phase() + side);
            float wave2 = wiggle * 0.7F * (0.2F + w) * (float) Math.sin(c.t * 0.052F - i * 0.4F + sp.phase() * 1.7F);
            float dragYaw = st.drag.dot(f.s) * 0.9F * w, dragPitch = st.drag.dot(f.u) * 0.9F * w;
            tx[i] = wave + dragYaw;
            tz[i] = wave2 + dragPitch;
            f.turn(ch.bx[i] + sp.curlY(), ch.bz[i] + sp.curlP()).step(segLen);
        }
        ch.stepTo(c.dt, tx, tz, 0.16F, 0.30F, 0.35F);
        // 2) geometria final
        V3[] pts = new V3[segs + 1];
        f = new Frame(root, d0, V3.Y);
        pts[0] = root;
        for (int i = 0; i < segs; i++) {
            f.turn(ch.bx[i] + sp.curlY(), ch.bz[i] + sp.curlP()).step(segLen);
            pts[i + 1] = f.p;
        }
        float[] rad = G.taper(segs + 1, r0, tip == 2 ? r0 * 0.35F : 0.006F, 0.8F);
        // el primer tramo es mas grueso y se funde con la espalda
        rad[0] *= 1.25F;
        int[] cols = G.ramp(segs + 1, Col.shade(tint, 0.7F), tint);
        G.tube(c.sc.solid(tex), pts, rad, 7, 1F, 1.6F, 0F, cols);
        if (glowTex != null) {
            float pulse = 0.6F + 0.4F * (float) Math.sin(c.t * 0.08F + index * 1.7F);
            G.tube(c.sc.glow(glowTex), pts, G.ffill(segs + 1, 0f).length > 0 ? scaleR(rad, 1.02F) : rad, 7, 1F, 1.6F, 0F, G.ramp(segs + 1, Col.alpha(glowCol, 0.5F * pulse), Col.alpha(glowCol, pulse)));
        }
        Frame end = f;
        if (tip == 1) flowerTip(c, end, index);
        else if (tip == 2) spade(c, end, pts, index);
    }

    private static float[] scaleR(float[] r, float k) { float[] o = new float[r.length]; for (int i = 0; i < r.length; i++) o[i] = r[i] * k; return o; }

    private void flowerTip(Ctx c, Frame end, int index) {
        float open = 0.5F + 0.5F * (float) Math.sin(c.t * 0.05F + index * 2.1F);
        for (int k = 0; k < 5; k++) {
            float a = k * 1.2566F + c.t * 0.003F;
            V3 dir = end.t.mul(1F).add(end.s.mul((float) Math.cos(a) * 0.9F * open)).add(end.u.mul((float) Math.sin(a) * 0.9F * open)).norm();
            V3 side = end.s.mul((float) -Math.sin(a)).add(end.u.mul((float) Math.cos(a))).norm();
            G.blade(c.sc.solid("petal_mort"), end.p, dir, side, 0.13F, 0.075F, 0.45F, end.t.neg(), 3, G.FLAT, new float[]{(k % 4) * 0.25F, 0F, (k % 4) * 0.25F + 0.25F, 1F}, Col.shade(tint, 0.8F), Col.WHITE, 0.004F);
        }
        G.sprite(c.sc.glow("glow"), end.p.add(end.t.mul(0.05F)), c.camR, c.camU, c.camF, 0.16F, 0.16F, 0, G.FULL, Col.alpha(glowCol, 0.5F + 0.4F * open));
    }

    private void spade(Ctx c, Frame end, V3[] pts, int index) {
        V3 d = end.t, s1 = end.s, s2 = end.u;
        Mesh m = c.sc.solid("spade");
        G.blade(m, end.p, d, s1, 0.26F, 0.2F, 0.05F, s2, 2, G.BLADE, G.FULL, Col.shade(tint, 0.9F), Col.WHITE, 0.012F);
        G.blade(m, end.p, d, s2, 0.26F, 0.2F, 0.05F, s1, 2, G.BLADE, G.FULL, Col.shade(tint, 0.9F), Col.WHITE, 0.012F);
        G.blade(c.sc.glow("spade_g"), end.p, d, s1, 0.26F, 0.2F, 0.05F, s2, 2, G.BLADE, G.FULL, Col.alpha(glowCol, 0.4F), Col.alpha(glowCol, 0.9F), 0.012F);
        G.blade(c.sc.glow("spade_g"), end.p, d, s2, 0.26F, 0.2F, 0.05F, s1, 2, G.BLADE, G.FULL, Col.alpha(glowCol, 0.4F), Col.alpha(glowCol, 0.9F), 0.012F);
    }
}
