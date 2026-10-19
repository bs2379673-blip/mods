package com.tunombre.modelos;

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
import java.util.Optional;
import java.util.function.Supplier;

public final class Net {
    private static final String V = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(new ResourceLocation(Modelos.MODID, "main"), () -> V, V::equals, V::equals);

    private Net() { }

    static void init() {
        CH.registerMessage(0, Data.class, Data::write, Data::read, Data::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH.registerMessage(2, Models.class, Models::write, Models::read, Models::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH.registerMessage(1, Cmd.class, (m, b) -> b.writeUtf(m.line(), 300), b -> new Cmd(b.readUtf(300)), Cmd::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    /** un jugador conectado y el modelo que tiene puesto por este mod ("" = ninguno) */
    public record Who(String name, String model) { }

    /** lo que muestra el menu: modelos de la carpeta, jugadores, y si CPM / Epic Fight estan instalados */
    public record Data(boolean open, boolean cpm, boolean epic, List<String> models, List<Who> players) {
        void write(FriendlyByteBuf b) {
            b.writeBoolean(open); b.writeBoolean(cpm); b.writeBoolean(epic);
            b.writeVarInt(models.size());
            for (String m : models) b.writeUtf(m, 100);
            b.writeVarInt(players.size());
            for (Who w : players) { b.writeUtf(w.name(), 40); b.writeUtf(w.model(), 100); }
        }

        static Data read(FriendlyByteBuf b) {
            boolean open = b.readBoolean(), cpm = b.readBoolean(), epic = b.readBoolean();
            int n = b.readVarInt();
            List<String> ms = new ArrayList<>(n);
            for (int i = 0; i < n; i++) ms.add(b.readUtf(100));
            int k = b.readVarInt();
            List<Who> ps = new ArrayList<>(k);
            for (int i = 0; i < k; i++) ps.add(new Who(b.readUtf(40), b.readUtf(100)));
            return new Data(open, cpm, epic, ms, ps);
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> com.tunombre.modelos.client.ModelScreen.update(this)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** el cliente pide un comando /modelos (el servidor comprueba que sea operador) */
    public record Cmd(String line) {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p == null || !p.hasPermissions(2) || !line.startsWith("modelos ")) return;
                p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), line);
                Server.sendMenu(p, false);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** los jugadores que tienen un modelo puesto (Epic Fight no puede dibujarlos ni activarse para ellos); va a todos los clientes */
    public record Models(List<java.util.UUID> ids) {
        void write(FriendlyByteBuf b) { b.writeVarInt(ids.size()); for (java.util.UUID u : ids) b.writeUUID(u); }

        static Models read(FriendlyByteBuf b) {
            int k = b.readVarInt();
            List<java.util.UUID> l = new ArrayList<>(k);
            for (int i = 0; i < k; i++) l.add(b.readUUID());
            return new Models(l);
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> com.tunombre.modelos.client.LockClient.set(ids)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static void send(ServerPlayer p, Models m) { CH.send(PacketDistributor.PLAYER.with(() -> p), m); }

    public static void sendAll(Models m) { CH.send(PacketDistributor.ALL.noArg(), m); }

    public static void send(ServerPlayer p, Data d) { CH.send(PacketDistributor.PLAYER.with(() -> p), d); }
}
