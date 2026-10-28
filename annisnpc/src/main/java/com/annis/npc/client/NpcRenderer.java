package com.annis.npc.client;

import com.annis.npc.entity.AnnisNpcEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class NpcRenderer extends LivingEntityRenderer<AnnisNpcEntity, PlayerEntityModel<AnnisNpcEntity>> {
    private static final Identifier STEVE = Identifier.of("minecraft", "textures/entity/player/wide/steve.png");
    private static final Identifier ALEX = Identifier.of("minecraft", "textures/entity/player/slim/alex.png");

    private final NpcModel classicModel;
    private final NpcModel slimModel;

    public NpcRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new NpcModel(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        this.classicModel = (NpcModel) this.model;
        this.slimModel = new NpcModel(ctx.getPart(EntityModelLayers.PLAYER_SLIM), true);

        this.addFeature(new ArmorFeatureRenderer<>(this,
                new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
                new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
        this.addFeature(new HeldItemFeatureRenderer<>(this, ctx.getHeldItemRenderer()));
        this.addFeature(new HeadFeatureRenderer<>(this, ctx.getModelLoader(), ctx.getHeldItemRenderer()));
        this.addFeature(new AccessoryFeature(this, ctx.getHeldItemRenderer()));
    }

    @Override
    public Identifier getTexture(AnnisNpcEntity npc) {
        Identifier custom = SkinManager.get(npc.getSkin());
        if (custom != null) {
            return custom;
        }
        return npc.isSlim() ? ALEX : STEVE;
    }

    @Override
    public void render(AnnisNpcEntity npc, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        NpcAnimState.of(npc).applier.setTickDelta(tickDelta);
        this.model = npc.isSlim() ? this.slimModel : this.classicModel;
        super.render(npc, yaw, tickDelta, matrices, vertexConsumers, light);
    }
}
