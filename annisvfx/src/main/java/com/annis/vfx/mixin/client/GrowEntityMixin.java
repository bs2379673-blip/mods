package com.annis.vfx.mixin.client;

import com.annis.vfx.client.Reveal;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Al aparecer, el personaje crece desde la luz (escala desde los pies). */
@Mixin(value = LivingEntityRenderer.class, priority = 800)
public abstract class GrowEntityMixin {
    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void annisvfx$grow(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                               VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        float s = Reveal.scale(entity, tickDelta);
        if (s != 1.0f) {
            matrices.scale(s, s, s);
        }
    }
}
