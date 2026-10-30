package com.annis.interact.net;

import com.annis.interact.AnnisInteract;
import net.minecraft.network.FriendlyByteBuf;
import com.annis.npc.compat.ByteBufCodecs;
import com.annis.npc.compat.StreamCodec;
import com.annis.npc.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> cliente: estas caido (segundos restantes) o ya no. */
public record DownedPayload(int seconds, boolean on) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DownedPayload> ID = new CustomPacketPayload.Type<>(new ResourceLocation(AnnisInteract.MOD_ID, "downed"));
    public static final StreamCodec<FriendlyByteBuf, DownedPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DownedPayload::seconds,
            ByteBufCodecs.BOOL, DownedPayload::on,
            DownedPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
