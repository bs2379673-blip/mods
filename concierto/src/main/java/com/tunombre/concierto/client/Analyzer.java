package com.tunombre.concierto.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** lee un .ogg, saca el espectro con una FFT (2048 puntos, 60 cuadros por segundo) y lo normaliza para que el cielo use todo su rango */
public final class Analyzer {
    private static final int N = 2048;

    private Analyzer() { }

    /** busca la cancion en config/concierto, en la carpeta del mod de sonidos y en los resource packs. null = no esta. */
    public static byte[] find(String song) {
        Path cfg = FMLPaths.CONFIGDIR.get();
        Path[] files = {
                cfg.resolve("concierto").resolve(song + ".ogg"),
                cfg.resolve("customsounds").resolve("sounds").resolve(song + ".ogg"),
                cfg.resolve("customsounds").resolve(song + ".ogg")
        };
        for (Path p : files) {
            try {
                if (Files.isRegularFile(p)) return Files.readAllBytes(p);
            } catch (IOException ignored) { }
        }
        for (String ns : new String[]{"customsounds", "concierto"}) {
            try {
                var res = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation(ns, "sounds/" + song + ".ogg"));
                if (res.isPresent()) try (InputStream in = res.get().open()) { return in.readAllBytes(); }
            } catch (Exception ignored) { }
        }
        return null;
    }

    public static Track analyze(byte[] ogg) {
        ByteBuffer buf = MemoryUtil.memAlloc(ogg.length);
        buf.put(ogg).flip();
        ShortBuffer pcm = null;
        try (MemoryStack st = MemoryStack.stackPush()) {
            IntBuffer ch = st.mallocInt(1), sr = st.mallocInt(1);
            pcm = STBVorbis.stb_vorbis_decode_memory(buf, ch, sr);
            if (pcm == null) return null;
            return compute(pcm, ch.get(0), sr.get(0));
        } finally {
            if (pcm != null) MemoryUtil.memFree(pcm);
            MemoryUtil.memFree(buf);
        }
    }

    private static Track compute(ShortBuffer pcm, int channels, int sr) {
        int total = pcm.limit() / channels;
        int hop = Math.max(1, Math.round(sr / Track.FPS));
        int frames = (total - N) / hop;
        if (frames < 2) return null;

        // bandas: 32, espaciadas en escala logaritmica entre 40 Hz y 14 kHz
        int[][] range = new int[Track.BANDS][2];
        double binHz = (double) sr / N;
        for (int b = 0; b < Track.BANDS; b++) {
            double lo = 40.0 * Math.pow(14000.0 / 40.0, b / (double) Track.BANDS);
            double hi = 40.0 * Math.pow(14000.0 / 40.0, (b + 1) / (double) Track.BANDS);
            int k0 = (int) Math.ceil(lo / binHz), k1 = (int) Math.ceil(hi / binHz) - 1;
            if (k1 < k0) { k0 = (int) Math.round((lo + hi) * 0.5 / binHz); k1 = k0; }
            range[b][0] = Math.min(k0, N / 2 - 1);
            range[b][1] = Math.min(k1, N / 2 - 1);
        }

        double[] win = new double[N];
        for (int i = 0; i < N; i++) win[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (N - 1));
        Fft fft = new Fft(N);
        double[] re = new double[N], im = new double[N];
        float[][] raw = new float[frames][Track.BANDS];
        float[] rms = new float[frames];

        for (int f = 0; f < frames; f++) {
            int s0 = f * hop;
            double e = 0;
            for (int i = 0; i < N; i++) {
                double v = 0;
                int base = (s0 + i) * channels;
                for (int c = 0; c < channels; c++) v += pcm.get(base + c);
                v /= 32768.0 * channels;
                e += v * v;
                re[i] = v * win[i];
                im[i] = 0;
            }
            rms[f] = (float) Math.sqrt(e / N);
            fft.transform(re, im);
            for (int b = 0; b < Track.BANDS; b++) {
                double sum = 0;
                int cnt = range[b][1] - range[b][0] + 1;
                for (int k = range[b][0]; k <= range[b][1]; k++) sum += Math.sqrt(re[k] * re[k] + im[k] * im[k]) / N;
                raw[f][b] = (float) Math.log10(1.0 + (sum / cnt) * 400.0);
            }
        }

        // normalizacion por banda: percentil 5 -> 0, percentil 97 -> 1
        float[] tmp = new float[frames];
        float[] lo = new float[Track.BANDS], hi = new float[Track.BANDS];
        for (int b = 0; b < Track.BANDS; b++) {
            for (int f = 0; f < frames; f++) tmp[f] = raw[f][b];
            Arrays.sort(tmp);
            lo[b] = tmp[(int) (frames * 0.05)];
            hi[b] = tmp[Math.min(frames - 1, (int) (frames * 0.97))] + 1e-6F;
        }
        System.arraycopy(rms, 0, tmp, 0, frames);
        Arrays.sort(tmp);
        float rmsHi = tmp[Math.min(frames - 1, (int) (frames * 0.97))] + 1e-6F;

        float[] out = new float[frames * Track.STRIDE];
        float[] bass = new float[frames];
        for (int f = 0; f < frames; f++) {
            int o = f * Track.STRIDE;
            float sb = 0, sm = 0, sh = 0;
            for (int b = 0; b < Track.BANDS; b++) {
                float v = (raw[f][b] - lo[b]) / Math.max(1e-6F, hi[b] - lo[b]);
                v = (float) Math.pow(Math.max(0F, Math.min(1F, v)), 1.3);
                out[o + b] = v;
                if (b < 6) sb += v; else if (b < 18) sm += v; else sh += v;
            }
            bass[f] = sb / 6F;
            out[o + Track.BASS] = bass[f];
            out[o + Track.MID] = sm / 12F;
            out[o + Track.HIGH] = sh / 14F;
            out[o + Track.LEVEL] = Math.min(1F, rms[f] / rmsHi);
        }

        // golpe: salto de los graves respecto a los 8 cuadros anteriores
        float[] flux = new float[frames];
        for (int f = 0; f < frames; f++) {
            float avg = 0;
            int c = 0;
            for (int j = Math.max(0, f - 8); j < f; j++) { avg += bass[j]; c++; }
            flux[f] = Math.max(0F, bass[f] - (c > 0 ? avg / c : 0F));
        }
        System.arraycopy(flux, 0, tmp, 0, frames);
        Arrays.sort(tmp);
        float fHi = tmp[Math.min(frames - 1, (int) (frames * 0.95))] + 1e-6F;
        for (int f = 0; f < frames; f++) {
            float v = Math.min(1F, flux[f] / fHi);
            out[f * Track.STRIDE + Track.BEAT] = v > 0.55F ? v : 0F;
        }
        return new Track(out, frames, false);
    }

    /** FFT radix-2 iterativa */
    private static final class Fft {
        final int n;
        final double[] cos, sin;
        final int[] rev;

        Fft(int n) {
            this.n = n;
            cos = new double[n / 2];
            sin = new double[n / 2];
            for (int i = 0; i < n / 2; i++) {
                cos[i] = Math.cos(2 * Math.PI * i / n);
                sin[i] = -Math.sin(2 * Math.PI * i / n);
            }
            rev = new int[n];
            int bits = Integer.numberOfTrailingZeros(n);
            for (int i = 0; i < n; i++) rev[i] = Integer.reverse(i) >>> (32 - bits);
        }

        void transform(double[] re, double[] im) {
            for (int i = 0; i < n; i++) {
                int j = rev[i];
                if (j > i) {
                    double t = re[i]; re[i] = re[j]; re[j] = t;
                    t = im[i]; im[i] = im[j]; im[j] = t;
                }
            }
            for (int size = 2; size <= n; size <<= 1) {
                int half = size >> 1, step = n / size;
                for (int i = 0; i < n; i += size) {
                    for (int j = 0, k = 0; j < half; j++, k += step) {
                        int a = i + j, b = a + half;
                        double tr = re[b] * cos[k] - im[b] * sin[k];
                        double ti = re[b] * sin[k] + im[b] * cos[k];
                        re[b] = re[a] - tr; im[b] = im[a] - ti;
                        re[a] += tr; im[a] += ti;
                    }
                }
            }
        }
    }
}
