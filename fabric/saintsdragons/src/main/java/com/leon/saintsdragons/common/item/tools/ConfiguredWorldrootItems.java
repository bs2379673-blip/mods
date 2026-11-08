package com.leon.saintsdragons.common.item.tools;

import com.google.common.collect.Multimap;
import com.leon.saintsdragons.common.config.ToolsArmorConfig;
import com.leon.saintsdragons.common.item.ConfiguredItemAttributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import com.leon.saintsdragons.util.compat.items.AxeItem;
import com.leon.saintsdragons.util.compat.items.HoeItem;
import net.minecraft.world.item.Item;
import com.leon.saintsdragons.util.compat.items.PickaxeItem;
import com.leon.saintsdragons.util.compat.items.ShovelItem;
import com.leon.saintsdragons.util.compat.items.SwordItem;

public final class ConfiguredWorldrootItems {
    public static final class Sword extends SwordItem {
        public Sword(Item.Properties properties) {
            super(WorldrootTier.INSTANCE, 3, -2.4F, properties);
        }
    }

    public static final class Pickaxe extends PickaxeItem {
        public Pickaxe(Item.Properties properties) {
            super(WorldrootTier.INSTANCE, 1, -2.8F, properties);
        }
    }

    public static final class Axe extends AxeItem {
        public Axe(Item.Properties properties) {
            super(WorldrootTier.INSTANCE, 5.0F, -3.0F, properties);
        }
    }

    public static final class Shovel extends ShovelItem {
        public Shovel(Item.Properties properties) {
            super(WorldrootTier.INSTANCE, 1.5F, -3.0F, properties);
        }
    }

    public static final class Hoe extends HoeItem {
        public Hoe(Item.Properties properties) {
            super(WorldrootTier.INSTANCE, -4, 0.0F, properties);
        }
    }

    private ConfiguredWorldrootItems() {
    }
}
