package com.starlevi.starscenes.mixin;

import com.starlevi.starscenes.client.PlayerGestures;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Los gestos de StarScenes (abrazo, beso...) encima de la animacion del jugador. */
@Mixin(value = PlayerModel.class, priority = 1500)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void starscenes$gesture(AvatarRenderState state, CallbackInfo ci) {
        PlayerGestures.apply(state.id, (HumanoidModel<?>) (Object) this);
    }
}
