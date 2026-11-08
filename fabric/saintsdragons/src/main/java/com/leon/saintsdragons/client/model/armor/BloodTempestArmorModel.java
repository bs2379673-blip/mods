package com.leon.saintsdragons.client.model.armor;

import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.common.item.BloodTempestArmorItem;
import net.minecraft.resources.Identifier;

public class BloodTempestArmorModel extends com.leon.saintsdragons.client.compat.geo.LegacyGeoModel<BloodTempestArmorItem> {
    private static final Identifier MODEL =
            SaintsDragonsCommon.rl("geo/armor/blood_tempest_armor.geo.json");
    private static final Identifier TEXTURE =
            SaintsDragonsCommon.rl("textures/armor/blood_tempest_armor.png");
    private static final Identifier ANIMATION =
            SaintsDragonsCommon.rl("animations/armor/blood_tempest_armor.animation.json");

    @Override
    public Identifier getLegacyModelResource(BloodTempestArmorItem animatable) {
        return MODEL;
    }

    @Override
    public Identifier getLegacyTextureResource(BloodTempestArmorItem animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getLegacyAnimationResource(BloodTempestArmorItem animatable) {
        return ANIMATION;
    }
}
