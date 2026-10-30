package com.annis.npc.client.importer;

/** Vectores y matrices 3x3 minimos (double[3], double[9] por filas). */
final class V {
    private V() {
    }

    static double[] v(double x, double y, double z) {
        return new double[]{x, y, z};
    }

    static double[] add(double[] a, double[] b) {
        return v(a[0] + b[0], a[1] + b[1], a[2] + b[2]);
    }

    static double[] sub(double[] a, double[] b) {
        return v(a[0] - b[0], a[1] - b[1], a[2] - b[2]);
    }

    static double[] mul(double[] a, double s) {
        return v(a[0] * s, a[1] * s, a[2] * s);
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static double[] cross(double[] a, double[] b) {
        return v(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]);
    }

    static double len(double[] a) {
        return Math.sqrt(dot(a, a));
    }

    static double[] norm(double[] a) {
        double l = len(a);
        return l < 1e-12 ? v(0, 0, 0) : mul(a, 1.0 / l);
    }

    static double[] mid(double[] a, double[] b) {
        return mul(add(a, b), 0.5);
    }

    static double[] identity() {
        return new double[]{1, 0, 0, 0, 1, 0, 0, 0, 1};
    }

    static double[] mm(double[] a, double[] b) {
        double[] r = new double[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
            }
        }
        return r;
    }

    static double[] mv(double[] m, double[] x) {
        return v(m[0] * x[0] + m[1] * x[1] + m[2] * x[2],
                m[3] * x[0] + m[4] * x[1] + m[5] * x[2],
                m[6] * x[0] + m[7] * x[1] + m[8] * x[2]);
    }

    static double[] transpose(double[] m) {
        return new double[]{m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]};
    }

    static double[] rot(char axis, double deg) {
        double a = Math.toRadians(deg);
        double c = Math.cos(a);
        double s = Math.sin(a);
        return switch (axis) {
            case 'X' -> new double[]{1, 0, 0, 0, c, -s, 0, s, c};
            case 'Y' -> new double[]{c, 0, s, 0, 1, 0, -s, 0, c};
            default -> new double[]{c, -s, 0, s, c, 0, 0, 0, 1};
        };
    }

    static double[] quat(double x, double y, double z, double w) {
        double n = Math.sqrt(x * x + y * y + z * z + w * w);
        if (n < 1e-12) {
            return identity();
        }
        x /= n;
        y /= n;
        z /= n;
        w /= n;
        return new double[]{
                1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w),
                2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w),
                2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)};
    }

    static double[] slerp(double[] a, double[] b, double t) {
        double d = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
        double[] bb = b;
        if (d < 0) {
            bb = new double[]{-b[0], -b[1], -b[2], -b[3]};
            d = -d;
        }
        double[] r = new double[4];
        if (d > 0.9995) {
            for (int i = 0; i < 4; i++) {
                r[i] = a[i] + (bb[i] - a[i]) * t;
            }
        } else {
            double th = Math.acos(d);
            double sa = Math.sin((1 - t) * th) / Math.sin(th);
            double sb = Math.sin(t * th) / Math.sin(th);
            for (int i = 0; i < 4; i++) {
                r[i] = a[i] * sa + bb[i] * sb;
            }
        }
        double n = Math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2] + r[3] * r[3]);
        for (int i = 0; i < 4; i++) {
            r[i] /= n;
        }
        return r;
    }
}
