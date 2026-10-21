package com.tunombre.jefes.core;

/** Matriz 4x4 (columna-mayor, como OpenGL/JOML) sin dependencias de Minecraft: la usan el render, el servidor y las pruebas. */
public final class Mat4 {
    public final float[] m = new float[16];

    public Mat4() { identity(); }

    public Mat4(Mat4 o) { System.arraycopy(o.m, 0, m, 0, 16); }

    public Mat4 identity() {
        java.util.Arrays.fill(m, 0F);
        m[0] = m[5] = m[10] = m[15] = 1F;
        return this;
    }

    public Mat4 set(Mat4 o) { System.arraycopy(o.m, 0, m, 0, 16); return this; }

    /** this = this * o */
    public Mat4 mul(Mat4 o) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++)
            for (int rr = 0; rr < 4; rr++) {
                float s = 0;
                for (int k = 0; k < 4; k++) s += m[k * 4 + rr] * o.m[c * 4 + k];
                r[c * 4 + rr] = s;
            }
        System.arraycopy(r, 0, m, 0, 16);
        return this;
    }

    public Mat4 translate(float x, float y, float z) {
        m[12] += m[0] * x + m[4] * y + m[8] * z;
        m[13] += m[1] * x + m[5] * y + m[9] * z;
        m[14] += m[2] * x + m[6] * y + m[10] * z;
        return this;
    }

    public Mat4 scale(float x, float y, float z) {
        for (int i = 0; i < 4; i++) { m[i] *= x; m[4 + i] *= y; m[8 + i] *= z; }
        return this;
    }

    /** this = this * Rz * Ry * Rx (el mismo orden que ModelPart.rotationZYX de Minecraft); angulos en radianes */
    public Mat4 rotateZYX(float x, float y, float z) {
        float cx = (float) Math.cos(x), sx = (float) Math.sin(x), cy = (float) Math.cos(y), sy = (float) Math.sin(y), cz = (float) Math.cos(z), sz = (float) Math.sin(z);
        Mat4 r = new Mat4();
        // R = Rz * Ry * Rx
        r.m[0] = cz * cy;               r.m[4] = cz * sy * sx - sz * cx;  r.m[8] = cz * sy * cx + sz * sx;
        r.m[1] = sz * cy;               r.m[5] = sz * sy * sx + cz * cx;  r.m[9] = sz * sy * cx - cz * sx;
        r.m[2] = -sy;                   r.m[6] = cy * sx;                 r.m[10] = cy * cx;
        return mul(r);
    }

    public Mat4 rotateY(float a) { return rotateZYX(0, a, 0); }

    public Mat4 rotateX(float a) { return rotateZYX(a, 0, 0); }

    public Mat4 rotateZ(float a) { return rotateZYX(0, 0, a); }

    public void point(float x, float y, float z, float[] out) {
        out[0] = m[0] * x + m[4] * y + m[8] * z + m[12];
        out[1] = m[1] * x + m[5] * y + m[9] * z + m[13];
        out[2] = m[2] * x + m[6] * y + m[10] * z + m[14];
    }

    public void dir(float x, float y, float z, float[] out) {
        out[0] = m[0] * x + m[4] * y + m[8] * z;
        out[1] = m[1] * x + m[5] * y + m[9] * z;
        out[2] = m[2] * x + m[6] * y + m[10] * z;
    }
}
