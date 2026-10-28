package com.annis.npc.mixin.mca;

import com.annis.npc.mca.McaIntegration;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.interaction.EntityCommandHandler;
import net.conczin.mca.entity.interaction.VillagerCommandHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VillagerCommandHandler.class, remap = false)
public abstract class VillagerCommandHandlerMixin extends EntityCommandHandler<VillagerEntityMCA> {
    protected VillagerCommandHandlerMixin(VillagerEntityMCA entity) {
        super(entity);
    }

    @Inject(method = "handle", at = @At("HEAD"))
    private void annisnpc$onCommand(ServerPlayerEntity player, String command, CallbackInfoReturnable<Boolean> cir) {
        McaIntegration.onCommand(this.entity, command);
    }
}
