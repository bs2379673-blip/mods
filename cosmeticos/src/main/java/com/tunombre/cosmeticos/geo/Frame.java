package com.tunombre.cosmeticos.geo;

/** sistema de ejes que avanza a lo largo de una curva: t = tangente, u = arriba de la curva, s = lado */
public final class Frame {
    public V3 p, t, u, s;

    public Frame(V3 p, V3 t, V3 up) {
        this.p = p;
        this.t = t.norm();
        V3 side = this.t.cross(up);
        if (side.len() < 1e-4F) side = this.t.cross(V3.X);
        this.s = side.norm();
        this.u = this.s.cross(this.t).norm();
    }

    public Frame copy() { Frame f = new Frame(p, t, u); f.s = s; f.u = u; return f; }

    /** gira la tangente: yaw hacia el lado, pitch hacia arriba (radianes) */
    public Frame turn(float yaw, float pitch) {
        if (yaw != 0) { t = t.rot(u, yaw); s = s.rot(u, yaw); }
        if (pitch != 0) { t = t.rot(s, -pitch); u = u.rot(s, -pitch); }
        t = t.norm();
        return this;
    }

    public Frame step(float len) { p = p.add(t.mul(len)); return this; }
}
