package com.starlevi.starfest.client;

/**
 * Como esta la musica en un instante: niveles (0..1), golpes, intensidad de la parte y la grilla de
 * tiempos (beats = tiempos transcurridos, con decimales).
 */
public record Beat(boolean playing, float t, float bass, float mid, float high, float level, float pulse, float kick,
                   float intensity, float beats, float bpm) {
    public static final Beat SILENCE = new Beat(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 120);

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
