package com.annis.npc.link;

import com.annis.npc.AnnisNpc;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import com.annis.npc.compat.ByteBufCodecs;
import com.annis.npc.compat.StreamCodec;
import com.annis.npc.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Aspecto de NPC Annis (skin, modelo, accesorios...) puesto a otra entidad. Compuesto vacio = quitar. */
public record EntityLookPayload(int entityId, CompoundTag look) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EntityLookPayload> ID = new CustomPacketPayload.Type<>(new ResourceLocation(AnnisNpc.MOD_ID, "entity_look"));
    public static final StreamCodec<FriendlyByteBuf, EntityLookPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EntityLookPayload::entityId,
            ByteBufCodecs.COMPOUND_TAG, EntityLookPayload::look,
            EntityLookPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
