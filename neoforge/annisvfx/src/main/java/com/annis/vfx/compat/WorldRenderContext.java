package com.annis.vfx.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

public record WorldRenderContext(PoseStack matrixStack, Camera camera, Matrix4f projectionMatrix, DeltaTracker tickCounter,
                                 MultiBufferSource consumers, ClientLevel world) {
}
