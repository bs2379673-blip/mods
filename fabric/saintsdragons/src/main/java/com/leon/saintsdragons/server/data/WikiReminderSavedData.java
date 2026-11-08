package com.leon.saintsdragons.server.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WikiReminderSavedData extends SavedData {
    // port 1.21.11
    private static final net.minecraft.world.level.saveddata.SavedDataType<WikiReminderSavedData> SD_TYPE =
            com.leon.saintsdragons.util.compat.Compat.savedData("saintsdragons_wiki_reminders", WikiReminderSavedData::new, WikiReminderSavedData::load, WikiReminderSavedData::save);

    private static final String DATA_NAME = "saintsdragons_wiki_reminders";
    private final Set<UUID> shownPlayers = new HashSet<>();

    public static WikiReminderSavedData get(ServerLevel level) {
        ServerLevel storageLevel = level.getServer().overworld();
        return storageLevel.getDataStorage().computeIfAbsent(SD_TYPE);
    }

    public boolean markShownIfFirst(UUID playerId) {
        boolean added = shownPlayers.add(playerId);
        if (added) {
            setDirty();
        }
        return added;
    }

    public CompoundTag save(CompoundTag tag) {
        ListTag players = new ListTag();
        for (UUID playerId : shownPlayers) {
            CompoundTag playerTag = new CompoundTag();
            com.leon.saintsdragons.util.compat.Compat.putUUID(playerTag, "UUID", playerId);
            players.add(playerTag);
        }
        tag.put("Players", players);
        return tag;
    }

    public static WikiReminderSavedData load(CompoundTag tag) {
        WikiReminderSavedData data = new WikiReminderSavedData();
        if (tag.contains("Players")) {
            ListTag players = tag.getListOrEmpty("Players");
            for (int i = 0; i < players.size(); i++) {
                CompoundTag playerTag = players.getCompoundOrEmpty(i);
                if (com.leon.saintsdragons.util.compat.Compat.hasUUID(playerTag, "UUID")) {
                    data.shownPlayers.add(com.leon.saintsdragons.util.compat.Compat.getUUID(playerTag, "UUID"));
                }
            }
        }
        return data;
    }
}