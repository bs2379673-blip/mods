package com.annis.interact.net;

import com.annis.interact.AnnisInteract;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Servidor -> clientes (modo mimos): quien toca (toucher) a quien (receiver, -1 = se termino),
 * zona, punto actual y punto inicial en el modelo del que recibe, y si esta tocando.
 */
public record TouchSyncPayload(int toucher, int receiver, int zone, float x, float y, float z,
                               float sx, float sy, float sz, boolean active) implements CustomPayload {
    public static final CustomPayload.Id<TouchSyncPayload> ID = new CustomPayload.Id<>(Identifier.of(AnnisInteract.MOD_ID, "touch_sync"));
    public static final PacketCodec<RegistryByteBuf, TouchSyncPayload> CODEC = PacketCodec.of(TouchSyncPayload::write, TouchSyncPayload::read);

    private void write(RegistryByteBuf buf) {
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

    private static TouchSyncPayload read(RegistryByteBuf buf) {
        return new TouchSyncPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
