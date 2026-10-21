package com.tunombre.cosmeticos.geo.cos;

import com.tunombre.cosmeticos.geo.*;

/** alas de mariposa: dos alas por lado (grandes arriba, pequenas abajo), abiertas como un cartel o juntas hacia arriba */
public final class Butterfly implements Cosm {
    private final String id = "alas_mariposa";

    @Override public Bone bone() { return Bone.CHEST; }

    @Override
    public void build(Ctx c) {
        Motion m = c.state(id, Motion::new);
        m.update(c, 0.05F, 0.40F);
        for (int side = -1; side <= 1; side += 2) {
            wing(c, m, side, true);
            wing(c, m, side, false);
        }
        Mesh g = c.sc.glow("glow");
        G.sprite(g, V3.of(0, -0.25, -0.2), c.camR, c.camU, c.camF, 0.4F, 0.4F, 0F, G.FULL, 0x30FF70D0);
        // polvillo brillante que cae
        for (int k = 0; k < 8; k++) {
            float life = (c.t * 0.011F + k * 0.125F) % 1F;
            int side = (k & 1) * 2 - 1;
            V3 p = V3.of(side * (0.25 + 0.7 * Noise.hash(k, 5)), 0.1 + 0.4 * Noise.hash(k, 6) - life * 0.8, -0.3 - 0.1 * Math.sin(c.t * 0.07 + k));
            float a = (float) Math.sin(Math.PI * life);
            G.sprite(c.sc.glow("spark"), p, c.camR, c.camU, c.camF, 0.06F * a + 0.01F, 0.06F * a + 0.01F, 0, G.FULL, Col.alpha(0xFFFF9AE8, a * 0.85F));
        }
    }

    private void wing(Ctx c, Motion m, int side, boolean upper) {
        float lag = upper ? 0F : 0.45F;
        float psi = G.PI * 0.5F * (1.15F - 1.0F * m.open) + 0.0F;
        psi += (m.amp * 1.5F + 0.04F) * (float) Math.sin(m.phase - lag) - 0.05F;
        psi = Noise.clamp(psi, 0.05F, 1.5F);
        V3 H = V3.of(side * 0.06, upper ? -0.13 : -0.40, -0.13);
        V3 out = V3.of(side * Math.cos(psi), 0, -Math.sin(psi));
        V3 nrm = V3.Y.cross(out).mul(side);       // perpendicular al plano horizontal del ala
        float Wd = (upper ? 0.95F : 0.66F), Hd = upper ? 0.82F : 0.62F;
        float b0 = upper ? -0.12F : -0.78F, b1 = upper ? 0.72F : 0.1F;
        float shear = upper ? 0.42F : -0.2F;
        int rows = 8, cols = 7;
        V3[][] g = new V3[rows + 1][cols + 1];
        for (int r = 0; r <= rows; r++) {
            float b = b0 + (b1 - b0) * r / rows;
            for (int cc = 0; cc <= cols; cc++) {
                float a = cc / (float) cols;
                float flutter = 0.03F * (float) Math.sin(c.t * 0.5F * (0.3F + m.amp * 3) + a * 4 + r * 0.7F) * a * (0.3F + m.amp * 3);
                V3 p = H.add(out.mul(a * Wd + b * shear * 0.4F)).add(0, b * Hd / 0.85F, 0).add(nrm.mul(-0.10F * a * a + flutter));
                g[r][cc] = p;
            }
        }
        float ua = upper ? 0F : 0.5F;
        Mesh sm = c.sc.solid("butterfly"), gm = c.sc.glow("butterfly_g");
        for (int r = 0; r < rows; r++) {
            for (int cc = 0; cc < cols; cc++) {
                V3 q0 = g[r][cc], q1 = g[r][cc + 1], q2 = g[r + 1][cc + 1], q3 = g[r + 1][cc];
                V3 nr = q1.sub(q0).cross(q3.sub(q0)).norm();
                float[] us = {cc / (float) cols, (cc + 1) / (float) cols};
                float v0 = 1F - (r + 1) / (float) rows, v1 = 1F - r / (float) rows;
                float uA = side > 0 ? ua + us[0] * 0.5F : ua + (1 - us[0]) * 0.5F, uB = side > 0 ? ua + us[1] * 0.5F : ua + (1 - us[1]) * 0.5F;
                int col = Col.shade(Col.WHITE, 0.82F + 0.18F * (float) Math.sin(psi * 2 + cc * 0.5F));
                sm.vert(q0, uA, v1, nr, col); sm.vert(q1, uB, v1, nr, col); sm.vert(q2, uB, v0, nr, col); sm.vert(q3, uA, v0, nr, col);
                int gc = Col.alpha(Col.WHITE, 0.55F + 0.45F * (float) Math.sin(c.t * 0.06F + cc * 0.4F));
                gm.vert(q0, uA, v1, nr, gc); gm.vert(q1, uB, v1, nr, gc); gm.vert(q2, uB, v0, nr, gc); gm.vert(q3, uA, v0, nr, gc);
            }
        }
    }
}
