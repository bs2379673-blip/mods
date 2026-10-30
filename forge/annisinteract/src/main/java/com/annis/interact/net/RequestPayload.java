package com.annis.interact.net;

import com.annis.interact.AnnisInteract;
import net.minecraft.network.FriendlyByteBuf;
import com.annis.npc.compat.ByteBufCodecs;
import com.annis.npc.compat.StreamCodec;
import com.annis.npc.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Cliente -> servidor: quiero hacer esta interaccion con esta entidad. */
public record RequestPayload(String interaction, int targetId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestPayload> ID = new CustomPacketPayload.Type<>(new ResourceLocation(AnnisInteract.MOD_ID, "request"));
    public static final StreamCodec<FriendlyByteBuf, RequestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RequestPayload::interaction,
            ByteBufCodecs.VAR_INT, RequestPayload::targetId,
            RequestPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
