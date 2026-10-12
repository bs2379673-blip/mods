package com.tunombre.interacciones;

import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.client.animation.Layer;
import yesman.epicfight.api.client.animation.property.ClientAnimationProperties;
import yesman.epicfight.api.client.animation.property.JointMaskEntry;
import yesman.epicfight.gameasset.Armatures;

/**
 * Animaciones de interaccion. Van en una capa COMPUESTA de prioridad maxima: asi no las pisa la animacion de caminar,
 * quedarse quieto o ir montado (el fallo tipico de 'se cancela una animacion y se buguea todo').
 * Las de saludo/carga solo mueven la mitad de arriba (las piernas siguen caminando); las de cuerpo entero mueven todo.
 */
public final class Anims {
    public static AnimationManager.AnimationAccessor<StaticAnimation> CARRY_HOLD, CARRIED, HIGH_FIVE, FIST_BUMP, TRADE_TALK, EXEC_ATTACKER, EXEC_VICTIM;

    private Anims() { }

    static void build(AnimationManager.AnimationBuilder b) {
        CARRY_HOLD = anim(b, "carry_hold", true, true);
        CARRIED = anim(b, "carried", true, false);
        HIGH_FIVE = anim(b, "high_five", false, true);
        FIST_BUMP = anim(b, "fist_bump", false, true);
        TRADE_TALK = anim(b, "trade_talk", true, true);
        EXEC_ATTACKER = anim(b, "exec_attacker", false, false);
        EXEC_VICTIM = anim(b, "exec_victim", false, false);
    }

    private static AnimationManager.AnimationAccessor<StaticAnimation> anim(AnimationManager.AnimationBuilder b, String n, boolean loop, boolean upperOnly) {
        return b.nextAccessor("biped/interact/" + n, acc -> {
            StaticAnimation a = new StaticAnimation(0.15F, loop, acc, Armatures.BIPED);
            a.addProperty(ClientAnimationProperties.LAYER_TYPE, Layer.LayerType.COMPOSITE_LAYER);
            a.addProperty(ClientAnimationProperties.PRIORITY, Layer.Priority.HIGHEST);
            if (upperOnly)
                a.addProperty(ClientAnimationProperties.JOINT_MASK, JointMaskEntry.builder().defaultMask(JointMaskEntry.BIPED_UPPER_JOINTS_WITH_ROOT).create());
            return a;
        });
    }
}
