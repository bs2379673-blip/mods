package com.starlevi.starfest.net;

import com.starlevi.starfest.StarFest;
import com.starlevi.starfest.server.ShowServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Mensajes entre servidor y clientes. */
public final class Net {
    private Net() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(StarFest.MOD_ID, path);
    }

    /** Servidor -> cliente: empieza a sonar una cancion en una zona (offsetMs = ya lleva sonando eso). */
    public record Play(String zone, String name, String hash, int bytes, long offsetMs, long durationMs, float volume,
                       int x, int y, int z, int radius) implements CustomPacketPayload {
        public static final Type<Play> TYPE = new Type<>(id("play"));
        public static final StreamCodec<FriendlyByteBuf, Play> CODEC = CustomPacketPayload.codec(Play::write, Play::new);

        Play(FriendlyByteBuf b) {
            this(b.readUtf(64), b.readUtf(128), b.readUtf(64), b.readVarInt(), b.readVarLong(), b.readVarLong(), b.readFloat(),
                    b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt());
        }

        void write(FriendlyByteBuf b) {
            b.writeUtf(this.zone, 64);
            b.writeUtf(this.name, 128);
            b.writeUtf(this.hash, 64);
            b.writeVarInt(this.bytes);
            b.writeVarLong(this.offsetMs);
            b.writeVarLong(this.durationMs);
            b.writeFloat(this.volume);
            b.writeVarInt(this.x);
            b.writeVarInt(this.y);
            b.writeVarInt(this.z);
            b.writeVarInt(this.radius);
        }

        @Override
        public Type<Play> type() {
            return TYPE;
        }
    }

    /** Servidor -> cliente: se termina la musica de una zona (o dejaste de estar en ella). */
    public record Stop(String zone) implements CustomPacketPayload {
        public static final Type<Stop> TYPE = new Type<>(id("stop"));
        public static final StreamCodec<FriendlyByteBuf, Stop> CODEC = CustomPacketPayload.codec(Stop::write, Stop::new);

        Stop(FriendlyByteBuf b) {
            this(b.readUtf(64));
        }

        void write(FriendlyByteBuf b) {
            b.writeUtf(this.zone, 64);
        }

        @Override
        public Type<Stop> type() {
            return TYPE;
        }
    }

    /** Servidor -> cliente: un pedazo de un .ogg. */
    public record Chunk(String hash, int offset, int total, byte[] data) implements CustomPacketPayload {
        public static final Type<Chunk> TYPE = new Type<>(id("chunk"));
        public static final StreamCodec<FriendlyByteBuf, Chunk> CODEC = CustomPacketPayload.codec(Chunk::write, Chunk::new);

        Chunk(FriendlyByteBuf b) {
            this(b.readUtf(64), b.readVarInt(), b.readVarInt(), b.readByteArray(1 << 20));
        }

        void write(FriendlyByteBuf b) {
            b.writeUtf(this.hash, 64);
            b.writeVarInt(this.offset);
            b.writeVarInt(this.total);
            b.writeByteArray(this.data);
        }

        @Override
        public Type<Chunk> type() {
            return TYPE;
        }
    }

    /** Cliente -> servidor: no tengo esta cancion, mandamela. */
    public record Need(String hash) implements CustomPacketPayload {
        public static final Type<Need> TYPE = new Type<>(id("need"));
        public static final StreamCodec<FriendlyByteBuf, Need> CODEC = CustomPacketPayload.codec(Need::write, Need::new);

        Need(FriendlyByteBuf b) {
            this(b.readUtf(64));
        }

        void write(FriendlyByteBuf b) {
            b.writeUtf(this.hash, 64);
        }

        @Override
        public Type<Need> type() {
            return TYPE;
        }
    }

    /** Servidor -> cliente: efecto puntual (fuegos artificiales) cerca de un punto. */
    public record Fx(String kind, int x, int y, int z, int radius) implements CustomPacketPayload {
        public static final Type<Fx> TYPE = new Type<>(id("fx"));
        public static final StreamCodec<FriendlyByteBuf, Fx> CODEC = CustomPacketPayload.codec(Fx::write, Fx::new);

        Fx(FriendlyByteBuf b) {
            this(b.readUtf(32), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt());
        }

        void write(FriendlyByteBuf b) {
            b.writeUtf(this.kind, 32);
            b.writeVarInt(this.x);
            b.writeVarInt(this.y);
            b.writeVarInt(this.z);
            b.writeVarInt(this.radius);
        }

        @Override
        public Type<Fx> type() {
            return TYPE;
        }
    }

    /** Del lado del cliente: quien atiende los mensajes (solo existe en el cliente). */
    public interface ClientSide {
        void play(Play p);

        void stop(Stop p);

        void chunk(Chunk p);

        void fx(Fx p);

    }

    public static ClientSide client;

    /** Los mensajes (los receptores del cliente los pone StarFestClient). */
    public static void register() {
        PayloadTypeRegistry.playS2C().register(Play.TYPE, Play.CODEC);
        PayloadTypeRegistry.playS2C().register(Stop.TYPE, Stop.CODEC);
        PayloadTypeRegistry.playS2C().register(Chunk.TYPE, Chunk.CODEC);
        PayloadTypeRegistry.playS2C().register(Fx.TYPE, Fx.CODEC);
        PayloadTypeRegistry.playC2S().register(Need.TYPE, Need.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Need.TYPE, (p, ctx) -> ShowServer.need(ctx.player(), p.hash()));
    }
}
