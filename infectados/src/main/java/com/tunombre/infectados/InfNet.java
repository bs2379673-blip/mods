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
    private static final String V = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(new ResourceLocation(Infectados.MODID, "main"), () -> V, V::equals, V::equals);

    private InfNet() { }

    static void init() {
        CH.registerMessage(0, Struggle.class, (m, b) -> { }, b -> new Struggle(), Struggle::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CH.registerMessage(1, Qte.class, Qte::write, Qte::read, Qte::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public record Struggle() {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p != null && p.getVehicle() instanceof InfMob m) m.struggle(p);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** estado del quick time event: barra 0-100, activo, tipo (0 aldeano, 1 motosierra) */
    public record Qte(int grip, boolean active, int kind) {
        void write(FriendlyByteBuf b) {
            b.writeByte(grip);
            b.writeBoolean(active);
            b.writeByte(kind);
        }

        static Qte read(FriendlyByteBuf b) { return new Qte(b.readByte(), b.readBoolean(), b.readByte()); }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.infectados.client.QteHud.update(grip, active, kind)));
            ctx.get().setPacketHandled(true);
        }
    }
}
