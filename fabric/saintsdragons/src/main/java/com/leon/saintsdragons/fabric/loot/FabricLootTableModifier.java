package com.leon.saintsdragons.fabric.loot;

import com.leon.saintsdragons.server.loot.DragonChestLootRegistry;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.world.item.ItemStack;

/**
 * Port 1.21.11: los objetos extra de los cofres se agregan al sacar el botin (las tablas de botin se cargan
 * antes que los datos del mod, asi que no se pueden cambiar al cargarlas).
 */
public class FabricLootTableModifier {
    public static void register() {
        LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
            var key = table.unwrapKey().orElse(null);
            if (key == null) {
                return;
            }
            for (DragonChestLootRegistry.Entry entry : DragonChestLootRegistry.entriesFor(key.identifier())) {
                double chance = entry.resolvedChance();
                if (chance > 0.0D && context.getRandom().nextFloat() < chance) {
                    drops.add(new ItemStack(entry.item(), entry.count()));
                }
            }
        });
    }
}
