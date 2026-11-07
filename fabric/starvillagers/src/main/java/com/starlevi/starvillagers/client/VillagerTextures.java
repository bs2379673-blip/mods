package com.starlevi.starvillagers.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.starlevi.starvillagers.Identity;
import com.starlevi.starvillagers.StarVillagers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.Villager;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Arma la skin de cada aldeano juntando sus capas: piel (con el color de sus genes), cara, ropa y
 * pelo (con su color). Tambien la version con los ojos cerrados para que parpadee. Los aldeanos que
 * se ven igual comparten la textura.
 */
public final class VillagerTextures {
    private static final int MAX = 300;
    private static final Map<String, Identifier[]> CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static final Map<String, DynamicTexture[]> TEX = new HashMap<>();
    private static final Map<String, NativeImage> PNG = new HashMap<>();
    private static NativeImage skinPalette, hairPalette;
    private static int serial;

    private VillagerTextures() {
    }

    /** Si el pelo largo se dibuja con fisicas (StarBody): esos pixeles se sacan de la skin y van en tiras. */
    public static boolean strips;

    /** Los pixeles de pelo que van en tiras: costados y atras del gorro, desde la tercera fila. */
    public static boolean stripPixel(int u, int v) {
        return v >= 10 && v < 16 && (u >= 32 && u < 40 || u >= 48 && u < 64);
    }

    /** La skin completa (con todo el pelo), para dibujar las tiras de pelo. */
    public static Identifier full(Villager v) {
        Identity id = StarVillagers.identity(v);
        Identifier[] t = id == null ? null : CACHE.get(id.looksKey());
        return t == null ? null : t.length > 2 && t[2] != null ? t[2] : t[0];
    }

    /** Que pixeles del pelo de este aldeano van en tiras ({u, v} de 64x64). */
    public static java.util.List<int[]> hairPixels(Identity id) {
        java.util.List<int[]> l = new java.util.ArrayList<>();
        NativeImage hair = png("mca/skins/" + id.hair());
        if (hair == null || hair.getWidth() < 64) {
            return l;
        }
        int k = hair.getWidth() / 64;
        for (int v = 10; v < 16; v++) {
            for (int u = 32; u < 64; u++) {
                if (stripPixel(u, v) && (hair.getPixel(u * k, v * k) >>> 24) > 40) {
                    l.add(new int[]{u, v});
                }
            }
        }
        return l;
    }

    public static Identifier texture(Villager v) {
        Identity id = StarVillagers.identity(v);
        if (id == null) {
            return null;
        }
        Identifier[] t = CACHE.get(id.looksKey());
        if (t == null) {
            // si usa algo de la biblioteca que todavia se esta bajando, espera (sin armar nada)
            for (String layer : new String[]{id.clothes(), id.hair()}) {
                int[] lib = com.starlevi.starvillagers.client.library.Library.parse(layer);
                if (lib != null && com.starlevi.starvillagers.client.library.Library.image(lib[0], lib[1]) == null
                        && !com.starlevi.starvillagers.client.library.Library.failed(lib[0], lib[1])) {
                    return null;
                }
            }
            t = build(id);
            if (t == null) {
                return null;
            }
            CACHE.put(id.looksKey(), t);
            trim();
        }
        // parpadea un momento cada tanto (cada uno a su ritmo); dormido, ojos cerrados
        boolean blink = v.isSleeping() || Math.floorMod(v.tickCount + id.seed(), 67) < 3;
        return blink && t[1] != null ? t[1] : t[0];
    }

    private static void trim() {
        while (CACHE.size() > MAX) {
            String oldest = CACHE.keySet().iterator().next();
            CACHE.remove(oldest);
            DynamicTexture[] d = TEX.remove(oldest);
            if (d != null) {
                for (DynamicTexture x : d) {
                    if (x != null) {
                        x.close();
                    }
                }
            }
        }
    }

    private static NativeImage png(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        int b = path.indexOf("biblioteca:");
        if (b >= 0) {
            // ropa o pelo de la biblioteca de MCA (bajada a config/starvillagers/biblioteca)
            int[] lib = com.starlevi.starvillagers.client.library.Library.parse(path.substring(b));
            return lib == null ? null : com.starlevi.starvillagers.client.library.Library.image(lib[0], lib[1]);
        }
        if (PNG.containsKey(path)) {
            return PNG.get(path);
        }
        NativeImage img = null;
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(StarVillagers.id(path));
            if (res.isPresent()) {
                try (InputStream in = res.get().open()) {
                    img = NativeImage.read(in);
                }
            }
        } catch (Exception e) {
            StarVillagers.LOGGER.warn("StarVillagers: no pude leer {}: {}", path, e.toString());
        }
        PNG.put(path, img);
        return img;
    }

    /** Color de un mapa de 32x32 (como en MCA): una fila por el primer gen, una columna por el segundo. */
    private static int palette(NativeImage p, float a, float b) {
        if (p == null) {
            return 0xFFFFFFFF;
        }
        int x = (int) Math.floor(Math.max(0, Math.min(1, b)) * (p.getWidth() - 1));
        int y = (int) Math.floor(Math.max(0, Math.min(1, a)) * (p.getHeight() - 1));
        return p.getPixel(x, y);
    }

    private static Identifier[] build(Identity id) {
        if (skinPalette == null) {
            skinPalette = png("mca/colormap/villager_skin.png");
            hairPalette = png("mca/colormap/villager_hair.png");
        }
        NativeImage skin = png("mca/skins/" + id.skin());
        if (skin == null) {
            return null;
        }
        int skinColor = palette(skinPalette, id.melanin(), id.hemoglobin());
        int hairColor = palette(hairPalette, id.eumelanin(), id.pheomelanin());
        String blinkFace = id.face().replace(".png", "_blink.png");
        NativeImage full = strips ? compose(skin, skinColor, png("mca/skins/" + id.face()), png("mca/skins/" + id.clothes()), png("mca/skins/" + id.hair()),
                hairColor) : null;
        NativeImage open = compose(skin, skinColor, png("mca/skins/" + id.face()), png("mca/skins/" + id.clothes()), png("mca/skins/" + id.hair()),
                hairColor);
        NativeImage closedFace = png("mca/skins/" + blinkFace);
        NativeImage closed = closedFace == null ? null
                : compose(skin, skinColor, closedFace, png("mca/skins/" + id.clothes()), png("mca/skins/" + id.hair()), hairColor);
        if (strips) {
            // el pelo que va en tiras no queda pintado en la cabeza (si no, se ve doble)
            java.util.List<int[]> px = hairPixels(id);
            for (NativeImage img : new NativeImage[]{open, closed}) {
                if (img != null) {
                    for (int[] p : px) {
                        img.setPixel(p[0], p[1], 0);
                    }
                }
            }
        }
        Identifier[] out = new Identifier[3];
        DynamicTexture[] tex = new DynamicTexture[3];
        NativeImage[] imgs = {open, closed, full};
        for (int i = 0; i < 3; i++) {
            if (imgs[i] == null) {
                continue;
            }
            Identifier tid = StarVillagers.id("aldeano/" + (serial++));
            tex[i] = new DynamicTexture(tid::toString, imgs[i]);
            Minecraft.getInstance().getTextureManager().register(tid, tex[i]);
            out[i] = tid;
        }
        TEX.put(id.looksKey(), tex);
        return out;
    }

    private static NativeImage compose(NativeImage skin, int skinColor, NativeImage face, NativeImage clothes, NativeImage hair, int hairColor) {
        NativeImage out = new NativeImage(64, 64, true);
        layer(out, skin, skinColor);
        layer(out, face, 0xFFFFFFFF);
        layer(out, clothes, 0xFFFFFFFF);
        layer(out, hair, hairColor);
        return out;
    }

    /** Pone una capa arriba (multiplicada por el color), mezclando por transparencia. */
    private static void layer(NativeImage out, NativeImage src, int tint) {
        if (src == null) {
            return;
        }
        int w = Math.min(64, src.getWidth()), h = Math.min(64, src.getHeight());
        float tr = (tint >> 16 & 255) / 255f, tg = (tint >> 8 & 255) / 255f, tb = (tint & 255) / 255f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int s = src.getPixel(x * src.getWidth() / w, y * src.getHeight() / h);
                int sa = s >>> 24;
                if (sa == 0) {
                    continue;
                }
                float sr = (s >> 16 & 255) * tr, sg = (s >> 8 & 255) * tg, sb = (s & 255) * tb;
                int d = out.getPixel(x, y);
                int da = d >>> 24;
                float a = sa / 255f;
                int r = (int) (sr * a + (d >> 16 & 255) * (1 - a)), g = (int) (sg * a + (d >> 8 & 255) * (1 - a)), b = (int) (sb * a + (d & 255) * (1 - a));
                int na = Math.min(255, sa + da * (255 - sa) / 255);
                out.setPixel(x, y, na << 24 | r << 16 | g << 8 | b);
            }
        }
    }

    public static void clear() {
        for (DynamicTexture[] d : TEX.values()) {
            for (DynamicTexture x : d) {
                if (x != null) {
                    x.close();
                }
            }
        }
        TEX.clear();
        CACHE.clear();
    }

    /** Cuantas texturas armadas hay (para la prueba). */
    public static int count() {
        return CACHE.size();
    }
}
