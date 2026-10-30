package com.annis.npc.mixin;

import com.annis.npc.client.AnimOverlay;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emotes, actividades e interacciones de Annis tambien en jugadores. */
// prioridad alta: despues de playerAnimator (que reinicia los dobleces)
@Mixin(value = PlayerModel.class, priority = 1500)
public abstract class PlayerModelMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void annisnpc$overlay(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress,
                                  float headYaw, float headPitch, CallbackInfo ci) {
        if (animationProgress == 0.0f && limbAngle == 0.0f && limbDistance == 0.0f) {
            return; // brazo en primera persona
        }
        AnimOverlay.afterSetAngles((PlayerModel<?>) (Object) this, entity, animationProgress);
    }
}
