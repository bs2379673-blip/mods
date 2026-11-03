package com.starlevi.starspirits.server;

import com.starlevi.starspirits.Config;
import com.starlevi.starspirits.ModRegistry;
import com.starlevi.starspirits.SpiritData;
import com.starlevi.starspirits.SpiritDef;
import com.starlevi.starspirits.SpiritEntity;
import com.starlevi.starspirits.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Casitas de espiritus: areas que cada jugador marca con la varita para que sus espiritus anden libres
 * (por ejemplo, en su casa). Se guardan con el mundo; los espiritus aparecen cuando hay alguien cerca.
 */
public final class Homes {
    /** Un area: de quien es, donde esta y que espiritus viven ahi (id de la entrada -> tipo). */
    public static final class Area {
        public UUID id, owner;
        public String name;
        public ResourceKey<Level> dim;
        public BlockPos a, b;
        public final Map<UUID, String> spirits = new HashMap<>();

        public AABB box() {
            return new AABB(Math.min(this.a.getX(), this.b.getX()), Math.min(this.a.getY(), this.b.getY()), Math.min(this.a.getZ(), this.b.getZ()),
                    Math.max(this.a.getX(), this.b.getX()) + 1, Math.max(this.a.getY(), this.b.getY()) + 1, Math.max(this.a.getZ(), this.b.getZ()) + 1);
        }
    }

    /** Lo que se guarda en el mundo. */
    public static final class Store extends SavedData {
        public final List<Area> areas = new ArrayList<>();

        static Store load(CompoundTag tag, HolderLookup.Provider reg) {
            Store s = new Store();
            for (Tag t : tag.getList("areas", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                Area a = new Area();
                a.id = c.getUUID("id");
                a.owner = c.getUUID("dueno");
                a.name = c.getString("nombre");
                a.dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(c.getString("dim")));
                a.a = BlockPos.of(c.getLong("a"));
                a.b = BlockPos.of(c.getLong("b"));
                for (Tag e : c.getList("espiritus", Tag.TAG_COMPOUND)) {
                    CompoundTag ec = (CompoundTag) e;
                    a.spirits.put(ec.getUUID("id"), ec.getString("tipo"));
                }
                s.areas.add(a);
            }
            return s;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
            ListTag list = new ListTag();
            for (Area a : this.areas) {
                CompoundTag c = new CompoundTag();
                c.putUUID("id", a.id);
                c.putUUID("dueno", a.owner);
                c.putString("nombre", a.name);
                c.putString("dim", a.dim.location().toString());
                c.putLong("a", a.a.asLong());
                c.putLong("b", a.b.asLong());
                ListTag sp = new ListTag();
                a.spirits.forEach((id, type) -> {
                    CompoundTag ec = new CompoundTag();
                    ec.putUUID("id", id);
                    ec.putString("tipo", type);
                    sp.add(ec);
                });
                c.put("espiritus", sp);
                list.add(c);
            }
            tag.put("areas", list);
            return tag;
        }
    }

    /** Area maxima (bloques por lado) para que no marquen medio mapa. */
    public static final int MAX_SIDE = 48;
    private static MinecraftServer server;
    /** Espiritus vivos en sus casitas (id de la entrada -> entidad). */
    private static final Map<UUID, SpiritEntity> LIVE = new HashMap<>();
    /** Seleccion de la varita de cada jugador (no se guarda). */
    private static final Map<UUID, BlockPos[]> SELECTION = new HashMap<>();

    private Homes() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            server = e.getServer();
            tick(e.getServer());
        });
    }

    public static Store store() {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Store::new, Store::load, null), "starspirits_casitas");
    }

    /** Ese espiritu vive en alguna casita (no sigue a su dueno). */
    public static boolean isHome(UUID entry) {
        if (server == null) {
            return false;
        }
        for (Area a : store().areas) {
            if (a.spirits.containsKey(entry)) {
                return true;
            }
        }
        return false;
    }

    public static List<Area> of(UUID owner) {
        List<Area> out = new ArrayList<>();
        for (Area a : store().areas) {
            if (a.owner.equals(owner)) {
                out.add(a);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ varita

    /** Clic en un bloque con la varita: marca una punta (se alternan). */
    public static void mark(ServerPlayer p, BlockPos pos) {
        BlockPos[] sel = SELECTION.computeIfAbsent(p.getUUID(), k -> new BlockPos[3]);
        int which = sel[2] == null || sel[2].getX() == 1 ? 0 : 1;
        sel[which] = pos.immutable();
        sel[2] = new BlockPos(which, 0, 0);
        p.displayClientMessage(Component.literal("✦ Punta " + (which + 1) + " marcada: " + pos.toShortString()
                + (sel[0] != null && sel[1] != null ? "  (" + size(sel[0], sel[1]) + ") — abrí el menú para crear el área" : ""))
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        p.level().playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.6f, 1.5f + which * 0.3f);
    }

    private static String size(BlockPos a, BlockPos b) {
        return (Math.abs(a.getX() - b.getX()) + 1) + "×" + (Math.abs(a.getY() - b.getY()) + 1) + "×" + (Math.abs(a.getZ() - b.getZ()) + 1);
    }

    /** Manda el menu de la varita (sus espiritus, sus areas y la seleccion). */
    public static void openMenu(ServerPlayer p, boolean open) {
        SpiritData d = Caravan.data(p);
        List<Net.MenuSpirit> spirits = new ArrayList<>();
        for (SpiritData.Entry e : d.entries) {
            UUID area = null;
            for (Area a : of(p.getUUID())) {
                if (a.spirits.containsKey(e.id())) {
                    area = a.id;
                }
            }
            spirits.add(new Net.MenuSpirit(e.id(), e.type(), d.follows(e.id()), area == null ? new UUID(0, 0) : area));
        }
        List<Net.MenuArea> areas = new ArrayList<>();
        for (Area a : of(p.getUUID())) {
            areas.add(new Net.MenuArea(a.id, a.name, size(a.a, a.b)));
        }
        BlockPos[] sel = SELECTION.get(p.getUUID());
        String selText = sel == null || sel[0] == null || sel[1] == null ? "" : size(sel[0], sel[1]);
        PacketDistributor.sendToPlayer(p, new Net.Menu(open, spirits, areas, selText, Config.maxFollowers));
    }

    /** Lo que eligio en el menu. */
    public static void action(ServerPlayer p, Net.MenuAction act) {
        SpiritData d = Caravan.data(p);
        switch (act.kind()) {
            case Net.MenuAction.FOLLOW -> {
                SpiritData.Entry e = d.find(act.spirit());
                if (e == null) {
                    break;
                }
                if (d.follows(e.id())) {
                    d.followers.remove(e.id());
                    Caravan.poof(e.id());
                } else if (d.followers.size() >= Config.maxFollowers) {
                    p.displayClientMessage(Component.literal("✦ Solo te pueden seguir " + Config.maxFollowers + " espíritus a la vez")
                            .withStyle(ChatFormatting.RED), true);
                } else {
                    unassign(e.id());
                    d.followers.add(e.id());
                }
                d.followersChosen = true;
            }
            case Net.MenuAction.ASSIGN -> {
                SpiritData.Entry e = d.find(act.spirit());
                if (e == null) {
                    break;
                }
                unassign(e.id());
                Area target = null;
                for (Area a : of(p.getUUID())) {
                    if (a.id.equals(act.area())) {
                        target = a;
                    }
                }
                if (target != null) {
                    d.followers.remove(e.id());
                    Caravan.poof(e.id());
                    target.spirits.put(e.id(), e.type());
                    p.displayClientMessage(Component.literal("🏠 " + Caravan.displayName(e.type()) + " ahora vive en " + target.name), true);
                }
                store().setDirty();
            }
            case Net.MenuAction.CREATE -> {
                BlockPos[] sel = SELECTION.get(p.getUUID());
                if (sel == null || sel[0] == null || sel[1] == null) {
                    p.displayClientMessage(Component.literal("✦ Marcá dos puntas con la varita (clic derecho en dos bloques)").withStyle(ChatFormatting.RED), true);
                    break;
                }
                if (Math.abs(sel[0].getX() - sel[1].getX()) >= MAX_SIDE || Math.abs(sel[0].getZ() - sel[1].getZ()) >= MAX_SIDE
                        || Math.abs(sel[0].getY() - sel[1].getY()) >= MAX_SIDE) {
                    p.displayClientMessage(Component.literal("✦ El área es muy grande (máximo " + MAX_SIDE + " bloques por lado)").withStyle(ChatFormatting.RED), true);
                    break;
                }
                Area a = new Area();
                a.id = UUID.randomUUID();
                a.owner = p.getUUID();
                a.dim = p.level().dimension();
                a.a = sel[0];
                a.b = sel[1];
                a.name = act.name().isBlank() ? "Área " + (of(p.getUUID()).size() + 1) : act.name().trim();
                if (a.name.length() > 24) {
                    a.name = a.name.substring(0, 24);
                }
                store().areas.add(a);
                store().setDirty();
                SELECTION.remove(p.getUUID());
                p.displayClientMessage(Component.literal("🏠 Área creada: " + a.name).withStyle(ChatFormatting.GREEN), true);
                p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 1.2f);
            }
            case Net.MenuAction.DELETE -> {
                for (Iterator<Area> it = store().areas.iterator(); it.hasNext(); ) {
                    Area a = it.next();
                    if (a.id.equals(act.area()) && a.owner.equals(p.getUUID())) {
                        for (UUID s : a.spirits.keySet()) {
                            despawn(s);
                        }
                        it.remove();
                    }
                }
                store().setDirty();
            }
            default -> {
            }
        }
        openMenu(p, false);
    }

    private static void unassign(UUID entry) {
        for (Area a : store().areas) {
            if (a.spirits.remove(entry) != null) {
                despawn(entry);
                store().setDirty();
            }
        }
    }

    private static void despawn(UUID entry) {
        SpiritEntity s = LIVE.remove(entry);
        if (s != null) {
            if (s.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CHERRY_LEAVES, s.getX(), s.getY() + 0.4, s.getZ(), 10, 0.25, 0.25, 0.25, 0.02);
            }
            s.discard();
        }
    }

    // ------------------------------------------------------------------ cada tick

    private static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        // contorno de la seleccion de quien tiene la varita en la mano
        if (now % 8 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!p.getMainHandItem().is(ModRegistry.WAND.get()) && !p.getOffhandItem().is(ModRegistry.WAND.get())) {
                    continue;
                }
                BlockPos[] sel = SELECTION.get(p.getUUID());
                if (sel != null && sel[0] != null && sel[1] != null) {
                    Area tmp = new Area();
                    tmp.a = sel[0];
                    tmp.b = sel[1];
                    outline(p, tmp.box(), new Vector3f(1f, 0.6f, 1f));
                } else if (sel != null && sel[0] != null) {
                    outline(p, new AABB(sel[0]), new Vector3f(1f, 0.6f, 1f));
                }
                for (Area a : of(p.getUUID())) {
                    if (a.dim == p.level().dimension() && a.box().getCenter().distanceTo(p.position()) < 64) {
                        outline(p, a.box(), new Vector3f(0.5f, 1f, 0.7f));
                    }
                }
            }
        }
        if (now % 20 != 0) {
            return;
        }
        // espiritus en sus casitas: aparecen si hay alguien cerca y el lugar esta cargado
        java.util.Set<UUID> wanted = new java.util.HashSet<>();
        for (Area a : store().areas) {
            ServerLevel level = server.getLevel(a.dim);
            if (level == null) {
                continue;
            }
            AABB box = a.box();
            Vec3 c = box.getCenter();
            boolean near = level.getNearestPlayer(c.x, c.y, c.z, 72, false) != null && level.isLoaded(BlockPos.containing(c));
            for (Map.Entry<UUID, String> e : a.spirits.entrySet()) {
                if (!near) {
                    continue;
                }
                wanted.add(e.getKey());
                SpiritEntity s = LIVE.get(e.getKey());
                if (s == null || !s.isAlive() || s.level() != level) {
                    s = ModRegistry.SPIRIT.get().create(level);
                    if (s == null) {
                        continue;
                    }
                    Vec3 at = spot(level, box);
                    s.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360, 0);
                    s.setSpiritType(e.getValue());
                    s.setOwner(a.owner);
                    s.entryId = e.getKey();
                    level.addFreshEntity(s);
                    LIVE.put(e.getKey(), s);
                }
                s.home = box;
                if (!box.inflate(2).contains(s.position())) {
                    Vec3 at = spot(level, box);
                    s.teleportTo(at.x, at.y, at.z);
                }
            }
        }
        for (Iterator<Map.Entry<UUID, SpiritEntity>> it = LIVE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, SpiritEntity> en = it.next();
            if (!wanted.contains(en.getKey()) || !en.getValue().isAlive()) {
                en.getValue().discard();
                it.remove();
            }
        }
    }

    /** Un lugar con piso adentro del area. */
    static Vec3 spot(ServerLevel level, AABB box) {
        for (int i = 0; i < 20; i++) {
            int x = (int) Math.floor(box.minX + level.random.nextDouble() * (box.maxX - box.minX));
            int z = (int) Math.floor(box.minZ + level.random.nextDouble() * (box.maxZ - box.minZ));
            for (int y = (int) box.maxY; y >= (int) box.minY; y--) {
                BlockPos at = new BlockPos(x, y, z);
                if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
                        && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)) {
                    return Vec3.atBottomCenterOf(at);
                }
            }
        }
        return box.getCenter();
    }

    private static void outline(ServerPlayer p, AABB b, Vector3f color) {
        DustParticleOptions dust = new DustParticleOptions(color, 1.0f);
        ServerLevel sl = p.serverLevel();
        double step = Math.max(1, Math.max(b.getXsize(), b.getZsize()) / 24);
        for (double x = b.minX; x <= b.maxX; x += step) {
            for (double y : new double[]{b.minY, b.maxY}) {
                sl.sendParticles(p, dust, true, x, y, b.minZ, 1, 0, 0, 0, 0);
                sl.sendParticles(p, dust, true, x, y, b.maxZ, 1, 0, 0, 0, 0);
            }
        }
        for (double z = b.minZ; z <= b.maxZ; z += step) {
            for (double y : new double[]{b.minY, b.maxY}) {
                sl.sendParticles(p, dust, true, b.minX, y, z, 1, 0, 0, 0, 0);
                sl.sendParticles(p, dust, true, b.maxX, y, z, 1, 0, 0, 0, 0);
            }
        }
        for (double y = b.minY; y <= b.maxY; y += step) {
            for (double[] c : new double[][]{{b.minX, b.minZ}, {b.minX, b.maxZ}, {b.maxX, b.minZ}, {b.maxX, b.maxZ}}) {
                sl.sendParticles(p, dust, true, c[0], y, c[1], 1, 0, 0, 0, 0);
            }
        }
    }

    public static SpiritDef def(String type) {
        return SpiritLibrary.get(type);
    }
}
