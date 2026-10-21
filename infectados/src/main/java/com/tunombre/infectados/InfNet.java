package com.tunombre.infectados;

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

public final class InfNet {
    private static final String V = "2";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(new ResourceLocation(Infectados.MODID, "main"), () -> V, V::equals, V::equals);

    private InfNet() { }

    static void init() {
        CH.registerMessage(0, Hold.class, Hold::write, Hold::read, Hold::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH.registerMessage(4, Drag.class, (m, b) -> b.writeBoolean(m.on()), b -> new Drag(b.readBoolean()), Drag::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** el servidor te tiene agarrado (activo) o te suelta: el cliente no debe bajarse solo y la camara se sacude. kind: 0 aldeano, 1 motosierra */
    public record Hold(boolean active, int kind) {
        void write(FriendlyByteBuf b) {
            b.writeBoolean(active);
            b.writeByte(kind);
        }

        static Hold read(FriendlyByteBuf b) { return new Hold(b.readBoolean(), b.readByte()); }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> { Infectados.clientHeld = active; DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.infectados.client.HoldFx.set(active, kind)); });
            ctx.get().setPacketHandled(true);
        }
    }

    /** arrastrado por el Smoker: el cliente se pone tumbado (camara baja) */
    public record Drag(boolean on) {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                net.minecraft.client.player.LocalPlayer lp = net.minecraft.client.Minecraft.getInstance().player;
                if (lp != null) lp.setForcedPose(on ? net.minecraft.world.entity.Pose.SWIMMING : null);
            }));
            ctx.get().setPacketHandled(true);
        }
    }
}
