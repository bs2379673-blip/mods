package com.annis.interact.net;

import com.annis.interact.AnnisInteract;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Cliente -> servidor: respuesta a una invitacion. */
public record AnswerPayload(boolean accept) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AnswerPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AnnisInteract.MOD_ID, "answer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AnswerPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, AnswerPayload::accept,
            AnswerPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
