package com.starlevi.starmodels.mixin;

import com.starlevi.starmodels.client.HidesVanilla;
import com.starlevi.starmodels.client.StarModelsClient;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Anota en el estado de dibujo de cada jugador que personaje lleva. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void starmodels$extract(Avatar entity, AvatarRenderState state, float partial, CallbackInfo ci) {
        StarModelsClient.extract(entity, state);
    }

    @Inject(method = {"renderRightHand", "renderLeftHand"}, at = @At("HEAD"))
    private void starmodels$hand(CallbackInfo ci) {
        if (((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel() instanceof HidesVanilla h) {
            h.starmodels$show();
        }
    }
}
