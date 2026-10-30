package com.annis.npc.compat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Todos los paquetes de un mod van por un canal de Forge ("<mod>:main") como un unico mensaje
 * que lleva el id del paquete y sus datos. Los dos lados se aceptan aunque al otro le falte el mod.
 */
public final class Net {
    private static final String VERSION = "1";
    private static final Map<String, SimpleChannel> CHANNELS = new HashMap<>();
    private static final Map<ResourceLocation, StreamCodec<? super FriendlyByteBuf, ? extends CustomPacketPayload>> CODECS = new HashMap<>();
    private static final Map<ResourceLocation, BiConsumer<CustomPacketPayload, NetworkEvent.Context>> SERVER = new HashMap<>();
    private static final Map<ResourceLocation, BiConsumer<CustomPacketPayload, NetworkEvent.Context>> CLIENT = new HashMap<>();

    private Net() {
    }

    /** Lo que viaja: id + datos. */
    record Packet(ResourceLocation id, CustomPacketPayload payload) {
    }

    @SuppressWarnings("unchecked")
    private static void write(Packet p, FriendlyByteBuf buf) {
        buf.writeResourceLocation(p.id());
        StreamCodec<? super FriendlyByteBuf, CustomPacketPayload> c =
                (StreamCodec<? super FriendlyByteBuf, CustomPacketPayload>) CODECS.get(p.id());
        c.encode(buf, p.payload());
    }

    private static Packet read(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        StreamCodec<? super FriendlyByteBuf, ? extends CustomPacketPayload> c = CODECS.get(id);
        if (c == null) {
            buf.skipBytes(buf.readableBytes());
            return new Packet(id, null);
        }
        return new Packet(id, c.decode(buf));
    }

    private static void handle(Packet p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.setPacketHandled(true);
        if (p.payload() == null) {
            return;
        }
        boolean toServer = ctx.getDirection().getReceptionSide().isServer();
        BiConsumer<CustomPacketPayload, NetworkEvent.Context> h = (toServer ? SERVER : CLIENT).get(p.id());
        if (h != null) {
            ctx.enqueueWork(() -> h.accept(p.payload(), ctx));
        }
    }

    static synchronized SimpleChannel channel(String namespace) {
        return CHANNELS.computeIfAbsent(namespace, ns -> {
            SimpleChannel ch = NetworkRegistry.ChannelBuilder.named(new ResourceLocation(ns, "main"))
                    .networkProtocolVersion(() -> VERSION)
                    .clientAcceptedVersions(NetworkRegistry.acceptMissingOr(VERSION))
                    .serverAcceptedVersions(NetworkRegistry.acceptMissingOr(VERSION))
                    .simpleChannel();
            ch.messageBuilder(Packet.class, 0).encoder(Net::write).decoder(Net::read).consumerNetworkThread(Net::handle).add();
            return ch;
        });
    }

    public static <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec) {
        CODECS.put(type.id(), codec);
        channel(type.id().getNamespace());
    }

    @SuppressWarnings("unchecked")
    static <T extends CustomPacketPayload> void onServer(CustomPacketPayload.Type<T> type, BiConsumer<T, NetworkEvent.Context> h) {
        channel(type.id().getNamespace());
        SERVER.put(type.id(), (BiConsumer<CustomPacketPayload, NetworkEvent.Context>) (BiConsumer<?, ?>) h);
    }

    @SuppressWarnings("unchecked")
    static <T extends CustomPacketPayload> void onClient(CustomPacketPayload.Type<T> type, BiConsumer<T, NetworkEvent.Context> h) {
        channel(type.id().getNamespace());
        CLIENT.put(type.id(), (BiConsumer<CustomPacketPayload, NetworkEvent.Context>) (BiConsumer<?, ?>) h);
    }

    static Packet wrap(CustomPacketPayload payload) {
        return new Packet(payload.type().id(), payload);
    }

    public static void toPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection == null) {
            return;
        }
        SimpleChannel ch = channel(payload.type().id().getNamespace());
        if (ch.isRemotePresent(player.connection.connection)) {
            ch.send(PacketDistributor.PLAYER.with(() -> player), wrap(payload));
        }
    }
}
