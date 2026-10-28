package com.annis.interact.client;

import com.annis.interact.Interaction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;

/** Avisos en pantalla: invitaciones, caido, como terminar. */
public final class InteractHud {
    private InteractHud() {
    }

    public static void render(DrawContext ctx, RenderTickCounter counter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) {
            return;
        }
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        var tr = mc.textRenderer;

        // caido: borde rojo y cuenta atras
        if (ClientState.downed) {
            int a = 0x60 + (int) (0x30 * Math.sin(System.currentTimeMillis() / 300.0));
            int red = (a << 24) | 0x8A0000;
            ctx.fillGradient(0, 0, w, h / 5, red, 0x00000000);
            ctx.fillGradient(0, h - h / 5, w, h, 0x00000000, red);
            String t1 = "Estas caido - " + ClientState.downedSeconds + " s";
            String t2 = "Espera a que alguien te ayude (G)  -  manten Shift para rendirte";
            ctx.drawCenteredTextWithShadow(tr, Text.literal(t1), w / 2, h / 2 - 40, 0xFFFF5555);
            ctx.drawCenteredTextWithShadow(tr, Text.literal(t2), w / 2, h / 2 - 28, 0xFFE0C0C0);
        }

        // invitacion
        if (!ClientState.inviteFrom.isEmpty() && System.currentTimeMillis() < ClientState.inviteUntil) {
            Interaction i = Interaction.get(ClientState.inviteType);
            String what = i == null ? ClientState.inviteType : i.name();
            long secs = (ClientState.inviteUntil - System.currentTimeMillis() + 999) / 1000;
            String line1 = ClientState.inviteFrom + ": " + what + "?";
            String line2 = "[" + AnnisInteractClient.ACCEPT.getBoundKeyLocalizedText().getString() + "] Aceptar   ["
                    + AnnisInteractClient.DECLINE.getBoundKeyLocalizedText().getString() + "] Rechazar   (" + secs + ")";
            int bw = Math.max(tr.getWidth(line1), tr.getWidth(line2)) / 2 + 10;
            int y = 22;
            ctx.fill(w / 2 - bw, y - 6, w / 2 + bw, y + 22, 0xC0201028);
            ctx.drawBorder(w / 2 - bw, y - 6, bw * 2, 28, 0xFFB04A8A);
            ctx.drawCenteredTextWithShadow(tr, Text.literal(line1), w / 2, y, 0xFFFFD0F0);
            ctx.drawCenteredTextWithShadow(tr, Text.literal(line2), w / 2, y + 11, 0xFFC8C8D8);
        }

        // en una interaccion larga
        if (!ClientState.session.isEmpty()) {
            Interaction i = Interaction.get(ClientState.session);
            String txt = (i == null ? "" : i.name() + "  -  ") + (i != null && i.kind() == Interaction.Kind.CARRY
                    ? "Shift para bajar" : "Shift para terminar");
            ctx.drawCenteredTextWithShadow(tr, Text.literal(txt), w / 2, h - 70, 0xFFE8D0FF);
        } else if (!ClientState.downed && mc.currentScreen == null) {
            // mirando a un caido
            Entity t = mc.targetedEntity;
            if (t != null && AnnisInteractClient.isDowned(t)) {
                String key = AnnisInteractClient.WHEEL.getBoundKeyLocalizedText().getString();
                ctx.drawCenteredTextWithShadow(tr, Text.literal("Manten " + key + ": ayudar a levantarse"), w / 2, h / 2 + 14, 0xFFFFE080);
            }
        }
    }
}
