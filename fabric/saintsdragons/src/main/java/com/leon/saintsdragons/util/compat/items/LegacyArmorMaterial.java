package com.leon.saintsdragons.util.compat.items;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.EnumMap;
import java.util.Map;

/** Port 1.21.11: el ArmorMaterial de 1.20.1 (ahora es un record con otros datos). */
public interface LegacyArmorMaterial {
    int getDurabilityForType(ArmorItem.Type type);

    int getDefenseForType(ArmorItem.Type type);

    int getEnchantmentValue();

    SoundEvent getEquipSound();

    Ingredient getRepairIngredient();

    String getName();

    float getToughness();

    float getKnockbackResistance();

    default ArmorMaterial toVanilla() {
        Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
        for (ArmorItem.Type t : ArmorItem.Type.values()) {
            defense.put(t.toVanilla(), getDefenseForType(t));
        }
        defense.put(ArmorType.BODY, getDefenseForType(ArmorItem.Type.CHESTPLATE));
        String name = getName().contains(":") ? getName().substring(getName().indexOf(':') + 1) : getName();
        TagKey<Item> repair = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("saintsdragons", "repair/" + name));
        ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath("saintsdragons", name));
        // la durabilidad de 1.20.1 era base de la ranura * multiplicador; aca va el multiplicador (casco = 11 * m)
        int mult = Math.max(1, getDurabilityForType(ArmorItem.Type.HELMET) / 11);
        return new ArmorMaterial(mult, defense, getEnchantmentValue(), BuiltInRegistries.SOUND_EVENT.wrapAsHolder(getEquipSound()),
                getToughness(), getKnockbackResistance(), repair, asset);
    }
}
