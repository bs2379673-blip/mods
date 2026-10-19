package com.tunombre.tacui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Cliente del sistema de imagenes: junta los trozos, decodifica PNG/GIF y lo dibuja encima de todo. */
public final class ImageOverlay {
    private ImageOverlay() {}

    private static final class Parts { final byte[][] chunks; int got; Parts(int n) { chunks = new byte[n][]; } }
    private static final class Anim { ResourceLocation[] frames; int[] delays; int total, w, h; }
    private static final class Active { String key, mode, text; float secs; long start = -1; boolean done; }
    private static final class Decoded { List<int[]> frames = new ArrayList<>(); List<Integer> delays = new ArrayList<>(); int w, h; }

    private static final Map<String, Parts> PARTS = new HashMap<>();
    private static final Map<String, Anim> CACHE = new LinkedHashMap<>();
    private static final Map<String, Boolean> LOADING = new HashMap<>();
    private static final List<Active> LIST = new ArrayList<>();
    private static int texCounter;

    // ------------------------------------------------------------------ red

    public static void onData(TacNet.ImageData m) {
        if (CACHE.containsKey(m.key()) || LOADING.containsKey(m.key())) return;
        Parts p = PARTS.computeIfAbsent(m.key(), k -> new Parts(Math.max(1, m.total())));
        if (m.index() < 0 || m.index() >= p.chunks.length) return;
        if (p.chunks[m.index()] == null) { p.chunks[m.index()] = m.data(); p.got++; }
        if (p.got < p.chunks.length) return;
        PARTS.remove(m.key());
        int len = 0;
        for (byte[] c : p.chunks) len += c.length;
        byte[] all = new byte[len];
        int o = 0;
        for (byte[] c : p.chunks) { System.arraycopy(c, 0, all, o, c.length); o += c.length; }
        load(m.key(), all);
    }

    public static void onShow(TacNet.ShowImage m) {
        Active a = new Active();
        a.key = m.key(); a.secs = m.seconds(); a.mode = m.mode(); a.text = m.text().replace('&', '§');
        synchronized (LIST) {
            if (a.mode.startsWith("p_")) LIST.removeIf(o -> o.mode.equals(a.mode));
            else LIST.removeIf(o -> !o.mode.startsWith("p_"));
            LIST.add(a);
        }
        if (!m.sound().isEmpty()) {
            try {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                        SoundEvent.createVariableRangeEvent(new ResourceLocation(m.sound())), 1.0f, m.volume()));
            } catch (Exception ex) { TacUI.LOGGER.warn("tacui: sonido no valido {}", m.sound()); }
        }
    }

    public static void onClear() { synchronized (LIST) { LIST.clear(); } }

    // ------------------------------------------------------------------ decodificacion

    private static void load(String key, byte[] bytes) {
        LOADING.put(key, true);
        boolean gif = bytes.length > 3 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F';
        Minecraft mc = Minecraft.getInstance();
        if (!gif) {
            try (NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes))) {
                Decoded d = new Decoded();
                d.w = img.getWidth(); d.h = img.getHeight();
                int[] px = new int[d.w * d.h];
                for (int y = 0; y < d.h; y++) for (int x = 0; x < d.w; x++) {
                    int abgr = img.getPixelRGBA(x, y);
                    px[y * d.w + x] = (abgr & 0xFF00FF00) | ((abgr & 0xFF) << 16) | ((abgr >> 16) & 0xFF);
                }
                d.frames.add(px); d.delays.add(1000);
                upload(key, d);
            } catch (Exception ex) {
                TacUI.LOGGER.warn("tacui: no se pudo leer la imagen {}: {}", key, ex.toString());
                LOADING.remove(key);
            }
            return;
        }
        CompletableFuture.supplyAsync(() -> decodeGif(bytes), Util.backgroundExecutor())
                .whenCompleteAsync((d, err) -> {
                    if (err != null || d == null || d.frames.isEmpty()) {
                        TacUI.LOGGER.warn("tacui: no se pudo leer el GIF {}: {}", key, String.valueOf(err));
                        LOADING.remove(key);
                    } else upload(key, d);
                }, mc);
    }

    private static int attr(IIOMetadataNode n, String a, int def) {
        try { return Integer.parseInt(n.getAttribute(a)); } catch (Exception e) { return def; }
    }

    /** GIF -> fotogramas ARGB ya compuestos (respeta posicion, transparencia y modo de borrado de cada cuadro). */
    private static Decoded decodeGif(byte[] bytes) {
        Decoded d = new Decoded();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName("gif");
            if (!it.hasNext()) return d;
            ImageReader r = it.next();
            r.setInput(in, false);
            int n = r.getNumImages(true);
            int W = 0, H = 0;
            try {
                IIOMetadataNode root = (IIOMetadataNode) r.getStreamMetadata().getAsTree("javax_imageio_gif_stream_1.0");
                for (int i = 0; i < root.getLength(); i++) {
                    IIOMetadataNode c = (IIOMetadataNode) root.item(i);
                    if (c.getNodeName().equals("LogicalScreenDescriptor")) { W = attr(c, "logicalScreenWidth", 0); H = attr(c, "logicalScreenHeight", 0); }
                }
            } catch (Exception ignored) { }
            if (W <= 0 || H <= 0) { BufferedImage f0 = r.read(0); W = f0.getWidth(); H = f0.getHeight(); }
            // limites de memoria: como mucho ~40 millones de pixeles entre todos los cuadros
            int step = 1;
            while ((long) W * H * ((n + step - 1) / step) > 40_000_000L && step < 64) step++;
            int[] canvas = new int[W * H];
            int acc = 0;
            for (int i = 0; i < n && d.frames.size() < 400; i++) {
                BufferedImage fr = r.read(i);
                int left = 0, top = 0, delay = 10;
                String disposal = "none";
                IIOMetadataNode root = (IIOMetadataNode) r.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
                for (int k = 0; k < root.getLength(); k++) {
                    IIOMetadataNode c = (IIOMetadataNode) root.item(k);
                    if (c.getNodeName().equals("ImageDescriptor")) { left = attr(c, "imageLeftPosition", 0); top = attr(c, "imageTopPosition", 0); }
                    if (c.getNodeName().equals("GraphicControlExtension")) { delay = attr(c, "delayTime", 10); disposal = c.getAttribute("disposalMethod"); }
                }
                int fw = fr.getWidth(), fh = fr.getHeight();
                int[] before = "restoreToPrevious".equals(disposal) ? canvas.clone() : null;
                int[] px = fr.getRGB(0, 0, fw, fh, null, 0, fw);
                for (int y = 0; y < fh; y++) {
                    int cy = top + y;
                    if (cy < 0 || cy >= H) continue;
                    for (int x = 0; x < fw; x++) {
                        int cx = left + x;
                        if (cx < 0 || cx >= W) continue;
                        int c = px[y * fw + x];
                        if ((c >>> 24) != 0) canvas[cy * W + cx] = c;
                    }
                }
                acc += Math.max(20, delay * 10);
                if (i % step == 0) { d.frames.add(canvas.clone()); d.delays.add(acc); acc = 0; }
                if ("restoreToBackgroundColor".equals(disposal)) {
                    for (int y = Math.max(0, top); y < Math.min(H, top + fh); y++)
                        for (int x = Math.max(0, left); x < Math.min(W, left + fw); x++) canvas[y * W + x] = 0;
                } else if (before != null) canvas = before;
            }
            r.dispose();
            d.w = W; d.h = H;
        } catch (Exception ex) {
            TacUI.LOGGER.warn("tacui: error leyendo GIF: {}", ex.toString());
        }
        return d;
    }

    private static void upload(String key, Decoded d) {
        Minecraft mc = Minecraft.getInstance();
        Anim a = new Anim();
        a.w = d.w; a.h = d.h;
        a.frames = new ResourceLocation[d.frames.size()];
        a.delays = new int[d.frames.size()];
        for (int i = 0; i < a.frames.length; i++) {
            NativeImage img = new NativeImage(d.w, d.h, false);
            int[] px = d.frames.get(i);
            for (int y = 0; y < d.h; y++) for (int x = 0; x < d.w; x++) {
                int c = px[y * d.w + x];   // ARGB -> ABGR
                img.setPixelRGBA(x, y, (c & 0xFF00FF00) | ((c & 0xFF) << 16) | ((c >> 16) & 0xFF));
            }
            ResourceLocation rl = new ResourceLocation(TacUI.ID, "screen/img" + (texCounter++));
            mc.getTextureManager().register(rl, new DynamicTexture(img));
            a.frames[i] = rl;
            a.delays[i] = d.delays.get(i);
            a.total += a.delays[i];
        }
        CACHE.put(key, a);
        LOADING.remove(key);
        // como mucho 8 imagenes en memoria
        while (CACHE.size() > 8) {
            String oldest = CACHE.keySet().iterator().next();
            boolean inUse = false;
            synchronized (LIST) { for (Active x : LIST) if (x.key.equals(oldest)) inUse = true; }
            if (inUse) break;
            Anim old = CACHE.remove(oldest);
            for (ResourceLocation rl : old.frames) mc.getTextureManager().release(rl);
        }
    }

    // ------------------------------------------------------------------ dibujo

    public static void render(GuiGraphics g, int w, int h) {
        List<Active> copy;
        synchronized (LIST) { copy = new ArrayList<>(LIST); }
        for (Active a : copy) render1(g, w, h, a);
        synchronized (LIST) { LIST.removeIf(x -> x.done); }
    }

    private static void render1(GuiGraphics g, int w, int h, Active a) {
        Anim an = CACHE.get(a.key);
        if (an == null || an.frames.length == 0) return;   // aun cargando
        long now = System.currentTimeMillis();
        if (a.start < 0) a.start = now;
        float el = (now - a.start) / 1000f;
        if (el > a.secs) { a.done = true; return; }
        boolean full = a.mode.equals("full");
        float fin = full ? 0.04f : 0.2f, fout = 0.4f;
        float alpha = Math.min(1f, Math.min(el / fin, (a.secs - el) / fout));
        int fi = 0;
        if (an.frames.length > 1 && an.total > 0) {
            int tt = (int) ((now - a.start) % an.total);
            while (fi < an.delays.length - 1 && tt >= an.delays[fi]) { tt -= an.delays[fi]; fi++; }
        }
        ResourceLocation tex = an.frames[fi];
        Theme t = Layout.get().theme();
        Font font = Minecraft.getInstance().font;

        g.pose().pushPose();
        g.pose().translate(0, 0, 800);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, Math.max(0f, alpha));
        if (a.mode.startsWith("p_")) {
            // sticker suelto en un punto de la pantalla: p_<x%>_<y%>_<ancho%>
            String[] sp = a.mode.split("_");
            float px = 50, py = 50, pw = 12;
            try { px = Float.parseFloat(sp[1]); py = Float.parseFloat(sp[2]); pw = Float.parseFloat(sp[3]); } catch (Exception ignored) { }
            int dw = Math.max(8, Math.round(w * pw / 100f));
            int dh = Math.round(dw * an.h / (float) an.w);
            if (dh > h / 4) { dh = h / 4; dw = Math.round(dh * an.w / (float) an.h); }
            float bob = (float) Math.sin(el * 3f + px * 0.3f) * 2f;
            float pop = Math.min(1f, el / 0.25f);
            g.pose().translate(w * px / 100f, h * py / 100f + bob, 0);
            g.pose().scale(0.6f + 0.4f * pop, 0.6f + 0.4f * pop, 1f);
            g.blit(tex, -dw / 2, -dh / 2, dw, dh, 0f, 0f, an.w, an.h, an.w, an.h);
        } else if (full) {
            // pantalla completa (susto): cubre todo y tiembla al principio
            float s = Math.max(w / (float) an.w, h / (float) an.h);
            int dw = Math.round(an.w * s), dh = Math.round(an.h * s);
            int sx = 0, sy = 0;
            if (el < 0.8f) { sx = (int) ((Math.random() - 0.5) * 10 * (1 - el / 0.8f)); sy = (int) ((Math.random() - 0.5) * 10 * (1 - el / 0.8f)); }
            g.fill(0, 0, w, h, 0xFF000000);
            g.blit(tex, (w - dw) / 2 + sx, (h - dh) / 2 + sy, dw, dh, 0f, 0f, an.w, an.h, an.w, an.h);
            if (!a.text.isEmpty()) caption(g, font, t, a.text, w / 2, h - 40, w - 40, 1.3f);
        } else if (a.mode.equals("corner")) {
            // alerta en la esquina: entra deslizando desde la derecha
            int cw = Math.min(150, w / 3), iw = cw - 10;
            int ih = Math.min(Math.round(iw * an.h / (float) an.w), h / 3);
            int iw2 = Math.round(ih * an.w / (float) an.h);
            List<FormattedCharSequence> lines = a.text.isEmpty() ? List.of() : font.split(Component.literal(a.text), (int) ((cw - 10) / 0.75f));
            int chh = 16 + ih + 6 + lines.size() * 8 + (lines.isEmpty() ? 0 : 4);
            float k = Math.min(1f, el / 0.35f), e = 1f - (1f - k) * (1f - k) * (1f - k);
            float ko = Math.max(0f, (el - (a.secs - 0.35f)) / 0.35f);
            int x = w - 8 - cw + (int) ((1f - e) * (cw + 12)) + (int) (ko * (cw + 12)), y = 44;
            Draw.panel(g, font, t, x, y, cw, chh, "!! ALERTA", true);
            if ((now / 400) % 2 == 0) g.fill(x + cw - 12, y + 4, x + cw - 6, y + 10, 0xFFFF4040);
            g.blit(tex, x + (cw - iw2) / 2, y + 16, iw2, ih, 0f, 0f, an.w, an.h, an.w, an.h);
            Draw.frame(g, x + (cw - iw2) / 2 - 1, y + 15, iw2 + 2, ih + 2, Theme.alpha(t.accent, 160));
            int ty = y + 16 + ih + 5;
            for (FormattedCharSequence l : lines) {
                g.pose().pushPose();
                g.pose().translate(x + 5, ty, 0);
                g.pose().scale(0.75f, 0.75f, 1f);
                g.drawString(font, l, 0, 0, 0xFFFFFFFF, false);
                g.pose().popPose();
                ty += 8;
            }
        } else {
            // aviso en el centro con marco tactico
            float k = Math.min(1f, el / 0.25f), sc = 0.9f + 0.1f * (1f - (1f - k) * (1f - k));
            int maxW = (int) (w * 0.6f), maxH = (int) (h * 0.55f);
            float s = Math.min(maxW / (float) an.w, maxH / (float) an.h);
            int dw = Math.round(an.w * s), dh = Math.round(an.h * s);
            List<FormattedCharSequence> lines = a.text.isEmpty() ? List.of() : font.split(Component.literal(a.text), dw);
            int ph = 16 + dh + 6 + lines.size() * 10 + (lines.isEmpty() ? 0 : 4);
            int px = (w - dw) / 2 - 6, py = (h - ph) / 2;
            g.pose().pushPose();
            g.pose().translate(w / 2f, h / 2f, 0);
            g.pose().scale(sc, sc, 1f);
            g.pose().translate(-w / 2f, -h / 2f, 0);
            g.fill(0, 0, w, h, 0x66000000);
            Draw.panel(g, font, t, px, py, dw + 12, ph, "// TRANSMISION ENTRANTE", true);
            g.blit(tex, px + 6, py + 16, dw, dh, 0f, 0f, an.w, an.h, an.w, an.h);
            Draw.corners(g, px + 4, py + 14, dw + 4, dh + 4, 6, t.accent);
            int ty = py + 16 + dh + 6;
            for (FormattedCharSequence l : lines) { g.drawCenteredString(font, l, w / 2, ty, 0xFFFFFFFF); ty += 10; }
            g.pose().popPose();
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        g.pose().popPose();
    }

    private static void caption(GuiGraphics g, Font font, Theme t, String text, int cx, int y, int maxW, float scale) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), (int) (maxW / scale));
        int ty = y;
        for (FormattedCharSequence l : lines) {
            int lw = (int) (font.width(l) * scale);
            g.fill(cx - lw / 2 - 4, ty - 2, cx + lw / 2 + 4, ty + (int) (9 * scale), 0xAA000000);
            g.pose().pushPose();
            g.pose().translate(cx - lw / 2f, ty, 0);
            g.pose().scale(scale, scale, 1f);
            g.drawString(font, l, 0, 0, 0xFFFFFFFF, false);
            g.pose().popPose();
            ty += (int) (11 * scale);
        }
    }
}
