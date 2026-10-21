package com.tunombre.cosmeticos.geo;

public final class Col {
    private Col() { }

    public static final int WHITE = 0xFFFFFFFF;

    public static int argb(float r, float g, float b, float a) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(float v) { return Math.max(0, Math.min(255, (int) (v * 255F + 0.5F))); }

    public static int mix(int c0, int c1, float k) {
        k = Math.max(0, Math.min(1, k));
        int a = (int) (((c0 >>> 24) & 255) * (1 - k) + ((c1 >>> 24) & 255) * k), r = (int) (((c0 >> 16) & 255) * (1 - k) + ((c1 >> 16) & 255) * k);
        int g = (int) (((c0 >> 8) & 255) * (1 - k) + ((c1 >> 8) & 255) * k), b = (int) ((c0 & 255) * (1 - k) + (c1 & 255) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int alpha(int c, float a) { return (((int) (((c >>> 24) & 255) * Math.max(0, Math.min(1, a)))) << 24) | (c & 0xFFFFFF); }

    /** multiplica los canales rgb (para oscurecer o aclarar sin tocar el alfa) */
    public static int shade(int c, float k) {
        int r = Math.min(255, (int) (((c >> 16) & 255) * k)), g = Math.min(255, (int) (((c >> 8) & 255) * k)), b = Math.min(255, (int) ((c & 255) * k));
        return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    public static int hsv(float h, float s, float v, float a) {
        h = h - (float) Math.floor(h);
        float r, g, b; int i = (int) (h * 6); float f = h * 6 - i, p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        switch (i % 6) { case 0 -> { r = v; g = t; b = p; } case 1 -> { r = q; g = v; b = p; } case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; } case 4 -> { r = t; g = p; b = v; } default -> { r = v; g = p; b = q; } }
        return argb(r, g, b, a);
    }
}
