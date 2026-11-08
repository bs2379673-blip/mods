package com.leon.saintsdragons.client.renderer.armor;

import com.leon.saintsdragons.client.model.armor.BloodTempestArmorModel;
import com.leon.saintsdragons.client.renderer.vfx.BloodTempestAfterimageRenderContext;
import com.leon.saintsdragons.common.item.BloodTempestArmorItem;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** Port 1.21.11 / GeckoLib 5. */
public class BloodTempestArmorRenderer<R extends HumanoidRenderState & GeoRenderState> extends GeoArmorRenderer<BloodTempestArmorItem, R> {
    public BloodTempestArmorRenderer() {
        super(new BloodTempestArmorModel());
    }

    @Override
    public String getBoneNameForSegment(R renderState, ArmorSegment segment) {
        // el casco de este modelo se llama "armorhead"
        return segment == ArmorSegment.HEAD ? "armorhead" : super.getBoneNameForSegment(renderState, segment);
    }

    @Override
    public RenderType getRenderType(R renderState, Identifier texture) {
        if (BloodTempestAfterimageRenderContext.isActive()) {
            return net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(texture);
        }
        return super.getRenderType(renderState, texture);
    }

    @Override
    public int getRenderColor(BloodTempestArmorItem animatable, RenderData data, float partialTick) {
        if (BloodTempestAfterimageRenderContext.isActive()) {
            return net.minecraft.util.ARGB.colorFromFloat(BloodTempestAfterimageRenderContext.alpha(), BloodTempestAfterimageRenderContext.red(),
                    BloodTempestAfterimageRenderContext.green(), BloodTempestAfterimageRenderContext.blue());
        }
        return super.getRenderColor(animatable, data, partialTick);
    }
}
