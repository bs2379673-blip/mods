package com.leon.saintsdragons.util.compat.items;

import net.minecraft.world.item.Item;

/** Port 1.21.11: el AxeItem de 1.20.1 (ahora es un Item con las propiedades de herramienta). */
public class AxeItem extends Item {
    private final Tier tier;

    public AxeItem(Tier tier, float attackDamage, float attackSpeed, Item.Properties properties) {
        super(properties.axe(tier.toVanilla(), attackDamage, attackSpeed));
        this.tier = tier;
    }

    public Tier getTier() {
        return this.tier;
    }
}
