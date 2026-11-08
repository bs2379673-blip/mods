package com.leon.saintsdragons.client.renderer.armor;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/** Port 1.21.11: quien le da a GeckoLib el renderer de la armadura. */
public final class DragonlordArmorRenderProvider implements GeoRenderProvider {
    private DragonlordArmorRenderer<?> renderer;

    @Override
    public GeoArmorRenderer<?, ?> getGeoArmorRenderer(ItemStack stack, EquipmentSlot slot) {
        if (this.renderer == null) {
            this.renderer = new DragonlordArmorRenderer<>();
        }
        return this.renderer;
    }
}
