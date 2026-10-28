package com.annis.interact.net;

import com.annis.interact.AnnisInteract;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Cliente -> servidor (modo mimos): zona que toca, punto actual y punto donde empezo a tocar
 * (en el modelo del que recibe, en pixeles), y si sigue tocando.
 */
public record TouchPayload(int zone, float x, float y, float z, float sx, float sy, float sz, boolean active)
        implements CustomPayload {
    public static final CustomPayload.Id<TouchPayload> ID = new CustomPayload.Id<>(Identifier.of(AnnisInteract.MOD_ID, "touch"));
    public static final PacketCodec<RegistryByteBuf, TouchPayload> CODEC = PacketCodec.of(TouchPayload::write, TouchPayload::read);

    private void write(RegistryByteBuf buf) {
        buf.writeVarInt(this.zone);
        buf.writeFloat(this.x);
        buf.writeFloat(this.y);
        buf.writeFloat(this.z);
        buf.writeFloat(this.sx);
        buf.writeFloat(this.sy);
        buf.writeFloat(this.sz);
        buf.writeBoolean(this.active);
    }

    private static TouchPayload read(RegistryByteBuf buf) {
        return new TouchPayload(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
