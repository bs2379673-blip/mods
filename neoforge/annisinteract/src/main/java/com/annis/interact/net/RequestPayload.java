package com.annis.interact.net;

import com.annis.interact.AnnisInteract;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Cliente -> servidor: quiero hacer esta interaccion con esta entidad. */
public record RequestPayload(String interaction, int targetId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AnnisInteract.MOD_ID, "request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RequestPayload::interaction,
            ByteBufCodecs.VAR_INT, RequestPayload::targetId,
            RequestPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
