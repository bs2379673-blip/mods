package com.tunombre.cosmeticos.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** primitivas procedurales: todo en coordenadas del mundo relativas a la camara, con colores ARGB por vertice (degradados = brillo) */
public final class Draw {
    public record V(float x, float y, float z) {
        V add(V o) { return new V(x + o.x, y + o.y, z + o.z); }
        V sub(V o) { return new V(x - o.x, y - o.y, z - o.z); }
        V mul(float k) { return new V(x * k, y * k, z * k); }
        float len() { return (float) Math.sqrt(x * x + y * y + z * z); }
        V norm() { float l = len(); return l < 1e-6F ? new V(0, 1, 0) : new V(x / l, y / l, z / l); }
        V cross(V o) { return new V(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x); }
    }

    public static V v(float x, float y, float z) { return new V(x, y, z); }

    public final VertexConsumer solid, add;
    public int count;
    // origen del jugador relativo a la camara, giro del cuerpo
    float ox, oy, oz, cs, sn;
    public float t, vx, vz, flapV;
    public boolean air, glide, crouch;

    public Draw(VertexConsumer solid, VertexConsumer add) { this.solid = solid; this.add = add; }

    public void setPlayer(float ox, float oy, float oz, float yawDeg, float t, float vx, float vz, boolean air, boolean glide, boolean crouch) {
        this.ox = ox; this.oy = oy; this.oz = oz;
        double a = Math.toRadians(yawDeg);
        cs = (float) Math.cos(a); sn = (float) Math.sin(a);
        this.t = t; this.vx = vx; this.vz = vz; this.air = air; this.glide = glide; this.crouch = crouch;
        // aleteo: -1..1
        flapV = glide ? 0F : (air ? (float) Math.sin(t * 0.5F) : 0.45F * (float) Math.sin(t * 0.11F));
    }

    // ---------- espacios ----------
    /** punto local (x derecha del jugador, y arriba, z adelante) -> relativo a la camara */
    public V L(float x, float y, float z) {
        if (crouch && y > 0.8F) { float k = Math.min(1F, (y - 0.8F) / 0.7F); y -= 0.17F * k; z -= 0.2F * k; }
        return new V(x * cs - z * sn + ox, y + oy, z * cs + x * sn + oz);
    }

    /** vector local (sin traslacion) -> mundo */
    public V D(float x, float y, float z) { return new V(x * cs - z * sn, y, z * cs + x * sn); }

    // ---------- color ----------
    public static int argb(float r, float g, float b, float a) {
        return (((int) (Math.max(0, Math.min(1, a)) * 255)) << 24) | (((int) (Math.max(0, Math.min(1, r)) * 255)) << 16)
                | (((int) (Math.max(0, Math.min(1, g)) * 255)) << 8) | ((int) (Math.max(0, Math.min(1, b)) * 255));
    }

    public static int hsv(float h, float s, float v, float a) {
        h = h - (float) Math.floor(h);
        float r, g, b; int i = (int) (h * 6); float f = h * 6 - i, p = v * (1 - s), q = v * (1 - f * s), tt = v * (1 - (1 - f) * s);
        switch (i % 6) { case 0 -> { r = v; g = tt; b = p; } case 1 -> { r = q; g = v; b = p; } case 2 -> { r = p; g = v; b = tt; }
            case 3 -> { r = p; g = q; b = v; } case 4 -> { r = tt; g = p; b = v; } default -> { r = v; g = p; b = q; } }
        return argb(r, g, b, a);
    }

    public static int mix(int c0, int c1, float k) {
        k = Math.max(0, Math.min(1, k));
        int a = (int) (((c0 >>> 24) & 255) * (1 - k) + ((c1 >>> 24) & 255) * k), r = (int) (((c0 >> 16) & 255) * (1 - k) + ((c1 >> 16) & 255) * k);
        int g = (int) (((c0 >> 8) & 255) * (1 - k) + ((c1 >> 8) & 255) * k), b = (int) ((c0 & 255) * (1 - k) + (c1 & 255) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int alpha(int c, float a) { return (((int) (((c >>> 24) & 255) * Math.max(0, Math.min(1, a)))) << 24) | (c & 0xFFFFFF); }

    // ---------- primitivas ----------
    private void vtx(VertexConsumer vc, V p, int c) {
        vc.vertex(p.x, p.y, p.z).color((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255).endVertex();
    }

    public void quad(boolean glow, V a, V b, V c, V d, int ca, int cb, int cc, int cd) {
        VertexConsumer vc = glow ? add : solid;
        vtx(vc, a, ca); vtx(vc, b, cb); vtx(vc, c, cc); vtx(vc, d, cd);
        count++;
    }

    public void tri(boolean glow, V a, V b, V c, int ca, int cb, int cc) { quad(glow, a, b, c, c, ca, cb, cc, cc); }

    /** ejes de "cartel" (siempre de cara a la camara) en el punto c */
    private V[] bill(V c) {
        V view = c.norm();
        V right = v(0, 1, 0).cross(view);
        if (right.len() < 1e-4F) right = v(1, 0, 0);
        right = right.norm();
        V up = view.cross(right).norm();
        return new V[]{right, up};
    }

    /** cinta de ancho variable que siempre mira a la camara */
    public void strip(boolean glow, V[] p, float[] w, int[] c) {
        int n = p.length;
        V[] l = new V[n], r = new V[n];
        for (int i = 0; i < n; i++) {
            V tan = (i == 0 ? p[1].sub(p[0]) : i == n - 1 ? p[n - 1].sub(p[n - 2]) : p[i + 1].sub(p[i - 1])).norm();
            V side = tan.cross(p[i].norm());
            side = side.len() < 1e-5F ? v(1, 0, 0) : side.norm();
            l[i] = p[i].add(side.mul(w[i] * 0.5F)); r[i] = p[i].sub(side.mul(w[i] * 0.5F));
        }
        for (int i = 0; i < n - 1; i++) quad(glow, l[i], l[i + 1], r[i + 1], r[i], c[i], c[i + 1], c[i + 1], c[i]);
    }

    /** disco/elipse de cartel: centro con color c0 y borde con c1 */
    public void disc(boolean glow, V c, float rx, float ry, int c0, int c1, int seg) {
        V[] b = bill(c);
        V prev = null;
        for (int i = 0; i <= seg; i++) {
            double a = Math.PI * 2 * i / seg;
            V e = c.add(b[0].mul((float) Math.cos(a) * rx)).add(b[1].mul((float) Math.sin(a) * ry));
            if (prev != null) quad(glow, c, prev, e, e, c0, c1, c1, c1);
            prev = e;
        }
    }

    public void glow(V c, float size, int col) { disc(true, c, size, size, col, alpha(col, 0F), 8); }

    /** destello de 4 puntas */
    public void star(V c, float size, int col) {
        V[] b = bill(c);
        V r = b[0].mul(size), u = b[1].mul(size);
        V rs = b[0].mul(size * 0.18F), us = b[1].mul(size * 0.18F);
        int e = alpha(col, 0F);
        tri(true, c.add(rs).add(us), c.add(u), c.sub(rs).add(us), col, e, col);
        tri(true, c.sub(rs).sub(us), c.sub(u), c.add(rs).sub(us), col, e, col);
        tri(true, c.add(rs).add(us), c.add(r), c.add(rs).sub(us), col, e, col);
        tri(true, c.sub(rs).add(us), c.sub(r), c.sub(rs).sub(us), col, e, col);
        glow(c, size * 0.55F, alpha(col, 0.5F));
    }

    /** hoja / pluma / petalo: base -> punta con ancho maximo al 35 % */
    public void leaf(boolean glow, V base, V dir, V side, float len, float width, int cBase, int cTip) {
        V tip = base.add(dir.mul(len));
        V m = base.add(dir.mul(len * 0.35F));
        V a = m.add(side.mul(width)), b = m.sub(side.mul(width));
        int cm = mix(cBase, cTip, 0.4F);
        quad(glow, base, a, tip, b, cBase, cm, cTip, cm);
    }

    /** hoja con orientacion automatica (el ancho mira a la camara) */
    public void leafAuto(boolean glow, V base, V dir, float len, float width, int cBase, int cTip) {
        V side = dir.cross(base.norm());
        side = side.len() < 1e-5F ? v(1, 0, 0) : side.norm();
        leaf(glow, base, dir.norm(), side, len, width, cBase, cTip);
    }
}
