package com.leon.saintsdragons.fabric.mixin;

import com.leon.saintsdragons.util.compat.RegistryContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Port 1.21.11: las Properties de un item del mod creadas mientras se registra ya traen su id. */
@Mixin(Item.Properties.class)
public abstract class ItemPropertiesMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void saintsdragons$id(CallbackInfo ci) {
        ResourceKey<Item> k = RegistryContext.item();
        if (k != null) {
            ((Item.Properties) (Object) this).setId(k);
        }
    }
}
