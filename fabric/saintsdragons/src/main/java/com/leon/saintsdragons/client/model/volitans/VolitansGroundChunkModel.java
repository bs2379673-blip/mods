package com.leon.saintsdragons.client.model.volitans;

import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.server.entity.effect.volitans.VolitansGroundChunkEntity;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class VolitansGroundChunkModel extends GeoModel<VolitansGroundChunkEntity> {
    private static final Identifier MODEL = SaintsDragonsCommon.rl("geo/objects/volitans/ground_chunk.geo.json");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/block/dirt.png");
    private static final Identifier ANIMATION = SaintsDragonsCommon.rl("animations/objects/volitans/ground_chunk.animation.json");

    @Override
    public Identifier getModelResource(VolitansGroundChunkEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(VolitansGroundChunkEntity animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(VolitansGroundChunkEntity animatable) {
        return ANIMATION;
    }
}
