package com.tunombre.visionfx;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** estado guardado en el mundo: parametros globales + anulaciones por jugador (true = forzado encendido, false = apagado) */
public class VisionData extends SavedData {
    public Params global = new Params();
    public final Map<UUID, Boolean> overrides = new HashMap<>();

    public static VisionData get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(VisionData::load, VisionData::new, "visionfx");
    }

    static VisionData load(CompoundTag t) {
        VisionData d = new VisionData();
        d.global = Params.load(t.getCompound("global"));
        ListTag l = t.getList("overrides", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag o = l.getCompound(i);
            d.overrides.put(o.getUUID("id"), o.getBoolean("on"));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        t.put("global", global.save());
        ListTag l = new ListTag();
        overrides.forEach((id, on) -> { CompoundTag o = new CompoundTag(); o.putUUID("id", id); o.putBoolean("on", on); l.add(o); });
        t.put("overrides", l);
        return t;
    }
}
