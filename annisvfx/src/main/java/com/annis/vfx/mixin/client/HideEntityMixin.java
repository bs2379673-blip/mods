package com.annis.vfx.mixin.client;

import com.annis.vfx.client.Reveal;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Quien todavia no "nacio" en la escena no se dibuja (ni su sombra ni su nombre). */
@Mixin(EntityRenderDispatcher.class)
public abstract class HideEntityMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void annisvfx$hide(E entity, double x, double y, double z, float yaw, float tickDelta,
                                                  MatrixStack matrices, VertexConsumerProvider consumers, int light,
                                                  CallbackInfo ci) {
        if (Reveal.hidden(entity)) {
            ci.cancel();
        }
    }
}
