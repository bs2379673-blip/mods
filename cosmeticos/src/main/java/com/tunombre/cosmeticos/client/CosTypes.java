package com.tunombre.cosmeticos.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Tipo de dibujo para los brillos (destellos, estrellas, estelas): mezcla por transparencia normal usando el canal alfa de la textura,
 * sin escribir profundidad y sin culling. El tipo "eyes" de vanilla (aditivo) dejaba cuadrados blancos duros en algunos dibujados.
 */
public final class CosTypes extends RenderType {
    private static final Map<ResourceLocation, RenderType> GLOW = new HashMap<>();

    private CosTypes() { super("cosmeticos_dummy", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, false, () -> { }, () -> { }); }

    public static RenderType glow(ResourceLocation tex) {
        return GLOW.computeIfAbsent(tex, t -> create("cosmeticos_glow", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new TextureStateShard(t, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(true)));
    }
}
