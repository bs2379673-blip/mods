package com.annis.npc.link;

import com.annis.npc.AnnisNpc;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Animacion (emote / actividad / movimiento) de cualquier entidad, con su tick de inicio. */
public record EntityAnimPayload(int entityId, String name, int start, int serial) implements CustomPayload {
    public static final CustomPayload.Id<EntityAnimPayload> ID = new CustomPayload.Id<>(Identifier.of(AnnisNpc.MOD_ID, "entity_anim"));
    public static final PacketCodec<RegistryByteBuf, EntityAnimPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, EntityAnimPayload::entityId,
            PacketCodecs.STRING, EntityAnimPayload::name,
            PacketCodecs.VAR_INT, EntityAnimPayload::start,
            PacketCodecs.VAR_INT, EntityAnimPayload::serial,
            EntityAnimPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
