// STUB SOLO PARA COMPILAR (no se incluye en el jar): firmas copiadas de sa_decor 1.0.3; en el juego se usan las clases reales.
package com.ogaba.sa_decor.common.loot;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
public final class DecorLootMarkerIndexSavedData extends SavedData {
    public static DecorLootMarkerIndexSavedData get(ServerLevel level) { throw new UnsupportedOperationException(); }
    public Map<BlockPos, Marker> snapshot() { throw new UnsupportedOperationException(); }
    public long countProfile(String id) { throw new UnsupportedOperationException(); }
    @Override public CompoundTag save(CompoundTag tag) { return tag; }
    public record Marker(String profileId, String blockId) { }
}
