package com.leon.saintsdragons.client.model.otheranimals;

import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.server.entity.otheranimals.Moop;

public class MoopModel extends com.leon.saintsdragons.client.compat.geo.LegacyGeoModel<Moop> {
    public MoopModel() {
        super(SaintsDragonsCommon.rl("moop/moop"));
    }
}
