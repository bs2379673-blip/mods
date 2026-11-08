package com.leon.saintsdragons.client.model.volitans;

import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.server.entity.effect.volitans.ArrowOfVenomEntity;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class ArrowOfVenomModel extends GeoModel<ArrowOfVenomEntity> {
    private static final Identifier MODEL =
            SaintsDragonsCommon.rl("geo/objects/volitans/arrow_of_venom.geo.json");
    private static final Identifier TEXTURE =
            SaintsDragonsCommon.rl("textures/objects/volitans/arrow_of_venom.png");
    private static final Identifier ANIMATION =
            SaintsDragonsCommon.rl("animations/objects/volitans/arrow_of_venom.animation.json");

    @Override
    public Identifier getModelResource(ArrowOfVenomEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(ArrowOfVenomEntity animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(ArrowOfVenomEntity animatable) {
        return ANIMATION;
    }
}
