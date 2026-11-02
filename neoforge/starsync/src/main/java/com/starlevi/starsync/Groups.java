package com.starlevi.starsync;

import me.srrapero720.waterframes.common.block.data.DisplayData;
import me.srrapero720.waterframes.common.block.entity.DisplayTile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Grupos de pantallas vinculadas (guardados en el mundo). Cada tick del servidor: si alguien cambio
 * una pantalla del grupo (URL, lista, play/pausa, encendido, repetir, o salto de tiempo), esa pasa a
 * mandar y las demas copian todo; ademas se corrige el tiempo si alguna se desfasa.
 */
public final class Groups extends SavedData {
    public static final class Member {
        final ResourceKey<Level> dim;
        final BlockPos pos;

        Member(ResourceKey<Level> dim, BlockPos pos) {
            this.dim = dim;
            this.pos = pos.immutable();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Member m && m.dim.equals(this.dim) && m.pos.equals(this.pos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.dim, this.pos);
        }
    }

    /** Lo que se copia entre pantallas. */
    record State(String uri, String uris, int index, boolean active, boolean paused, boolean loop) {
        static State of(DisplayData d) {
            StringBuilder s = new StringBuilder();
            if (d.uris != null) {
                for (URI u : d.uris) {
                    s.append(u).append('\n');
                }
            }
            return new State(String.valueOf(d.uri), s.toString(), d.uri_index, d.active, d.paused, d.loop);
        }
    }

    public static final class Group {
        public final String id;
        public final List<Member> members = new ArrayList<>();
        /** true = suena solo la primera pantalla del grupo (las demas en silencio). */
        public boolean oneSound = true;
        transient State state;
        transient Member leader;
        transient int lastTick = -1;

        Group(String id) {
            this.id = id;
        }
    }

    private final Map<String, Group> groups = new LinkedHashMap<>();
    private static Groups instance;

    public static Groups get() {
        return instance;
    }

    public static void load(MinecraftServer s) {
        instance = s.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Groups::new, Groups::read), "starsync_grupos");
    }

    public static void unload() {
        instance = null;
    }

    public Map<String, Group> all() {
        return this.groups;
    }

    public Group group(String id) {
        return this.groups.get(id);
    }

    public Group create() {
        int n = 1;
        while (this.groups.containsKey("grupo-" + n)) {
            n++;
        }
        Group g = new Group("grupo-" + n);
        this.groups.put(g.id, g);
        setDirty();
        return g;
    }

    /** Grupo con un nombre elegido (comandos). */
    public Group create(String id) {
        Group g = new Group(id);
        this.groups.put(id, g);
        setDirty();
        return g;
    }

    public void remove(String id) {
        this.groups.remove(id);
        setDirty();
    }

    /** Agrega o saca una pantalla; devuelve true si quedo adentro. */
    public boolean toggle(Group g, ResourceKey<Level> dim, BlockPos pos) {
        Member m = new Member(dim, pos);
        // una pantalla esta en un solo grupo
        for (Group other : this.groups.values()) {
            if (other != g) {
                other.members.remove(m);
            }
        }
        boolean in;
        if (g.members.remove(m)) {
            in = false;
        } else {
            g.members.add(m);
            in = true;
        }
        g.state = null;
        setDirty();
        return in;
    }

    public Group groupOf(ResourceKey<Level> dim, BlockPos pos) {
        Member m = new Member(dim, pos);
        for (Group g : this.groups.values()) {
            if (g.members.contains(m)) {
                return g;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ sincronizacion

    public void tick(MinecraftServer s) {
        for (Group g : this.groups.values()) {
            List<DisplayTile> tiles = new ArrayList<>();
            List<Member> who = new ArrayList<>();
            for (Member m : g.members) {
                ServerLevel level = s.getLevel(m.dim);
                if (level == null || !level.isLoaded(m.pos)) {
                    continue;
                }
                BlockEntity be = level.getBlockEntity(m.pos);
                if (be instanceof DisplayTile t) {
                    tiles.add(t);
                    who.add(m);
                }
            }
            if (tiles.isEmpty()) {
                continue;
            }
            // quien cambio algo: esa pantalla manda
            int changed = -1;
            if (g.state == null) {
                changed = Math.max(0, who.indexOf(g.leader));
            } else {
                for (int i = 0; i < tiles.size(); i++) {
                    if (!State.of(tiles.get(i).data).equals(g.state)) {
                        changed = i;
                        break;
                    }
                }
            }
            int leaderIdx = changed >= 0 ? changed : Math.max(0, who.indexOf(g.leader));
            DisplayTile lead = tiles.get(leaderIdx);
            boolean seek = changed < 0 && g.lastTick >= 0 && Math.abs(lead.data.tick - g.lastTick) > 40;
            if (changed < 0 && !seek) {
                // nadie toco nada: alguien adelanto o atraso? (salto de tiempo en otra pantalla)
                for (int i = 0; i < tiles.size(); i++) {
                    if (i != leaderIdx && Math.abs(tiles.get(i).data.tick - lead.data.tick) > 60
                            && Math.abs(tiles.get(i).data.tick - g.lastTick) > 40) {
                        leaderIdx = i;
                        lead = tiles.get(i);
                        seek = true;
                        break;
                    }
                }
            }
            g.leader = who.get(leaderIdx);
            g.state = State.of(lead.data);
            boolean resync = changed >= 0 || seek || s.getTickCount() % 40 == 0;
            for (int i = 0; i < tiles.size(); i++) {
                DisplayTile t = tiles.get(i);
                boolean dirty = false;
                if (i != leaderIdx && (changed >= 0 || seek || !State.of(t.data).equals(g.state))) {
                    copy(lead.data, t.data);
                    dirty = true;
                }
                if (i != leaderIdx && resync && Math.abs(t.data.tick - lead.data.tick) > (changed >= 0 || seek ? 0 : 6)) {
                    t.data.tick = lead.data.tick;
                    t.data.tickMax = Math.max(t.data.tickMax, lead.data.tickMax);
                    dirty = true;
                }
                // sonido: solo la primera del grupo, o todas
                boolean shouldMute = g.oneSound && !who.get(i).equals(g.members.get(0));
                if (shouldMute != t.data.muted) {
                    t.data.muted = shouldMute;
                    dirty = true;
                }
                if (dirty) {
                    t.setDirty();
                }
            }
            g.lastTick = lead.data.tick;
        }
    }

    private static void copy(DisplayData from, DisplayData to) {
        to.uri = from.uri;
        to.uris = from.uris == null ? null : new LinkedList<>(from.uris);
        to.uri_index = from.uri_index;
        to.active = from.active;
        to.paused = from.paused;
        to.loop = from.loop;
        to.tick = from.tick;
        to.tickMax = from.tickMax;
    }

    // ------------------------------------------------------------------ guardado

    static Groups read(CompoundTag tag, HolderLookup.Provider p) {
        Groups g = new Groups();
        ListTag list = tag.getList("grupos", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            Group gr = new Group(c.getString("id"));
            gr.oneSound = !c.contains("un_sonido") || c.getBoolean("un_sonido");
            ListTag ms = c.getList("pantallas", Tag.TAG_COMPOUND);
            for (int k = 0; k < ms.size(); k++) {
                CompoundTag m = ms.getCompound(k);
                ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(m.getString("dim")));
                gr.members.add(new Member(dim, BlockPos.of(m.getLong("pos"))));
            }
            g.groups.put(gr.id, gr);
        }
        return g;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
        ListTag list = new ListTag();
        for (Group g : this.groups.values()) {
            CompoundTag c = new CompoundTag();
            c.putString("id", g.id);
            c.putBoolean("un_sonido", g.oneSound);
            ListTag ms = new ListTag();
            for (Member m : g.members) {
                CompoundTag mt = new CompoundTag();
                mt.putString("dim", m.dim.location().toString());
                mt.putLong("pos", m.pos.asLong());
                ms.add(mt);
            }
            c.put("pantallas", ms);
            list.add(c);
        }
        tag.put("grupos", list);
        return tag;
    }
}
