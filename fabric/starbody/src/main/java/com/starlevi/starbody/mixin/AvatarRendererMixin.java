package com.starlevi.starbody.mixin;

import com.starlevi.starbody.client.StarBodyClient;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Avisa a StarBody de cada jugador que se dibuja y le cambia la skin por la "sin el pelo que cuelga". */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void starbody$extract(Avatar entity, AvatarRenderState state, float partial, CallbackInfo ci) {
        StarBodyClient.extract(entity, state, partial);
    }

    @Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/resources/Identifier;", at = @At("RETURN"), cancellable = true)
    private void starbody$texture(AvatarRenderState state, CallbackInfoReturnable<Identifier> cir) {
        Identifier swap = StarBodyClient.texture(state);
        if (swap != null) {
            cir.setReturnValue(swap);
        }
    }
}
