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
import java.util.UUID;

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

    public record MenuSpirit(UUID id, String type, boolean follows, UUID area) {
    }

    public record MenuArea(UUID id, String name, String size) {
    }

    /** Servidor -> cliente: el menu de la varita (tus espiritus, tus areas, la seleccion). */
    public record Menu(boolean open, List<MenuSpirit> spirits, List<MenuArea> areas, String selection, int maxFollow) implements CustomPacketPayload {
        public static final Type<Menu> TYPE = new Type<>(id("menu"));
        public static final StreamCodec<FriendlyByteBuf, Menu> CODEC = CustomPacketPayload.codec(Menu::write, Menu::new);

        Menu(FriendlyByteBuf b) {
            this(b.readBoolean(), b.readList(x -> new MenuSpirit(x.readUUID(), x.readUtf(128), x.readBoolean(), x.readUUID())),
                    b.readList(x -> new MenuArea(x.readUUID(), x.readUtf(64), x.readUtf(32))), b.readUtf(32), b.readVarInt());
        }

        void write(FriendlyByteBuf b) {
            b.writeBoolean(this.open);
            b.writeCollection(this.spirits, (x, m) -> {
                x.writeUUID(m.id());
                x.writeUtf(m.type(), 128);
                x.writeBoolean(m.follows());
                x.writeUUID(m.area());
            });
            b.writeCollection(this.areas, (x, a) -> {
                x.writeUUID(a.id());
                x.writeUtf(a.name(), 64);
                x.writeUtf(a.size(), 32);
            });
            b.writeUtf(this.selection, 32);
            b.writeVarInt(this.maxFollow);
        }

        @Override
        public Type<Menu> type() {
            return TYPE;
        }
    }

    /** Cliente -> servidor: lo que eligio en el menu de la varita. */
    public record MenuAction(int kind, UUID spirit, UUID area, String name) implements CustomPacketPayload {
        public static final int FOLLOW = 0, ASSIGN = 1, CREATE = 2, DELETE = 3, REFRESH = 4;
        public static final Type<MenuAction> TYPE = new Type<>(id("menu_accion"));
        public static final StreamCodec<FriendlyByteBuf, MenuAction> CODEC = CustomPacketPayload.codec(MenuAction::write, MenuAction::new);

        MenuAction(FriendlyByteBuf b) {
            this(b.readVarInt(), b.readUUID(), b.readUUID(), b.readUtf(32));
        }

        void write(FriendlyByteBuf b) {
            b.writeVarInt(this.kind);
            b.writeUUID(this.spirit);
            b.writeUUID(this.area);
            b.writeUtf(this.name, 32);
        }

        @Override
        public Type<MenuAction> type() {
            return TYPE;
        }
    }

    /** Lo que hace el cliente con cada mensaje (lo pone el cliente; en el servidor dedicado queda vacio). */
    public interface ClientSide {
        void defs(Defs d);

        void chunk(Chunk c);

        default void menu(Menu m) {
        }
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
        r.playToClient(Menu.TYPE, Menu.CODEC, (p, ctx) -> client.menu(p));
        r.playToServer(MenuAction.TYPE, MenuAction.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                if (p.kind() == MenuAction.REFRESH) {
                    com.starlevi.starspirits.server.Homes.openMenu(sp, false);
                } else {
                    com.starlevi.starspirits.server.Homes.action(sp, p);
                }
            }
        });
        r.playToServer(Need.TYPE, Need.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                Uploads.need(sp, p.hash());
            }
        });
    }
}
