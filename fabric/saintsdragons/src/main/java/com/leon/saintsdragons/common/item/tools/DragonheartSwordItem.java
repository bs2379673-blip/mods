package com.leon.saintsdragons.common.item.tools;

import com.google.common.collect.Multimap;
import com.leon.saintsdragons.common.config.ToolsArmorConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import com.leon.saintsdragons.util.compat.items.SwordItem;
import com.leon.saintsdragons.util.compat.items.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.UUID;

public class DragonheartSwordItem extends SwordItem {
    private static final int DRAGONLORD_FIRE_ASPECT_SECONDS = 8;
    public static final UUID ENTITY_REACH_MODIFIER_UUID = UUID.fromString("513fc6ee-03f7-4aa7-8f3b-6f8f7fd57d60");
    public static final UUID TARGETING_REACH_MODIFIER_UUID = UUID.fromString("f614ef20-d69e-4c41-8363-c7e9c9b106ec");
    public static final double VANILLA_ENTITY_REACH = 3.0D;
    public static final double VANILLA_BLOCK_REACH = 4.5D;

    private final double entityReach;
    private final float criticalDamageBonus;
    private final Tier tier;

    public DragonheartSwordItem(Tier tier,
                                int attackDamageModifier,
                                float attackSpeedModifier,
                                double entityReach,
                                float criticalDamageBonus,
                                Properties properties) {
        super(tier, attackDamageModifier, attackSpeedModifier, properties);
        this.entityReach = entityReach;
        this.criticalDamageBonus = criticalDamageBonus;
        this.tier = tier;
    }

    public double getEntityReach() {
        if (ToolsArmorConfig.BLOOD_TEMPEST_KATANA_REACH == null || ToolsArmorConfig.DRAGONLORD_SWORD_REACH == null) {
            return this.entityReach;
        }
        return isBloodTempest() ? ToolsArmorConfig.BLOOD_TEMPEST_KATANA_REACH.get() : ToolsArmorConfig.DRAGONLORD_SWORD_REACH.get();
    }

    public double getEntityReachBonus() {
        return getEntityReach() - VANILLA_ENTITY_REACH;
    }

    public double getTargetingReachBonus() {
        return getEntityReach() - VANILLA_BLOCK_REACH;
    }

    public float getCriticalDamageBonus() {
        if (ToolsArmorConfig.BLOOD_TEMPEST_KATANA_CRITICAL_BONUS == null
                || ToolsArmorConfig.DRAGONLORD_SWORD_CRITICAL_BONUS == null) {
            return this.criticalDamageBonus;
        }
        return (float) (isBloodTempest()
                ? ToolsArmorConfig.BLOOD_TEMPEST_KATANA_CRITICAL_BONUS.get()
                : ToolsArmorConfig.DRAGONLORD_SWORD_CRITICAL_BONUS.get());
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.hurtEnemy(stack, target, attacker);
        if (!isBloodTempest()) {
            target.igniteForSeconds(DRAGONLORD_FIRE_ASPECT_SECONDS);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext saintsdragons$ctx, net.minecraft.world.item.component.TooltipDisplay saintsdragons$display, java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, saintsdragons$ctx, saintsdragons$display, tooltip, flag);
        if (!isBloodTempest()) {
            tooltip.accept(Component.empty());
            tooltip.accept(Component.translatable("item.saintsdragons.dragonlord_sword.tooltip.ability.title")
                    .withStyle(ChatFormatting.GOLD));
            tooltip.accept(Component.translatable("item.saintsdragons.dragonlord_sword.tooltip.ability.description")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.accept(Component.empty());
            tooltip.accept(Component.translatable("item.saintsdragons.dragonlord_sword.tooltip.description")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
            return;
        }

        tooltip.accept(Component.empty());
        tooltip.accept(Component.translatable("item.saintsdragons.blood_tempest_katana.tooltip.passive.title")
                .withStyle(ChatFormatting.DARK_RED));
        tooltip.accept(Component.translatable("item.saintsdragons.blood_tempest_katana.tooltip.passive.description")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.empty());
        tooltip.accept(Component.translatable("item.saintsdragons.blood_tempest_katana.tooltip.ability.title")
                .withStyle(ChatFormatting.DARK_RED));
        tooltip.accept(Component.translatable("item.saintsdragons.blood_tempest_katana.tooltip.ability.description")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.empty());
        tooltip.accept(Component.translatable("item.saintsdragons.blood_tempest_katana.tooltip.quote")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
    }

    private boolean isBloodTempest() {
        return this.tier == DragonheartWeaponTier.CHUNK;
    }

    private double bloodTempestDamage() {
        return ToolsArmorConfig.BLOOD_TEMPEST_KATANA_DAMAGE == null
                ? 1.0D + this.tier.getAttackDamageBonus() + 3.0D
                : ToolsArmorConfig.BLOOD_TEMPEST_KATANA_DAMAGE.get();
    }

    private double bloodTempestSpeed() {
        return ToolsArmorConfig.BLOOD_TEMPEST_KATANA_SPEED == null
                ? 3.0D
                : ToolsArmorConfig.BLOOD_TEMPEST_KATANA_SPEED.get();
    }

    private double dragonlordDamage() {
        return ToolsArmorConfig.DRAGONLORD_SWORD_DAMAGE == null
                ? 1.0D + this.tier.getAttackDamageBonus() + 5.0D
                : ToolsArmorConfig.DRAGONLORD_SWORD_DAMAGE.get();
    }

    private double dragonlordSpeed() {
        return ToolsArmorConfig.DRAGONLORD_SWORD_SPEED == null
                ? 1.4D
                : ToolsArmorConfig.DRAGONLORD_SWORD_SPEED.get();
    }
}
