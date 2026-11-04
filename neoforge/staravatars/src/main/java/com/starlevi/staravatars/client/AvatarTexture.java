package com.starlevi.staravatars.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Textura de personaje con mipmaps (versiones mas chicas ya promediadas), como las dibuja Roblox: de
 * lejos no se ve granulada ni con ruido. Minecraft por defecto usa el pixel mas cercano y sin mipmaps,
 * que con texturas de 1024 px queda muy feo.
 */
public class AvatarTexture extends AbstractTexture {
    private final NativeImage image;

    public AvatarTexture(NativeImage image) {
        this.image = image;
        RenderSystem.assertOnRenderThreadOrInit();
        int w = image.getWidth(), h = image.getHeight();
        int levels = 0;
        while ((w >> (levels + 1)) >= 1 && (h >> (levels + 1)) >= 1 && levels < 8) {
            levels++;
        }
        TextureUtil.prepareImage(getId(), levels, w, h);
        NativeImage cur = image;
        for (int lv = 0; lv <= levels; lv++) {
            cur.upload(lv, 0, 0, false);
            if (lv < levels) {
                NativeImage next = half(cur);
                if (cur != image) {
                    cur.close();
                }
                cur = next;
            }
        }
        if (cur != image) {
            cur.close();
        }
        bind();
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, levels);
    }

    /** Mitad de tamano promediando de a 4 pixeles (con el alfa pesado, para que no se oscurezcan los bordes). */
    private static NativeImage half(NativeImage src) {
        int w = Math.max(1, src.getWidth() / 2), h = Math.max(1, src.getHeight() / 2);
        NativeImage out = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = 0, g = 0, b = 0, a = 0, ws = 0;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int sx = Math.min(src.getWidth() - 1, x * 2 + dx), sy = Math.min(src.getHeight() - 1, y * 2 + dy);
                        int p = src.getPixelRGBA(sx, sy); // ABGR
                        int pa = p >>> 24;
                        int wt = pa + 1;
                        r += (p & 0xFF) * wt;
                        g += (p >> 8 & 0xFF) * wt;
                        b += (p >> 16 & 0xFF) * wt;
                        a += pa;
                        ws += wt;
                    }
                }
                out.setPixelRGBA(x, y, (a / 4) << 24 | (b / ws) << 16 | (g / ws) << 8 | (r / ws));
            }
        }
        return out;
    }

    @Override
    public void load(ResourceManager manager) {
        // ya esta subida
    }

    @Override
    public void close() {
        this.image.close();
        super.close();
    }
}
