package com.annis.figuraemf.mixin;

import com.annis.figuraemf.EmfBridge;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prioridad baja = se ejecuta antes que el mixin de Figura, que engancha en el mismo punto
 * (justo despues de setAngles) y copia ahi la pose de las partes.
 */
@Mixin(value = LivingEntityRenderer.class, priority = 500)
public abstract class LivingEntityRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/model/EntityModel;setAngles(Lnet/minecraft/entity/Entity;FFFFF)V",
                    shift = At.Shift.AFTER))
    private void figuraemf$animateEarly(CallbackInfo ci) {
        EmfBridge.animateEarly(((LivingEntityRenderer<?, ?>) (Object) this).getModel());
    }
}
