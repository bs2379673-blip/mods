package com.tunombre.interacciones.client;

import com.tunombre.interacciones.Act;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** aviso de solicitud: 'X quiere chocar los cinco   [Y] aceptar   [N] rechazar' con barra de tiempo */
public final class PromptHud {
    private static String from = "";
    private static int action;
    private static long until, start;

    private PromptHud() { }

    public static void set(String f, int a, int ticks, boolean on) {
        if (!on) { clear(); return; }
        from = f;
        action = a;
        start = System.currentTimeMillis();
        until = start + ticks * 50L;
    }

    static void clear() { until = 0; }

    static boolean active() { return System.currentTimeMillis() < until; }

    static void render(GuiGraphics g, int w, int h) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        String l1 = from + " quiere " + Act.VERB[Math.max(0, Math.min(Act.VERB.length - 1, action))];
        String l2 = "[" + ClientSetup.ACCEPT.getTranslatedKeyMessage().getString().toUpperCase() + "] aceptar     ["
                + ClientSetup.DECLINE.getTranslatedKeyMessage().getString().toUpperCase() + "] rechazar";
        int bw = Math.max(mc.font.width(l1), mc.font.width(l2)) + 24, bh = 34;
        int x = (w - bw) / 2, y = h / 2 + 28;
        g.fill(x, y, x + bw, y + bh, 0xC0101014);
        g.fill(x, y, x + bw, y + 1, 0xFF58C8FF);
        float k = (float) (until - System.currentTimeMillis()) / Math.max(1, until - start);
        g.fill(x, y + bh - 2, x + (int) (bw * k), y + bh, 0xFF58C8FF);
        g.drawCenteredString(mc.font, l1, w / 2, y + 6, 0xFFFFFFFF);
        g.drawCenteredString(mc.font, l2, w / 2, y + 18, 0xFFB8E4FF);
    }
}
