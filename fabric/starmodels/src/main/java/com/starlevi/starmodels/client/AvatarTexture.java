package com.starlevi.starmodels.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.renderer.texture.AbstractTexture;

/**
 * Textura de personaje con mipmaps y filtro suave, como las dibuja Roblox: de lejos no se ve granulada.
 * (Minecraft usa el pixel mas cercano y sin mipmaps, que con texturas de 1024 px queda muy feo.)
 */
public class AvatarTexture extends AbstractTexture {
    public AvatarTexture(String label, NativeImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int levels = 1;
        while ((w >> levels) >= 1 && (h >> levels) >= 1 && levels < 9) {
            levels++;
        }
        GpuDevice dev = RenderSystem.getDevice();
        this.texture = dev.createTexture(() -> label, GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST, TextureFormat.RGBA8, w, h, 1, levels);
        this.textureView = dev.createTextureView(this.texture);
        this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR, true);
        CommandEncoder enc = dev.createCommandEncoder();
        NativeImage cur = image;
        for (int lv = 0; lv < levels; lv++) {
            enc.writeToTexture(this.texture, cur, lv, 0, 0, 0, cur.getWidth(), cur.getHeight(), 0, 0);
            if (lv < levels - 1) {
                NativeImage next = half(cur);
                cur.close();
                cur = next;
            }
        }
        cur.close();
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
                        int p = src.getPixel(sx, sy); // ARGB
                        int pa = p >>> 24;
                        int wt = pa + 1;
                        r += (p >> 16 & 0xFF) * wt;
                        g += (p >> 8 & 0xFF) * wt;
                        b += (p & 0xFF) * wt;
                        a += pa;
                        ws += wt;
                    }
                }
                out.setPixel(x, y, (a / 4) << 24 | (r / ws) << 16 | (g / ws) << 8 | (b / ws));
            }
        }
        return out;
    }
}
