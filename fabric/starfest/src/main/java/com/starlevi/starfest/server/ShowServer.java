package com.starlevi.starfest.server;

import com.starlevi.starfest.StarFest;
import com.starlevi.starfest.net.Net;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Servidor: zonas con musica (el boliche, el escenario...). Quien entra a la zona empieza a escuchar
 * en el punto en que va la cancion; quien sale deja de escucharla. Las canciones se mandan una sola
 * vez a cada cliente (las guarda) y en pedazos, para no trabar a nadie.
 */
public final class ShowServer {
    /** Bytes por tick y jugador al mandar canciones (~4 MB por segundo). */
    private static final int BYTES_PER_TICK = 200 * 1024;
    private static final int CHUNK = 100 * 1024;

    public enum Mode { UNA, TODAS, ALEATORIO }

    public static final class Zone {
        public final String id;
        public final ResourceKey<Level> dim;
        public final BlockPos center;
        public final int radius;
        public final float volume;
        public final Mode mode;
        final List<String> playlist = new ArrayList<>();
        int index;
        public MusicLibrary.Track track;
        long startMs;
        final Set<UUID> listeners = new HashSet<>();

        Zone(String id, ResourceKey<Level> dim, BlockPos center, int radius, float volume, Mode mode) {
            this.id = id;
            this.dim = dim;
            this.center = center;
            this.radius = radius;
            this.volume = volume;
            this.mode = mode;
        }

        public long elapsedMs() {
            return System.currentTimeMillis() - this.startMs;
        }
    }

    private record Job(String hash, byte[] data, int[] sent) {
    }

    private static final Map<String, Zone> ZONES = new HashMap<>();

    private static final Map<UUID, Deque<Job>> UPLOADS = new HashMap<>();
    private static MinecraftServer server;

    private ShowServer() {
    }

    public static void start(MinecraftServer s) {
        server = s;
        MusicLibrary.scan();
        Dancers.load(s);
    }

    public static void stopAll() {
        ZONES.clear();
        UPLOADS.clear();
        server = null;
    }

    public static Zone zone(String id) {
        return ZONES.get(id);
    }

    /** Pone musica en una zona (reemplaza lo que sonaba ahi). */
    public static Zone play(String id, ResourceKey<Level> dim, BlockPos center, int radius, float volume, Mode mode,
                            List<String> tracks) {
        stop(id);
        Zone z = new Zone(id, dim, center, radius, volume, mode);
        z.playlist.addAll(tracks);
        if (mode == Mode.ALEATORIO) {
            Collections.shuffle(z.playlist);
        }
        ZONES.put(id, z);
        z.index = -1;
        next(z);
        return ZONES.get(id);
    }

    public static void stop(String id) {
        Zone z = ZONES.remove(id);
        if (z != null) {
            for (UUID u : z.listeners) {
                ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(u);
                if (p != null) {
                    ServerPlayNetworking.send(p, new Net.Stop(z.id));
                }
            }
        }
    }

    /** Siguiente cancion de la lista (o se apaga la zona si no hay mas). */
    public static boolean next(Zone z) {
        MusicLibrary.Track t = null;
        for (int tries = 0; tries < z.playlist.size() && t == null; tries++) {
            if (z.mode == Mode.UNA && z.index >= 0) {
                break;
            }
            z.index = (z.index + 1) % Math.max(1, z.playlist.size());
            if (z.index == 0 && z.mode == Mode.ALEATORIO && z.track != null) {
                Collections.shuffle(z.playlist);
            }
            t = MusicLibrary.get(z.playlist.get(z.index));
        }
        if (t == null) {
            stop(z.id);
            return false;
        }
        z.track = t;
        z.startMs = System.currentTimeMillis();
        // todos los que estan adentro empiezan la nueva cancion
        for (UUID u : z.listeners) {
            ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(u);
            if (p != null) {
                send(p, z);
            }
        }
        StarFest.LOGGER.info("StarFest: zona {} -> {}", z.id, t.name());
        return true;
    }

    private static void send(ServerPlayer p, Zone z) {
        MusicLibrary.Track t = z.track;
        ServerPlayNetworking.send(p, new Net.Play(z.id, t.name(), t.hash(), t.bytes(), Math.max(0, z.elapsedMs()),
                t.durationMs(), z.volume, z.center.getX(), z.center.getY(), z.center.getZ(), z.radius));
    }

    public static void tick(MinecraftServer s) {
        server = s;
        int now = s.getTickCount();
        if (now % 40 == 0) {
            MusicLibrary.scan();
        }
        if (now % 10 == 0) {
            Dancers.tick(s);
        }
        if (now % 5 == 0) {
            for (Zone z : new ArrayList<>(ZONES.values())) {
                if (z.track == null) {
                    continue;
                }
                if (z.elapsedMs() > z.track.durationMs() + 600) {
                    if (!next(z)) {
                        continue;
                    }
                }
                for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                    boolean same = p.level().dimension() == z.dim;
                    double d2 = same ? p.blockPosition().distSqr(z.center) : Double.MAX_VALUE;
                    boolean inside = d2 <= (double) z.radius * z.radius;
                    boolean far = d2 > (double) (z.radius + 6) * (z.radius + 6);
                    if (inside && z.listeners.add(p.getUUID())) {
                        send(p, z);
                    } else if (far && z.listeners.remove(p.getUUID())) {
                        ServerPlayNetworking.send(p, new Net.Stop(z.id));
                    }
                }
            }
        }
        // canciones pedidas, de a pedazos
        Iterator<Map.Entry<UUID, Deque<Job>>> it = UPLOADS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Deque<Job>> e = it.next();
            ServerPlayer p = s.getPlayerList().getPlayer(e.getKey());
            if (p == null || e.getValue().isEmpty()) {
                it.remove();
                continue;
            }
            int budget = BYTES_PER_TICK;
            while (budget > 0 && !e.getValue().isEmpty()) {
                Job j = e.getValue().peek();
                int off = j.sent()[0];
                int len = Math.min(CHUNK, j.data().length - off);
                byte[] part = java.util.Arrays.copyOfRange(j.data(), off, off + len);
                ServerPlayNetworking.send(p, new Net.Chunk(j.hash(), off, j.data().length, part));
                j.sent()[0] += len;
                budget -= len;
                if (j.sent()[0] >= j.data().length) {
                    e.getValue().poll();
                }
            }
        }
    }

    public static void need(ServerPlayer p, String hash) {
        MusicLibrary.Track t = MusicLibrary.byHash(hash);
        if (t == null) {
            return;
        }
        Deque<Job> q = UPLOADS.computeIfAbsent(p.getUUID(), u -> new ArrayDeque<>());
        for (Job j : q) {
            if (j.hash().equals(hash)) {
                return;
            }
        }
        try {
            q.add(new Job(hash, MusicLibrary.read(t), new int[1]));
        } catch (IOException ex) {
            StarFest.LOGGER.warn("StarFest: no se pudo leer {}: {}", t.name(), ex.toString());
        }
    }

    public static void left(ServerPlayer p) {
        UPLOADS.remove(p.getUUID());
        for (Zone z : ZONES.values()) {
            z.listeners.remove(p.getUUID());
        }
    }

    /** La musica que suena en un punto (para los bailarines). */
    public static Zone playingAt(ResourceKey<Level> dim, BlockPos pos) {
        for (Zone z : ZONES.values()) {
            if (z.track != null && z.dim == dim && pos.distSqr(z.center) <= (double) z.radius * z.radius) {
                return z;
            }
        }
        return null;
    }

    public static Map<String, Zone> zones() {
        return ZONES;
    }
}
