package com.annis.npc.mixin.mca;

import com.annis.npc.mca.McaIntegration;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.resources.Dialogues;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Dialogues.class, remap = false)
public abstract class DialoguesMixin {
    @Inject(method = "selectAnswer", at = @At("HEAD"))
    private void annisnpc$onAnswer(VillagerEntityMCA villager, ServerPlayerEntity player, String questionId, String answerId, CallbackInfo ci) {
        McaIntegration.onDialogue(villager, questionId);
    }
}
