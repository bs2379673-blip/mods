package __PKG__;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Los paquetes se anotan al arrancar y se registran en NeoForge cuando llega el evento de registro.
 * Son "opcionales": un cliente o servidor sin el mod puede conectarse igual.
 */
public final class Net {
    record Entry(CustomPacketPayload.Type<?> type, StreamCodec<? super RegistryFriendlyByteBuf, ?> codec, boolean toServer) {
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final Map<ResourceLocation, BiConsumer<CustomPacketPayload, Player>> SERVER = new HashMap<>();
    private static final Map<ResourceLocation, BiConsumer<CustomPacketPayload, Player>> CLIENT = new HashMap<>();
    private static boolean hooked;

    private Net() {
    }

    static synchronized void register(CustomPacketPayload.Type<?> type, StreamCodec<? super RegistryFriendlyByteBuf, ?> codec, boolean toServer) {
        if (!hooked) {
            hooked = true;
            Compat.modBus.addListener(Net::onRegister);
        }
        ENTRIES.add(new Entry(type, codec, toServer));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onRegister(RegisterPayloadHandlersEvent e) {
        Map<String, PayloadRegistrar> registrars = new HashMap<>();
        for (Entry en : ENTRIES) {
            String ns = en.type().id().getNamespace();
            PayloadRegistrar r = registrars.computeIfAbsent(ns, k -> e.registrar("1").optional());
            ResourceLocation id = en.type().id();
            if (en.toServer()) {
                r.playToServer((CustomPacketPayload.Type) en.type(), (StreamCodec) en.codec(), (p, ctx) -> {
                    BiConsumer<CustomPacketPayload, Player> h = SERVER.get(id);
                    if (h != null) h.accept((CustomPacketPayload) p, ctx.player());
                });
            } else {
                r.playToClient((CustomPacketPayload.Type) en.type(), (StreamCodec) en.codec(), (p, ctx) -> {
                    BiConsumer<CustomPacketPayload, Player> h = CLIENT.get(id);
                    if (h != null) h.accept((CustomPacketPayload) p, ctx.player());
                });
            }
        }
    }

    @SuppressWarnings("unchecked")
    static <T extends CustomPacketPayload> void onServer(CustomPacketPayload.Type<T> type, BiConsumer<T, Player> h) {
        SERVER.put(type.id(), (BiConsumer<CustomPacketPayload, Player>) (BiConsumer<?, ?>) h);
    }

    @SuppressWarnings("unchecked")
    static <T extends CustomPacketPayload> void onClient(CustomPacketPayload.Type<T> type, BiConsumer<T, Player> h) {
        CLIENT.put(type.id(), (BiConsumer<CustomPacketPayload, Player>) (BiConsumer<?, ?>) h);
    }
}
