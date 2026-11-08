package com.leon.saintsdragons.common.particle;

import com.leon.saintsdragons.common.registry.ModParticles;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public record FireBreathBurstData(int dragonId) implements com.leon.saintsdragons.util.compat.LegacyParticleOptions {
    public static final Codec<FireBreathBurstData> CODEC = Codec.INT.fieldOf("dragon_id")
            .xmap(FireBreathBurstData::new, FireBreathBurstData::dragonId).codec();
    public static final com.leon.saintsdragons.util.compat.ParticleDeserializer<FireBreathBurstData> DESERIALIZER = new com.leon.saintsdragons.util.compat.ParticleDeserializer<>() {
        @Override
        public @NotNull FireBreathBurstData fromCommand(@NotNull ParticleType<FireBreathBurstData> type,
                                                        @NotNull StringReader reader) throws CommandSyntaxException {
            reader.expect(' ');
            return new FireBreathBurstData(reader.readInt());
        }

        @Override
        public @NotNull FireBreathBurstData fromNetwork(@NotNull ParticleType<FireBreathBurstData> type,
                                                        @NotNull FriendlyByteBuf buffer) {
            return new FireBreathBurstData(buffer.readVarInt());
        }
    };

    @Override
    public @NotNull ParticleType<FireBreathBurstData> getType() {
        return ModParticles.FIRE_BREATH_BURST.get();
    }

        public void writeToNetwork(@NotNull FriendlyByteBuf buffer) {
        buffer.writeVarInt(dragonId);
    }

        public @NotNull String writeToString() {
        return "saintsdragons:fire_breath_burst " + dragonId;
    }
}
