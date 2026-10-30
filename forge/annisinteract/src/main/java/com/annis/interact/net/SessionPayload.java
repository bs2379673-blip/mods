package com.annis.interact.net;

import com.annis.interact.AnnisInteract;
import net.minecraft.network.FriendlyByteBuf;
import com.annis.npc.compat.ByteBufCodecs;
import com.annis.npc.compat.StreamCodec;
import com.annis.npc.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> cliente: estas en una interaccion que se termina con Shift (texto vacio = ninguna). */
public record SessionPayload(String interaction) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SessionPayload> ID = new CustomPacketPayload.Type<>(new ResourceLocation(AnnisInteract.MOD_ID, "session"));
    public static final StreamCodec<FriendlyByteBuf, SessionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SessionPayload::interaction,
            SessionPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
