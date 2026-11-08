package com.leon.saintsdragons.fabric.mixin.fabric;

import com.leon.saintsdragons.fabric.client.camera.DragonCameraState;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port 1.21.11: el giro (alabeo) de la camara al volar. Mientras Camera.setup corre, cada setRotation lleva
 * el giro del dragon (al reves si la vista esta espejada); fuera de setup, nada.
 */
@Mixin(Camera.class)
public class CameraRollMixin {
    @Unique
    private float saintsdragons$tempRoll = 0.0f;

    @Inject(method = "setup", at = @At("HEAD"), require = 0)
    private void saintsdragons$prepareRoll(net.minecraft.world.level.Level area, Entity focusedEntity, boolean thirdPerson,
                                           boolean inverseView, float partialTick, CallbackInfo ci) {
        float roll = DragonCameraState.getCurrentRoll();
        this.saintsdragons$tempRoll = inverseView ? -roll : roll;
    }

    @Inject(method = "setup", at = @At("RETURN"), require = 0)
    private void saintsdragons$clearRoll(net.minecraft.world.level.Level area, Entity focusedEntity, boolean thirdPerson,
                                         boolean inverseView, float partialTick, CallbackInfo ci) {
        this.saintsdragons$tempRoll = 0.0f;
    }

    @ModifyArg(
            method = "setRotation",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Quaternionf;rotationYXZ(FFF)Lorg/joml/Quaternionf;",
                    remap = false
            ),
            index = 2,
            require = 0
    )
    private float saintsdragons$injectRollIntoCamera(float originalRoll) {
        float roll = this.saintsdragons$tempRoll;
        return roll != 0.0f ? roll * ((float) Math.PI / 180.0f) : originalRoll;
    }
}
