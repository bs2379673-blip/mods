package com.tunombre.obscurenpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.ServerChatEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Conversaciones jugador-NPC: dialogos con nodos y opciones (config/obscurenpc/dialogos/*.json), lineas simples y charla libre
 * con la "IA" por contexto. El NPC habla en burbuja y en el chat mientras hace gestos.
 */
public final class Dialogs {
    private static final class Conv {
        UUID player, npc;
        JsonObject file;
        String node;
        final List<String> lines = new ArrayList<>();
        int idx;
        int wait;
        boolean awaiting;                       // esperando que el jugador elija una opcion
        final List<JsonObject> opts = new ArrayList<>();
        String pendingReply;                    // respuesta libre por decir (despues de que se vea el mensaje del jugador)
        String pendingGesture;
        int idle;                               // ticks sin actividad
        boolean free;                           // charla libre (sin dialogo guionado)
        JsonObject nodeObj;
    }

    private static final Map<UUID, Conv> CONVS = new HashMap<>();
    private static final Map<String, Long> GREETED = new HashMap<>();
    private static final Map<String, JsonObject> FILES = new HashMap<>();
    private static final Map<String, Long> STAMPS = new HashMap<>();

    private Dialogs() { }

    public static void reload() { FILES.clear(); STAMPS.clear(); Brain.reload(); }

    private static Path dir() { return State.dir().resolve("dialogos"); }

    public static List<String> fileIds() {
        List<String> l = new ArrayList<>();
        try (var s = Files.isDirectory(dir()) ? Files.list(dir()) : java.util.stream.Stream.<Path>empty()) {
            s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json")).forEach(n -> l.add(n.substring(0, n.length() - 5)));
        } catch (IOException ignored) { }
        java.util.Collections.sort(l);
        return l;
    }

    static JsonObject loadFile(String id) {
        if (id == null || id.isBlank()) return null;
        String k = id.toLowerCase(Locale.ROOT);
        Path f = dir().resolve(k + ".json");
        try {
            if (!Files.isRegularFile(f)) return null;
            long st = Files.getLastModifiedTime(f).toMillis();
            if (FILES.containsKey(k) && STAMPS.get(k) == st) return FILES.get(k);
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            FILES.put(k, o);
            STAMPS.put(k, st);
            return o;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ inicio ----
    public static boolean markGreeted(Actor npc, ServerPlayer p) {
        String k = npc.getUUID() + ":" + p.getUUID();
        long now = npc.level().getGameTime();
        Long last = GREETED.get(k);
        if (last != null && now - last < 24000L) return false;
        GREETED.put(k, now);
        return true;
    }

    public static void interact(Actor npc, ServerPlayer p) {
        Conv old = CONVS.get(p.getUUID());
        if (old != null && old.npc.equals(npc.getUUID()) && old.awaiting) { showOptions(npc, p, old); return; }
        start(npc, p, npc.data().dialogo);
    }

    /** arranca (o reinicia) la conversacion del jugador con ese NPC usando el dialogo indicado ("" = el del NPC) */
    public static void start(Actor npc, ServerPlayer p, String dialogId) {
        end(p.getUUID());
        NpcData d = npc.data();
        UUID id = p.getUUID();
        npc.addCounter(id, "visitas", 1);
        Conv c = new Conv();
        c.player = id;
        c.npc = npc.getUUID();
        npc.talkingTo = id;
        JsonObject file = loadFile(dialogId == null || dialogId.isBlank() ? d.dialogo : dialogId);
        CONVS.put(id, c);
        if (file != null) {
            c.file = file;
            String start = "inicio";
            if (file.has("inicios")) {
                for (JsonElement e : file.getAsJsonArray("inicios")) {
                    JsonObject o = e.getAsJsonObject();
                    if (Fmt.test(npc, p, o.get("si"))) { start = o.get("nodo").getAsString(); break; }
                }
            } else if (file.has("empieza")) start = file.get("empieza").getAsString();
            enterNode(npc, p, c, start);
        } else if (!d.lineas.isEmpty()) {
            c.lines.addAll(d.lineas);
            c.free = true;
        } else {
            c.free = true;
            c.lines.add(Brain.greeting(npc, p));
        }
    }

    private static void enterNode(Actor npc, ServerPlayer p, Conv c, String node) {
        c.nodeObj = c.file != null && c.file.has("nodos") && c.file.getAsJsonObject("nodos").has(node) ? c.file.getAsJsonObject("nodos").getAsJsonObject(node) : null;
        c.node = node;
        c.lines.clear();
        c.idx = 0;
        c.awaiting = false;
        c.opts.clear();
        if (c.nodeObj == null) { c.free = true; return; }
        JsonObject n = c.nodeObj;
        if (n.has("decir")) {
            JsonElement e = n.get("decir");
            if (e.isJsonArray()) for (JsonElement x : e.getAsJsonArray()) c.lines.add(x.getAsString());
            else c.lines.add(e.getAsString());
        }
        if (n.has("gesto")) npc.gesture(n.get("gesto").getAsString());
        if (n.has("postura")) npc.setStance(Stances.byName(n.get("postura").getAsString()));
    }

    // ------------------------------------------------------------------ avance ----
    public static void tick(MinecraftServer server) {
        if (CONVS.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Conv>> it = CONVS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Conv> en = it.next();
            Conv c = en.getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            Actor npc = p == null ? null : findNpc(p, c.npc);
            if (p == null || npc == null || !npc.isAlive() || p.distanceTo(npc) > 12 || !p.isAlive()) {
                if (npc != null && npc.talkingTo != null && npc.talkingTo.equals(en.getKey())) npc.talkingTo = null;
                it.remove();
                continue;
            }
            c.idle++;
            if (c.wait > 0) { c.wait--; continue; }
            if (c.pendingReply != null) {
                String r = c.pendingReply, g = c.pendingGesture;
                c.pendingReply = null;
                c.pendingGesture = null;
                if (g != null) npc.gesture(g);
                c.wait = npc.speak(r, p, true) + Fmt.lastPause;
                c.idle = 0;
                continue;
            }
            if (c.idx < c.lines.size()) {
                String line = c.lines.get(c.idx++);
                c.wait = npc.speak(line, p, true) + Fmt.lastPause + (c.idx < c.lines.size() ? 8 : 0);
                c.idle = 0;
                continue;
            }
            if (c.awaiting) {
                if (c.idle > 1800) { stop(npc, it); }
                continue;
            }
            // fin del nodo: acciones y siguiente paso
            if (c.nodeObj != null) {
                JsonObject n = c.nodeObj;
                apply(npc, p, n);
                List<JsonObject> opts = new ArrayList<>();
                if (n.has("opciones")) for (JsonElement e : n.getAsJsonArray("opciones")) {
                    JsonObject o = e.getAsJsonObject();
                    if (Fmt.test(npc, p, o.get("si"))) opts.add(o);
                }
                c.nodeObj = null;
                if (!opts.isEmpty()) {
                    c.opts.clear();
                    c.opts.addAll(opts);
                    c.awaiting = true;
                    c.idle = 0;
                    showOptions(npc, p, c);
                    continue;
                }
                if (n.has("ir")) { enterNode(npc, p, c, n.get("ir").getAsString()); continue; }
                if (n.has("fin") && !n.get("fin").getAsBoolean()) { c.free = true; continue; }
                // sin salida: la charla queda abierta unos segundos para preguntas libres
                c.free = true;
                c.idle = 1200;
                continue;
            }
            // charla libre abierta: se cierra tras un rato sin que el jugador diga nada
            if (c.idle > 1800) stop(npc, it);
        }
    }

    private static void stop(Actor npc, Iterator<Map.Entry<UUID, Conv>> it) {
        if (npc != null) npc.talkingTo = null;
        it.remove();
    }

    private static Actor findNpc(ServerPlayer p, UUID id) {
        Entity e = ((ServerLevel) p.level()).getEntity(id);
        return e instanceof Actor a ? a : null;
    }

    private static void apply(Actor npc, ServerPlayer p, JsonObject n) {
        UUID id = p.getUUID();
        if (n.has("pone")) for (JsonElement e : arr(n, "pone")) npc.setFlag(id, e.getAsString());
        if (n.has("quita")) for (JsonElement e : arr(n, "quita")) npc.clearFlag(id, e.getAsString());
        if (n.has("gpone")) for (JsonElement e : arr(n, "gpone")) State.set(e.getAsString());
        if (n.has("gquita")) for (JsonElement e : arr(n, "gquita")) State.clear(e.getAsString());
        if (n.has("afinidad")) npc.addCounter(id, "afinidad", n.get("afinidad").getAsInt());
        if (n.has("cmd")) for (JsonElement e : arr(n, "cmd")) Fmt.runCommand(npc, e.getAsString(), p);
        if (n.has("dar")) for (JsonElement e : arr(n, "dar")) {
            try {
                String[] sp = e.getAsString().split(" ");
                var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(sp[0]));
                if (item != null) {
                    var st = new net.minecraft.world.item.ItemStack(item, sp.length > 1 ? Integer.parseInt(sp[1]) : 1);
                    if (!p.getInventory().add(st)) p.drop(st, false);
                }
            } catch (RuntimeException ignored) { }
        }
    }

    private static JsonArray arr(JsonObject o, String k) {
        JsonElement e = o.get(k);
        if (e == null) return new JsonArray();
        if (e.isJsonArray()) return e.getAsJsonArray();
        JsonArray a = new JsonArray();
        a.add(e);
        return a;
    }

    // ------------------------------------------------------------------ opciones ----
    private static void showOptions(Actor npc, ServerPlayer p, Conv c) {
        p.sendSystemMessage(Component.literal("— " + npc.data().nombre + " espera tu respuesta —").withStyle(ChatFormatting.DARK_GRAY));
        for (int i = 0; i < c.opts.size(); i++) {
            String text = Fmt.vars(npc, c.opts.get(i).get("texto").getAsString(), p);
            final int n = i + 1;
            Component line = Component.literal(" [" + n + "] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(text).withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/onpc responder " + n))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click para responder (o escribe " + n + ")")))));
            p.sendSystemMessage(line);
        }
    }

    public static void choose(ServerPlayer p, int n) {
        Conv c = CONVS.get(p.getUUID());
        if (c == null || !c.awaiting || n < 1 || n > c.opts.size()) { p.sendSystemMessage(Component.literal("No hay nada que responder ahora.").withStyle(ChatFormatting.GRAY)); return; }
        Actor npc = findNpc(p, c.npc);
        if (npc == null) { end(p.getUUID()); return; }
        JsonObject o = c.opts.get(n - 1);
        p.sendSystemMessage(Component.literal("› " + Fmt.vars(npc, o.get("texto").getAsString(), p)).withStyle(ChatFormatting.GRAY));
        apply(npc, p, o);
        c.awaiting = false;
        c.opts.clear();
        c.idle = 0;
        c.wait = 6;
        if (o.has("ir")) enterNode(npc, p, c, o.get("ir").getAsString());
        else { c.nodeObj = null; c.lines.clear(); c.free = true; c.idle = 1200; if (o.has("fin") && o.get("fin").getAsBoolean()) end(p.getUUID()); }
    }

    // ------------------------------------------------------------------ chat libre ----
    public static void onChat(ServerChatEvent e) {
        ServerPlayer p = e.getPlayer();
        String msg = e.getRawText();
        MinecraftServer server = p.getServer();
        if (server == null) return;
        Conv c = CONVS.get(p.getUUID());
        String trimmed = msg.trim();
        if (c != null && c.awaiting && trimmed.matches("\\d{1,2}")) {
            e.setCanceled(true);
            int n = Integer.parseInt(trimmed);
            server.execute(() -> choose(p, n));
            return;
        }
        server.execute(() -> handleChat(p, msg));
    }

    private static void handleChat(ServerPlayer p, String msg) {
        Conv c = CONVS.get(p.getUUID());
        Actor npc = null;
        if (c != null) npc = findNpc(p, c.npc);
        String nm = Brain.norm(msg);
        if (npc == null || !npc.isAlive()) {
            npc = null;
            double best = 1e9;
            for (Actor a : p.level().getEntitiesOfClass(Actor.class, p.getBoundingBox().inflate(16))) {
                if (!a.data().charla_ia) continue;
                String n1 = Brain.norm(a.data().nombre);
                boolean named = !n1.isEmpty() && (" " + nm + " ").contains(" " + n1 + " ");
                boolean close = a.distanceTo(p) < 4 && a.hasLineOfSight(p) && msg.trim().endsWith("?");
                if (!named && !close) continue;
                double dd = a.distanceToSqr(p);
                if (dd < best) { best = dd; npc = a; }
            }
        }
        if (npc == null || !npc.data().charla_ia) return;
        if (c == null || !c.npc.equals(npc.getUUID())) {
            npc.addCounter(p.getUUID(), "visitas", 1);
            c = new Conv();
            c.player = p.getUUID();
            c.npc = npc.getUUID();
            c.free = true;
            end(p.getUUID());
            CONVS.put(p.getUUID(), c);
        }
        npc.talkingTo = p.getUUID();
        c.idle = 0;
        c.awaiting = false;
        c.opts.clear();
        c.lines.clear();
        Brain.Reply r = Brain.reply(npc, p, null, msg);
        c.pendingReply = r.text();
        c.pendingGesture = r.gesture();
        c.wait = Math.max(c.wait, 14);
    }

    public static void say(Actor npc, ServerPlayer p, String text) {
        Conv c = CONVS.get(p.getUUID());
        if (c == null || !c.npc.equals(npc.getUUID())) {
            c = new Conv();
            c.player = p.getUUID();
            c.npc = npc.getUUID();
            c.free = true;
            CONVS.put(p.getUUID(), c);
        }
        c.lines.add(text);
        npc.talkingTo = p.getUUID();
    }

    public static void end(UUID player) {
        Conv c = CONVS.remove(player);
        if (c != null) {
            // el NPC deja de mirar al jugador
            for (var lvl : net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getAllLevels()) {
                Entity e = lvl.getEntity(c.npc);
                if (e instanceof Actor a && player.equals(a.talkingTo)) a.talkingTo = null;
            }
        }
    }

    public static void endAllWith(Actor npc) {
        CONVS.values().removeIf(c -> c.npc.equals(npc.getUUID()));
        npc.talkingTo = null;
    }

    public static void endAll() { CONVS.clear(); GREETED.clear(); }
}
