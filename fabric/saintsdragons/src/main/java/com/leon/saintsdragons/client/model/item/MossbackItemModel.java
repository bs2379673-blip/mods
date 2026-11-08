package com.leon.saintsdragons.client.model.item;

import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.common.item.MossbackItem;
import net.minecraft.resources.Identifier;

public class MossbackItemModel extends com.leon.saintsdragons.client.compat.geo.LegacyGeoModel<MossbackItem> {
    private final boolean baby;

    public MossbackItemModel() {
        this(false);
    }

    public MossbackItemModel(boolean baby) {
        this.baby = baby;
    }

    @Override
    public Identifier getLegacyModelResource(MossbackItem animatable) {
        return SaintsDragonsCommon.rl(baby
                ? "geo/entity/mossback/baby_mossback.geo.json"
                : "geo/entity/mossback/mossback.geo.json");
    }

    @Override
    public Identifier getLegacyTextureResource(MossbackItem animatable) {
        return SaintsDragonsCommon.rl(baby
                ? "textures/entity/mossback/baby_mossback.png"
                : "textures/entity/mossback/mossback.png");
    }

    @Override
    public Identifier getLegacyAnimationResource(MossbackItem animatable) {
        return SaintsDragonsCommon.rl(baby
                ? "animations/entity/mossback/baby_mossback.animation.json"
                : "animations/entity/mossback/mossback.animation.json");
    }
}
