package com.annis.interact;

/**
 * Modo mimos: zonas que se pueden tocar y como se mueve el punto de contacto.
 * Solo zonas de carino: cabeza y cara, hombros, brazos, manos y espalda (la parte baja de la espalda
 * da cosquillas). El pecho, la panza, la cadera y las piernas no se pueden tocar.
 *
 * Coordenadas del modelo de quien recibe, en pixeles: x = su izquierda, y = abajo, z = atras.
 */
public final class Touch {
    public static final int NONE = 0;
    public static final int PELO = 1;
    public static final int MEJILLA = 2;
    public static final int MANO_D = 3;
    public static final int MANO_I = 4;
    public static final int BRAZO_D = 5;
    public static final int BRAZO_I = 6;
    public static final int HOMBRO_D = 7;
    public static final int HOMBRO_I = 8;
    public static final int NARIZ = 9;
    public static final int FRENTE = 10;
    public static final int BARBILLA = 11;
    public static final int OREJA_D = 12;
    public static final int OREJA_I = 13;
    public static final int ESPALDA = 14;
    public static final int COSQUILLAS = 15;
    public static final int COUNT = 16;

    private static final String[] NAMES = {"", "Pelo", "Mejilla", "Mano derecha", "Mano izquierda",
            "Brazo derecho", "Brazo izquierdo", "Hombro derecho", "Hombro izquierdo",
            "Nariz", "Frente", "Barbilla", "Oreja derecha", "Oreja izquierda", "Espalda", "Cosquillas"};

    // cajas en reposo: min x,y,z / max x,y,z (la cabeza con la capa del sombrero)
    private static final float[] HEAD = {-4.5f, -8.5f, -4.5f, 4.5f, 0.5f, 4.5f};
    private static final float[] BODY = {-4.2f, 0, -2.2f, 4.2f, 12, 2.2f};
    private static final float[] RARM = {-8.3f, -0.3f, -2.3f, -4, 12, 2.3f};
    private static final float[] LARM = {4, -0.3f, -2.3f, 8.3f, 12, 2.3f};
    private static final float[] LEGS = {-4.2f, 12, -2.2f, 4.2f, 24, 2.2f};

    private Touch() {
    }

    public static String name(int zone) {
        return zone > 0 && zone < COUNT ? NAMES[zone] : "";
    }

    /** Zonas de la cabeza (la mano se apoya por fuera). */
    public static boolean isHead(int zone) {
        return zone == PELO || zone == MEJILLA || zone == NARIZ || zone == FRENTE || zone == BARBILLA
                || zone == OREJA_D || zone == OREJA_I;
    }

    public static boolean isBack(int zone) {
        return zone == ESPALDA || zone == COSQUILLAS;
    }

    public static boolean isHand(int zone) {
        return zone == MANO_D || zone == MANO_I || zone == BRAZO_D || zone == BRAZO_I;
    }

    /** true = brazo derecho (x negativa). */
    public static boolean rightSide(int zone) {
        return zone == MANO_D || zone == BRAZO_D || zone == HOMBRO_D;
    }

    /** Resultado de apuntar: zona tocada y punto de contacto. */
    public record Hit(int zone, float x, float y, float z) {
    }

    /**
     * Rayo (origen o, direccion d, en el modelo del que recibe) contra el cuerpo. Devuelve la zona
     * de carino tocada, o null si no toca nada o si lo primero que toca no se puede tocar.
     */
    public static Hit pick(float ox, float oy, float oz, float dx, float dy, float dz) {
        float[][] boxes = {HEAD, BODY, RARM, LARM, LEGS};
        float best = Float.MAX_VALUE;
        int which = -1;
        for (int i = 0; i < boxes.length; i++) {
            float t = ray(boxes[i], ox, oy, oz, dx, dy, dz);
            if (t >= 0 && t < best) {
                best = t;
                which = i;
            }
        }
        if (which < 0 || which == 4) {
            return null;
        }
        float x = ox + dx * best, y = oy + dy * best, z = oz + dz * best;
        int zone;
        if (which == 1) {
            // del torso solo la espalda
            if (z < 1.8f) {
                return null;
            }
            zone = y > 7.0f ? COSQUILLAS : ESPALDA;
        } else if (which == 0) {
            if (z < -3.8f) {
                // la cara del skin (de arriba abajo): frente -8..-5, ojos -5..-3.5, nariz -3.5..-2,
                // boca y barbilla -2..0; las mejillas a los costados de la nariz y la boca
                float ax = Math.abs(x);
                if (y < -5.0f || (y < -3.6f && ax < 1.5f)) {
                    zone = FRENTE;
                } else if (y > -1.2f && ax < 2.6f) {
                    zone = BARBILLA;
                } else if (ax < 1.6f && y > -3.9f) {
                    zone = NARIZ;
                } else if (y > -4.2f) {
                    zone = MEJILLA;
                } else {
                    zone = FRENTE;
                }
            } else if (Math.abs(x) > 3.8f && y > -6.0f && y < -0.5f) {
                zone = x < 0 ? OREJA_D : OREJA_I;
            } else {
                zone = PELO;
            }
        } else {
            boolean right = which == 2;
            if (y > 8.0f) {
                zone = right ? MANO_D : MANO_I;
            } else if (y < 3.5f) {
                zone = right ? HOMBRO_D : HOMBRO_I;
            } else {
                zone = right ? BRAZO_D : BRAZO_I;
            }
        }
        return new Hit(zone, x, y, z);
    }

    /** Distancia del rayo a la caja (slab), o -1. */
    static float ray(float[] b, float ox, float oy, float oz, float dx, float dy, float dz) {
        float tmin = 0, tmax = Float.MAX_VALUE;
        float[] o = {ox, oy, oz}, d = {dx, dy, dz};
        for (int a = 0; a < 3; a++) {
            float lo = b[a], hi = b[a + 3];
            if (Math.abs(d[a]) < 1.0e-6f) {
                if (o[a] < lo || o[a] > hi) {
                    return -1;
                }
                continue;
            }
            float t1 = (lo - o[a]) / d[a], t2 = (hi - o[a]) / d[a];
            if (t1 > t2) {
                float k = t1;
                t1 = t2;
                t2 = k;
            }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) {
                return -1;
            }
        }
        return tmin;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * Lleva el punto arrastrado a un sitio valido para la zona: sobre el pelo, sobre la cara,
     * al alcance del brazo, el hombro fijo. Lo usan el cliente (al arrastrar) y el servidor (por si acaso).
     */
    public static float[] constrain(int zone, float x, float y, float z) {
        switch (zone) {
            case PELO -> {
                // sobre la superficie de la cabeza, de las orejas para arriba
                float cx = x, cy = y + 4, cz = z;
                float m = Math.max(Math.abs(cx), Math.max(Math.abs(cy), Math.abs(cz)));
                if (m < 0.01f) {
                    return new float[]{0, -8.6f, 0};
                }
                float k = 4.6f / m;
                return new float[]{clamp(cx * k, -4.6f, 4.6f), clamp(cy * k - 4, -8.6f, -2.5f), clamp(cz * k, -4.6f, 4.6f)};
            }
            case MEJILLA -> {
                // a un costado de la nariz, a la altura de la boca
                float side = x < 0 ? -1 : 1;
                return new float[]{side * clamp(Math.abs(x), 2.2f, 4.4f), clamp(y, -3.2f, -0.6f), -4.6f};
            }
            case NARIZ -> {
                return new float[]{clamp(x, -0.6f, 0.6f), -2.6f, -4.6f};
            }
            case FRENTE -> {
                return new float[]{clamp(x, -3.5f, 3.5f), clamp(y, -7.4f, -5.0f), -4.6f};
            }
            case BARBILLA -> {
                // la mano por debajo de la barbilla
                return new float[]{clamp(x, -1.8f, 1.8f), 0.6f, clamp(z, -3.6f, -2.0f)};
            }
            case OREJA_D -> {
                return new float[]{-4.6f, clamp(y, -5.5f, -1.5f), clamp(z, -2.0f, 2.0f)};
            }
            case OREJA_I -> {
                return new float[]{4.6f, clamp(y, -5.5f, -1.5f), clamp(z, -2.0f, 2.0f)};
            }
            case ESPALDA -> {
                return new float[]{clamp(x, -3.6f, 3.6f), clamp(y, 1.0f, 7.0f), 2.4f};
            }
            case COSQUILLAS -> {
                return new float[]{clamp(x, -3.8f, 3.8f), clamp(y, 7.0f, 11.0f), 2.4f};
            }
            case HOMBRO_D -> {
                return new float[]{-6.2f, -0.6f, 0.2f};
            }
            case HOMBRO_I -> {
                return new float[]{6.2f, -0.6f, 0.2f};
            }
            case MANO_D, MANO_I, BRAZO_D, BRAZO_I -> {
                float sx = rightSide(zone) ? -5 : 5, sy = 2, sz = 0;
                // fuera del propio torso y la cabeza: si cae dentro, va adelante
                if (Math.abs(x) < 5.0f && y > -9 && y < 13 && z > -3.2f) {
                    z = -3.2f;
                }
                float dx = x - sx, dy = y - sy, dz = z - sz;
                float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                float want = clamp(len, 4.0f, 11.0f);
                if (len < 1.0e-3f) {
                    return new float[]{sx, sy + 10, sz};
                }
                float k = want / len;
                return new float[]{sx + dx * k, sy + dy * k, sz + dz * k};
            }
            default -> {
                return new float[]{clamp(x, -16, 16), clamp(y, -16, 26), clamp(z, -16, 16)};
            }
        }
    }
}
