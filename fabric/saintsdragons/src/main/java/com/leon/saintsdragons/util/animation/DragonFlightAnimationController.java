package com.leon.saintsdragons.util.animation;

import com.leon.saintsdragons.server.entity.base.RideableFlyingDragon;
import com.leon.saintsdragons.server.flight.DragonFlightAnimationProfile;
import com.leon.saintsdragons.server.flight.DragonFlightBlend;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.constant.DataTickets;

/**
 * Vuelo. Port 1.21.11 (GeckoLib 5): en GeckoLib 4 mezclaba a mano planeo, aleteo, sprint y quieto en el
 * aire; aca se elige el clip que mas pesa en cada momento y GeckoLib 5 hace la transicion entre clips.
 */
public final class DragonFlightAnimationController<T extends RideableFlyingDragon> extends EntityAnimationController<T> {
    public DragonFlightAnimationController(T dragon, int transitionTicks, AnimationStateHandler<T> predicate) {
        super(dragon, AnimationHelper.FLIGHT_CONTROLLER, Math.max(0, transitionTicks), state -> {
            state.controller().setTransitionTicks(Math.max(4, transitionTicks));
            PlayState result = predicate.handle(state);
            T d = state.animatable();
            DragonFlightAnimationProfile profile = d.getFlightAnimationProfile();
            if (profile == null || !d.isFlightBlendActive() || state.controller().isPlayingTriggeredAnimation()) {
                return result;
            }
            float partial = state.getDataOrDefault(DataTickets.PARTIAL_TICK, 1f);
            DragonFlightBlend blend = d.getFlightBlend();
            float flap = blend.flap(partial), sprint = blend.sprint(partial), idle = blend.idle(partial);
            float powered = Mth.clamp(flap + sprint, 0, 1);
            String clip;
            if (idle > 0.5F) {
                clip = profile.clips().idle().name();
            } else if (powered > 0.5F) {
                clip = sprint > flap ? profile.clips().sprint().name() : profile.clips().flap().name();
            } else {
                clip = profile.clips().glide();
            }
            state.setAnimation(RawAnimation.begin().thenLoop(clip));
            return PlayState.CONTINUE;
        });
    }
}
