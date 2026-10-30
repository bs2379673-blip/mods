package com.annis.npc.link;

import com.annis.npc.AnnisNpc;
import net.minecraft.network.FriendlyByteBuf;
import com.annis.npc.compat.ByteBufCodecs;
import com.annis.npc.compat.StreamCodec;
import com.annis.npc.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Animacion (emote / actividad / movimiento) de cualquier entidad, con su tick de inicio. */
public record EntityAnimPayload(int entityId, String name, int start, int serial) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EntityAnimPayload> ID = new CustomPacketPayload.Type<>(new ResourceLocation(AnnisNpc.MOD_ID, "entity_anim"));
    public static final StreamCodec<FriendlyByteBuf, EntityAnimPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EntityAnimPayload::entityId,
            ByteBufCodecs.STRING_UTF8, EntityAnimPayload::name,
            ByteBufCodecs.VAR_INT, EntityAnimPayload::start,
            ByteBufCodecs.VAR_INT, EntityAnimPayload::serial,
            EntityAnimPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
