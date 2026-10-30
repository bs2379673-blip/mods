package com.annis.npc.client;

import com.annis.npc.entity.AnnisNpcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public class NpcRenderer extends LivingEntityRenderer<AnnisNpcEntity, PlayerModel<AnnisNpcEntity>> {
    private static final ResourceLocation STEVE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png");
    private static final ResourceLocation ALEX = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/player/slim/alex.png");

    private final NpcModel classicModel;
    private final NpcModel slimModel;
    private final NpcModel jointClassic = new NpcModel(JointModel.build(false), false);
    private final NpcModel jointSlim = new NpcModel(JointModel.build(true), true);

    public NpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new NpcModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.classicModel = (NpcModel) this.model;
        this.slimModel = new NpcModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);

        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
        this.addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
        this.addLayer(new CustomHeadLayer<>(this, ctx.getModelSet(), ctx.getItemInHandRenderer()));
        this.addLayer(new AccessoryFeature(this, ctx.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTexture(AnnisNpcEntity npc) {
        ResourceLocation custom = SkinManager.get(npc.getSkin());
        if (custom != null) {
            return custom;
        }
        return npc.isSlim() ? ALEX : STEVE;
    }

    @Override
    protected void setupTransforms(AnnisNpcEntity npc, PoseStack matrices, float animationProgress,
                                   float bodyYaw, float tickDelta, float scale) {
        super.setupRotations(npc, matrices, animationProgress, bodyYaw, tickDelta, scale);
        float yaw = NpcAnimState.of(npc).rootYaw(npc, tickDelta);
        if (yaw != 0.0f) {
            matrices.mulPose(com.mojang.math.Axis.YP.rotation(yaw));
        }
        applyRoot(matrices, NpcAnimState.of(npc).rootPose(npc, tickDelta));
    }

    /**
     * Pose de cuerpo entero en el marco del render (antes del volteo del modelo: delante = -Z, su
     * izquierda = -X, arriba = +Y). Mismo criterio que la capa "body" de playerAnimator.
     */
    public static void applyRoot(PoseStack matrices, Activities.RootPose rp) {
        if (rp == null) {
            return;
        }
        matrices.translate(-rp.left(), rp.up() + 0.7f, -rp.forward());
        matrices.mulPose(com.mojang.math.Axis.ZP.rotation(rp.roll()));
        matrices.mulPose(com.mojang.math.Axis.YP.rotation(rp.yaw()));
        matrices.mulPose(com.mojang.math.Axis.XP.rotation(rp.pitch()));
        matrices.translate(0.0f, -0.7f, 0.0f);
    }

    @Override
    public void render(AnnisNpcEntity npc, float yaw, float tickDelta, PoseStack matrices,
                       MultiBufferSource vertexConsumers, int light) {
        NpcAnimState.of(npc).applier.setTickDelta(tickDelta);
        if (npc.isJointed()) {
            this.model = npc.isSlim() ? this.jointSlim : this.jointClassic;
        } else {
            this.model = npc.isSlim() ? this.slimModel : this.classicModel;
        }
        super.render(npc, yaw, tickDelta, matrices, vertexConsumers, light);
    }
}
