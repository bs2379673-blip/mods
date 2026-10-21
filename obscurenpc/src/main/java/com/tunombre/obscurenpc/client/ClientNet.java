package com.tunombre.obscurenpc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Mensajes del servidor al cliente: abrir el editor y avisos. */
public final class ClientNet {
    private ClientNet() { }

    public static void handle(String kind, String json) {
        Minecraft mc = Minecraft.getInstance();
        switch (kind) {
            case "editor" -> mc.tell(() -> NpcPanel.open(json));
            case "msg" -> {
                if (mc.player != null) mc.player.displayClientMessage(Component.literal(json), true);
                NpcPanel.notice(json);
            }
            default -> { }
        }
    }
}
