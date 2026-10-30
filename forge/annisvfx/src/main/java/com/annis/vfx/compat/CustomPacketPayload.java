package com.annis.vfx.compat;

import net.minecraft.resources.ResourceLocation;

/** Como el CustomPacketPayload de 1.21: un paquete con un id. */
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    record Type<T extends CustomPacketPayload>(ResourceLocation id) {
    }
}
