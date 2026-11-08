package com.starlevi.starpackport.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Desde 1.20.2 la interfaz (corazones, hotbar, botones...) son imagenes sueltas en textures/gui/sprites/.
 * 1.20.1 usa hojas grandes (icons.png, widgets.png...). Esto pega cada imagen en su lugar.
 */
final class SpriteAtlases {
    private record Spot(String name, String atlas, int x, int y, int w, int h) {
    }

    private final PackConverter c;

    SpriteAtlases(PackConverter c) {
        this.c = c;
    }


    private static List<Spot> table() throws IOException {
        List<Spot> out = new ArrayList<>();
        try (InputStream in = SpriteAtlases.class.getResourceAsStream("/starpackport/sprites.json")) {
            if (in == null) {
                return out;
            }
            JsonObject o = PackConverter.parseObject(in.readAllBytes());
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                for (JsonElement t : e.getValue().getAsJsonArray()) {
                    JsonArray a = t.getAsJsonArray();
                    out.add(new Spot(e.getKey(), a.get(0).getAsString(), a.get(1).getAsInt(), a.get(2).getAsInt(),
                            a.get(3).getAsInt(), a.get(4).getAsInt()));
                }
            }
        }
        return out;
    }

    int apply() throws IOException {
        String pre = "assets/minecraft/textures/gui/sprites/";
        Map<String, List<Spot>> byAtlas = new LinkedHashMap<>();
        for (Spot s : table()) {
            if (c.files.containsKey(pre + s.name + ".png")) {
                byAtlas.computeIfAbsent(s.atlas, k -> new ArrayList<>()).add(s);
            }
        }
        int count = 0;
        for (Map.Entry<String, List<Spot>> e : byAtlas.entrySet()) {
            String atlasPath = "assets/minecraft/textures/" + e.getKey();
            byte[] vb = c.vanilla.get(atlasPath);
            if (vb == null) {
                c.log.accept("StarPackPort: no encontre la hoja original " + atlasPath);
                continue;
            }
            BufferedImage vanilla = argb(ImageIO.read(new ByteArrayInputStream(vb)));
            int logicalW = vanilla.getWidth();
            int logicalH = vanilla.getHeight();
            // base: la hoja vieja que traiga el pack (si trae) o la del juego
            BufferedImage base = vanilla;
            if (c.files.containsKey(atlasPath)) {
                BufferedImage own = read(c.files.get(atlasPath));
                if (own != null) {
                    base = own;
                }
            }
            int k = Math.max(1, Math.round(base.getWidth() / (float) logicalW));
            Map<Spot, BufferedImage> imgs = new LinkedHashMap<>();
            for (Spot s : e.getValue()) {
                BufferedImage img = read(c.files.get(pre + s.name + ".png"));
                if (img == null) {
                    continue;
                }
                img = firstFrame(img, s, c.files.get(pre + s.name + ".png.mcmeta"));
                imgs.put(s, img);
                k = Math.max(k, Math.round(img.getWidth() / (float) s.w));
            }
            k = Math.min(k, 16);
            BufferedImage out = scale(base, logicalW * k, logicalH * k);
            for (Map.Entry<Spot, BufferedImage> si : imgs.entrySet()) {
                Spot s = si.getKey();
                BufferedImage img = si.getValue();
                JsonObject mm = mcmeta(c.files.get(pre + s.name + ".png.mcmeta"));
                clear(out, s.x * k, s.y * k, s.w * k, s.h * k);
                float rx = img.getWidth() / (float) s.w;
                float ry = img.getHeight() / (float) s.h;
                int[] border = nineSlice(mm);
                if (Math.abs(rx - ry) > 0.01f && border != null) {
                    float unit = mm.getAsJsonObject("gui").getAsJsonObject("scaling").has("width")
                            ? img.getWidth() / mm.getAsJsonObject("gui").getAsJsonObject("scaling").get("width").getAsFloat() : 1;
                    drawNine(out, img, s.x * k, s.y * k, s.w * k, s.h * k, border, unit, k);
                } else {
                    draw(out, scale(img, s.w * k, s.h * k), s.x * k, s.y * k);
                }
                count++;
            }
            c.files.put(atlasPath, png(out));
        }
        return count;
    }

    // ------------------------------------------------------------------ imagenes

    static BufferedImage argb(BufferedImage in) {
        if (in.getType() == BufferedImage.TYPE_INT_ARGB) {
            return in;
        }
        BufferedImage out = new BufferedImage(in.getWidth(), in.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < in.getHeight(); y++) {
            for (int x = 0; x < in.getWidth(); x++) {
                out.setRGB(x, y, in.getRGB(x, y));
            }
        }
        return out;
    }

    static BufferedImage read(byte[] b) {
        if (b == null) {
            return null;
        }
        try {
            BufferedImage i = ImageIO.read(new ByteArrayInputStream(b));
            return i == null ? null : argb(i);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bo);
        return bo.toByteArray();
    }

    static JsonObject mcmeta(byte[] b) {
        if (b == null) {
            return null;
        }
        try {
            return PackConverter.parseObject(b);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Si la imagen es animada (varios cuadros uno debajo del otro) se queda con el primero. */
    private static BufferedImage firstFrame(BufferedImage img, Spot s, byte[] meta) {
        int frameH = Math.round(img.getWidth() * s.h / (float) s.w);
        JsonObject mm = mcmeta(meta);
        if (mm != null && mm.has("animation") && img.getHeight() > frameH && frameH > 0) {
            JsonObject an = mm.getAsJsonObject("animation");
            int fw = an.has("width") ? an.get("width").getAsInt() : img.getWidth();
            int fh = an.has("height") ? an.get("height").getAsInt() : (an.has("width") ? fw : Math.min(img.getWidth(), img.getHeight()));
            if (fh > 0 && fh < img.getHeight()) {
                return img.getSubimage(0, 0, Math.min(fw, img.getWidth()), fh);
            }
        }
        return img;
    }

    private static int[] nineSlice(JsonObject mm) {
        try {
            JsonObject sc = mm.getAsJsonObject("gui").getAsJsonObject("scaling");
            if (!"nine_slice".equals(sc.get("type").getAsString())) {
                return null;
            }
            JsonElement b = sc.get("border");
            if (b.isJsonPrimitive()) {
                int v = b.getAsInt();
                return new int[]{v, v, v, v};
            }
            JsonObject o = b.getAsJsonObject();
            return new int[]{o.get("left").getAsInt(), o.get("top").getAsInt(), o.get("right").getAsInt(), o.get("bottom").getAsInt()};
        } catch (RuntimeException e) {
            return null;
        }
    }

    static BufferedImage scale(BufferedImage in, int w, int h) {
        if (in.getWidth() == w && in.getHeight() == h) {
            return in;
        }
        BufferedImage out = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < out.getHeight(); y++) {
            int sy = Math.min(in.getHeight() - 1, (int) ((y + 0.5f) * in.getHeight() / out.getHeight()));
            for (int x = 0; x < out.getWidth(); x++) {
                int sx = Math.min(in.getWidth() - 1, (int) ((x + 0.5f) * in.getWidth() / out.getWidth()));
                out.setRGB(x, y, in.getRGB(sx, sy));
            }
        }
        return out;
    }

    private static void clear(BufferedImage img, int x, int y, int w, int h) {
        for (int j = y; j < Math.min(img.getHeight(), y + h); j++) {
            for (int i = x; i < Math.min(img.getWidth(), x + w); i++) {
                img.setRGB(i, j, 0);
            }
        }
    }

    private static void draw(BufferedImage dst, BufferedImage src, int x, int y) {
        for (int j = 0; j < src.getHeight(); j++) {
            for (int i = 0; i < src.getWidth(); i++) {
                int dx = x + i;
                int dy = y + j;
                if (dx >= 0 && dy >= 0 && dx < dst.getWidth() && dy < dst.getHeight()) {
                    dst.setRGB(dx, dy, src.getRGB(i, j));
                }
            }
        }
    }

    /** Estira una imagen de "nine slice" (bordes fijos y centro estirado) al tamano del hueco. */
    private static void drawNine(BufferedImage dst, BufferedImage src, int x, int y, int w, int h, int[] b, float unit, int k) {
        int sl = Math.round(b[0] * unit), st = Math.round(b[1] * unit), sr = Math.round(b[2] * unit), sb = Math.round(b[3] * unit);
        int dl = b[0] * k, dt = b[1] * k, dr = b[2] * k, db = b[3] * k;
        int[] sxs = {0, sl, src.getWidth() - sr, src.getWidth()};
        int[] sys = {0, st, src.getHeight() - sb, src.getHeight()};
        int[] dxs = {0, dl, w - dr, w};
        int[] dys = {0, dt, h - db, h};
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 3; i++) {
                int sw = sxs[i + 1] - sxs[i], sh = sys[j + 1] - sys[j];
                int dw = dxs[i + 1] - dxs[i], dh = dys[j + 1] - dys[j];
                if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) {
                    continue;
                }
                draw(dst, scale(src.getSubimage(sxs[i], sys[j], sw, sh), dw, dh), x + dxs[i], y + dys[j]);
            }
        }
    }
}
