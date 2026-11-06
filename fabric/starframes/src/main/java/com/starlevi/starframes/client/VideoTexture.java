package com.starlevi.starframes.client;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.renderer.texture.AbstractTexture;

/**
 * La textura de video de WaterMedia vista como una textura de Minecraft, sin copiarla: Minecraft la usa
 * para dibujar, pero nunca la borra (es de WaterMedia y la libera el reproductor).
 */
final class VideoTexture extends AbstractTexture {
    final int glId, width, height;

    /** Envoltorio de una textura OpenGL ajena: cerrarlo no la borra. */
    private static final class External extends GlTexture {
        External(int id, int w, int h) {
            super(GpuTexture.USAGE_TEXTURE_BINDING, "starframes_video", TextureFormat.RGBA8, w, h, 1, 1, id);
        }

        @Override
        public void close() {
            this.closed = true;
        }

        @Override
        public void removeViews() {
            // la textura no es nuestra: nunca se borra desde aca
        }
    }

    VideoTexture(int glId, int width, int height) {
        this.glId = glId;
        this.width = width;
        this.height = height;
        this.texture = new External(glId, Math.max(1, width), Math.max(1, height));
        this.textureView = RenderSystem.getDevice().createTextureView(this.texture);
        this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    @Override
    public void close() {
        if (this.textureView != null) {
            this.textureView.close();
            this.textureView = null;
        }
        if (this.texture != null) {
            this.texture.close();
            this.texture = null;
        }
    }
}
