package com.annis.interact.mixin;

import com.annis.interact.CarryPose;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Coloca al que va en brazos / a caballito delante o detras del que lo lleva (pies en el punto pedido). */
@Mixin(Entity.class)
public abstract class EntityRidingMixin {
    @Inject(method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V",
            at = @At("HEAD"), cancellable = true)
    private void annisinteract$carry(Entity passenger, Entity.MoveFunction move, CallbackInfo ci) {
        Vec3 pos = CarryPose.ridingPos((Entity) (Object) this, passenger);
        if (pos != null) {
            move.accept(passenger, pos.x, pos.y, pos.z);
            ci.cancel();
        }
    }
}
