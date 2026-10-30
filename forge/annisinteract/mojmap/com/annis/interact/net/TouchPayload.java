package com.annis.interact.net;

import com.annis.interact.AnnisInteract;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Cliente -> servidor (modo mimos): zona que toca, punto actual y punto donde empezo a tocar
 * (en el modelo del que recibe, en pixeles), y si sigue tocando.
 */
public record TouchPayload(int zone, float x, float y, float z, float sx, float sy, float sz, boolean active)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TouchPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AnnisInteract.MOD_ID, "touch"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TouchPayload> CODEC = StreamCodec.ofMember(TouchPayload::write, TouchPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.zone);
        buf.writeFloat(this.x);
        buf.writeFloat(this.y);
        buf.writeFloat(this.z);
        buf.writeFloat(this.sx);
        buf.writeFloat(this.sy);
        buf.writeFloat(this.sz);
        buf.writeBoolean(this.active);
    }

    private static TouchPayload read(RegistryFriendlyByteBuf buf) {
        return new TouchPayload(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
