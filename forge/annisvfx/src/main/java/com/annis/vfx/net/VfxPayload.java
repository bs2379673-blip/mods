package com.annis.vfx.net;

import com.annis.vfx.AnnisVfx;
import net.minecraft.network.FriendlyByteBuf;
import com.annis.vfx.compat.StreamCodec;
import com.annis.vfx.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Servidor -> clientes: empieza un efecto. effect = nombre ("arbol", "hojas", "brillo", "flores",
 * "parar"); x,y,z = donde; start = tick del mundo en que empieza (reloj comun: todos lo ven igual);
 * seed = semilla del azar (las mismas hojas para todos); entity = a quien hace aparecer (-1 = nadie);
 * a, b = parametros del efecto (cantidad, radio, segundos...).
 */
public record VfxPayload(String effect, double x, double y, double z, long start, int seed, int entity, float a, float b)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<VfxPayload> ID = new CustomPacketPayload.Type<>(new ResourceLocation(AnnisVfx.MOD_ID, "play"));
    public static final StreamCodec<FriendlyByteBuf, VfxPayload> CODEC = StreamCodec.ofMember(VfxPayload::write, VfxPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.effect);
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeLong(this.start);
        buf.writeInt(this.seed);
        buf.writeVarInt(this.entity);
        buf.writeFloat(this.a);
        buf.writeFloat(this.b);
    }

    private static VfxPayload read(FriendlyByteBuf buf) {
        return new VfxPayload(buf.readUtf(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readLong(),
                buf.readInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
