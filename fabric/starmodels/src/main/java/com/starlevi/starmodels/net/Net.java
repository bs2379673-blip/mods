package com.starlevi.starmodels.net;

import com.starlevi.starmodels.StarModels;
import com.starlevi.starmodels.server.Outfits;
import com.starlevi.starmodels.server.Uploads;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.UUID;

/**
 * Mensajes: la lista de personajes del servidor, sus archivos por pedazos, y el atuendo de cada jugador
 * (personaje base + accesorios sacados, agregados y ajustados, en json).
 */
public final class Net {
    private Net() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(StarModels.MOD_ID, path);
    }

    /** Un archivo del personaje: nombre, hash y tamano (comprimido, que es como viaja y se guarda). */
    public record FileRef(String file, String hash, int bytes) {
    }

    public record Avatar(String id, String name, String json, List<FileRef> files) {
    }

    /** Servidor -> cliente: todos los personajes que hay. */
    public record Index(List<Avatar> avatars) implements CustomPacketPayload {
        public static final Type<Index> TYPE = new Type<>(id("personajes"));
        public static final StreamCodec<FriendlyByteBuf, Index> CODEC = CustomPacketPayload.codec(Index::write, Index::new);

        Index(FriendlyByteBuf b) {
            this(b.readList(x -> new Avatar(x.readUtf(128), x.readUtf(128), x.readUtf(65536),
                    x.readList(y -> new FileRef(y.readUtf(256), y.readUtf(64), y.readVarInt())))));
        }

        void write(FriendlyByteBuf b) {
            b.writeCollection(this.avatars, (x, a) -> {
                x.writeUtf(a.id(), 128);
                x.writeUtf(a.name(), 128);
                x.writeUtf(a.json(), 65536);
                x.writeCollection(a.files(), (y, f) -> {
                    y.writeUtf(f.file(), 256);
                    y.writeUtf(f.hash(), 64);
                    y.writeVarInt(f.bytes());
                });
            });
        }

        @Override
        public Type<Index> type() {
            return TYPE;
        }
    }

    /** Cliente -> servidor: mandame este archivo. */
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

    /** Servidor -> cliente: el atuendo de un jugador ("" = su skin de Minecraft). */
    public record Outfit(UUID player, String json) implements CustomPacketPayload {
        public static final Type<Outfit> TYPE = new Type<>(id("atuendo"));
        public static final StreamCodec<FriendlyByteBuf, Outfit> CODEC = CustomPacketPayload.codec(Outfit::write, Outfit::new);

        Outfit(FriendlyByteBuf b) {
            this(b.readUUID(), b.readUtf(32767));
        }

        void write(FriendlyByteBuf b) {
            b.writeUUID(this.player);
            b.writeUtf(this.json, 32767);
        }

        @Override
        public Type<Outfit> type() {
            return TYPE;
        }
    }

    /** Cliente -> servidor: quiero usar este atuendo ("" = mi skin). */
    public record Choose(String json) implements CustomPacketPayload {
        public static final Type<Choose> TYPE = new Type<>(id("elegir"));
        public static final StreamCodec<FriendlyByteBuf, Choose> CODEC = CustomPacketPayload.codec(Choose::write, Choose::new);

        Choose(FriendlyByteBuf b) {
            this(b.readUtf(32767));
        }

        void write(FriendlyByteBuf b) {
            b.writeUtf(this.json, 32767);
        }

        @Override
        public Type<Choose> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(Index.TYPE, Index.CODEC);
        PayloadTypeRegistry.playS2C().register(Chunk.TYPE, Chunk.CODEC);
        PayloadTypeRegistry.playS2C().register(Outfit.TYPE, Outfit.CODEC);
        PayloadTypeRegistry.playC2S().register(Need.TYPE, Need.CODEC);
        PayloadTypeRegistry.playC2S().register(Choose.TYPE, Choose.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Need.TYPE, (p, ctx) -> Uploads.need(ctx.player(), p.hash()));
        ServerPlayNetworking.registerGlobalReceiver(Choose.TYPE, (p, ctx) -> Outfits.choose(ctx.player(), p.json()));
    }
}
