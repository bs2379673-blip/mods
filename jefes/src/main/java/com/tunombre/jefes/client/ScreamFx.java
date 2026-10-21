package com.tunombre.jefes.client;

import com.tunombre.jefes.Jefes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

/** Screamer propio (cuando no hay una imagen de TacUI configurada): destellos, dientes que se cierran y temblor. */
public final class ScreamFx {
    private static long until, startMs;
    private static boolean on;

    private ScreamFx() { }

    public static boolean active() { return on && System.currentTimeMillis() < until; }

    public static void start(float secs, boolean visual) {
        Minecraft mc = Minecraft.getInstance();
        if (!visual) return;
        startMs = System.currentTimeMillis();
        until = startMs + (long) (secs * 1000);
        on = true;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(Jefes.SCREAM.get(), 1.0F, 1.0F));
    }

    public static void draw(GuiGraphics g, int w, int h) {
        if (!active()) return;
        long now = System.currentTimeMillis();
        float k = Math.min(1F, (now - startMs) / 450F);
        int fl = (int) ((now - startMs) / 55) % 4;
        int bg = fl == 0 ? 0xFFB0151A : fl == 2 ? 0xFFFFFFFF : 0xFF000000;
        g.fill(0, 0, w, h, bg);
        // dientes arriba y abajo que se cierran
        int teeth = 11;
        float tw = w / (float) teeth;
        int th = (int) (h * 0.42F * k);
        int jit = (int) (Math.sin(now / 23.0) * 5);
        for (int i = 0; i < teeth; i++) {
            int x0 = (int) (i * tw), x1 = (int) ((i + 1) * tw);
            int hh = th - (i % 2 == 0 ? 0 : th / 5) + jit;
            for (int r = 0; r < hh; r++) {
                float c = 1F - r / (float) Math.max(1, hh);
                int inset = (int) ((x1 - x0) * 0.5F * (r / (float) Math.max(1, hh)));
                g.fill(x0 + inset, r, x1 - inset, r + 1, 0xFFE8E4D4);
                g.fill(x0 + inset, h - 1 - r, x1 - inset, h - r, 0xFFE8E4D4);
            }
        }
        g.fill(0, 0, w, h, 0x30000000);
    }
}
