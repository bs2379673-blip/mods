package com.annis.npc.net;

import com.annis.npc.AnnisNpc;
import io.netty.buffer.ByteBuf;
import com.annis.npc.compat.ByteBufCodecs;
import com.annis.npc.compat.StreamCodec;
import com.annis.npc.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** El servidor le pide al cliente que abra el editor de un NPC. */
public record OpenEditorPayload(int entityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenEditorPayload> ID =
            new CustomPacketPayload.Type<>(new ResourceLocation(AnnisNpc.MOD_ID, "open_editor"));

    public static final StreamCodec<ByteBuf, OpenEditorPayload> CODEC =
            ByteBufCodecs.VAR_INT.map(OpenEditorPayload::new, OpenEditorPayload::entityId);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
