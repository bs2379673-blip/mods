package com.annis.interact.net;

import com.annis.interact.AnnisInteract;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> cliente: estas caido (segundos restantes) o ya no. */
public record DownedPayload(int seconds, boolean on) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DownedPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AnnisInteract.MOD_ID, "downed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DownedPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DownedPayload::seconds,
            ByteBufCodecs.BOOL, DownedPayload::on,
            DownedPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
