package com.leon.saintsdragons.client.model.volitans;

import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.server.entity.effect.volitans.VolitansBurrowMoundEntity;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class VolitansBurrowMoundModel extends GeoModel<VolitansBurrowMoundEntity> {
    private static final Identifier MODEL = SaintsDragonsCommon.rl("geo/objects/volitans/burrow_mound.geo.json");
    private static final Identifier TEXTURE = SaintsDragonsCommon.rl("textures/objects/volitans/burrow_mound.png");
    private static final Identifier ANIMATION = SaintsDragonsCommon.rl("animations/objects/volitans/burrow_mound.animation.json");

    @Override
    public Identifier getModelResource(VolitansBurrowMoundEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(VolitansBurrowMoundEntity animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(VolitansBurrowMoundEntity animatable) {
        return ANIMATION;
    }
}
