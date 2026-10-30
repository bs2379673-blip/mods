package com.annis.interact.net;

import com.annis.interact.AnnisInteract;
import net.minecraft.network.FriendlyByteBuf;
import com.annis.npc.compat.ByteBufCodecs;
import com.annis.npc.compat.StreamCodec;
import com.annis.npc.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> cliente: invitacion pendiente (seconds 0 = ya no hay). */
public record InvitePayload(String from, String interaction, int seconds) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<InvitePayload> ID = new CustomPacketPayload.Type<>(new ResourceLocation(AnnisInteract.MOD_ID, "invite"));
    public static final StreamCodec<FriendlyByteBuf, InvitePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, InvitePayload::from,
            ByteBufCodecs.STRING_UTF8, InvitePayload::interaction,
            ByteBufCodecs.VAR_INT, InvitePayload::seconds,
            InvitePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
