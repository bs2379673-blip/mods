package com.tunombre.infectados;

import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.MovementAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Armatures;

/** Todas las animaciones (ids infectados:biped/<grupo>/<nombre>). */
public final class Anims {
    public static AnimationManager.AnimationAccessor<StaticAnimation> G_IDLE, G_POINT, G_CHOP, G_SWIPE, G_GRAB, G_PLAGA, G_THRUST, P_DECAP,
            P_GRABBED, P_CHAINSAW,
            M_IDLE, M_SWING, M_OVERHEAD, M_GRAB, M_TAUNT,
            S_IDLE, S_SHOOT, S_PULL,
            B_IDLE, B_VOMIT,
            W_CRY, W_ALERT, W_SCREAM, W_SLASH;
    public static AnimationManager.AnimationAccessor<MovementAnimation> G_WALK, G_RUN, G_HEADLESS, M_WALK, M_RUN, S_WALK, B_WALK, W_RUN;

    private Anims() { }

    static void build(AnimationManager.AnimationBuilder b) {
        G_IDLE = loop(b, "ganado/idle");
        G_WALK = move(b, "ganado/walk");
        G_RUN = move(b, "ganado/run");
        G_HEADLESS = move(b, "ganado/headless");
        G_POINT = once(b, "ganado/point");
        G_CHOP = once(b, "ganado/chop");
        G_SWIPE = once(b, "ganado/swipe");
        G_GRAB = loop(b, "ganado/grab");
        G_PLAGA = once(b, "ganado/plaga");
        G_THRUST = once(b, "ganado/thrust");
        P_DECAP = once(b, "player/decapitated");
        P_GRABBED = loop(b, "player/grabbed");
        P_CHAINSAW = loop(b, "player/chainsaw");
        M_IDLE = loop(b, "motosierra/idle");
        M_WALK = move(b, "motosierra/walk");
        M_RUN = move(b, "motosierra/run");
        M_SWING = once(b, "motosierra/swing");
        M_OVERHEAD = once(b, "motosierra/overhead");
        M_GRAB = loop(b, "motosierra/grab");
        M_TAUNT = once(b, "motosierra/taunt");
        S_IDLE = loop(b, "smoker/idle");
        S_WALK = move(b, "smoker/walk");
        S_SHOOT = once(b, "smoker/shoot");
        S_PULL = loop(b, "smoker/pull");
        B_IDLE = loop(b, "boomer/idle");
        B_WALK = move(b, "boomer/walk");
        B_VOMIT = once(b, "boomer/vomit");
        W_CRY = loop(b, "witch/cry");
        W_ALERT = loop(b, "witch/alert");
        W_SCREAM = once(b, "witch/scream");
        W_RUN = move(b, "witch/run");
        W_SLASH = once(b, "witch/slash");
    }

    private static AnimationManager.AnimationAccessor<StaticAnimation> loop(AnimationManager.AnimationBuilder b, String n) {
        return b.nextAccessor("biped/" + n, acc -> new StaticAnimation(0.2F, true, acc, Armatures.BIPED));
    }

    private static AnimationManager.AnimationAccessor<StaticAnimation> once(AnimationManager.AnimationBuilder b, String n) {
        return b.nextAccessor("biped/" + n, acc -> new StaticAnimation(0.1F, false, acc, Armatures.BIPED));
    }

    private static AnimationManager.AnimationAccessor<MovementAnimation> move(AnimationManager.AnimationBuilder b, String n) {
        return b.nextAccessor("biped/" + n, acc -> new MovementAnimation(0.2F, true, acc, Armatures.BIPED));
    }
}
