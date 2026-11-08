package com.leon.saintsdragons.client.renderer.vfx;

import com.leon.saintsdragons.client.renderer.ShaderPassCompatibility;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Unlit alpha blending in vanilla, retaining the existing entity pass for shader packs. */
public abstract class BeamRenderTypes {
    private static final Map<Identifier, RenderType> UNLIT = new HashMap<>();

    public static RenderType translucent(Identifier texture) {
        if (ShaderPassCompatibility.isShaderPackInUse()) {
            return net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(texture);
        }
        return UNLIT.computeIfAbsent(texture, BeamRenderTypes::createUnlit);
    }

    private static RenderType createUnlit(Identifier texture) {
        return net.minecraft.client.renderer.rendertype.RenderTypes.beaconBeam(texture, true);
    }
}
