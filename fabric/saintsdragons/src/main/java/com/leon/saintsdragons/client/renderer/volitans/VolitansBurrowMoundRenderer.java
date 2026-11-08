package com.leon.saintsdragons.client.renderer.volitans;

import com.leon.saintsdragons.client.model.volitans.VolitansBurrowMoundModel;
import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.server.entity.effect.volitans.VolitansBurrowMoundEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.model.GeoBone;
import software.bernie.geckolib.cache.model.cuboid.GeoCube;
import software.bernie.geckolib.cache.model.GeoQuad;
import software.bernie.geckolib.cache.model.GeoVertex;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.RenderUtil;

import java.util.ArrayDeque;
import java.util.Deque;

public class VolitansBurrowMoundRenderer extends com.leon.saintsdragons.client.compat.geo.LegacyGeoEntityRenderer<VolitansBurrowMoundEntity> {
    private static final Identifier TEXTURE = SaintsDragonsCommon.rl("textures/objects/volitans/burrow_mound.png");
    private static final float MODEL_FORWARD_YAW_OFFSET = 0.0F;

    public VolitansBurrowMoundRenderer(EntityRendererProvider.Context context) {
        super(context, new VolitansBurrowMoundModel());
        this.shadowRadius = 0.0F;
    }

    @Override
    protected float getDeathMaxRotation(@NotNull VolitansBurrowMoundEntity entity) {
        return 0.0F;
    }

    @Override
    public void render(@NotNull VolitansBurrowMoundEntity entity, float entityYaw, float partialTick,
                       @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight) {
        if (entity.getOpacity(partialTick) <= 0.001F) {
            return;
        }
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getVisualYaw() + MODEL_FORWARD_YAW_OFFSET));
        super.render(entity, 0.0F, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    private static boolean usesBlockMaterial(GeoBone bone) {
        String name = bone.name();
        return "pillars".equals(name) || "formations".equals(name);
    }

    @Override
    public @NotNull Identifier getTextureLocation(@NotNull VolitansBurrowMoundEntity entity) {
        return TEXTURE;
    }

    @Override
    public RenderType getRenderType(VolitansBurrowMoundEntity animatable, Identifier texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(texture);
    }

    // port 1.21.11: los pilares y formaciones se dibujan como el bloque del suelo, achicandose al desaparecer
    @Override
    protected boolean isBlockBone(VolitansBurrowMoundEntity entity, GeoBone bone) {
        return usesBlockMaterial(bone);
    }

    @Override
    protected BlockState blockFor(VolitansBurrowMoundEntity entity) {
        return entity.getBlockState();
    }

    @Override
    protected float blockScale(VolitansBurrowMoundEntity entity, float partialTick) {
        return entity.getOpacity(partialTick);
    }
}
