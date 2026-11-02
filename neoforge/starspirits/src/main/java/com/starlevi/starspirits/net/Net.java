package com.starlevi.starspirits.net;

import com.starlevi.starspirits.StarSpirits;
import com.starlevi.starspirits.server.Uploads;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

/** Mensajes: la lista de espiritus del servidor y los archivos (texturas, modelos) por pedazos. */
public final class Net {
    private Net() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(StarSpirits.MOD_ID, path);
    }

    public record FileRef(String file, String hash, int bytes) {
    }

    public record Def(String id, String json, List<FileRef> files) {
    }

    /** Servidor -> cliente: todos los espiritus que existen en el servidor. */
    public record Defs(List<Def> defs) implements CustomPacketPayload {
        public static final Type<Defs> TYPE = new Type<>(id("espiritus"));
        public static final StreamCodec<FriendlyByteBuf, Defs> CODEC = CustomPacketPayload.codec(Defs::write, Defs::new);

        Defs(FriendlyByteBuf b) {
            this(b.readList(x -> new Def(x.readUtf(128), x.readUtf(65536),
                    x.readList(y -> new FileRef(y.readUtf(256), y.readUtf(64), y.readVarInt())))));
        }

        void write(FriendlyByteBuf b) {
            b.writeCollection(this.defs, (x, d) -> {
                x.writeUtf(d.id(), 128);
                x.writeUtf(d.json(), 65536);
                x.writeCollection(d.files(), (y, f) -> {
                    y.writeUtf(f.file(), 256);
                    y.writeUtf(f.hash(), 64);
                    y.writeVarInt(f.bytes());
                });
            });
        }

        @Override
        public Type<Defs> type() {
            return TYPE;
        }
    }

    /** Cliente -> servidor: no tengo este archivo, mandamelo. */
    public record Need(String hash) implements CustomPacketPayload {
        public static final Type<Need> TYPE = new Type<>(id("necesito"));
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

    /** Servidor -> cliente: un pedazo de un archivo. */
    public record Chunk(String hash, int offset, int total, byte[] data) implements CustomPacketPayload {
        public static final Type<Chunk> TYPE = new Type<>(id("pedazo"));
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

    /** Lo que hace el cliente con cada mensaje (lo pone el cliente; en el servidor dedicado queda vacio). */
    public interface ClientSide {
        void defs(Defs d);

        void chunk(Chunk c);
    }

    public static ClientSide client = new ClientSide() {
        @Override
        public void defs(Defs d) {
        }

        @Override
        public void chunk(Chunk c) {
        }
    };

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1").optional();
        r.playToClient(Defs.TYPE, Defs.CODEC, (p, ctx) -> client.defs(p));
        r.playToClient(Chunk.TYPE, Chunk.CODEC, (p, ctx) -> client.chunk(p));
        r.playToServer(Need.TYPE, Need.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                Uploads.need(sp, p.hash());
            }
        });
    }
}
