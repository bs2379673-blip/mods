package com.tunombre.lootmaster;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cómo se coloca cada tipo de bloque de decoración: bloques simples, muebles de varias partes de Survivors Arsenal (armarios, heladeras,
 * escritorios, estantes…), vehículos y camas. Calcula la huella (qué celdas ocupa) preguntándole al propio bloque, así no depende de
 * suposiciones sobre hacia dónde crece cada mueble. Se puede consultar desde otro hilo (el plan se genera aparte del hilo del servidor).
 */
public final class Pieces {
    public enum Kind { SIMPLE, MULTI, VEHICLE, BED }

    /** Descripción de un bloque: tipo, huella por orientación y qué papel cumple (cuelga de la pared, va sobre una mesa, es una calcomanía del piso). */
    public static final class Info {
        public final Block block;
        public final String path;
        public Kind kind = Kind.SIMPLE;
        public volatile boolean ok = true;
        public boolean hang, top, decal;
        Class<?> cls;
        Method layout, parts, place, getPart, getController, shouldCreate, variantCount, maxVariant;
        Method partUp;
        int vh = 2;
        IntegerProperty pRight, pUp, pBack, pVariant;
        final Map<Direction, List<int[]>> cache = new ConcurrentHashMap<>();

        Info(Block b) {
            block = b;
            var k = ForgeRegistries.BLOCKS.getKey(b);
            path = k == null ? "?" : k.getPath();
        }
    }

    /** Cuelgan de la pared (necesitan la pared detrás, no el piso). */
    private static final Set<String> HANG = Set.of("smart_tv", "smart_tv_broken", "small_lamp", "small_lamp_flickering", "wall_clock", "mirror", "small_mirror",
            "noteboard", "wall_wooden_drawer", "wall_wooden_drawer_glass", "wall_metal_counter_cabinet", "medicinal_cabinet", "kitchen_shelf", "toilet_paper",
            "air_conditioner", "wall_vent", "electrical_outlet", "light_switch", "wall_metal_rack", "usa_flag", "brazil_flag", "exit_sign", "electrical_box",
            "fuse_box", "grime", "keycard_reader", "umbrella_sign", "danger_sign", "bathroom_sink", "cobweb");
    /** Objetos chicos que van sobre un mueble, nunca en el piso. */
    private static final Set<String> TOP = Set.of("pc", "pc_broken", "old_computer", "broken_old_computer", "tv", "tv_broken", "microwave", "radio", "military_radio",
            "black_telephone", "white_telephone", "black_desk_lamp", "blue_desk_lamp", "medical_props", "cash_register_machine");
    /** Calcomanías y restos que se pueden pisar: no cuentan como mueble. */
    private static final Set<String> DECAL = Set.of("paper_props", "dirty_props", "blood_props", "broken_glass", "grime_carpet", "lying_corpse", "garbage_props",
            "trash_props", "ammo_props", "backpack_loot", "wires", "loose_key", "loose_keycard", "fly_emitter");

    private static final Map<Block, Info> CACHE = new ConcurrentHashMap<>();

    private Pieces() { }

    public static void clearCache() { CACHE.clear(); }

    public static Info info(Block b) {
        Info i = CACHE.get(b);
        if (i == null) {
            i = build(b);
            Info prev = CACHE.putIfAbsent(b, i);
            if (prev != null) i = prev;
        }
        return i;
    }

    private static Method find(Class<?> start, String name, Class<?>... args) {
        for (Class<?> c = start; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, args);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException | RuntimeException ignored) { }
        }
        return null;
    }

    private static Info build(Block b) {
        Info i = new Info(b);
        i.hang = HANG.contains(i.path);
        i.top = TOP.contains(i.path);
        i.decal = DECAL.contains(i.path);
        try {
            for (Property<?> p : b.defaultBlockState().getProperties()) if (p.getValueClass() == DoubleBlockHalf.class) { i.ok = false; return i; }
            if (b instanceof BedBlock) { i.kind = Kind.BED; return i; }
            for (Class<?> c = b.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                String n = c.getSimpleName();
                if (n.equals("DecorMultiblockBlock")) {
                    i.kind = Kind.MULTI;
                    i.cls = c;
                    i.layout = c.getMethod("getLayout");
                    Object lay = i.layout.invoke(b);
                    i.parts = lay.getClass().getMethod("parts");
                    Object[] ps = (Object[]) i.parts.invoke(lay);
                    Class<?> pc = ps[0].getClass();
                    i.partUp = pc.getMethod("upIndex");
                    i.place = c.getMethod("placeMultiblockAt", net.minecraft.world.level.Level.class, BlockPos.class, Direction.class);
                    i.getPart = c.getMethod("getPartPos", BlockPos.class, BlockState.class, pc);
                    return i;
                }
                if (n.equals("SedanWhiteBlock")) {
                    i.kind = Kind.VEHICLE;
                    i.cls = c;
                    i.pRight = prop(b, "right");
                    i.pUp = prop(b, "up");
                    i.pBack = prop(b, "back");
                    i.pVariant = prop(b, "variant");
                    if (i.pRight == null || i.pUp == null || i.pBack == null) { i.ok = false; return i; }
                    i.getController = c.getMethod("getControllerPos", BlockState.class, BlockPos.class);
                    i.shouldCreate = find(b.getClass(), "shouldCreatePart", int.class, int.class, int.class);
                    i.variantCount = find(b.getClass(), "getVariantCount");
                    try { i.vh = (Integer) c.getMethod("getMultiblockHeight").invoke(b); } catch (ReflectiveOperationException ignored) { }
                    return i;
                }
            }
            // bloques con "variante" (basura, papeles, sangre…): se sortea una al colocar
            Method mv = null;
            try { mv = b.getClass().getMethod("getMaxVariant"); } catch (NoSuchMethodException ignored) { }
            i.maxVariant = mv;
            i.pVariant = prop(b, "variant");
        } catch (ReflectiveOperationException | RuntimeException e) {
            LootMaster.LOG.warn("[LootMaster] no se pudo analizar el bloque {}: {}", i.path, e.toString());
            i.ok = false;
        }
        return i;
    }

    private static IntegerProperty prop(Block b, String name) {
        Property<?> p = b.getStateDefinition().getProperty(name);
        return p instanceof IntegerProperty ip ? ip : null;
    }

    private static DirectionProperty facingProp(BlockState s) {
        for (Property<?> p : s.getProperties()) if (p instanceof DirectionProperty dp && p.getName().equals("facing")) return dp;
        return null;
    }

    /** Celdas {dx,dy,dz} que ocupa la pieza respecto de su origen, mirando hacia {@code wf}. */
    public static List<int[]> cells(Info i, Direction wf) {
        List<int[]> c = i.cache.get(wf);
        if (c != null) return c;
        c = new ArrayList<>();
        try {
            switch (i.kind) {
                case MULTI -> {
                    BlockState cs = i.block.defaultBlockState();
                    DirectionProperty fp = facingProp(cs);
                    if (fp != null && fp.getPossibleValues().contains(wf)) cs = cs.setValue(fp, wf);
                    Object lay = i.layout.invoke(i.block);
                    for (Object part : (Object[]) i.parts.invoke(lay)) {
                        BlockPos o = (BlockPos) i.getPart.invoke(i.block, BlockPos.ZERO, cs, part);
                        c.add(new int[]{o.getX(), (Integer) i.partUp.invoke(part), o.getZ()});
                    }
                }
                case VEHICLE -> {
                    BlockState base = i.block.defaultBlockState();
                    DirectionProperty fp = facingProp(base);
                    if (fp != null) base = base.setValue(fp, wf);
                    int maxR = i.pRight.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
                    int maxB = i.pBack.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
                    int maxU = i.pUp.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
                    for (int r = 0; r <= maxR; r++)
                        for (int u = 0; u < i.vh && u <= maxU; u++)
                            for (int b = 0; b <= maxB; b++) {
                                if (i.shouldCreate != null) {
                                    boolean sc;
                                    try { sc = (Boolean) i.shouldCreate.invoke(i.block, r, u, b); } catch (ReflectiveOperationException | RuntimeException e) { sc = true; }
                                    if (!sc) continue;
                                }
                                BlockState ps = base.setValue(i.pRight, r).setValue(i.pUp, u).setValue(i.pBack, b);
                                BlockPos ctl = (BlockPos) i.getController.invoke(i.block, ps, BlockPos.ZERO);
                                c.add(new int[]{-ctl.getX(), u, -ctl.getZ()});
                            }
                    if (c.isEmpty()) c.add(new int[]{0, 0, 0});
                }
                case BED -> {
                    c.add(new int[]{0, 0, 0});
                    c.add(new int[]{wf.getStepX(), 0, wf.getStepZ()});
                }
                default -> c.add(new int[]{0, 0, 0});
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LootMaster.LOG.warn("[LootMaster] huella de {} no disponible: {}", i.path, e.toString());
            c = new ArrayList<>();
            c.add(new int[]{0, 0, 0});
            i.ok = false;
        }
        i.cache.put(wf, c);
        return c;
    }

    /** ¿Están libres todas las celdas? */
    public static boolean free(ServerLevel lv, Info i, BlockPos origin, Direction wf) {
        for (int[] d : cells(i, wf)) {
            BlockPos p = origin.offset(d[0], d[1], d[2]);
            if (p.getY() < lv.getMinBuildHeight() || p.getY() >= lv.getMaxBuildHeight()) return false;
            BlockState s = lv.getBlockState(p);
            if (!s.isAir() && !s.canBeReplaced()) return false;
        }
        return true;
    }

    /** Guarda en {@code out} cada posición que se va a tocar (para poder deshacer). */
    public static void touched(Info i, BlockPos origin, Direction wf, List<BlockPos> out) {
        for (int[] d : cells(i, wf)) out.add(origin.offset(d[0], d[1], d[2]));
    }

    private static int variantBound(Info i) {
        try {
            if (i.variantCount != null) {
                int n = (Integer) i.variantCount.invoke(i.block);
                if (n > 0) return n;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return 1;
    }

    /** Coloca la pieza. @return true si quedó puesta */
    public static boolean place(ServerLevel lv, Info i, BlockPos origin, Direction wf, RandomSource rnd) {
        if (!i.ok) return false;
        try {
            BlockState st = i.block.defaultBlockState();
            DirectionProperty fp = facingProp(st);
            if (fp != null && fp.getPossibleValues().contains(wf)) st = st.setValue(fp, wf);
            switch (i.kind) {
                case MULTI -> {
                    i.place.invoke(i.block, lv, origin, wf);
                    return true;
                }
                case VEHICLE -> {
                    if (i.pVariant != null) {
                        int n = Math.min(variantBound(i), i.pVariant.getPossibleValues().size());
                        st = st.setValue(i.pVariant, rnd.nextInt(Math.max(1, n)));
                    }
                    lv.setBlock(origin, st, 3);
                    i.block.setPlacedBy(lv, origin, st, null, ItemStack.EMPTY);
                    return true;
                }
                case BED -> {
                    if (!st.hasProperty(BedBlock.PART)) return false;
                    BlockPos head = origin.relative(wf);
                    lv.setBlock(origin, st.setValue(BedBlock.PART, BedPart.FOOT), 3);
                    lv.setBlock(head, st.setValue(BedBlock.PART, BedPart.HEAD), 3);
                    return true;
                }
                default -> {
                    if (i.pVariant != null && i.maxVariant != null) {
                        int max = (Integer) i.maxVariant.invoke(i.block);
                        if (max > 0) st = st.setValue(i.pVariant, rnd.nextInt(Math.min(max, 15) + 1));
                    }
                    // mesas, bancos, etc. se conectan con los vecinos
                    BlockState linked = st;
                    for (Direction d : Direction.values()) {
                        BlockPos np = origin.relative(d);
                        linked = linked.updateShape(d, lv.getBlockState(np), lv, origin, np);
                    }
                    if (!linked.isAir()) st = linked;
                    lv.setBlock(origin, st, 3);
                    if (i.path.equals("lying_corpse")) variantCorpse(lv, origin, rnd);
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LootMaster.LOG.warn("[LootMaster] no se pudo colocar {}: {}", i.path, e.toString());
            return false;
        }
    }

    /** Los cuerpos tienen varias versiones (ropa, sangre): se elige una al azar con el mismo método que usa SA al cambiar de variante. */
    private static void variantCorpse(ServerLevel lv, BlockPos p, RandomSource rnd) {
        BlockEntity be = lv.getBlockEntity(p);
        if (be == null) return;
        try {
            Method m = be.getClass().getMethod("cycleVariant", boolean.class, RandomSource.class);
            int n = rnd.nextInt(6);
            for (int k = 0; k < n; k++) m.invoke(be, false, rnd);
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }
}
