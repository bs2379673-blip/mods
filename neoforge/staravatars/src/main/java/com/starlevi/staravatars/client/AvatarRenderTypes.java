package com.starlevi.staravatars.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Como entityCutout de Minecraft, pero con la textura suavizada y con mipmaps (si no, la fuerza al pixel mas cercano). */
public final class AvatarRenderTypes extends RenderType {
    private static final Map<ResourceLocation, RenderType> WITH_CULL = new HashMap<>(), WITHOUT_CULL = new HashMap<>();

    private AvatarRenderTypes(String a, VertexFormat b, VertexFormat.Mode c, int d, boolean e, boolean f, Runnable g, Runnable h) {
        super(a, b, c, d, e, f, g, h);
    }

    public static RenderType cutout(ResourceLocation tex, boolean doubleSided) {
        Map<ResourceLocation, RenderType> cache = doubleSided ? WITHOUT_CULL : WITH_CULL;
        return cache.computeIfAbsent(tex, t -> create("staravatars_cutout" + (doubleSided ? "_nocull" : ""), DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 1536, true, false, CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_CUTOUT_SHADER)
                        .setTextureState(new TextureStateShard(t, true, true))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setCullState(doubleSided ? NO_CULL : CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .createCompositeState(true)));
    }
}
