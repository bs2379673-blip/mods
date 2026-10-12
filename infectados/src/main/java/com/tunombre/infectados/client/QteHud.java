package com.tunombre.infectados.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** quick time event de los agarres: barra de fuerza + tecla de saltar a spamear */
public final class QteHud {
    static volatile int grip = 50, kind;
    static volatile boolean active;
    static float shown = 50, flash;
    static long last;

    private QteHud() { }

    public static void update(int g, boolean a, int k) {
        grip = g;
        active = a;
        kind = k;
        last = System.currentTimeMillis();
    }

    static boolean on() {
        Minecraft mc = Minecraft.getInstance();
        if (active && System.currentTimeMillis() - last > 1500) active = false;
        return active && mc.player != null && mc.player.isAlive();
    }

    static void pressed() { flash = 1F; }

    static void render(GuiGraphics g, float pt) {
        if (!on()) { shown = 50; return; }
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        shown += (grip - shown) * 0.25F;
        float danger = 1F - shown / 100F;
        float t = mc.player.tickCount + pt;
        int a = (int) (50 + 120 * danger * (0.6F + 0.4F * Mth.sin(t * 0.5F)));
        int red = (a << 24) | 0x5A0000;
        int edge = Math.max(30, h / 5);
        g.fillGradient(0, 0, w, edge, red, 0);
        g.fillGradient(0, h - edge, w, h, 0, red);
        int sx = (int) (Mth.sin(t * 2.1F) * 3 * danger), sy = (int) (Mth.cos(t * 1.7F) * 2 * danger);
        int cx = w / 2 + sx, y = h - 86 + sy;
        String title = kind == 1 ? "¡QUE NO TE CORTE LA CABEZA!" : "¡ZÁFATE!";
        g.drawString(mc.font, title, cx - mc.font.width(title) / 2, y - 26, 0xFFFF4040, true);
        String key = mc.options.keyJump.getTranslatedKeyMessage().getString().toUpperCase();
        float pop = 1F + 0.25F * flash;
        flash *= 0.8F;
        int kw = Math.max(44, mc.font.width(key) + 18), kh = 20;
        g.pose().pushPose();
        g.pose().translate(cx, y - 2, 0);
        g.pose().scale(pop, pop, 1);
        g.fill(-kw / 2 - 1, -kh / 2 - 1, kw / 2 + 1, kh / 2 + 1, 0xFFE8E8E8);
        g.fill(-kw / 2, -kh / 2, kw / 2, kh / 2, 0xFF1A1A1A);
        g.drawString(mc.font, key, -mc.font.width(key) / 2, -4, 0xFFFFFFFF, false);
        g.pose().popPose();
        if ((int) (t / 4) % 2 == 0) g.drawString(mc.font, "¡SPAMEA!", cx + kw / 2 + 10, y - 6, 0xFFFFD040, true);
        int bw = 220, bh = 10, bx = cx - bw / 2, by = y + 16;
        g.fill(bx - 2, by - 2, bx + bw + 2, by + bh + 2, 0xC0000000);
        int col = shown < 30 ? 0xFFD02020 : shown < 60 ? 0xFFE0A020 : 0xFF40C040;
        g.fill(bx, by, bx + (int) (bw * Mth.clamp(shown / 100F, 0F, 1F)), by + bh, col);
        g.fill(bx + bw - 2, by - 3, bx + bw, by + bh + 3, 0xFFFFFFFF);
        String l = kind == 1 ? "DECAPITADO" : "TE MUERDE", r = "LIBRE";
        g.drawString(mc.font, l, bx, by + bh + 4, 0xFFB04040, true);
        g.drawString(mc.font, r, bx + bw - mc.font.width(r), by + bh + 4, 0xFF60D060, true);
    }

    static float shake() { return on() ? 0.3F + 0.7F * (1F - shown / 100F) * (kind == 1 ? 1.4F : 1F) : 0F; }
}
