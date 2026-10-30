package com.annis.npc.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientPlayNetworking {
    private ClientPlayNetworking() {
    }

    public interface Context {
        default Minecraft client() {
            return Minecraft.getInstance();
        }

        default LocalPlayer player() {
            return Minecraft.getInstance().player;
        }
    }

    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }

    private static final Context CTX = new Context() {
    };

    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
        Net.onClient(type, (p, player) -> handler.receive(p, CTX));
        return true;
    }

    public static void send(CustomPacketPayload payload) {
        if (canSend(payload.type())) {
            PacketDistributor.sendToServer(payload);
        }
    }

    public static boolean canSend(CustomPacketPayload.Type<?> type) {
        Minecraft mc = Minecraft.getInstance();
        return mc.getConnection() != null && mc.getConnection().hasChannel(type);
    }
}
