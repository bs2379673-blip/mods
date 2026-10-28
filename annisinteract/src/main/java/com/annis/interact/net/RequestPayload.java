package com.annis.interact.net;

import com.annis.interact.AnnisInteract;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Cliente -> servidor: quiero hacer esta interaccion con esta entidad. */
public record RequestPayload(String interaction, int targetId) implements CustomPayload {
    public static final CustomPayload.Id<RequestPayload> ID = new CustomPayload.Id<>(Identifier.of(AnnisInteract.MOD_ID, "request"));
    public static final PacketCodec<RegistryByteBuf, RequestPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, RequestPayload::interaction,
            PacketCodecs.VAR_INT, RequestPayload::targetId,
            RequestPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
