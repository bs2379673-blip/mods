package com.leon.saintsdragons.client.model.volitans;

import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.server.entity.effect.volitans.VolitansBurrowMoundEntity;
import net.minecraft.resources.Identifier;

public class VolitansBurrowMoundModel extends com.leon.saintsdragons.client.compat.geo.LegacyGeoModel<VolitansBurrowMoundEntity> {
    private static final Identifier MODEL = SaintsDragonsCommon.rl("geo/objects/volitans/burrow_mound.geo.json");
    private static final Identifier TEXTURE = SaintsDragonsCommon.rl("textures/objects/volitans/burrow_mound.png");
    private static final Identifier ANIMATION = SaintsDragonsCommon.rl("animations/objects/volitans/burrow_mound.animation.json");

    @Override
    public Identifier getLegacyModelResource(VolitansBurrowMoundEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getLegacyTextureResource(VolitansBurrowMoundEntity animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getLegacyAnimationResource(VolitansBurrowMoundEntity animatable) {
        return ANIMATION;
    }
}
