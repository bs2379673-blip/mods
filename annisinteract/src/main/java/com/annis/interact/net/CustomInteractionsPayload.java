package com.annis.interact.net;

import com.annis.interact.AnnisInteract;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Servidor -> cliente: las interacciones creadas por archivo (el JSON tal cual). */
public record CustomInteractionsPayload(String json) implements CustomPayload {
    public static final CustomPayload.Id<CustomInteractionsPayload> ID = new CustomPayload.Id<>(Identifier.of(AnnisInteract.MOD_ID, "custom_interactions"));
    public static final PacketCodec<RegistryByteBuf, CustomInteractionsPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(262144), CustomInteractionsPayload::json,
            CustomInteractionsPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
