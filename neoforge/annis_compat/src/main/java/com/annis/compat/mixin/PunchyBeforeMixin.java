package com.annis.compat.mixin;

import com.annis.compat.client.CinematicModule;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Antes de Punchy Moves (prioridad 900): guarda la pose que dejo Cinematic Interactions. */
@Mixin(value = PlayerModel.class, priority = 800)
public abstract class PunchyBeforeMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void annis_compat$capture(LivingEntity entity, float limbAngle, float limbDistance, float ageInTicks,
                                      float headYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player player) || !CinematicModule.active()) {
            return;
        }
        if (ageInTicks == 0.0f && limbAngle == 0.0f && limbDistance == 0.0f) {
            return; // brazo en primera persona
        }
        try {
            CinematicModule.beforePunchy((PlayerModel<?>) (Object) this, player);
        } catch (Throwable ignored) {
            // nunca romper el dibujo del jugador por esto
        }
    }
}
