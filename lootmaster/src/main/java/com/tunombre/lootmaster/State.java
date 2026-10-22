package com.tunombre.lootmaster;

import com.google.gson.Gson;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Estado persistente del mod (guardado en el mundo): relleno hecho, perfiles protegidos, evento activo, originales, ajustes. */
public final class State extends SavedData {
    private static final Gson GSON = new Gson();
    private static State cached;
    private static MinecraftServer cachedFor;

    public static final class Orig { public boolean per, rEn, rOnly, rClear; public long ticks; }

    public static final class Data {
        public Map<String, String> fillDone = new TreeMap<>();
        public Set<String> prot = new TreeSet<>();
        public Map<String, Boolean> typeOn = new TreeMap<>();
        public Set<String> seen = new TreeSet<>();
        public String evProfile = "";
        public Map<String, String> snap = new LinkedHashMap<>();
        public Map<String, Orig> orig = new TreeMap<>();
        public String fillProfile = "civil";
        public boolean fillSeeds = true, fillNonEmpty = false, evClear = true;
        public String gPer = "", gResp = "", gOnly = "", gClear = "";
        public int gMin = 0;
        public int perTick = 3;
    }

    public Data d = new Data();

    public static State get(MinecraftServer s) {
        if (cached == null || cachedFor != s) {
            cached = s.overworld().getDataStorage().computeIfAbsent(State::load, State::new, "lootmaster");
            cachedFor = s;
        }
        return cached;
    }

    public static void reset() { cached = null; cachedFor = null; }

    public static State load(CompoundTag t) {
        State s = new State();
        try {
            Data x = GSON.fromJson(t.getString("json"), Data.class);
            if (x != null) s.d = x;
        } catch (RuntimeException ignored) { }
        if (s.d.fillDone == null) s.d.fillDone = new TreeMap<>();
        if (s.d.prot == null) s.d.prot = new TreeSet<>();
        if (s.d.typeOn == null) s.d.typeOn = new TreeMap<>();
        if (s.d.seen == null) s.d.seen = new TreeSet<>();
        if (s.d.snap == null) s.d.snap = new LinkedHashMap<>();
        if (s.d.orig == null) s.d.orig = new TreeMap<>();
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        t.putString("json", GSON.toJson(d));
        return t;
    }

    public void dirty() { setDirty(); }
}
