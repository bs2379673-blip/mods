package com.starlevi.starscenes.net;

import com.starlevi.starscenes.StarScenes;
import com.starlevi.starscenes.server.Edits;
import com.starlevi.starscenes.server.Uploads;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.starlevi.starscenes.talk.Dialogues;
import com.starlevi.starscenes.talk.Shops;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.ItemStack;
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

    public static Identifier id(String path) {
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

    /** Servidor -> cliente: abri el menu de este NPC (con su dialogo y los que hay). */
    public record OpenMenu(int entity, String dialogue, List<String> dialogues) implements CustomPacketPayload {
        public static final Type<OpenMenu> TYPE = new Type<>(id("menu"));
        public static final StreamCodec<FriendlyByteBuf, OpenMenu> CODEC = CustomPacketPayload.codec(OpenMenu::write, OpenMenu::new);

        public static OpenMenu of(com.starlevi.starscenes.entity.NpcEntity npc) {
            return new OpenMenu(npc.getId(), npc.dialogue(), Dialogues.ids());
        }

        OpenMenu(FriendlyByteBuf b) {
            this(b.readVarInt(), b.readUtf(128), b.readList(x -> x.readUtf(128)));
        }

        void write(FriendlyByteBuf b) {
            b.writeVarInt(this.entity);
            b.writeUtf(this.dialogue, 128);
            b.writeCollection(this.dialogues, (x, d) -> x.writeUtf(d, 128));
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

    /** Servidor -> cliente: lo que dice un NPC y las respuestas posibles (hablante -1 = sin NPC). */
    public record Talk(int speaker, String name, String text, List<String> options, String voice, float pitch) implements CustomPacketPayload {
        public static final Type<Talk> TYPE = new Type<>(id("dialogo"));
        public static final StreamCodec<FriendlyByteBuf, Talk> CODEC = CustomPacketPayload.codec(Talk::write, Talk::new);

        Talk(FriendlyByteBuf b) {
            this(b.readVarInt(), b.readUtf(256), b.readUtf(8192), b.readList(x -> x.readUtf(1024)), b.readUtf(32), b.readFloat());
        }

        void write(FriendlyByteBuf b) {
            b.writeVarInt(this.speaker);
            b.writeUtf(this.name, 256);
            b.writeUtf(this.text, 8192);
            b.writeCollection(this.options, (x, o) -> x.writeUtf(o, 1024));
            b.writeUtf(this.voice, 32);
            b.writeFloat(this.pitch);
        }

        @Override
        public Type<Talk> type() {
            return TYPE;
        }
    }

    /** Servidor -> cliente: se termino el dialogo. */
    public record TalkEnd() implements CustomPacketPayload {
        public static final Type<TalkEnd> TYPE = new Type<>(id("dialogo_fin"));
        public static final StreamCodec<FriendlyByteBuf, TalkEnd> CODEC = StreamCodec.unit(new TalkEnd());

        @Override
        public Type<TalkEnd> type() {
            return TYPE;
        }
    }

    /** Cliente -> servidor: eligio la respuesta (o -1 para irse). */
    public record Choose(int index) implements CustomPacketPayload {
        public static final Type<Choose> TYPE = new Type<>(id("elegir"));
        public static final StreamCodec<FriendlyByteBuf, Choose> CODEC = CustomPacketPayload.codec(Choose::write, Choose::new);

        Choose(FriendlyByteBuf b) {
            this(b.readVarInt());
        }

        void write(FriendlyByteBuf b) {
            b.writeVarInt(this.index);
        }

        @Override
        public Type<Choose> type() {
            return TYPE;
        }
    }

    /** Servidor -> cliente: el cartel de misiones (ya armado). */
    public record QuestHud(List<Component> lines) implements CustomPacketPayload {
        public static final Type<QuestHud> TYPE = new Type<>(id("misiones"));
        public static final StreamCodec<RegistryFriendlyByteBuf, QuestHud> CODEC = CustomPacketPayload.codec(QuestHud::write, QuestHud::new);

        QuestHud(RegistryFriendlyByteBuf b) {
            this(b.readList(x -> ComponentSerialization.TRUSTED_STREAM_CODEC.decode((RegistryFriendlyByteBuf) x)));
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeCollection(this.lines, (x, c) -> ComponentSerialization.TRUSTED_STREAM_CODEC.encode((RegistryFriendlyByteBuf) x, c));
        }

        @Override
        public Type<QuestHud> type() {
            return TYPE;
        }
    }

    /** Una oferta como la ve el jugador; left = compras que le quedan (-1 sin limite). */
    public record OfferView(int index, List<ItemStack> price, ItemStack result, String label, int left) {
        static OfferView read(RegistryFriendlyByteBuf b) {
            return new OfferView(b.readVarInt(), ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(b), ItemStack.OPTIONAL_STREAM_CODEC.decode(b), b.readUtf(256),
                    b.readVarInt());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeVarInt(this.index);
            ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(b, this.price);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(b, this.result);
            b.writeUtf(this.label, 256);
            b.writeVarInt(this.left);
        }
    }

    /** Servidor -> cliente: abri (o actualiza) la tienda. */
    public record ShopOpen(String id, String name, List<OfferView> offers) implements CustomPacketPayload {
        public static final Type<ShopOpen> TYPE = new Type<>(Net.id("tienda"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ShopOpen> CODEC = CustomPacketPayload.codec(ShopOpen::write, ShopOpen::new);

        ShopOpen(RegistryFriendlyByteBuf b) {
            this(b.readUtf(128), b.readUtf(256), readOffers(b));
        }

        private static List<OfferView> readOffers(RegistryFriendlyByteBuf b) {
            int n = b.readVarInt();
            List<OfferView> l = new java.util.ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                l.add(OfferView.read(b));
            }
            return l;
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeUtf(this.id, 128);
            b.writeUtf(this.name, 256);
            b.writeVarInt(this.offers.size());
            for (OfferView o : this.offers) {
                o.write(b);
            }
        }

        @Override
        public Type<ShopOpen> type() {
            return TYPE;
        }
    }

    /** Cliente -> servidor: comprar una oferta (indice -1: cerro la tienda). */
    public record Buy(String shop, int index) implements CustomPacketPayload {
        public static final Type<Buy> TYPE = new Type<>(id("comprar"));
        public static final StreamCodec<FriendlyByteBuf, Buy> CODEC = CustomPacketPayload.codec(Buy::write, Buy::new);

        Buy(FriendlyByteBuf b) {
            this(b.readUtf(128), b.readVarInt());
        }

        void write(FriendlyByteBuf b) {
            b.writeUtf(this.shop, 128);
            b.writeVarInt(this.index);
        }

        @Override
        public Type<Buy> type() {
            return TYPE;
        }
    }

    /** Servidor -> cliente: un globito de texto arriba de un personaje por un rato. */
    /** Un gesto del modelo humano en un jugador (abrazo, beso...); ticks 0 = cortarlo. */
    public record Gesture(int entity, String anim, int ticks) implements CustomPacketPayload {
        public static final Type<Gesture> TYPE = new Type<>(Net.id("gesto"));
        public static final StreamCodec<FriendlyByteBuf, Gesture> CODEC = CustomPacketPayload.codec(Gesture::write, Gesture::new);

        Gesture(FriendlyByteBuf b) {
            this(b.readVarInt(), b.readUtf(64), b.readVarInt());
        }

        void write(FriendlyByteBuf b) {
            b.writeVarInt(this.entity);
            b.writeUtf(this.anim, 64);
            b.writeVarInt(this.ticks);
        }

        @Override
        public Type<Gesture> type() {
            return TYPE;
        }
    }

    public record Say(int entity, String text, int ticks) implements CustomPacketPayload {
        public static final Type<Say> TYPE = new Type<>(Net.id("decir"));
        public static final StreamCodec<FriendlyByteBuf, Say> CODEC = CustomPacketPayload.codec(Say::write, Say::new);

        Say(FriendlyByteBuf b) {
            this(b.readVarInt(), b.readUtf(256), b.readVarInt());
        }

        void write(FriendlyByteBuf b) {
            b.writeVarInt(this.entity);
            b.writeUtf(this.text, 256);
            b.writeVarInt(this.ticks);
        }

        @Override
        public Type<Say> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(Index.TYPE, Index.CODEC);
        PayloadTypeRegistry.playS2C().register(Chunk.TYPE, Chunk.CODEC);
        PayloadTypeRegistry.playS2C().register(Play.TYPE, Play.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenMenu.TYPE, OpenMenu.CODEC);
        PayloadTypeRegistry.playS2C().register(Talk.TYPE, Talk.CODEC);
        PayloadTypeRegistry.playS2C().register(Say.TYPE, Say.CODEC);
        PayloadTypeRegistry.playS2C().register(Gesture.TYPE, Gesture.CODEC);
        PayloadTypeRegistry.playS2C().register(TalkEnd.TYPE, TalkEnd.CODEC);
        PayloadTypeRegistry.playS2C().register(QuestHud.TYPE, QuestHud.CODEC);
        PayloadTypeRegistry.playS2C().register(ShopOpen.TYPE, ShopOpen.CODEC);
        PayloadTypeRegistry.playC2S().register(Choose.TYPE, Choose.CODEC);
        PayloadTypeRegistry.playC2S().register(Buy.TYPE, Buy.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Choose.TYPE, (p, ctx) -> Dialogues.choose(ctx.player(), p.index()));
        ServerPlayNetworking.registerGlobalReceiver(Buy.TYPE, (p, ctx) -> {
            if (p.index() < 0) {
                Shops.close(ctx.player());
            } else {
                Shops.buy(ctx.player(), p.shop(), p.index());
            }
        });
        PayloadTypeRegistry.playC2S().register(Need.TYPE, Need.CODEC);
        PayloadTypeRegistry.playC2S().register(Edit.TYPE, Edit.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Need.TYPE, (p, ctx) -> Uploads.need(ctx.player(), p.hash()));
        ServerPlayNetworking.registerGlobalReceiver(Edit.TYPE, (p, ctx) -> Edits.fromMenu(ctx.player(), p.entity(), p.action(), p.value()));
    }
}
