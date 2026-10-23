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
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
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
 * Decorador inteligente. Lee la zona marcada con la varita, encuentra las habitaciones y las zonas exteriores, decide qué es cada una
 * (dormitorio, cocina, baño, oficina, comercio, calle, patio…) y las amuebla con COMPOSICIONES: conjuntos que tienen sentido (una cocina
 * con heladera, mesada, cocina y bacha en fila y alacenas arriba; un escritorio con su compu encima y su silla; una cama con mesitas;
 * un auto chocado con vidrios y conos…). Todo se define en config/lootmaster/decor/*.json.
 * Primero genera un PLAN que se ve en el mundo; luego se aplica (con deshacer) y los muebles que guardan cosas reciben su plantilla de loot.
 */
public final class Decor {
    private static final Gson GSON = new Gson();
    private static final int MAX_TILES = 300_000, MAX_PLAN = 40_000, PREVIEW_MAX = 12_000;
    private static final Direction[] HORIZ = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    // ------------------------------------------------------------------ modelo de los temas ----
    /** Un bloque dentro de una composición (x a lo largo de la pared, z hacia adentro del cuarto, y en altura). */
    public static final class Part {
        Block[] bs;
        int x, z, y;
        String f = "in", loot;
        double p = 1;
        boolean once;
    }

    /** Un elemento de una fila a lo largo de la pared. */
    public static final class Elem {
        Block[] bs;
        String f = "in", loot;
        int n = 1, gap;
        double p = 1;
        final List<Part> over = new ArrayList<>();
    }

    /** Cosas sueltas que se reparten alrededor de una composición (vidrios, conos, sangre…). */
    public static final class Scatter {
        Block[] bs;
        int min = 1, max = 1, d0 = 1, d1 = 2;
        double p = 1;
        String f = "rand", loot;
    }

    public static final class Comp {
        String id;
        int w = 1, max = 99, spacing, clear = 1, minArea, maxArea = Integer.MAX_VALUE, cat = 1;
        boolean core, open;
        double p = 1;
        String anchor = "wall", alt, loot, align;
        Set<Integer> ground;
        final List<Elem> row = new ArrayList<>();
        final List<Part> parts = new ArrayList<>();
        final List<Scatter> scatter = new ArrayList<>();
    }

    public static final class Clutter {
        Block b;
        String where = "free";
        int w = 1, max = 99, spacing = 1;
    }

    public static final class Room {
        final List<Comp> comps = new ArrayList<>();
        final List<Clutter> clutter = new ArrayList<>();
        final Map<String, Comp> byId = new HashMap<>();
    }

    public static final class Theme {
        public String id, name;
        public double cover = 0.26, clutter = 0.07, exterior = 0.004;
        public final Map<String, Room> rooms = new LinkedHashMap<>();
    }

    /** Una pieza del plan: un bloque (o mueble de varias partes) con su posición de origen y hacia dónde mira. */
    public record Piece(Block block, BlockPos pos, Direction facing, String loot, int cat, int group) { }

    public static final class Plan {
        public String dim = "";
        public final List<Piece> list = new ArrayList<>();
        public String theme = "";
        public int seed;
        /** Resumen legible (habitaciones, escenas, cuerpos…) para mostrar al terminar de planificar. */
        public String summary = "";
    }

    private static final Map<String, Theme> THEMES = new TreeMap<>();
    private static final Map<UUID, Plan> PLANS = new HashMap<>();
    private static final java.util.Set<UUID> BUSY = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private Decor() { }

    public static Plan plan(ServerPlayer p) { return PLANS.get(p.getUUID()); }

    public static void discard(ServerPlayer p) { PLANS.remove(p.getUUID()); }

    public static void clearAll() { PLANS.clear(); }

    public static Map<String, Theme> themes() { return THEMES; }

    // ------------------------------------------------------------------ carga de temas ----
    public static void loadThemes() {
        THEMES.clear();
        Pieces.clearCache();
        Path dir = FMLPaths.CONFIGDIR.get().resolve("lootmaster").resolve("decor");
        try {
            Files.createDirectories(dir);
            installDefault(dir);
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

    /** Copia el tema de ejemplo; si en la carpeta hay uno más viejo (otro formato o versión anterior) lo guarda como .bak y pone el nuevo. */
    private static void installDefault(Path dir) throws IOException {
        byte[] bundled;
        try (InputStream in = Decor.class.getResourceAsStream("/defaults/decor/apocaliptico.json")) {
            if (in == null) return;
            bundled = in.readAllBytes();
        }
        int bv = versionOf(GSON.fromJson(new String(bundled, StandardCharsets.UTF_8), JsonObject.class));
        Path def = dir.resolve("apocaliptico.json");
        boolean write = !Files.exists(def);
        if (!write) {
            int cv = 0;
            try (Reader r = Files.newBufferedReader(def, StandardCharsets.UTF_8)) { cv = versionOf(GSON.fromJson(r, JsonObject.class)); } catch (RuntimeException ignored) { }
            if (cv < bv) {
                Files.move(def, dir.resolve("apocaliptico.v" + cv + ".json.bak"), StandardCopyOption.REPLACE_EXISTING);
                LootMaster.LOG.info("[LootMaster] tema apocaliptico actualizado a la version {} (el anterior quedo como .bak)", bv);
                write = true;
            }
        }
        if (write) Files.write(def, bundled);
    }

    private static int versionOf(JsonObject o) {
        if (o == null || !o.has("format") || o.get("format").getAsInt() < 2) return 0;
        return o.has("version") ? o.get("version").getAsInt() : 1;
    }

    private static Block[] blocks(JsonElement e, String what) {
        List<Block> l = new ArrayList<>();
        if (e == null) return null;
        List<String> ids = new ArrayList<>();
        if (e.isJsonArray()) for (JsonElement x : e.getAsJsonArray()) ids.add(x.getAsString());
        else ids.add(e.getAsString());
        for (String id : ids) {
            if (id.startsWith("sa:")) id = "sa_decor:" + id.substring(3);
            Block b = Edits.block(id);
            if (b == null || b == Blocks.AIR) { LootMaster.LOG.info("[LootMaster] bloque de tema ausente (se omite) en {}: {}", what, id); continue; }
            if (!Pieces.info(b).ok) { LootMaster.LOG.info("[LootMaster] bloque no soportado por el decorador (se omite) en {}: {}", what, id); continue; }
            l.add(b);
        }
        return l.isEmpty() ? null : l.toArray(new Block[0]);
    }

    private static int i(JsonObject o, String k, int d) { return o.has(k) ? o.get(k).getAsInt() : d; }

    private static double dbl(JsonObject o, String k, double d) { return o.has(k) ? o.get(k).getAsDouble() : d; }

    private static String s(JsonObject o, String k, String d) { return o.has(k) ? o.get(k).getAsString() : d; }

    private static Part part(JsonObject o, String what) {
        Part p = new Part();
        p.bs = blocks(o.get("b"), what);
        if (p.bs == null) return null;
        p.x = i(o, "x", 0);
        p.z = i(o, "z", 0);
        p.y = i(o, "y", 0);
        p.f = s(o, "f", "in");
        p.p = dbl(o, "p", 1);
        p.loot = o.has("loot") ? o.get("loot").getAsString() : null;
        p.once = o.has("once") && o.get("once").getAsBoolean();
        return p;
    }

    private static Comp comp(JsonObject o, String room) {
        Comp c = new Comp();
        c.id = s(o, "id", room + "_" + Integer.toHexString(o.hashCode()));
        String what = room + "/" + c.id;
        c.w = i(o, "w", 1);
        c.max = i(o, "max", 99);
        c.spacing = i(o, "spacing", 0);
        c.clear = i(o, "clear", 1);
        c.minArea = i(o, "minArea", 0);
        c.maxArea = i(o, "maxArea", Integer.MAX_VALUE);
        c.cat = i(o, "cat", 1);
        c.core = o.has("core") && o.get("core").getAsBoolean();
        c.open = o.has("open") && o.get("open").getAsBoolean();
        c.p = dbl(o, "p", 1);
        c.anchor = s(o, "anchor", "wall");
        c.alt = o.has("alt") ? o.get("alt").getAsString() : null;
        c.loot = o.has("loot") ? o.get("loot").getAsString() : null;
        c.align = o.has("align") ? o.get("align").getAsString() : null;
        if (o.has("ground")) {
            c.ground = new HashSet<>();
            for (JsonElement g : o.getAsJsonArray("ground")) {
                switch (g.getAsString()) {
                    case "calle" -> c.ground.add(1);
                    case "acera" -> c.ground.add(2);
                    case "tierra" -> c.ground.add(3);
                    default -> c.ground.add(0);
                }
            }
        }
        if (o.has("row")) {
            for (JsonElement e : o.getAsJsonArray("row")) {
                JsonObject x = e.getAsJsonObject();
                Elem el = new Elem();
                el.bs = blocks(x.get("b"), what);
                if (el.bs == null) continue;
                el.f = s(x, "f", "in");
                el.n = i(x, "n", 1);
                el.gap = i(x, "gap", 0);
                el.p = dbl(x, "p", 1);
                el.loot = x.has("loot") ? x.get("loot").getAsString() : null;
                if (x.has("over")) for (JsonElement ov : x.getAsJsonArray("over")) { Part q = part(ov.getAsJsonObject(), what); if (q != null) el.over.add(q); }
                c.row.add(el);
            }
        }
        if (o.has("parts")) for (JsonElement e : o.getAsJsonArray("parts")) { Part q = part(e.getAsJsonObject(), what); if (q != null) c.parts.add(q); }
        if (o.has("scatter")) {
            for (JsonElement e : o.getAsJsonArray("scatter")) {
                JsonObject x = e.getAsJsonObject();
                Scatter sc = new Scatter();
                sc.bs = blocks(x.get("b"), what);
                if (sc.bs == null) continue;
                sc.min = i(x, "min", 1);
                sc.max = Math.max(sc.min, i(x, "max", sc.min));
                sc.d0 = i(x, "d0", 1);
                sc.d1 = Math.max(sc.d0, i(x, "d1", 2));
                sc.p = dbl(x, "p", 1);
                sc.f = s(x, "f", "rand");
                sc.loot = x.has("loot") ? x.get("loot").getAsString() : null;
                c.scatter.add(sc);
            }
        }
        return c.row.isEmpty() && c.parts.isEmpty() ? null : c;
    }

    private static Theme parse(String id, JsonObject o) {
        if (o == null) return null;
        if (versionOf(o) == 0) {
            LootMaster.LOG.warn("[LootMaster] tema {} es del formato viejo (sin \"format\": 2): se ignora. Borralo o pasalo al formato nuevo.", id);
            return null;
        }
        Theme t = new Theme();
        t.id = id;
        t.name = s(o, "name", id);
        if (o.has("density")) {
            JsonObject d = o.getAsJsonObject("density");
            t.cover = dbl(d, "interior", t.cover);
            t.clutter = dbl(d, "clutter", t.clutter);
            t.exterior = dbl(d, "exterior", t.exterior);
        }
        JsonObject rooms = o.getAsJsonObject("rooms");
        if (rooms == null) return t;
        for (Map.Entry<String, JsonElement> re : rooms.entrySet()) {
            Room r = new Room();
            JsonObject ro = re.getValue().getAsJsonObject();
            if (ro.has("comps")) {
                for (JsonElement e : ro.getAsJsonArray("comps")) {
                    Comp c = comp(e.getAsJsonObject(), re.getKey());
                    if (c == null) continue;
                    r.comps.add(c);
                    r.byId.put(c.id, c);
                }
            }
            if (ro.has("clutter")) {
                for (JsonElement e : ro.getAsJsonArray("clutter")) {
                    JsonObject x = e.getAsJsonObject();
                    Block[] bs = blocks(x.get("b"), re.getKey() + "/clutter");
                    if (bs == null) continue;
                    for (Block b : bs) {
                        Clutter c = new Clutter();
                        c.b = b;
                        c.where = s(x, "where", "free");
                        c.w = i(x, "w", 1);
                        c.max = i(x, "max", 99);
                        c.spacing = i(x, "spacing", 1);
                        r.clutter.add(c);
                    }
                }
            }
            t.rooms.put(re.getKey(), r);
        }
        return t;
    }

    // ------------------------------------------------------------------ lectura de la zona ----
    private static final class Tile { int y; boolean roof, nearDoor, hollow, cut; int walls, head, ground; }

    private static final class Ctx {
        final Map<Long, Tile> tiles = new HashMap<>();
        final List<long[]> marks = new ArrayList<>();
        final List<Long> doors = new ArrayList<>();
        boolean overflow;
        int ya, yb;
    }

    private static final int BED = 1, KITCHEN = 2, BATH = 3, OFFICE = 4, SHOP = 5, HOSP = 6;

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
        if (n.contains("hospital") || n.contains("iv_stand") || n.contains("medical") || n.contains("medicinal")) return HOSP;
        if (n.contains("stove") || n.contains("fridge") || n.contains("refrigerator") || n.contains("microwave") || n.contains("freezer") || n.contains("kitchen")
                || n.equals("furnace") || n.equals("smoker") || n.equals("blast_furnace") || n.equals("cauldron") || n.equals("campfire")) return KITCHEN;
        if (n.contains("bookshelf") || n.contains("lectern") || n.contains("desk") || n.contains("computer") || n.equals("pc") || n.contains("noteboard") || n.contains("office_chair")) return OFFICE;
        if (n.contains("counter") || n.contains("cash_register") || n.contains("store_shelf") || n.contains("marketing_shelf") || n.contains("vending")) return SHOP;
        return 0;
    }

    private static final Set<String> NATURAL = Set.of("grass_block", "dirt", "coarse_dirt", "podzol", "mud", "sand", "red_sand", "gravel", "snow_block", "dirt_path",
            "rooted_dirt", "mycelium", "moss_block", "farmland", "packed_mud", "sandstone", "terracotta");

    private static BlockState at(ServerLevel lv, Jobs.Job j, BlockPos p) {
        if (!j.chunks.contains(ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4))) return Blocks.STONE.defaultBlockState();
        return lv.getBlockState(p);
    }

    private static boolean solid(BlockState s) { return s.blocksMotion(); }

    private static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};   // N, E, S, W (bit 0..3)

    private static int bit(Direction d) {
        return switch (d) { case NORTH -> 1; case EAST -> 2; case SOUTH -> 4; case WEST -> 8; default -> 0; };
    }

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
                    int head = 1;
                    for (; head < 4; head++) { m3.set(x, y + head, z); if (!ch.getBlockState(m3).isAir()) break; }
                    if (head < 2) continue;
                    if (cx.tiles.size() >= MAX_TILES) { cx.overflow = true; return; }
                    Tile t = new Tile();
                    t.y = y;
                    t.head = head;
                    for (int h = head; h <= 12 && y + h < lv.getMaxBuildHeight(); h++) {
                        m3.set(x, y + h, z);
                        BlockState u = ch.getBlockState(m3);
                        if (solid(u) && !(u.getBlock() instanceof LeavesBlock)) { t.roof = true; break; }
                    }
                    if (!t.roof) {
                        // un "suelo" con aire debajo es un techo, balcón o puente: ahí no van autos ni basura
                        for (int k = 2; k <= 6 && y - k > lv.getMinBuildHeight(); k++) {
                            m3.set(x, y - k, z);
                            if (ch.getBlockState(m3).isAir()) { t.hollow = true; break; }
                        }
                    }
                    ResourceLocation g = ForgeRegistries.BLOCKS.getKey(bs.getBlock());
                    String gp = g == null ? "" : g.getPath();
                    t.ground = gp.contains("asphalt") || gp.contains("road") ? 1 : gp.contains("sidewalk") || gp.contains("paver") ? 2 : NATURAL.contains(gp) ? 3 : 0;
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

    // ------------------------------------------------------------------ planificación ----
    public static String startPlan(MinecraftServer s, ServerPlayer pl, ServerLevel lv, BlockPos a, BlockPos b, State.Data d) {
        Theme theme = THEMES.get(d.dTheme);
        if (theme == null) return "Elige un tema (config/lootmaster/decor).";
        int seed = d.dSeed;
        double factor = Math.max(0, Math.min(100, d.dDensity)) / 50.0;
        boolean interior = d.dInterior, exterior = d.dExterior;
        Ctx cx = new Ctx();
        // altura: desde 3 bloques bajo el punto más bajo hasta 24 sobre el más alto (así no se decoran cuevas ni sótanos lejanos)
        int ya = Math.max(lv.getMinBuildHeight() + 1, Math.min(a.getY(), b.getY()) - 3);
        int yb = Math.min(lv.getMaxBuildHeight() - 2, Math.max(a.getY(), b.getY()) + 24);
        cx.ya = ya;
        cx.yb = yb;
        Plan plan = new Plan();
        plan.dim = lv.dimension().location().toString();
        plan.theme = theme.id;
        plan.seed = seed;
        Jobs.Job j = Jobs.create("planificar decoración", (job, level, ch, pos) -> { if (!cx.overflow) scanChunk(job, cx, level, ch); }, () -> {
            if (cx.overflow) {
                LootMaster.LOG.warn("[LootMaster] decoracion: zona demasiado grande");
                Jobs.current().inc2("zona_demasiado_grande");
                Net.toClient(pl, "msg", "La zona tiene demasiado espacio libre para analizarla de una vez. Divídela en partes más chicas.");
                return;
            }
            // el armado del plan es solo cálculo sobre lo ya leído: se hace en otro hilo para no frenar el servidor
            UUID id = pl.getUUID();
            if (!BUSY.add(id)) return;
            Net.toClient(pl, "msg", "Zona leída (" + cx.tiles.size() + " suelos). Armando la decoración…");
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                String err = null;
                try {
                    new Gen(cx, theme, seed, factor, plan).run(interior, exterior);
                } catch (Throwable e) {
                    err = e.toString();
                    LootMaster.LOG.warn("[LootMaster] fallo armando la decoracion", e);
                }
                final String ferr = err;
                s.execute(() -> {
                    BUSY.remove(id);
                    if (ferr != null) { Net.toClient(pl, "msg", "Error armando la decoración: " + ferr); return; }
                    PLANS.put(id, plan);
                    Ops.sendPlan(pl, plan);
                    Net.toClient(pl, "msg", plan.list.isEmpty() ? "No encontré dónde poner decoración en esa zona." : "Plan listo: " + plan.summary + ". Revisa las cajas en el mundo y pulsa APLICAR.");
                    Net.toClient(pl, "state", Ops.state(s, pl).toString());
                });
            }, net.minecraft.Util.backgroundExecutor());
        });
        j.setBox(Math.min(a.getX(), b.getX()), Math.max(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()), Math.max(a.getZ(), b.getZ()), ya, yb);
        int n = Jobs.boxUnits(s, lv, j, j.x1(), j.x2(), j.z1(), j.z2());
        if (n > 4000) return "La zona es demasiado grande para decorar de una vez (" + n + " chunks, máximo 4000). Divídela.";
        return Jobs.begin(j);
    }

    // ------------------------------------------------------------------ generador ----
    /** Sistema de coordenadas de una composición: R apunta de la pared hacia el cuarto, A corre a lo largo de la pared. */
    private static final class Frame {
        final Direction R, A;

        Frame(Direction r, boolean mirror) {
            R = r;
            A = mirror ? r.getCounterClockWise() : r.getClockWise();
        }

        BlockPos at(BlockPos t, int a, int r, int y) {
            return t.offset(A.getStepX() * a + R.getStepX() * r, y, A.getStepZ() * a + R.getStepZ() * r);
        }

        Direction face(String f, Random rnd) {
            return switch (f) {
                case "out" -> R.getOpposite();
                case "r" -> A;
                case "l" -> A.getOpposite();
                case "rand" -> HORIZ[rnd.nextInt(4)];
                default -> R;
            };
        }
    }

    /** Un bloque ya ubicado en coordenadas locales de la composición. */
    private static final class LP {
        Block b;
        Pieces.Info info;
        int a, r, y;
        Direction wf;
        String loot;
        double p = 1;
        final List<int[]> cells = new ArrayList<>();   // {a, r, y} locales
    }

    private static final class Laid {
        final List<LP> lps = new ArrayList<>();
        int lenA = 1, depR = 1;
    }

    private static long ck(int a, int r, int y) { return ((long) (a + 512) << 40) | ((long) (r + 512) << 20) | (y + 512); }

    private static final class Gen {
        final Ctx cx;
        final Theme th;
        final Plan plan;
        final int seed;
        final double factor;
        final Set<Long> furn = new HashSet<>();      // celdas de piso con mueble sólido
        final Set<Long> occ = new HashSet<>();       // cualquier celda ocupada (incluye altura)
        final Set<Long> soft = new HashSet<>();      // espacio libre delante de los muebles
        final Map<String, List<int[]>> spots = new HashMap<>();
        final Map<String, Integer> total = new LinkedHashMap<>();
        int group;

        Gen(Ctx cx, Theme th, int seed, double factor, Plan plan) {
            this.cx = cx; this.th = th; this.seed = seed; this.factor = factor; this.plan = plan;
        }

        void count(String k) { total.merge(k, 1, Integer::sum); }

        void run(boolean interior, boolean exterior) {
            // cerca de puertas
            for (long dp : cx.doors) {
                BlockPos d = BlockPos.of(dp);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 0; dy++) {
                    Tile t = cx.tiles.get(new BlockPos(d.getX() + dx, d.getY() + dy, d.getZ() + dz).asLong());
                    if (t != null) t.nearDoor = true;
                }
            }
            // pasos angostos (puertas sin hoja, arcos, pasillos de un bloque): separan las habitaciones y se dejan libres
            for (Map.Entry<Long, Tile> e : cx.tiles.entrySet()) {
                Tile t = e.getValue();
                if (!t.roof) continue;
                boolean pinch = ((t.walls & 2) != 0 && (t.walls & 8) != 0) || ((t.walls & 1) != 0 && (t.walls & 4) != 0);
                if (!pinch) continue;
                t.cut = true;
            }
            List<Long> cutKeys = new ArrayList<>();
            for (Map.Entry<Long, Tile> e : cx.tiles.entrySet()) if (e.getValue().cut) cutKeys.add(e.getKey());
            for (long ck0 : cutKeys) {
                BlockPos c0 = BlockPos.of(ck0);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    Tile t = cx.tiles.get(c0.offset(dx, 0, dz).asLong());
                    if (t != null) t.nearDoor = true;
                }
            }
            // votos de tipo de habitacion
            Map<Long, int[]> votes = new HashMap<>();
            for (long[] m : cx.marks) {
                BlockPos p = BlockPos.of(m[0]);
                for (int[] dd : DIRS) {
                    long k = new BlockPos(p.getX() + dd[0], p.getY(), p.getZ() + dd[1]).asLong();
                    if (cx.tiles.containsKey(k)) votes.computeIfAbsent(k, q -> new int[8])[(int) m[1]]++;
                }
            }
            Set<Long> seen = new HashSet<>();
            List<List<Long>> rooms = new ArrayList<>();
            List<int[]> roomVotes = new ArrayList<>();
            List<Long> outside = new ArrayList<>();
            List<Long> keys = new ArrayList<>(cx.tiles.keySet());
            Collections.sort(keys);
            for (long k0 : keys) {
                Tile t = cx.tiles.get(k0);
                if (!t.roof) { if (!t.hollow) outside.add(k0); continue; }
                if (t.cut || !seen.add(k0)) continue;
                List<Long> tiles = new ArrayList<>();
                int[] rv = new int[8];
                ArrayDeque<Long> q = new ArrayDeque<>();
                q.add(k0);
                while (!q.isEmpty()) {
                    long k = q.poll();
                    tiles.add(k);
                    int[] v = votes.get(k);
                    if (v != null) for (int i = 1; i < 8; i++) rv[i] += v[i];
                    BlockPos p = BlockPos.of(k);
                    for (int[] dd : DIRS) {
                        long nk = new BlockPos(p.getX() + dd[0], p.getY(), p.getZ() + dd[1]).asLong();
                        Tile nt = cx.tiles.get(nk);
                        if (nt != null && nt.roof && !nt.cut && seen.add(nk)) q.add(nk);
                    }
                }
                rooms.add(tiles);
                roomVotes.add(rv);
            }
            int nRooms = rooms.size();
            if (interior) {
                for (int ri = 0; ri < nRooms; ri++) {
                    List<Long> tiles = rooms.get(ri);
                    if (tiles.size() < 4) continue;
                    Random rnd = new Random(seed * 31L + tiles.get(0) * 7919L);
                    String type = roomType(tiles, roomVotes.get(ri), rnd);
                    Room room = th.rooms.get(type);
                    if (room == null || room.comps.isEmpty()) { type = tiles.size() >= 40 ? "sala" : "cuarto"; room = th.rooms.get(type); }
                    if (room == null) continue;
                    count("habitacion_" + type);
                    fillRoom(tiles, room, rnd);
                }
            }
            if (exterior) fillOutside(outside);
            int bodies = 0, cars = 0, furniture = 0;
            for (Piece p : plan.list) {
                Pieces.Info in = Pieces.info(p.block());
                if (in.path.equals("lying_corpse")) bodies++;
                else if (in.kind == Pieces.Kind.VEHICLE) cars++;
                else if (!in.decal) furniture++;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(nRooms).append(" habitaciones, ").append(plan.list.size()).append(" piezas (").append(furniture).append(" muebles, ")
                    .append(bodies).append(" cuerpos, ").append(cars).append(" vehículos)");
            plan.summary = sb.toString();
        }

        // ---- tipo de habitacion
        String roomType(List<Long> tiles, int[] rv, Random rnd) {
            int area = tiles.size();
            int open = 0, sides = 0;
            for (long k : tiles) {
                Tile t = cx.tiles.get(k);
                BlockPos p = BlockPos.of(k);
                for (int d = 0; d < 4; d++) {
                    if ((t.walls & (1 << d)) != 0) continue;
                    sides++;
                    if (!cx.tiles.containsKey(new BlockPos(p.getX() + DIRS[d][0], p.getY(), p.getZ() + DIRS[d][1]).asLong())) open++;
                }
            }
            if (sides > 0 && open > 0.35 * (sides + 4)) return "porche";
            int best = 0, bi = 0;
            for (int i = 1; i < 8; i++) if (rv[i] > best) { best = rv[i]; bi = i; }
            if (best > 0) return switch (bi) { case BED -> "dormitorio"; case KITCHEN -> "cocina"; case BATH -> "bano"; case OFFICE -> "oficina"; case HOSP -> "hospital"; default -> "comercio"; };
            double r = rnd.nextDouble();
            if (area < 6) return "cuarto";
            if (area <= 12) return r < 0.30 ? "bano" : r < 0.65 ? "cuarto" : "dormitorio";
            if (area <= 30) return r < 0.34 ? "dormitorio" : r < 0.56 ? "cocina" : r < 0.78 ? "sala" : r < 0.90 ? "oficina" : "cuarto";
            return r < 0.55 ? "sala" : r < 0.70 ? "oficina" : r < 0.82 ? "cocina" : r < 0.92 ? "dormitorio" : "comercio";
        }

        // ---- amueblar una habitacion
        void fillRoom(List<Long> tiles, Room room, Random rnd) {
            int area = tiles.size();
            Set<Long> keys = new HashSet<>(tiles);
            List<Long> order = new ArrayList<>(tiles);
            Collections.shuffle(order, rnd);
            double budget = area * th.cover * factor;
            double used = 0;
            Map<String, Integer> here = new HashMap<>();
            if (factor > 0) {
                for (Comp c : room.comps) {
                    if (!c.core || area < c.minArea || area > c.maxArea) continue;
                    used += tryChain(c, room, order, keys, rnd, here, area <= 900, 160);
                }
                int fails = 0;
                while (used < budget && fails < 8 && plan.list.size() < MAX_PLAN) {
                    Comp c = pick(room.comps, here, rnd, area);
                    if (c == null) break;
                    int got = tryChain(c, room, order, keys, rnd, here, area <= 900, 160);
                    if (got == 0) fails++; else used += got;
                }
            }
            clutter(order, keys, room, rnd, th.clutter * factor, 1);
        }

        Comp pick(List<Comp> comps, Map<String, Integer> used, Random rnd, int area) {
            int sum = 0;
            for (Comp c : comps) if (!c.core && c.w > 0 && area >= c.minArea && area <= c.maxArea && used.getOrDefault(c.id, 0) < c.max) sum += c.w;
            if (sum <= 0) return null;
            int r = rnd.nextInt(sum);
            for (Comp c : comps) {
                if (c.core || c.w <= 0 || area < c.minArea || area > c.maxArea || used.getOrDefault(c.id, 0) >= c.max) continue;
                r -= c.w;
                if (r < 0) return c;
            }
            return null;
        }

        /** Intenta la composición y, si no cabe, la alternativa que indique. @return celdas de piso que ocupó (0 si no cupo) */
        int tryChain(Comp c, Room room, List<Long> order, Set<Long> keys, Random rnd, Map<String, Integer> here, boolean conn, int sample) {
            if (c.p < 1 && rnd.nextDouble() > c.p) return 0;
            Comp cur = c;
            int guard = 0;
            while (cur != null && guard++ < 6) {
                if (here.getOrDefault(cur.id, 0) < cur.max) {
                    int n = tryPlace(cur, order, keys, rnd, conn, sample);
                    if (n > 0) { here.merge(cur.id, 1, Integer::sum); count("pone_" + cur.id); return n; }
                }
                cur = cur.alt == null ? null : room.byId.get(cur.alt);
            }
            return 0;
        }

        // ---- afuera
        void fillOutside(List<Long> outside) {
            Map<String, List<Long>> zones = new LinkedHashMap<>();
            for (long k : outside) {
                Tile t = cx.tiles.get(k);
                zones.computeIfAbsent(t.ground == 1 ? "calle" : t.ground == 2 ? "acera" : "patio", q -> new ArrayList<>()).add(k);
            }
            Map<String, Integer> glob = new HashMap<>();
            for (Map.Entry<String, List<Long>> ze : zones.entrySet()) {
                Room room = th.rooms.get(ze.getKey());
                if (room == null || room.comps.isEmpty()) continue;
                List<Long> tiles = ze.getValue();
                Random rnd = new Random(seed * 13L + ze.getKey().hashCode());
                Collections.shuffle(tiles, rnd);
                Set<Long> keys = new HashSet<>(tiles);
                int n = (int) Math.min(6000, Math.round(tiles.size() * th.exterior * factor));
                int fails = 0;
                for (int i = 0; i < n && plan.list.size() < MAX_PLAN; i++) {
                    Comp c = pick(room.comps, glob, rnd, Integer.MAX_VALUE - 1);
                    if (c == null) break;
                    // se parte de un lugar distinto cada vez
                    List<Long> shuffled = tiles;
                    int got = tryChain(c, room, shuffled, keys, rnd, glob, false, 40);
                    if (got == 0) { if (++fails > 400) break; }
                    Collections.rotate(tiles, 1 + rnd.nextInt(Math.max(1, tiles.size() / 3)));
                }
                clutter(tiles, keys, room, rnd, th.clutter * factor * 0.12, 4);
            }
        }

        // ---- armado de una composicion
        Block choose(Block[] bs, Random rnd) { return bs[rnd.nextInt(bs.length)]; }

        LP mk(Part src, Block b, int x, int z, int y, Direction wf, Frame fr, String loot) {
            LP lp = new LP();
            lp.b = b;
            lp.info = Pieces.info(b);
            lp.wf = wf;
            lp.y = y;
            lp.p = src == null ? 1 : src.p;
            lp.loot = loot != null ? loot : src == null ? null : src.loot;
            List<int[]> cs = Pieces.cells(lp.info, wf);
            int mnA = Integer.MAX_VALUE, mnR = Integer.MAX_VALUE;
            for (int[] c : cs) {
                mnA = Math.min(mnA, c[0] * fr.A.getStepX() + c[2] * fr.A.getStepZ());
                mnR = Math.min(mnR, c[0] * fr.R.getStepX() + c[2] * fr.R.getStepZ());
            }
            lp.a = x - mnA;
            lp.r = z - mnR;
            for (int[] c : cs) {
                int la = c[0] * fr.A.getStepX() + c[2] * fr.A.getStepZ();
                int lr = c[0] * fr.R.getStepX() + c[2] * fr.R.getStepZ();
                lp.cells.add(new int[]{x - mnA + la, z - mnR + lr, y + c[1]});
            }
            return lp;
        }

        Laid layout(Comp c, Frame fr, long seedL) {
            Random rnd = new Random(seedL);
            Laid L = new Laid();
            int cursor = 0;
            for (Elem e : c.row) {
                for (int k = 0; k < e.n; k++) {
                    if (e.p < 1 && rnd.nextDouble() > e.p) continue;
                    Block b = choose(e.bs, rnd);
                    LP lp = mk(null, b, cursor, 0, 0, fr.face(e.f, rnd), fr, e.loot);
                    L.lps.add(lp);
                    int x0 = cursor, maxA = x0;
                    for (int[] cell : lp.cells) maxA = Math.max(maxA, cell[0]);
                    for (Part o : e.over) {
                        if (o.once) {
                            // una sola vez, en la columna x del elemento (0 = la primera)
                            L.lps.add(mk(o, choose(o.bs, rnd), x0 + o.x, o.z, o.y, fr.face(o.f, rnd), fr, null));
                            continue;
                        }
                        for (int ax = x0; ax <= maxA; ax++) L.lps.add(mk(o, choose(o.bs, rnd), ax, o.z, o.y, fr.face(o.f, rnd), fr, null));
                    }
                    cursor = maxA + 1 + e.gap;
                }
            }
            for (Part p : c.parts) L.lps.add(mk(p, choose(p.bs, rnd), p.x, p.z, p.y, fr.face(p.f, rnd), fr, null));
            int la = 0, lr = 0;
            for (LP lp : L.lps) for (int[] cell : lp.cells) { la = Math.max(la, cell[0]); lr = Math.max(lr, cell[1]); }
            L.lenA = la + 1;
            L.depR = lr + 1;
            return L;
        }

        /** Deja solo lo que va a existir (por probabilidad) y quita lo que quedaría flotando: nada va sobre aire ni sobre un mueble que no está. */
        List<LP> resolve(Laid L, long seedL) {
            Random rnd = new Random(seedL ^ 0x5DEECE66DL);
            List<LP> keep = new ArrayList<>();
            for (LP lp : L.lps) if (lp.p >= 1 || rnd.nextDouble() <= lp.p) keep.add(lp);
            boolean changed = true;
            while (changed) {
                changed = false;
                Set<Long> sup = new HashSet<>();
                for (LP lp : keep) if (!lp.info.decal && !lp.info.hang) for (int[] c : lp.cells) sup.add(ck(c[0], c[1], c[2]));
                for (int idx = 0; idx < keep.size(); idx++) {
                    LP lp = keep.get(idx);
                    boolean bad = false;
                    if (lp.info.top && lp.y == 0) bad = true;
                    else if (!lp.info.hang) {
                        int minY = Integer.MAX_VALUE;
                        for (int[] c : lp.cells) minY = Math.min(minY, c[2]);
                        if (minY > 0) for (int[] c : lp.cells) if (c[2] == minY && !sup.contains(ck(c[0], c[1], c[2] - 1))) { bad = true; break; }
                    }
                    if (bad) { keep.remove(idx); changed = true; break; }
                }
            }
            return keep;
        }

        boolean fits(Comp c, List<LP> lps, Laid L, Frame fr, BlockPos t, Set<Long> keys) {
            for (LP lp : lps) {
                for (int[] cell : lp.cells) {
                    BlockPos fl = fr.at(t, cell[0], cell[1], 0);
                    long fk = fl.asLong();
                    Tile tl = cx.tiles.get(fk);
                    if (tl == null || (keys != null && !keys.contains(fk))) return false;
                    int y = cell[2];
                    if (tl.head <= y) return false;
                    if (occ.contains(fl.above(y).asLong())) return false;
                    if (y == 0) {
                        if (!lp.info.decal) { if (furn.contains(fk) || soft.contains(fk) || tl.nearDoor) return false; }
                        else if (furn.contains(fk)) return false;
                        if (c.ground != null && !c.ground.contains(tl.ground)) return false;
                        if (c.open && tl.walls != 0) return false;
                    }
                    if (lp.info.hang && (cell[1] != 0 || (tl.walls & bit(fr.R.getOpposite())) == 0)) return false;
                }
            }
            if (!c.anchor.equals("free")) {
                int wb = bit(fr.R.getOpposite());
                for (int a = 0; a < L.lenA; a++) {
                    Tile tl = cx.tiles.get(fr.at(t, a, 0, 0).asLong());
                    if (tl == null || (tl.walls & wb) == 0) return false;
                }
                if (c.anchor.equals("corner")) {
                    Tile t0 = cx.tiles.get(t.asLong());
                    if (t0 == null || (t0.walls & bit(fr.A.getOpposite())) == 0) return false;
                }
                for (int a = 0; a < L.lenA; a++)
                    for (int k = 0; k < c.clear; k++) {
                        long fk = fr.at(t, a, L.depR + k, 0).asLong();
                        if (!cx.tiles.containsKey(fk) || furn.contains(fk) || (keys != null && !keys.contains(fk))) return false;
                    }
            }
            return true;
        }

        boolean spaced(Comp c, BlockPos t) {
            if (c.spacing <= 0) return true;
            List<int[]> l = spots.get(c.id);
            if (l == null) return true;
            long lim = (long) c.spacing * c.spacing;
            for (int[] s : l) {
                long dx = s[0] - t.getX(), dz = s[1] - t.getZ();
                if (dx * dx + dz * dz < lim) return false;
            }
            return true;
        }

        /** ¿Sigue pudiéndose caminar por todo el cuarto si se ocupan estas celdas? No se admite partir el cuarto ni dejar rincones aislados. */
        boolean connected(Set<Long> keys, Set<Long> add) {
            int[] before = components(keys, Collections.emptySet());
            int[] after = components(keys, add);
            if (after[1] == 0) return false;
            return after[0] <= Math.max(1, before[0]) && (after[1] - after[2]) <= (before[1] - before[2]);
        }

        /** @return {cantidad de zonas libres, celdas libres, celdas de la zona más grande} */
        int[] components(Set<Long> keys, Set<Long> add) {
            Set<Long> seen = new HashSet<>();
            int comps = 0, total = 0, largest = 0;
            for (long k : keys) {
                if (furn.contains(k) || add.contains(k) || seen.contains(k)) continue;
                comps++;
                int size = 0;
                ArrayDeque<Long> q = new ArrayDeque<>();
                q.add(k);
                seen.add(k);
                while (!q.isEmpty()) {
                    BlockPos p = BlockPos.of(q.poll());
                    size++;
                    for (int[] dd : DIRS) {
                        long nk = p.offset(dd[0], 0, dd[1]).asLong();
                        if (keys.contains(nk) && !furn.contains(nk) && !add.contains(nk) && seen.add(nk)) q.add(nk);
                    }
                }
                total += size;
                largest = Math.max(largest, size);
            }
            return new int[]{comps, total, largest};
        }

        /** Hacia dónde corre la calle en este punto: 0 = eje X, 1 = eje Z, -1 = no se sabe (cruce o lugar chico). */
        int roadAxis(BlockPos t) {
            int ex = 1, ez = 1;
            for (int s2 = -1; s2 <= 1; s2 += 2) {
                for (int k = 1; k <= 8; k++) { Tile q = cx.tiles.get(t.offset(s2 * k, 0, 0).asLong()); if (q == null || q.ground != 1) break; ex++; }
                for (int k = 1; k <= 8; k++) { Tile q = cx.tiles.get(t.offset(0, 0, s2 * k).asLong()); if (q == null || q.ground != 1) break; ez++; }
            }
            if (Math.max(ex, ez) < 6 || Math.abs(ex - ez) < 3) return -1;
            return ex > ez ? 0 : 1;
        }

        boolean alignOk(Comp c, Frame fr, Laid L, BlockPos t) {
            if (c.align == null) return true;
            int ax = roadAxis(t);
            if (ax < 0) return true;
            Direction longDir = L.lenA >= L.depR ? fr.A : fr.R;
            return (longDir.getAxis() == Direction.Axis.X ? 0 : 1) == ax;
        }

        /** @return celdas de piso ocupadas, 0 si no se pudo */
        int tryPlace(Comp c, List<Long> order, Set<Long> keys, Random rnd, boolean checkConn, int sample) {
            boolean free = c.anchor.equals("free");
            List<Frame> frames = new ArrayList<>();
            for (Direction r : HORIZ) { frames.add(new Frame(r, false)); frames.add(new Frame(r, true)); }
            Collections.shuffle(frames, rnd);
            long seedL = rnd.nextLong();
            for (Frame fr : frames) {
                Laid L = layout(c, fr, seedL);
                List<LP> lps = resolve(L, seedL);
                if (lps.isEmpty()) continue;
                int wb = bit(fr.R.getOpposite());
                int tested = 0;
                for (long k : order) {
                    Tile tl = cx.tiles.get(k);
                    if (!free && (tl.walls & wb) == 0) continue;
                    if (++tested > sample) break;
                    BlockPos t = BlockPos.of(k);
                    if (!spaced(c, t)) continue;
                    if (!fits(c, lps, L, fr, t, keys)) continue;
                    if (!alignOk(c, fr, L, t)) continue;
                    Set<Long> add = new HashSet<>();
                    for (LP lp : lps) if (!lp.info.decal) for (int[] cell : lp.cells) if (cell[2] == 0) add.add(fr.at(t, cell[0], cell[1], 0).asLong());
                    if (checkConn && !connected(keys, add)) continue;
                    return commit(c, lps, L, fr, t, rnd, keys, add);
                }
            }
            return 0;
        }

        int commit(Comp c, List<LP> lps, Laid L, Frame fr, BlockPos t, Random rnd, Set<Long> keys, Set<Long> add) {
            group++;
            for (LP lp : lps) {
                BlockPos origin = fr.at(t, lp.a, lp.r, lp.y);
                plan.list.add(new Piece(lp.b, origin, lp.wf, lp.loot != null ? lp.loot : c.loot, c.cat, group));
                for (int[] cell : lp.cells) {
                    BlockPos fl = fr.at(t, cell[0], cell[1], 0);
                    occ.add(fl.above(cell[2]).asLong());
                    if (cell[2] == 0 && !lp.info.decal) furn.add(fl.asLong());
                }
            }
            if (!c.anchor.equals("free")) {
                for (int a = 0; a < L.lenA; a++)
                    for (int k = 0; k < c.clear; k++) soft.add(fr.at(t, a, L.depR + k, 0).asLong());
            }
            spots.computeIfAbsent(c.id, q -> new ArrayList<>()).add(new int[]{t.getX(), t.getZ()});
            for (Scatter sc : c.scatter) scatter(c, sc, L, fr, t, rnd, keys);
            return Math.max(1, add.size());
        }

        void scatter(Comp c, Scatter sc, Laid L, Frame fr, BlockPos t, Random rnd, Set<Long> keys) {
            int n = sc.min + (sc.max > sc.min ? rnd.nextInt(sc.max - sc.min + 1) : 0);
            for (int i = 0; i < n; i++) {
                if (sc.p < 1 && rnd.nextDouble() > sc.p) continue;
                Block b = choose(sc.bs, rnd);
                Pieces.Info in = Pieces.info(b);
                for (int attempt = 0; attempt < 14; attempt++) {
                    int a = rnd.nextInt(L.lenA + 2 * sc.d1) - sc.d1, r = rnd.nextInt(L.depR + 2 * sc.d1) - sc.d1;
                    int da = a < 0 ? -a : Math.max(0, a - (L.lenA - 1)), dr = r < 0 ? -r : Math.max(0, r - (L.depR - 1));
                    int dist = Math.max(da, dr);
                    if (dist < sc.d0 || dist > sc.d1) continue;
                    BlockPos fl = fr.at(t, a, r, 0);
                    long fk = fl.asLong();
                    Tile tl = cx.tiles.get(fk);
                    if (tl == null || (keys != null && !keys.contains(fk)) || tl.head < 2 || tl.nearDoor) continue;
                    if (c.ground != null && !c.ground.contains(tl.ground)) continue;
                    if (occ.contains(fk) || furn.contains(fk) || (!in.decal && soft.contains(fk))) continue;
                    if (!in.decal && !in.hang && ringBlocks(fl)) continue;
                    if (in.hang || in.top) continue;
                    Direction wf = fr.face(sc.f, rnd);
                    List<int[]> cs = Pieces.cells(in, wf);
                    boolean ok = true;
                    for (int[] d : cs) {
                        long k2 = fl.offset(d[0], d[1], d[2]).asLong();
                        if ((d[0] != 0 || d[2] != 0) && (occ.contains(k2) || !cx.tiles.containsKey(fl.offset(d[0], 0, d[2]).asLong()))) { ok = false; break; }
                    }
                    if (!ok) continue;
                    plan.list.add(new Piece(b, fl, wf, sc.loot != null ? sc.loot : c.loot, c.cat, group));
                    for (int[] d : cs) {
                        BlockPos p2 = fl.offset(d[0], d[1], d[2]);
                        occ.add(p2.asLong());
                        if (d[1] == 0 && !in.decal) furn.add(p2.asLong());
                    }
                    count("suelta_" + in.path);
                    break;
                }
            }
        }

        /** Anillo de 8 vecinos: si al tapar este piso el espacio libre alrededor se parte en dos, no se pone (no cierra pasillos). */
        boolean ringBlocks(BlockPos p) {
            int[][] ring = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
            boolean[] free = new boolean[8];
            int nfree = 0;
            for (int i = 0; i < 8; i++) {
                long nk = p.offset(ring[i][0], 0, ring[i][1]).asLong();
                free[i] = cx.tiles.containsKey(nk) && !furn.contains(nk);
                if (free[i]) nfree++;
            }
            if (nfree == 0 || nfree == 8) return false;
            int runs = 0;
            for (int i = 0; i < 8; i++) if (free[i] && !free[(i + 7) % 8]) runs++;
            return runs > 1;
        }

        // ---- cosas sueltas (papeles, basura, vidrios) pegadas a muebles, paredes y esquinas
        void clutter(List<Long> order, Set<Long> keys, Room room, Random rnd, double rate, int cat) {
            if (room.clutter.isEmpty() || rate <= 0 || order.isEmpty()) return;
            int n = (int) Math.floor(order.size() * rate + rnd.nextDouble());
            Map<Block, Integer> used = new HashMap<>();
            for (int i = 0; i < n && plan.list.size() < MAX_PLAN; i++) {
                int sum = 0;
                for (Clutter e : room.clutter) if (used.getOrDefault(e.b, 0) < e.max) sum += Math.max(1, e.w);
                if (sum <= 0) break;
                int r = rnd.nextInt(sum);
                Clutter e = null;
                for (Clutter x : room.clutter) {
                    if (used.getOrDefault(x.b, 0) >= x.max) continue;
                    r -= Math.max(1, x.w);
                    if (r < 0) { e = x; break; }
                }
                if (e == null) break;
                Pieces.Info in = Pieces.info(e.b);
                if (in.top) { used.merge(e.b, 1000, Integer::sum); continue; }   // los objetos de mesa nunca van al piso
                for (int attempt = 0; attempt < 12; attempt++) {
                    long k = order.get(rnd.nextInt(order.size()));
                    Tile tl = cx.tiles.get(k);
                    if (tl == null || occ.contains(k) || furn.contains(k) || (keys != null && !keys.contains(k))) continue;
                    if (tl.nearDoor && !in.decal) continue;
                    BlockPos p = BlockPos.of(k);
                    int walls = Integer.bitCount(tl.walls);
                    boolean near = false;
                    for (int[] dd : DIRS) if (furn.contains(p.offset(dd[0], 0, dd[1]).asLong())) { near = true; break; }
                    switch (e.where) {
                        case "wall" -> { if (walls == 0) continue; }
                        case "corner" -> { if (walls < 2) continue; }
                        case "near" -> { if (!near) continue; }
                        default -> { }
                    }
                    if (!spacedClutter(e, p)) continue;
                    if (in.hang) { if (walls == 0) continue; }
                    else if (!in.decal && ringBlocks(p)) continue;
                    Direction wf;
                    if (tl.walls != 0 && !e.where.equals("free")) {
                        List<Direction> ds = new ArrayList<>();
                        for (int d = 0; d < 4; d++) if ((tl.walls & (1 << d)) != 0) ds.add(HORIZ[d].getOpposite());
                        wf = ds.get(rnd.nextInt(ds.size()));
                    } else if (in.hang && tl.walls != 0) {
                        List<Direction> ds = new ArrayList<>();
                        for (int d = 0; d < 4; d++) if ((tl.walls & (1 << d)) != 0) ds.add(HORIZ[d].getOpposite());
                        wf = ds.get(rnd.nextInt(ds.size()));
                    } else wf = HORIZ[rnd.nextInt(4)];
                    List<int[]> cs = Pieces.cells(in, wf);
                    boolean ok = true;
                    for (int[] d : cs) {
                        if (d[0] == 0 && d[2] == 0) continue;
                        BlockPos p2 = p.offset(d[0], 0, d[2]);
                        if (occ.contains(p2.offset(0, d[1], 0).asLong()) || !cx.tiles.containsKey(p2.asLong())) { ok = false; break; }
                    }
                    if (!ok) continue;
                    plan.list.add(new Piece(e.b, p, wf, null, cat, ++group));
                    for (int[] d : cs) {
                        BlockPos p2 = p.offset(d[0], d[1], d[2]);
                        occ.add(p2.asLong());
                        if (d[1] == 0 && !in.decal) furn.add(p2.asLong());
                    }
                    used.merge(e.b, 1, Integer::sum);
                    clutterSpots.computeIfAbsent(e.b, q -> new ArrayList<>()).add(new int[]{p.getX(), p.getZ()});
                    count("suelto_" + in.path);
                    break;
                }
            }
        }

        final Map<Block, List<int[]>> clutterSpots = new HashMap<>();

        boolean spacedClutter(Clutter e, BlockPos p) {
            if (e.spacing <= 1) return true;
            List<int[]> l = clutterSpots.get(e.b);
            if (l == null) return true;
            long lim = (long) e.spacing * e.spacing;
            for (int[] s : l) {
                long dx = s[0] - p.getX(), dz = s[1] - p.getZ();
                if (dx * dx + dz * dz < lim) return false;
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ aplicar ----
    public static String startApply(MinecraftServer s, ServerPlayer pl) {
        Plan plan = PLANS.get(pl.getUUID());
        if (plan == null || plan.list.isEmpty()) return "No hay un plan. Pulsa PLANIFICAR primero.";
        ServerLevel lv = null;
        for (ServerLevel l : s.getAllLevels()) if (l.dimension().location().toString().equals(plan.dim)) lv = l;
        if (lv == null) return "Dimensión no disponible.";
        State st = State.get(s);
        State.Data d = st.d;
        boolean withLoot = d.dLoot;
        int respawnMin = d.dRespawn;
        Edits.Op op = new Edits.Op();
        op.label = "Decoración «" + plan.theme + "»";
        op.dim = plan.dim;
        // cada grupo (una composición) se coloca entero desde el chunk de su primera pieza
        Map<Integer, List<Piece>> groups = new LinkedHashMap<>();
        for (Piece p : plan.list) groups.computeIfAbsent(p.group(), q -> new ArrayList<>()).add(p);
        Map<Long, List<BlockPos>> by = new LinkedHashMap<>();
        Map<Long, List<List<Piece>>> byPos = new HashMap<>();
        for (List<Piece> g : groups.values()) {
            BlockPos a = g.get(0).pos();
            by.computeIfAbsent(ChunkPos.asLong(a.getX() >> 4, a.getZ() >> 4), q -> new ArrayList<>()).add(a);
            byPos.computeIfAbsent(a.asLong(), q -> new ArrayList<>()).add(g);
        }
        Set<String> profiles = new java.util.TreeSet<>();
        net.minecraft.util.RandomSource rnd = net.minecraft.util.RandomSource.create(plan.seed * 31L + 7);
        Jobs.Job j = Jobs.create("aplicar decoración", (job, level, ch, pos) -> {
            if (pos == null) return;
            for (BlockPos ap : pos) {
                List<List<Piece>> gl = byPos.get(ap.asLong());
                if (gl == null) continue;
                for (List<Piece> g : gl) placeGroup(level, g, op, job, rnd, withLoot, profiles);
            }
        }, () -> {
            Edits.commit(s, op);
            PLANS.remove(pl.getUUID());
            Ops.sendPlan(pl, null);
            if (op.full) Net.toClient(pl, "msg", "Aviso: había tantos bloques que el historial para DESHACER quedó incompleto.");
            if (withLoot && respawnMin > 0 && !profiles.isEmpty()) {
                List<String> on = LootPick.ensureRespawn(d, profiles, respawnMin);
                st.dirty();
                LootMaster.LOG.info("[LootMaster] plantillas usadas por la decoracion: {} · reinicio de {} min activado en: {}", profiles, respawnMin, on);
                Jobs.Job cur = Jobs.current();
                if (cur != null) cur.c.put("plantillas_con_reinicio_nuevo", on.size());
            }
        });
        j.finOnCancel = true;
        for (Map.Entry<Long, List<BlockPos>> e : by.entrySet()) Jobs.unit(j, lv, ChunkPos.getX(e.getKey()), ChunkPos.getZ(e.getKey()), e.getValue());
        return Jobs.begin(j);
    }

    /** ¿Hay pared de verdad detrás de una pieza que cuelga? (se mira al colocar, no solo al planificar) */
    private static boolean wallBehind(ServerLevel lv, BlockPos pos, Direction facing) {
        BlockPos b = pos.relative(facing.getOpposite());
        if (lv.getChunkSource().getChunkNow(b.getX() >> 4, b.getZ() >> 4) == null) return true;   // fuera de lo cargado: se confía en el plan
        BlockState bs = lv.getBlockState(b);
        return bs.blocksMotion() || bs.isFaceSturdy(lv, b, facing);
    }

    private static void placeGroup(ServerLevel lv, List<Piece> g, Edits.Op op, Jobs.Job job, net.minecraft.util.RandomSource rnd, boolean withLoot, Set<String> profiles) {
        List<Piece> sorted = new ArrayList<>(g);
        sorted.sort((a, b) -> Integer.compare(a.pos().getY(), b.pos().getY()));
        for (Piece p : sorted) {
            Pieces.Info in = Pieces.info(p.block());
            if (!in.ok) continue;
            if (!Pieces.free(lv, in, p.pos(), p.facing())) { job.inc2("omitidos_ocupados"); continue; }
            if (in.hang) {
                if (!wallBehind(lv, p.pos(), p.facing())) { job.inc2("omitidos_sin_pared"); continue; }
            } else {
                BlockPos bp = p.pos().below();
                BlockState below = lv.getBlockState(bp);
                if (below.isAir() || below.getCollisionShape(lv, bp).isEmpty()) { job.inc2("omitidos_sin_apoyo"); continue; }
            }
            List<BlockPos> touched = new ArrayList<>();
            Pieces.touched(in, p.pos(), p.facing(), touched);
            for (BlockPos t : touched) Edits.record(op, lv, t);
            if (!Pieces.place(lv, in, p.pos(), p.facing(), rnd)) { job.inc2("fallaron"); continue; }
            job.inc2(in.decal ? "restos" : "colocados");
            if (withLoot && !in.decal) {
                String prof = LootPick.assign(lv, in, p, touched, rnd);
                if (prof != null) { profiles.add(prof); job.inc2("con_plantilla_de_loot"); }
            }
        }
    }

    /** [x,y,z,categoria, ...] para el preview. */
    public static JsonArray preview(Plan plan) {
        JsonArray a = new JsonArray();
        int step = Math.max(1, plan.list.size() / PREVIEW_MAX + (plan.list.size() % PREVIEW_MAX == 0 ? 0 : 1));
        for (int i = 0; i < plan.list.size(); i += step) {
            Piece p = plan.list.get(i);
            a.add(p.pos().getX());
            a.add(p.pos().getY());
            a.add(p.pos().getZ());
            a.add(p.cat());
        }
        return a;
    }
}
