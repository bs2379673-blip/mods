package com.annis.interact.net;

import com.annis.interact.AnnisInteract;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Cliente -> servidor: respuesta a una invitacion. */
public record AnswerPayload(boolean accept) implements CustomPayload {
    public static final CustomPayload.Id<AnswerPayload> ID = new CustomPayload.Id<>(Identifier.of(AnnisInteract.MOD_ID, "answer"));
    public static final PacketCodec<RegistryByteBuf, AnswerPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, AnswerPayload::accept,
            AnswerPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
