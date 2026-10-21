package com.tunombre.obscurenpc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tunombre.obscurenpc.Actor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/** Renderer vanilla del NPC (modelo de jugador). Epic Fight tambien lo usa para pedir la textura y las capas. */
public class NpcRenderer extends HumanoidMobRenderer<Actor, NpcModel> {
    private final NpcModel wide;
    private final NpcModel slim;

    public NpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new NpcModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wide = this.model;
        this.slim = new NpcModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<Actor>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<Actor>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
    }

    @Override
    public void render(Actor a, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        this.model = a.getEntityData().get(Actor.SLIM) ? slim : wide;
        super.render(a, yaw, pt, ps, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(Actor a) { return SkinCache.get(a); }

    @Override
    protected void scale(Actor a, PoseStack ps, float pt) {
        float s = a.scale();
        ps.scale(s, s, s);
        if (a.stance() == 1) ps.translate(0.0F, 0.45F, 0.0F);
    }

    @Override
    protected boolean shouldShowName(Actor a) { return false; }
}
