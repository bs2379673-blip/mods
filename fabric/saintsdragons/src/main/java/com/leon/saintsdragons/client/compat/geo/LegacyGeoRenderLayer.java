package com.leon.saintsdragons.client.compat.geo;

import com.leon.saintsdragons.client.compat.render.DeferredBufferSource;
import com.leon.saintsdragons.util.compat.SDTickets;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.model.BakedGeoModel;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.renderer.base.RenderPassInfo;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** Port 1.21.11: una capa de GeckoLib 4 (render(...) con reRender del modelo) sobre GeckoLib 5. */
public abstract class LegacyGeoRenderLayer<T extends Entity & GeoAnimatable> extends GeoRenderLayer<T, Void, SDRenderState> {
    protected LegacyGeoRenderLayer(LegacyGeoEntityRenderer<T> renderer) {
        super(renderer);
    }

    @Override
    @SuppressWarnings("unchecked")
    public LegacyGeoEntityRenderer<T> getRenderer() {
        return (LegacyGeoEntityRenderer<T>) this.renderer;
    }

    /** Como en GeckoLib 4. */
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType, MultiBufferSource bufferSource,
                       VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
    }

    @Override
    @SuppressWarnings("unchecked")
    public void submitRenderTask(RenderPassInfo<SDRenderState> info, SubmitNodeCollector collector) {
        T e = (T) info.renderState().getGeckolibData(SDTickets.ENTITY);
        if (e == null || !info.willRender()) {
            return;
        }
        Float pt = info.renderState().getGeckolibData(DataTickets.PARTIAL_TICK);
        DeferredBufferSource buffers = new DeferredBufferSource();
        LegacyGeoEntityRenderer.pushLayer(info, collector);
        RenderPassInfo<?> prevInfo = CURRENT.get();
        CURRENT.set(info);
        try {
            render(info.poseStack(), e, info.model(), null, buffers, null, pt == null ? 1 : pt, info.packedLight(), info.packedOverlay());
        } finally {
            LegacyGeoEntityRenderer.popLayer();
            CURRENT.set(prevInfo);
        }
        buffers.submit(collector);
    }

    private static final ThreadLocal<RenderPassInfo<?>> CURRENT = new ThreadLocal<>();

    /**
     * Port 1.21.11: donde esta un hueso en el mundo en este cuadro (en GeckoLib 4 se leia de la matriz del hueso).
     * Solo sirve dentro de render(...) de una capa.
     */
    public static net.minecraft.world.phys.Vec3 boneWorldPosition(String boneName) {
        RenderPassInfo<?> info = CURRENT.get();
        if (info == null || boneName == null) {
            return null;
        }
        var bone = info.model().getBone(boneName);
        if (bone.isEmpty()) {
            return null;
        }
        net.minecraft.world.phys.Vec3[] out = new net.minecraft.world.phys.Vec3[1];
        info.renderPosed(() -> {
            PoseStack ps = new PoseStack();
            ps.last().set(info.getModelRenderMatrixPose());
            software.bernie.geckolib.util.RenderUtil.transformToBone(ps, bone.get());
            org.joml.Matrix4f m = ps.last().pose();
            net.minecraft.world.phys.Vec3 cam = info.cameraState() == null || info.cameraState().pos == null
                    ? net.minecraft.world.phys.Vec3.ZERO : info.cameraState().pos;
            out[0] = new net.minecraft.world.phys.Vec3(cam.x + m.m30(), cam.y + m.m31(), cam.z + m.m32());
        });
        return out[0];
    }
}
