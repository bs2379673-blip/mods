package com.leon.saintsdragons.client.renderer.layer.volitans;

import com.leon.saintsdragons.client.renderer.layer.NightEmissiveLayer;
import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.server.entity.dragons.volitans.Volitans;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.renderer.base.GeoRenderer;

public class VolitansNightEmissiveLayer extends NightEmissiveLayer<Volitans> {
    private static final Identifier EMISSIVE_TEXTURE =
            SaintsDragonsCommon.rl("textures/entity/volitans/volitans_emissive.png");
    private static final Identifier BLOODSHOT_EMISSIVE_TEXTURE =
            SaintsDragonsCommon.rl("textures/entity/volitans/volitans_bloodshot_emissive.png");

    public VolitansNightEmissiveLayer(GeoRenderer<Volitans> renderer) {
        super(renderer);
    }

    @Override
    protected Identifier getEmissiveTexture(Volitans animatable) {
        if (animatable.getTextureVariant() == Volitans.VARIANT_BLOODSHOT) {
            return BLOODSHOT_EMISSIVE_TEXTURE;
        }
        return EMISSIVE_TEXTURE;
    }
}
