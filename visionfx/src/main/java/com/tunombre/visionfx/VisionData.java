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
    /** anulaciones por jugador de la vision PS1 */
    public final Map<UUID, Boolean> ps1Over = new HashMap<>();

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
        ListTag l2 = t.getList("ps1Over", Tag.TAG_COMPOUND);
        for (int i = 0; i < l2.size(); i++) {
            CompoundTag o = l2.getCompound(i);
            d.ps1Over.put(o.getUUID("id"), o.getBoolean("on"));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        t.put("global", global.save());
        ListTag l = new ListTag();
        overrides.forEach((id, on) -> { CompoundTag o = new CompoundTag(); o.putUUID("id", id); o.putBoolean("on", on); l.add(o); });
        t.put("overrides", l);
        ListTag l2 = new ListTag();
        ps1Over.forEach((id, on) -> { CompoundTag o = new CompoundTag(); o.putUUID("id", id); o.putBoolean("on", on); l2.add(o); });
        t.put("ps1Over", l2);
        return t;
    }
}
