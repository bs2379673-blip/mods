package com.annis.vfx.net;

import com.annis.vfx.AnnisVfx;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Servidor -> clientes: empieza un efecto. effect = nombre ("arbol", "hojas", "brillo", "flores",
 * "parar"); x,y,z = donde; start = tick del mundo en que empieza (reloj comun: todos lo ven igual);
 * seed = semilla del azar (las mismas hojas para todos); entity = a quien hace aparecer (-1 = nadie);
 * a, b = parametros del efecto (cantidad, radio, segundos...).
 */
public record VfxPayload(String effect, double x, double y, double z, long start, int seed, int entity, float a, float b)
        implements CustomPayload {
    public static final CustomPayload.Id<VfxPayload> ID = new CustomPayload.Id<>(Identifier.of(AnnisVfx.MOD_ID, "play"));
    public static final PacketCodec<RegistryByteBuf, VfxPayload> CODEC = PacketCodec.of(VfxPayload::write, VfxPayload::read);

    private void write(RegistryByteBuf buf) {
        buf.writeString(this.effect);
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeLong(this.start);
        buf.writeInt(this.seed);
        buf.writeVarInt(this.entity);
        buf.writeFloat(this.a);
        buf.writeFloat(this.b);
    }

    private static VfxPayload read(RegistryByteBuf buf) {
        return new VfxPayload(buf.readString(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readLong(),
                buf.readInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
