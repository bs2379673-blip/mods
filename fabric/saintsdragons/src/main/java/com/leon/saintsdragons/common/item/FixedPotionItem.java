package com.leon.saintsdragons.common.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

/**
 * Port 1.21.11: pocion con un efecto fijo. Se bebe como una pocion comun (el efecto lo da el componente de
 * contenido de pocion, que tambien pone el color y la descripcion).
 */
public class FixedPotionItem extends Item {
    private final Supplier<Potion> potion;

    public FixedPotionItem(Item.Properties properties, Supplier<Potion> potion) {
        super(properties.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK).usingConvertsTo(Items.GLASS_BOTTLE));
        this.potion = potion;
    }

    private ItemStack ensurePotion(ItemStack stack) {
        if (!stack.has(DataComponents.POTION_CONTENTS)) {
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(BuiltInRegistries.POTION.wrapAsHolder(this.potion.get())));
        }
        return stack;
    }

    @Override
    public ItemStack getDefaultInstance() {
        return ensurePotion(super.getDefaultInstance());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ensurePotion(player.getItemInHand(hand));
        return super.use(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        ensurePotion(stack);
        return super.finishUsingItem(stack, level, livingEntity);
    }
}
