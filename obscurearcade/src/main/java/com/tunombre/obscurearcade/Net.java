package com.tunombre.obscurearcade;

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

/** Dos mensajes genericos (tipo + texto): servidor a cliente (abrir config, avisos, estado de duelo) y cliente a servidor (config y notas). */
public final class Net {
    private static final String V = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(new ResourceLocation(ObscureArcade.MODID, "main"), () -> V, V::equals, V::equals);

    private Net() { }

    static void init() {
        CH.registerMessage(0, ToClient.class, ToClient::write, ToClient::read, ToClient::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH.registerMessage(1, ToServer.class, ToServer::write, ToServer::read, ToServer::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public record ToClient(String kind, String json) {
        void write(FriendlyByteBuf b) { b.writeUtf(kind, 64); b.writeUtf(json, 30000); }

        static ToClient read(FriendlyByteBuf b) { return new ToClient(b.readUtf(64), b.readUtf(30000)); }

        void handle(Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.obscurearcade.client.ClientNet.handle(kind, json)));
            c.get().setPacketHandled(true);
        }
    }

    public record ToServer(String kind, String json) {
        void write(FriendlyByteBuf b) { b.writeUtf(kind, 64); b.writeUtf(json, 30000); }

        static ToServer read(FriendlyByteBuf b) { return new ToServer(b.readUtf(64), b.readUtf(30000)); }

        void handle(Supplier<NetworkEvent.Context> c) {
            ServerPlayer p = c.get().getSender();
            c.get().enqueueWork(() -> { if (p != null) Duels.onPacket(p, kind, json); });
            c.get().setPacketHandled(true);
        }
    }

    public static void toClient(ServerPlayer p, String kind, String json) { CH.send(PacketDistributor.PLAYER.with(() -> p), new ToClient(kind, json)); }
    public static void sendToServer(String kind, String json) { CH.sendToServer(new ToServer(kind, json)); }
}
