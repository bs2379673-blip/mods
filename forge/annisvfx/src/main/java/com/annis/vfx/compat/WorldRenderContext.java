package com.annis.vfx.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

/** Lo que trae un evento de dibujo del mundo. matrixStack() ya tiene el giro de la camara (1.20.1). */
public record WorldRenderContext(PoseStack matrixStack, Camera camera, Matrix4f projectionMatrix, float partialTick,
                                 MultiBufferSource consumers, ClientLevel world) {
    public DeltaTracker tickCounter() {
        return DeltaTracker.INSTANCE;
    }
}
