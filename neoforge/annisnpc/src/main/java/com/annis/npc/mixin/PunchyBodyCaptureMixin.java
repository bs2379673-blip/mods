package com.annis.npc.mixin;

import com.annis.npc.client.PunchyBodyOff;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Justo antes de Punchy Moves (que va con prioridad 900). */
@Mixin(value = PlayerModel.class, priority = 899)
public abstract class PunchyBodyCaptureMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void annisnpc$beforePunchy(LivingEntity entity, float a, float b, float c, float d, float e, CallbackInfo ci) {
        PunchyBodyOff.capture((PlayerModel<?>) (Object) this);
    }
}
