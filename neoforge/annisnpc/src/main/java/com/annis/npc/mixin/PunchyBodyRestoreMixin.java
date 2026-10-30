package com.annis.npc.mixin;

import com.annis.npc.client.PunchyBodyOff;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Justo despues de Punchy Moves: vuelve la pose de antes (Punchy solo pone la cara). */
@Mixin(value = PlayerModel.class, priority = 901)
public abstract class PunchyBodyRestoreMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void annisnpc$afterPunchy(LivingEntity entity, float a, float b, float c, float d, float e, CallbackInfo ci) {
        PunchyBodyOff.restore((PlayerModel<?>) (Object) this);
    }
}
