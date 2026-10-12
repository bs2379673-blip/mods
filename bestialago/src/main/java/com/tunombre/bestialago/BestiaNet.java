package com.tunombre.bestialago;

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

/** Red: el cliente manda cada pulsacion del quick time event; el servidor manda el estado de la barra. */
public final class BestiaNet {
    private static final String V = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(new ResourceLocation(BestiaLago.MODID, "main"), () -> V, V::equals, V::equals);

    private BestiaNet() { }

    public static void init() {
        CH.registerMessage(0, Struggle.class, (m, b) -> { }, b -> new Struggle(), Struggle::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CH.registerMessage(1, Qte.class, Qte::write, Qte::read, Qte::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public record Struggle() {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p != null && p.getVehicle() instanceof Bestia b) b.struggle(p);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record Qte(int grip, boolean active) {
        void write(FriendlyByteBuf b) {
            b.writeByte(grip);
            b.writeBoolean(active);
        }

        static Qte read(FriendlyByteBuf b) { return new Qte(b.readByte(), b.readBoolean()); }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> { BestiaLago.clientHeld = active; DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.bestialago.client.QteHud.update(grip, active)); });
            ctx.get().setPacketHandled(true);
        }
    }
}
