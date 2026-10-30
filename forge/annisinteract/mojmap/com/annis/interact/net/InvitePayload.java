package com.annis.interact.net;

import com.annis.interact.AnnisInteract;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> cliente: invitacion pendiente (seconds 0 = ya no hay). */
public record InvitePayload(String from, String interaction, int seconds) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<InvitePayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AnnisInteract.MOD_ID, "invite"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InvitePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, InvitePayload::from,
            ByteBufCodecs.STRING_UTF8, InvitePayload::interaction,
            ByteBufCodecs.VAR_INT, InvitePayload::seconds,
            InvitePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
