package com.tunombre.crafteos;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class Net {
    private static final String V = "2";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(new ResourceLocation(Crafteos.MODID, "main"), () -> V, V::equals, V::equals);
    /** ultima consulta de cada admin, para reenviarle la lista actualizada despues de un cambio */
    static final Map<UUID, Query> LAST = new ConcurrentHashMap<>();

    private Net() { }

    static void init() {
        CH.registerMessage(0, Page.class, Page::write, Page::read, Page::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH.registerMessage(1, Query.class, Query::write, Query::read, Query::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CH.registerMessage(3, Banned.class, Banned::write, Banned::read, Banned::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH.registerMessage(2, Cmd.class, (m, b) -> b.writeUtf(m.line(), 300), b -> new Cmd(b.readUtf(300)), Cmd::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    /** pedido de una pagina de la lista. tipo: 0 todos, 1 mesa, 2 hornos, 3 herreria/cantero, 4 otros (mods, TACZ...), 5 mods enteros. estado: 0 todos, 1 desactivados, 2 activos. */
    public record Query(String search, int tipo, int estado, int page, int perPage, boolean open) {
        void write(FriendlyByteBuf b) { b.writeUtf(search, 80); b.writeByte(tipo); b.writeByte(estado); b.writeVarInt(page); b.writeByte(perPage); b.writeBoolean(open); }
        static Query read(FriendlyByteBuf b) { return new Query(b.readUtf(80), b.readByte(), b.readByte(), b.readVarInt(), b.readByte(), b.readBoolean()); }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> { if (p != null && p.hasPermissions(2)) Cmds.sendPage(p, this); });
            ctx.get().setPacketHandled(true);
        }
    }

    /** una fila: clave (id de resultado, de receta, o "mod:*"), mod, tipos (bits), cantidad de recetas, desactivada */
    public record Row(String key, String mod, int kinds, int count, boolean off) { }

    public record Page(boolean open, int total, int page, int disabledTotal, List<Row> rows) {
        void write(FriendlyByteBuf b) {
            b.writeBoolean(open); b.writeVarInt(total); b.writeVarInt(page); b.writeVarInt(disabledTotal);
            b.writeVarInt(rows.size());
            for (Row r : rows) { b.writeUtf(r.key(), 120); b.writeUtf(r.mod(), 60); b.writeByte(r.kinds()); b.writeVarInt(r.count()); b.writeBoolean(r.off()); }
        }

        static Page read(FriendlyByteBuf b) {
            boolean open = b.readBoolean(); int total = b.readVarInt(), page = b.readVarInt(), dt = b.readVarInt(), n = b.readVarInt();
            List<Row> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(new Row(b.readUtf(120), b.readUtf(60), b.readByte(), b.readVarInt(), b.readBoolean()));
            return new Page(open, total, page, dt, l);
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.crafteos.client.CraftScreen.update(this)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** el cliente pide un comando /crafteos (el servidor comprueba que sea operador) */
    public record Cmd(String line) {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p == null || !p.hasPermissions(2) || !line.startsWith("crafteos ")) return;
                p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), line);
                Query q = LAST.get(p.getUUID());
                if (q != null) Cmds.sendPage(p, new Query(q.search(), q.tipo(), q.estado(), q.page(), q.perPage(), false));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** lista de items prohibidos para los clientes (los esconden del inventario creativo) */
    public record Banned(java.util.List<String> ids) {
        void write(FriendlyByteBuf b) { b.writeVarInt(ids.size()); for (String s : ids) b.writeUtf(s, 120); }
        static Banned read(FriendlyByteBuf b) { int n = b.readVarInt(); List<String> l = new ArrayList<>(n); for (int i = 0; i < n; i++) l.add(b.readUtf(120)); return new Banned(l); }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.crafteos.client.ClientBans.set(new java.util.HashSet<>(ids))));
            ctx.get().setPacketHandled(true);
        }
    }

    public static void sendBanned(ServerPlayer p) { CH.send(PacketDistributor.PLAYER.with(() -> p), new Banned(new ArrayList<>(Bans.all()))); }

    public static void sendBanned(net.minecraft.server.MinecraftServer s) { CH.send(PacketDistributor.ALL.noArg(), new Banned(new ArrayList<>(Bans.all()))); }

    public static void send(ServerPlayer p, Page d) { CH.send(PacketDistributor.PLAYER.with(() -> p), d); }
}
