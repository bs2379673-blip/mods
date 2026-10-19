package com.tunombre.anomalias;

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
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(new ResourceLocation(Anomalias.MODID, "main"), () -> V, V::equals, V::equals);
    /** START: tipo + duracion (0 = fija, hasta /anomalia fin) + segundos ya transcurridos; STOP: se apaga con fundido; SETTINGS: a = destellos 0..1 */
    public static final byte START = 0, STOP = 1, SETTINGS = 2;

    private Net() { }

    static void init() {
        CH.registerMessage(0, Msg.class, Msg::write, Msg::read, Msg::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public record Msg(byte op, int tipo, float duration, float elapsed, float a) {
        void write(FriendlyByteBuf b) { b.writeByte(op); b.writeVarInt(tipo); b.writeFloat(duration); b.writeFloat(elapsed); b.writeFloat(a); }
        static Msg read(FriendlyByteBuf b) { return new Msg(b.readByte(), b.readVarInt(), b.readFloat(), b.readFloat(), b.readFloat()); }
        void handle(Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.anomalias.client.AnomaliaClient.receive(this)));
            c.get().setPacketHandled(true);
        }
    }

    public static void send(ServerPlayer p, Msg m) { CH.send(PacketDistributor.PLAYER.with(() -> p), m); }
    public static void sendAll(Msg m) { CH.send(PacketDistributor.ALL.noArg(), m); }
}
