package com.annis.npc.link;

import com.annis.npc.AnnisNpc;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Aspecto de NPC Annis (skin, modelo, accesorios...) puesto a otra entidad. Compuesto vacio = quitar. */
public record EntityLookPayload(int entityId, NbtCompound look) implements CustomPayload {
    public static final CustomPayload.Id<EntityLookPayload> ID = new CustomPayload.Id<>(Identifier.of(AnnisNpc.MOD_ID, "entity_look"));
    public static final PacketCodec<RegistryByteBuf, EntityLookPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, EntityLookPayload::entityId,
            PacketCodecs.NBT_COMPOUND, EntityLookPayload::look,
            EntityLookPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
