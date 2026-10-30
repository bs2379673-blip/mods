package com.annis.npc.link;

import com.annis.npc.AnnisNpc;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Aspecto de NPC Annis (skin, modelo, accesorios...) puesto a otra entidad. Compuesto vacio = quitar. */
public record EntityLookPayload(int entityId, CompoundTag look) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EntityLookPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AnnisNpc.MOD_ID, "entity_look"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EntityLookPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EntityLookPayload::entityId,
            ByteBufCodecs.COMPOUND_TAG, EntityLookPayload::look,
            EntityLookPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
