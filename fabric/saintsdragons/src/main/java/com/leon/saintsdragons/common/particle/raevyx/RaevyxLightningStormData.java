package com.leon.saintsdragons.common.particle.raevyx;

import com.leon.saintsdragons.common.registry.ModParticles;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

import org.jetbrains.annotations.NotNull;
import java.util.Locale;

public record RaevyxLightningStormData(float size, boolean nightGold) implements com.leon.saintsdragons.util.compat.LegacyParticleOptions {
    public RaevyxLightningStormData(float size) {
        this(size, false);
    }

    public static final com.leon.saintsdragons.util.compat.ParticleDeserializer<RaevyxLightningStormData> DESERIALIZER = new com.leon.saintsdragons.util.compat.ParticleDeserializer<>() {
        @Override
        public @NotNull RaevyxLightningStormData fromCommand(@NotNull ParticleType<RaevyxLightningStormData> type, @NotNull StringReader reader) throws CommandSyntaxException {
            reader.expect(' ');
            float size = reader.readFloat();
            return new RaevyxLightningStormData(size, isNightGoldType(type));
        }

        @Override
        public @NotNull RaevyxLightningStormData fromNetwork(@NotNull ParticleType<RaevyxLightningStormData> type, @NotNull FriendlyByteBuf buf) {
            return new RaevyxLightningStormData(buf.readFloat(), isNightGoldType(type));
        }
    };

    public static Codec<RaevyxLightningStormData> CODEC(@SuppressWarnings("unused") ParticleType<RaevyxLightningStormData> type) {
        return RecordCodecBuilder.create(b -> b.group(
                Codec.FLOAT.fieldOf("size").forGetter(RaevyxLightningStormData::size)
        ).apply(b, size -> new RaevyxLightningStormData(size, isNightGoldType(type))));
    }

    private static boolean isNightGoldType(ParticleType<RaevyxLightningStormData> type) {
        return type == ModParticles.LIGHTNING_STORM_NIGHT_GOLD.get();
    }

        public void writeToNetwork(@NotNull FriendlyByteBuf buf) {
        buf.writeFloat(this.size);
    }

        public @NotNull String writeToString() {
        return String.format(
                Locale.ROOT,
                "%s %.2f",
                BuiltInRegistries.PARTICLE_TYPE.getKey(getType()),
                this.size
        );
    }

    @Override
    public @NotNull ParticleType<RaevyxLightningStormData> getType() {
        return this.nightGold ? ModParticles.LIGHTNING_STORM_NIGHT_GOLD.get() : ModParticles.LIGHTNING_STORM.get();
    }
}
