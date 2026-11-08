package com.leon.saintsdragons.client.renderer.layer.atroxiia;

import com.leon.saintsdragons.client.renderer.layer.NightEmissiveLayer;
import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.server.entity.dragons.atroxiia.Atroxiia;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.renderer.base.GeoRenderer;

public class AtroxiiaNightEmissiveLayer extends NightEmissiveLayer<Atroxiia> {
    private static final Identifier EMISSIVE_TEXTURE =
            SaintsDragonsCommon.rl("textures/entity/atroxiia/atroxiia_emissive.png");

    public AtroxiiaNightEmissiveLayer(GeoRenderer<Atroxiia> renderer) {
        super(renderer);
    }

    @Override
    protected Identifier getEmissiveTexture(Atroxiia animatable) {
        return EMISSIVE_TEXTURE;
    }
}
