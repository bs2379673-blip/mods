package com.leon.saintsdragons.client.model.volitans;

import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.server.entity.effect.volitans.VolitansSpineEntity;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class VolitansSpineModel extends GeoModel<VolitansSpineEntity> {
    private static final Identifier MODEL =
            SaintsDragonsCommon.rl("geo/objects/volitans/volitans_spine.geo.json");
    private static final Identifier TEXTURE =
            SaintsDragonsCommon.rl("textures/objects/volitans/volitans_spine.png");
    private static final Identifier ANIMATION =
            SaintsDragonsCommon.rl("animations/objects/volitans/volitans_spine.animation.json");

    @Override
    public Identifier getModelResource(VolitansSpineEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(VolitansSpineEntity animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(VolitansSpineEntity animatable) {
        return ANIMATION;
    }
}