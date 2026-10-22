package com.tunombre.lootmaster.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Mensajes del servidor al cliente. */
public final class ClientNet {
    private ClientNet() { }

    public static void handle(String kind, String json) {
        Minecraft mc = Minecraft.getInstance();
        switch (kind) {
            case "state" -> mc.tell(() -> LootScreen.onState(json));
            case "sel" -> mc.tell(() -> { WandRender.set(json); LootScreen.onSel(json); });
            case "tab" -> mc.tell(() -> LootScreen.wantTab(json));
            case "job" -> mc.tell(() -> LootScreen.onJob(json));
            case "msg" -> {
                if (mc.player != null) mc.player.displayClientMessage(Component.literal(json), true);
                LootScreen.notice(json);
            }
            default -> { }
        }
    }
}
