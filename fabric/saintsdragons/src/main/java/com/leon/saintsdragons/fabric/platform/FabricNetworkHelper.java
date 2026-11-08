package com.leon.saintsdragons.fabric.platform;

import com.leon.saintsdragons.platform.NetworkHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FabricNetworkHelper implements NetworkHelper {
    private enum Direction {
        SERVERBOUND,
        CLIENTBOUND
    }

    private static final class Binding<T> {
        final Identifier id;
        final PacketEncoder<T> encoder;
        final Direction direction;

        Binding(Identifier id, PacketEncoder<T> encoder, Direction direction) {
            this.id = id;
            this.encoder = encoder;
            this.direction = direction;
        }
    }

    private final Map<Class<?>, Binding<?>> bindings = new ConcurrentHashMap<>();

    /** Port 1.21.11: cada mensaje viaja como un CustomPacketPayload con sus bytes (el formato de siempre). */
    public record RawPayload(net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<RawPayload> type, byte[] data)
            implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    }

    private static final Map<Identifier, net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<RawPayload>> TYPES = new ConcurrentHashMap<>();

    private static net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<RawPayload> type(Identifier id, boolean toClient) {
        return TYPES.computeIfAbsent(id, i -> {
            net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<RawPayload> t =
                    new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(i);
            net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, RawPayload> codec =
                    net.minecraft.network.codec.StreamCodec.of((buf, p) -> buf.writeBytes(p.data()), buf -> {
                        byte[] b = new byte[buf.readableBytes()];
                        buf.readBytes(b);
                        return new RawPayload(t, b);
                    });
            if (toClient) {
                net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(t, codec);
            } else {
                net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(t, codec);
            }
            return t;
        });
    }

    private static FriendlyByteBuf wrap(byte[] data) {
        return new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(data));
    }

    private static byte[] bytes(FriendlyByteBuf buf) {
        byte[] b = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), b);
        return b;
    }

    @Override
    public <T> void registerServerbound(Class<T> type,
                                        Identifier id,
                                        PacketEncoder<T> encoder,
                                        PacketDecoder<T> decoder,
                                        ServerboundHandler<T> handler) {
        bindings.put(type, new Binding<>(id, encoder, Direction.SERVERBOUND));
        ServerPlayNetworking.registerGlobalReceiver(type(id, false), (payload, context) -> {
            T message = decoder.decode(wrap(payload.data()));
            context.server().execute(() -> handler.handle(message, context.player()));
        });
    }

    @Override
    public <T> void registerClientbound(Class<T> type,
                                        Identifier id,
                                        PacketEncoder<T> encoder,
                                        PacketDecoder<T> decoder,
                                        ClientboundHandler<T> handler) {
        bindings.put(type, new Binding<>(id, encoder, Direction.CLIENTBOUND));
        type(id, true);
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientAccess.register(id, decoder, handler);
        }
    }

    @Override
    public void sendToServer(Object message) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            throw new IllegalStateException("Client-only networking method invoked on a non-client environment");
        }
        Binding<Object> binding = bindingFor(message);
        if (binding.direction != Direction.SERVERBOUND) {
            throw new IllegalStateException("Attempted to send clientbound packet to server: " + message.getClass());
        }
        FriendlyByteBuf buffer = createBuffer(binding, message);
        ClientAccess.send(binding.id, buffer);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, Object message) {
        Binding<Object> binding = bindingFor(message);
        if (binding.direction != Direction.CLIENTBOUND) {
            throw new IllegalStateException("Attempted to send serverbound packet to player: " + message.getClass());
        }
        FriendlyByteBuf buffer = createBuffer(binding, message);
        ServerPlayNetworking.send(player, new RawPayload(type(binding.id, true), bytes(buffer)));
    }

    @Override
    public void sendToTracking(Entity entity, Object message) {
        Binding<Object> binding = bindingFor(message);
        if (binding.direction != Direction.CLIENTBOUND) {
            throw new IllegalStateException("Attempted to send serverbound packet to tracking players: " + message.getClass());
        }
        for (ServerPlayer tracking : PlayerLookup.tracking(entity)) {
            FriendlyByteBuf buffer = createBuffer(binding, message);
            ServerPlayNetworking.send(tracking, new RawPayload(type(binding.id, true), bytes(buffer)));
        }
    }

    @Override
    public void sendToDimension(Level level, Object message) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Binding<Object> binding = bindingFor(message);
        if (binding.direction != Direction.CLIENTBOUND) {
            throw new IllegalStateException("Attempted to send serverbound packet to dimension: " + message.getClass());
        }
        for (ServerPlayer player : PlayerLookup.world(serverLevel)) {
            FriendlyByteBuf buffer = createBuffer(binding, message);
            ServerPlayNetworking.send(player, new RawPayload(type(binding.id, true), bytes(buffer)));
        }
    }

    private Binding<Object> bindingFor(Object message) {
        Class<?> messageClass = message.getClass();
        @SuppressWarnings("unchecked")
        Binding<Object> binding = (Binding<Object>) bindings.get(messageClass);
        if (binding == null) {
            throw new IllegalStateException("No network binding registered for " + messageClass.getName());
        }
        return binding;
    }

    private FriendlyByteBuf createBuffer(Binding<Object> binding, Object message) {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        @SuppressWarnings("unchecked")
        PacketEncoder<Object> encoder = (PacketEncoder<Object>) binding.encoder;
        encoder.encode(message, buffer);
        return buffer;
    }

    @Environment(EnvType.CLIENT)
    private static final class ClientAccess {
        private ClientAccess() {}

        private static <T> void register(Identifier id,
                                         PacketDecoder<T> decoder,
                                         ClientboundHandler<T> handler) {
            ClientPlayNetworking.registerGlobalReceiver(type(id, true), (payload, context) -> {
                T message = decoder.decode(wrap(payload.data()));
                context.client().execute(() -> handler.handle(message));
            });
        }

        private static void send(Identifier id, FriendlyByteBuf buffer) {
            ClientPlayNetworking.send(new RawPayload(type(id, false), bytes(buffer)));
        }
    }
}
