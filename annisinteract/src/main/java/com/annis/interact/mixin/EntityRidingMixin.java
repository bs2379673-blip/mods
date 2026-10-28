package com.annis.interact.mixin;

import com.annis.interact.CarryPose;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Coloca al que va en brazos / a caballito delante o detras del que lo lleva. */
@Mixin(Entity.class)
public abstract class EntityRidingMixin {
    @Inject(method = "getPassengerRidingPos", at = @At("HEAD"), cancellable = true)
    private void annisinteract$carry(Entity passenger, CallbackInfoReturnable<Vec3d> cir) {
        Vec3d pos = CarryPose.ridingPos((Entity) (Object) this, passenger);
        if (pos != null) {
            cir.setReturnValue(pos);
        }
    }
}
