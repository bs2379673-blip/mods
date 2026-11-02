package com.starlevi.starfest.client;

import com.starlevi.starfest.StarFest;
import com.starlevi.starfest.net.Net;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente: la musica que suena y su ritmo. Fuentes: la zona del servidor (boliche, escenario) y los
 * tocadiscos cercanos. Cada bloque de show pregunta "como esta la musica aca" y recibe un Beat.
 */
public final class Show {
    /** Una musica sonando con su analisis. */
    public static final class Source {
        final String id;
        final Vec3 center;
        final double radius;
        final long startNanos;
        final float offset;
        final SoundInstance sound;
        volatile Analysis analysis;
        final float duration;
        /** Musica en vivo (pantallas de WaterFrames): su estado sale de aca en vez del analisis. */
        java.util.function.Supplier<Beat> live;

        Source(String id, Vec3 center, double radius, float offset, SoundInstance sound, float duration) {
            this.id = id;
            this.center = center;
            this.radius = radius;
            this.startNanos = System.nanoTime();
            this.offset = offset;
            this.sound = sound;
            this.duration = duration;
        }

        public float time() {
            return this.offset + (System.nanoTime() - this.startNanos) / 1.0e9f;
        }

        private long cachedFrame = -1;
        private Beat cached = Beat.SILENCE;

        public Beat beat() {
            if (this.live != null) {
                return this.live.get();
            }
            Analysis a = this.analysis;
            if (a == null) {
                return Beat.SILENCE;
            }
            long frame = frameId;
            if (frame != this.cachedFrame) {
                this.cachedFrame = frame;
                float t = time();
                this.cached = t >= 0 && t <= a.seconds + 0.5f ? a.sample(t) : Beat.SILENCE;
            }
            return this.cached;
        }
    }

    private static final Map<String, Source> SOURCES = new ConcurrentHashMap<>();
    /** Canciones guardadas en memoria (las ultimas). */
    private static final Map<String, byte[]> CACHE = new LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> e) {
            return size() > 6;
        }
    };
    private static final Map<String, Analysis> ANALYSES = new ConcurrentHashMap<>();
    /** Descargas en curso: hash -> bytes y quienes esperan el archivo completo. */
    private static final class Pending {
        final byte[] buf;
        final List<java.util.function.Consumer<byte[]>> waiters = new ArrayList<>();

        Pending(int size) {
            this.buf = new byte[size];
        }
    }

    private static final Map<String, Pending> DOWNLOADS = new HashMap<>();
    private static final Map<String, Net.Play> WAITING = new HashMap<>();
    private static final Map<String, Long> WAITING_SINCE = new HashMap<>();
    static long frameId;

    private Show() {
    }

    private static Path cacheDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("starfest_cache");
    }

    private static byte[] cached(String hash) {
        byte[] b = CACHE.get(hash);
        if (b != null) {
            return b;
        }
        Path f = cacheDir().resolve(hash + ".ogg");
        try {
            if (Files.isRegularFile(f)) {
                b = Files.readAllBytes(f);
                CACHE.put(hash, b);
                return b;
            }
        } catch (Exception ignored) {
            // se vuelve a pedir
        }
        return null;
    }

    // ------------------------------------------------------------------ mensajes del servidor

    public static void play(Net.Play p) {
        stopSource(p.zone());
        byte[] data = cached(p.hash());
        if (data == null) {
            WAITING.put(p.hash(), p);
            WAITING_SINCE.put(p.hash(), System.nanoTime());
            StarFest.LOGGER.info("StarFest: bajando '{}' ({} KB)", p.name(), p.bytes() / 1024);
            request(p.hash(), p.bytes(), buf -> {
                Net.Play w = WAITING.remove(p.hash());
                Long since = WAITING_SINCE.remove(p.hash());
                if (w != null) {
                    start(w, buf, since == null ? 0 : (System.nanoTime() - since) / 1.0e9f);
                }
            });
            return;
        }
        start(p, data, 0);
    }

    private static void start(Net.Play p, byte[] data, float extraSeconds) {
        float offset = p.offsetMs() / 1000.0f + extraSeconds;
        if (offset > p.durationMs() / 1000.0f - 0.5f) {
            return;
        }
        StreamSound sound = new StreamSound(data, p.volume(), offset);
        Source s = new Source(p.zone(), new Vec3(p.x() + 0.5, p.y(), p.z() + 0.5), p.radius(), offset, sound, p.durationMs() / 1000.0f);
        SOURCES.put(p.zone(), s);
        Minecraft.getInstance().getSoundManager().play(sound);
        analyze(p.hash(), p.name(), data, s);
    }

    /** El analisis se hace una vez por cancion y en segundo plano. */
    static void analyze(String key, String name, byte[] data, Source s) {
        Analysis a = ANALYSES.get(key);
        if (a != null) {
            s.analysis = a;
            return;
        }
        Util.backgroundExecutor().execute(() -> {
            try {
                long t0 = System.nanoTime();
                Analysis r = Analysis.analyze(data);
                ANALYSES.put(key, r);
                s.analysis = r;
                StarFest.LOGGER.info("StarFest: ritmo de '{}': {} BPM (confianza {}), {} s, analizado en {} ms", name,
                        Math.round(r.bpm * 10) / 10.0f, Math.round(r.confidence * 100) / 100.0f, Math.round(r.seconds),
                        (System.nanoTime() - t0) / 1_000_000);
            } catch (Exception e) {
                StarFest.LOGGER.warn("StarFest: no se pudo analizar '{}': {}", name, e.toString());
            }
        });
    }

    /** Pide un archivo al servidor (o lo toma de la memoria / del disco) y avisa cuando esta completo. */
    public static void request(String hash, int size, java.util.function.Consumer<byte[]> done) {
        byte[] have = cached(hash);
        if (have != null) {
            done.accept(have);
            return;
        }
        Pending p = DOWNLOADS.get(hash);
        if (p == null) {
            p = new Pending(size);
            DOWNLOADS.put(hash, p);
            PacketDistributor.sendToServer(new Net.Need(hash));
        }
        p.waiters.add(done);
    }

    public static void chunk(Net.Chunk c) {
        Pending p = DOWNLOADS.get(c.hash());
        if (p == null || p.buf.length != c.total()) {
            return;
        }
        byte[] buf = p.buf;
        System.arraycopy(c.data(), 0, buf, c.offset(), Math.min(c.data().length, buf.length - c.offset()));
        if (c.offset() + c.data().length < c.total()) {
            return;
        }
        DOWNLOADS.remove(c.hash());
        CACHE.put(c.hash(), buf);
        try {
            Files.createDirectories(cacheDir());
            Files.write(cacheDir().resolve(c.hash() + ".ogg"), buf);
        } catch (Exception ignored) {
            // queda solo en memoria
        }
        for (java.util.function.Consumer<byte[]> w : p.waiters) {
            w.accept(buf);
        }
    }

    public static void stop(Net.Stop s) {
        stopSource(s.zone());
        WAITING.values().removeIf(p -> p.zone().equals(s.zone()));
    }

    private static void stopSource(String id) {
        Source old = SOURCES.remove(id);
        if (old != null && old.sound != null) {
            Minecraft.getInstance().getSoundManager().stop(old.sound);
        }
    }

    // ------------------------------------------------------------------ tocadiscos

    static void addJukebox(String key, SoundInstance sound, byte[] data, String name) {
        Vec3 at = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        Source s = new Source("tocadiscos@" + BlockPos.containing(at).toShortString(), at, 40, 0, sound, 600);
        SOURCES.put(s.id, s);
        analyze(key, name, data, s);
    }

    // ------------------------------------------------------------------ musica en vivo (pantallas)

    public static void addLive(String id, Vec3 center, double radius, java.util.function.Supplier<Beat> beat) {
        Source s = new Source(id, center, radius, 0, null, Float.MAX_VALUE);
        s.live = beat;
        SOURCES.put(id, s);
    }

    public static void removeLive(String id) {
        SOURCES.remove(id);
    }

    // ------------------------------------------------------------------ consulta

    /** Cada cuadro: se renueva el estado y se limpian las fuentes que ya no suenan. */
    public static void frame() {
        frameId++;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Iterator<Source> it = SOURCES.values().iterator();
        while (it.hasNext()) {
            Source s = it.next();
            if (s.live != null) {
                continue;
            }
            boolean ended = s.time() > s.duration + 1.0f;
            boolean jukebox = s.id.startsWith("tocadiscos@");
            if (ended || jukebox && s.sound != null && !mc.getSoundManager().isActive(s.sound) && s.time() > 2.0f) {
                it.remove();
            }
        }
    }

    public static void clear() {
        for (String id : new ArrayList<>(SOURCES.keySet())) {
            stopSource(id);
        }
        DOWNLOADS.clear();
        WAITING.clear();
        WAITING_SINCE.clear();
    }

    /** La musica que llega a este punto (la mas cercana que lo cubre), o null. */
    public static Source at(BlockPos pos) {
        Source best = null;
        double bestD = Double.MAX_VALUE;
        for (Source s : SOURCES.values()) {
            double d = s.center.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            double r = s.radius + 24;
            if (d <= r * r && d < bestD && (s.analysis != null || s.live != null && s.live.get().playing())) {
                best = s;
                bestD = d;
            }
        }
        return best;
    }

    public static Beat beatAt(BlockPos pos) {
        Source s = at(pos);
        return s == null ? Beat.SILENCE : s.beat();
    }

    /** Centro de la musica (para figuras que salen del centro, como las ondas del piso). */
    public static Vec3 centerAt(BlockPos pos) {
        Source s = at(pos);
        return s == null ? null : s.center;
    }

    public static List<Source> sources() {
        return new ArrayList<>(SOURCES.values());
    }
}
