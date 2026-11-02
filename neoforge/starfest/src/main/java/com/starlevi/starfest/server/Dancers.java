package com.starlevi.starfest.server;

import com.starlevi.starfest.StarFest;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.fml.ModList;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bailarines: NPC de Annis, aldeanos de MCA o cualquier criatura marcada. Bailan mientras suena musica
 * de StarFest donde estan, y se quedan quietos cuando se corta. Se guardan en el mundo.
 */
public final class Dancers {
    public static final boolean ANNIS = ModList.get().isLoaded("annisnpc");
    private static Data data;
    /** Los que estan bailando ahora (para no cortar animaciones que no pusimos nosotros). */
    private static final Set<UUID> DANCING = new HashSet<>();
    private static final Map<UUID, Long> REFRESH = new java.util.HashMap<>();

    private Dancers() {
    }

    public static final class Data extends SavedData {
        final Map<UUID, String> dancers = new LinkedHashMap<>();

        static Data load(CompoundTag tag, HolderLookup.Provider p) {
            Data d = new Data();
            ListTag l = tag.getList("bailarines", Tag.TAG_COMPOUND);
            for (int i = 0; i < l.size(); i++) {
                CompoundTag c = l.getCompound(i);
                d.dancers.put(c.getUUID("id"), c.getString("anim"));
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
            ListTag l = new ListTag();
            for (Map.Entry<UUID, String> e : this.dancers.entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putUUID("id", e.getKey());
                c.putString("anim", e.getValue());
                l.add(c);
            }
            tag.put("bailarines", l);
            return tag;
        }
    }

    public static void load(MinecraftServer s) {
        data = s.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load), "starfest_bailarines");
        DANCING.clear();
    }

    public static Map<UUID, String> all() {
        return data == null ? Map.of() : data.dancers;
    }

    public static void add(LivingEntity e, String anim) {
        data.dancers.put(e.getUUID(), anim);
        data.setDirty();
    }

    public static boolean remove(MinecraftServer s, LivingEntity e) {
        boolean had = data.dancers.remove(e.getUUID()) != null;
        if (had) {
            data.setDirty();
            if (DANCING.remove(e.getUUID()) && ANNIS) {
                AnnisBridge.stop(e);
            }
        }
        return had;
    }

    public static int clear(MinecraftServer s) {
        int n = data.dancers.size();
        for (UUID id : data.dancers.keySet()) {
            Entity e = find(s, id);
            if (e instanceof LivingEntity le && DANCING.contains(id) && ANNIS) {
                AnnisBridge.stop(le);
            }
        }
        data.dancers.clear();
        DANCING.clear();
        data.setDirty();
        return n;
    }

    private static Entity find(MinecraftServer s, UUID id) {
        for (ServerLevel l : s.getAllLevels()) {
            Entity e = l.getEntity(id);
            if (e != null) {
                return e;
            }
        }
        return null;
    }

    public static void tick(MinecraftServer s) {
        if (data == null || !ANNIS || data.dancers.isEmpty()) {
            return;
        }
        long now = s.overworld().getGameTime();
        for (Map.Entry<UUID, String> en : data.dancers.entrySet()) {
            Entity e = find(s, en.getKey());
            if (!(e instanceof LivingEntity le) || !le.isAlive()) {
                continue;
            }
            boolean music = ShowServer.playingAt(le.level().dimension(), le.blockPosition()) != null;
            UUID id = en.getKey();
            try {
                if (music) {
                    // se repite cada tanto por si otra cosa le cambio la animacion
                    Long last = REFRESH.get(id);
                    String cur = AnnisBridge.current(le);
                    if (!DANCING.contains(id) || !en.getValue().equals(cur) && (last == null || now - last > 100)) {
                        if (cur.isEmpty() || DANCING.contains(id)) {
                            AnnisBridge.play(le, en.getValue());
                            DANCING.add(id);
                            REFRESH.put(id, now);
                        }
                    }
                } else if (DANCING.remove(id)) {
                    if (en.getValue().equals(AnnisBridge.current(le))) {
                        AnnisBridge.stop(le);
                    }
                }
            } catch (Throwable t) {
                StarFest.LOGGER.debug("StarFest: bailarin {}: {}", id, t.toString());
            }
        }
    }
}
