package com.annis.npc.net;

import com.annis.npc.AnnisNpc;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Cliente -> servidor: lance un emote de Punchy Moves ("punchy:<animacion>"); el servidor lo reparte
 * para que los demas lo vean. ticks = largo (0 = se repite hasta que me mueva).
 */
public record EmoteSyncPayload(String anim, int ticks) implements CustomPayload {
    public static final CustomPayload.Id<EmoteSyncPayload> ID = new CustomPayload.Id<>(Identifier.of(AnnisNpc.MOD_ID, "emote_sync"));
    public static final PacketCodec<RegistryByteBuf, EmoteSyncPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, EmoteSyncPayload::anim,
            PacketCodecs.VAR_INT, EmoteSyncPayload::ticks,
            EmoteSyncPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
