package com.starlevi.starspirits;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Los espiritus de un jugador (se guarda con el jugador y se conserva al morir). */
public final class SpiritData {
    public record Entry(UUID id, String type) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Entry::id),
                Codec.STRING.fieldOf("tipo").forGetter(Entry::type)).apply(i, Entry::new));
    }

    public static final Codec<SpiritData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Entry.CODEC.listOf().fieldOf("espiritus").forGetter(d -> d.entries),
            UUIDUtil.CODEC.optionalFieldOf("activo").forGetter(d -> Optional.ofNullable(d.active)),
            Codec.LONG.optionalFieldOf("bonsai", 0L).forGetter(d -> d.saveReadyAt),
            UUIDUtil.CODEC.listOf().optionalFieldOf("siguen", List.of()).forGetter(d -> d.followers)).apply(i, SpiritData::new));

    public final List<Entry> entries;
    public UUID active;
    /** Tick del juego desde el que el espiritu "salvar" puede volver a salvarte. */
    public long saveReadyAt;
    /** Espiritu elegido para la maceta (shift + clic): no se guarda. */
    public transient UUID potPick;
    /** Ya se eligieron los que siguen (para no volver a llenar la lista si el jugador la vacio). */
    public transient boolean followersChosen;

    /** Los que te siguen (como mucho Config.maxFollowers, elegidos con la varita). */
    public final List<UUID> followers;

    public SpiritData() {
        this(new ArrayList<>(), Optional.empty(), 0L, List.of());
    }

    private SpiritData(List<Entry> entries, Optional<UUID> active, long saveReadyAt, List<UUID> followers) {
        this.entries = new ArrayList<>(entries);
        this.active = active.orElse(null);
        this.saveReadyAt = saveReadyAt;
        this.followers = new ArrayList<>(followers);
    }

    public boolean follows(UUID id) {
        return this.followers.contains(id);
    }

    public Entry find(UUID id) {
        for (Entry e : this.entries) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        return null;
    }

    /** El espiritu activo (el que da beneficio): el elegido o, si no hay, el primero. */
    public Entry activeEntry() {
        Entry e = this.active == null ? null : find(this.active);
        return e != null ? e : this.entries.isEmpty() ? null : this.entries.get(0);
    }

    public Entry add(String type) {
        Entry e = new Entry(UUID.randomUUID(), type);
        this.entries.add(e);
        // si hay lugar, el nuevo te sigue
        if (this.followers.size() < com.starlevi.starspirits.Config.maxFollowers) {
            this.followers.add(e.id());
        }
        return e;
    }

    public boolean remove(UUID id) {
        boolean r = this.entries.removeIf(e -> e.id().equals(id));
        this.followers.remove(id);
        if (id.equals(this.active)) {
            this.active = null;
        }
        return r;
    }
}
