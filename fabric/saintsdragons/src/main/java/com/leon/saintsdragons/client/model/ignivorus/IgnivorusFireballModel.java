package com.leon.saintsdragons.client.model.ignivorus;

import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.client.renderer.vfx.BeamRenderTypes;
import com.leon.saintsdragons.server.entity.effect.ignivorus.IgnivorusFireballEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.rendertype.RenderType;
import software.bernie.geckolib.model.GeoModel;

public class IgnivorusFireballModel extends GeoModel<IgnivorusFireballEntity> {
    private static final Identifier MODEL = SaintsDragonsCommon.rl("geo/objects/shared/fireball/fireball_stage_1.geo.json");
    private static final Identifier TEXTURE = SaintsDragonsCommon.rl("textures/objects/shared/fireball/fireball_stage_1.png");
    private static final Identifier ANIMATION = SaintsDragonsCommon.rl("animations/objects/shared/fireball/fireball_stage_1.animation.json");

    private static final Identifier STAGE_TWO_MODEL = SaintsDragonsCommon.rl("geo/objects/shared/fireball/fireball_stage_2.geo.json");
    private static final Identifier STAGE_TWO_TEXTURE = SaintsDragonsCommon.rl("textures/objects/shared/fireball/fireball_stage_2.png");
    private static final Identifier STAGE_TWO_ANIMATION = SaintsDragonsCommon.rl("animations/objects/shared/fireball/fireball_stage_2.animation.json");

    private static final Identifier STAGE_THREE_MODEL = SaintsDragonsCommon.rl("geo/objects/ignivorus/ignivorus_fireball_stage_3.geo.json");
    private static final Identifier STAGE_THREE_TEXTURE = SaintsDragonsCommon.rl("textures/objects/ignivorus/ignivorus_fireball_stage_3.png");
    private static final Identifier STAGE_THREE_ANIMATION = SaintsDragonsCommon.rl("animations/objects/ignivorus/ignivorus_fireball_stage_3.animation.json");

    @Override
    public Identifier getModelResource(IgnivorusFireballEntity entity) {
        if (entity.getVisualScale() >= 8.0F) return STAGE_THREE_MODEL;
        return entity.getVisualScale() >= 6.0F ? STAGE_TWO_MODEL : MODEL;
    }

    @Override
    public Identifier getTextureResource(IgnivorusFireballEntity entity) {
        if (entity.getVisualScale() >= 8.0F) return STAGE_THREE_TEXTURE;
        return entity.getVisualScale() >= 6.0F ? STAGE_TWO_TEXTURE : TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(IgnivorusFireballEntity entity) {
        if (entity.getVisualScale() >= 8.0F) return STAGE_THREE_ANIMATION;
        return entity.getVisualScale() >= 6.0F ? STAGE_TWO_ANIMATION : ANIMATION;
    }

    @Override
    public RenderType getRenderType(IgnivorusFireballEntity entity, Identifier texture) {
        return BeamRenderTypes.translucent(texture);
    }
}
