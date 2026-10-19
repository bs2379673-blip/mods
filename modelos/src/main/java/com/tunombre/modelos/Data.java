package com.tunombre.modelos;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** que modelo le pusimos a cada jugador (para saber a quien mantener con Epic Fight apagado) */
public class Data extends SavedData {
    public final Map<UUID, String> applied = new HashMap<>();
    /** jugadores a los que ESTE mod les apago Epic Fight (se les vuelve a prender cuando ya no tengan modelo) */
    public final java.util.Set<UUID> efOff = new java.util.HashSet<>();

    public static Data get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, "modelos");
    }

    static Data load(CompoundTag t) {
        Data d = new Data();
        ListTag l = t.getList("applied", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) { CompoundTag o = l.getCompound(i); d.applied.put(o.getUUID("id"), o.getString("model")); }
        ListTag e = t.getList("efOff", Tag.TAG_COMPOUND);
        for (int i = 0; i < e.size(); i++) d.efOff.add(e.getCompound(i).getUUID("id"));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        ListTag l = new ListTag();
        applied.forEach((id, m) -> { CompoundTag o = new CompoundTag(); o.putUUID("id", id); o.putString("model", m); l.add(o); });
        t.put("applied", l);
        ListTag e = new ListTag();
        efOff.forEach(id -> { CompoundTag o = new CompoundTag(); o.putUUID("id", id); e.add(o); });
        t.put("efOff", e);
        return t;
    }
}
