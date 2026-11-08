package com.leon.saintsdragons.util.compat;

import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.state.AnimationTest;
import software.bernie.geckolib.constant.DataTickets;

/** Port 1.21.11: cosas de GeckoLib 4 que en GeckoLib 5 se piden distinto. */
public final class GeoCompat {
    private GeoCompat() {
    }

    public static float partialTick(AnimationTest<?> state) {
        return state.getDataOrDefault(DataTickets.PARTIAL_TICK, 1f);
    }

    public static float partialTick(software.bernie.geckolib.animation.state.KeyFrameEvent<?, ?> event) {
        return event.getPartialTick();
    }

    public static RawAnimation triggered(AnimationController<?> controller) {
        return controller.isPlayingTriggeredAnimation() ? controller.getCurrentRawAnimation() : null;
    }

    /** Port GeckoLib 5: el nombre de la animacion (la primera etapa) de un RawAnimation. */
    public static String name(software.bernie.geckolib.animation.RawAnimation raw) {
        return raw == null || raw.getAnimationStages().isEmpty() ? "" : raw.getAnimationStages().get(0).animationName();
    }
}
