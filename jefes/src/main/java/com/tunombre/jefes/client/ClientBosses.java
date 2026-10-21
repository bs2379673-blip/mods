package com.tunombre.jefes.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.tunombre.jefes.Boss;
import com.tunombre.jefes.Jefes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.util.IdentityHashMap;
import java.util.Map;

/** Texturas de los jefes: llegan del servidor como PNG y se registran como texturas dinamicas. */
public final class ClientBosses {
    private static final Map<Boss, ResourceLocation> TEX = new IdentityHashMap<>();

    private ClientBosses() { }

    public static synchronized ResourceLocation texture(Boss b) {
        ResourceLocation rl = TEX.get(b);
        if (rl != null) return rl;
        rl = new ResourceLocation(Jefes.MODID, "dyn/" + b.id().replaceAll("[^a-z0-9_]", "_"));
        try (NativeImage img = NativeImage.read(new ByteArrayInputStream(b.png))) {
            Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(img));
        } catch (Exception e) {
            Jefes.LOG.error("[jefes] textura rota de {}", b.id(), e);
        }
        TEX.put(b, rl);
        return rl;
    }

    public static synchronized void clear() { TEX.clear(); }
}
