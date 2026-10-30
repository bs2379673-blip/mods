package com.annis.interact.net;

import com.annis.interact.AnnisInteract;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Servidor -> clientes (modo mimos): quien toca (toucher) a quien (receiver, -1 = se termino),
 * zona, punto actual y punto inicial en el modelo del que recibe, y si esta tocando.
 */
public record TouchSyncPayload(int toucher, int receiver, int zone, float x, float y, float z,
                               float sx, float sy, float sz, boolean active) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TouchSyncPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AnnisInteract.MOD_ID, "touch_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TouchSyncPayload> CODEC = StreamCodec.ofMember(TouchSyncPayload::write, TouchSyncPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.toucher);
        buf.writeVarInt(this.receiver);
        buf.writeVarInt(this.zone);
        buf.writeFloat(this.x);
        buf.writeFloat(this.y);
        buf.writeFloat(this.z);
        buf.writeFloat(this.sx);
        buf.writeFloat(this.sy);
        buf.writeFloat(this.sz);
        buf.writeBoolean(this.active);
    }

    private static TouchSyncPayload read(RegistryFriendlyByteBuf buf) {
        return new TouchSyncPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
