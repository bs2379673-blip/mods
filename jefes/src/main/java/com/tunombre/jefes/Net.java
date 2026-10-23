package com.tunombre.jefes;

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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Red del mod. S2C: definiciones de jefes, aviso de control, camara de agarre, screamer. C2S: entrada y acciones del admin. */
public final class Net {
    private static final String V = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(new ResourceLocation(Jefes.MODID, "main"), () -> V, V::equals, V::equals);

    private Net() { }

    public static void init() {
        int i = 0;
        CH.registerMessage(i++, Defs.class, Defs::write, Defs::read, Defs::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH.registerMessage(i++, Possess.class, Possess::write, Possess::read, Possess::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH.registerMessage(i++, Cine.class, Cine::write, Cine::read, Cine::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH.registerMessage(i++, Scream.class, Scream::write, Scream::read, Scream::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH.registerMessage(i++, Game.class, Game::write, Game::read, Game::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH.registerMessage(i++, Burst.class, Burst::write, Burst::read, Burst::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH.registerMessage(i++, Input.class, Input::write, Input::read, Input::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CH.registerMessage(i++, Action.class, Action::write, Action::read, Action::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void toPlayer(ServerPlayer p, Object msg) { CH.send(PacketDistributor.PLAYER.with(() -> p), msg); }

    private static byte[] gz(byte[] b) {
        try (ByteArrayOutputStream o = new ByteArrayOutputStream(); GZIPOutputStream g = new GZIPOutputStream(o)) { g.write(b); g.finish(); return o.toByteArray(); } catch (IOException e) { throw new IllegalStateException(e); }
    }

    private static byte[] ungz(byte[] b) {
        try (GZIPInputStream g = new GZIPInputStream(new ByteArrayInputStream(b))) { return g.readAllBytes(); } catch (IOException e) { throw new IllegalStateException(e); }
    }

    /** un jefe completo (definicion + modelo + textura), comprimido */
    public record Defs(byte[] spec, byte[] model, byte[] png) {
        public static Defs of(Boss b) { return new Defs(gz(b.specJson.getBytes(StandardCharsets.UTF_8)), gz(b.modelJson.getBytes(StandardCharsets.UTF_8)), b.png); }

        void write(FriendlyByteBuf b) { b.writeByteArray(spec); b.writeByteArray(model); b.writeByteArray(png); }

        static Defs read(FriendlyByteBuf b) { return new Defs(b.readByteArray(1 << 20), b.readByteArray(1 << 20), b.readByteArray(1 << 20)); }

        void handle(Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.jefes.client.ClientHooks.defs(new String(ungz(spec), StandardCharsets.UTF_8), new String(ungz(model), StandardCharsets.UTF_8), png)));
            c.get().setPacketHandled(true);
        }
    }

    /** empezas (id de la entidad) o dejas (-1) de controlar un jefe */
    public record Possess(int entity) {
        void write(FriendlyByteBuf b) { b.writeVarInt(entity + 1); }

        static Possess read(FriendlyByteBuf b) { return new Possess(b.readVarInt() - 1); }

        void handle(Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.jefes.client.ClientHooks.possess(entity)));
            c.get().setPacketHandled(true);
        }
    }

    /** camara de agarre: entidad del jefe (o -1 para terminar) */
    public record Cine(int entity) {
        void write(FriendlyByteBuf b) { b.writeVarInt(entity + 1); }

        static Cine read(FriendlyByteBuf b) { return new Cine(b.readVarInt() - 1); }

        void handle(Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.jefes.client.ClientHooks.cine(entity)));
            c.get().setPacketHandled(true);
        }
    }

    /** screamer propio: segundos, y si hay que dibujar el susto (si no, lo dibuja tacui) */
    public record Scream(float secs, boolean visual) {
        void write(FriendlyByteBuf b) { b.writeFloat(secs); b.writeBoolean(visual); }

        static Scream read(FriendlyByteBuf b) { return new Scream(b.readFloat(), b.readBoolean()); }

        void handle(Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.jefes.client.ClientHooks.scream(secs, visual)));
            c.get().setPacketHandled(true);
        }
    }

    /** estado del minijuego de las paginas */
    public record Game(int collected, int total, boolean active) {
        void write(FriendlyByteBuf b) { b.writeVarInt(collected); b.writeVarInt(total); b.writeBoolean(active); }

        static Game read(FriendlyByteBuf b) { return new Game(b.readVarInt(), b.readVarInt(), b.readBoolean()); }

        void handle(Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.jefes.client.SlenderFx.game(collected, total, active)));
            c.get().setPacketHandled(true);
        }
    }

    /** rafaga de glitch en la pantalla: ticks, fuerza 0..1 y si muestra la cara de Slenderman */
    public record Burst(int ticks, float strength, boolean flash) {
        void write(FriendlyByteBuf b) { b.writeVarInt(ticks); b.writeFloat(strength); b.writeBoolean(flash); }

        static Burst read(FriendlyByteBuf b) { return new Burst(b.readVarInt(), b.readFloat(), b.readBoolean()); }

        void handle(Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.jefes.client.SlenderFx.burst(ticks, strength, flash)));
            c.get().setPacketHandled(true);
        }
    }

    /** lo que aprieta el admin cada tick mientras controla un jefe */
    public record Input(float fwd, float strafe, boolean jump, boolean sprint, boolean crouch, float yaw, float pitch) {
        void write(FriendlyByteBuf b) { b.writeFloat(fwd); b.writeFloat(strafe); b.writeBoolean(jump); b.writeBoolean(sprint); b.writeBoolean(crouch); b.writeFloat(yaw); b.writeFloat(pitch); }

        static Input read(FriendlyByteBuf b) { return new Input(b.readFloat(), b.readFloat(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readFloat(), b.readFloat()); }

        void handle(Supplier<NetworkEvent.Context> c) {
            ServerPlayer p = c.get().getSender();
            c.get().enqueueWork(() -> { if (p != null) Possession.input(p, this); });
            c.get().setPacketHandled(true);
        }
    }

    /** accion del admin (ver {@link JefeEntity#doAction}) */
    public record Action(int id) {
        void write(FriendlyByteBuf b) { b.writeByte(id); }

        static Action read(FriendlyByteBuf b) { return new Action(b.readByte()); }

        void handle(Supplier<NetworkEvent.Context> c) {
            ServerPlayer p = c.get().getSender();
            c.get().enqueueWork(() -> { if (p != null) Possession.action(p, id); });
            c.get().setPacketHandled(true);
        }
    }
}
