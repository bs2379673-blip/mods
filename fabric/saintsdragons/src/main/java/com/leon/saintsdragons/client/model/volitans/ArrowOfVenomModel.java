package com.leon.saintsdragons.client.model.volitans;

import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.server.entity.effect.volitans.ArrowOfVenomEntity;
import net.minecraft.resources.Identifier;

public class ArrowOfVenomModel extends com.leon.saintsdragons.client.compat.geo.LegacyGeoModel<ArrowOfVenomEntity> {
    private static final Identifier MODEL =
            SaintsDragonsCommon.rl("geo/objects/volitans/arrow_of_venom.geo.json");
    private static final Identifier TEXTURE =
            SaintsDragonsCommon.rl("textures/objects/volitans/arrow_of_venom.png");
    private static final Identifier ANIMATION =
            SaintsDragonsCommon.rl("animations/objects/volitans/arrow_of_venom.animation.json");

    @Override
    public Identifier getLegacyModelResource(ArrowOfVenomEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getLegacyTextureResource(ArrowOfVenomEntity animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getLegacyAnimationResource(ArrowOfVenomEntity animatable) {
        return ANIMATION;
    }
}
