package com.starlevi.starriders.mixin;

import com.starlevi.starriders.client.RiderPose;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Despues de la pose normal (y de Punchy Moves): pose del jinete agarrado a la montura. */
@Mixin(value = PlayerModel.class, priority = 1450)
public abstract class RiderPoseMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void starriders$rider(LivingEntity entity, float limbAngle, float limbDistance, float ageInTicks,
                                  float headYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof Player p && p.isPassenger() && !(ageInTicks == 0.0f && limbAngle == 0.0f && limbDistance == 0.0f)) {
            RiderPose.apply((PlayerModel<?>) (Object) this, p, ageInTicks);
        }
    }
}
