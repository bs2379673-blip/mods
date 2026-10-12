package com.tunombre.tacui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Dibujo procedural (sin texturas): marcos, paneles, casillas, barras y efectos de pantalla. */
public final class Draw {
    private Draw() {}

    public static void frame(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y + 1, x + 1, y + h - 1, c);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, c);
    }

    /** Esquinas tipo visor de camara. */
    public static void corners(GuiGraphics g, int x, int y, int w, int h, int len, int c) {
        g.fill(x, y, x + len, y + 1, c); g.fill(x, y, x + 1, y + len, c);
        g.fill(x + w - len, y, x + w, y + 1, c); g.fill(x + w - 1, y, x + w, y + len, c);
        g.fill(x, y + h - 1, x + len, y + h, c); g.fill(x, y + h - len, x + 1, y + h, c);
        g.fill(x + w - len, y + h - 1, x + w, y + h, c); g.fill(x + w - 1, y + h - len, x + w, y + h, c);
    }

    public static void text(GuiGraphics g, Font f, String s, int x, int y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(f, s, 0, 0, color, false);
        g.pose().popPose();
    }

    public static void textRight(GuiGraphics g, Font f, String s, int xRight, int y, int color, float scale) {
        text(g, f, s, xRight - (int) (f.width(s) * scale), y, color, scale);
    }

    public static void textCenter(GuiGraphics g, Font f, String s, int xCenter, int y, int color, float scale) {
        text(g, f, s, xCenter - (int) (f.width(s) * scale / 2f), y, color, scale);
    }

    /** Panel con barra de titulo. Devuelve la altura de la barra. */
    public static int panel(GuiGraphics g, Font f, Theme t, int x, int y, int w, int h, String title, boolean highlight) {
        g.fill(x, y, x + w, y + h, t.panel);
        frame(g, x, y, w, h, highlight ? t.accent : Theme.alpha(t.accent, 120));
        // marcas en las esquinas y muesca diagonal abajo a la derecha
        corners(g, x - 2, y - 2, w + 4, h + 4, 5, Theme.alpha(t.accent, highlight ? 255 : 170));
        for (int i = 0; i < 6; i++) g.fill(x + w - 7 + i, y + h - 2 - i, x + w - 6 + i, y + h - 1 - i, Theme.alpha(t.accent, 150));
        if (title.isEmpty()) return 0;   // panel sin barra de titulo
        int th = 13;
        g.fill(x + 1, y + 1, x + w - 1, y + th, Theme.alpha(t.dim, 200));
        g.fill(x + 1, y + th, x + w - 1, y + th + 1, Theme.alpha(t.accent, 160));
        text(g, f, title, x + 5, y + 3, t.text, 0.8f);
        g.fill(x + w - 6, y + 4, x + w - 3, y + 7, t.accent);
        return th;
    }

    /** Marco de casilla (18x18 de contenido: se dibuja alrededor del item de 16x16). */
    public static void slot(GuiGraphics g, Theme t, int x, int y, boolean hover, boolean selected) {
        if (hover) frame(g, x - 2, y - 2, 20, 20, Theme.alpha(t.accent, 70));
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xCC050303);
        frame(g, x - 1, y - 1, 18, 18, selected ? t.accent : hover ? Theme.alpha(t.accent, 220) : Theme.alpha(t.dim, 230));
        if (selected) g.fill(x, y, x + 16, y + 16, Theme.alpha(t.accent, 50));
    }

    public static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, int color, int back) {
        g.fill(x, y, x + w, y + h, back);
        int fw = Math.round(w * Math.max(0f, Math.min(1f, frac)));
        if (fw > 0) {
            g.fill(x, y, x + fw, y + h, color);
            g.fill(x, y, x + fw, y + 1, Theme.alpha(0xFFFFFFFF, 60));
        }
    }

    public static void vbar(GuiGraphics g, int x, int y, int w, int h, float frac, int color, int back) {
        g.fill(x, y, x + w, y + h, back);
        int fh = Math.round(h * Math.max(0f, Math.min(1f, frac)));
        if (fh > 0) g.fill(x, y + h - fh, x + w, y + h, color);
    }

    /** Lineas de barrido y vineta para dar aspecto de pantalla de dispositivo. */
    public static void scanlines(GuiGraphics g, int w, int h) {
        int c = 0x14000000;
        for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, c);
    }

    public static void vignette(GuiGraphics g, int w, int h) {
        int s = Math.max(24, Math.min(w, h) / 6);
        int a = 0x99000000, z = 0x00000000;
        g.fillGradient(0, 0, w, s, a, z);
        g.fillGradient(0, h - s, w, h, z, a);
    }

    /** Linea punteada entre dos puntos. */
    public static void dotted(GuiGraphics g, int x1, int y1, int x2, int y2, int color, int step) {
        double dx = x2 - x1, dy = y2 - y1, len = Math.sqrt(dx * dx + dy * dy);
        int n = (int) (len / step);
        for (int i = 1; i < n; i++) {
            int px = (int) (x1 + dx * i / n), py = (int) (y1 + dy * i / n);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }
}
