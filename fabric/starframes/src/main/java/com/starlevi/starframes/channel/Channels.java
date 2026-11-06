package com.starlevi.starframes.channel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starlevi.starframes.StarFrames;
import com.starlevi.starframes.block.ScreenBlockEntity;
import com.starlevi.starframes.net.Net;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import org.watermedia.api.player.MediaPlayer;
import org.watermedia.api.player.PlayerAPI;
import org.watermedia.api.player.ServerMediaPlayer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Los canales del servidor. Cada pantalla mira un canal; las que miran el mismo muestran lo mismo. El
 * canal tiene la lista de reproduccion y el reloj (ServerMediaPlayer de WaterMedia): el servidor lleva
 * el tiempo y todos los clientes lo siguen, asi todos ven el mismo segundo. Se guardan con el mundo.
 */
public final class Channels {
    public static final class Channel {
        public final String id;
        public final List<String> list = new ArrayList<>();
        public int index;
        public int volume = 80;
        public boolean loopOne;
        public boolean loopList = true;
        ServerMediaPlayer clock;
        boolean durationKnown;
        int sentRevision = Integer.MIN_VALUE;
        boolean dirty = true;

        Channel(String id) {
            this.id = id;
        }

        public String current() {
            return this.index >= 0 && this.index < this.list.size() ? this.list.get(this.index) : "";
        }

        /** Empieza de cero lo que esta en index (con un reloj nuevo). */
        void load(int idx) {
            this.index = this.list.isEmpty() ? 0 : Math.floorMod(idx, this.list.size());
            if (this.clock != null) {
                this.clock.release();
            }
            this.clock = PlayerAPI.serverPlayer();
            this.clock.repeat(repeats());
            this.durationKnown = false;
            if (!current().isEmpty()) {
                this.clock.start();
            }
            this.dirty = true;
        }

        boolean repeats() {
            return this.loopOne || this.loopList && this.list.size() == 1;
        }

        Net.ChannelState state() {
            byte[] snap;
            if (this.clock == null) {
                snap = new byte[0];
            } else {
                java.nio.ByteBuffer b = this.clock.snapshot().toBytes();
                snap = new byte[b.remaining()];
                b.get(snap);
            }
            return new Net.ChannelState(this.id, new ArrayList<>(this.list), this.index, this.volume, this.loopOne, this.loopList, snap);
        }
    }

    private static final Map<String, Channel> MAP = new LinkedHashMap<>();
    private static MinecraftServer server;
    private static boolean needSave;
    private static int saveTimer;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Channels() {
    }

    private static Path file() {
        return server.getWorldPath(LevelResource.ROOT).resolve("starframes_canales.json");
    }

    public static synchronized void start(MinecraftServer s) {
        server = s;
        MAP.clear();
        try {
            Path f = file();
            if (Files.isRegularFile(f)) {
                JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                for (JsonElement e : root.getAsJsonArray("canales")) {
                    JsonObject o = e.getAsJsonObject();
                    Channel c = new Channel(o.get("id").getAsString());
                    for (JsonElement u : o.getAsJsonArray("lista")) {
                        c.list.add(u.getAsString());
                    }
                    c.volume = o.has("volumen") ? o.get("volumen").getAsInt() : 80;
                    c.loopOne = o.has("repetir_uno") && o.get("repetir_uno").getAsBoolean();
                    c.loopList = !o.has("repetir_lista") || o.get("repetir_lista").getAsBoolean();
                    c.load(o.has("indice") ? o.get("indice").getAsInt() : 0);
                    if (o.has("pausado") && o.get("pausado").getAsBoolean()) {
                        c.clock.pause();
                    }
                    MAP.put(c.id, c);
                }
            }
        } catch (Exception e) {
            StarFrames.LOGGER.error("StarFrames: no se pudieron leer los canales", e);
        }
        StarFrames.LOGGER.info("StarFrames: {} canales", MAP.size());
    }

    public static synchronized void stop() {
        save();
        for (Channel c : MAP.values()) {
            if (c.clock != null) {
                c.clock.release();
            }
        }
        MAP.clear();
        server = null;
    }

    private static void save() {
        if (server == null) {
            return;
        }
        JsonArray arr = new JsonArray();
        for (Channel c : MAP.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", c.id);
            JsonArray l = new JsonArray();
            c.list.forEach(l::add);
            o.add("lista", l);
            o.addProperty("indice", c.index);
            o.addProperty("volumen", c.volume);
            o.addProperty("repetir_uno", c.loopOne);
            o.addProperty("repetir_lista", c.loopList);
            o.addProperty("pausado", c.clock != null && c.clock.paused());
            arr.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("canales", arr);
        try {
            Files.writeString(file(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            StarFrames.LOGGER.error("StarFrames: no se pudieron guardar los canales", e);
        }
    }

    public static synchronized Channel get(String id) {
        return MAP.get(id);
    }

    /** Un canal nuevo con nombre al azar (para una pantalla recien puesta). */
    public static synchronized Channel create() {
        String id;
        do {
            id = "p" + Integer.toString(ThreadLocalRandom.current().nextInt(36 * 36 * 36 * 36), 36);
        } while (MAP.containsKey(id));
        return getOrCreate(id);
    }

    public static synchronized Channel getOrCreate(String id) {
        return MAP.computeIfAbsent(id, k -> {
            needSave = true;
            Channel c = new Channel(k);
            c.load(0);
            return c;
        });
    }

    public static String cleanName(String s) {
        String n = s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        return n.length() > 32 ? n.substring(0, 32) : n;
    }

    // ------------------------------------------------------------------ cada tick

    public static synchronized void tick() {
        if (server == null) {
            return;
        }
        for (Channel c : MAP.values()) {
            if (c.clock == null) {
                continue;
            }
            // termino: al siguiente de la lista (o vuelve al primero)
            if (c.clock.status() == MediaPlayer.Status.ENDED && !c.list.isEmpty()) {
                if (c.index + 1 < c.list.size()) {
                    c.load(c.index + 1);
                    needSave = true;
                } else if (c.loopList) {
                    c.load(0);
                    needSave = true;
                }
            }
            int rev = c.clock.revision();
            if (c.dirty || rev != c.sentRevision) {
                c.dirty = false;
                c.sentRevision = rev;
                broadcast(c.state());
            }
        }
        if (needSave && ++saveTimer > 100) {
            saveTimer = 0;
            needSave = false;
            save();
        }
    }

    private static void broadcast(Net.ChannelState s) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, s);
        }
    }

    public static synchronized void join(ServerPlayer p) {
        for (Channel c : MAP.values()) {
            ServerPlayNetworking.send(p, c.state());
        }
    }

    // ------------------------------------------------------------------ desde los clientes

    public static boolean allowed(ServerPlayer p) {
        return p.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || p.level().getServer().isSingleplayer();
    }

    public static synchronized void editScreen(ServerPlayer p, BlockPos pos, String field, String value) {
        if (!allowed(p) || p.blockPosition().distSqr(pos) > 64 * 64) {
            return;
        }
        BlockEntity be = p.level().getBlockEntity(pos);
        if (!(be instanceof ScreenBlockEntity s)) {
            return;
        }
        String msg = null;
        switch (field) {
            case "url" -> {
                // reproducir ya este link en el canal de la pantalla
                Channel c = getOrCreate(s.channel());
                String url = value.trim();
                if (!url.isEmpty()) {
                    if (c.list.isEmpty()) {
                        c.list.add(url);
                    } else {
                        c.list.set(Math.min(c.index, c.list.size() - 1), url);
                    }
                    c.load(c.index);
                    needSave = true;
                    msg = "Reproduciendo en el canal " + c.id;
                }
            }
            case "canal" -> {
                String id = cleanName(value);
                if (!id.isEmpty()) {
                    getOrCreate(id);
                    s.set("canal", id);
                    msg = "Pantalla en el canal " + id;
                }
            }
            default -> {
                s.set(field, value);
            }
        }
        if (msg != null) {
            p.displayClientMessage(Component.literal(msg), true);
        }
    }

    public static synchronized void control(ServerPlayer p, String id, String action, String value) {
        if (!allowed(p)) {
            p.displayClientMessage(Component.literal("Necesitás permiso de administrador para controlar las pantallas"), true);
            return;
        }
        Channel c = MAP.get(id);
        if (c == null) {
            return;
        }
        ServerMediaPlayer k = c.clock;
        switch (action) {
            case "pausa" -> {
                if (k.paused()) {
                    k.resume();
                } else if (k.ended() || k.stopped()) {
                    c.load(c.index);
                } else {
                    k.pause();
                }
            }
            case "saltar" -> {
                long t = Math.max(0, k.time() + parseLong(value, 10000));
                if (k.duration() > 0) {
                    k.seek(Math.min(t, k.duration() - 1));
                }
            }
            case "ir" -> {
                if (k.duration() > 0) {
                    k.seek(Math.max(0, Math.min(parseLong(value, 0), k.duration() - 1)));
                }
            }
            case "siguiente" -> c.load(c.index + 1);
            case "anterior" -> c.load(c.index - 1);
            case "elegir" -> c.load((int) parseLong(value, 0));
            case "agregar" -> {
                String url = value.trim();
                if (!url.isEmpty()) {
                    c.list.add(url);
                    if (c.list.size() == 1) {
                        c.load(0);
                    } else {
                        k.repeat(c.repeats());
                    }
                }
            }
            case "quitar" -> {
                int i = (int) parseLong(value, -1);
                if (i >= 0 && i < c.list.size()) {
                    boolean playing = i == c.index;
                    c.list.remove(i);
                    if (i < c.index) {
                        c.index--;
                    }
                    if (playing) {
                        c.load(Math.min(c.index, Math.max(0, c.list.size() - 1)));
                    } else {
                        k.repeat(c.repeats());
                    }
                }
            }
            case "subir", "bajar" -> {
                int i = (int) parseLong(value, -1), j = action.equals("subir") ? i - 1 : i + 1;
                if (i >= 0 && j >= 0 && i < c.list.size() && j < c.list.size()) {
                    String a = c.list.get(i);
                    c.list.set(i, c.list.get(j));
                    c.list.set(j, a);
                    if (c.index == i) {
                        c.index = j;
                    } else if (c.index == j) {
                        c.index = i;
                    }
                }
            }
            case "volumen" -> c.volume = (int) Math.max(0, Math.min(100, parseLong(value, 80)));
            case "repetir_uno" -> {
                c.loopOne = !c.loopOne;
                k.repeat(c.repeats());
            }
            case "repetir_lista" -> {
                c.loopList = !c.loopList;
                k.repeat(c.repeats());
            }
            case "parar" -> k.stop();
            default -> {
                return;
            }
        }
        c.dirty = true;
        needSave = true;
    }

    /** Un cliente abrio lo que suena y sabe cuanto dura: el reloj lo necesita para adelantar y terminar. */
    public static synchronized void duration(String id, String url, long millis, boolean live) {
        Channel c = MAP.get(id);
        if (c == null || c.durationKnown || !url.equals(c.current())) {
            return;
        }
        c.durationKnown = true;
        if (live) {
            c.clock.live(true);
        } else if (millis > 0) {
            c.clock.syncDuration(millis);
        } else if (c.list.size() > 1) {
            // una imagen en una lista: 10 segundos y pasa a la siguiente
            c.clock.syncDuration(10000);
        }
        c.dirty = true;
    }

    private static long parseLong(String s, long def) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
