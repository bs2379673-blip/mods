package com.annis.npc.net;

import com.annis.npc.AnnisNpc;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** El servidor le pide al cliente que abra el editor de un NPC. */
public record OpenEditorPayload(int entityId) implements CustomPayload {
    public static final CustomPayload.Id<OpenEditorPayload> ID =
            new CustomPayload.Id<>(Identifier.of(AnnisNpc.MOD_ID, "open_editor"));

    public static final PacketCodec<ByteBuf, OpenEditorPayload> CODEC =
            PacketCodecs.VAR_INT.xmap(OpenEditorPayload::new, OpenEditorPayload::entityId);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
