package com.leon.saintsdragons.fabric.mixin;

import com.leon.saintsdragons.util.compat.RegistryContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Port 1.21.11: las Properties de un bloque del mod creadas mientras se registra ya traen su id. */
@Mixin(BlockBehaviour.Properties.class)
public abstract class BlockPropertiesMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void saintsdragons$id(CallbackInfo ci) {
        ResourceKey<Block> k = RegistryContext.block();
        if (k != null) {
            ((BlockBehaviour.Properties) (Object) this).setId(k);
        }
    }
}
