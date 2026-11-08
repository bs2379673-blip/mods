package com.starlevi.starpackport.mixin.client;

import com.starlevi.starpackport.ModelRotations;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.client.renderer.block.model.FaceBakery;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gira los vertices como 1.21.11 (cualquier angulo y varios ejes). */
@Mixin(FaceBakery.class)
public abstract class FaceBakeryMixin {
    @Inject(method = "applyElementRotation", at = @At("HEAD"), cancellable = true)
    private void starpackport$rotate(Vector3f pos, BlockElementRotation rot, CallbackInfo ci) {
        if (rot != null) {
            ModelRotations.apply(pos, rot);
            ci.cancel();
        }
    }
}
