package com.annis.interact.client;

import com.annis.interact.Interaction;
import com.annis.npc.compat.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/** Avisos en pantalla: invitaciones, caido, como terminar. */
public final class InteractHud {
    private InteractHud() {
    }

    public static void render(GuiGraphics ctx, DeltaTracker counter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
        var tr = mc.font;

        // caido: borde rojo y cuenta atras
        if (ClientState.downed) {
            int a = 0x60 + (int) (0x30 * Math.sin(System.currentTimeMillis() / 300.0));
            int red = (a << 24) | 0x8A0000;
            ctx.fillGradient(0, 0, w, h / 5, red, 0x00000000);
            ctx.fillGradient(0, h - h / 5, w, h, 0x00000000, red);
            String t1 = "Estas caido - " + ClientState.downedSeconds + " s";
            String t2 = "Espera a que alguien te ayude (G)  -  manten Shift para rendirte";
            ctx.drawCenteredString(tr, Component.literal(t1), w / 2, h / 2 - 40, 0xFFFF5555);
            ctx.drawCenteredString(tr, Component.literal(t2), w / 2, h / 2 - 28, 0xFFE0C0C0);
        }

        // invitacion
        if (!ClientState.inviteFrom.isEmpty() && System.currentTimeMillis() < ClientState.inviteUntil) {
            Interaction i = Interaction.get(ClientState.inviteType);
            String what = i == null ? ClientState.inviteType : i.name();
            long secs = (ClientState.inviteUntil - System.currentTimeMillis() + 999) / 1000;
            String line1 = ClientState.inviteFrom + ": " + what + "?";
            String line2 = "[" + AnnisInteractClient.ACCEPT.getTranslatedKeyMessage().getString() + "] Aceptar   ["
                    + AnnisInteractClient.DECLINE.getTranslatedKeyMessage().getString() + "] Rechazar   (" + secs + ")";
            int bw = Math.max(tr.width(line1), tr.width(line2)) / 2 + 10;
            int y = 22;
            ctx.fill(w / 2 - bw, y - 6, w / 2 + bw, y + 22, 0xC0201028);
            ctx.renderOutline(w / 2 - bw, y - 6, bw * 2, 28, 0xFFB04A8A);
            ctx.drawCenteredString(tr, Component.literal(line1), w / 2, y, 0xFFFFD0F0);
            ctx.drawCenteredString(tr, Component.literal(line2), w / 2, y + 11, 0xFFC8C8D8);
        }

        // en una interaccion larga
        if (!ClientState.session.isEmpty()) {
            Interaction i = Interaction.get(ClientState.session);
            String txt = (i == null ? "" : i.name() + "  -  ") + (i != null && i.kind() == Interaction.Kind.CARRY
                    ? "Shift para bajar" : "Shift para terminar");
            ctx.drawCenteredString(tr, Component.literal(txt), w / 2, h - 70, 0xFFE8D0FF);
        } else if (!ClientState.downed && mc.screen == null) {
            // mirando a un caido
            Entity t = mc.crosshairPickEntity;
            if (t != null && AnnisInteractClient.isDowned(t)) {
                String key = AnnisInteractClient.WHEEL.getTranslatedKeyMessage().getString();
                ctx.drawCenteredString(tr, Component.literal("Manten " + key + ": ayudar a levantarse"), w / 2, h / 2 + 14, 0xFFFFE080);
            }
        }
    }
}
