package com.leon.saintsdragons.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import com.leon.saintsdragons.util.compat.items.ArmorItem;
import com.leon.saintsdragons.util.compat.items.LegacyArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;
import java.util.List;

import com.leon.saintsdragons.client.renderer.armor.DraconianArmorTextures;

public class DraconianArmorItem extends ArmorItem {
    public DraconianArmorItem(LegacyArmorMaterial material, Type type, Properties properties) {
        super(material, type, properties);
    }

    // Forge's armor texture hook also feeds render replacements such as Epic Fight.
    @Nullable
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, @Nullable String type) {
        if (type != null) {
            return null;
        }

        return DraconianArmorTextures
                .texture(slot == EquipmentSlot.LEGS)
                .toString();
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext saintsdragons$ctx, net.minecraft.world.item.component.TooltipDisplay saintsdragons$display, java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, saintsdragons$ctx, saintsdragons$display, tooltip, flag);
        tooltip.accept(Component.empty());
        tooltip.accept(Component.translatable("item.saintsdragons.draconian_armor.tooltip.passive.title")
                .withStyle(ChatFormatting.WHITE));
        tooltip.accept(Component.empty()
                .append(Component.translatable("item.saintsdragons.draconian_armor.tooltip.full_set")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(" "))
                .append(Component.translatable("item.saintsdragons.draconian_armor.tooltip.passive.description")
                        .withStyle(ChatFormatting.GRAY)));
    }
}
