package com.annis.npc.mixin.mca;

import com.annis.npc.mca.McaIntegration;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.resources.data.dialogue.Actions;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Actions.class, remap = false)
public abstract class ActionsMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void annisnpc$onTrigger(VillagerEntityMCA villager, ServerPlayer player, CallbackInfo ci) {
        Actions self = (Actions) (Object) this;
        McaIntegration.onReaction(villager, self.isPositive(), self.isNegative());
    }
}
