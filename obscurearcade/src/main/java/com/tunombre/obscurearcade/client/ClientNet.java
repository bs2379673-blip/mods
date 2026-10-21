package com.tunombre.obscurearcade.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ClientNet {
    private ClientNet() { }

    public static void handle(String kind, String json) {
        Minecraft mc = Minecraft.getInstance();
        switch (kind) {
            case "cfg" -> mc.setScreen(new ConfigScreen(json));
            case "duel" -> ClientState.dueling = json.equals("on");
            case "msg" -> { if (mc.player != null) mc.player.displayClientMessage(Component.literal(json), false); }
            default -> { }
        }
    }
}
