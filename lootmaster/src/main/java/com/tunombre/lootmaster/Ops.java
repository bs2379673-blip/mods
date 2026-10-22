package com.tunombre.lootmaster;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ogaba.sa_decor.common.loot.DecorLootConfig;
import com.ogaba.sa_decor.common.loot.DecorLootConfigManager;
import com.ogaba.sa_decor.common.loot.DecorLootRespawn;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Acciones que llegan de la pantalla y estado que se le devuelve. */
public final class Ops {
    private static final Set<UUID> WATCH = new HashSet<>();

    private Ops() { }

    public static List<ServerPlayer> watchers(MinecraftServer s) {
        List<ServerPlayer> l = new ArrayList<>();
        for (UUID u : WATCH) { ServerPlayer p = s.getPlayerList().getPlayer(u); if (p != null) l.add(p); }
        return l;
    }

    public static boolean isWatching(ServerPlayer p) { return WATCH.contains(p.getUUID()); }

    public static void clearWatchers() { WATCH.clear(); }

    public static void open(ServerPlayer p) {
        WATCH.add(p.getUUID());
        Net.toClient(p, "state", state(p.getServer(), p).toString());
    }

    public static String summary(Jobs.Job j) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : j.c.entrySet()) sb.append(e.getKey().replace('_', ' ')).append(' ').append(e.getValue()).append(", ");
        return sb.length() == 0 ? "sin cambios" : sb.substring(0, sb.length() - 2);
    }

    public static JsonObject jobJson() {
        JsonObject o = new JsonObject();
        Jobs.Job j = Jobs.current();
        boolean running = j != null;
        if (j == null) j = Jobs.last();
        o.addProperty("running", running);
        if (j != null) {
            o.addProperty("label", Jobs.label(j));
            o.addProperty("done", j.doneUnits);
            o.addProperty("total", j.total);
            o.addProperty("cancelled", j.cancelled);
            o.addProperty("sum", summary(j));
            o.addProperty("kind", j.kind.name());
        }
        return o;
    }

    public static JsonObject state(MinecraftServer s, ServerPlayer pl) {
        State st = State.get(s);
        State.Data d = st.d;
        JsonObject o = new JsonObject();
        Map<String, Integer> counts = Jobs.countByProfile(s);
        JsonArray profs = new JsonArray();
        List<DecorLootConfig> cfgs = new ArrayList<>(DecorLootConfigManager.values());
        cfgs.sort(Comparator.comparing(DecorLootConfig::id));
        Set<String> ids = new TreeSet<>();
        for (DecorLootConfig c : cfgs) {
            ids.add(c.id());
            DecorLootRespawn r = c.respawn();
            JsonObject p = new JsonObject();
            p.addProperty("id", c.id());
            p.addProperty("name", c.displayName() == null ? c.id() : c.displayName());
            p.addProperty("count", counts.getOrDefault(c.id(), 0));
            p.addProperty("per", c.perPlayer());
            p.addProperty("rEn", r.enabled());
            p.addProperty("rMin", Math.round(r.timeTicks() / 12.0) / 100.0);
            p.addProperty("rOnly", r.onlyIfEmpty());
            p.addProperty("rClear", r.clearBeforeRespawn());
            p.addProperty("prot", d.prot.contains(c.id()));
            profs.add(p);
        }
        o.add("profiles", profs);
        JsonArray dims = new JsonArray();
        Map<String, Integer> byDim = Jobs.countByDim(s);
        for (ServerLevel lv : s.getAllLevels()) {
            String id = lv.dimension().location().toString();
            JsonObject x = new JsonObject();
            x.addProperty("id", id);
            x.addProperty("count", byDim.getOrDefault(id, 0));
            x.addProperty("done", d.fillDone.getOrDefault(id, ""));
            dims.add(x);
        }
        o.add("dims", dims);
        Set<String> types = new TreeSet<>(d.seen);
        types.addAll(d.typeOn.keySet());
        JsonArray ty = new JsonArray();
        for (String t : types) {
            boolean sa = t.startsWith("sa_decor:") || t.startsWith("sa_survival:");
            JsonObject x = new JsonObject();
            x.addProperty("id", t);
            x.addProperty("on", Jobs.typeAllowed(d, t, sa));
            x.addProperty("auto", !d.typeOn.containsKey(t));
            ty.add(x);
        }
        o.add("types", ty);
        JsonObject set = new JsonObject();
        set.addProperty("fillProfile", d.fillProfile);
        set.addProperty("fillSeeds", d.fillSeeds);
        set.addProperty("fillNonEmpty", d.fillNonEmpty);
        set.addProperty("evClear", d.evClear);
        set.addProperty("gPer", d.gPer);
        set.addProperty("gResp", d.gResp);
        set.addProperty("gOnly", d.gOnly);
        set.addProperty("gClear", d.gClear);
        set.addProperty("gMin", d.gMin);
        set.addProperty("perTick", d.perTick);
        set.addProperty("wProfile", d.wProfile);
        set.addProperty("wIgnoreY", d.wIgnoreY);
        set.addProperty("wReplace", d.wReplace);
        set.addProperty("wReplaceProt", d.wReplaceProt);
        set.addProperty("wNonEmpty", d.wNonEmpty);
        o.add("set", set);
        o.add("sel", Wand.json(pl));
        o.addProperty("wandUndo", d.wandSnap.size());
        o.addProperty("evProfile", d.evProfile);
        o.addProperty("evChanged", d.snap.size());
        o.addProperty("origCount", d.orig.size());
        o.add("job", jobJson());
        return o;
    }

    private static String str(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; }

    private static boolean bool(JsonObject o, String k) { return o.has(k) && o.get(k).getAsBoolean(); }

    private static void msg(ServerPlayer p, String m) { Net.toClient(p, "msg", m); }

    public static void handle(ServerPlayer p, String json) {
        MinecraftServer s = p.getServer();
        if (s == null) return;
        JsonObject o;
        try { o = JsonParser.parseString(json).getAsJsonObject(); } catch (RuntimeException e) { return; }
        State st = State.get(s);
        State.Data d = st.d;
        String op = str(o, "op");
        try {
            switch (op) {
                case "open" -> { open(p); return; }
                case "close" -> { WATCH.remove(p.getUUID()); return; }
                case "stop" -> { Jobs.stop(); msg(p, "Deteniendo la tarea…"); }
                case "set" -> setting(d, str(o, "k"), str(o, "v"));
                case "profile" -> profile(p, st, str(o, "id"), str(o, "k"), str(o, "v"));
                case "type" -> {
                    String id = str(o, "id"), v = str(o, "v");
                    if (v.equals("auto")) d.typeOn.remove(id); else d.typeOn.put(id, v.equals("true"));
                }
                case "scan" -> startMsg(p, Jobs.start(s, Jobs.Kind.SCAN, "", dim(o)), "Escaneo iniciado (solo cuenta, no cambia nada).");
                case "fill" -> {
                    String prof = d.fillProfile;
                    if (!DecorLootConfigManager.has(prof)) msg(p, "El perfil de relleno «" + prof + "» no existe.");
                    else startMsg(p, Jobs.start(s, Jobs.Kind.FILL, prof, dim(o)), "Relleno inicial iniciado con «" + prof + "».");
                }
                case "unlock" -> { d.fillDone.remove(str(o, "dim")); msg(p, "Relleno desbloqueado para " + str(o, "dim")); }
                case "event" -> {
                    String prof = str(o, "profile");
                    if (!DecorLootConfigManager.has(prof)) msg(p, "El perfil «" + prof + "» no existe.");
                    else startMsg(p, Jobs.start(s, Jobs.Kind.EVENT, prof, "todas"), "Evento «" + prof + "» en marcha.");
                }
                case "revert" -> {
                    if (d.snap.isEmpty()) msg(p, "No hay ningún evento que revertir.");
                    else startMsg(p, Jobs.start(s, Jobs.Kind.REVERT, "", "todas"), "Revirtiendo al loot anterior…");
                }
                case "reset" -> startMsg(p, Jobs.start(s, Jobs.Kind.RESET, str(o, "profile"), "todas"), "Reinicio de loots en marcha.");
                case "wand" -> {
                    Wand.Sel sel = Wand.get(p);
                    if (sel == null || sel.a == null || sel.b == null) msg(p, "Marca los dos puntos con la varita (clic izquierdo y clic derecho en un bloque).");
                    else {
                        var lv = Wand.levelOf(p, sel);
                        boolean dry = bool(o, "dry");
                        startMsg(p, lv == null ? "Dimensión no disponible." : Jobs.startWand(s, lv, sel.a, sel.b, dry), dry ? "Contando la zona…" : "Rellenando la zona…");
                    }
                }
                case "wundo" -> {
                    if (d.wandSnap.isEmpty()) msg(p, "No hay nada que deshacer.");
                    else startMsg(p, Jobs.start(s, Jobs.Kind.UNDO, "", "todas"), "Deshaciendo el último relleno de zona…");
                }
                case "gapply" -> { globalApply(d); msg(p, "Ajustes globales aplicados (los perfiles protegidos no se tocan)."); }
                case "grestore" -> { globalRestore(d); msg(p, "Perfiles devueltos a como estaban antes."); }
                default -> { }
            }
        } catch (RuntimeException e) {
            msg(p, "Error: " + e.getMessage());
            LootMaster.LOG.warn("op {} fallo", op, e);
        }
        st.dirty();
        Net.toClient(p, "state", state(s, p).toString());
    }

    private static String dim(JsonObject o) { String v = str(o, "dim"); return v.isEmpty() ? "todas" : v; }

    private static void startMsg(ServerPlayer p, String err, String ok) { msg(p, err != null ? err : ok); }

    private static void setting(State.Data d, String k, String v) {
        switch (k) {
            case "fillProfile" -> d.fillProfile = v;
            case "fillSeeds" -> d.fillSeeds = v.equals("true");
            case "fillNonEmpty" -> d.fillNonEmpty = v.equals("true");
            case "evClear" -> d.evClear = v.equals("true");
            case "gPer" -> d.gPer = v;
            case "gResp" -> d.gResp = v;
            case "gOnly" -> d.gOnly = v;
            case "gClear" -> d.gClear = v;
            case "gMin" -> d.gMin = Math.max(0, Math.min(100000, (int) Double.parseDouble(v)));
            case "wProfile" -> d.wProfile = v;
            case "wIgnoreY" -> d.wIgnoreY = v.equals("true");
            case "wReplace" -> d.wReplace = v.equals("true");
            case "wReplaceProt" -> d.wReplaceProt = v.equals("true");
            case "wNonEmpty" -> d.wNonEmpty = v.equals("true");
            case "perTick" -> d.perTick = Math.max(1, Math.min(40, (int) Double.parseDouble(v)));
            default -> { }
        }
    }

    private static int ticks(double minutes) { return (int) Math.max(20, Math.min(Integer.MAX_VALUE / 2, Math.round(minutes * 1200))); }

    private static void profile(ServerPlayer p, State st, String id, String k, String v) {
        if (!DecorLootConfigManager.has(id)) return;
        if (k.equals("prot")) {
            if (v.equals("true")) st.d.prot.add(id); else st.d.prot.remove(id);
            return;
        }
        DecorLootConfig c = DecorLootConfigManager.get(id);
        DecorLootRespawn r = c.respawn();
        switch (k) {
            case "per" -> DecorLootConfigManager.setPerPlayer(id, v.equals("true"));
            case "resp" -> DecorLootConfigManager.setRespawn(id, v.equals("true"), (int) r.timeTicks(), r.onlyIfEmpty(), r.clearBeforeRespawn());
            case "min" -> DecorLootConfigManager.setRespawn(id, r.enabled(), ticks(Double.parseDouble(v)), r.onlyIfEmpty(), r.clearBeforeRespawn());
            case "only" -> DecorLootConfigManager.setRespawn(id, r.enabled(), (int) r.timeTicks(), v.equals("true"), r.clearBeforeRespawn());
            case "clear" -> DecorLootConfigManager.setRespawn(id, r.enabled(), (int) r.timeTicks(), r.onlyIfEmpty(), v.equals("true"));
            default -> { }
        }
    }

    private static void globalApply(State.Data d) {
        List<String> ids = new ArrayList<>();
        for (DecorLootConfig c : DecorLootConfigManager.values()) ids.add(c.id());
        for (String id : ids) {
            if (d.prot.contains(id)) continue;
            DecorLootConfig c = DecorLootConfigManager.get(id);
            if (c == null) continue;
            DecorLootRespawn r = c.respawn();
            State.Orig og = d.orig.get(id);
            if (og == null) {
                og = new State.Orig();
                og.per = c.perPlayer();
                og.rEn = r.enabled();
                og.rOnly = r.onlyIfEmpty();
                og.rClear = r.clearBeforeRespawn();
                og.ticks = r.timeTicks();
                d.orig.put(id, og);
            }
            if (!d.gPer.isEmpty()) DecorLootConfigManager.setPerPlayer(id, d.gPer.equals("si"));
            boolean any = !d.gResp.isEmpty() || !d.gOnly.isEmpty() || !d.gClear.isEmpty() || d.gMin > 0;
            if (any) {
                boolean en = d.gResp.isEmpty() ? r.enabled() : d.gResp.equals("on");
                boolean only = d.gOnly.isEmpty() ? r.onlyIfEmpty() : d.gOnly.equals("si");
                boolean clr = d.gClear.isEmpty() ? r.clearBeforeRespawn() : d.gClear.equals("si");
                int t = d.gMin > 0 ? ticks(d.gMin) : (int) r.timeTicks();
                DecorLootConfigManager.setRespawn(id, en, t, only, clr);
            }
        }
    }

    private static void globalRestore(State.Data d) {
        for (Map.Entry<String, State.Orig> e : new ArrayList<>(d.orig.entrySet())) {
            if (!DecorLootConfigManager.has(e.getKey())) continue;
            State.Orig og = e.getValue();
            DecorLootConfigManager.setPerPlayer(e.getKey(), og.per);
            DecorLootConfigManager.setRespawn(e.getKey(), og.rEn, (int) Math.max(1, og.ticks), og.rOnly, og.rClear);
        }
        d.orig.clear();
    }
}
