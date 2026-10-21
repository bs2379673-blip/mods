package com.tunombre.bestialago;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/** Red: el servidor avisa al cliente de que la bestia lo tiene agarrado (el quick time lo gestiona el mod QuickTime Events). */
public final class BestiaNet {
    private static final String V = "2";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(new ResourceLocation(BestiaLago.MODID, "main"), () -> V, V::equals, V::equals);

    private BestiaNet() { }

    public static void init() {
        CH.registerMessage(0, Hold.class, Hold::write, Hold::read, Hold::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** la bestia te tiene en la boca (activo) o te suelta */
    public record Hold(boolean active) {
        void write(FriendlyByteBuf b) { b.writeBoolean(active); }

        static Hold read(FriendlyByteBuf b) { return new Hold(b.readBoolean()); }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> { BestiaLago.clientHeld = active; DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.bestialago.client.HoldFx.set(active)); });
            ctx.get().setPacketHandled(true);
        }
    }
}
