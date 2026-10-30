package com.annis.interact.net;

import com.annis.interact.AnnisInteract;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> cliente: estas en una interaccion que se termina con Shift (texto vacio = ninguna). */
public record SessionPayload(String interaction) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SessionPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AnnisInteract.MOD_ID, "session"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SessionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SessionPayload::interaction,
            SessionPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
