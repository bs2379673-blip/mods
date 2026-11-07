package com.starlevi.starscenes.talk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.starlevi.starscenes.api.StarScenesAPI;
import com.starlevi.starscenes.net.Net;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Los dialogos: config/starscenes/dialogos/&lt;id&gt;.json. Cada uno tiene nodos con lo que dice el
 * NPC (uno o varios textos al azar), la animacion mientras habla, acciones al llegar y las opciones
 * del jugador (con condiciones para mostrarlas, acciones y a que nodo van).
 */
public final class Dialogues {
    public record Option(String text, String next, List<String> conditions, List<String> actions) {
    }

    public record Node(List<String> texts, String anim, String voice, List<String> conditions, String otherwise, List<String> actions, List<Option> options) {
    }

    public record Dialogue(String id, String start, Map<String, Node> nodes) {
    }

    private static final class Session {
        final int speaker;
        final Dialogue dialogue;
        String node;
        List<Option> shown = List.of();
        int version, hops;

        Session(int speaker, Dialogue d) {
            this.speaker = speaker;
            this.dialogue = d;
        }
    }

    private static final Map<String, Dialogue> ALL = new LinkedHashMap<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private Dialogues() {
    }

    public static List<String> ids() {
        return new ArrayList<>(ALL.keySet());
    }

    public static boolean exists(String id) {
        return ALL.containsKey(id);
    }

    /** Lee la carpeta; devuelve un resumen. */
    public static String load(List<String> errors) {
        ALL.clear();
        for (Map.Entry<String, JsonObject> e : JsonFolder.read("dialogos", errors).entrySet()) {
            try {
                ALL.put(e.getKey(), parse(e.getKey(), e.getValue()));
            } catch (Exception ex) {
                errors.add("dialogos/" + e.getKey() + ": " + ex.getMessage());
            }
        }
        return ALL.size() + " diálogos";
    }

    private static Dialogue parse(String id, JsonObject o) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        JsonObject ns = o.has("nodos") ? o.getAsJsonObject("nodos") : o;
        for (Map.Entry<String, JsonElement> e : ns.entrySet()) {
            if (!e.getValue().isJsonObject()) {
                continue;
            }
            JsonObject n = e.getValue().getAsJsonObject();
            List<Option> opts = new ArrayList<>();
            if (n.has("opciones")) {
                for (JsonElement oe : n.getAsJsonArray("opciones")) {
                    JsonObject op = oe.getAsJsonObject();
                    opts.add(new Option(JsonFolder.str(op, "texto", "..."), JsonFolder.str(op, "ir", null), JsonFolder.list(op, "si"),
                            JsonFolder.list(op, "acciones")));
                }
            }
            nodes.put(e.getKey(), new Node(JsonFolder.list(n, "texto"), JsonFolder.str(n, "animacion", "talk"), JsonFolder.str(n, "voz", null),
                    JsonFolder.list(n, "si"),
                    JsonFolder.str(n, "sino", null), JsonFolder.list(n, "acciones"), opts));
        }
        String start = JsonFolder.str(o, "inicio", nodes.isEmpty() ? "" : nodes.keySet().iterator().next());
        if (!nodes.containsKey(start)) {
            throw new IllegalArgumentException("no existe el nodo de inicio '" + start + "'");
        }
        return new Dialogue(id, start, nodes);
    }

    public static boolean open(ServerPlayer p, Entity speaker, String id) {
        Dialogue d = ALL.get(id);
        if (d == null) {
            p.displayClientMessage(Component.literal("§cNo existe el diálogo " + id), true);
            return false;
        }
        Session s = new Session(speaker == null ? -1 : speaker.getId(), d);
        SESSIONS.put(p.getUUID(), s);
        Quests.talked(p, speaker, id);
        go(p, d.start);
        return true;
    }

    private static Entity speaker(ServerPlayer p, Session s) {
        return s.speaker < 0 ? null : p.level().getEntity(s.speaker);
    }

    /** Pasa al nodo (desde una opcion o una accion "ir:nodo"). */
    public static void go(ServerPlayer p, String nodeId) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null) {
            return;
        }
        if (++s.hops > 24) {
            // un nodo que lleva a otro que vuelve: se corta
            close(p);
            return;
        }
        Node n = s.dialogue.nodes.get(nodeId);
        if (n == null) {
            close(p);
            return;
        }
        Entity sp = speaker(p, s);
        // un nodo con condiciones que no se cumplen salta a "sino" (o termina)
        if (!Actions.testAll(p, sp, n.conditions())) {
            if (n.otherwise() != null) {
                go(p, n.otherwise());
            } else {
                close(p);
            }
            return;
        }
        s.node = nodeId;
        int version = ++s.version;
        Actions.runAll(p, sp, n.actions());
        if (SESSIONS.get(p.getUUID()) != s || s.version != version) {
            // una accion cerro el dialogo o fue a otro nodo
            return;
        }
        List<Option> shown = new ArrayList<>();
        for (Option o : n.options()) {
            if (Actions.testAll(p, sp, o.conditions())) {
                shown.add(o);
            }
        }
        s.shown = shown;
        String text = n.texts().isEmpty() ? "" : n.texts().get(p.getRandom().nextInt(n.texts().size()));
        List<String> labels = new ArrayList<>();
        for (Option o : shown) {
            labels.add(Actions.fill(o.text(), p, sp));
        }
        if (sp != null && n.anim() != null && !n.anim().isEmpty()) {
            String anim = n.anim().contains("%") ? Actions.fill(n.anim(), p, sp).replace("§", "") : n.anim();
            if (!anim.isBlank()) {
                StarScenesAPI.play(sp, anim.trim());
            }
        }
        String name = sp == null ? "" : sp.getDisplayName().getString();
        String title = sp == null ? "" : Actions.placeholder("titulo", p, sp);
        if (!title.isEmpty()) {
            name = name + "  " + title.replace('&', '§');
        }
        String voice = n.voice() != null ? n.voice() : sp instanceof com.starlevi.starscenes.api.SceneActor a ? a.sceneVoice() : "simple";
        float pitch = sp instanceof com.starlevi.starscenes.api.SceneActor a ? a.sceneVoicePitch() : 1f;
        ServerPlayNetworking.send(p, new Net.Talk(s.speaker, name, Actions.fill(text, p, sp), labels, voice, pitch));
    }

    /** El jugador eligio una opcion (o -1: se fue). */
    public static void choose(ServerPlayer p, int index) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null) {
            return;
        }
        if (index < 0 || index >= s.shown.size()) {
            if (index < 0 || s.shown.isEmpty()) {
                close(p);
            }
            return;
        }
        Option o = s.shown.get(index);
        Entity sp = speaker(p, s);
        s.hops = 0;
        int version = s.version;
        Actions.runAll(p, sp, o.actions());
        if (SESSIONS.get(p.getUUID()) != s || s.version != version) {
            return;
        }
        if (o.next() != null) {
            go(p, o.next());
        } else {
            close(p);
        }
    }

    public static void close(ServerPlayer p) {
        if (SESSIONS.remove(p.getUUID()) != null) {
            ServerPlayNetworking.send(p, new Net.TalkEnd());
        }
    }

    public static boolean talking(ServerPlayer p) {
        return SESSIONS.containsKey(p.getUUID());
    }

    /** Se corta si el NPC desaparece o el jugador se aleja. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0 || SESSIONS.isEmpty()) {
            return;
        }
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            Session s = SESSIONS.get(id);
            if (p == null) {
                SESSIONS.remove(id);
                continue;
            }
            if (s.speaker >= 0) {
                Entity e = speaker(p, s);
                if (e == null || e.isRemoved() || e.distanceToSqr(p) > 12 * 12) {
                    close(p);
                }
            }
        }
    }

    public static void left(ServerPlayer p) {
        SESSIONS.remove(p.getUUID());
    }
}
