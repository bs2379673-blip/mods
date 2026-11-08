package com.leon.saintsdragons.client.model.volitans;

import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.server.entity.effect.volitans.VolitansGroundChunkEntity;
import net.minecraft.resources.Identifier;

public class VolitansGroundChunkModel extends com.leon.saintsdragons.client.compat.geo.LegacyGeoModel<VolitansGroundChunkEntity> {
    private static final Identifier MODEL = SaintsDragonsCommon.rl("geo/objects/volitans/ground_chunk.geo.json");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/block/dirt.png");
    private static final Identifier ANIMATION = SaintsDragonsCommon.rl("animations/objects/volitans/ground_chunk.animation.json");

    @Override
    public Identifier getLegacyModelResource(VolitansGroundChunkEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getLegacyTextureResource(VolitansGroundChunkEntity animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getLegacyAnimationResource(VolitansGroundChunkEntity animatable) {
        return ANIMATION;
    }
}
