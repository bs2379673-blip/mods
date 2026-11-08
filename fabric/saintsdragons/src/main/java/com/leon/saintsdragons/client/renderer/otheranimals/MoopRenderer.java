package com.leon.saintsdragons.client.renderer.otheranimals;

import com.leon.saintsdragons.client.model.otheranimals.MoopModel;
import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.server.entity.otheranimals.Moop;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class MoopRenderer extends com.leon.saintsdragons.client.compat.geo.LegacyGeoEntityRenderer<Moop> {
    public MoopRenderer(EntityRendererProvider.Context context) {
        super(context, new MoopModel());
        this.shadowRadius = 0.25F;
    }

    @Override
    public @NotNull Identifier getTextureLocation(@NotNull Moop entity) {
        return SaintsDragonsCommon.rl("textures/entity/moop/moop.png");
    }
}
