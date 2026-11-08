package com.leon.saintsdragons.util.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

/**
 * Port 1.21.11: el dibujo de interfaz ya no usa RenderSystem.setShaderColor ni blend a mano; el color
 * (y la transparencia) va en cada blit. Se guarda el "color actual" como antes y se aplica aca.
 */
public final class Gui {
    private static int color = -1;

    private Gui() {
    }

    public static void color(float r, float g, float b, float a) {
        int ai = Math.round(Math.max(0, Math.min(1, a)) * 255), ri = Math.round(Math.max(0, Math.min(1, r)) * 255),
                gi = Math.round(Math.max(0, Math.min(1, g)) * 255), bi = Math.round(Math.max(0, Math.min(1, b)) * 255);
        color = ai << 24 | ri << 16 | gi << 8 | bi;
    }

    public static int color() {
        return color;
    }

    /** blit(textura, x, y, u, v, ancho, alto, anchoTextura, altoTextura) de 1.20.1. */
    public static void blit(GuiGraphics g, Identifier tex, int x, int y, float u, float v, int w, int h, int tw, int th) {
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h, tw, th, color);
    }

    /** blit(textura, x, y, u, v, ancho, alto) de 1.20.1 (textura de 256). */
    public static void blit(GuiGraphics g, Identifier tex, int x, int y, int u, int v, int w, int h) {
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h, 256, 256, color);
    }

    /** blit(textura, x, y, ancho, alto, u, v, anchoUV, altoUV, anchoTextura, altoTextura) de 1.20.1. */
    public static void blit(GuiGraphics g, Identifier tex, int x, int y, int w, int h, float u, float v, int uw, int vh, int tw, int th) {
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h, uw, vh, tw, th, color);
    }

    /** blit(x, y, z, ancho, alto, sprite) de 1.20.1. */
    public static void blit(GuiGraphics g, int x, int y, int z, int w, int h, TextureAtlasSprite sprite) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h, color);
    }

    /**
     * renderEntityInInventoryFollowsMouse de 1.20.1 (pies en x, y; escala en pixeles por bloque). En 1.21.11 se
     * dibuja centrado en un recuadro: se arma uno alrededor de donde iba.
     */
    public static void entity(GuiGraphics g, int x, int y, int scale, float lookX, float lookY, net.minecraft.world.entity.LivingEntity e) {
        float h = e.getBbHeight() * scale;
        int m = Math.max(8, scale);
        int half = Math.max(m, (int) (Math.max(e.getBbWidth(), 1.0F) * scale * 1.5F));
        int top = (int) (y - h) - m, bottom = y + m;
        int cy = (top + bottom) / 2;
        net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(g, x - half, top, x + half, bottom, scale, 0.0625F,
                x - lookX, cy - lookY, e);
    }
}
