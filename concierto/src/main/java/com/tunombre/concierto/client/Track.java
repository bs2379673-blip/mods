package com.tunombre.concierto.client;

/** analisis de una cancion: por cada cuadro (60 por segundo) 32 bandas + volumen, graves, medios, agudos y golpe */
public final class Track {
    public static final int BANDS = 32, STRIDE = BANDS + 7;
    public static final int LEVEL = 32, BASS = 33, MID = 34, HIGH = 35, BEAT = 36, KICK = 36, SNARE = 37, ACCENT = 38;
    public static final float FPS = 60F;

    public final float[] data;
    public final int frames;
    public final boolean synthetic;

    public Track(float[] data, int frames, boolean synthetic) {
        this.data = data;
        this.frames = frames;
        this.synthetic = synthetic;
    }

    public float duration() { return frames / FPS; }

    /** valor interpolado del campo k en el instante t (segundos) */
    public float at(float t, int k) {
        float f = t * FPS;
        if (f < 0F || frames < 2) return 0F;
        int i = (int) f;
        if (i >= frames - 1) return data[(frames - 1) * STRIDE + k];
        float fr = f - i;
        return data[i * STRIDE + k] * (1F - fr) + data[(i + 1) * STRIDE + k] * fr;
    }

    /** los golpes no se interpolan: se toma el cuadro mas cercano */
    public float beatAt(float t) { return hitAt(t, KICK); }

    public float hitAt(float t, int k) {
        int i = (int) (t * FPS);
        if (i < 0 || i >= frames) return 0F;
        return data[i * STRIDE + k];
    }

    /** analisis empaquetado en el mod (tools/concierto/make_tracks.py): 'CTK2' | frames(int LE) | stride | bytes */
    public static Track fromBytes(byte[] b) {
        if (b.length < 9 || b[0] != 'C' || b[1] != 'T' || b[2] != 'K' || b[3] != '2') return null;
        int frames = (b[4] & 0xFF) | (b[5] & 0xFF) << 8 | (b[6] & 0xFF) << 16 | (b[7] & 0xFF) << 24;
        int stride = b[8] & 0xFF;
        if (stride != STRIDE || b.length < 9 + (long) frames * stride) return null;
        float[] d = new float[frames * stride];
        for (int i = 0; i < d.length; i++) d[i] = (b[9 + i] & 0xFF) / 255F;
        return new Track(d, frames, false);
    }

    /** pista inventada (128 BPM) para cuando falta el archivo de audio: asi el cielo igual se mueve */
    public static Track synthetic(float seconds) {
        int n = (int) (seconds * FPS);
        float[] d = new float[n * STRIDE];
        for (int f = 0; f < n; f++) {
            float t = f / FPS;
            float beatPhase = (t * 128F / 60F) % 1F;
            float kick = (float) Math.exp(-beatPhase * 6F);
            float sec = 0.5F + 0.5F * (float) Math.sin(t * 0.2F);
            for (int b = 0; b < BANDS; b++) {
                float x = b / (float) (BANDS - 1);
                float v = kick * (1F - x) * 0.9F + 0.45F * sec * (float) Math.abs(Math.sin(t * (1.3F + b * 0.37F) + b)) * (0.4F + 0.6F * x);
                d[f * STRIDE + b] = Math.min(1F, v);
            }
            d[f * STRIDE + LEVEL] = 0.35F + 0.55F * kick * sec;
            d[f * STRIDE + BASS] = kick;
            d[f * STRIDE + MID] = 0.3F + 0.4F * sec;
            d[f * STRIDE + HIGH] = 0.25F + 0.25F * (float) Math.abs(Math.sin(t * 7F));
            d[f * STRIDE + BEAT] = beatPhase < 0.03F ? 1F : 0F;
        }
        return new Track(d, n, true);
    }
}
