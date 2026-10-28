package com.annis.npc.net;

import com.annis.npc.AnnisNpc;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** El servidor le pide al cliente que abra la conversacion con un NPC. */
public record OpenDialogPayload(int entityId) implements CustomPayload {
    public static final CustomPayload.Id<OpenDialogPayload> ID =
            new CustomPayload.Id<>(Identifier.of(AnnisNpc.MOD_ID, "open_dialog"));

    public static final PacketCodec<ByteBuf, OpenDialogPayload> CODEC =
            PacketCodecs.VAR_INT.xmap(OpenDialogPayload::new, OpenDialogPayload::entityId);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
