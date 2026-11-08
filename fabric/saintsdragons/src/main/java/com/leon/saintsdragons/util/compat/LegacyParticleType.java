package com.leon.saintsdragons.util.compat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Port 1.21.11: un tipo de particula con datos, armado con lo de 1.20.1 (codec y deserializer). */
public abstract class LegacyParticleType<T extends LegacyParticleOptions> extends ParticleType<T> {
    private final ParticleDeserializer<T> deserializer;
    private MapCodec<T> mapCodec;
    private final StreamCodec<RegistryFriendlyByteBuf, T> stream;

    protected LegacyParticleType(boolean overrideLimiter, ParticleDeserializer<T> deserializer) {
        super(overrideLimiter);
        this.deserializer = deserializer;
        this.stream = StreamCodec.of((buf, value) -> value.writeToNetwork(buf), buf -> this.deserializer.fromNetwork(this, buf));
    }

    /** El codec de 1.20.1. */
    public abstract Codec<T> legacyCodec();

    @Override
    public MapCodec<T> codec() {
        if (this.mapCodec == null) {
            this.mapCodec = MapCodec.assumeMapUnsafe(legacyCodec());
        }
        return this.mapCodec;
    }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec() {
        return this.stream;
    }
}
