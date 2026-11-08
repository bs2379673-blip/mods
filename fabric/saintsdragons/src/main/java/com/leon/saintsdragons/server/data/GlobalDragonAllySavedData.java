package com.leon.saintsdragons.server.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent per-player ally registry shared across all dragons.
 */
public class GlobalDragonAllySavedData extends SavedData {
    // port 1.21.11
    private static final net.minecraft.world.level.saveddata.SavedDataType<GlobalDragonAllySavedData> SD_TYPE =
            com.leon.saintsdragons.util.compat.Compat.savedData("saintsdragons_global_allies", GlobalDragonAllySavedData::new, GlobalDragonAllySavedData::load, GlobalDragonAllySavedData::save);

    private static final String DATA_NAME = "saintsdragons_global_allies";

    private final Map<UUID, Map<UUID, String>> alliesByOwner = new HashMap<>();

    public static GlobalDragonAllySavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SD_TYPE);
    }

    public Map<UUID, String> getAllies(UUID ownerId) {
        Map<UUID, String> allies = alliesByOwner.get(ownerId);
        if (allies == null) {
            return Map.of();
        }
        return new HashMap<>(allies);
    }

    public boolean isAlly(UUID ownerId, UUID allyId) {
        Map<UUID, String> allies = alliesByOwner.get(ownerId);
        if (allies == null) {
            return false;
        }
        return allies.containsKey(allyId);
    }

    public int getAllyCount(UUID ownerId) {
        Map<UUID, String> allies = alliesByOwner.get(ownerId);
        return allies == null ? 0 : allies.size();
    }

    public void addAlly(UUID ownerId, UUID allyId, String username) {
        alliesByOwner.computeIfAbsent(ownerId, id -> new HashMap<>()).put(allyId, username);
        setDirty();
    }

    public boolean removeAlly(UUID ownerId, UUID allyId) {
        Map<UUID, String> allies = alliesByOwner.get(ownerId);
        if (allies == null) {
            return false;
        }
        boolean removed = allies.remove(allyId) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public void clearAllies(UUID ownerId) {
        Map<UUID, String> allies = alliesByOwner.get(ownerId);
        if (allies != null && !allies.isEmpty()) {
            allies.clear();
            setDirty();
        }
    }

    public CompoundTag save(CompoundTag tag) {
        ListTag playerList = new ListTag();
        for (Map.Entry<UUID, Map<UUID, String>> entry : alliesByOwner.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            com.leon.saintsdragons.util.compat.Compat.putUUID(playerTag, "Owner", entry.getKey());
            ListTag allyList = new ListTag();
            for (Map.Entry<UUID, String> allyEntry : entry.getValue().entrySet()) {
                CompoundTag allyTag = new CompoundTag();
                com.leon.saintsdragons.util.compat.Compat.putUUID(allyTag, "UUID", allyEntry.getKey());
                allyTag.putString("Username", allyEntry.getValue());
                allyList.add(allyTag);
            }
            playerTag.put("Allies", allyList);
            playerList.add(playerTag);
        }
        tag.put("Players", playerList);
        return tag;
    }

    public static GlobalDragonAllySavedData load(CompoundTag tag) {
        GlobalDragonAllySavedData data = new GlobalDragonAllySavedData();
        if (tag.contains("Players")) {
            ListTag playerList = tag.getListOrEmpty("Players");
            for (int i = 0; i < playerList.size(); i++) {
                CompoundTag playerTag = playerList.getCompoundOrEmpty(i);
                if (!com.leon.saintsdragons.util.compat.Compat.hasUUID(playerTag, "Owner")) {
                    continue;
                }
                UUID ownerId = com.leon.saintsdragons.util.compat.Compat.getUUID(playerTag, "Owner");
                Map<UUID, String> allies = new HashMap<>();
                if (playerTag.contains("Allies")) {
                    ListTag allyList = playerTag.getListOrEmpty("Allies");
                    for (int j = 0; j < allyList.size(); j++) {
                        CompoundTag allyTag = allyList.getCompoundOrEmpty(j);
                        if (com.leon.saintsdragons.util.compat.Compat.hasUUID(allyTag, "UUID") && allyTag.contains("Username")) {
                            allies.put(com.leon.saintsdragons.util.compat.Compat.getUUID(allyTag, "UUID"), allyTag.getStringOr("Username", ""));
                        }
                    }
                }
                data.alliesByOwner.put(ownerId, allies);
            }
        }
        return data;
    }
}
