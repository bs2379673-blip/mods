package com.annis.interact.mixin;

import com.annis.interact.CarryPose;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Coloca al que va en brazos / a caballito delante o detras del que lo lleva.
 * LivingEntity tiene su propia version (usa el giro del cuerpo), por eso se engancha en las dos.
 */
@Mixin({Entity.class, LivingEntity.class})
public abstract class EntityRidingMixin {
    @Inject(method = "getPassengerRidingPosition", at = @At("HEAD"), cancellable = true, require = 0)
    private void annisinteract$carry(Entity passenger, CallbackInfoReturnable<Vec3> cir) {
        Vec3 pos = CarryPose.ridingPos((Entity) (Object) this, passenger);
        if (pos != null) {
            cir.setReturnValue(pos);
        }
    }
}
