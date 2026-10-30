package com.annis.npc.mixin.mca;

import com.annis.npc.mca.McaClientHooks;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntityModelMCA.class)
public abstract class VillagerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void annisnpc$afterAngles(LivingEntity villager, float limbAngle, float limbDistance, float animationProgress,
                                       float headYaw, float headPitch, CallbackInfo ci) {
        McaClientHooks.afterSetAngles((VillagerEntityModelMCA<?>) (Object) this, villager, animationProgress);
    }
}
