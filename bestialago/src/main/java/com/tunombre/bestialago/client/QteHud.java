package com.tunombre.bestialago.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Quick time event: barra de fuerza + tecla a spamear. Se enciende cuando el servidor dice que estas en la boca. */
public final class QteHud {
    static volatile int grip = 50;
    static volatile boolean active;
    static float shown = 50;
    static long lastUpdate;
    static int presses;
    static float flash;

    private QteHud() { }

    public static void update(int g, boolean a) {
        grip = g;
        active = a;
        lastUpdate = System.currentTimeMillis();
    }

    static boolean on() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() instanceof com.tunombre.bestialago.Bestia b && b.getState() == com.tunombre.bestialago.Bestia.GRAB) {
            // dato sincronizado de la propia bestia: funciona aunque no llegue el paquete del QTE
            grip = Math.round(b.getGrip());
            active = true;
            lastUpdate = System.currentTimeMillis();
        }
        if (active && System.currentTimeMillis() - lastUpdate > 1500) active = false;   // se perdio el paquete de fin
        return active && mc.player != null && mc.player.isAlive();
    }

    static void pressed() {
        presses++;
        flash = 1F;
    }

    static void render(GuiGraphics g, float pt) {
        if (!on()) {
            shown = 50;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        shown += (grip - shown) * 0.25F;
        float danger = 1F - shown / 100F;
        float t = (mc.player.tickCount + pt);
        // vineta roja que late mas fuerte cuando la boca se esta cerrando
        int a = (int) (60 + 110 * danger * (0.6F + 0.4F * Mth.sin(t * 0.5F)));
        int red = (a << 24) | 0x5A0000;
        int edge = Math.max(30, h / 5);
        g.fillGradient(0, 0, w, edge, red, 0x00000000);
        g.fillGradient(0, h - edge, w, h, 0x00000000, red);
        // temblor
        int sx = (int) (Mth.sin(t * 2.1F) * 3 * danger), sy = (int) (Mth.cos(t * 1.7F) * 2 * danger);
        int cx = w / 2 + sx, y = h - 86 + sy;
        String title = "¡NO DEJES QUE CIERRE LA BOCA!";
        g.drawString(mc.font, title, cx - mc.font.width(title) / 2, y - 26, 0xFFFF4040, true);
        // tecla
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
        String hint = "¡SPAMEA!";
        if ((int) (t / 4) % 2 == 0) g.drawString(mc.font, hint, cx + kw / 2 + 10, y - 6, 0xFFFFD040, true);
        // barra
        int bw = 220, bh = 10, bx = cx - bw / 2, by = y + 16;
        g.fill(bx - 2, by - 2, bx + bw + 2, by + bh + 2, 0xC0000000);
        int fillw = (int) (bw * Mth.clamp(shown / 100F, 0F, 1F));
        int col = shown < 30 ? 0xFFD02020 : shown < 60 ? 0xFFE0A020 : 0xFF40C040;
        g.fill(bx, by, bx + fillw, by + bh, col);
        g.fill(bx + bw - 2, by - 3, bx + bw, by + bh + 3, 0xFFFFFFFF);   // meta: escapar
        String l = "TRAGADO", r = "ESCAPAS";
        g.drawString(mc.font, l, bx, by + bh + 4, 0xFFB04040, true);
        g.drawString(mc.font, r, bx + bw - mc.font.width(r), by + bh + 4, 0xFF60D060, true);
    }

    static float shakeIntensity() { return on() ? 0.4F + 0.6F * (1F - shown / 100F) : 0F; }
}
