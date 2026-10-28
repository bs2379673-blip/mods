package com.annis.interact.net;

import com.annis.interact.AnnisInteract;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Servidor -> cliente: estas caido (segundos restantes) o ya no. */
public record DownedPayload(int seconds, boolean on) implements CustomPayload {
    public static final CustomPayload.Id<DownedPayload> ID = new CustomPayload.Id<>(Identifier.of(AnnisInteract.MOD_ID, "downed"));
    public static final PacketCodec<RegistryByteBuf, DownedPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, DownedPayload::seconds,
            PacketCodecs.BOOL, DownedPayload::on,
            DownedPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
