package com.tunombre.lootmaster;

import com.ogaba.sa_decor.common.loot.DecorLooseLootSavedData;
import com.ogaba.sa_decor.common.loot.DecorLootConfigManager;
import com.ogaba.sa_decor.common.loot.DecorLootContainerData;
import com.ogaba.sa_decor.common.loot.DecorLootInteraction;
import com.ogaba.sa_decor.common.loot.DecorLootMarkerIndexSavedData;
import com.ogaba.sa_decor.common.loot.DecorLootTargeting;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tareas largas repartidas en ticks: escaneo, relleno inicial, evento, revertir y reinicio de loots. Una sola a la vez. */
public final class Jobs {
    public enum Kind { SCAN, FILL, EVENT, REVERT, RESET }

    private record Unit(ServerLevel level, int cx, int cz, List<BlockPos> pos) { }

    public static final class Job {
        public final Kind kind;
        public final String arg;
        public final Map<String, Integer> c = new LinkedHashMap<>();
        final Deque<Unit> units = new ArrayDeque<>();
        final List<String> dims = new ArrayList<>();
        int total, doneUnits;
        boolean cancelled, finished;
        State.Data st;

        Job(Kind kind, String arg) { this.kind = kind; this.arg = arg; }

        void inc(String k) { c.merge(k, 1, Integer::sum); }
    }

    private static Job current;
    private static Job last;
    private static final Pattern REGION = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    private static final java.util.Set<String> TECH = java.util.Set.of("minecraft:hopper", "minecraft:dropper", "minecraft:dispenser", "minecraft:furnace",
            "minecraft:blast_furnace", "minecraft:smoker", "minecraft:brewing_stand", "minecraft:crafter", "minecraft:jukebox", "minecraft:chiseled_bookshelf",
            "minecraft:decorated_pot", "minecraft:lectern", "minecraft:campfire", "minecraft:soul_campfire");

    private Jobs() { }

    public static Job current() { return current; }
    public static Job last() { return last; }

    public static void stop() { if (current != null) { current.cancelled = true; } }

    // ------------------------------------------------------------------ arranque ----
    /** @return mensaje de error o null si arranco */
    public static String start(MinecraftServer s, Kind kind, String arg, String dimSel) {
        if (current != null) return "Ya hay una tarea en curso (" + label(current) + "). Espera o deténla.";
        State st = State.get(s);
        Job j = new Job(kind, arg == null ? "" : arg);
        j.st = st.d;
        try {
            switch (kind) {
                case SCAN, FILL -> {
                    for (ServerLevel lv : s.getAllLevels()) {
                        String id = lv.dimension().location().toString();
                        if (!dimSel.equals("todas") && !dimSel.equals(id)) continue;
                        if (kind == Kind.FILL && st.d.fillDone.containsKey(id)) { j.inc("dimensiones_ya_rellenadas"); continue; }
                        j.dims.add(id);
                        for (long l : regionChunks(s, lv)) j.units.add(new Unit(lv, (int) (l >> 32), (int) l, null));
                    }
                    if (kind == Kind.FILL && j.dims.isEmpty()) return "Ese relleno ya se hizo. Para repetirlo hay que desbloquearlo antes.";
                }
                case EVENT, RESET -> {
                    for (ServerLevel lv : s.getAllLevels()) {
                        Map<Long, List<BlockPos>> by = new LinkedHashMap<>();
                        for (Map.Entry<BlockPos, DecorLootMarkerIndexSavedData.Marker> e : DecorLootMarkerIndexSavedData.get(lv).snapshot().entrySet()) {
                            String pid = e.getValue().profileId();
                            if (kind == Kind.RESET && !j.arg.isEmpty() && !j.arg.equals(pid)) continue;
                            if (!(kind == Kind.RESET && !j.arg.isEmpty()) && st.d.prot.contains(pid)) { j.inc("protegidos"); continue; }
                            by.computeIfAbsent(ChunkPos.asLong(e.getKey()), k -> new ArrayList<>()).add(e.getKey());
                        }
                        for (Map.Entry<Long, List<BlockPos>> e : by.entrySet()) j.units.add(new Unit(lv, ChunkPos.getX(e.getKey()), ChunkPos.getZ(e.getKey()), e.getValue()));
                    }
                }
                case REVERT -> {
                    Map<ServerLevel, Map<Long, List<BlockPos>>> by = new LinkedHashMap<>();
                    for (String key : st.d.snap.keySet()) {
                        String[] p = key.split("\\|");
                        if (p.length != 4) continue;
                        ServerLevel lv = s.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new ResourceLocation(p[0])));
                        if (lv == null) continue;
                        BlockPos bp = new BlockPos(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
                        by.computeIfAbsent(lv, k -> new LinkedHashMap<>()).computeIfAbsent(ChunkPos.asLong(bp), k -> new ArrayList<>()).add(bp);
                    }
                    for (Map.Entry<ServerLevel, Map<Long, List<BlockPos>>> a : by.entrySet())
                        for (Map.Entry<Long, List<BlockPos>> e : a.getValue().entrySet()) j.units.add(new Unit(a.getKey(), ChunkPos.getX(e.getKey()), ChunkPos.getZ(e.getKey()), e.getValue()));
                }
            }
        } catch (RuntimeException | java.io.IOException ex) {
            return "No se pudo preparar la tarea: " + ex.getMessage();
        }
        j.total = j.units.size();
        current = j;
        return null;
    }

    private static List<Long> regionChunks(MinecraftServer s, ServerLevel lv) throws java.io.IOException {
        List<Long> out = new ArrayList<>();
        Path dir = DimensionType.getStorageFolder(lv.dimension(), s.getWorldPath(LevelResource.ROOT)).resolve("region");
        if (!Files.isDirectory(dir)) return out;
        try (var stream = Files.list(dir)) {
            for (Path f : (Iterable<Path>) stream::iterator) {
                Matcher m = REGION.matcher(f.getFileName().toString());
                if (!m.matches()) continue;
                int rx = Integer.parseInt(m.group(1)), rz = Integer.parseInt(m.group(2));
                byte[] head;
                try (InputStream in = Files.newInputStream(f)) { head = in.readNBytes(4096); }
                if (head.length < 4096) continue;
                for (int i = 0; i < 1024; i++) {
                    int off = ((head[i * 4] & 255) << 16) | ((head[i * 4 + 1] & 255) << 8) | (head[i * 4 + 2] & 255);
                    int cnt = head[i * 4 + 3] & 255;
                    if (off == 0 || cnt == 0) continue;
                    int cx = rx * 32 + (i & 31), cz = rz * 32 + (i >> 5);
                    out.add(((long) cx << 32) | (cz & 0xFFFFFFFFL));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ avance ----
    /** Se llama cada tick de servidor. @return true si la tarea acaba de terminar. */
    public static boolean tick(MinecraftServer s) {
        Job j = current;
        if (j == null) return false;
        State st = State.get(s);
        long t0 = System.nanoTime();
        int n = 0;
        while (!j.cancelled && !j.units.isEmpty() && n < Math.max(1, st.d.perTick) && System.nanoTime() - t0 < 18_000_000L) {
            Unit u = j.units.poll();
            n++;
            j.doneUnits++;
            try {
                LevelChunk ch = u.level.getChunkSource().getChunk(u.cx, u.cz, ChunkStatus.FULL, true) instanceof LevelChunk lc ? lc : null;
                if (ch == null) { j.inc("chunks_no_cargados"); continue; }
                j.inc("chunks");
                List<BlockPos> list = u.pos != null ? u.pos : new ArrayList<>(ch.getBlockEntities().keySet());
                for (BlockPos p : list) handle(s, st, j, u.level, p);
            } catch (RuntimeException ex) {
                j.inc("errores");
                LootMaster.LOG.warn("Fallo en chunk {} {}: {}", u.cx, u.cz, ex.toString());
            }
        }
        if (j.cancelled || j.units.isEmpty()) {
            finish(st, j);
            return true;
        }
        return false;
    }

    private static void finish(State st, Job j) {
        j.finished = true;
        if (!j.cancelled) {
            String now = java.time.LocalDateTime.now().withNano(0).toString().replace('T', ' ');
            if (j.kind == Kind.FILL) for (String d : j.dims) st.d.fillDone.put(d, now);
            if (j.kind == Kind.EVENT) st.d.evProfile = j.arg;
            if (j.kind == Kind.REVERT) { st.d.snap.clear(); st.d.evProfile = ""; }
        } else if (j.kind == Kind.EVENT && st.d.snap.isEmpty()) st.d.evProfile = "";
        st.dirty();
        last = j;
        current = null;
    }

    public static String label(Job j) {
        return switch (j.kind) {
            case SCAN -> "escaneo";
            case FILL -> "relleno inicial";
            case EVENT -> "evento «" + j.arg + "»";
            case REVERT -> "revertir evento";
            case RESET -> j.arg.isEmpty() ? "reinicio de todos los loots" : "reinicio del perfil «" + j.arg + "»";
        };
    }

    // ------------------------------------------------------------------ por bloque ----
    private static String key(ServerLevel lv, BlockPos p) { return lv.dimension().location() + "|" + p.getX() + "|" + p.getY() + "|" + p.getZ(); }

    private static boolean isLoose(ServerLevel lv, BlockPos p) {
        try { return DecorLooseLootSavedData.get(lv).getManualProfile(p) != null; } catch (RuntimeException e) { return false; }
    }

    private static void handle(MinecraftServer s, State st, Job j, ServerLevel lv, BlockPos p) {
        switch (j.kind) {
            case SCAN, FILL -> fill(st, j, lv, p);
            case EVENT -> event(st, j, lv, p);
            case RESET -> reset(st, j, lv, p);
            case REVERT -> revert(st, j, lv, p);
        }
    }

    public static boolean typeAllowed(State.Data d, String bid, boolean sa) {
        Boolean ov = d.typeOn.get(bid);
        if (ov != null) return ov;
        return defaultType(bid, sa);
    }

    public static boolean defaultType(String bid, boolean sa) {
        if (sa) return true;
        // cualquier contenedor de cualquier mod, menos bloques tecnicos (se pueden activar a mano en la pestana Tipos)
        return !TECH.contains(bid);
    }

    private static void fill(State st, Job j, ServerLevel lv, BlockPos p) {
        BlockEntity be = lv.getBlockEntity(p);
        if (be == null) return;
        if (!(be instanceof Container)) {
            if (be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).isPresent()) {
                j.inc("inventarios_no_compatibles_con_SA");
                ResourceLocation r0 = ForgeRegistries.BLOCKS.getKey(be.getBlockState().getBlock());
                if (r0 != null) LootMaster.LOG.info("[LootMaster] inventario sin soporte de sa_decor (no implementa Container): {}", r0);
            }
            return;
        }
        BlockState bs = be.getBlockState();
        ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(bs.getBlock());
        String bid = rl == null ? "?" : rl.toString();
        j.inc("contenedores");
        if (st.d.seen.add(bid)) st.dirty();
        if (bs.getBlock() instanceof ChestBlock && bs.hasProperty(ChestBlock.TYPE) && bs.getValue(ChestBlock.TYPE) != ChestType.SINGLE) { j.inc("cofres_dobles_no_soportados"); return; }
        Optional<DecorLootTargeting.Target> ot = DecorLootTargeting.findLootContainer(lv, p);
        if (ot.isEmpty()) { j.inc("no_soportados"); return; }
        DecorLootTargeting.Target t = ot.get();
        DecorLootContainerData data = t.data();
        if (data.isMarkedAsLootCrate()) { j.inc("ya_con_etiqueta"); return; }
        if (data.hasPendingSeed()) {
            if (!st.d.fillSeeds) { j.inc("semillas_omitidas"); return; }
            if (j.kind == Kind.FILL) DecorLootInteraction.applyStructureSeedIfPresent(lv, t);
            j.inc("semillas_de_estructura");
            return;
        }
        if (isLoose(lv, p)) { j.inc("loot_suelto"); return; }
        if (!typeAllowed(st.d, bid, t.lootContainer() != null)) { j.inc("tipo_desactivado"); return; }
        boolean empty = DecorLootInteraction.isContainerEmpty(t.container());
        if (!empty && !st.d.fillNonEmpty) { j.inc("con_objetos_omitidos"); return; }
        if (j.kind == Kind.SCAN) { j.inc("se_rellenarian"); return; }
        DecorLootInteraction.markAsLootCrate(lv, t, j.arg, false, !empty);
        j.inc("rellenados");
    }

    private static void event(State st, Job j, ServerLevel lv, BlockPos p) {
        Optional<DecorLootTargeting.Target> ot = DecorLootTargeting.findLootContainer(lv, p);
        if (ot.isEmpty()) { j.inc("sin_contenedor"); return; }
        DecorLootTargeting.Target t = ot.get();
        DecorLootContainerData data = t.data();
        if (!data.isMarkedAsLootCrate()) { j.inc("sin_etiqueta"); return; }
        String cur = data.configId();
        if (st.d.prot.contains(cur)) { j.inc("protegidos"); return; }
        if (cur.equals(j.arg)) { j.inc("ya_eran_asi"); return; }
        if (isLoose(lv, p)) { j.inc("loot_suelto"); return; }
        st.d.snap.putIfAbsent(key(lv, p), cur);
        DecorLootInteraction.markAsLootCrate(lv, t, j.arg, data.isLocked(), st.d.evClear);
        j.inc("cambiados");
    }

    private static void reset(State st, Job j, ServerLevel lv, BlockPos p) {
        Optional<DecorLootTargeting.Target> ot = DecorLootTargeting.findLootContainer(lv, p);
        if (ot.isEmpty()) { j.inc("sin_contenedor"); return; }
        DecorLootTargeting.Target t = ot.get();
        DecorLootContainerData data = t.data();
        if (!data.isMarkedAsLootCrate()) { j.inc("sin_etiqueta"); return; }
        String cur = data.configId();
        if (!DecorLootConfigManager.has(cur)) { j.inc("perfil_inexistente"); return; }
        if (isLoose(lv, p)) { j.inc("loot_suelto"); return; }
        DecorLootInteraction.markAsLootCrate(lv, t, cur, data.isLocked(), true);
        j.inc("reiniciados");
    }

    private static void revert(State st, Job j, ServerLevel lv, BlockPos p) {
        String old = st.d.snap.get(key(lv, p));
        if (old == null) return;
        Optional<DecorLootTargeting.Target> ot = DecorLootTargeting.findLootContainer(lv, p);
        if (ot.isEmpty() || !DecorLootConfigManager.has(old)) { j.inc("no_restaurados"); return; }
        DecorLootContainerData data = ot.get().data();
        DecorLootInteraction.markAsLootCrate(lv, ot.get(), old, data.isLocked(), st.d.evClear);
        j.inc("restaurados");
    }

    public static Map<String, Integer> countByProfile(MinecraftServer s) {
        Map<String, Integer> m = new TreeMap<>();
        for (ServerLevel lv : s.getAllLevels())
            for (DecorLootMarkerIndexSavedData.Marker mk : DecorLootMarkerIndexSavedData.get(lv).snapshot().values()) m.merge(mk.profileId(), 1, Integer::sum);
        return m;
    }

    public static Map<String, Integer> countByDim(MinecraftServer s) {
        Map<String, Integer> m = new TreeMap<>();
        for (ServerLevel lv : s.getAllLevels()) m.put(lv.dimension().location().toString(), DecorLootMarkerIndexSavedData.get(lv).snapshot().size());
        return m;
    }
}
