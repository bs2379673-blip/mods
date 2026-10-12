package com.tunombre.taaef;

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

/** Lo unico que TAA no sincroniza: el gesto de abrir la puerta con la mano (lo reenviamos a los que te ven). */
public final class Net {
    private static final String V = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(new ResourceLocation(TaaEF.MODID, "main"), () -> V, V::equals, V::equals);

    private Net() { }

    static void init() {
        CH.registerMessage(0, DoorPush.class, (m, b) -> { }, b -> new DoorPush(), DoorPush::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CH.registerMessage(1, PlayFx.class, (m, b) -> { b.writeVarInt(m.entity()); b.writeByte(m.kind()); }, b -> new PlayFx(b.readVarInt(), b.readByte()),
                PlayFx::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public record DoorPush() {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> { if (p != null) CH.send(PacketDistributor.TRACKING_ENTITY.with(() -> p), new PlayFx(p.getId(), 0)); });
            ctx.get().setPacketHandled(true);
        }
    }

    public record PlayFx(int entity, int kind) {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientDriver.remoteFx(entity, kind)));
            ctx.get().setPacketHandled(true);
        }
    }
}
