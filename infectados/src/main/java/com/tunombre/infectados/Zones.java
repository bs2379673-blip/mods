package com.tunombre.infectados;

import com.tunombre.infectados.mob.Ganado;
import com.tunombre.infectados.mob.Motosierra;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Zonas de las plagas: SOLO aparecen aldeanos (y, si se activa, el de la motosierra) dentro de las zonas que crea un admin
 * con /infectados zona crear <nombre> <radio>, centradas donde esta parado. Aparecen mientras haya jugadores cerca,
 * siempre fuera de su vista inmediata (a mas de 16 bloques), hasta el maximo de la zona. Se guardan con el mundo.
 */
public class Zones extends SavedData {
    public static class Zone {
        public String name;
        public ResourceKey<Level> dim;
        public BlockPos center;
        public int radius, max = 10;
        public boolean active = true, boss = false;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("name", name);
            t.putString("dim", dim.location().toString());
            t.putLong("center", center.asLong());
            t.putInt("radius", radius);
            t.putInt("max", max);
            t.putBoolean("active", active);
            t.putBoolean("boss", boss);
            return t;
        }

        static Zone load(CompoundTag t) {
            Zone z = new Zone();
            z.name = t.getString("name");
            z.dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(t.getString("dim")));
            z.center = BlockPos.of(t.getLong("center"));
            z.radius = t.getInt("radius");
            z.max = t.getInt("max");
            z.active = t.getBoolean("active");
            z.boss = t.getBoolean("boss");
            return z;
        }

        public String describe() {
            return name + " | " + dim.location().getPath() + " " + center.toShortString() + " | radio " + radius + " | max " + max
                    + (boss ? " | +motosierra" : "") + (active ? "" : " | DESACTIVADA");
        }
    }

    public final Map<String, Zone> zones = new LinkedHashMap<>();

    public static Zones get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(Zones::load, Zones::new, "infectados_zonas");
    }

    static Zones load(CompoundTag t) {
        Zones z = new Zones();
        ListTag l = t.getList("zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            Zone zo = Zone.load(l.getCompound(i));
            z.zones.put(zo.name, zo);
        }
        return z;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        ListTag l = new ListTag();
        zones.values().forEach(z -> l.add(z.save()));
        t.put("zones", l);
        return t;
    }

    // ------------------------------------------------------------------ aparicion ----
    private static final String TAG = "infectados_zona";

    public static void tick(MinecraftServer server) {
        Zones data = get(server);
        for (Zone z : data.zones.values()) {
            if (!z.active) continue;
            ServerLevel lv = server.getLevel(z.dim);
            if (lv == null) continue;
            boolean near = false;
            for (ServerPlayer p : lv.players()) {
                if (!p.isSpectator() && p.blockPosition().distSqr(z.center) < (double) (z.radius + 48) * (z.radius + 48)) { near = true; break; }
            }
            if (!near || !lv.hasChunkAt(z.center)) continue;
            AABB box = new AABB(z.center).inflate(z.radius + 32, 64, z.radius + 32);
            int ganados = 0, bosses = 0;
            for (Mob m : lv.getEntitiesOfClass(Mob.class, box, m -> z.name.equals(m.getPersistentData().getString(TAG)))) {
                if (m instanceof Motosierra) bosses++;
                else ganados++;
            }
            if (ganados < z.max) {
                int n = Math.min(z.max - ganados, 1 + lv.random.nextInt(3));
                for (int i = 0; i < n; i++) spawn(lv, z, Infectados.GANADO.get().create(lv));
            }
            if (z.boss && bosses == 0 && lv.random.nextInt(Infectados.cfgBossRarity()) == 0) spawn(lv, z, Infectados.MOTOSIERRA.get().create(lv));
        }
    }

    private static void spawn(ServerLevel lv, Zone z, Mob m) {
        if (m == null) return;
        for (int tries = 0; tries < 12; tries++) {
            double a = lv.random.nextDouble() * Math.PI * 2, r = Math.sqrt(lv.random.nextDouble()) * z.radius;
            int x = z.center.getX() + (int) Math.round(Math.cos(a) * r), zz = z.center.getZ() + (int) Math.round(Math.sin(a) * r);
            if (!lv.hasChunkAt(new BlockPos(x, 0, zz))) continue;
            int y = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, zz);
            BlockPos p = new BlockPos(x, y, zz);
            if (!lv.getBlockState(p.below()).isSolidRender(lv, p.below()) || !lv.isEmptyBlock(p) || !lv.isEmptyBlock(p.above())) continue;
            boolean seen = false;
            for (ServerPlayer pl : lv.players()) if (pl.blockPosition().distSqr(p) < 16 * 16) { seen = true; break; }
            if (seen) continue;
            m.moveTo(x + 0.5, y, zz + 0.5, lv.random.nextFloat() * 360F, 0);
            if (!lv.noCollision(m)) continue;
            m.finalizeSpawn(lv, lv.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null, null);
            m.getPersistentData().putString(TAG, z.name);
            lv.addFreshEntity(m);
            return;
        }
        m.discard();
    }
}
