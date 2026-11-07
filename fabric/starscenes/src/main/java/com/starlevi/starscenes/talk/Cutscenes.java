package com.starlevi.starscenes.talk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.starlevi.starscenes.StarScenes;
import com.starlevi.starscenes.api.SceneActor;
import com.starlevi.starscenes.api.StarScenesAPI;
import com.starlevi.starscenes.net.Net;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Escenas (cinematicas): un .json en config/starscenes/escenas con pasos en el tiempo. El servidor hace lo
 * del mundo (los NPCs se animan, hablan, caminan; acciones y comandos) y le manda la escena entera al
 * jugador, que mueve la camara, pone las barras negras, los fundidos, los titulos y la musica.
 * Se lanza con /starscenes escena &lt;id&gt; o con la accion "escena:&lt;id&gt;" de un dialogo.
 */
public final class Cutscenes {
    private static final Map<String, JsonObject> SCENES = new LinkedHashMap<>();
    private static final Map<UUID, Running> RUNNING = new HashMap<>();

    private static final class Walk {
        Entity who;
        Vec3 from, to;
        long start, end;
    }

    private static final class Running {
        String id;
        JsonObject json;
        List<JsonObject> steps = new ArrayList<>();
        boolean[] done;
        long start;
        double length;
        Vec3 origin, playerAt;
        float yaw, pitch;
        boolean lock, skippable, relative;
        Entity speaker;
        List<Walk> walks = new ArrayList<>();
        Map<String, Entity> actors = new HashMap<>();
        List<String> onEnd = new ArrayList<>();
    }

    private Cutscenes() {
    }

    public static List<String> ids() {
        return new ArrayList<>(SCENES.keySet());
    }

    public static String load(List<String> errors) {
        SCENES.clear();
        SCENES.putAll(JsonFolder.read("escenas", errors));
        return SCENES.size() + " escenas";
    }

    public static boolean playing(ServerPlayer p) {
        return RUNNING.containsKey(p.getUUID());
    }

    static double time(JsonObject o) {
        return JsonFolder.dbl(o, "en", JsonFolder.dbl(o, "t", 0));
    }

    /** Empieza la escena id para el jugador (speaker = el NPC que la lanzo, puede ser null). */
    public static boolean play(ServerPlayer p, Entity speaker, String id) {
        JsonObject json = SCENES.get(id);
        if (json == null) {
            StarScenes.LOGGER.warn("StarScenes: no existe la escena '{}'", id);
            return false;
        }
        return play(p, speaker, id, json);
    }

    /** Igual, con una escena armada en el momento (la usan otros mods, ej. el casamiento de los aldeanos). */
    public static boolean play(ServerPlayer p, Entity speaker, String id, JsonObject json) {
        stop(p, false);
        Dialogues.close(p);
        Running r = new Running();
        r.id = id;
        r.json = json;
        r.speaker = speaker;
        r.start = p.level().getGameTime();
        r.playerAt = p.position();
        r.yaw = p.getYRot();
        r.pitch = p.getXRot();
        r.relative = JsonFolder.bool(json, "relativo", false);
        r.origin = r.relative ? p.position() : Vec3.ZERO;
        r.lock = JsonFolder.bool(json, "bloquear", true);
        r.skippable = JsonFolder.bool(json, "saltear", true);
        r.onEnd = JsonFolder.list(json, "al_terminar");
        JsonElement steps = json.get("pasos");
        if (steps instanceof JsonArray a) {
            for (JsonElement e : a) {
                if (e.isJsonObject()) {
                    r.steps.add(e.getAsJsonObject());
                }
            }
        }
        r.steps.sort((x, y) -> Double.compare(time(x), time(y)));
        r.done = new boolean[r.steps.size()];
        double len = JsonFolder.dbl(json, "duracion", 0);
        for (JsonObject s : r.steps) {
            double end = time(s) + Math.max(JsonFolder.dbl(s, "duracion", 0), 0);
            if (s.has("camara") && s.get("camara").isJsonObject()) {
                end = Math.max(end, time(s) + JsonFolder.dbl(s.getAsJsonObject("camara"), "duracion", 0));
            }
            len = Math.max(len, end);
        }
        r.length = len + 0.25;
        RUNNING.put(p.getUUID(), r);
        ServerPlayNetworking.send(p, new Net.SceneStart(id, json.toString(), r.origin.x, r.origin.y, r.origin.z));
        return true;
    }

    /** Corta la escena del jugador (endActions = correr "al_terminar"). */
    public static void stop(ServerPlayer p, boolean endActions) {
        Running r = RUNNING.remove(p.getUUID());
        if (r == null) {
            return;
        }
        for (Walk w : r.walks) {
            finishWalk(w);
        }
        ServerPlayNetworking.send(p, new Net.SceneStop());
        if (r.lock) {
            p.connection.teleport(r.playerAt.x, r.playerAt.y, r.playerAt.z, r.yaw, r.pitch);
        }
        if (endActions) {
            Actions.runAll(p, r.speaker, r.onEnd);
        }
    }

    public static void skip(ServerPlayer p) {
        Running r = RUNNING.get(p.getUUID());
        if (r != null && r.skippable) {
            // lo que hace la escena en el mundo igual tiene que pasar (acciones, comandos)
            for (int i = 0; i < r.steps.size(); i++) {
                if (!r.done[i]) {
                    r.done[i] = true;
                    JsonObject s = r.steps.get(i);
                    Actions.runAll(p, r.speaker, JsonFolder.list(s, "accion"));
                    if (s.has("comando")) {
                        Actions.run(p, r.speaker, "comando:" + JsonFolder.str(s, "comando", ""));
                    }
                }
            }
            stop(p, true);
        }
    }

    public static void left(ServerPlayer p) {
        Running r = RUNNING.remove(p.getUUID());
        if (r != null) {
            for (Walk w : r.walks) {
                finishWalk(w);
            }
        }
    }

    public static void tick(MinecraftServer server) {
        if (RUNNING.isEmpty()) {
            return;
        }
        for (ServerPlayer p : List.copyOf(server.getPlayerList().getPlayers())) {
            Running r = RUNNING.get(p.getUUID());
            if (r == null) {
                continue;
            }
            long now = p.level().getGameTime();
            double t = (now - r.start) / 20.0;
            for (int i = 0; i < r.steps.size(); i++) {
                if (!r.done[i] && time(r.steps.get(i)) <= t) {
                    r.done[i] = true;
                    try {
                        step(p, r, r.steps.get(i), now);
                    } catch (Exception e) {
                        StarScenes.LOGGER.warn("StarScenes: escena {}: paso fallido: {}", r.id, e.toString());
                    }
                    if (!RUNNING.containsKey(p.getUUID())) {
                        break; // el paso termino la escena
                    }
                }
            }
            if (!RUNNING.containsKey(p.getUUID())) {
                continue;
            }
            r.walks.removeIf(w -> walk(w, now));
            if (r.lock && p.position().distanceToSqr(r.playerAt) > 0.04) {
                p.connection.teleport(r.playerAt.x, r.playerAt.y, r.playerAt.z, p.getYRot(), p.getXRot());
            }
            if (t >= r.length) {
                stop(p, true);
            }
        }
    }

    private static Vec3 vec(Running r, JsonElement e) {
        if (!(e instanceof JsonArray a) || a.size() < 3) {
            return null;
        }
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()).add(r.origin);
    }

    /** Un actor de la escena: "@npc" (el que la lanzo), o el NPC/aldeano con ese nombre mas cerca. */
    private static Entity actor(ServerPlayer p, Running r, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        if (name.equals("@npc") || name.equals("@hablando")) {
            return r.speaker;
        }
        if (name.equals("@jugador")) {
            return p;
        }
        Entity known = r.actors.get(name);
        if (known != null && known.isAlive()) {
            return known;
        }
        String low = name.toLowerCase(Locale.ROOT);
        Entity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : p.level().getEntities((Entity) null, p.getBoundingBox().inflate(96), e -> e instanceof SceneActor)) {
            String n = e.getName().getString().toLowerCase(Locale.ROOT);
            if (n.equals(low) || e.getTags().contains(name)) {
                double d = e.distanceToSqr(p);
                if (d < bd) {
                    bd = d;
                    best = e;
                }
            }
        }
        if (best != null) {
            r.actors.put(name, best);
        }
        return best;
    }

    private static void step(ServerPlayer p, Running r, JsonObject s, long now) {
        Entity a = actor(p, r, JsonFolder.str(s, "npc", ""));
        if (a != null) {
            if (s.has("animacion")) {
                StarScenesAPI.play(a, JsonFolder.str(s, "animacion", "idle"));
            }
            if (s.has("decir")) {
                String text = Actions.fill(JsonFolder.str(s, "decir", ""), p, a);
                StarScenesAPI.say(a, text, (float) JsonFolder.dbl(s, "segundos", Math.max(2.5, text.length() / 12.0)));
            }
            Vec3 look = vec(r, s.get("mirar"));
            if (look == null && "@jugador".equals(JsonFolder.str(s, "mirar", ""))) {
                look = p.getEyePosition();
            }
            if (look != null) {
                Vec3 d = look.subtract(a.getEyePosition());
                float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
                float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
                a.setYRot(yaw);
                a.setYHeadRot(yaw);
                a.setYBodyRot(yaw);
                a.setXRot(pitch);
            }
            Vec3 to = vec(r, s.get("caminar"));
            if (to != null) {
                double secs = JsonFolder.dbl(s, "duracion", 0);
                if (a instanceof Mob m && secs <= 0) {
                    m.getNavigation().moveTo(to.x, to.y, to.z, JsonFolder.dbl(s, "velocidad", 0.6));
                } else {
                    Walk w = new Walk();
                    w.who = a;
                    w.from = a.position();
                    w.to = to;
                    w.start = now;
                    w.end = now + Math.max(1, Math.round((secs > 0 ? secs : w.from.distanceTo(to) / 2.5) * 20));
                    r.walks.removeIf(o -> o.who == a);
                    r.walks.add(w);
                    StarScenesAPI.play(a, JsonFolder.str(s, "animacion_caminar", "walk"));
                }
            }
            Vec3 tp = vec(r, s.get("teletransportar"));
            if (tp != null) {
                a.snapTo(tp.x, tp.y, tp.z, a.getYRot(), a.getXRot());
            }
        }
        Actions.runAll(p, a != null ? a : r.speaker, JsonFolder.list(s, "accion"));
        if (s.has("comando")) {
            Actions.run(p, a != null ? a : r.speaker, "comando:" + JsonFolder.str(s, "comando", ""));
        }
        if (s.has("gesto_jugador")) {
            StarScenesAPI.playerGesture(p, JsonFolder.str(s, "gesto_jugador", ""), (float) JsonFolder.dbl(s, "segundos", 3));
        }
        if (JsonFolder.bool(s, "fin", false)) {
            stop(p, true);
        }
    }

    /** Mueve al que camina; true cuando llego. */
    private static boolean walk(Walk w, long now) {
        if (!w.who.isAlive()) {
            return true;
        }
        double k = Math.min(1, (now - w.start) / (double) (w.end - w.start));
        Vec3 at = w.from.lerp(w.to, k);
        Vec3 d = w.to.subtract(w.from);
        float yaw = d.horizontalDistanceSqr() > 1e-4 ? (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90) : w.who.getYRot();
        w.who.snapTo(at.x, at.y, at.z, yaw, w.who.getXRot());
        w.who.setYHeadRot(yaw);
        w.who.setYBodyRot(yaw);
        if (k >= 1) {
            finishWalk(w);
            return true;
        }
        return false;
    }

    private static void finishWalk(Walk w) {
        if (w.who.isAlive()) {
            w.who.snapTo(w.to.x, w.to.y, w.to.z, w.who.getYRot(), w.who.getXRot());
            StarScenesAPI.play(w.who, "idle");
        }
    }
}
