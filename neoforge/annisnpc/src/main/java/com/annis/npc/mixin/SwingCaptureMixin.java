package com.annis.npc.mixin;

import com.annis.npc.client.SwingFix;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pose del jugador tal como la deja el juego (antes de Punchy Moves, que va con prioridad 900). */
@Mixin(value = PlayerModel.class, priority = 800)
public abstract class SwingCaptureMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void annisnpc$captureSwing(LivingEntity entity, float limbAngle, float limbDistance, float ageInTicks,
                                       float headYaw, float headPitch, CallbackInfo ci) {
        SwingFix.capture((PlayerModel<?>) (Object) this);
    }
}
