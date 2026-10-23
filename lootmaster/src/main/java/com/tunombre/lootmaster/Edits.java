package com.tunombre.lootmaster;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cambios de bloques con historial (deshacer de varios pasos) y la herramienta de reemplazar bloques. */
public final class Edits {
    public static final int MAX_OPS = 15, MAX_ENTRIES = 400_000;

    public static final class Op {
        public String label = "", dim = "";
        public final List<Long> pos = new ArrayList<>();
        public final List<Integer> st = new ArrayList<>();
        public final Map<Integer, CompoundTag> be = new HashMap<>();
        final Set<Long> seen = new HashSet<>();
        public boolean full;
    }

    /** Historial guardado en el mundo. */
    public static final class Store extends SavedData {
        public final Deque<Op> ops = new ArrayDeque<>();

        static Store load(CompoundTag t) {
            Store s = new Store();
            for (Tag e : t.getList("ops", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) e;
                Op o = new Op();
                o.label = c.getString("label");
                o.dim = c.getString("dim");
                long[] p = c.getLongArray("pos");
                int[] st = c.getIntArray("st");
                for (int i = 0; i < p.length && i < st.length; i++) { o.pos.add(p[i]); o.st.add(st[i]); }
                for (Tag b : c.getList("bes", Tag.TAG_COMPOUND)) { CompoundTag bc = (CompoundTag) b; o.be.put(bc.getInt("i"), bc.getCompound("t")); }
                s.ops.addLast(o);
            }
            return s;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            ListTag l = new ListTag();
            for (Op o : ops) {
                CompoundTag c = new CompoundTag();
                c.putString("label", o.label);
                c.putString("dim", o.dim);
                c.putLongArray("pos", o.pos.stream().mapToLong(Long::longValue).toArray());
                c.putIntArray("st", o.st.stream().mapToInt(Integer::intValue).toArray());
                ListTag bl = new ListTag();
                for (Map.Entry<Integer, CompoundTag> e : o.be.entrySet()) { CompoundTag b = new CompoundTag(); b.putInt("i", e.getKey()); b.put("t", e.getValue()); bl.add(b); }
                c.put("bes", bl);
                l.add(c);
            }
            t.put("ops", l);
            return t;
        }
    }

    private static Store cached;
    private static MinecraftServer cachedFor;

    public static Store store(MinecraftServer s) {
        if (cached == null || cachedFor != s) {
            cached = s.overworld().getDataStorage().computeIfAbsent(Store::load, Store::new, "lootmaster_undo");
            cachedFor = s;
        }
        return cached;
    }

    public static void reset() { cached = null; cachedFor = null; }

    public static String lastLabel(MinecraftServer s) {
        Op o = store(s).ops.peekLast();
        return o == null ? "" : o.label + " · " + o.pos.size() + " bloques";
    }

    /** Guarda el estado ANTERIOR de la posicion (solo la primera vez que se toca). */
    public static void record(Op op, ServerLevel lv, BlockPos p) {
        if (op.full || !op.seen.add(p.asLong())) return;
        if (op.pos.size() >= MAX_ENTRIES) { op.full = true; return; }
        int idx = op.pos.size();
        op.pos.add(p.asLong());
        op.st.add(Block.getId(lv.getBlockState(p)));
        BlockEntity be = lv.getBlockEntity(p);
        if (be != null) op.be.put(idx, be.saveWithFullMetadata());
    }

    public static void commit(MinecraftServer s, Op op) {
        if (op.pos.isEmpty()) return;
        Store st = store(s);
        st.ops.addLast(op);
        while (st.ops.size() > MAX_OPS) st.ops.pollFirst();
        st.setDirty();
    }

    private static ServerLevel levelOf(MinecraftServer s, String dim) {
        return s.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dim)));
    }

    // ------------------------------------------------------------------ deshacer ----
    public static String startUndo(MinecraftServer s) {
        Store store = store(s);
        Op op = store.ops.peekLast();
        if (op == null) return "No hay nada que deshacer.";
        ServerLevel lv = levelOf(s, op.dim);
        if (lv == null) return "La dimensión de ese cambio no está disponible.";
        Map<Long, List<Integer>> by = new LinkedHashMap<>();
        for (int i = 0; i < op.pos.size(); i++) by.computeIfAbsent(ChunkPos.asLong(BlockPos.of(op.pos.get(i))), k -> new ArrayList<>()).add(i);
        Jobs.Job j = Jobs.create("deshacer: " + op.label, (job, level, ch, pos) -> {
            List<Integer> idx = by.get(ch.getPos().toLong());
            if (idx == null) return;
            for (int i : idx) {
                BlockPos p = BlockPos.of(op.pos.get(i));
                BlockState old = Block.stateById(op.st.get(i));
                level.setBlock(p, old, 3);
                CompoundTag be = op.be.get(i);
                if (be != null) {
                    BlockEntity nb = level.getBlockEntity(p);
                    if (nb != null) nb.load(be);
                }
                job.inc("restaurados");
            }
        }, () -> { store.ops.pollLast(); store.setDirty(); });
        for (Long k : by.keySet()) Jobs.unit(j, lv, ChunkPos.getX(k), ChunkPos.getZ(k), null);
        return Jobs.begin(j);
    }

    // ------------------------------------------------------------------ reemplazar ----
    public static Block block(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id.trim().contains(":") ? id.trim() : "minecraft:" + id.trim());
        if (rl == null || !ForgeRegistries.BLOCKS.containsKey(rl)) return null;
        return ForgeRegistries.BLOCKS.getValue(rl);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState copyProps(BlockState to, BlockState old) {
        for (Property<?> p : old.getProperties()) {
            for (Property<?> q : to.getProperties()) {
                if (q.getName().equals(p.getName()) && q.getValueClass() == p.getValueClass()) {
                    Comparable v = old.getValue(p);
                    if (q.getPossibleValues().contains(v)) to = to.setValue((Property) q, v);
                }
            }
        }
        return to;
    }

    public static String startReplace(MinecraftServer s, ServerLevel lv, BlockPos a, BlockPos b, String fromId, String toId, boolean keep, boolean ignoreY, boolean dry) {
        Block from = block(fromId), to = block(toId);
        if (from == null) return "El bloque «" + fromId + "» no existe.";
        if (!dry && to == null) return "El bloque «" + toId + "» no existe.";
        Op op = new Op();
        op.label = "Reemplazar " + fromId + " → " + toId;
        op.dim = lv.dimension().location().toString();
        Jobs.Job j = Jobs.create(dry ? "contar bloques a reemplazar" : "reemplazar bloques", (job, level, ch, pos) -> {
            int x1 = Math.max(job.x1(), ch.getPos().getMinBlockX()), x2 = Math.min(job.x2(), ch.getPos().getMaxBlockX());
            int z1 = Math.max(job.z1(), ch.getPos().getMinBlockZ()), z2 = Math.min(job.z2(), ch.getPos().getMaxBlockZ());
            int ya = ignoreY ? level.getMinBuildHeight() : Math.max(level.getMinBuildHeight(), job.y1());
            int yb = ignoreY ? level.getMaxBuildHeight() - 1 : Math.min(level.getMaxBuildHeight() - 1, job.y2());
            BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
            for (int si = 0; si < ch.getSectionsCount(); si++) {
                LevelChunkSection sec = ch.getSection(si);
                if (sec.hasOnlyAir() || !sec.maybeHas(st -> st.is(from))) continue;
                int base = ch.getSectionYFromSectionIndex(si) << 4;
                for (int y = Math.max(base, ya); y <= Math.min(base + 15, yb); y++)
                    for (int x = x1; x <= x2; x++)
                        for (int z = z1; z <= z2; z++) {
                            BlockState old = sec.getBlockState(x & 15, y & 15, z & 15);
                            if (!old.is(from)) continue;
                            job.inc(dry ? "se_reemplazarian" : "reemplazados");
                            if (dry) continue;
                            mp.set(x, y, z);
                            BlockState ns = to.defaultBlockState();
                            if (keep) ns = copyProps(ns, old);
                            record(op, level, mp);
                            level.setBlock(mp, ns, 3);
                        }
            }
        }, () -> { if (!dry) commit(s, op); });
        j.setBox(Math.min(a.getX(), b.getX()), Math.max(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()), Math.max(a.getZ(), b.getZ()), Math.min(a.getY(), b.getY()), Math.max(a.getY(), b.getY()));
        int n = Jobs.boxUnits(s, lv, j, j.x1(), j.x2(), j.z1(), j.z2());
        if (n > 40000) return "La zona es demasiado grande (" + n + " chunks).";
        return Jobs.begin(j);
    }
}
