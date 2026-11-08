package com.leon.saintsdragons.util.compat.items;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/** Port 1.21.11: el "Tier" de 1.20.1 (ahora el juego usa ToolMaterial). */
public interface Tier {
    int getUses();

    float getSpeed();

    float getAttackDamageBonus();

    int getLevel();

    int getEnchantmentValue();

    Ingredient getRepairIngredient();

    default TagKey<Item> repairTag() {
        String n = this instanceof Enum<?> e ? e.name().toLowerCase(java.util.Locale.ROOT) : "tier";
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("saintsdragons", "repair/" + n));
    }

    default ToolMaterial toVanilla() {
        TagKey<Block> incorrect = switch (getLevel()) {
            case 0 -> BlockTags.INCORRECT_FOR_WOODEN_TOOL;
            case 1 -> BlockTags.INCORRECT_FOR_STONE_TOOL;
            case 2 -> BlockTags.INCORRECT_FOR_IRON_TOOL;
            case 3 -> BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
            default -> BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
        };
        return new ToolMaterial(incorrect, getUses(), getSpeed(), getAttackDamageBonus(), getEnchantmentValue(), repairTag());
    }
}
