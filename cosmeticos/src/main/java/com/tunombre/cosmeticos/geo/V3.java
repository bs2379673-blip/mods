package com.tunombre.cosmeticos.geo;

/** vector inmutable (sin depender de Minecraft para poder probar las formas fuera del juego) */
public final class V3 {
    public static final V3 ZERO = new V3(0, 0, 0), X = new V3(1, 0, 0), Y = new V3(0, 1, 0), Z = new V3(0, 0, 1);
    public final float x, y, z;

    public V3(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }

    public static V3 of(double x, double y, double z) { return new V3((float) x, (float) y, (float) z); }

    public V3 add(V3 o) { return new V3(x + o.x, y + o.y, z + o.z); }
    public V3 add(float a, float b, float c) { return new V3(x + a, y + b, z + c); }
    public V3 sub(V3 o) { return new V3(x - o.x, y - o.y, z - o.z); }
    public V3 mul(float k) { return new V3(x * k, y * k, z * k); }
    public float dot(V3 o) { return x * o.x + y * o.y + z * o.z; }
    public V3 cross(V3 o) { return new V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x); }
    public float len() { return (float) Math.sqrt(x * x + y * y + z * z); }
    public V3 norm() { float l = len(); return l < 1e-7F ? new V3(0, 1, 0) : new V3(x / l, y / l, z / l); }
    public V3 lerp(V3 o, float t) { return new V3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t); }
    public V3 neg() { return new V3(-x, -y, -z); }

    /** rotacion alrededor de un eje unitario (Rodrigues) */
    public V3 rot(V3 axis, float ang) {
        float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
        return mul(c).add(axis.cross(this).mul(s)).add(axis.mul(axis.dot(this) * (1 - c)));
    }

    /** componente perpendicular a n (n unitario) */
    public V3 perp(V3 n) { return sub(n.mul(dot(n))); }

    @Override public String toString() { return String.format("(%.3f, %.3f, %.3f)", x, y, z); }
}
