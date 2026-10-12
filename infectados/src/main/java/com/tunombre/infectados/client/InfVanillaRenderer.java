package com.tunombre.infectados.client;

import com.tunombre.infectados.Infectados;
import com.tunombre.infectados.InfMob;
import com.tunombre.infectados.mob.Ganado;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Renderer vanilla (Epic Fight le pide la textura; el modelo 3D lo pone InfRenderer). */
public class InfVanillaRenderer extends HumanoidMobRenderer<InfMob, HumanoidModel<InfMob>> {
    private final String name;

    public InfVanillaRenderer(EntityRendererProvider.Context ctx, String name) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
        this.name = name;
    }

    public static ResourceLocation tex(String n) { return new ResourceLocation(Infectados.MODID, "textures/entity/" + n + ".png"); }

    @Override
    public ResourceLocation getTextureLocation(InfMob e) {
        if (e instanceof Ganado g) return tex("ganado" + g.variant() + (g.outfit() > 0 ? "_" + g.outfit() : "") + (g.headless() ? "_sincabeza" : ""));
        return tex(name);
    }
}
