package com.tunombre.efzombies;

import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.world.capabilities.entitypatch.mob.ZombiePatch;

/**
 * Parche de Epic Fight del trepador: es un zombi para Epic Fight (ataques, golpes, faccion) con animaciones propias.
 * El estado trepado lo decide la entidad (sincronizado); aqui solo se traduce a "motions" de Epic Fight:
 * pared moviendose = CLIMB, pared quieto = KNEEL, techo moviendose = FLY, techo quieto = FLOAT.
 * Esos motions no los usa ningun zombi, asi que no chocan con nada.
 */
public class TrepadorPatch extends ZombiePatch<Trepador> {

    @Override
    public void initAnimator(Animator animator) {
        super.initAnimator(animator);
        animator.addLivingAnimation(LivingMotions.IDLE, EfZombies.T_IDLE);
        animator.addLivingAnimation(LivingMotions.WALK, EfZombies.T_WALK);
        animator.addLivingAnimation(LivingMotions.CHASE, EfZombies.T_RUN);
        animator.addLivingAnimation(LivingMotions.FALL, EfZombies.T_FALL);
        animator.addLivingAnimation(LivingMotions.CLIMB, EfZombies.T_WALL);
        animator.addLivingAnimation(LivingMotions.KNEEL, EfZombies.T_WALL_IDLE);
        animator.addLivingAnimation(LivingMotions.FLY, EfZombies.T_CEIL);
        animator.addLivingAnimation(LivingMotions.FLOAT, EfZombies.T_CEIL_IDLE);
    }

    @Override
    public void updateMotion(boolean considerInaction) {
        Trepador t = this.getOriginal();
        byte s = t.getClimbState();
        if (s == Trepador.WALL) {
            this.currentLivingMotion = t.isClimbMoving() ? LivingMotions.CLIMB : LivingMotions.KNEEL;
            this.currentCompositeMotion = this.currentLivingMotion;
            return;
        }
        if (s == Trepador.CEILING) {
            this.currentLivingMotion = t.isClimbMoving() ? LivingMotions.FLY : LivingMotions.FLOAT;
            this.currentCompositeMotion = this.currentLivingMotion;
            return;
        }
        super.updateMotion(considerInaction);
    }
}
