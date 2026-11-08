package com.leon.saintsdragons.client.renderer.armor;

import com.leon.saintsdragons.client.model.armor.DragonlordArmorModel;
import com.leon.saintsdragons.common.item.DragonlordArmorItem;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** Port 1.21.11 / GeckoLib 5 (los huesos ya tienen los nombres de GeckoLib: armorHead, armorBody...). */
public class DragonlordArmorRenderer<R extends HumanoidRenderState & GeoRenderState> extends GeoArmorRenderer<DragonlordArmorItem, R> {
    public DragonlordArmorRenderer() {
        super(new DragonlordArmorModel());
    }
}
