package com.starlevi.starscenes.client;

import com.starlevi.starscenes.client.ui.UI;
import com.starlevi.starscenes.net.Net;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.List;

/** El cartel de misiones arriba a la izquierda (lo arma el servidor). */
public final class QuestHud {
    public static volatile List<Component> lines = List.of();

    private QuestHud() {
    }

    public static void register() {
        HudElementRegistry.addLast(Net.id("misiones"), (g, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            List<Component> l = lines;
            if (l.isEmpty() || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || mc.screen instanceof TalkScreen) {
                return;
            }
            Font f = mc.font;
            int w = 0;
            for (Component c : l) {
                w = Math.max(w, f.width(c));
            }
            w = Math.min(w, 220);
            int x = 4, y = 4, h = l.size() * 10 + 8;
            UI.box(g, x, y, w + 12, h, 0xA0100C18, 0xC03D3156);
            g.fill(x + 1, y + 1, x + 3, y + h - 1, UI.ACCENT);
            for (int i = 0; i < l.size(); i++) {
                g.drawString(f, l.get(i), x + 7, y + 5 + i * 10, 0xFFFFFFFF, true);
            }
        });
    }
}
