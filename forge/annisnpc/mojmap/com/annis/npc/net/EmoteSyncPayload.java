package com.annis.npc.net;

import com.annis.npc.AnnisNpc;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Cliente -> servidor: lance un emote de Punchy Moves ("punchy:<animacion>"); el servidor lo reparte
 * para que los demas lo vean. ticks = largo (0 = se repite hasta que me mueva).
 */
public record EmoteSyncPayload(String anim, int ticks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EmoteSyncPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AnnisNpc.MOD_ID, "emote_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EmoteSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, EmoteSyncPayload::anim,
            ByteBufCodecs.VAR_INT, EmoteSyncPayload::ticks,
            EmoteSyncPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
