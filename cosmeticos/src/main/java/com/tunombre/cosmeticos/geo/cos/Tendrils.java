package com.tunombre.cosmeticos.geo.cos;

import com.tunombre.cosmeticos.geo.*;

/**
 * Tentáculos (y colas) con la técnica de los de Slenderman: cada uno es una cadena de puntos con inercia de verdad (Verlet) que sigue una
 * forma de reposo viva (arco hacia arriba y afuera desde la espalda, ondas lentas que recorren el tentáculo). Como se calcula en el
 * espacio del hueso, el movimiento del cuerpo entra como fuerzas: al correr, saltar o girar, los tentáculos se quedan atrás y latiguean.
 * No atraviesan el torso. Se dibujan como tubos redondeados (Catmull-Rom) que nacen anchos dentro de la espalda, con bultos que viajan
 * y brillo húmedo según la cámara.
 */
public final class Tendrils implements Cosm {
    /** raíz (lado izquierdo; se refleja), dirección de reposo, largo, arco hacia arriba (+) o caída (-), fase */
    public record T(V3 root, V3 dir, float len, float curl, float phase) { }

    public enum Tip { NONE, FLOWER, SPADE, EYE }

    private final String id, tex, glowTex;
    private final Bone bone;
    private final T[] specs;
    private final boolean mirror, wet;
    private final int segs;
    private final float r0, r1, wave, grav;
    private final int tint, glowCol;
    private final Tip tip;

    public Tendrils(String id, Bone bone, String tex, String glowTex, T[] specs, boolean mirror, int segs, float r0, float r1, float wave, float grav,
                    int tint, int glowCol, Tip tip, boolean wet) {
        this.id = id; this.bone = bone; this.tex = tex; this.glowTex = glowTex; this.specs = specs; this.mirror = mirror; this.segs = segs;
        this.r0 = r0; this.r1 = r1; this.wave = wave; this.grav = grav; this.tint = tint; this.glowCol = glowCol; this.tip = tip; this.wet = wet;
    }

    @Override public Bone bone() { return bone; }

    private static final class State {
        float[][] p, q;   // por cadena: x y z de cada punto (actual y anterior)
        boolean init;
        float ext = 1F;
    }

    @Override
    public void build(Ctx c) {
        int n = specs.length * (mirror ? 2 : 1), m = segs + 1;
        State st = c.state(id, State::new);
        if (st.p == null || st.p.length != n) { st.p = new float[n][m * 3]; st.q = new float[n][m * 3]; st.init = false; }
        float dt = Noise.clamp(c.dt, 0F, 2F);
        float want = c.crouch ? 0.62F : (c.glide || c.fly) ? 1.12F : 1F;
        st.ext += (want - st.ext) * (1F - (float) Math.exp(-dt * 0.1F));
        int idx = 0;
        for (int sd = mirror ? -1 : 1; sd <= 1; sd += 2) {
            for (T sp : specs) { chain(c, st, idx, sp, sd, dt); idx++; }
            if (!mirror) break;
        }
        st.init = true;
    }

    // ------------------------------------------------------------------ simulación ----
    private void chain(Ctx c, State st, int k, T sp, int side, float dt) {
        int m = segs + 1;
        float[] p = st.p[k], q = st.q[k];
        V3 root = V3.of(sp.root().x * side, sp.root().y, sp.root().z);
        V3 dir = V3.of(sp.dir().x * side, sp.dir().y, sp.dir().z).norm();
        V3 p1 = dir.cross(V3.Y);
        p1 = p1.len() < 1e-4F ? V3.X : p1.norm();
        V3 p2 = dir.cross(p1).norm();
        float L = sp.len() * st.ext, seg = L / segs;
        float ph = sp.phase() + (side > 0 ? 2.1F : 0F);
        // forma de reposo: sale de la espalda, se arquea, y unas ondas lentas la recorren (nunca está quieta)
        float[] rest = new float[m * 3];
        for (int i = 0; i < m; i++) {
            float s = i / (float) segs;
            float lat = (float) Math.sin(c.t * 0.06F + ph + s * 3.4F) * wave * s;
            float ver = (float) Math.cos(c.t * 0.047F + ph * 1.3F + s * 2.6F) * wave * 0.7F * s;
            V3 r = root.add(dir.mul(L * s)).add(p1.mul(lat)).add(p2.mul(ver)).add(c.up.mul(sp.curl() * s * s * L));
            rest[i * 3] = r.x; rest[i * 3 + 1] = r.y; rest[i * 3 + 2] = r.z;
        }
        if (!st.init) { System.arraycopy(rest, 0, p, 0, m * 3); System.arraycopy(rest, 0, q, 0, m * 3); }
        // el cuerpo se movió: en el espacio del hueso eso es un empujón contrario (se quedan atrás) y un giro contrario al del cuerpo
        float vx = -c.vl * dt * 0.9F, vy = -c.vy * dt * 0.9F, vz = -c.vf * dt * 0.9F;
        float rot = (float) Math.toRadians(-c.turn * dt * 0.75F);
        float cs = (float) Math.cos(rot), sn = (float) Math.sin(rot);
        float damp = (float) Math.pow(0.9, dt);
        for (int i = 1; i < m; i++) {
            float s = i / (float) segs;
            float kk = Math.min(1F, 0.16F * (1F - 0.55F * s) * dt);
            int o = i * 3;
            float x = p[o], y = p[o + 1], z = p[o + 2];
            float nx = x + (x - q[o]) * damp, ny = y + (y - q[o + 1]) * damp, nz = z + (z - q[o + 2]) * damp;
            q[o] = x; q[o + 1] = y; q[o + 2] = z;
            nx += vx * s; ny += vy * s; nz += vz * s;
            // giro alrededor del eje vertical que pasa por la raíz
            float rx = nx - root.x, rz = nz - root.z;
            nx = root.x + rx * cs - rz * sn;
            nz = root.z + rx * sn + rz * cs;
            nx += (rest[o] - nx) * kk; ny += (rest[o + 1] - ny) * kk; nz += (rest[o + 2] - nz) * kk;
            nx -= c.up.x * grav * dt * s; ny -= c.up.y * grav * dt * s; nz -= c.up.z * grav * dt * s;
            p[o] = nx; p[o + 1] = ny; p[o + 2] = nz;
        }
        // largo fijo de cada tramo (la raíz no se mueve) y sin atravesar el torso
        for (int it = 0; it < 4; it++) {
            p[0] = root.x; p[1] = root.y; p[2] = root.z;
            for (int i = 1; i < m; i++) {
                int o = i * 3, b = o - 3;
                float dx = p[o] - p[b], dy = p[o + 1] - p[b + 1], dz = p[o + 2] - p[b + 2];
                float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (l < 1e-6F) continue;
                float f = seg / l;
                p[o] = p[b] + dx * f; p[o + 1] = p[b + 1] + dy * f; p[o + 2] = p[b + 2] + dz * f;
                if (bone == Bone.CHEST) {
                    if (Math.abs(p[o]) < 0.29F && p[o + 1] < 0.08F && p[o + 1] > -0.82F && p[o + 2] > -0.17F) p[o + 2] = -0.17F;
                } else if (bone == Bone.PELVIS) {
                    if (Math.abs(p[o]) < 0.27F && p[o + 1] > -0.05F && p[o + 1] < 0.75F && p[o + 2] > -0.16F) p[o + 2] = -0.16F;
                }
            }
        }
        V3[] pts = new V3[m];
        for (int i = 0; i < m; i++) pts[i] = new V3(p[i * 3], p[i * 3 + 1], p[i * 3 + 2]);
        // empieza un poco dentro de la espalda para que no quede un hueco
        V3 inward = pts[1].sub(pts[0]).norm().neg();
        pts[0] = pts[0].add(inward.mul(0.05F));
        V3[] sm = smooth(pts, 3);
        tube(c, sm, ph, k);
        Frame end = new Frame(sm[sm.length - 1], sm[sm.length - 1].sub(sm[sm.length - 2]), V3.Y);
        switch (tip) {
            case FLOWER -> flower(c, end, k);
            case SPADE -> spade(c, end);
            case EYE -> eye(c, end, k);
            default -> { }
        }
    }

    private static V3[] smooth(V3[] p, int sub) {
        int m = p.length;
        V3[] o = new V3[(m - 1) * sub + 1];
        int w = 0;
        for (int i = 0; i < m - 1; i++) {
            V3 a = p[Math.max(0, i - 1)], b = p[i], cc = p[i + 1], d = p[Math.min(m - 1, i + 2)];
            for (int s = 0; s < sub; s++) {
                float u = s / (float) sub, u2 = u * u, u3 = u2 * u;
                o[w++] = b.mul(2).add(cc.sub(a).mul(u)).add(a.mul(2).sub(b.mul(5)).add(cc.mul(4)).sub(d).mul(u2)).add(a.neg().add(b.mul(3)).sub(cc.mul(3)).add(d).mul(u3)).mul(0.5F);
            }
        }
        o[w] = p[m - 1];
        return o;
    }

    // ------------------------------------------------------------------ dibujo ----
    private static final int SIDES = 8;

    private void tube(Ctx c, V3[] p, float phase, int k) {
        int m = p.length;
        Mesh sm = c.sc.solid(tex);
        Mesh gm = glowTex != null ? c.sc.glow(glowTex) : null;
        V3[][] ring = new V3[m][SIDES], nr = new V3[m][SIDES];
        float[] rr = new float[m];
        V3 prevU = null;
        V3 view = c.camF.neg();
        V3 half = view.add(c.up.mul(0.9F)).norm();
        float dist = 0;
        float[] vv = new float[m];
        for (int i = 0; i < m; i++) {
            V3 t = p[Math.min(m - 1, i + 1)].sub(p[Math.max(0, i - 1)]).norm();
            V3 u = prevU == null ? t.cross(Math.abs(t.y) > 0.95F ? V3.X : V3.Y).norm() : prevU.perp(t).norm();
            prevU = u;
            V3 v = t.cross(u).norm();
            float s = i / (m - 1F);
            float r = (r0 + (r1 - r0) * (float) Math.pow(s, 0.85F)) * (1F + 0.6F * (float) Math.exp(-s * 12F));
            r *= 1F + 0.16F * (float) Math.sin(s * 22F - c.t * 0.15F + phase) * Math.min(1F, s * 4F) * (1F - 0.6F * s);
            rr[i] = r;
            if (i > 0) dist += p[i].sub(p[i - 1]).len();
            vv[i] = dist;
            for (int q = 0; q < SIDES; q++) {
                float a = 6.2832F * q / SIDES;
                V3 n = u.mul((float) Math.cos(a)).add(v.mul((float) Math.sin(a)));
                nr[i][q] = n;
                ring[i][q] = p[i].add(n.mul(r));
            }
        }
        for (int i = 0; i < m - 1; i++) {
            float s0 = i / (m - 1F), s1 = (i + 1) / (m - 1F);
            for (int q = 0; q < SIDES; q++) {
                int q2 = (q + 1) % SIDES;
                float u0 = q / (float) SIDES, u1 = (q + 1) / (float) SIDES;
                float v0 = vv[i] * 1.4F, v1 = vv[i + 1] * 1.4F;
                int c00 = shade(c, nr[i][q], view, half, s0, phase), c10 = shade(c, nr[i][q2], view, half, s0, phase);
                int c11 = shade(c, nr[i + 1][q2], view, half, s1, phase), c01 = shade(c, nr[i + 1][q], view, half, s1, phase);
                sm.vert(ring[i][q], u0, v0, nr[i][q], c00); sm.vert(ring[i][q2], u1, v0, nr[i][q2], c10);
                sm.vert(ring[i + 1][q2], u1, v1, nr[i + 1][q2], c11); sm.vert(ring[i + 1][q], u0, v1, nr[i + 1][q], c01);
                if (gm != null) {
                    float pulse = 0.5F + 0.5F * (float) Math.sin(c.t * 0.09F - s0 * 9F + k * 1.7F);
                    int g0 = Col.alpha(glowCol, (0.25F + 0.6F * pulse) * s0), g1 = Col.alpha(glowCol, (0.25F + 0.6F * pulse) * s1);
                    float e = 1.03F;
                    gm.vert(p[i].add(nr[i][q].mul(rr[i] * e)), u0, v0, nr[i][q], g0); gm.vert(p[i].add(nr[i][q2].mul(rr[i] * e)), u1, v0, nr[i][q2], g0);
                    gm.vert(p[i + 1].add(nr[i + 1][q2].mul(rr[i + 1] * e)), u1, v1, nr[i + 1][q2], g1); gm.vert(p[i + 1].add(nr[i + 1][q].mul(rr[i + 1] * e)), u0, v1, nr[i + 1][q], g1);
                }
            }
        }
    }

    /** color de cada vértice: base oscura, borde que brilla de costado (Fresnel) y reflejo húmedo que corre por el tentáculo */
    private int shade(Ctx c, V3 n, V3 view, V3 half, float s, float phase) {
        float nv = Math.abs(n.dot(view));
        float fres = (float) Math.pow(1F - nv, 2.6F);
        float spec = (float) Math.pow(Math.max(0F, n.dot(half)), wet ? 36F : 14F) * (0.55F + 0.45F * (float) Math.sin(s * 16F - c.t * 0.09F + phase));
        float base = 0.55F + 0.45F * s;
        float r = ((tint >> 16) & 255) / 255F, g = ((tint >> 8) & 255) / 255F, b = (tint & 255) / 255F;
        float sp = wet ? 0.75F : 0.35F, fr = wet ? 0.22F : 0.12F;
        return Col.argb(r * base + fr * fres + sp * spec, g * base + fr * fres + sp * spec, b * base + fr * 1.4F * fres + sp * 1.1F * spec, 1F);
    }

    private void flower(Ctx c, Frame end, int k) {
        float open = 0.5F + 0.5F * (float) Math.sin(c.t * 0.05F + k * 2.1F);
        for (int j = 0; j < 5; j++) {
            float a = j * 1.2566F + c.t * 0.004F;
            V3 dir = end.t.add(end.s.mul((float) Math.cos(a) * 0.95F * open)).add(end.u.mul((float) Math.sin(a) * 0.95F * open)).norm();
            V3 side = end.s.mul((float) -Math.sin(a)).add(end.u.mul((float) Math.cos(a))).norm();
            G.blade(c.sc.solid("petal_mort"), end.p, dir, side, 0.15F, 0.085F, 0.5F, end.t.neg(), 3, G.FLAT, new float[]{(j % 4) * 0.25F, 0F, (j % 4) * 0.25F + 0.25F, 1F},
                    Col.shade(0xFFFFFFFF, 0.8F), Col.WHITE, 0.004F);
        }
        G.sprite(c.sc.glow("glow"), end.p.add(end.t.mul(0.05F)), c.camR, c.camU, c.camF, 0.2F, 0.2F, 0, G.FULL, Col.alpha(glowCol, 0.45F + 0.45F * open));
        // esporas que se desprenden
        for (int j = 0; j < 3; j++) {
            float life = (c.t * 0.02F + j * 0.33F + k * 0.17F) % 1F;
            V3 sp = end.p.add(end.t.mul(0.08F + life * 0.3F)).add(c.up.mul(life * 0.25F)).add(end.s.mul(0.08F * (float) Math.sin(c.t * 0.1F + j * 3)));
            G.sprite(c.sc.glow("spark"), sp, c.camR, c.camU, c.camF, 0.04F, 0.04F, 0, G.FULL, Col.alpha(glowCol, (float) Math.sin(Math.PI * life)));
        }
    }

    private void spade(Ctx c, Frame end) {
        V3 d = end.t, s1 = end.s, s2 = end.u;
        G.blade(c.sc.solid("spade"), end.p, d, s1, 0.26F, 0.2F, 0.05F, s2, 2, G.BLADE, G.FULL, Col.shade(tint, 0.9F), Col.WHITE, 0.012F);
        G.blade(c.sc.solid("spade"), end.p, d, s2, 0.26F, 0.2F, 0.05F, s1, 2, G.BLADE, G.FULL, Col.shade(tint, 0.9F), Col.WHITE, 0.012F);
        if (glowCol != 0) {
            G.blade(c.sc.glow("spade_g"), end.p, d, s1, 0.26F, 0.2F, 0.05F, s2, 2, G.BLADE, G.FULL, Col.alpha(glowCol, 0.4F), Col.alpha(glowCol, 0.9F), 0.012F);
            G.blade(c.sc.glow("spade_g"), end.p, d, s2, 0.26F, 0.2F, 0.05F, s1, 2, G.BLADE, G.FULL, Col.alpha(glowCol, 0.4F), Col.alpha(glowCol, 0.9F), 0.012F);
        }
    }

    /** un ojo en la punta que mira hacia la cámara y parpadea */
    private void eye(Ctx c, Frame end, int k) {
        V3 at = end.p.add(end.t.mul(0.03F));
        float blink = (c.t * 0.009F + k * 0.37F) % 1F;
        float lid = blink > 0.95F ? 0.2F : 1F;
        G.sphere(c.sc.solid("eye"), at, c.camR, c.camU.mul(lid), c.camF.neg(), 0.055F, 8, 12, Col.WHITE);
        G.sprite(c.sc.glow("glow"), at, c.camR, c.camU, c.camF, 0.22F, 0.22F, 0, G.FULL, Col.alpha(glowCol, 0.5F));
    }
}
