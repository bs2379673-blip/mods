package com.tunombre.cosmeticos;

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

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class Net {
    private static final String V = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(new ResourceLocation(Cosmeticos.MODID, "main"), () -> V, V::equals, V::equals);

    private Net() { }

    static void init() {
        CH.registerMessage(0, Sync.class, (m, b) -> { b.writeUUID(m.id()); b.writeLong(m.mask()); },
                b -> new Sync(b.readUUID(), b.readLong()), Sync::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** mascara de bits de cosmeticos de un jugador */
    public record Sync(UUID id, long mask) {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.cosmeticos.client.CosClient.set(id, mask)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static void toAll(UUID id, long mask) { CH.send(PacketDistributor.ALL.noArg(), new Sync(id, mask)); }

    public static void to(ServerPlayer sp, UUID id, long mask) { CH.send(PacketDistributor.PLAYER.with(() -> sp), new Sync(id, mask)); }
}
