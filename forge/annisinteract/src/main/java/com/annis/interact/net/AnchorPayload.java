package com.annis.interact.net;

import com.annis.interact.AnnisInteract;
import net.minecraft.network.FriendlyByteBuf;
import com.annis.npc.compat.ByteBufCodecs;
import com.annis.npc.compat.StreamCodec;
import com.annis.npc.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> cliente: fija hacia donde mira el cuerpo de una entidad durante una interaccion. */
public record AnchorPayload(int entityId, float yaw, boolean on) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AnchorPayload> ID = new CustomPacketPayload.Type<>(new ResourceLocation(AnnisInteract.MOD_ID, "anchor"));
    public static final StreamCodec<FriendlyByteBuf, AnchorPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AnchorPayload::entityId,
            ByteBufCodecs.FLOAT, AnchorPayload::yaw,
            ByteBufCodecs.BOOL, AnchorPayload::on,
            AnchorPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
