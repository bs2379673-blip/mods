package com.annis.interact.net;

import com.annis.interact.AnnisInteract;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Servidor -> cliente: invitacion pendiente (seconds 0 = ya no hay). */
public record InvitePayload(String from, String interaction, int seconds) implements CustomPayload {
    public static final CustomPayload.Id<InvitePayload> ID = new CustomPayload.Id<>(Identifier.of(AnnisInteract.MOD_ID, "invite"));
    public static final PacketCodec<RegistryByteBuf, InvitePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, InvitePayload::from,
            PacketCodecs.STRING, InvitePayload::interaction,
            PacketCodecs.VAR_INT, InvitePayload::seconds,
            InvitePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
