package com.starlevi.starfest.client;

import net.minecraft.client.sounds.JOrbisAudioStream;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Analisis del ritmo de cualquier cancion (.ogg), hecho en el cliente al recibirla: graves, medios y
 * agudos cuadro a cuadro, golpes (onsets), tempo (BPM) con su fase, e intensidad de cada parte
 * (para saber cuando viene el "drop"). Asi las luces siguen la musica sin que nadie tenga que
 * preparar nada para cada cancion.
 */
public final class Analysis {
    public final float fps;
    public final int frames;
    public final float[] bass;
    public final float[] mid;
    public final float[] high;
    public final float[] level;
    /** Golpe reciente (1 en el golpe, decae rapido). */
    public final float[] pulse;
    /** Golpe de graves reciente (bombo). */
    public final float[] kick;
    /** Intensidad de la parte de la cancion: ~1 normal, mas de 1.2 = la parte fuerte. */
    public final float[] intensity;
    public final float bpm;
    /** Segundo del primer pulso de la grilla de tiempos. */
    public final float beatOffset;
    public final float confidence;
    public final float seconds;

    private Analysis(float fps, int frames, float[] bass, float[] mid, float[] high, float[] level, float[] pulse, float[] kick,
                     float[] intensity, float bpm, float beatOffset, float confidence) {
        this.fps = fps;
        this.frames = frames;
        this.bass = bass;
        this.mid = mid;
        this.high = high;
        this.level = level;
        this.pulse = pulse;
        this.kick = kick;
        this.intensity = intensity;
        this.bpm = bpm;
        this.beatOffset = beatOffset;
        this.confidence = confidence;
        this.seconds = frames / fps;
    }

    // ------------------------------------------------------------------ lectura en tiempo real

    private static float at(float[] a, float i) {
        if (a.length == 0) {
            return 0;
        }
        if (i <= 0) {
            return a[0];
        }
        int k = (int) i;
        if (k >= a.length - 1) {
            return a[a.length - 1];
        }
        float f = i - k;
        return a[k] + (a[k + 1] - a[k]) * f;
    }

    /** Estado de la musica en el segundo t. */
    public Beat sample(float t) {
        float i = t * this.fps;
        float beats = (t - this.beatOffset) * this.bpm / 60.0f;
        if (beats < 0) {
            beats = 0;
        }
        return new Beat(true, t, at(this.bass, i), at(this.mid, i), at(this.high, i), at(this.level, i), at(this.pulse, i),
                at(this.kick, i), at(this.intensity, i), beats, this.bpm);
    }

    // ------------------------------------------------------------------ analisis

    private static final int N = 2048;

    public static Analysis analyze(byte[] ogg) throws IOException {
        try (JOrbisAudioStream in = new JOrbisAudioStream(new ByteArrayInputStream(ogg))) {
            AudioFormat f = in.getFormat();
            int channels = Math.max(1, f.getChannels());
            float rate = f.getSampleRate();
            int hop = rate > 30000 ? 1024 : 512;
            float fps = rate / hop;
            float[] ring = new float[N];
            float[] win = new float[N];
            for (int i = 0; i < N; i++) {
                win[i] = 0.5f - 0.5f * (float) Math.cos(2 * Math.PI * i / (N - 1));
            }
            float[] re = new float[N], im = new float[N];
            int bassLo = bin(30, rate), bassHi = bin(150, rate), midHi = bin(2500, rate), highHi = Math.min(N / 2, bin(14000, rate));
            Growing gb = new Growing(), gm = new Growing(), gh = new Growing(), gl = new Growing();
            int[] state = new int[4]; // canal actual, posicion en el anillo, desde el ultimo salto, muestras llenas
            float[] acc = new float[1];
            while (in.readChunk(sample -> {
                acc[0] += sample;
                if (++state[0] < channels) {
                    return;
                }
                state[0] = 0;
                ring[state[1]] = acc[0] / channels;
                acc[0] = 0;
                state[1] = (state[1] + 1) % N;
                if (state[3] < N) {
                    state[3]++;
                }
                if (++state[2] >= hop && state[3] >= N) {
                    state[2] = 0;
                    double sq = 0;
                    for (int k = 0; k < N; k++) {
                        float v = ring[(state[1] + k) % N];
                        sq += v * v;
                        re[k] = v * win[k];
                        im[k] = 0;
                    }
                    fft(re, im);
                    gb.add(band(re, im, bassLo, bassHi));
                    gm.add(band(re, im, bassHi, midHi));
                    gh.add(band(re, im, midHi, highHi));
                    gl.add((float) Math.sqrt(sq / N));
                }
            })) {
                // sigue leyendo
            }
            return build(fps, gb.done(), gm.done(), gh.done(), gl.done());
        }
    }

    private static int bin(float hz, float rate) {
        return Math.max(1, Math.round(hz * N / rate));
    }

    private static float band(float[] re, float[] im, int lo, int hi) {
        double s = 0;
        for (int k = lo; k < hi; k++) {
            s += Math.sqrt(re[k] * re[k] + im[k] * im[k]);
        }
        return (float) (s / Math.max(1, hi - lo));
    }

    private static Analysis build(float fps, float[] b, float[] m, float[] h, float[] l) {
        int n = b.length;
        float[] bass = norm(b), mid = norm(m), high = norm(h), level = norm(l);
        // golpes: aumentos bruscos de energia (sobre todo de graves)
        float[] flux = new float[n], kickFlux = new float[n];
        for (int i = 1; i < n; i++) {
            float db = Math.max(0, bass[i] - bass[i - 1]);
            kickFlux[i] = db;
            flux[i] = db + 0.6f * Math.max(0, mid[i] - mid[i - 1]) + 0.3f * Math.max(0, high[i] - high[i - 1]);
        }
        float[] onset = peaks(flux, fps, 0.15f);
        float[] kickOn = peaks(kickFlux, fps, 0.2f);
        float[] pulse = decay(onset, fps, 0.14f);
        float[] kick = decay(kickOn, fps, 0.12f);
        // envolventes: suben de golpe y bajan suave (se ve mejor en las luces)
        float[] bassE = envelope(bass, fps, 0.18f), midE = envelope(mid, fps, 0.22f), highE = envelope(high, fps, 0.1f);
        float[] levelE = envelope(level, fps, 0.3f);
        // intensidad de la parte: energia promedio de unos 3 segundos contra la de toda la cancion
        float[] inten = new float[n];
        int w = Math.max(1, Math.round(fps * 1.5f));
        double total = 0;
        for (float v : level) {
            total += v;
        }
        float mean = (float) Math.max(1e-4, total / Math.max(1, n));
        double run = 0;
        int lo = 0, hi = -1;
        for (int i = 0; i < n; i++) {
            while (hi < Math.min(n - 1, i + w)) {
                run += level[++hi];
            }
            while (lo < i - w) {
                run -= level[lo++];
            }
            inten[i] = (float) Math.min(2.0, run / (hi - lo + 1) / mean);
        }
        // tempo: autocorrelacion de los golpes entre 70 y 190 BPM, con preferencia cerca de 120
        int minLag = Math.max(1, Math.round(fps * 60 / 190f)), maxLag = Math.round(fps * 60 / 70f);
        double best = -1, zero = 1e-9;
        int bestLag = Math.round(fps * 0.5f);
        for (int i = 0; i < n; i++) {
            zero += flux[i] * flux[i];
        }
        for (int lag = minLag; lag <= maxLag && lag < n; lag++) {
            double s = 0;
            for (int i = lag; i < n; i++) {
                s += flux[i] * flux[i - lag];
            }
            double bpm = 60.0 * fps / lag;
            double pref = Math.exp(-0.5 * Math.pow(Math.log(bpm / 120.0) / Math.log(2) / 0.9, 2));
            s *= pref;
            if (s > best) {
                best = s;
                bestLag = lag;
            }
        }
        float lagF = bestLag;
        if (bestLag > minLag && bestLag < maxLag) {
            double a = acf(flux, bestLag - 1), c0 = acf(flux, bestLag), c = acf(flux, bestLag + 1);
            double den = a - 2 * c0 + c;
            if (Math.abs(den) > 1e-9) {
                lagF = (float) (bestLag + 0.5 * (a - c) / den);
            }
        }
        // ajuste fino: periodo y fase que mejor alinean todos los golpes (suma circular)
        float bestP = lagF, offsetF = 0;
        double bestMag = -1;
        for (float per = lagF * 0.97f; per <= lagF * 1.03f; per += lagF * 0.0005f) {
            double sr = 0, si = 0;
            for (int i = 0; i < n; i++) {
                if (onset[i] > 0) {
                    double a = 2 * Math.PI * i / per;
                    sr += onset[i] * Math.cos(a);
                    si += onset[i] * Math.sin(a);
                }
            }
            double mag = sr * sr + si * si;
            if (mag > bestMag) {
                bestMag = mag;
                bestP = per;
                double ph = Math.atan2(si, sr);
                if (ph < 0) {
                    ph += 2 * Math.PI;
                }
                offsetF = (float) (ph / (2 * Math.PI) * per);
            }
        }
        float bpm = 60.0f * fps / bestP;
        float offset = offsetF / fps;
        float conf = (float) Math.min(1.0, acf(flux, Math.round(lagF)) / zero * 2.0);
        return new Analysis(fps, n, bassE, midE, highE, levelE, pulse, kick, inten, bpm, offset, conf);
    }

    private static double acf(float[] x, int lag) {
        double s = 0;
        for (int i = lag; i < x.length; i++) {
            s += x[i] * x[i - lag];
        }
        return s;
    }

    /** Escala para que el 95% de los valores quede entre 0 y 1 (canciones fuertes y suaves reaccionan igual). */
    private static float[] norm(float[] v) {
        if (v.length == 0) {
            return v;
        }
        float[] s = v.clone();
        Arrays.sort(s);
        float p = Math.max(1e-6f, s[Math.min(s.length - 1, (int) (s.length * 0.95f))]);
        float floor = s[(int) (s.length * 0.08f)];
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = Math.max(0, Math.min(1.2f, (v[i] - floor) / Math.max(1e-6f, p - floor)));
        }
        return out;
    }

    /** Picos sobre un umbral que se adapta (promedio local), separados al menos minGap segundos. */
    private static float[] peaks(float[] x, float fps, float minGap) {
        int n = x.length;
        float[] out = new float[n];
        int w = Math.max(2, Math.round(fps * 0.4f));
        double run = 0;
        int lo = 0, hi = -1;
        float[] sorted = x.clone();
        Arrays.sort(sorted);
        float top = Math.max(1e-6f, sorted[Math.min(n - 1, (int) (n * 0.98f))]);
        int last = -100000;
        int gap = Math.max(1, Math.round(minGap * fps));
        for (int i = 0; i < n; i++) {
            while (hi < Math.min(n - 1, i + w)) {
                run += x[++hi];
            }
            while (lo < i - w) {
                run -= x[lo++];
            }
            float mean = (float) (run / (hi - lo + 1));
            boolean peak = x[i] > mean * 1.5f + top * 0.05f;
            for (int k = Math.max(0, i - 3); k <= Math.min(n - 1, i + 3) && peak; k++) {
                if (x[k] > x[i]) {
                    peak = false;
                }
            }
            if (peak && i - last >= gap) {
                out[i] = Math.min(1.0f, x[i] / top);
                last = i;
            }
        }
        return out;
    }

    private static float[] decay(float[] on, float fps, float seconds) {
        float k = (float) Math.exp(-1.0 / (fps * seconds));
        float[] out = new float[on.length];
        float v = 0;
        for (int i = 0; i < on.length; i++) {
            v = Math.max(on[i], v * k);
            out[i] = v;
        }
        return out;
    }

    private static float[] envelope(float[] x, float fps, float release) {
        float k = (float) Math.exp(-1.0 / (fps * release));
        float[] out = new float[x.length];
        float v = 0;
        for (int i = 0; i < x.length; i++) {
            v = Math.max(x[i], v * k);
            out[i] = v;
        }
        return out;
    }

    /** FFT iterativa (radix 2) en el lugar. */
    private static void fft(float[] re, float[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                float t = re[i];
                re[i] = re[j];
                re[j] = t;
                t = im[i];
                im[i] = im[j];
                im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            float wr = (float) Math.cos(ang), wi = (float) Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                float cr = 1, ci = 0;
                for (int j = 0; j < len / 2; j++) {
                    int a = i + j, b = a + len / 2;
                    float xr = re[b] * cr - im[b] * ci, xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr;
                    im[b] = im[a] - xi;
                    re[a] += xr;
                    im[a] += xi;
                    float t = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = t;
                }
            }
        }
    }

    private static final class Growing {
        float[] a = new float[4096];
        int n;

        void add(float v) {
            if (this.n == this.a.length) {
                this.a = Arrays.copyOf(this.a, this.a.length * 2);
            }
            this.a[this.n++] = v;
        }

        float[] done() {
            return Arrays.copyOf(this.a, this.n);
        }
    }
}
