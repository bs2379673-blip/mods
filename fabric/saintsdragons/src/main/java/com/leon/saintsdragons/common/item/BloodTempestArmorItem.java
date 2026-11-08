package com.leon.saintsdragons.common.item;

import com.google.common.collect.Multimap;
import com.leon.saintsdragons.common.config.ToolsArmorConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.leon.saintsdragons.util.compat.items.ArmorItem;
import com.leon.saintsdragons.util.compat.items.LegacyArmorMaterial;

public class BloodTempestArmorItem extends ArmorItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public BloodTempestArmorItem(LegacyArmorMaterial armorMaterial, Type type, Properties properties) {
        super(armorMaterial, type, properties);
    }

    @Override
    public int getDefense() {
        return (int) Math.round(configuredDefense());
    }

    @Override
    public float getToughness() {
        return (float) ToolsArmorConfig.BLOOD_TEMPEST_TOUGHNESS.get();
    }

    private double configuredDefense() {
        return switch (getType()) {
            case HELMET -> ToolsArmorConfig.BLOOD_TEMPEST_HELMET_ARMOR.get();
            case CHESTPLATE -> ToolsArmorConfig.BLOOD_TEMPEST_CHESTPLATE_ARMOR.get();
            case LEGGINGS -> ToolsArmorConfig.BLOOD_TEMPEST_LEGGINGS_ARMOR.get();
            case BOOTS -> ToolsArmorConfig.BLOOD_TEMPEST_BOOTS_ARMOR.get();
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext saintsdragons$ctx, net.minecraft.world.item.component.TooltipDisplay saintsdragons$display, java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, saintsdragons$ctx, saintsdragons$display, tooltip, flag);
        tooltip.accept(Component.empty());
        tooltip.accept(Component.empty()
                .append(Component.translatable("item.saintsdragons.blood_tempest_armor.tooltip.title")
                        .withStyle(ChatFormatting.DARK_RED))
                .append(Component.literal(" "))
                .append(Component.translatable("item.saintsdragons.blood_tempest_armor.tooltip.full_set")
                        .withStyle(ChatFormatting.GRAY)));
        tooltip.accept(Component.translatable("item.saintsdragons.blood_tempest_armor.tooltip.description")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // port 1.21.11 / GeckoLib 5: el renderer se pide aca (solo en el cliente)
    @Override
    public void createGeoRenderer(Consumer<software.bernie.geckolib.animatable.client.GeoRenderProvider> consumer) {
        consumer.accept(new com.leon.saintsdragons.client.renderer.armor.BloodTempestRenderProvider());
    }
}
