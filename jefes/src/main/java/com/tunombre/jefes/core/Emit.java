package com.tunombre.jefes.core;

/** Convierte el modelo en las posiciones/uv/normales de cada vertice (en el espacio de {@code world[0]}'s root). */
public final class Emit {
    public interface Sink {
        void vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz);
    }

    private Emit() { }

    public static void quads(Model m, Mat4[] world, Sink s) {
        float[] p = new float[3], nn = new float[3];
        for (int i = 0; i < m.n; i++) {
            float[] q = m.quads[i];
            if (q.length == 0) continue;
            Mat4 w = world[i];
            for (int o = 0; o + 19 < q.length; o += 20) {
                // normal local: (p1-p0) x (p3-p0)
                float ax = q[o + 5] - q[o], ay = q[o + 6] - q[o + 1], az = q[o + 7] - q[o + 2];
                float bx = q[o + 15] - q[o], by = q[o + 16] - q[o + 1], bz = q[o + 17] - q[o + 2];
                float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
                w.dir(nx, ny, nz, nn);
                float l = (float) Math.sqrt(nn[0] * nn[0] + nn[1] * nn[1] + nn[2] * nn[2]);
                if (l < 1e-9F) l = 1F;
                for (int k = 0; k < 4; k++) {
                    int b = o + k * 5;
                    w.point(q[b], q[b + 1], q[b + 2], p);
                    s.vertex(p[0], p[1], p[2], q[b + 3], q[b + 4], nn[0] / l, nn[1] / l, nn[2] / l);
                }
            }
        }
    }
}
