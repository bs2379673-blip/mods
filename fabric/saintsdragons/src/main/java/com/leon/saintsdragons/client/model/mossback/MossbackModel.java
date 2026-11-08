package com.leon.saintsdragons.client.model.mossback;

import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.server.entity.dragons.Mossback;
import net.minecraft.resources.Identifier;

public class MossbackModel extends com.leon.saintsdragons.client.compat.geo.LegacyGeoModel<Mossback> {
    private static final Identifier ADULT_MODEL = SaintsDragonsCommon.rl("geo/entity/mossback/mossback.geo.json");
    private static final Identifier BABY_MODEL = SaintsDragonsCommon.rl("geo/entity/mossback/baby_mossback.geo.json");
    private static final Identifier ADULT_TEXTURE = SaintsDragonsCommon.rl("textures/entity/mossback/mossback.png");
    private static final Identifier BABY_TEXTURE = SaintsDragonsCommon.rl("textures/entity/mossback/baby_mossback.png");
    private static final Identifier ADULT_ANIMATION = SaintsDragonsCommon.rl("animations/entity/mossback/mossback.animation.json");
    private static final Identifier BABY_ANIMATION = SaintsDragonsCommon.rl("animations/entity/mossback/baby_mossback.animation.json");

    public MossbackModel() {
        super(SaintsDragonsCommon.rl("mossback"));
    }

    @Override
    public Identifier getLegacyModelResource(Mossback entity) {
        return entity != null && entity.isBaby() ? BABY_MODEL : ADULT_MODEL;
    }

    @Override
    public Identifier getLegacyTextureResource(Mossback entity) {
        return entity != null && entity.isBaby() ? BABY_TEXTURE : ADULT_TEXTURE;
    }

    @Override
    public Identifier getLegacyAnimationResource(Mossback entity) {
        return entity != null && entity.isBaby() ? BABY_ANIMATION : ADULT_ANIMATION;
    }
}
