package com.tunombre.visionfx;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

public final class Net {
    private static final String V = "2";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(new ResourceLocation(VisionFX.MODID, "main"), () -> V, V::equals, V::equals);

    private Net() { }

    static void init() {
        CH.registerMessage(0, Sync.class, (m, b) -> m.p().write(b), b -> new Sync(Params.read(b)), Sync::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** el servidor manda a cada jugador sus parametros efectivos (ya con su anulacion aplicada en 'enabled') */
    public record Sync(Params p) {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.visionfx.client.FxClient.setServer(p)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static void send(ServerPlayer sp, Params p) { CH.send(PacketDistributor.PLAYER.with(() -> sp), new Sync(p)); }
}
