package com.annis.interact.net;

import com.annis.interact.AnnisInteract;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Servidor -> cliente: fija hacia donde mira el cuerpo de una entidad durante una interaccion. */
public record AnchorPayload(int entityId, float yaw, boolean on) implements CustomPayload {
    public static final CustomPayload.Id<AnchorPayload> ID = new CustomPayload.Id<>(Identifier.of(AnnisInteract.MOD_ID, "anchor"));
    public static final PacketCodec<RegistryByteBuf, AnchorPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, AnchorPayload::entityId,
            PacketCodecs.FLOAT, AnchorPayload::yaw,
            PacketCodecs.BOOL, AnchorPayload::on,
            AnchorPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
