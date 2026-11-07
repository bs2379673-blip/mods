package com.starlevi.starframes.client;

import com.starlevi.starframes.StarFrames;
import com.starlevi.starframes.block.ScreenBlockEntity;
import com.starlevi.starframes.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.watermedia.api.player.ALEngine;
import org.watermedia.api.player.MRL;
import org.watermedia.api.player.MediaPlayer;
import org.watermedia.api.player.PlayerAPI;
import org.watermedia.api.player.SFXEngine;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lo que suena en cada canal, en este cliente: un solo reproductor de WaterMedia por canal (aunque
 * haya muchas pantallas), que sigue el reloj del servidor. Cada pantalla cercana es un parlante: el
 * sonido sale desde donde esta. Si no hay pantallas cerca por un rato, se libera.
 */
public final class ClientChannels {
    static final class CC {
        Net.ChannelState state;
        String url = "";
        MRL mrl;
        MediaPlayer player;
        VideoTexture tex;
        Identifier texId;
        long lastSeen;
        boolean reported, failed;
        final Map<BlockPos, ALEngine.Sink> sinks = new HashMap<>();
        BlockPos primaryAt;
    }

    private static final Map<String, CC> MAP = new LinkedHashMap<>();
    static Thread renderThread;
    static int version;
    private static int texSeq;

    private ClientChannels() {
    }

    public static Net.ChannelState state(String id) {
        CC c = MAP.get(id);
        return c == null ? null : c.state;
    }

    /** Tiempo y duracion de lo que suena (milisegundos), o null si aca no se esta reproduciendo. */
    public static long[] time(String id) {
        CC c = MAP.get(id);
        if (c == null || c.player == null) {
            return null;
        }
        return new long[]{c.player.time(), c.player.duration()};
    }

    public static String status(String id) {
        CC c = MAP.get(id);
        if (c == null) {
            return "";
        }
        if (c.failed) {
            return "no se pudo abrir: " + reason(c);
        }
        if (c.player == null) {
            return c.mrl == null ? "" : "cargando…";
        }
        return switch (c.player.status()) {
            case PLAYING -> "reproduciendo";
            case PAUSED -> "en pausa";
            case BUFFERING, LOADING, WAITING -> "cargando…";
            case ENDED -> "terminó";
            case STOPPED -> "parado";
            case ERROR -> "error";
        };
    }

    public static void receive(Net.ChannelState s) {
        CC c = MAP.computeIfAbsent(s.id(), k -> new CC());
        c.state = s;
        version++;
        if (c.player != null && s.current().equals(c.url)) {
            follow(c);
        }
    }

    public static void gone(String id) {
        CC c = MAP.remove(id);
        if (c != null) {
            release(c);
        }
        version++;
    }

    private static void follow(CC c) {
        MediaPlayer p = c.player;
        p.volume(c.state.volume());
        if (c.state.clock().length > 0) {
            try {
                p.sync(MediaPlayer.PlayerState.read(ByteBuffer.wrap(c.state.clock())));
            } catch (Exception e) {
                StarFrames.LOGGER.warn("StarFrames: reloj ilegible: {}", e.toString());
            }
        }
    }

    // ------------------------------------------------------------------ cada tick del cliente

    public static void tick(Minecraft mc) {
        Player me = mc.player;
        if (mc.level == null || me == null) {
            if (!MAP.isEmpty()) {
                disconnect();
            }
            return;
        }
        long now = System.currentTimeMillis();
        // pantallas cerca, por canal (cada una es un parlante)
        Map<String, List<ScreenBlockEntity>> near = new HashMap<>();
        for (ScreenBlockEntity s : new ArrayList<>(ScreenBlockEntity.CLIENT)) {
            if (s.isRemoved() || s.getLevel() != mc.level || s.channel().isEmpty()) {
                continue;
            }
            double reach = s.range() + 16 + Math.max(s.width(), s.height());
            if (s.getBlockPos().getCenter().distanceToSqr(me.position()) <= reach * reach) {
                near.computeIfAbsent(s.channel(), k -> new ArrayList<>()).add(s);
            }
        }
        for (Map.Entry<String, CC> e : MAP.entrySet()) {
            CC c = e.getValue();
            List<ScreenBlockEntity> screens = near.getOrDefault(e.getKey(), List.of());
            if (!screens.isEmpty()) {
                c.lastSeen = now;
            }
            if (now - c.lastSeen > 10000 || c.state == null) {
                release(c);
                continue;
            }
            ensure(e.getKey(), c);
            speakers(c, screens);
        }
    }

    /** Por que fallo, en palabras simples. */
    private static String reason(CC c) {
        Throwable t = c.mrl == null ? null : c.mrl.exception();
        String m = t == null ? "" : String.valueOf(t.getMessage()) + (t.getCause() == null ? "" : " " + t.getCause().getMessage());
        String low = m.toLowerCase(java.util.Locale.ROOT);
        if (low.contains("not a bot") || low.contains("sign in") || low.contains("po_token")) {
            return "YouTube pidió verificar (probá otro video o más tarde)";
        }
        if (low.contains("http 4") || low.contains("403") || low.contains("400")) {
            return "la página no deja descargarlo (probá el link directo)";
        }
        if (low.contains("http 5")) {
            return "la página no responde";
        }
        if (low.contains("unknownhost") || low.contains("unresolved") || low.contains("connect")) {
            return "sin conexión a esa página";
        }
        if (c.player == null && c.mrl != null && c.mrl.status() == org.watermedia.api.player.MRL.Status.LOADED) {
            return "no se pudo crear el reproductor";
        }
        return m.isBlank() ? "link no válido" : (m.length() > 60 ? m.substring(0, 60) + "…" : m);
    }

    private static void ensure(String id, CC c) {
        String url = c.state.current();
        if (!url.equals(c.url)) {
            release(c);
            c.url = url;
            c.reported = false;
            c.failed = false;
            if (!url.isEmpty()) {
                try {
                    c.mrl = PlayerAPI.mrl(url);
                } catch (Exception ex) {
                    StarFrames.LOGGER.error("StarFrames: WaterMedia no esta lista: {}", ex.toString());
                    c.failed = true;
                }
            }
        }
        if (c.mrl == null || c.failed) {
            return;
        }
        if (c.player == null) {
            switch (c.mrl.status()) {
                case LOADED -> {
                    Minecraft mc = Minecraft.getInstance();
                    c.player = PlayerAPI.player(c.mrl, 0, () -> PlayerAPI.glEngine(renderThread, mc), () -> {
                        try {
                            return PlayerAPI.alEngine(true);
                        } catch (Throwable t) {
                            StarFrames.LOGGER.warn("StarFrames: sin sonido posicional ({}), sonido comun", t.toString());
                            try {
                                return (SFXEngine) PlayerAPI.alEngine();
                            } catch (Throwable t2) {
                                // sin sistema de sonido: el video se ve igual, mudo
                                StarFrames.LOGGER.warn("StarFrames: sin sonido ({}), solo imagen", t2.toString());
                                return null;
                            }
                        }
                    });
                    if (c.player == null) {
                        c.failed = true;
                        return;
                    }
                    c.player.volume(c.state.volume());
                    c.player.startPaused();
                    follow(c);
                    version++;
                }
                case ERROR, BLOCKED -> {
                    if (!c.failed) {
                        StarFrames.LOGGER.warn("StarFrames: no se pudo abrir {}: {}", c.url, c.mrl.exception());
                    }
                    c.failed = true;
                }
                case EXPIRED, FORGOTTEN -> c.mrl = c.mrl.reload();
                default -> {
                }
            }
            return;
        }
        // el primero que sabe cuanto dura se lo dice al servidor (el reloj lo necesita)
        if (!c.reported && (c.player.duration() > 0 || c.player.live() || c.player.playing() && c.player.duration() == 0 && c.player.texture() != 0)) {
            c.reported = true;
            ClientPlayNetworking.send(new Net.Duration(id, c.url, c.player.duration(), c.player.live()));
        }
    }

    /** Cada pantalla cercana del canal suena desde donde esta (un solo video, varios parlantes). */
    private static void speakers(CC c, List<ScreenBlockEntity> screens) {
        if (c.player == null || !c.player.spatial()) {
            return;
        }
        Set<BlockPos> keep = new HashSet<>();
        for (ScreenBlockEntity s : screens) {
            BlockPos pos = s.getBlockPos();
            keep.add(pos);
            ALEngine.Sink sink = c.sinks.get(pos);
            if (sink == null) {
                if (c.primaryAt == null || !c.sinks.containsKey(c.primaryAt)) {
                    List<ALEngine.Sink> all = c.player.sinks();
                    sink = all.isEmpty() ? null : all.get(0);
                    c.primaryAt = pos;
                } else {
                    sink = c.player.addSink(ALEngine.Mix.MONO);
                }
                if (sink == null) {
                    continue;
                }
                c.sinks.put(pos, sink);
                double[] at = center(s);
                float max = Math.max(4, s.range());
                sink.spatialAudio(new SFXEngine.SpatialAudio(at[0], at[1], at[2], Math.max(2f, max / 4f), max, 1.0f, false, null));
            }
        }
        c.sinks.entrySet().removeIf(en -> {
            if (keep.contains(en.getKey())) {
                return false;
            }
            if (!en.getValue().primary()) {
                en.getValue().release();
                return true;
            }
            return false;
        });
    }

    /** El centro del video de una pantalla, en el mundo. */
    static double[] center(ScreenBlockEntity s) {
        Direction f = s.getBlockState().hasProperty(com.starlevi.starframes.block.ScreenBlock.FACING)
                ? s.getBlockState().getValue(com.starlevi.starframes.block.ScreenBlock.FACING) : Direction.NORTH;
        BlockPos p = s.getBlockPos();
        // derecha de quien mira la pantalla
        Direction right = f.getCounterClockWise();
        double x = p.getX() + 0.5 + right.getStepX() * s.offsetX(), z = p.getZ() + 0.5 + right.getStepZ() * s.offsetX();
        return new double[]{x, p.getY() + s.offsetY() + s.height() / 2, z};
    }

    /** La textura de lo que suena en el canal (para dibujar), o null; y lo marca como visto. */
    static VideoTexture texture(String id) {
        CC c = MAP.get(id);
        if (c == null) {
            return null;
        }
        c.lastSeen = System.currentTimeMillis();
        if (c.player == null) {
            return null;
        }
        long t = c.player.texture();
        int w = c.player.width(), h = c.player.height();
        if (t == 0 || w <= 0 || h <= 0) {
            return null;
        }
        if (c.tex == null || c.tex.glId != (int) t || c.tex.width != w || c.tex.height != h) {
            if (c.texId == null) {
                c.texId = Identifier.fromNamespaceAndPath(StarFrames.MOD_ID, "video/" + id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_") + "_" + texSeq++);
            }
            c.tex = new VideoTexture((int) t, w, h);
            Minecraft.getInstance().getTextureManager().register(c.texId, c.tex);
        }
        return c.tex;
    }

    static Identifier textureId(String id) {
        CC c = MAP.get(id);
        return c == null ? null : c.texId;
    }

    private static void release(CC c) {
        if (c.player != null) {
            try {
                c.player.release();
            } catch (Exception e) {
                StarFrames.LOGGER.warn("StarFrames: al liberar: {}", e.toString());
            }
            c.player = null;
            version++;
        }
        c.sinks.clear();
        c.primaryAt = null;
        c.mrl = null;
        c.url = "";
        if (c.texId != null) {
            Minecraft.getInstance().getTextureManager().release(c.texId);
            c.texId = null;
        }
        c.tex = null;
    }

    public static void disconnect() {
        for (CC c : MAP.values()) {
            release(c);
        }
        MAP.clear();
        version++;
    }
}
