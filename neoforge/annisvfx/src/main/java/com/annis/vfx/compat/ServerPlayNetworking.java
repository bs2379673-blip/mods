package com.annis.vfx.compat;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ServerPlayNetworking {
    private ServerPlayNetworking() {
    }

    public interface Context {
        ServerPlayer player();

        default MinecraftServer server() {
            return player().server;
        }
    }

    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }

    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
        Net.onServer(type, (p, player) -> {
            if (player instanceof ServerPlayer sp) {
                handler.receive(p, () -> sp);
            }
        });
        return true;
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (canSend(player, payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection != null && player.connection.hasChannel(type);
    }
}
