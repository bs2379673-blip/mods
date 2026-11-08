package com.leon.saintsdragons.util.compat;

import org.joml.Matrix3f;

/** Port 1.21.11: setNormal(Matrix3f, x, y, z) ya no existe; esto transforma la normal a mano. */
public final class NormalUtil {
    private NormalUtil() {
    }

    private static float len(Matrix3f m, float x, float y, float z) {
        float a = m.m00() * x + m.m10() * y + m.m20() * z, b = m.m01() * x + m.m11() * y + m.m21() * z, c = m.m02() * x + m.m12() * y + m.m22() * z;
        float l = (float) Math.sqrt(a * a + b * b + c * c);
        return l < 1e-6f ? 1 : l;
    }

    public static float x(Matrix3f m, float x, float y, float z) {
        return (m.m00() * x + m.m10() * y + m.m20() * z) / len(m, x, y, z);
    }

    public static float y(Matrix3f m, float x, float y, float z) {
        return (m.m01() * x + m.m11() * y + m.m21() * z) / len(m, x, y, z);
    }

    public static float z(Matrix3f m, float x, float y, float z) {
        return (m.m02() * x + m.m12() * y + m.m22() * z) / len(m, x, y, z);
    }
}
