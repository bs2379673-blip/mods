package com.leon.saintsdragons.util.compat;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

/** Port 1.21.11: el ParticleOptions.Deserializer de 1.20.1 (el juego ahora usa codecs). */
public interface ParticleDeserializer<T extends ParticleOptions> {
    T fromCommand(ParticleType<T> type, StringReader reader) throws CommandSyntaxException;

    T fromNetwork(ParticleType<T> type, FriendlyByteBuf buf);
}
