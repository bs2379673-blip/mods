package com.starlevi.starbody.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.starlevi.starbody.Traits;

/**
 * Los pixeles extra que necesita Just Expressions / Fresh Moves (EMF + ETF) para animar ojos y
 * cejas. Se copian de la cara de la skin a los lugares sin uso donde el paquete los busca
 * (posiciones sacadas de su modelo player.jem, licencia CC0). La cara de la skin no se toca.
 * <p>
 * Ojos en las columnas 1-2 (derecho) y 5-6 (izquierdo) de la cara, como en las skins normales.
 */
public final class Expressions {
    private static final int FACE_U = 8, FACE_V = 8, HAT_U = 40;
    private static final int[] COLS = {1, 2, 5, 6};

    private Expressions() {
    }

    /** Filas validas donde pueden empezar los ojos (desde arriba de la cara). */
    public static int minEyeRow(int size) {
        return size == 2 ? 2 : 3;
    }

    public static int maxEyeRow(int size) {
        return size == 2 ? 5 : 6;
    }

    public static int clampEye(int size, int row) {
        return Math.max(minEyeRow(size), Math.min(maxEyeRow(size), row));
    }

    public static int clampBrow(int row) {
        return Math.max(2, Math.min(5, row));
    }

    /**
     * Color del parpado: la piel de abajo del ojo (la mejilla), un poco mas oscura, igual en los dos
     * pixeles de cada ojo (como hacen las skins de ejemplo del paquete).
     */
    private static int lid(NativeImage img, int eye, int belowRow) {
        int a = face(img, COLS[eye * 2], belowRow), b = face(img, COLS[eye * 2 + 1], belowRow);
        int r = ((a >> 16 & 255) + (b >> 16 & 255)) / 2 * 9 / 10, g = ((a >> 8 & 255) + (b >> 8 & 255)) / 2 * 9 / 10,
                bl = ((a & 255) + (b & 255)) / 2 * 9 / 10;
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    private static int face(NativeImage img, int col, int row) {
        return img.getPixel(FACE_U + col, FACE_V + Math.max(0, Math.min(7, row)));
    }

    /** Escribe los pixeles extra en la imagen (una copia de la skin). */
    public static void apply(NativeImage img, Traits t) {
        if (t.eyes == 1) {
            int r = clampEye(1, t.eyeRow);
            int y = (r - 3) * 2;
            for (int i = 0; i < 4; i++) {
                img.setPixel(4 + i, y, lid(img, i / 2, r + 1));          // parpado
                img.setPixel(4 + i, y + 1, face(img, COLS[i], r));      // blanco y pupila
            }
        } else if (t.eyes == 2) {
            int r = clampEye(2, t.eyeRow);
            int x0 = r >= 4 ? 24 : 28, y = r == 5 || r == 3 ? 5 : 2;
            for (int i = 0; i < 4; i++) {
                img.setPixel(x0 + i, y, lid(img, i / 2, r + 2));
                img.setPixel(x0 + i, y + 1, face(img, COLS[i], r));
                img.setPixel(x0 + i, y + 2, face(img, COLS[i], r + 1));
            }
        }
        if (t.brows == 1) {
            int b = clampBrow(t.browRow);
            int y = (b - 2) * 2;
            for (int i = 0; i < 4; i++) {
                img.setPixel(60 + i, y, face(img, COLS[i], b - 1));     // la piel que queda cuando la ceja se mueve
                img.setPixel(60 + i, y + 1, face(img, COLS[i], b));     // la ceja
            }
        } else if (t.brows == 2) {
            int b = clampBrow(t.browRow);
            int y = b + 2;
            for (int i = 0; i < 4; i++) {
                img.setPixel(56 + i, y, img.getPixel(HAT_U + COLS[i], FACE_V + b));
            }
        }
    }
}
