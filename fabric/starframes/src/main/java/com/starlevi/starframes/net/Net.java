package com.starlevi.starframes.net;

import com.starlevi.starframes.StarFrames;
import com.starlevi.starframes.channel.Channels;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Mensajes de StarFrames: el estado de cada canal (lista, que suena y su reloj) y los cambios de los menus. */
public final class Net {
    private Net() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(StarFrames.MOD_ID, path);
    }

    /**
     * Servidor -> cliente: un canal. clock es el reloj de WaterMedia (27 bytes): todos los clientes lo
     * siguen y ven el mismo momento.
     */
    public record ChannelState(String id, List<String> list, int index, int volume, boolean loopOne, boolean loopList, byte[] clock)
            implements CustomPacketPayload {
        public static final Type<ChannelState> TYPE = new Type<>(Net.id("canal"));
        public static final StreamCodec<FriendlyByteBuf, ChannelState> CODEC = CustomPacketPayload.codec(ChannelState::write, ChannelState::new);

        ChannelState(FriendlyByteBuf b) {
            this(b.readUtf(64), b.readList(x -> x.readUtf(2048)), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readByteArray(256));
        }

        void write(FriendlyByteBuf b) {
            b.writeUtf(this.id, 64);
            b.writeCollection(this.list, (x, s) -> x.writeUtf(s, 2048));
            b.writeVarInt(this.index);
            b.writeVarInt(this.volume);
            b.writeBoolean(this.loopOne);
            b.writeBoolean(this.loopList);
            b.writeByteArray(this.clock);
        }

        public String current() {
            return this.index >= 0 && this.index < this.list.size() ? this.list.get(this.index) : "";
        }

        @Override
        public Type<ChannelState> type() {
            return TYPE;
        }
    }

    /** Servidor -> cliente: un canal que ya no existe. */
    public record ChannelGone(String id) implements CustomPacketPayload {
        public static final Type<ChannelGone> TYPE = new Type<>(Net.id("canal_borrado"));
        public static final StreamCodec<FriendlyByteBuf, ChannelGone> CODEC = CustomPacketPayload.codec(ChannelGone::write, ChannelGone::new);

        ChannelGone(FriendlyByteBuf b) {
            this(b.readUtf(64));
        }

        void write(FriendlyByteBuf b) {
            b.writeUtf(this.id, 64);
        }

        @Override
        public Type<ChannelGone> type() {
            return TYPE;
        }
    }

    /** Servidor -> cliente: abri el menu de esta pantalla. */
    public record OpenScreen(BlockPos pos) implements CustomPacketPayload {
        public static final Type<OpenScreen> TYPE = new Type<>(id("abrir_pantalla"));
        public static final StreamCodec<FriendlyByteBuf, OpenScreen> CODEC = CustomPacketPayload.codec(OpenScreen::write, OpenScreen::new);

        OpenScreen(FriendlyByteBuf b) {
            this(b.readBlockPos());
        }

        void write(FriendlyByteBuf b) {
            b.writeBlockPos(this.pos);
        }

        @Override
        public Type<OpenScreen> type() {
            return TYPE;
        }
    }

    /** Servidor -> cliente: abri el control remoto de este canal. */
    public record OpenRemote(String channel) implements CustomPacketPayload {
        public static final Type<OpenRemote> TYPE = new Type<>(id("abrir_control"));
        public static final StreamCodec<FriendlyByteBuf, OpenRemote> CODEC = CustomPacketPayload.codec(OpenRemote::write, OpenRemote::new);

        OpenRemote(FriendlyByteBuf b) {
            this(b.readUtf(64));
        }

        void write(FriendlyByteBuf b) {
            b.writeUtf(this.channel, 64);
        }

        @Override
        public Type<OpenRemote> type() {
            return TYPE;
        }
    }

    /** Cliente -> servidor: un cambio en una pantalla (tamaño, canal, alcance...). */
    public record EditScreen(BlockPos pos, String field, String value) implements CustomPacketPayload {
        public static final Type<EditScreen> TYPE = new Type<>(id("editar_pantalla"));
        public static final StreamCodec<FriendlyByteBuf, EditScreen> CODEC = CustomPacketPayload.codec(EditScreen::write, EditScreen::new);

        EditScreen(FriendlyByteBuf b) {
            this(b.readBlockPos(), b.readUtf(32), b.readUtf(2048));
        }

        void write(FriendlyByteBuf b) {
            b.writeBlockPos(this.pos);
            b.writeUtf(this.field, 32);
            b.writeUtf(this.value, 2048);
        }

        @Override
        public Type<EditScreen> type() {
            return TYPE;
        }
    }

    /** Cliente -> servidor: control de un canal (play, pausa, adelantar, lista...). */
    public record Control(String channel, String action, String value) implements CustomPacketPayload {
        public static final Type<Control> TYPE = new Type<>(id("control"));
        public static final StreamCodec<FriendlyByteBuf, Control> CODEC = CustomPacketPayload.codec(Control::write, Control::new);

        Control(FriendlyByteBuf b) {
            this(b.readUtf(64), b.readUtf(32), b.readUtf(2048));
        }

        void write(FriendlyByteBuf b) {
            b.writeUtf(this.channel, 64);
            b.writeUtf(this.action, 32);
            b.writeUtf(this.value, 2048);
        }

        @Override
        public Type<Control> type() {
            return TYPE;
        }
    }

    /** Cliente -> servidor: cuanto dura lo que suena (el primer cliente que lo abrio se lo dice al reloj). */
    public record Duration(String channel, String url, long millis, boolean live) implements CustomPacketPayload {
        public static final Type<Duration> TYPE = new Type<>(id("duracion"));
        public static final StreamCodec<FriendlyByteBuf, Duration> CODEC = CustomPacketPayload.codec(Duration::write, Duration::new);

        Duration(FriendlyByteBuf b) {
            this(b.readUtf(64), b.readUtf(2048), b.readVarLong(), b.readBoolean());
        }

        void write(FriendlyByteBuf b) {
            b.writeUtf(this.channel, 64);
            b.writeUtf(this.url, 2048);
            b.writeVarLong(this.millis);
            b.writeBoolean(this.live);
        }

        @Override
        public Type<Duration> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(ChannelState.TYPE, ChannelState.CODEC);
        PayloadTypeRegistry.playS2C().register(ChannelGone.TYPE, ChannelGone.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenScreen.TYPE, OpenScreen.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenRemote.TYPE, OpenRemote.CODEC);
        PayloadTypeRegistry.playC2S().register(EditScreen.TYPE, EditScreen.CODEC);
        PayloadTypeRegistry.playC2S().register(Control.TYPE, Control.CODEC);
        PayloadTypeRegistry.playC2S().register(Duration.TYPE, Duration.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(EditScreen.TYPE, (p, ctx) -> Channels.editScreen(ctx.player(), p.pos(), p.field(), p.value()));
        ServerPlayNetworking.registerGlobalReceiver(Control.TYPE, (p, ctx) -> Channels.control(ctx.player(), p.channel(), p.action(), p.value()));
        ServerPlayNetworking.registerGlobalReceiver(Duration.TYPE, (p, ctx) -> Channels.duration(p.channel(), p.url(), p.millis(), p.live()));
    }
}
