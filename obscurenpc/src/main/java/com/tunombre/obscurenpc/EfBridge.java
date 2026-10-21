package com.tunombre.obscurenpc;

import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;

/** Puente servidor -> parche de Epic Fight del NPC. */
public final class EfBridge {
    private EfBridge() { }

    public static void play(Actor a, String id) {
        try {
            ActorPatch p = EpicFightCapabilities.getEntityPatch(a, ActorPatch.class);
            if (p == null) return;
            AnimationManager.AnimationAccessor<? extends StaticAnimation> acc = Gestures.find(id);
            if (acc != null) p.playAnimationSynchronized(acc, 0.12F);
        } catch (RuntimeException ignored) { }
    }
}
