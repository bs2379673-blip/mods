package com.annis.npc.link;

import com.annis.npc.AnnisNpc;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Animacion (emote / actividad / movimiento) de cualquier entidad, con su tick de inicio. */
public record EntityAnimPayload(int entityId, String name, int start, int serial) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EntityAnimPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AnnisNpc.MOD_ID, "entity_anim"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EntityAnimPayload> CODEC = StreamCodec.composite(
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
