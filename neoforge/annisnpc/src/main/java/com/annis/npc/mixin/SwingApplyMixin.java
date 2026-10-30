package com.annis.npc.mixin;

import com.annis.npc.client.SwingFix;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Despues de Punchy Moves: le devuelve el golpe de la mano si su animacion lo borro. */
@Mixin(value = PlayerModel.class, priority = 1100)
public abstract class SwingApplyMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void annisnpc$applySwing(LivingEntity entity, float limbAngle, float limbDistance, float ageInTicks,
                                     float headYaw, float headPitch, CallbackInfo ci) {
        if (ageInTicks == 0.0f && limbAngle == 0.0f && limbDistance == 0.0f) {
            return; // brazo en primera persona
        }
        SwingFix.apply((PlayerModel<?>) (Object) this, entity, ageInTicks);
    }
}
