package com.tunombre.cosmeticos.geo;

/** lista de vertices con textura: x y z u v nx ny nz r g b a (12 floats por vertice, de 4 en 4 forman un quad) */
public final class Mesh {
    public static final int STRIDE = 12;
    public final String tex;
    public final boolean glow;
    public float[] d = new float[STRIDE * 256];
    public int n;

    public Mesh(String tex, boolean glow) { this.tex = tex; this.glow = glow; }

    public void vert(V3 p, float u, float v, V3 nr, int argb) {
        if ((n + 1) * STRIDE > d.length) d = java.util.Arrays.copyOf(d, d.length * 2);
        int i = n * STRIDE;
        d[i] = p.x; d[i + 1] = p.y; d[i + 2] = p.z; d[i + 3] = u; d[i + 4] = v;
        d[i + 5] = nr.x; d[i + 6] = nr.y; d[i + 7] = nr.z;
        d[i + 8] = ((argb >> 16) & 255) / 255F; d[i + 9] = ((argb >> 8) & 255) / 255F; d[i + 10] = (argb & 255) / 255F; d[i + 11] = ((argb >>> 24) & 255) / 255F;
        n++;
    }

    public int quads() { return n / 4; }
}
