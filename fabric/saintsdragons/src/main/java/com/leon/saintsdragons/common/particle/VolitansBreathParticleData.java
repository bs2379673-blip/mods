package com.leon.saintsdragons.common.particle;

import com.leon.saintsdragons.common.registry.ModParticles;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public record VolitansBreathParticleData(int dragonId) implements com.leon.saintsdragons.util.compat.LegacyParticleOptions {
    public static final Codec<VolitansBreathParticleData> CODEC = Codec.INT.fieldOf("dragon_id")
            .xmap(VolitansBreathParticleData::new, VolitansBreathParticleData::dragonId).codec();
    public static final com.leon.saintsdragons.util.compat.ParticleDeserializer<VolitansBreathParticleData> DESERIALIZER = new com.leon.saintsdragons.util.compat.ParticleDeserializer<>() {
        @Override
        public @NotNull VolitansBreathParticleData fromCommand(@NotNull ParticleType<VolitansBreathParticleData> type,
                                                               @NotNull StringReader reader) throws CommandSyntaxException {
            reader.expect(' ');
            return new VolitansBreathParticleData(reader.readInt());
        }

        @Override
        public @NotNull VolitansBreathParticleData fromNetwork(@NotNull ParticleType<VolitansBreathParticleData> type,
                                                               @NotNull FriendlyByteBuf buffer) {
            return new VolitansBreathParticleData(buffer.readVarInt());
        }
    };

    @Override
    public @NotNull ParticleType<VolitansBreathParticleData> getType() {
        return ModParticles.VOLITANS_BREATH_STREAM.get();
    }

        public void writeToNetwork(@NotNull FriendlyByteBuf buffer) {
        buffer.writeVarInt(dragonId);
    }

        public @NotNull String writeToString() {
        return "saintsdragons:volitans_breath_stream " + dragonId;
    }
}
