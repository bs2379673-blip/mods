package com.tunombre.taaef;

import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.client.animation.Layer;
import yesman.epicfight.api.client.animation.property.ClientAnimationProperties;
import yesman.epicfight.api.client.animation.property.JointMask;
import yesman.epicfight.api.client.animation.property.JointMaskEntry;
import yesman.epicfight.gameasset.Armatures;

/**
 * Capa compuesta de prioridad maxima: se pone encima de la pose del arma de Epic Arsenal sin borrarla.
 * Las de "solo brazos" (high ready, pared, correr, municion) dejan las piernas a Epic Fight (andar, correr...).
 */
public final class Anims {
    public static AnimationManager.AnimationAccessor<StaticAnimation> HIGH_READY_RIFLE, HIGH_READY_PISTOL, WALL_RIFLE, WALL_PISTOL,
            GUN_RUN_RIFLE, GUN_RUN_PISTOL, AMMO_CHECK_RIFLE, AMMO_CHECK_PISTOL, KICK_RIFLE, KICK_PISTOL,
            PRONE_RIFLE, PRONE_PISTOL, PRONE_CRAWL_RIFLE, PRONE_CRAWL_PISTOL, DOOR_PUSH;

    private Anims() { }

    static void build(AnimationManager.AnimationBuilder b) {
        HIGH_READY_RIFLE = anim(b, "high_ready_rifle", true, true);
        HIGH_READY_PISTOL = anim(b, "high_ready_pistol", true, true);
        WALL_RIFLE = anim(b, "wall_rifle", true, true);
        WALL_PISTOL = anim(b, "wall_pistol", true, true);
        GUN_RUN_RIFLE = anim(b, "gun_run_rifle", true, true);
        GUN_RUN_PISTOL = anim(b, "gun_run_pistol", true, true);
        AMMO_CHECK_RIFLE = anim(b, "ammo_check_rifle", false, true);
        AMMO_CHECK_PISTOL = anim(b, "ammo_check_pistol", false, true);
        KICK_RIFLE = anim(b, "kick_rifle", false, false);
        KICK_PISTOL = anim(b, "kick_pistol", false, false);
        PRONE_RIFLE = anim(b, "prone_rifle", true, false);
        PRONE_PISTOL = anim(b, "prone_pistol", true, false);
        PRONE_CRAWL_RIFLE = anim(b, "prone_crawl_rifle", true, false);
        PRONE_CRAWL_PISTOL = anim(b, "prone_crawl_pistol", true, false);
        // empujar la puerta: solo el brazo izquierdo, en una capa por debajo (no corta el resto)
        DOOR_PUSH = b.nextAccessor("biped/taa/door_push", acc -> {
            StaticAnimation a = new StaticAnimation(0.1F, false, acc, Armatures.BIPED);
            a.addProperty(ClientAnimationProperties.LAYER_TYPE, Layer.LayerType.COMPOSITE_LAYER);
            a.addProperty(ClientAnimationProperties.PRIORITY, Layer.Priority.HIGH);
            a.addProperty(ClientAnimationProperties.JOINT_MASK, JointMaskEntry.builder().defaultMask(JointMask.JointMaskSet.of(
                    JointMask.of("Shoulder_L"), JointMask.of("Arm_L"), JointMask.of("Elbow_L"), JointMask.of("Hand_L"), JointMask.of("Tool_L"))).create());
            return a;
        });
    }

    private static AnimationManager.AnimationAccessor<StaticAnimation> anim(AnimationManager.AnimationBuilder b, String n, boolean loop, boolean upperOnly) {
        return b.nextAccessor("biped/taa/" + n, acc -> {
            StaticAnimation a = new StaticAnimation(0.15F, loop, acc, Armatures.BIPED);
            a.addProperty(ClientAnimationProperties.LAYER_TYPE, Layer.LayerType.COMPOSITE_LAYER);
            a.addProperty(ClientAnimationProperties.PRIORITY, Layer.Priority.HIGHEST);
            if (upperOnly)
                a.addProperty(ClientAnimationProperties.JOINT_MASK, JointMaskEntry.builder().defaultMask(JointMaskEntry.BIPED_UPPER_JOINTS_WITH_ROOT).create());
            return a;
        });
    }
}
