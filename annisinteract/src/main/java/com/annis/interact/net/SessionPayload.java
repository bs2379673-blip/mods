package com.annis.interact.net;

import com.annis.interact.AnnisInteract;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Servidor -> cliente: estas en una interaccion que se termina con Shift (texto vacio = ninguna). */
public record SessionPayload(String interaction) implements CustomPayload {
    public static final CustomPayload.Id<SessionPayload> ID = new CustomPayload.Id<>(Identifier.of(AnnisInteract.MOD_ID, "session"));
    public static final PacketCodec<RegistryByteBuf, SessionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, SessionPayload::interaction,
            SessionPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
