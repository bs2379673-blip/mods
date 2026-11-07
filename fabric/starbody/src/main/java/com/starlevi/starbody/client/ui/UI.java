package com.starlevi.starbody.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** El estilo de los mods Star (igual que StarScenes): paneles oscuros con borde y una linea de color arriba. */
public final class UI {
    public static final int BG = 0xEE16111F, BG2 = 0xF0201930, BG3 = 0xFF2A2140, BORDER = 0xFF3D3156, BORDER_HI = 0xFF6A5492;
    public static final int ACCENT = 0xFFC58BFF, ACCENT_DARK = 0xFF7E4FC0, GOLD = 0xFFFFC66B, TEXT = 0xFFEDE6F7, MUTED = 0xFF9C90AE;
    public static final int OK = 0xFF7BE08A, BAD = 0xFFFF6B7A, SHADOW = 0x66000000;

    private UI() {
    }

    /** Un rectangulo con las esquinas recortadas (se ve redondeado). */
    public static void box(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x + 1, y, x + w - 1, y + 1, border);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, border);
        g.fill(x, y + 1, x + 1, y + h - 1, border);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    /** Panel principal: sombra, fondo, borde y la linea de color de arriba. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 2, y + 3, x + w + 2, y + h + 3, SHADOW);
        box(g, x, y, w, h, BG, BORDER);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + 3, ACCENT, ACCENT_DARK);
    }

    /** Titulo con un rombo a cada lado. */
    public static void title(GuiGraphics g, Font f, String text, int cx, int y) {
        String t = "✦ " + text + " ✦";
        g.drawString(f, t, cx - f.width(t) / 2, y, ACCENT, true);
    }

    /** Linea separadora suave. */
    public static void separator(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, BORDER);
    }

    /** Recorta un texto al ancho con "…". */
    public static String fit(Font f, String s, int w) {
        if (f.width(s) <= w) {
            return s;
        }
        String r = s;
        while (!r.isEmpty() && f.width(r + "…") > w) {
            r = r.substring(0, r.length() - 1);
        }
        return r + "…";
    }
}
