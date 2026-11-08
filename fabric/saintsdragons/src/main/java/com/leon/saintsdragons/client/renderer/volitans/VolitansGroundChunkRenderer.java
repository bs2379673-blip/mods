package com.leon.saintsdragons.client.renderer.volitans;

import com.leon.saintsdragons.client.model.volitans.VolitansGroundChunkModel;
import com.leon.saintsdragons.server.entity.effect.volitans.VolitansGroundChunkEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.cache.model.GeoBone;
import software.bernie.geckolib.cache.model.cuboid.GeoCube;
import software.bernie.geckolib.cache.model.GeoQuad;
import software.bernie.geckolib.cache.model.GeoVertex;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.RenderUtil;

public class VolitansGroundChunkRenderer extends com.leon.saintsdragons.client.compat.geo.LegacyGeoEntityRenderer<VolitansGroundChunkEntity> {
    private static final Identifier DIRT_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/block/dirt.png");
    private static final float MODEL_FORWARD_YAW_OFFSET = 0.0F;

    public VolitansGroundChunkRenderer(EntityRendererProvider.Context context) {
        super(context, new VolitansGroundChunkModel());
        this.shadowRadius = 0.0F;
    }

    @Override
    protected float getDeathMaxRotation(@NotNull VolitansGroundChunkEntity entity) {
        return 0.0F;
    }

    @Override
    public @NotNull Identifier getTextureLocation(@NotNull VolitansGroundChunkEntity entity) {
        return DIRT_TEXTURE;
    }

    @Override
    public void render(@NotNull VolitansGroundChunkEntity entity, float entityYaw, float partialTick,
                       @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight) {
        if (!entity.isReady()) {
            return;
        }
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getVisualYaw() + MODEL_FORWARD_YAW_OFFSET));
        super.render(entity, 0.0F, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    // port 1.21.11: los cubos del modelo se dibujan como el bloque del pedazo de tierra
    @Override
    protected boolean isBlockBone(VolitansGroundChunkEntity entity, GeoBone bone) {
        return true;
    }

    @Override
    protected BlockState blockFor(VolitansGroundChunkEntity entity) {
        return entity.getBlockState();
    }
}
