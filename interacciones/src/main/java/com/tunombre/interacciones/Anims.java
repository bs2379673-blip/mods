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
    public static AnimationManager.AnimationAccessor<StaticAnimation> CARRY_HOLD, CARRIED, HIGH_FIVE, FIST_BUMP, TRADE_TALK, EXEC_ATTACKER, EXEC_VICTIM,
            HUG, HANDSHAKE, BOW, SALUTE, CHEST_BUMP, PAT_GIVE, PAT_TAKE, DANCE, SHOVE, SHOVED, SLAP, SLAPPED, FRISK, FRISKED, PIGGY_CARRY, PIGGY_RIDE,
            ARREST_OFFICER, ARREST_SUSPECT, CHOKE_ATTACKER, CHOKE_VICTIM, SCARE_ATTACKER, SCARED, BANDAGE_GIVE, BANDAGE_TAKE, GIVE_OFFER, GIVE_TAKE,
            TOAST, RPS, SECRET_HANDSHAKE, SLOW_DANCE_LEAD, SLOW_DANCE_FOLLOW, BOOST_GIVE, BOOST_TAKE, BRIDAL_CARRY, BRIDAL_CARRIED;

    private Anims() { }

    static void build(AnimationManager.AnimationBuilder b) {
        CARRY_HOLD = anim(b, "carry_hold", true, true);
        CARRIED = anim(b, "carried", true, false);
        HIGH_FIVE = anim(b, "high_five", false, true);
        FIST_BUMP = anim(b, "fist_bump", false, true);
        TRADE_TALK = anim(b, "trade_talk", true, true);
        EXEC_ATTACKER = anim(b, "exec_attacker", false, false);
        EXEC_VICTIM = anim(b, "exec_victim", false, false);
        // saludos y gestos: solo la mitad de arriba (las piernas siguen su animacion normal)
        HUG = anim(b, "hug", false, true);
        HANDSHAKE = anim(b, "handshake", false, true);
        SALUTE = anim(b, "salute", false, true);
        PAT_GIVE = anim(b, "pat_give", false, true);
        PAT_TAKE = anim(b, "pat_take", false, true);
        SHOVE = anim(b, "shove", false, true);
        SLAP = anim(b, "slap", false, true);
        SLAPPED = anim(b, "slapped", false, true);
        PIGGY_CARRY = anim(b, "piggy_carry", true, true);
        // cuerpo entero: se mueven las piernas (salto, baile, paso atras, agacharse...)
        BOW = anim(b, "bow", false, false);
        CHEST_BUMP = anim(b, "chest_bump", false, false);
        DANCE = anim(b, "dance", false, false);
        SHOVED = anim(b, "shoved", false, false);
        FRISK = anim(b, "frisk", false, false);
        FRISKED = anim(b, "frisked", false, false);
        PIGGY_RIDE = anim(b, "piggy_ride", true, false);
        // tanda 3
        ARREST_OFFICER = anim(b, "arrest_officer", false, false);
        ARREST_SUSPECT = anim(b, "arrest_suspect", false, false);
        CHOKE_ATTACKER = anim(b, "choke_attacker", false, false);
        CHOKE_VICTIM = anim(b, "choke_victim", false, false);
        SCARE_ATTACKER = anim(b, "scare_attacker", false, false);
        SCARED = anim(b, "scared", false, false);
        BANDAGE_GIVE = anim(b, "bandage_give", false, false);
        BANDAGE_TAKE = anim(b, "bandage_take", false, true);
        GIVE_OFFER = anim(b, "give_offer", false, true);
        GIVE_TAKE = anim(b, "give_take", false, true);
        TOAST = anim(b, "toast", false, true);
        RPS = anim(b, "rps", false, true);
        SECRET_HANDSHAKE = anim(b, "secret_handshake", false, false);
        SLOW_DANCE_LEAD = anim(b, "slow_dance_lead", false, false);
        SLOW_DANCE_FOLLOW = anim(b, "slow_dance_follow", false, false);
        BOOST_GIVE = anim(b, "boost_give", false, false);
        BOOST_TAKE = anim(b, "boost_take", false, false);
        BRIDAL_CARRY = anim(b, "bridal_carry", true, true);
        BRIDAL_CARRIED = anim(b, "bridal_carried", true, false);
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
