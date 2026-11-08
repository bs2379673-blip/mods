package com.leon.saintsdragons.client.model.cindervane;

import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.client.renderer.vfx.BeamRenderTypes;
import com.leon.saintsdragons.server.entity.effect.cindervane.CindervaneFireballEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.rendertype.RenderType;

public class CindervaneFireballModel extends com.leon.saintsdragons.client.compat.geo.LegacyGeoModel<CindervaneFireballEntity> {
    private static final Identifier MODEL = SaintsDragonsCommon.rl("geo/objects/shared/fireball/fireball_stage_1.geo.json");
    private static final Identifier TEXTURE = SaintsDragonsCommon.rl("textures/objects/shared/fireball/fireball_stage_1.png");
    private static final Identifier ANIMATION = SaintsDragonsCommon.rl("animations/objects/shared/fireball/fireball_stage_1.animation.json");

    @Override
    public Identifier getLegacyModelResource(CindervaneFireballEntity entity) { return MODEL; }

    @Override
    public Identifier getLegacyTextureResource(CindervaneFireballEntity entity) { return TEXTURE; }

    @Override
    public Identifier getLegacyAnimationResource(CindervaneFireballEntity entity) { return ANIMATION; }

    @Override
    public RenderType getRenderType(CindervaneFireballEntity entity, Identifier texture) {
        return BeamRenderTypes.translucent(texture);
    }
}
