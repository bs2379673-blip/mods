package com.leon.saintsdragons.common.init;

import com.leon.saintsdragons.common.registry.ModItems;
import com.leon.saintsdragons.common.registry.ModPotions;
import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.alchemy.Potions;

/** Port 1.21.11: las mezclas se agregan cuando el juego arma las del soporte para pociones. */
public final class CommonBrewingRecipes {
    private static boolean registered;

    private CommonBrewingRecipes() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        FabricBrewingRecipeRegistryBuilder.BUILD.register(builder -> {
            builder.addMix(Potions.AWKWARD, ModItems.VARASUCHUS_SCALE.get(), BuiltInRegistries.POTION.wrapAsHolder(ModPotions.TIDEGUARD.get()));
            builder.addMix(Potions.AWKWARD, ModItems.IGNIVORUS_TOOTH.get(), BuiltInRegistries.POTION.wrapAsHolder(ModPotions.SEARING.get()));
        });
    }
}
