package com.starlevi.starspirits.server;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Espiritus que vuelven a un jugador desconectado (por ejemplo, rompieron su maceta): se le dan al entrar. */
public final class Pending extends SavedData {
    private final Map<UUID, List<String>> waiting = new HashMap<>();

    private static Pending get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Pending::new, Pending::read), "starspirits_pendientes");
    }

    public static void add(MinecraftServer s, UUID owner, String type) {
        Pending p = get(s);
        p.waiting.computeIfAbsent(owner, u -> new ArrayList<>()).add(type);
        p.setDirty();
    }

    public static void giveBack(ServerPlayer player) {
        Pending p = get(player.server);
        List<String> list = p.waiting.remove(player.getUUID());
        if (list == null) {
            return;
        }
        p.setDirty();
        for (String t : list) {
            Caravan.give(player, t);
        }
        player.sendSystemMessage(Component.literal("🌱 Volvieron " + list.size() + " espíritus de una maceta que se rompió"));
    }

    static Pending read(CompoundTag tag, HolderLookup.Provider prov) {
        Pending p = new Pending();
        CompoundTag all = tag.getCompound("pendientes");
        for (String k : all.getAllKeys()) {
            List<String> l = new ArrayList<>();
            ListTag lt = all.getList(k, Tag.TAG_STRING);
            for (int i = 0; i < lt.size(); i++) {
                l.add(lt.getString(i));
            }
            try {
                p.waiting.put(UUID.fromString(k), l);
            } catch (IllegalArgumentException ignored) {
                // clave rota
            }
        }
        return p;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider prov) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<UUID, List<String>> e : this.waiting.entrySet()) {
            ListTag lt = new ListTag();
            for (String t : e.getValue()) {
                lt.add(StringTag.valueOf(t));
            }
            all.put(e.getKey().toString(), lt);
        }
        tag.put("pendientes", all);
        return tag;
    }
}
