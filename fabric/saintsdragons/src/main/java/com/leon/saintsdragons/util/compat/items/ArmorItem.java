package com.leon.saintsdragons.util.compat.items;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorType;

/** Port 1.21.11: el ArmorItem de 1.20.1 (ahora es un Item con las propiedades de armadura). */
public class ArmorItem extends Item {
    public enum Type {
        HELMET(ArmorType.HELMET, EquipmentSlot.HEAD),
        CHESTPLATE(ArmorType.CHESTPLATE, EquipmentSlot.CHEST),
        LEGGINGS(ArmorType.LEGGINGS, EquipmentSlot.LEGS),
        BOOTS(ArmorType.BOOTS, EquipmentSlot.FEET);

        private final ArmorType vanilla;
        private final EquipmentSlot slot;

        Type(ArmorType vanilla, EquipmentSlot slot) {
            this.vanilla = vanilla;
            this.slot = slot;
        }

        public ArmorType toVanilla() {
            return this.vanilla;
        }

        public EquipmentSlot getSlot() {
            return this.slot;
        }

        public String getName() {
            return this.vanilla.getName();
        }
    }

    protected final LegacyArmorMaterial material;
    protected final Type type;

    public ArmorItem(LegacyArmorMaterial material, Type type, Item.Properties properties) {
        super(properties.humanoidArmor(material.toVanilla(), type.toVanilla()));
        this.material = material;
        this.type = type;
    }

    public Type getType() {
        return this.type;
    }

    public EquipmentSlot getEquipmentSlot() {
        return this.type.getSlot();
    }

    public LegacyArmorMaterial getMaterial() {
        return this.material;
    }

    public int getDefense() {
        return this.material.getDefenseForType(this.type);
    }

    public float getToughness() {
        return this.material.getToughness();
    }
}
