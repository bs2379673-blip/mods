package com.leon.saintsdragons.client.model.ignivorus;

import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.server.entity.effect.ignivorus.IgnivorusMagmaPillarEntity;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class IgnivorusMagmaPillarModel extends GeoModel<IgnivorusMagmaPillarEntity> {
    private static final Identifier MODEL = SaintsDragonsCommon.rl("geo/objects/ignivorus/ignivorus_magma_pillar.geo.json");
    private static final Identifier TEXTURE = SaintsDragonsCommon.rl("textures/objects/ignivorus/ignivorus_magma_pillar.png");
    private static final Identifier ANIMATION = SaintsDragonsCommon.rl("animations/objects/ignivorus/ignivorus_magma_pillar.animation.json");

    @Override
    public Identifier getModelResource(IgnivorusMagmaPillarEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(IgnivorusMagmaPillarEntity animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(IgnivorusMagmaPillarEntity animatable) {
        return ANIMATION;
    }
}
