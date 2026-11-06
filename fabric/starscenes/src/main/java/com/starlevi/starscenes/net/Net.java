package com.starlevi.starscenes.net;

import com.starlevi.starscenes.StarScenes;
import com.starlevi.starscenes.server.Edits;
import com.starlevi.starscenes.server.Uploads;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Mensajes: la lista de modelos y skins del servidor, sus archivos por pedazos, las animaciones que
 * dispara el servidor y el menu de edicion.
 */
public final class Net {
    private Net() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(StarScenes.MOD_ID, path);
    }

    /** Un archivo: nombre (sin extension), hash y tamaño comprimido (como viaja y se guarda). */
    public record FileRef(String id, String hash, int bytes) {
        static FileRef read(FriendlyByteBuf b) {
            return new FileRef(b.readUtf(128), b.readUtf(64), b.readVarInt());
        }

        static void write(FriendlyByteBuf b, FileRef f) {
            b.writeUtf(f.id, 128);
            b.writeUtf(f.hash, 64);
            b.writeVarInt(f.bytes);
        }
    }

    /** Servidor -> cliente: los modelos y skins que hay. */
    public record Index(List<FileRef> models, List<FileRef> skins) implements CustomPacketPayload {
        public static final Type<Index> TYPE = new Type<>(id("indice"));
        public static final StreamCodec<FriendlyByteBuf, Index> CODEC = CustomPacketPayload.codec(Index::write, Index::new);

        Index(FriendlyByteBuf b) {
            this(b.readList(FileRef::read), b.readList(FileRef::read));
        }

        void write(FriendlyByteBuf b) {
            b.writeCollection(this.models, FileRef::write);
            b.writeCollection(this.skins, FileRef::write);
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

    /** Servidor -> cliente: que un NPC haga una animacion ("interact", o el nombre de una). */
    public record Play(int entity, String anim) implements CustomPacketPayload {
        public static final Type<Play> TYPE = new Type<>(id("animar"));
        public static final StreamCodec<FriendlyByteBuf, Play> CODEC = CustomPacketPayload.codec(Play::write, Play::new);

        Play(FriendlyByteBuf b) {
            this(b.readVarInt(), b.readUtf(128));
        }

        void write(FriendlyByteBuf b) {
            b.writeVarInt(this.entity);
            b.writeUtf(this.anim, 128);
        }

        @Override
        public Type<Play> type() {
            return TYPE;
        }
    }

    /** Servidor -> cliente: abri el menu de este NPC. */
    public record OpenMenu(int entity) implements CustomPacketPayload {
        public static final Type<OpenMenu> TYPE = new Type<>(id("menu"));
        public static final StreamCodec<FriendlyByteBuf, OpenMenu> CODEC = CustomPacketPayload.codec(OpenMenu::write, OpenMenu::new);

        OpenMenu(FriendlyByteBuf b) {
            this(b.readVarInt());
        }

        void write(FriendlyByteBuf b) {
            b.writeVarInt(this.entity);
        }

        @Override
        public Type<OpenMenu> type() {
            return TYPE;
        }
    }

    /** Cliente -> servidor: un cambio hecho desde el menu (accion y valor, como los comandos). */
    public record Edit(int entity, String action, String value) implements CustomPacketPayload {
        public static final Type<Edit> TYPE = new Type<>(id("editar"));
        public static final StreamCodec<FriendlyByteBuf, Edit> CODEC = CustomPacketPayload.codec(Edit::write, Edit::new);

        Edit(FriendlyByteBuf b) {
            this(b.readVarInt(), b.readUtf(64), b.readUtf(256));
        }

        void write(FriendlyByteBuf b) {
            b.writeVarInt(this.entity);
            b.writeUtf(this.action, 64);
            b.writeUtf(this.value, 256);
        }

        @Override
        public Type<Edit> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(Index.TYPE, Index.CODEC);
        PayloadTypeRegistry.playS2C().register(Chunk.TYPE, Chunk.CODEC);
        PayloadTypeRegistry.playS2C().register(Play.TYPE, Play.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenMenu.TYPE, OpenMenu.CODEC);
        PayloadTypeRegistry.playC2S().register(Need.TYPE, Need.CODEC);
        PayloadTypeRegistry.playC2S().register(Edit.TYPE, Edit.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Need.TYPE, (p, ctx) -> Uploads.need(ctx.player(), p.hash()));
        ServerPlayNetworking.registerGlobalReceiver(Edit.TYPE, (p, ctx) -> Edits.fromMenu(ctx.player(), p.entity(), p.action(), p.value()));
    }
}
