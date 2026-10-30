package com.annis.interact.net;

import com.annis.interact.AnnisInteract;
import net.minecraft.network.FriendlyByteBuf;
import com.annis.npc.compat.StreamCodec;
import com.annis.npc.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Cliente -> servidor (modo mimos): zona que toca, punto actual y punto donde empezo a tocar
 * (en el modelo del que recibe, en pixeles), y si sigue tocando.
 */
public record TouchPayload(int zone, float x, float y, float z, float sx, float sy, float sz, boolean active)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TouchPayload> ID = new CustomPacketPayload.Type<>(new ResourceLocation(AnnisInteract.MOD_ID, "touch"));
    public static final StreamCodec<FriendlyByteBuf, TouchPayload> CODEC = StreamCodec.ofMember(TouchPayload::write, TouchPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.zone);
        buf.writeFloat(this.x);
        buf.writeFloat(this.y);
        buf.writeFloat(this.z);
        buf.writeFloat(this.sx);
        buf.writeFloat(this.sy);
        buf.writeFloat(this.sz);
        buf.writeBoolean(this.active);
    }

    private static TouchPayload read(FriendlyByteBuf buf) {
        return new TouchPayload(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
