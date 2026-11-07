package com.starlevi.starbody.net;

import com.starlevi.starbody.StarBody;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Mensajes: el jugador manda sus rasgos y el servidor se los reparte a todos. */
public final class Net {
    private Net() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(StarBody.MOD_ID, path);
    }

    /** Cliente -> servidor: estos son los rasgos de mi skin (vacio = sacarlos). */
    public record SetTraits(String skin, byte[] data) implements CustomPacketPayload {
        public static final Type<SetTraits> TYPE = new Type<>(Net.id("poner_rasgos"));
        public static final StreamCodec<FriendlyByteBuf, SetTraits> CODEC = CustomPacketPayload.codec(SetTraits::write, SetTraits::new);

        SetTraits(FriendlyByteBuf b) {
            this(b.readUtf(256), b.readByteArray(4096));
        }

        void write(FriendlyByteBuf b) {
            b.writeUtf(this.skin, 256);
            b.writeByteArray(this.data);
        }

        @Override
        public Type<SetTraits> type() {
            return TYPE;
        }
    }

    /** Servidor -> cliente: los rasgos de una skin. */
    public record SkinTraits(String skin, byte[] data) implements CustomPacketPayload {
        public static final Type<SkinTraits> TYPE = new Type<>(Net.id("rasgos"));
        public static final StreamCodec<FriendlyByteBuf, SkinTraits> CODEC = CustomPacketPayload.codec(SkinTraits::write, SkinTraits::new);

        SkinTraits(FriendlyByteBuf b) {
            this(b.readUtf(256), b.readByteArray(4096));
        }

        void write(FriendlyByteBuf b) {
            b.writeUtf(this.skin, 256);
            b.writeByteArray(this.data);
        }

        @Override
        public Type<SkinTraits> type() {
            return TYPE;
        }
    }
}
