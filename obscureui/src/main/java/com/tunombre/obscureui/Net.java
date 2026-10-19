package com.tunombre.obscureui;

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

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Supplier;

public final class Net {
    private static final String V = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(new ResourceLocation(ObscureUI.MODID, "main"), () -> V, V::equals, V::equals);

    private Net() { }

    static void init() {
        CH.registerMessage(0, Cmd.class, Cmd::write, Cmd::read, Cmd::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CH.registerMessage(1, Req.class, Req::write, Req::read, Req::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CH.registerMessage(2, Data.class, Data::write, Data::read, Data::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** El cliente pide ejecutar un comando (con los permisos del propio jugador: hace falta ser operador). */
    public record Cmd(String cmd) {
        void write(FriendlyByteBuf b) { b.writeUtf(cmd, 600); }
        static Cmd read(FriendlyByteBuf b) { return new Cmd(b.readUtf(600)); }
        void handle(Supplier<NetworkEvent.Context> c) {
            ServerPlayer p = c.get().getSender();
            c.get().enqueueWork(() -> {
                if (p == null || !p.hasPermissions(2)) return;
                String s = cmd.trim();
                if (s.startsWith("/")) s = s.substring(1);
                if (s.isEmpty()) return;
                p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), s);
            });
            c.get().setPacketHandled(true);
        }
    }

    /** Peticiones de datos / guardado de los paneles propios. */
    public record Req(String key, String payload) {
        void write(FriendlyByteBuf b) { b.writeUtf(key, 64); b.writeUtf(payload, 32000); }
        static Req read(FriendlyByteBuf b) { return new Req(b.readUtf(64), b.readUtf(32000)); }
        void handle(Supplier<NetworkEvent.Context> c) {
            ServerPlayer p = c.get().getSender();
            c.get().enqueueWork(() -> { if (p != null && p.hasPermissions(2)) Panels.handle(p, this); });
            c.get().setPacketHandled(true);
        }
    }

    public record Data(String key, String json) {
        void write(FriendlyByteBuf b) { b.writeUtf(key, 64); b.writeUtf(json, 32000); }
        static Data read(FriendlyByteBuf b) { return new Data(b.readUtf(64), b.readUtf(32000)); }
        void handle(Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.obscureui.client.ObscureClient.onData(this)));
            c.get().setPacketHandled(true);
        }
    }

    public static void toClient(ServerPlayer p, String key, String json) { CH.send(PacketDistributor.PLAYER.with(() -> p), new Data(key, json)); }

    /** Llama a un metodo estatico de otro mod por reflexion (sin dependencia dura). */
    static String call(String cls, String method, Object... args) {
        try {
            Class<?>[] types = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) types[i] = args[i].getClass();
            Method m = Class.forName(cls).getMethod(method, types);
            Object r = m.invoke(null, args);
            return r == null ? "" : r.toString();
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Throwable t) {
            ObscureUI.LOGGER.warn("Obscure UI: fallo llamando {}.{}: {}", cls, method, t.toString());
            return null;
        }
    }
}
