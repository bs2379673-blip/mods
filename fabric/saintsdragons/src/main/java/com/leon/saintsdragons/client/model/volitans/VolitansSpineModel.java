package com.leon.saintsdragons.client.model.volitans;

import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.server.entity.effect.volitans.VolitansSpineEntity;
import net.minecraft.resources.Identifier;

public class VolitansSpineModel extends com.leon.saintsdragons.client.compat.geo.LegacyGeoModel<VolitansSpineEntity> {
    private static final Identifier MODEL =
            SaintsDragonsCommon.rl("geo/objects/volitans/volitans_spine.geo.json");
    private static final Identifier TEXTURE =
            SaintsDragonsCommon.rl("textures/objects/volitans/volitans_spine.png");
    private static final Identifier ANIMATION =
            SaintsDragonsCommon.rl("animations/objects/volitans/volitans_spine.animation.json");

    @Override
    public Identifier getLegacyModelResource(VolitansSpineEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getLegacyTextureResource(VolitansSpineEntity animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getLegacyAnimationResource(VolitansSpineEntity animatable) {
        return ANIMATION;
    }
}