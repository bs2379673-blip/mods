package com.leon.saintsdragons.util.compat;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.FriendlyByteBuf;

/** Port 1.21.11: particulas con datos que se mandaban por red con writeToNetwork. */
public interface LegacyParticleOptions extends ParticleOptions {
    void writeToNetwork(FriendlyByteBuf buf);
}
