package com.annis.interact.mixin.client;

import com.annis.interact.client.ClientState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Durante una interaccion el cuerpo mira hacia donde lo puso el servidor, aunque la cabeza (la
 * camara) se mueva: asi los brazos de los dos coinciden.
 */
@Mixin(value = LivingEntityRenderer.class, priority = 900)
public abstract class AnchorRenderMixin {
    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void annisinteract$anchor(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                                      VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        com.annis.interact.client.CarryRender.apply(entity, tickDelta, matrices);
        Float anchored = ClientState.ANCHORS.get(entity.getId());
        if (anchored != null) {
            entity.bodyYaw = anchored;
            entity.prevBodyYaw = anchored;
        }
    }
}
