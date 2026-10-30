package com.annis.interact.net;

import com.annis.interact.AnnisInteract;
import net.minecraft.network.FriendlyByteBuf;
import com.annis.npc.compat.ByteBufCodecs;
import com.annis.npc.compat.StreamCodec;
import com.annis.npc.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> cliente: las interacciones creadas por archivo (el JSON tal cual). */
public record CustomInteractionsPayload(String json) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CustomInteractionsPayload> ID = new CustomPacketPayload.Type<>(new ResourceLocation(AnnisInteract.MOD_ID, "custom_interactions"));
    public static final StreamCodec<FriendlyByteBuf, CustomInteractionsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(262144), CustomInteractionsPayload::json,
            CustomInteractionsPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
