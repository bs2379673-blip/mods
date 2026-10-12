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

    /**
     * Modo "diferido": mientras esta activo, el texto (y lo que se pase a later()) no se dibuja al momento sino que
     * se apunta para despues. Asi un bloque entero de formas puede ir en un solo lote (drawManaged) y el texto
     * encima, sin cambiar el codigo que dibuja.
     */
    private static java.util.List<Runnable> defer;

    public static void beginDefer() { defer = new java.util.ArrayList<>(); }

    public static void later(Runnable r) { if (defer != null) defer.add(r); else r.run(); }

    public static void endDefer() {
        java.util.List<Runnable> d = defer;
        defer = null;
        if (d != null) for (Runnable r : d) r.run();
    }

    public static void text(GuiGraphics g, Font f, String s, int x, int y, int color, float scale) {
        if (defer != null) { defer.add(() -> text(g, f, s, x, y, color, scale)); return; }
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

    /** Solo la forma del panel (para dibujarla dentro de un lote); el titulo va aparte con panelTitle. */
    public static void panelShape(GuiGraphics g, Theme t, int x, int y, int w, int h, boolean titled, boolean highlight) {
        g.fill(x, y, x + w, y + h, t.panel);
        frame(g, x, y, w, h, highlight ? t.accent : Theme.alpha(t.accent, 120));
        corners(g, x - 2, y - 2, w + 4, h + 4, 5, Theme.alpha(t.accent, highlight ? 255 : 170));
        g.fill(x + w - 7, y + h - 2, x + w - 1, y + h - 1, Theme.alpha(t.accent, 150));
        if (!titled) return;
        g.fill(x + 1, y + 1, x + w - 1, y + 13, Theme.alpha(t.dim, 200));
        g.fill(x + 1, y + 13, x + w - 1, y + 14, Theme.alpha(t.accent, 160));
        g.fill(x + w - 6, y + 4, x + w - 3, y + 7, t.accent);
    }

    public static void panelTitle(GuiGraphics g, Font f, Theme t, int x, int y, String title) {
        text(g, f, title, x + 5, y + 3, t.text, 0.8f);
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

    /** Paralelogramo inclinado: la fila de arriba se desplaza 'skew' px a la derecha. */
    public static void slant(GuiGraphics g, int x, int y, int w, int h, int skew, int c) {
        if (w <= 0) return;
        for (int r = 0; r < h; r++) {
            int off = h <= 1 ? 0 : skew * (h - 1 - r) / (h - 1);
            g.fill(x + off, y + r, x + off + w, y + r + 1, c);
        }
    }

    /** Barra inclinada con segmentos. */
    public static void slantBar(GuiGraphics g, int x, int y, int w, int h, int skew, float frac, int col, int back, int segs) {
        slant(g, x, y, w, h, skew, back);
        int fw = Math.round(w * Math.max(0f, Math.min(1f, frac)));
        slant(g, x, y, fw, h, skew, col);
        if (fw > 0 && h > 2) slant(g, x + (h > 1 ? 0 : 0), y, fw, 1, 0, 0x30FFFFFF);
        for (int s = 1; s < segs; s++) {
            int sx = x + w * s / segs;
            for (int r = 0; r < h; r++) {
                int off = h <= 1 ? 0 : skew * (h - 1 - r) / (h - 1);
                g.fill(sx + off, y + r, sx + off + 1, y + r + 1, 0x99000000);
            }
        }
    }

    /** Degradado horizontal de negro (para el fondo de los bloques del HUD). dir 1 = se desvanece hacia la derecha. */
    public static void fadeRect(GuiGraphics g, int x, int y, int w, int h, int alpha, int dir) {
        int steps = 16;
        for (int i = 0; i < steps; i++) {
            int a = (int) (alpha * (1f - i / (float) steps));
            int x1 = dir > 0 ? x + w * i / steps : x + w - w * (i + 1) / steps;
            int x2 = dir > 0 ? x + w * (i + 1) / steps : x + w - w * i / steps;
            g.fill(x1, y, x2, y + h, a << 24);
        }
    }

    /** Cremallera: dientes metalicos a lo largo de una linea; 'open' 0..1 = hasta donde esta abierta (el tirador va ahi). */
    public static void zipper(GuiGraphics g, Theme t, int x, int y, int w, float open) {
        int ox = x + Math.round(w * Math.max(0f, Math.min(1f, open)));
        for (int i = 0; x + i * 3 < x + w; i++) {
            int tx = x + i * 3;
            boolean opened = tx < ox;
            int up = (i % 2 == 0) ? 0 : 2;
            int dy = opened ? (up == 0 ? -1 : 2) : up;
            g.fill(tx, y + dy, tx + 2, y + dy + 2, 0xFFB8B8B8);
            g.fill(tx, y + dy + 1, tx + 2, y + dy + 2, 0xFF6E6E6E);
        }
        g.fill(x, y + 4, x + w, y + 5, 0x55000000);
        // tirador
        g.fill(ox - 2, y - 2, ox + 4, y + 5, 0xFFD0D0D0);
        frame(g, ox - 2, y - 2, 6, 7, 0xFF505050);
        g.fill(ox, y + 5, ox + 2, y + 10, 0xFFB0B0B0);
        g.fill(ox - 1, y + 9, ox + 3, y + 11, t.accent);
    }

    /** Costura: linea discontinua por dentro de un rectangulo. */
    public static void stitch(GuiGraphics g, int x, int y, int w, int h, int c) {
        for (int i = 0; i < w; i += 4) { g.fill(x + i, y, x + Math.min(i + 2, w), y + 1, c); g.fill(x + i, y + h - 1, x + Math.min(i + 2, w), y + h, c); }
        for (int i = 0; i < h; i += 4) { g.fill(x, y + i, x + 1, y + Math.min(i + 2, h), c); g.fill(x + w - 1, y + i, x + w, y + Math.min(i + 2, h), c); }
    }
}
