package com.starlevi.starfest.client.sky;

import com.mojang.blaze3d.platform.NativeImage;
import com.starlevi.starfest.StarFest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Imagenes de los cielos: las que trae el mod (nebulosa, galaxia, aurora...) y las que manda el
 * servidor (PNG o GIF animado). Un GIF queda como una lista de cuadros con su duracion.
 */
public final class SkyImages {
    /** Una imagen (o animacion): cuadros y milisegundos de cada uno. */
    public static final class Img {
        final List<ResourceLocation> frames = new ArrayList<>();
        final List<Integer> delays = new ArrayList<>();
        int total;
        public final float aspect;

        Img(float aspect) {
            this.aspect = aspect;
        }

        public ResourceLocation frame(long ms) {
            if (this.frames.size() == 1 || this.total <= 0) {
                return this.frames.get(0);
            }
            long t = ms % this.total;
            for (int i = 0; i < this.frames.size(); i++) {
                t -= this.delays.get(i);
                if (t < 0) {
                    return this.frames.get(i);
                }
            }
            return this.frames.get(this.frames.size() - 1);
        }
    }

    private static final Map<String, Img> BY_HASH = new HashMap<>();
    private static final Map<String, Img> BUILTIN = new HashMap<>();
    private static final String[] BUILTIN_NAMES = {"nebulosa", "nubes", "galaxia", "brillo", "estrella", "anillo", "haz", "aurora"};

    private SkyImages() {
    }

    public static Img builtin(String name) {
        return BUILTIN.computeIfAbsent(name, n -> {
            Img i = new Img(n.equals("nebulosa") || n.equals("nubes") ? 2 : n.equals("aurora") ? 4 : 1);
            i.frames.add(ResourceLocation.fromNamespaceAndPath(StarFest.MOD_ID, "textures/cielo/" + n + ".png"));
            i.delays.add(100);
            return i;
        });
    }

    public static boolean isBuiltin(String name) {
        for (String n : BUILTIN_NAMES) {
            if (n.equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static Img byHash(String hash) {
        return BY_HASH.get(hash);
    }

    /** Carga una imagen recibida (en el hilo del juego). */
    public static void load(String hash, String fileName, byte[] data) {
        if (BY_HASH.containsKey(hash)) {
            return;
        }
        try {
            List<BufferedImage> frames = new ArrayList<>();
            List<Integer> delays = new ArrayList<>();
            if (fileName.toLowerCase(Locale.ROOT).endsWith(".gif")) {
                readGif(data, frames, delays);
            }
            if (frames.isEmpty()) {
                BufferedImage bi = ImageIO.read(new ByteArrayInputStream(data));
                if (bi == null) {
                    throw new IllegalStateException("formato no reconocido");
                }
                frames.add(bi);
                delays.add(100);
            }
            // un GIF muy largo se recorta para no gastar memoria de video de mas
            int step = Math.max(1, frames.size() / 120);
            BufferedImage first = frames.get(0);
            Img img = new Img(first.getWidth() / (float) Math.max(1, first.getHeight()));
            for (int i = 0; i < frames.size(); i += step) {
                DynamicTexture tex = new DynamicTexture(toNative(frames.get(i)));
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(StarFest.MOD_ID, "cielo_dyn/" + hash + "_" + i);
                Minecraft.getInstance().getTextureManager().register(id, tex);
                img.frames.add(id);
                int d = 0;
                for (int k = i; k < Math.min(frames.size(), i + step); k++) {
                    d += delays.get(k);
                }
                img.delays.add(d);
                img.total += d;
            }
            BY_HASH.put(hash, img);
        } catch (Exception e) {
            StarFest.LOGGER.warn("StarFest: no se pudo leer la imagen del cielo {}: {}", fileName, e.toString());
        }
    }

    private static NativeImage toNative(BufferedImage bi) {
        int w = Math.min(2048, bi.getWidth()), h = Math.min(2048, bi.getHeight());
        NativeImage n = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = bi.getRGB(x, y);
                // NativeImage guarda ABGR
                int a = argb >>> 24, r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
                n.setPixelRGBA(x, y, a << 24 | b << 16 | g << 8 | r);
            }
        }
        return n;
    }

    /** GIF animado con sus cuadros completos (respetando posicion y "dispose" de cada cuadro). */
    private static void readGif(byte[] data, List<BufferedImage> out, List<Integer> delays) throws Exception {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            return;
        }
        ImageReader r = readers.next();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            r.setInput(in, false);
            int n = r.getNumImages(true);
            BufferedImage canvas = null;
            for (int i = 0; i < n && i < 600; i++) {
                BufferedImage frame = r.read(i);
                IIOMetadata meta = r.getImageMetadata(i);
                Node root = meta.getAsTree("javax_imageio_gif_image_1.0");
                int x = 0, y = 0, delay = 100;
                String dispose = "none";
                for (Node c = root.getFirstChild(); c != null; c = c.getNextSibling()) {
                    if (c.getNodeName().equals("ImageDescriptor")) {
                        x = Integer.parseInt(c.getAttributes().getNamedItem("imageLeftPosition").getNodeValue());
                        y = Integer.parseInt(c.getAttributes().getNamedItem("imageTopPosition").getNodeValue());
                    } else if (c.getNodeName().equals("GraphicControlExtension")) {
                        delay = Integer.parseInt(c.getAttributes().getNamedItem("delayTime").getNodeValue()) * 10;
                        dispose = c.getAttributes().getNamedItem("disposalMethod").getNodeValue();
                    }
                }
                if (canvas == null) {
                    int w = r.getWidth(0), h = r.getHeight(0);
                    canvas = new BufferedImage(Math.max(w, x + frame.getWidth()), Math.max(h, y + frame.getHeight()), BufferedImage.TYPE_INT_ARGB);
                }
                BufferedImage before = dispose.equals("restoreToPrevious") ? copy(canvas) : null;
                Graphics2D g = canvas.createGraphics();
                g.drawImage(frame, x, y, null);
                g.dispose();
                out.add(copy(canvas));
                delays.add(delay <= 10 ? 100 : delay);
                if (dispose.equals("restoreToBackgroundColor")) {
                    Graphics2D c2 = canvas.createGraphics();
                    c2.setComposite(java.awt.AlphaComposite.Clear);
                    c2.fillRect(x, y, frame.getWidth(), frame.getHeight());
                    c2.dispose();
                } else if (before != null) {
                    canvas = before;
                }
            }
        } finally {
            r.dispose();
        }
    }

    private static BufferedImage copy(BufferedImage src) {
        BufferedImage c = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = c.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return c;
    }
}
