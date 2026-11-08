package com.leon.saintsdragons.client.compat.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * Port 1.21.11: un EntityRenderer con la forma de 1.20.1 (render(entidad, ...) con MultiBufferSource).
 * El juego ahora junta lo que se dibuja y lo dibuja despues: lo de render() se guarda y se manda al final.
 */
public abstract class LegacyEntityRenderer<T extends Entity> extends EntityRenderer<T, LegacyEntityRenderer.State> {
    public static class State extends net.minecraft.client.renderer.entity.state.EntityRenderState {
        public Entity entity;
        public float partialTick;
        public float yaw;
    }

    protected LegacyEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.entity = entity;
        state.partialTick = partialTick;
        state.yaw = entity.getYRot(partialTick);
    }

    public Identifier getTextureLocation(T entity) {
        return null;
    }

    /** Como en 1.20.1. */
    @SuppressWarnings("unused")
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
    }

    @Override
    @SuppressWarnings("unchecked")
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, poseStack, collector, camera);
        if (state.entity == null) {
            return;
        }
        DeferredBufferSource buffers = new DeferredBufferSource();
        poseStack.pushPose();
        try {
            render((T) state.entity, state.yaw, state.partialTick, poseStack, buffers, state.lightCoords);
        } finally {
            poseStack.popPose();
        }
        buffers.submit(collector);
    }
}
