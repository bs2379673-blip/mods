package com.starlevi.starriders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Las monturas de un jugador (se guardan con el jugador y se conservan al morir). */
public final class RiderData {
    public record Mount(UUID id, String type) {
        public static final Codec<Mount> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Mount::id),
                Codec.STRING.fieldOf("tipo").forGetter(Mount::type)).apply(i, Mount::new));
    }

    public static final Codec<RiderData> CODEC = Mount.CODEC.listOf().xmap(RiderData::new, d -> d.mounts);

    public final List<Mount> mounts;

    public RiderData() {
        this(new ArrayList<>());
    }

    private RiderData(List<Mount> mounts) {
        this.mounts = new ArrayList<>(mounts);
    }

    public Mount find(UUID id) {
        for (Mount m : this.mounts) {
            if (m.id().equals(id)) {
                return m;
            }
        }
        return null;
    }
}
