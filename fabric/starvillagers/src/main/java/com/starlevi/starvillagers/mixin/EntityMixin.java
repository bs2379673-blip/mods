package com.starlevi.starvillagers.mixin;

import com.starlevi.starvillagers.social.Together;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Al cargar a alguien en brazos, va adelante a la altura del pecho (no arriba de la cabeza). */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "getPassengerAttachmentPoint", at = @At("HEAD"), cancellable = true)
    private void starvillagers$carry(Entity passenger, EntityDimensions dims, float scale, CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity) (Object) this;
        if (Together.carrying(self, passenger)) {
            cir.setReturnValue(new Vec3(0, Math.max(0.1, dims.height() * 0.58 - passenger.getBbHeight() * 0.38), 0.42 * scale));
        }
    }
}
