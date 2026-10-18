package com.tunombre.concierto;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

public final class Net {
    private static final String V = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(new ResourceLocation(Concierto.MODID, "main"), () -> V, V::equals, V::equals);

    public static final byte PLAY = 0, STOP = 1, SCENE = 2, PARAMS = 3, STAGE = 4, LIGHT = 5, FX = 6;

    private Net() { }

    static void init() {
        CH.registerMessage(0, Msg.class, Msg::write, Msg::read, Msg::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /**
     * una sola orden servidor -> cliente.
     * PLAY: song + scene (-1 = segun el guion) + offset (segundos ya transcurridos); SCENE: scene; PARAMS: a = intensidad, b = estrobo;
     * STAGE: x,y,z del escenario, a = giro (grados), b = escala (0 = sin escenario), arr = medidas;
     * LIGHT: x,y,z, scene = color 0xRRGGBB, a = tamano, b = segundos; FX: song = nombre del efecto
     */
    public record Msg(byte op, String song, int scene, float a, float b, float offset, double x, double y, double z, float[] arr) {
        public Msg(byte op, String song, int scene, float a, float b, float offset) { this(op, song, scene, a, b, offset, 0, 0, 0, new float[0]); }

        void write(FriendlyByteBuf buf) {
            buf.writeByte(op);
            buf.writeUtf(song, 64);
            buf.writeInt(scene);
            buf.writeFloat(a);
            buf.writeFloat(b);
            buf.writeFloat(offset);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeVarInt(arr.length);
            for (float f : arr) buf.writeFloat(f);
        }

        static Msg read(FriendlyByteBuf buf) {
            byte op = buf.readByte();
            String song = buf.readUtf(64);
            int scene = buf.readInt();
            float a = buf.readFloat(), b = buf.readFloat(), offset = buf.readFloat();
            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
            int n = Math.min(64, buf.readVarInt());
            float[] arr = new float[n];
            for (int i = 0; i < n; i++) arr[i] = buf.readFloat();
            return new Msg(op, song, scene, a, b, offset, x, y, z, arr);
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.concierto.client.ConciertoClient.receive(this)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static void send(ServerPlayer sp, Msg m) { CH.send(PacketDistributor.PLAYER.with(() -> sp), m); }

    public static void sendAll(Msg m) { CH.send(PacketDistributor.ALL.noArg(), m); }
}
