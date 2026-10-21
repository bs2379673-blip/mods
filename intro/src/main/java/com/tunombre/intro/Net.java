package com.tunombre.intro;

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
import java.util.function.Supplier;

/** Un solo mensaje servidor -> cliente: un paso de la escena como JSON ({"op":"fundido",...}). */
public final class Net {
    private static final String V = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(new ResourceLocation(Intro.MODID, "main"), () -> V, V::equals, V::equals);

    private Net() { }

    static void init() {
        CH.registerMessage(0, Msg.class, Msg::write, Msg::read, Msg::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public record Msg(String json) {
        void write(FriendlyByteBuf b) { b.writeUtf(json, 32000); }

        static Msg read(FriendlyByteBuf b) { return new Msg(b.readUtf(32000)); }

        void handle(Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.intro.client.IntroClient.handle(json)));
            c.get().setPacketHandled(true);
        }
    }

    static void send(ServerPlayer p, String json) { CH.send(PacketDistributor.PLAYER.with(() -> p), new Msg(json)); }
}
