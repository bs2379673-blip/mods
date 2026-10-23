package com.tunombre.lootmaster;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Decorador inteligente: lee la zona marcada con la varita, encuentra las habitaciones (suelo con techo y paredes), decide de que tipo
 * es cada una (dormitorio, cocina, baño, oficina, comercio...) y propone decoracion segun un tema (config/lootmaster/decor/*.json),
 * pegada a paredes y esquinas, sin tapar puertas ni pasillos. Primero genera un PLAN que se ve en el mundo; luego se aplica (con deshacer).
 */
public final class Decor {
    private static final Gson GSON = new Gson();
    private static final int MAX_TILES = 300_000, MAX_PLAN = 20_000, PREVIEW_MAX = 12_000;

    public record Entry(Block block, String where, int w, int max, int height, int spacing) { }

    public static final class Theme {
        public String id, name;
        public double dIn = 0.10, dEx = 0.012;
        public final Map<String, List<Entry>> rooms = new LinkedHashMap<>();
    }

    public record Placement(BlockPos pos, BlockState state, int cat) { }

    public static final class Plan {
        public String dim = "";
        public final List<Placement> list = new ArrayList<>();
        public String theme = "";
        public int seed;
    }

    private static final Map<String, Theme> THEMES = new TreeMap<>();
    private static final Map<UUID, Plan> PLANS = new HashMap<>();
    private static final Set<String> BAD_BLOCKS = new HashSet<>();

    private Decor() { }

    public static Plan plan(ServerPlayer p) { return PLANS.get(p.getUUID()); }

    public static void discard(ServerPlayer p) { PLANS.remove(p.getUUID()); }

    public static void clearAll() { PLANS.clear(); }

    // ------------------------------------------------------------------ temas ----
    public static Map<String, Theme> themes() { return THEMES; }

    public static void loadThemes() {
        THEMES.clear();
        Path dir = FMLPaths.CONFIGDIR.get().resolve("lootmaster").resolve("decor");
        try {
            Files.createDirectories(dir);
            Path def = dir.resolve("apocaliptico.json");
            if (!Files.exists(def)) {
                try (InputStream in = Decor.class.getResourceAsStream("/defaults/decor/apocaliptico.json")) {
                    if (in != null) Files.write(def, in.readAllBytes());
                }
            }
            try (var st = Files.list(dir)) {
                for (Path f : (Iterable<Path>) st::iterator) {
                    String n = f.getFileName().toString();
                    if (!n.endsWith(".json")) continue;
                    try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                        Theme t = parse(n.substring(0, n.length() - 5), GSON.fromJson(r, JsonObject.class));
                        if (t != null) THEMES.put(t.id, t);
                    } catch (RuntimeException | IOException e) {
                        LootMaster.LOG.warn("[LootMaster] tema {} invalido: {}", n, e.toString());
                    }
                }
            }
        } catch (IOException e) {
            LootMaster.LOG.warn("[LootMaster] no se pudieron leer los temas: {}", e.toString());
        }
    }

    private static Theme parse(String id, JsonObject o) {
        if (o == null) return null;
        Theme t = new Theme();
        t.id = id;
        t.name = o.has("name") ? o.get("name").getAsString() : id;
        if (o.has("density")) {
            JsonObject d = o.getAsJsonObject("density");
            if (d.has("interior")) t.dIn = d.get("interior").getAsDouble();
            if (d.has("exterior")) t.dEx = d.get("exterior").getAsDouble();
        }
        JsonObject rooms = o.getAsJsonObject("rooms");
        if (rooms == null) return t;
        for (Map.Entry<String, JsonElement> re : rooms.entrySet()) {
            List<Entry> l = new ArrayList<>();
            for (JsonElement e : re.getValue().getAsJsonArray()) {
                JsonObject x = e.getAsJsonObject();
                Block b = Edits.block(x.get("block").getAsString());
                if (b == null || b == Blocks.AIR) { LootMaster.LOG.info("[LootMaster] bloque de tema ausente (se omite): {}", x.get("block").getAsString()); continue; }
                if (unsupported(b)) continue;
                l.add(new Entry(b, x.has("where") ? x.get("where").getAsString() : "free", x.has("w") ? x.get("w").getAsInt() : 1,
                        x.has("max") ? x.get("max").getAsInt() : 99, x.has("height") ? x.get("height").getAsInt() : 1, x.has("spacing") ? x.get("spacing").getAsInt() : 1));
            }
            t.rooms.put(re.getKey(), l);
        }
        return t;
    }

    /** Bloques de varias partes o de doble altura: se colocan con su propio item, no con un setBlock simple. */
    private static boolean unsupported(Block b) {
        for (Class<?> c = b.getClass(); c != null && c != Object.class; c = c.getSuperclass())
            if (c.getSimpleName().endsWith("MultiblockBlock")) { LootMaster.LOG.info("[LootMaster] bloque de varias partes (se omite): {}", ForgeRegistries.BLOCKS.getKey(b)); return true; }
        for (Property<?> p : b.defaultBlockState().getProperties()) if (p.getValueClass() == DoubleBlockHalf.class) return true;
        return false;
    }

    // ------------------------------------------------------------------ lectura de la zona ----
    private static final class Tile { int y; boolean roof, nearDoor; int walls, head, ground; }

    private static final class Ctx {
        final Map<Long, Tile> tiles = new HashMap<>();
        final List<long[]> marks = new ArrayList<>();
        final List<Long> doors = new ArrayList<>();
        boolean overflow;
        int ya, yb;
    }

    private static final int BED = 1, KITCHEN = 2, BATH = 3, OFFICE = 4, SHOP = 5;

    private static final Map<Block, Integer> MARK_CACHE = new HashMap<>();

    private static int markOf(BlockState s) {
        Integer c = MARK_CACHE.get(s.getBlock());
        if (c == null) { c = markOf0(s); MARK_CACHE.put(s.getBlock(), c); }
        return c;
    }

    private static int markOf0(BlockState s) {
        Block b = s.getBlock();
        if (b instanceof BedBlock) return BED;
        ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(b);
        if (rl == null) return 0;
        String n = rl.getPath();
        if (n.contains("bed") || n.contains("sleeping_bag")) return BED;
        if (n.contains("toilet") || n.contains("bathroom") || n.contains("bathtub") || n.contains("shower")) return BATH;
        if (n.contains("stove") || n.contains("fridge") || n.contains("refrigerator") || n.contains("microwave") || n.contains("freezer") || n.contains("kitchen")
                || n.equals("furnace") || n.equals("smoker") || n.equals("blast_furnace") || n.equals("cauldron") || n.equals("campfire")) return KITCHEN;
        if (n.contains("bookshelf") || n.contains("lectern") || n.contains("desk") || n.contains("computer") || n.equals("pc") || n.contains("noteboard") || n.contains("office_chair")) return OFFICE;
        if (n.contains("counter") || n.contains("cash_register") || n.contains("store_shelf") || n.contains("marketing_shelf") || n.contains("vending")) return SHOP;
        return 0;
    }

    private static BlockState at(ServerLevel lv, Jobs.Job j, BlockPos p) {
        if (!j.chunks.contains(ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4))) return Blocks.STONE.defaultBlockState();
        return lv.getBlockState(p);
    }

    private static boolean solid(BlockState s) { return s.blocksMotion(); }

    private static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};   // N, E, S, W (bit 0..3)
    private static final Direction[] FACE_AWAY = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};

    private static void scanChunk(Jobs.Job j, Ctx cx, ServerLevel lv, LevelChunk ch) {
        int x1 = Math.max(j.x1(), ch.getPos().getMinBlockX()), x2 = Math.min(j.x2(), ch.getPos().getMaxBlockX());
        int z1 = Math.max(j.z1(), ch.getPos().getMinBlockZ()), z2 = Math.min(j.z2(), ch.getPos().getMaxBlockZ());
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos(), m2 = new BlockPos.MutableBlockPos(), m3 = new BlockPos.MutableBlockPos();
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                for (int y = cx.ya; y <= cx.yb; y++) {
                    int si = ch.getSectionIndex(y);
                    LevelChunkSection sec = ch.getSection(si);
                    if (sec.hasOnlyAir()) {
                        int below = si - 1;
                        if (below < 0 || ch.getSection(below).hasOnlyAir() || (y & 15) != 0) { y |= 15; continue; }
                    }
                    mp.set(x, y, z);
                    BlockState s = ch.getBlockState(mp);
                    if (!s.isAir()) {
                        int mk = markOf(s);
                        if (mk != 0) cx.marks.add(new long[]{mp.asLong(), mk});
                        Block b = s.getBlock();
                        if (b instanceof DoorBlock || b instanceof FenceGateBlock) cx.doors.add(mp.asLong());
                        continue;
                    }
                    if (y <= lv.getMinBuildHeight()) continue;
                    m2.set(x, y - 1, z);
                    BlockState bs = ch.getBlockState(m2);
                    if (bs.isAir() || !bs.isFaceSturdy(lv, m2, Direction.UP)) continue;
                    // altura libre
                    int head = 1;
                    for (; head < 4; head++) { m3.set(x, y + head, z); if (!ch.getBlockState(m3).isAir()) break; }
                    if (head < 2) continue;
                    if (cx.tiles.size() >= MAX_TILES) { cx.overflow = true; return; }
                    Tile t = new Tile();
                    t.y = y;
                    t.head = head;
                    // techo
                    for (int h = head; h <= 12 && y + h < lv.getMaxBuildHeight(); h++) {
                        m3.set(x, y + h, z);
                        BlockState u = ch.getBlockState(m3);
                        if (solid(u) && !(u.getBlock() instanceof LeavesBlock)) { t.roof = true; break; }
                    }
                    ResourceLocation g = ForgeRegistries.BLOCKS.getKey(bs.getBlock());
                    String gp = g == null ? "" : g.getPath();
                    t.ground = gp.contains("asphalt") || gp.contains("road") ? 1 : gp.contains("sidewalk") || gp.contains("paver") ? 2 : 0;
                    for (int d = 0; d < 4; d++) {
                        BlockPos n = new BlockPos(x + DIRS[d][0], y, z + DIRS[d][1]);
                        BlockState sn = at(lv, j, n);
                        BlockState sn2 = at(lv, j, n.above());
                        if (sn.getBlock() instanceof DoorBlock || sn.getBlock() instanceof FenceGateBlock) t.nearDoor = true;
                        if (solid(sn) && solid(sn2)) t.walls |= 1 << d;
                    }
                    cx.tiles.put(mp.asLong(), t);
                }
    }

    // ------------------------------------------------------------------ planificacion ----
    public static String startPlan(MinecraftServer s, ServerPlayer pl, ServerLevel lv, BlockPos a, BlockPos b, State.Data d) {
        Theme theme = THEMES.get(d.dTheme);
        if (theme == null) return "Elige un tema (config/lootmaster/decor).";
        int seed = d.dSeed;
        double factor = Math.max(0, Math.min(100, d.dDensity)) / 50.0;
        boolean interior = d.dInterior, exterior = d.dExterior;
        Ctx cx = new Ctx();
        // altura: desde 3 bloques bajo el punto mas bajo hasta 24 sobre el mas alto (asi no se decoran cuevas ni sotanos lejanos)
        int ya = Math.max(lv.getMinBuildHeight() + 1, Math.min(a.getY(), b.getY()) - 3);
        int yb = Math.min(lv.getMaxBuildHeight() - 2, Math.max(a.getY(), b.getY()) + 24);
        cx.ya = ya;
        cx.yb = yb;
        Plan plan = new Plan();
        plan.dim = lv.dimension().location().toString();
        plan.theme = theme.id;
        plan.seed = seed;
        Jobs.Job j = Jobs.create("planificar decoración", (job, level, ch, pos) -> { if (!cx.overflow) scanChunk(job, cx, level, ch); }, () -> {
            if (cx.overflow) { LootMaster.LOG.warn("[LootMaster] decoracion: zona demasiado grande"); Jobs.current().inc2("zona_demasiado_grande"); return; }
            build(Jobs.current(), cx, theme, seed, factor, interior, exterior, plan);
            PLANS.put(pl.getUUID(), plan);
            Ops.sendPlan(pl, plan);
        });
        j.setBox(Math.min(a.getX(), b.getX()), Math.max(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()), Math.max(a.getZ(), b.getZ()), ya, yb);
        int n = Jobs.boxUnits(s, lv, j, j.x1(), j.x2(), j.z1(), j.z2());
        if (n > 4000) return "La zona es demasiado grande para decorar de una vez (" + n + " chunks, máximo 4000). Divídela.";
        return Jobs.begin(j);
    }

    private static final class Room { final List<Long> tiles = new ArrayList<>(); final int[] votes = new int[6]; String type = "cuarto"; }

    private static void build(Jobs.Job job, Ctx cx, Theme theme, int seed, double factor, boolean interior, boolean exterior, Plan plan) {
        // cerca de puertas
        for (long dp : cx.doors) {
            BlockPos d = BlockPos.of(dp);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 0; dy++) {
                Tile t = cx.tiles.get(new BlockPos(d.getX() + dx, d.getY() + dy, d.getZ() + dz).asLong());
                if (t != null) t.nearDoor = true;
            }
        }
        // votos de tipo de habitacion
        Map<Long, int[]> votes = new HashMap<>();
        for (long[] m : cx.marks) {
            BlockPos p = BlockPos.of(m[0]);
            for (int[] dd : DIRS) {
                long k = new BlockPos(p.getX() + dd[0], p.getY(), p.getZ() + dd[1]).asLong();
                if (cx.tiles.containsKey(k)) votes.computeIfAbsent(k, q -> new int[6])[(int) m[1]]++;
            }
        }
        // habitaciones (con techo)
        Set<Long> seen = new HashSet<>();
        List<Room> rooms = new ArrayList<>();
        List<Long> outside = new ArrayList<>();
        for (Map.Entry<Long, Tile> e : cx.tiles.entrySet()) {
            Tile t = e.getValue();
            if (!t.roof) { outside.add(e.getKey()); continue; }
            if (!seen.add(e.getKey())) continue;
            Room r = new Room();
            java.util.ArrayDeque<Long> q = new java.util.ArrayDeque<>();
            q.add(e.getKey());
            while (!q.isEmpty()) {
                long k = q.poll();
                r.tiles.add(k);
                int[] v = votes.get(k);
                if (v != null) for (int i = 1; i < 6; i++) r.votes[i] += v[i];
                BlockPos p = BlockPos.of(k);
                for (int[] dd : DIRS) {
                    long nk = new BlockPos(p.getX() + dd[0], p.getY(), p.getZ() + dd[1]).asLong();
                    Tile nt = cx.tiles.get(nk);
                    if (nt != null && nt.roof && seen.add(nk)) q.add(nk);
                }
            }
            rooms.add(r);
        }
        Random seedRnd = new Random(seed * 1_000_003L + 17);
        Set<Long> blocked = new HashSet<>();
        Map<String, Integer> count = new LinkedHashMap<>();
        if (interior) {
            for (Room r : rooms) {
                int area = r.tiles.size();
                if (area < 4) continue;
                // abierta (porche) o cerrada
                int open = 0, sides = 0;
                for (long k : r.tiles) {
                    Tile t = cx.tiles.get(k);
                    BlockPos p = BlockPos.of(k);
                    for (int d = 0; d < 4; d++) {
                        if ((t.walls & (1 << d)) != 0) continue;
                        sides++;
                        if (!cx.tiles.containsKey(new BlockPos(p.getX() + DIRS[d][0], p.getY(), p.getZ() + DIRS[d][1]).asLong())) open++;
                    }
                }
                String type;
                if (sides > 0 && open > 0.35 * (sides + 4)) type = "porche";
                else {
                    int best = 0, bi = 0;
                    for (int i = 1; i < 6; i++) if (r.votes[i] > best) { best = r.votes[i]; bi = i; }
                    type = best > 0 ? switch (bi) { case BED -> "dormitorio"; case KITCHEN -> "cocina"; case BATH -> "bano"; case OFFICE -> "oficina"; default -> "comercio"; } : area >= 40 ? "sala" : "cuarto";
                }
                r.type = type;
                List<Entry> list = theme.rooms.get(type);
                if (list == null || list.isEmpty()) list = theme.rooms.get(area >= 40 ? "sala" : "cuarto");
                if (list == null || list.isEmpty()) continue;
                double want = area * theme.dIn * factor;
                int n = (int) Math.floor(want + seedRnd.nextDouble());
                Random rnd = new Random(seed * 31L + r.tiles.get(0));
                n = placeInRoom(cx, r.tiles, list, n, rnd, blocked, plan, type.equals("porche") ? 4 : 1, count, type);
            }
        }
        if (exterior) {
            Collections.shuffle(outside, new Random(seed * 7L + 5));
            Random rnd = new Random(seed * 13L + 3);
            Map<String, Integer> perEntryCount = new HashMap<>();
            for (long k : outside) {
                if (plan.list.size() >= MAX_PLAN) break;
                Tile t = cx.tiles.get(k);
                if (t.nearDoor) continue;
                String type = t.ground == 1 ? "calle" : t.ground == 2 ? "acera" : "exterior";
                List<Entry> list = theme.rooms.get(type);
                if (list == null || list.isEmpty()) continue;
                if (rnd.nextDouble() > theme.dEx * factor) continue;
                Entry en = pick(list, rnd, perEntryCount, "ex");
                if (en == null || t.head < Math.max(1, en.height())) continue;
                if (!okSpacing(cx, k, blocked, en.spacing()) || (t.ground != 1 && !ringOk(cx, k, blocked))) continue;
                if (en.where().equals("wall") && t.walls == 0) continue;
                put(plan, cx, k, en, t, rnd, 4, blocked);
                count.merge("colocara_" + type, 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> e : count.entrySet()) job.c.put(e.getKey(), e.getValue());
        job.c.put("habitaciones", rooms.size());
        job.c.put("total_decoraciones", plan.list.size());
    }

    private static Entry pick(List<Entry> list, Random rnd, Map<String, Integer> used, String prefix) {
        int total = 0;
        for (Entry e : list) if (used.getOrDefault(prefix + e.block().getDescriptionId(), 0) < e.max()) total += Math.max(1, e.w());
        if (total <= 0) return null;
        int r = rnd.nextInt(total);
        for (Entry e : list) {
            if (used.getOrDefault(prefix + e.block().getDescriptionId(), 0) >= e.max()) continue;
            r -= Math.max(1, e.w());
            if (r < 0) return e;
        }
        return null;
    }

    private static int placeInRoom(Ctx cx, List<Long> tiles, List<Entry> list, int n, Random rnd, Set<Long> blocked, Plan plan, int cat, Map<String, Integer> count, String type) {
        List<Long> wall = new ArrayList<>(), corner = new ArrayList<>(), center = new ArrayList<>(), all = new ArrayList<>();
        for (long k : tiles) {
            Tile t = cx.tiles.get(k);
            if (t.nearDoor) continue;
            all.add(k);
            int w = Integer.bitCount(t.walls);
            if (w >= 2 && ((t.walls & 5) != 0 && (t.walls & 10) != 0)) corner.add(k);
            else if (w >= 1) wall.add(k);
            else {
                BlockPos p = BlockPos.of(k);
                boolean in = true;
                for (int dx = -1; dx <= 1 && in; dx++) for (int dz = -1; dz <= 1; dz++) {
                    Tile o = cx.tiles.get(new BlockPos(p.getX() + dx, p.getY(), p.getZ() + dz).asLong());
                    if (o == null || o.walls != 0) { in = false; break; }
                }
                if (in) center.add(k);
            }
            if (w >= 1) { /* tambien cuenta para wall */ }
        }
        Map<String, Integer> used = new HashMap<>();
        int placed = 0;
        for (int i = 0; i < n && plan.list.size() < MAX_PLAN; i++) {
            Entry en = pick(list, rnd, used, "");
            if (en == null) break;
            List<Long> src = switch (en.where()) { case "wall" -> wall.isEmpty() ? corner : wall; case "corner" -> corner.isEmpty() ? wall : corner; case "center" -> center.isEmpty() ? all : center; default -> all; };
            if (src.isEmpty()) src = all;
            for (int attempt = 0; attempt < 14 && !src.isEmpty(); attempt++) {
                long k = src.get(rnd.nextInt(src.size()));
                Tile t = cx.tiles.get(k);
                if (blocked.contains(k) || t.head < Math.max(1, en.height())) continue;
                if (!okSpacing(cx, k, blocked, en.spacing()) || !ringOk(cx, k, blocked)) continue;
                put(plan, cx, k, en, t, rnd, cat, blocked);
                used.merge(en.block().getDescriptionId(), 1, Integer::sum);
                count.merge("colocara_" + type, 1, Integer::sum);
                placed++;
                break;
            }
        }
        return placed;
    }

    private static boolean okSpacing(Ctx cx, long k, Set<Long> blocked, int sp) {
        BlockPos p = BlockPos.of(k);
        for (int dx = -sp; dx <= sp; dx++) for (int dz = -sp; dz <= sp; dz++)
            if ((dx != 0 || dz != 0) && blocked.contains(new BlockPos(p.getX() + dx, p.getY(), p.getZ() + dz).asLong())) return false;
        return true;
    }

    /** Anillo de 8 vecinos: si al tapar este suelo el espacio libre alrededor se parte en dos, se descarta (no cierra pasillos). */
    private static boolean ringOk(Ctx cx, long k, Set<Long> blocked) {
        BlockPos p = BlockPos.of(k);
        int[][] ring = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
        boolean[] free = new boolean[8];
        int nfree = 0;
        for (int i = 0; i < 8; i++) {
            long nk = new BlockPos(p.getX() + ring[i][0], p.getY(), p.getZ() + ring[i][1]).asLong();
            free[i] = cx.tiles.containsKey(nk) && !blocked.contains(nk);
            if (free[i]) nfree++;
        }
        if (nfree == 0 || nfree == 8) return true;
        int runs = 0;
        for (int i = 0; i < 8; i++) if (free[i] && !free[(i + 7) % 8]) runs++;
        return runs <= 1;
    }

    private static void put(Plan plan, Ctx cx, long k, Entry en, Tile t, Random rnd, int cat, Set<Long> blocked) {
        BlockPos p = BlockPos.of(k);
        Direction face;
        int bits = t.walls;
        if (bits != 0 && !en.where().equals("center")) {
            List<Integer> ds = new ArrayList<>();
            for (int d = 0; d < 4; d++) if ((bits & (1 << d)) != 0) ds.add(d);
            face = FACE_AWAY[ds.get(rnd.nextInt(ds.size()))];
        } else face = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}[rnd.nextInt(4)];
        BlockState st = en.block().defaultBlockState();
        for (Property<?> pr : st.getProperties()) {
            if (pr instanceof DirectionProperty dp && dp.getName().equals("facing") && dp.getPossibleValues().contains(face)) { st = st.setValue(dp, face); break; }
        }
        plan.list.add(new Placement(p, st, cat));
        blocked.add(k);
    }

    // ------------------------------------------------------------------ aplicar ----
    public static String startApply(MinecraftServer s, ServerPlayer pl) {
        Plan plan = PLANS.get(pl.getUUID());
        if (plan == null || plan.list.isEmpty()) return "No hay un plan. Pulsa PLANIFICAR primero.";
        ServerLevel lv = null;
        for (ServerLevel l : s.getAllLevels()) if (l.dimension().location().toString().equals(plan.dim)) lv = l;
        if (lv == null) return "Dimensión no disponible.";
        Edits.Op op = new Edits.Op();
        op.label = "Decoración «" + plan.theme + "»";
        op.dim = plan.dim;
        Map<Long, List<BlockPos>> by = new LinkedHashMap<>();
        Map<Long, BlockState> states = new HashMap<>();
        for (Placement p : plan.list) {
            by.computeIfAbsent(ChunkPos.asLong(p.pos().getX() >> 4, p.pos().getZ() >> 4), q -> new ArrayList<>()).add(p.pos());
            states.put(p.pos().asLong(), p.state());
        }
        Jobs.Job j = Jobs.create("aplicar decoración", (job, level, ch, pos) -> {
            if (pos == null) return;
            for (BlockPos p : pos) {
                BlockState st = states.get(p.asLong());
                if (st == null) continue;
                if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()) { job.inc("omitidos_ocupados"); continue; }
                Edits.record(op, level, p);
                level.setBlock(p, st, 3);
                job.inc("colocados");
            }
        }, () -> { Edits.commit(s, op); PLANS.remove(pl.getUUID()); Ops.sendPlan(pl, null); });
        for (Map.Entry<Long, List<BlockPos>> e : by.entrySet()) Jobs.unit(j, lv, ChunkPos.getX(e.getKey()), ChunkPos.getZ(e.getKey()), e.getValue());
        return Jobs.begin(j);
    }

    /** [x,y,z,categoria, ...] para el preview. */
    public static JsonArray preview(Plan plan) {
        JsonArray a = new JsonArray();
        int step = Math.max(1, plan.list.size() / PREVIEW_MAX + (plan.list.size() % PREVIEW_MAX == 0 ? 0 : 1));
        for (int i = 0; i < plan.list.size(); i += step) {
            Placement p = plan.list.get(i);
            a.add(p.pos().getX());
            a.add(p.pos().getY());
            a.add(p.pos().getZ());
            a.add(p.cat());
        }
        return a;
    }
}
