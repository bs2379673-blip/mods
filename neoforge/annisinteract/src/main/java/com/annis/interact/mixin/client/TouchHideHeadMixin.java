package com.annis.interact.mixin.client;

import com.annis.interact.client.TouchCam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mimos en primera persona: se dibuja el propio cuerpo para ver las manos, pero sin la cabeza. */
@Mixin(PlayerRenderer.class)
public abstract class TouchHideHeadMixin {
    @Inject(method = "setModelProperties", at = @At("TAIL"))
    private void annisinteract$hideHead(AbstractClientPlayer player, CallbackInfo ci) {
        if (TouchCam.active && TouchCam.firstPerson && player == Minecraft.getInstance().player) {
            PlayerModel<AbstractClientPlayer> model = ((PlayerRenderer) (Object) this).getModel();
            model.head.visible = false;
            model.hat.visible = false;
        }
    }
}
