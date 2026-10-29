package com.annis.interact.mixin.client;

import com.annis.interact.client.TouchCam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mimos en primera persona: se dibuja el propio cuerpo para ver las manos, pero sin la cabeza. */
@Mixin(PlayerEntityRenderer.class)
public abstract class TouchHideHeadMixin {
    @Inject(method = "setModelPose", at = @At("TAIL"))
    private void annisinteract$hideHead(AbstractClientPlayerEntity player, CallbackInfo ci) {
        if (TouchCam.active && TouchCam.firstPerson && player == MinecraftClient.getInstance().player) {
            PlayerEntityModel<AbstractClientPlayerEntity> model = ((PlayerEntityRenderer) (Object) this).getModel();
            model.head.visible = false;
            model.hat.visible = false;
        }
    }
}
