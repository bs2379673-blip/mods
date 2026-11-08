package com.leon.saintsdragons.client.renderer.layer;

import com.leon.saintsdragons.server.entity.base.DragonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.cache.model.BakedGeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.function.Predicate;

public final class DragonEquipmentLayer<T extends DragonEntity> extends GeoRenderLayer<T> {
    private final Predicate<T> visible;
    private final Identifier texture;

    public DragonEquipmentLayer(GeoRenderer<T> renderer, Predicate<T> visible, Identifier texture) {
        super(renderer);
        this.visible = visible;
        this.texture = texture;
    }

    @Override
    public void render(
            @NotNull PoseStack poseStack,
            T animatable,
            BakedGeoModel bakedModel,
            @NotNull RenderType renderType,
            @NotNull MultiBufferSource bufferSource,
            @NotNull VertexConsumer buffer,
            float partialTick,
            int packedLight,
            int packedOverlay
    ) {
        if (!visible.test(animatable)) {
            return;
        }

        RenderType equipmentRenderType = RenderType.entityCutoutNoCull(texture);
        VertexConsumer equipmentBuffer = bufferSource.getBuffer(equipmentRenderType);
        getRenderer().reRender(
                bakedModel,
                poseStack,
                bufferSource,
                animatable,
                equipmentRenderType,
                equipmentBuffer,
                partialTick,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );
    }
}
