package com.annis.vfx.compat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class PayloadTypeRegistry {
    private static final PayloadTypeRegistry C2S = new PayloadTypeRegistry(true);
    private static final PayloadTypeRegistry S2C = new PayloadTypeRegistry(false);
    private final boolean toServer;

    private PayloadTypeRegistry(boolean toServer) {
        this.toServer = toServer;
    }

    public static PayloadTypeRegistry playC2S() {
        return C2S;
    }

    public static PayloadTypeRegistry playS2C() {
        return S2C;
    }

    public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        Net.register(type, codec, this.toServer);
    }
}
