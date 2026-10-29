package com.annis.interact.mixin;

import com.annis.interact.CarryPose;
import com.annis.interact.Sessions;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Durante una interaccion (abrazo, cama...) los dos no se empujan aunque esten pegados. */
@Mixin(LivingEntity.class)
public abstract class NoPushMixin {
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void annisinteract$noPush(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        boolean anchored = self.getWorld().isClient ? CarryPose.clientAnchored.test(self) : Sessions.anchorOf(self) != null;
        if (anchored) {
            cir.setReturnValue(false);
        }
    }
}
