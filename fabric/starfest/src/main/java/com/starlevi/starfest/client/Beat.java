package com.starlevi.starfest.client;

/**
 * Como esta la musica en un instante: niveles (0..1), golpes, intensidad de la parte y la grilla de
 * tiempos (beats = tiempos transcurridos, con decimales).
 */
public record Beat(boolean playing, float t, float bass, float mid, float high, float level, float pulse, float kick,
                   float intensity, float beats, float bpm, float[] spec) {
    public static final Beat SILENCE = new Beat(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 120, new float[Analysis.BANDS]);

    /** Banda del espectro (0 graves .. 1 agudos), interpolada. */
    public float band(float x) {
        float f = Math.max(0, Math.min(1, x)) * (this.spec.length - 1);
        int i = (int) f;
        int j = Math.min(this.spec.length - 1, i + 1);
        return this.spec[i] + (this.spec[j] - this.spec[i]) * (f - i);
    }

    public int beat() {
        return (int) Math.floor(this.beats);
    }

    /** 0..1 dentro del tiempo actual. */
    public float phase() {
        return this.beats - (float) Math.floor(this.beats);
    }

    public int bar() {
        return beat() / 4;
    }

    /** Cambia cada 8 compases: para rotar figuras y colores. */
    public int section() {
        return bar() / 8;
    }

    /** La parte fuerte de la cancion. */
    public boolean drop() {
        return this.intensity > 1.18f;
    }
}
