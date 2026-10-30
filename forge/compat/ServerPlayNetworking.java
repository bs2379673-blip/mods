package __PKG__;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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
        Net.onServer(type, (p, ctx) -> {
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                handler.receive(p, () -> sender);
            }
        });
        return true;
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        Net.toPlayer(player, payload);
    }

    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection != null && Net.channel(type.id().getNamespace()).isRemotePresent(player.connection.connection);
    }
}
