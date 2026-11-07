package com.starlevi.starvillagers.social;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lo que un aldeano siente y recuerda: su personalidad, su humor y lo que piensa de cada jugador.
 * Se guarda con el aldeano (solo en el servidor).
 */
public final class Mind {
    /** Lo que piensa de un jugador. */
    public static final class Bond {
        public int hearts;          // -100..100
        public long lastChat = -1;  // dia del ultimo "charlar" que sumo
        public long lastGiftDay = -1;
        public int giftsToday;
        public String lastGift = "";
        public int hits;
        public long lastSeen = -1;  // dia

        public static final Codec<Bond> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("corazones", 0).forGetter(b -> b.hearts),
                Codec.LONG.optionalFieldOf("charla", -1L).forGetter(b -> b.lastChat),
                Codec.LONG.optionalFieldOf("regalo_dia", -1L).forGetter(b -> b.lastGiftDay),
                Codec.INT.optionalFieldOf("regalos_hoy", 0).forGetter(b -> b.giftsToday),
                Codec.STRING.optionalFieldOf("ultimo_regalo", "").forGetter(b -> b.lastGift),
                Codec.INT.optionalFieldOf("golpes", 0).forGetter(b -> b.hits),
                Codec.LONG.optionalFieldOf("visto", -1L).forGetter(b -> b.lastSeen)
        ).apply(i, (h, c, gd, gt, lg, hi, ls) -> {
            Bond b = new Bond();
            b.hearts = h;
            b.lastChat = c;
            b.lastGiftDay = gd;
            b.giftsToday = gt;
            b.lastGift = lg;
            b.hits = hi;
            b.lastSeen = ls;
            return b;
        }));
    }

    public String personality;
    public int mood;
    public final Map<UUID, Bond> bonds;

    public Mind(String personality, int mood, Map<UUID, Bond> bonds) {
        this.personality = personality;
        this.mood = mood;
        this.bonds = new HashMap<>(bonds);
    }

    public static final Codec<Mind> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("personalidad").forGetter(m -> m.personality),
            Codec.INT.optionalFieldOf("humor", 0).forGetter(m -> m.mood),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Bond.CODEC).optionalFieldOf("jugadores", Map.of()).forGetter(m -> m.bonds)
    ).apply(i, Mind::new));

    public Bond bond(UUID player) {
        return bonds.computeIfAbsent(player, k -> new Bond());
    }

    public int hearts(UUID player) {
        Bond b = bonds.get(player);
        return b == null ? 0 : b.hearts;
    }

    public void addHearts(UUID player, int n) {
        Bond b = bond(player);
        b.hearts = Math.max(-100, Math.min(100, b.hearts + n));
    }

    public void addMood(int n) {
        mood = Math.max(-100, Math.min(100, mood + n));
    }
}
