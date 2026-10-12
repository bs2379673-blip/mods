package com.tunombre.infectados;

import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.entitypatch.Faction;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;

/**
 * Parche de Epic Fight comun: animaciones propias del mob y SIN la IA de combate de Epic Fight (los ataques los hace el
 * propio mob con su animacion y su instante de impacto). Epic Fight sigue poniendo los golpes recibidos y la muerte.
 */
public class InfPatch extends HumanoidMobPatch<InfMob> {
    public InfPatch(Faction f) {
        super(f);
    }

    @Override
    protected void initAI() {
        // nada: la IA es la del mob
    }

    @Override
    public void initAnimator(Animator animator) {
        animator.addLivingAnimation(LivingMotions.FALL, Animations.BIPED_FALL);
        animator.addLivingAnimation(LivingMotions.DEATH, Animations.BIPED_DEATH);
        animator.addLivingAnimation(LivingMotions.MOUNT, Animations.BIPED_MOUNT);
        this.getOriginal().motions().forEach(animator::addLivingAnimation);
    }

    @Override
    public void updateMotion(boolean considerInaction) {
        LivingMotion forced = this.getOriginal().forcedMotion();
        if (forced != null && this.getOriginal().isAlive()) {
            this.currentLivingMotion = forced;
            this.currentCompositeMotion = forced;
            return;
        }
        this.commonAggressiveMobUpdateMotion(considerInaction);
        if (this.currentLivingMotion == LivingMotions.MOUNT) this.currentLivingMotion = LivingMotions.IDLE;
    }
}
