package com.starlevi.starfest.server;

import com.starlevi.starfest.StarFest;
import com.starlevi.starfest.net.Net;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

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

    /** Zona con un cielo: quien entra lo ve, quien sale deja de verlo. */
    public static final class SkyZone {
        public final String id;
        public final ResourceKey<Level> dim;
        public final BlockPos center;
        public final int radius;
        public String name;
        String sentVersion = "";
        final Set<UUID> viewers = new HashSet<>();

        SkyZone(String id, ResourceKey<Level> dim, BlockPos center, int radius, String name) {
            this.id = id;
            this.dim = dim;
            this.center = center;
            this.radius = radius;
            this.name = name;
        }
    }

    private static final Map<String, SkyZone> SKY_ZONES = new HashMap<>();

    public static Map<String, SkyZone> skyZones() {
        return SKY_ZONES;
    }

    public static void sky(String id, ResourceKey<Level> dim, BlockPos center, int radius, String name) {
        stopSky(id);
        SKY_ZONES.put(id, new SkyZone(id, dim, center, radius, name));
    }

    public static void stopSky(String id) {
        SkyZone z = SKY_ZONES.remove(id);
        if (z != null && server != null) {
            for (UUID u : z.viewers) {
                ServerPlayer p = server.getPlayerList().getPlayer(u);
                if (p != null) {
                    PacketDistributor.sendToPlayer(p, new Net.SkyStop(z.id));
                }
            }
        }
    }

    private static void sendSky(ServerPlayer p, SkyZone z, SkyLibrary.Sky sky) {
        java.util.List<Net.SkyImage> imgs = new java.util.ArrayList<>();
        for (Map.Entry<String, String> e : sky.images().entrySet()) {
            byte[] d = SkyLibrary.imageByHash(e.getValue());
            if (d != null) {
                imgs.add(new Net.SkyImage(e.getKey(), e.getValue(), d.length));
            }
        }
        PacketDistributor.sendToPlayer(p, new Net.Sky(z.id, sky.name(), sky.version(), sky.json(), imgs,
                z.center.getX(), z.center.getY(), z.center.getZ(), z.radius));
    }

    private static void tickSkies(MinecraftServer s) {
        for (SkyZone z : new ArrayList<>(SKY_ZONES.values())) {
            SkyLibrary.Sky sky = SkyLibrary.get(z.name);
            if (sky == null) {
                continue;
            }
            boolean changed = !sky.version().equals(z.sentVersion);
            z.sentVersion = sky.version();
            for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                boolean same = p.level().dimension() == z.dim;
                double d2 = same ? p.blockPosition().distSqr(z.center) : Double.MAX_VALUE;
                boolean inside = d2 <= (double) (z.radius + 16) * (z.radius + 16);
                if (inside && (z.viewers.add(p.getUUID()) || changed)) {
                    sendSky(p, z, sky);
                } else if (!inside && z.viewers.remove(p.getUUID())) {
                    PacketDistributor.sendToPlayer(p, new Net.SkyStop(z.id));
                }
            }
        }
    }
    private static final Map<UUID, Deque<Job>> UPLOADS = new HashMap<>();
    private static MinecraftServer server;

    private ShowServer() {
    }

    public static void start(MinecraftServer s) {
        server = s;
        MusicLibrary.scan();
        SkyLibrary.scan();
        Dancers.load(s);
    }

    public static void stopAll() {
        SKY_ZONES.clear();
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
                    PacketDistributor.sendToPlayer(p, new Net.Stop(z.id));
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
        PacketDistributor.sendToPlayer(p, new Net.Play(z.id, t.name(), t.hash(), t.bytes(), Math.max(0, z.elapsedMs()),
                t.durationMs(), z.volume, z.center.getX(), z.center.getY(), z.center.getZ(), z.radius));
    }

    public static void tick(MinecraftServer s) {
        server = s;
        int now = s.getTickCount();
        if (now % 40 == 0) {
            MusicLibrary.scan();
            SkyLibrary.scan();
        }
        if (now % 10 == 0) {
            tickSkies(s);
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
                        PacketDistributor.sendToPlayer(p, new Net.Stop(z.id));
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
                PacketDistributor.sendToPlayer(p, new Net.Chunk(j.hash(), off, j.data().length, part));
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
        byte[] image = t == null ? SkyLibrary.imageByHash(hash) : null;
        if (t == null && image == null) {
            return;
        }
        Deque<Job> q = UPLOADS.computeIfAbsent(p.getUUID(), u -> new ArrayDeque<>());
        for (Job j : q) {
            if (j.hash().equals(hash)) {
                return;
            }
        }
        if (image != null) {
            q.add(new Job(hash, image, new int[1]));
            return;
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
        for (SkyZone z : SKY_ZONES.values()) {
            z.viewers.remove(p.getUUID());
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
