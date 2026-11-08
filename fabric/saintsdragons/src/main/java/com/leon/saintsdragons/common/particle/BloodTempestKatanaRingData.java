package com.leon.saintsdragons.common.particle;

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

public record BloodTempestKatanaRingData(float yaw, float pitch, float scale, int duration)
        implements com.leon.saintsdragons.util.compat.LegacyParticleOptions {
    public static final com.leon.saintsdragons.util.compat.ParticleDeserializer<BloodTempestKatanaRingData> DESERIALIZER =
            new com.leon.saintsdragons.util.compat.ParticleDeserializer<>() {
                @Override
                public @NotNull BloodTempestKatanaRingData fromCommand(
                        @NotNull ParticleType<BloodTempestKatanaRingData> type,
                        @NotNull StringReader reader) throws CommandSyntaxException {
                    reader.expect(' ');
                    float yaw = reader.readFloat();
                    reader.expect(' ');
                    float pitch = reader.readFloat();
                    reader.expect(' ');
                    float scale = reader.readFloat();
                    reader.expect(' ');
                    int duration = reader.readInt();
                    return new BloodTempestKatanaRingData(yaw, pitch, scale, duration);
                }

                @Override
                public @NotNull BloodTempestKatanaRingData fromNetwork(
                        @NotNull ParticleType<BloodTempestKatanaRingData> type,
                        @NotNull FriendlyByteBuf buffer) {
                    return new BloodTempestKatanaRingData(
                            buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readInt());
                }
            };

    public static Codec<BloodTempestKatanaRingData> codec(
            @SuppressWarnings("unused") ParticleType<BloodTempestKatanaRingData> type) {
        return RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.fieldOf("yaw").forGetter(BloodTempestKatanaRingData::yaw),
                Codec.FLOAT.fieldOf("pitch").forGetter(BloodTempestKatanaRingData::pitch),
                Codec.FLOAT.fieldOf("scale").forGetter(BloodTempestKatanaRingData::scale),
                Codec.INT.fieldOf("duration").forGetter(BloodTempestKatanaRingData::duration)
        ).apply(instance, BloodTempestKatanaRingData::new));
    }

        public void writeToNetwork(@NotNull FriendlyByteBuf buffer) {
        buffer.writeFloat(this.yaw);
        buffer.writeFloat(this.pitch);
        buffer.writeFloat(this.scale);
        buffer.writeInt(this.duration);
    }

        public @NotNull String writeToString() {
        return String.format(
                Locale.ROOT,
                "%s %.2f %.2f %.2f %d",
                BuiltInRegistries.PARTICLE_TYPE.getKey(getType()),
                this.yaw,
                this.pitch,
                this.scale,
                this.duration
        );
    }

    @Override
    public @NotNull ParticleType<BloodTempestKatanaRingData> getType() {
        return ModParticles.BLOOD_TEMPEST_SWORD_RING.get();
    }
}
