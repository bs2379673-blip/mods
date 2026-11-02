package com.starlevi.starriders.net;

import com.starlevi.starriders.PhoenixEntity;
import com.starlevi.starriders.StarRiders;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Mensajes: el jinete avisa si despego o aterrizo; el servidor manda las velocidades de vuelo. */
public final class Net {
    private Net() {
    }

    private static ResourceLocation id(String p) {
        return ResourceLocation.fromNamespaceAndPath(StarRiders.MOD_ID, p);
    }

    public record Flight(int entity, boolean flying) implements CustomPacketPayload {
        public static final Type<Flight> TYPE = new Type<>(id("vuelo"));
        public static final StreamCodec<FriendlyByteBuf, Flight> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeVarInt(p.entity);
                    b.writeBoolean(p.flying);
                }, b -> new Flight(b.readVarInt(), b.readBoolean()));

        @Override
        public Type<Flight> type() {
            return TYPE;
        }
    }

    public record Settings(double flySpeed, double flyBoost) implements CustomPacketPayload {
        public static final Type<Settings> TYPE = new Type<>(id("ajustes"));
        public static final StreamCodec<FriendlyByteBuf, Settings> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeDouble(p.flySpeed);
                    b.writeDouble(p.flyBoost);
                }, b -> new Settings(b.readDouble(), b.readDouble()));

        @Override
        public Type<Settings> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1").optional();
        r.playToServer(Flight.TYPE, Flight.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp && sp.level().getEntity(p.entity()) instanceof PhoenixEntity ph) {
                ph.clientSaysFlying(sp, p.flying());
            }
        });
        r.playToClient(Settings.TYPE, Settings.CODEC, (p, ctx) -> {
            PhoenixEntity.Settings.flySpeed = p.flySpeed();
            PhoenixEntity.Settings.flyBoost = p.flyBoost();
        });
    }
}
