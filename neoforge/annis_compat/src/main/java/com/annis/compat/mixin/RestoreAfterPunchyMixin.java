package com.annis.compat.mixin;

import com.annis.compat.PunchyPause;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Despues de Punchy Moves y antes de Annis NPC (1500): devuelve la pose de Cinematic. */
@Mixin(value = PlayerModel.class, priority = 1400)
public abstract class RestoreAfterPunchyMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void annis_compat$restore(LivingEntity entity, float limbAngle, float limbDistance, float ageInTicks,
                                 float headYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player player)) {
            return;
        }
        if (ageInTicks == 0.0f && limbAngle == 0.0f && limbDistance == 0.0f) {
            return; // brazo en primera persona
        }
        if (PunchyPause.enabled()) {
            PunchyPause.restore((PlayerModel<?>) (Object) this, player, ageInTicks);
        }
        if (PunchyPause.probe != null) {
            PunchyPause.probe.accept((PlayerModel<?>) (Object) this, player);
        }
    }
}
