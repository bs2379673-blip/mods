package com.leon.saintsdragons.client.model.draconianswarm;

import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.server.entity.draconianswarm.Whettled;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import com.leon.saintsdragons.client.compat.geo.LegacyAnimState;

public class WhettledModel extends com.leon.saintsdragons.client.compat.geo.LegacyGeoModel<Whettled> {
    private static final Identifier MODEL = SaintsDragonsCommon.rl("geo/entity/draconian_swarm/whettled/whettled.geo.json");
    private static final Identifier TEXTURE =
            SaintsDragonsCommon.rl("textures/entity/draconian_swarm/whettled/whettled.png");
    private static final Identifier ANIMATIONS =
            SaintsDragonsCommon.rl("animations/entity/draconian_swarm/whettled/whettled.animation.json");

    @Override
    public Identifier getLegacyModelResource(Whettled animatable) {
        return MODEL;
    }

    @Override
    public Identifier getLegacyTextureResource(Whettled animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getLegacyAnimationResource(Whettled animatable) {
        return ANIMATIONS;
    }

    @Override
    public void setCustomAnimations(Whettled entity, long instanceId, LegacyAnimState<Whettled> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);
        if (!entity.isAlive()) {
            return;
        }
        float partialTick = animationState.getPartialTick();
        float pitch = Mth.clamp(entity.getFlightPitchRadians(partialTick), -0.95F, 0.95F);
        float drag = Mth.clamp(entity.getTailDragYawRadians(partialTick), -0.95F, 0.95F);
        getBone("root").ifPresent(bone -> bone.setRotX(bone.getInitialSnapshot().getRotX() + pitch));
        applyTailDrag("tail1rot", drag * 0.70F);
        applyTailDrag("tail2rot", drag * 1.00F);
        applyTailDrag("tail3rot", drag * 1.30F);
    }

    private void applyTailDrag(String boneName, float yaw) {
        getBone(boneName).ifPresent(bone -> bone.setRotY(bone.getInitialSnapshot().getRotY() + yaw));
    }
}
