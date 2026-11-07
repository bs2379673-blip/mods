package com.starlevi.starvillagers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Quien es un aldeano: nombre, si es mujer, sus genes (color de piel y de pelo, altura) y que
 * capas de skin usa (piel, cara, pelo y ropa). Se guarda en el aldeano y se manda a los clientes.
 */
public record Identity(String name, boolean female, float melanin, float hemoglobin, float eumelanin, float pheomelanin, float height, String skin,
                       String face, String hair, String clothes, float voice, int seed) {
    public static final Codec<Identity> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("nombre").forGetter(Identity::name),
            Codec.BOOL.fieldOf("mujer").forGetter(Identity::female),
            Codec.FLOAT.fieldOf("melanina").forGetter(Identity::melanin),
            Codec.FLOAT.fieldOf("hemoglobina").forGetter(Identity::hemoglobin),
            Codec.FLOAT.fieldOf("eumelanina").forGetter(Identity::eumelanin),
            Codec.FLOAT.fieldOf("feomelanina").forGetter(Identity::pheomelanin),
            Codec.FLOAT.fieldOf("altura").forGetter(Identity::height),
            Codec.STRING.fieldOf("piel").forGetter(Identity::skin),
            Codec.STRING.fieldOf("cara").forGetter(Identity::face),
            Codec.STRING.fieldOf("pelo").forGetter(Identity::hair),
            Codec.STRING.fieldOf("ropa").forGetter(Identity::clothes),
            Codec.FLOAT.fieldOf("voz").forGetter(Identity::voice),
            Codec.INT.fieldOf("semilla").forGetter(Identity::seed)
    ).apply(i, Identity::new));

    public static final StreamCodec<FriendlyByteBuf, Identity> STREAM_CODEC = StreamCodec.of((b, v) -> {
        b.writeUtf(v.name, 64);
        b.writeBoolean(v.female);
        b.writeFloat(v.melanin);
        b.writeFloat(v.hemoglobin);
        b.writeFloat(v.eumelanin);
        b.writeFloat(v.pheomelanin);
        b.writeFloat(v.height);
        b.writeUtf(v.skin, 128);
        b.writeUtf(v.face, 128);
        b.writeUtf(v.hair, 128);
        b.writeUtf(v.clothes, 128);
        b.writeFloat(v.voice);
        b.writeInt(v.seed);
    }, b -> new Identity(b.readUtf(64), b.readBoolean(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readUtf(128),
            b.readUtf(128), b.readUtf(128), b.readUtf(128), b.readFloat(), b.readInt()));

    public Identity withClothes(String c) {
        return new Identity(this.name, this.female, this.melanin, this.hemoglobin, this.eumelanin, this.pheomelanin, this.height, this.skin, this.face,
                this.hair, c, this.voice, this.seed);
    }

    /** Clave de como se ve (para compartir la textura entre aldeanos iguales). */
    public String looksKey() {
        return this.skin + "|" + this.face + "|" + this.hair + "|" + this.clothes + "|" + Math.round(this.melanin * 31) + "," + Math.round(this.hemoglobin * 31)
                + "|" + Math.round(this.eumelanin * 31) + "," + Math.round(this.pheomelanin * 31);
    }
}
