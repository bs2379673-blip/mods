package com.annis.interact.net;

import com.annis.interact.AnnisInteract;
import net.minecraft.network.FriendlyByteBuf;
import com.annis.npc.compat.ByteBufCodecs;
import com.annis.npc.compat.StreamCodec;
import com.annis.npc.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Cliente -> servidor: respuesta a una invitacion. */
public record AnswerPayload(boolean accept) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AnswerPayload> ID = new CustomPacketPayload.Type<>(new ResourceLocation(AnnisInteract.MOD_ID, "answer"));
    public static final StreamCodec<FriendlyByteBuf, AnswerPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, AnswerPayload::accept,
            AnswerPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
