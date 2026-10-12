package com.tunombre.interacciones;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

public final class Net {
    private static final String V = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(new ResourceLocation(Interacciones.MODID, "main"), () -> V, V::equals, V::equals);

    private Net() { }

    static void init() {
        CH.registerMessage(0, Request.class, (m, b) -> { b.writeByte(m.action()); b.writeVarInt(m.target()); }, b -> new Request(b.readByte(), b.readVarInt()), Request::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CH.registerMessage(1, Answer.class, (m, b) -> b.writeBoolean(m.accept()), b -> new Answer(b.readBoolean()), Answer::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CH.registerMessage(2, Prompt.class, Prompt::write, Prompt::read, Prompt::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** el jugador pide una interaccion con otro (target = id de entidad) */
    public record Request(int action, int target) {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> { if (p != null) Sessions.request(p, action, target); });
            ctx.get().setPacketHandled(true);
        }
    }

    /** respuesta a una solicitud */
    public record Answer(boolean accept) {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> { if (p != null) Sessions.answer(p, accept); });
            ctx.get().setPacketHandled(true);
        }
    }

    /** aviso en pantalla: 'X quiere ...  [Y] aceptar [N] rechazar' */
    public record Prompt(String from, int action, int ticks, boolean active) {
        void write(FriendlyByteBuf b) {
            b.writeUtf(from, 64);
            b.writeByte(action);
            b.writeVarInt(ticks);
            b.writeBoolean(active);
        }

        static Prompt read(FriendlyByteBuf b) { return new Prompt(b.readUtf(64), b.readByte(), b.readVarInt(), b.readBoolean()); }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.interacciones.client.PromptHud.set(from, action, ticks, active)));
            ctx.get().setPacketHandled(true);
        }
    }
}
