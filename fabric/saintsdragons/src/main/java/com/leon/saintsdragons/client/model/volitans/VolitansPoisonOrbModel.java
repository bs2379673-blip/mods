package com.leon.saintsdragons.client.model.volitans;

import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.server.entity.effect.volitans.VolitansPoisonOrbEntity;
import net.minecraft.resources.Identifier;

public final class VolitansPoisonOrbModel extends com.leon.saintsdragons.client.compat.geo.LegacyGeoModel<VolitansPoisonOrbEntity> {
    @Override
    public Identifier getLegacyModelResource(VolitansPoisonOrbEntity entity) {
        return SaintsDragonsCommon.rl("geo/objects/volitans/volitans_poison_orb.geo.json");
    }
    @Override
    public Identifier getLegacyTextureResource(VolitansPoisonOrbEntity entity) {
        return SaintsDragonsCommon.rl("textures/objects/volitans/volitans_poison_orb.png");
    }
    @Override
    public Identifier getLegacyAnimationResource(VolitansPoisonOrbEntity entity) {
        return SaintsDragonsCommon.rl("animations/objects/volitans/volitans_poison_orb.animation.json");
    }
}
