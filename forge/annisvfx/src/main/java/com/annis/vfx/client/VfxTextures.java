package com.annis.vfx.client;

import com.annis.vfx.AnnisVfx;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Texturas del mod, dibujadas por codigo al arrancar (no hay imagenes de nadie mas): brillo suave,
 * anillo, hoja, petalo, haz de luz y chispa. Blancas o en escala de grises para tenirlas con el color
 * del vertice; la hoja ya viene en verdes.
 */
public final class VfxTextures {
    public static final ResourceLocation GLOW = id("glow");
    public static final ResourceLocation RING = id("ring");
    public static final ResourceLocation LEAF = id("leaf");
    public static final ResourceLocation PETAL = id("petal");
    public static final ResourceLocation BEAM = id("beam");
    public static final ResourceLocation SPARK = id("spark");
    public static final ResourceLocation VIGNETTE = id("vignette");
    public static final ResourceLocation WHITE = id("white");

    private static boolean ready;

    private VfxTextures() {
    }

    private static ResourceLocation id(String name) {
        return new ResourceLocation(AnnisVfx.MOD_ID, "dyn/" + name);
    }

    public static void ensure() {
        if (ready) {
            return;
        }
        ready = true;
        register(GLOW, glow(64));
        register(RING, ring(128));
        register(LEAF, leaf(32));
        register(PETAL, petal(32));
        register(BEAM, beam(16, 64));
        register(SPARK, spark(16));
        register(VIGNETTE, vignette(128));
        NativeImage white = new NativeImage(4, 4, false);
        white.fillRect(0, 0, 4, 4, 0xFFFFFFFF);
        register(WHITE, white);
    }

    private static void register(ResourceLocation id, NativeImage img) {
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
    }

    /** NativeImage guarda ABGR. */
    private static int abgr(float r, float g, float b, float a) {
        int ia = clamp255(a), ir = clamp255(r), ig = clamp255(g), ib = clamp255(b);
        return (ia << 24) | (ib << 16) | (ig << 8) | ir;
    }

    /**
     * Para las texturas de luz: la capa de luz SUMA color e ignora la transparencia, asi que el
     * brillo tiene que ir en el color (negro = nada).
     */
    private static int lum(float a) {
        return abgr(a, a, a, a);
    }

    private static int clamp255(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255.0f)));
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    private static NativeImage glow(int n) {
        NativeImage img = new NativeImage(n, n, false);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float dx = (x + 0.5f) / n * 2 - 1, dy = (y + 0.5f) / n * 2 - 1;
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                float a = (float) Math.pow(Math.max(0, 1 - r), 2.2);
                img.setPixelRGBA(x, y, lum(a));
            }
        }
        return img;
    }

    private static NativeImage ring(int n) {
        NativeImage img = new NativeImage(n, n, false);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float dx = (x + 0.5f) / n * 2 - 1, dy = (y + 0.5f) / n * 2 - 1;
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                float core = (float) Math.exp(-Math.pow((r - 0.82) / 0.05, 2));
                float halo = 0.35f * (float) Math.exp(-Math.pow((r - 0.78) / 0.18, 2));
                float a = Math.min(1, core + halo) * (r < 0.98f ? 1 : 0);
                img.setPixelRGBA(x, y, lum(a));
            }
        }
        return img;
    }

    /** Hoja: forma de lagrima con nervio central y nervios laterales, verdes con borde mas claro. */
    private static NativeImage leaf(int n) {
        NativeImage img = new NativeImage(n, n, false);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float u = (x + 0.5f) / n * 2 - 1;         // -1..1 a lo ancho
                float v = 1 - (y + 0.5f) / n;             // 0 = tallo (abajo), 1 = punta
                float leafV = (v - 0.08f) / 0.92f;
                float width = leafV <= 0 ? 0 : (float) (0.62 * Math.pow(Math.sin(Math.PI * Math.min(1, leafV)), 0.85))
                        * (1 - 0.25f * leafV);
                float edge = width - Math.abs(u);
                boolean stem = v < 0.12f && Math.abs(u) < 0.06f;
                float a = stem ? 1 : smooth(-0.04f, 0.03f, edge);
                if (a <= 0) {
                    img.setPixelRGBA(x, y, 0);
                    continue;
                }
                float rim = 1 - smooth(0.0f, 0.18f, edge);
                float vein = (float) Math.exp(-Math.pow(u / 0.05, 2)) * (leafV > 0 ? 1 : 0);
                // nervios laterales en diagonal hacia la punta
                float side = (float) Math.abs(Math.sin((leafV - Math.abs(u) * 0.8f) * 18.0));
                float sideVein = (float) Math.pow(Math.max(0, 1 - side * 4), 2) * 0.5f * (Math.abs(u) < width * 0.85f ? 1 : 0);
                float r = 0.22f + 0.30f * rim + 0.45f * vein + 0.20f * sideVein;
                float g = 0.62f + 0.30f * rim + 0.33f * vein + 0.18f * sideVein + 0.10f * leafV;
                float b = 0.28f + 0.20f * rim + 0.35f * vein + 0.12f * sideVein;
                img.setPixelRGBA(x, y, abgr(r, g, b, a));
            }
        }
        return img;
    }

    /** Petalo: gota redondeada, blanco con la base un poco mas oscura (se tine con el color). */
    private static NativeImage petal(int n) {
        NativeImage img = new NativeImage(n, n, false);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float u = (x + 0.5f) / n * 2 - 1;
                float v = 1 - (y + 0.5f) / n;             // 0 = base, 1 = punta
                float width = (float) (0.75 * Math.pow(Math.sin(Math.PI * Math.min(1, v * 0.92 + 0.04)), 0.6)) * (0.55f + 0.45f * v);
                float edge = width - Math.abs(u);
                float a = smooth(-0.05f, 0.04f, edge);
                float shade = 0.72f + 0.28f * smooth(0.0f, 0.7f, v) - 0.10f * (1 - smooth(0.0f, 0.25f, edge));
                img.setPixelRGBA(x, y, abgr(shade, shade, shade, a));
            }
        }
        return img;
    }

    /** Haz vertical: suave a los lados y desvanecido arriba y abajo. */
    private static NativeImage beam(int w, int h) {
        NativeImage img = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float u = (x + 0.5f) / w * 2 - 1;
                float v = (y + 0.5f) / h;
                float side = (float) Math.exp(-Math.pow(u / 0.45, 2));
                float vert = smooth(0.0f, 0.12f, 1 - v) * (0.25f + 0.75f * (float) Math.pow(1 - v, 0.6) * smooth(0.0f, 0.2f, v));
                img.setPixelRGBA(x, y, lum(side * vert));
            }
        }
        return img;
    }

    /** Chispa: punto brillante con una cruz de rayos finos. */
    private static NativeImage spark(int n) {
        NativeImage img = new NativeImage(n, n, false);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float dx = (x + 0.5f) / n * 2 - 1, dy = (y + 0.5f) / n * 2 - 1;
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                float core = (float) Math.pow(Math.max(0, 1 - r * 1.6f), 2.5);
                float cross = (float) (Math.exp(-Math.pow(dx / 0.07, 2)) + Math.exp(-Math.pow(dy / 0.07, 2))) * Math.max(0, 1 - r) * 0.6f;
                img.setPixelRGBA(x, y, lum(Math.min(1, core + cross)));
            }
        }
        return img;
    }

    /** Vineta: transparente en el centro y suave hacia los bordes (para tenir la pantalla). */
    private static NativeImage vignette(int n) {
        NativeImage img = new NativeImage(n, n, false);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float dx = (x + 0.5f) / n * 2 - 1, dy = (y + 0.5f) / n * 2 - 1;
                float r = (float) Math.sqrt(dx * dx + dy * dy) / 1.4142f;
                float a = smooth(0.25f, 1.0f, r);
                img.setPixelRGBA(x, y, abgr(1, 1, 1, a));
            }
        }
        return img;
    }
}
