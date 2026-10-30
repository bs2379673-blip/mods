package com.annis.npc.net;

import com.annis.npc.AnnisNpc;
import io.netty.buffer.ByteBuf;
import com.annis.npc.compat.ByteBufCodecs;
import com.annis.npc.compat.StreamCodec;
import com.annis.npc.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** El servidor le pide al cliente que abra la conversacion con un NPC. */
public record OpenDialogPayload(int entityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenDialogPayload> ID =
            new CustomPacketPayload.Type<>(new ResourceLocation(AnnisNpc.MOD_ID, "open_dialog"));

    public static final StreamCodec<ByteBuf, OpenDialogPayload> CODEC =
            ByteBufCodecs.VAR_INT.map(OpenDialogPayload::new, OpenDialogPayload::entityId);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
