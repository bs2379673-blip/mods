package com.tunombre.cosmeticos.geo;

public final class Noise {
    private Noise() { }

    public static float hash(int a) {
        a = (a ^ 61) ^ (a >>> 16); a *= 9; a ^= a >>> 4; a *= 0x27d4eb2d; a ^= a >>> 15;
        return (a & 0xFFFFFF) / 16777215F;
    }

    public static float hash(int a, int b) { return hash(a * 73856093 ^ b * 19349663); }

    /** ruido de valor 1D suave en [-1, 1] */
    public static float n1(float x, int seed) {
        int i = (int) Math.floor(x);
        float f = x - i;
        f = f * f * (3 - 2 * f);
        float a = hash(i, seed) * 2 - 1, b = hash(i + 1, seed) * 2 - 1;
        return a + (b - a) * f;
    }

    public static float smooth(float e0, float e1, float x) {
        float t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    public static float clamp(float v, float a, float b) { return v < a ? a : Math.min(v, b); }

    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
