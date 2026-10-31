package com.annis.compat.mixin;

import com.annis.compat.client.CinematicModule;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Despues de Punchy Moves (prioridad 900) y antes de Annis NPC (1500): asi las animaciones de las
 * estaciones ganan a la caminata de Punchy, y un emote o interaccion de Annis sigue ganando.
 */
@Mixin(value = PlayerModel.class, priority = 1400)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void annis_compat$stations(LivingEntity entity, float limbAngle, float limbDistance, float ageInTicks,
                                       float headYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player player) || !CinematicModule.active()) {
            return;
        }
        if (ageInTicks == 0.0f && limbAngle == 0.0f && limbDistance == 0.0f) {
            return; // brazo en primera persona
        }
        try {
            CinematicModule.afterSetupAnim((PlayerModel<?>) (Object) this, player, ageInTicks);
        } catch (Throwable ignored) {
            // nunca romper el dibujo del jugador por esto
        }
    }
}
