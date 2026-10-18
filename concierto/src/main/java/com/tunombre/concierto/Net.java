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

    public static final byte PLAY = 0, STOP = 1, SCENE = 2, PARAMS = 3;

    private Net() { }

    static void init() {
        CH.registerMessage(0, Msg.class, Msg::write, Msg::read, Msg::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** una sola orden servidor -> cliente. PLAY: cancion + escena (-1 = automatico) + segundos ya transcurridos; SCENE: escena; PARAMS: a = intensidad, b = estrobo */
    public record Msg(byte op, String song, int scene, float a, float b, float offset) {
        void write(FriendlyByteBuf buf) {
            buf.writeByte(op);
            buf.writeUtf(song, 64);
            buf.writeInt(scene);
            buf.writeFloat(a);
            buf.writeFloat(b);
            buf.writeFloat(offset);
        }

        static Msg read(FriendlyByteBuf buf) {
            return new Msg(buf.readByte(), buf.readUtf(64), buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readFloat());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.concierto.client.ConciertoClient.receive(this)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static void send(ServerPlayer sp, Msg m) { CH.send(PacketDistributor.PLAYER.with(() -> sp), m); }

    public static void sendAll(Msg m) { CH.send(PacketDistributor.ALL.noArg(), m); }
}
