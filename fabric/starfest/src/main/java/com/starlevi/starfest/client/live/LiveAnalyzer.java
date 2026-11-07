package com.starlevi.starfest.client.live;

import com.starlevi.starfest.client.Analysis;
import com.starlevi.starfest.client.Beat;

import java.util.ArrayDeque;

/**
 * Analisis del ritmo EN VIVO (para el audio de las pantallas de StarFrames, que no se conoce de
 * antemano): graves, medios, agudos, espectro, golpes, tempo con su fase e intensidad, todo con
 * niveles que se adaptan solos a cada video. El resultado se entrega con el retraso del parlante,
 * asi las luces van justo con lo que se escucha.
 */
public final class LiveAnalyzer {
    private static final int N = 2048;
    private static final int HIST = 600; // ~13 s de golpes para calcular el tempo

    private final float rate;
    private final int hop;
    private final float fps;
    private final float[] ring = new float[N];
    private int ringPos;
    private int filled;
    private int sinceHop;
    private long hops;
    private final float[] win = new float[N];
    private final float[] re = new float[N];
    private final float[] im = new float[N];
    private final int bassLo, bassHi, midHi, highHi;
    private final int[] edges = new int[Analysis.BANDS + 1];

    // niveles adaptables: piso y techo que siguen a la cancion
    private final Norm nBass = new Norm(), nMid = new Norm(), nHigh = new Norm(), nLevel = new Norm();
    private final Norm[] nSpec = new Norm[Analysis.BANDS];
    private float eBass, eMid, eHigh, eLevel, pulse, kick;
    private final float[] eSpec = new float[Analysis.BANDS];
    private float prevB, prevM, prevH;
    private final float[] flux = new float[HIST];
    private final float[] onsets = new float[HIST];
    private float fluxMean;
    private int lastOnset = -1000;
    private double shortAvg, longAvg;
    private float bpm = 120, periodHops;
    private double phaseHop;
    private float conf;

    private record Timed(long at, Beat beat) {
    }

    private final ArrayDeque<Timed> queue = new ArrayDeque<>();
    private volatile Beat current = Beat.SILENCE;
    private volatile long lastAudio;

    public LiveAnalyzer(float rate) {
        this.rate = rate;
        this.hop = rate > 30000 ? 1024 : 512;
        this.fps = rate / this.hop;
        this.periodHops = this.fps * 60 / this.bpm;
        for (int i = 0; i < N; i++) {
            this.win[i] = 0.5f - 0.5f * (float) Math.cos(2 * Math.PI * i / (N - 1));
        }
        this.bassLo = bin(30);
        this.bassHi = bin(150);
        this.midHi = bin(2500);
        this.highHi = Math.min(N / 2, bin(14000));
        for (int j = 0; j <= Analysis.BANDS; j++) {
            this.edges[j] = Math.min(N / 2, bin((float) (40 * Math.pow(16000 / 40.0, j / (double) Analysis.BANDS))));
            if (j > 0 && this.edges[j] <= this.edges[j - 1]) {
                this.edges[j] = this.edges[j - 1] + 1;
            }
        }
        for (int j = 0; j < Analysis.BANDS; j++) {
            this.nSpec[j] = new Norm();
        }
    }

    private int bin(float hz) {
        return Math.max(1, Math.round(hz * N / this.rate));
    }

    /** Muestras de 16 bits intercaladas (canales), tal como salen del reproductor. latencyNanos: retraso del parlante. */
    public void feed(short[] pcm, int frames, int channels, long latencyNanos) {
        this.lastAudio = System.nanoTime();
        for (int f = 0; f < frames; f++) {
            float s = 0;
            for (int c = 0; c < channels; c++) {
                s += pcm[f * channels + c];
            }
            this.ring[this.ringPos] = s / channels / 32768f;
            this.ringPos = (this.ringPos + 1) % N;
            if (this.filled < N) {
                this.filled++;
            }
            if (++this.sinceHop >= this.hop && this.filled >= N) {
                this.sinceHop = 0;
                step(latencyNanos);
            }
        }
    }

    private void step(long latency) {
        double sq = 0;
        for (int k = 0; k < N; k++) {
            float v = this.ring[(this.ringPos + k) % N];
            sq += v * v;
            this.re[k] = v * this.win[k];
            this.im[k] = 0;
        }
        fft(this.re, this.im);
        float b = this.nBass.put(band(this.bassLo, this.bassHi));
        float m = this.nMid.put(band(this.bassHi, this.midHi));
        float h = this.nHigh.put(band(this.midHi, this.highHi));
        float raw = (float) Math.sqrt(sq / N);
        float l = this.nLevel.put(raw);
        float[] spec = new float[Analysis.BANDS];
        for (int j = 0; j < Analysis.BANDS; j++) {
            float v = this.nSpec[j].put(band(this.edges[j], this.edges[j + 1]));
            this.eSpec[j] = Math.max(v, this.eSpec[j] * decay(0.12f));
            spec[j] = this.eSpec[j];
        }
        this.eBass = Math.max(b, this.eBass * decay(0.18f));
        this.eMid = Math.max(m, this.eMid * decay(0.22f));
        this.eHigh = Math.max(h, this.eHigh * decay(0.10f));
        this.eLevel = Math.max(l, this.eLevel * decay(0.30f));

        // golpes
        float db = Math.max(0, b - this.prevB);
        float fx = db + 0.6f * Math.max(0, m - this.prevM) + 0.3f * Math.max(0, h - this.prevH);
        this.prevB = b;
        this.prevM = m;
        this.prevH = h;
        int idx = (int) (this.hops % HIST);
        this.flux[idx] = fx;
        this.fluxMean += (fx - this.fluxMean) * (1 - decay(0.4f));
        boolean onset = fx > this.fluxMean * 1.6f + 0.06f && this.hops - this.lastOnset > this.fps * 0.15f;
        this.onsets[idx] = onset ? Math.min(1, fx) : 0;
        if (onset) {
            this.lastOnset = (int) this.hops;
            this.pulse = Math.max(this.pulse, Math.min(1, fx * 1.5f));
            if (db > 0.12f) {
                this.kick = Math.max(this.kick, Math.min(1, db * 2.2f));
            }
        }
        this.pulse *= decay(0.14f);
        this.kick *= decay(0.12f);

        // intensidad de la parte: 3 s contra 40 s
        // (al principio los promedios se arman con lo que haya, para no arrancar en cero)
        // (volumen real, sin adaptar: asi se nota cuando la cancion sube)
        this.shortAvg += (raw - this.shortAvg) / Math.min(this.fps * 3, this.hops + 1);
        this.longAvg += (raw - this.longAvg) / Math.min(this.fps * 40, this.hops + 1);
        float inten = (float) Math.min(2, this.shortAvg / Math.max(1e-3, this.longAvg));

        // tempo: se recalcula cada medio segundo con lo ultimo que sono
        if (this.hops % Math.max(1, (int) (this.fps / 2)) == 0 && this.hops > this.fps * 4) {
            tempo();
        }
        this.hops++;
        float t = this.hops / this.fps;
        float beats = (float) ((this.hops - this.phaseHop) / this.periodHops);
        Beat out = new Beat(true, t, this.eBass, this.eMid, this.eHigh, this.eLevel, this.pulse, this.kick,
                Math.max(0.6f, inten), Math.max(0, beats), this.bpm, spec);
        synchronized (this.queue) {
            this.queue.add(new Timed(System.nanoTime() + latency, out));
            while (this.queue.size() > 400) {
                this.queue.poll();
            }
        }
    }

    private void tempo() {
        int n = (int) Math.min(HIST, this.hops);
        int minLag = Math.max(1, Math.round(this.fps * 60 / 190f)), maxLag = Math.round(this.fps * 60 / 70f);
        double best = -1;
        int bestLag = Math.round(this.periodHops);
        for (int lag = minLag; lag <= maxLag && lag < n / 2; lag++) {
            double s = 0;
            for (int i = lag; i < n; i++) {
                s += at(i) * at(i - lag);
            }
            double bpmL = 60.0 * this.fps / lag;
            s *= Math.exp(-0.5 * Math.pow(Math.log(bpmL / 120.0) / Math.log(2) / 0.9, 2));
            // un poco de preferencia por el tempo que ya teniamos (que no salte)
            if (Math.abs(lag - this.periodHops) < 2) {
                s *= 1.15;
            }
            if (s > best) {
                best = s;
                bestLag = lag;
            }
        }
        if (best <= 0) {
            return;
        }
        float target = bestLag;
        this.periodHops += (target - this.periodHops) * 0.35f;
        this.bpm = 60.0f * this.fps / this.periodHops;
        // fase: promedio circular de los golpes recientes
        double sr = 0, si = 0;
        long start = this.hops - n;
        for (int i = 0; i < n; i++) {
            float o = this.onsets[(int) ((start + i) % HIST + HIST) % HIST];
            if (o > 0) {
                double a = 2 * Math.PI * (start + i) / this.periodHops;
                sr += o * Math.cos(a);
                si += o * Math.sin(a);
            }
        }
        if (sr * sr + si * si > 1e-6) {
            double ph = Math.atan2(si, sr);
            double want = ph / (2 * Math.PI) * this.periodHops;
            // acomodar la fase sin saltos bruscos (la cuenta de tiempos sigue siendo continua)
            double cur = this.phaseHop % this.periodHops;
            double diff = want - cur;
            diff -= Math.round(diff / this.periodHops) * this.periodHops;
            this.phaseHop += diff * 0.3;
        }
        this.conf = (float) Math.min(1, best / Math.max(1e-6, energy(n)) * 2);
    }

    private float at(int i) {
        long start = this.hops - Math.min(HIST, this.hops);
        return this.flux[(int) (((start + i) % HIST + HIST) % HIST)];
    }

    private double energy(int n) {
        double s = 0;
        for (int i = 0; i < n; i++) {
            float v = at(i);
            s += v * v;
        }
        return s;
    }

    private float decay(float seconds) {
        return (float) Math.exp(-1.0 / (this.fps * seconds));
    }

    private float band(int lo, int hi) {
        double s = 0;
        for (int k = lo; k < hi; k++) {
            s += Math.sqrt(this.re[k] * this.re[k] + this.im[k] * this.im[k]);
        }
        return (float) (s / Math.max(1, hi - lo));
    }

    /** El estado que corresponde a lo que se escucha ahora (o silencio si el video no suena). */
    public Beat get() {
        long now = System.nanoTime();
        if (now - this.lastAudio > 1_500_000_000L) {
            return Beat.SILENCE;
        }
        synchronized (this.queue) {
            while (!this.queue.isEmpty() && this.queue.peek().at() <= now) {
                this.current = this.queue.poll().beat();
            }
        }
        return this.current;
    }

    public float confidence() {
        return this.conf;
    }

    /** Piso y techo que se adaptan: el techo baja despacio, el piso sube despacio. */
    private final class Norm {
        float lo = Float.MAX_VALUE, hi = 1e-6f;

        float put(float v) {
            if (this.lo == Float.MAX_VALUE) {
                this.lo = v;
                this.hi = v + 1e-4f;
            }
            this.hi = Math.max(v, this.hi * (1 - 0.15f / LiveAnalyzer.this.fps));
            this.lo = Math.min(v, this.lo + (this.hi - this.lo) * 0.02f / LiveAnalyzer.this.fps);
            float r = (v - this.lo) / Math.max(1e-6f, this.hi - this.lo);
            return Math.max(0, Math.min(1.2f, r));
        }
    }

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
}
