package com.annis.npc.mixin;

import com.annis.npc.client.AnimOverlay;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emotes, actividades e interacciones de Annis tambien en jugadores. */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void annisnpc$overlay(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress,
                                  float headYaw, float headPitch, CallbackInfo ci) {
        if (animationProgress == 0.0f && limbAngle == 0.0f && limbDistance == 0.0f) {
            return; // brazo en primera persona
        }
        AnimOverlay.afterSetAngles((PlayerEntityModel<?>) (Object) this, entity, animationProgress);
    }
}
