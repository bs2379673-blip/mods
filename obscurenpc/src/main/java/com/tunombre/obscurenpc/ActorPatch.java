package com.tunombre.obscurenpc;

import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.utils.math.MathUtils;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.entitypatch.Factions;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;

/**
 * Parche de Epic Fight del NPC. Camina/corre/lucha como un humanoide de Epic Fight y ademas tiene posturas fijas
 * (sentado, brazos cruzados...), que se traducen a "motions" que ningun mob usa.
 */
public class ActorPatch extends HumanoidMobPatch<Actor> {
    public ActorPatch() {
        super(Factions.NEUTRAL);
    }

    @Override
    public void initAnimator(Animator animator) {
        super.initAnimator(animator);
        animator.addLivingAnimation(LivingMotions.IDLE, Animations.BIPED_IDLE);
        animator.addLivingAnimation(LivingMotions.WALK, Animations.BIPED_WALK);
        animator.addLivingAnimation(LivingMotions.CHASE, Animations.BIPED_RUN);
        animator.addLivingAnimation(LivingMotions.FALL, Animations.BIPED_FALL);
        animator.addLivingAnimation(LivingMotions.MOUNT, Animations.BIPED_MOUNT);
        animator.addLivingAnimation(LivingMotions.DEATH, Animations.BIPED_DEATH);
        animator.addLivingAnimation(LivingMotions.SIT, Animations.BIPED_SIT);
        animator.addLivingAnimation(LivingMotions.SLEEP, Animations.BIPED_SLEEPING);
        animator.addLivingAnimation(LivingMotions.KNEEL, Animations.BIPED_KNEEL);
        animator.addLivingAnimation(LivingMotions.SNEAK, Animations.BIPED_SNEAK);
        add(animator, LivingMotions.ADMIRE, "cross_arms");
        add(animator, LivingMotions.CREATIVE_IDLE, "hands_back");
        add(animator, LivingMotions.CELEBRATE, "hands_hip");
        add(animator, LivingMotions.SPELLCAST, "think_pose");
        add(animator, LivingMotions.BLOCK, "guard");
    }

    private static void add(Animator animator, LivingMotions motion, String gesture) {
        var acc = Gestures.MINE.get(gesture);
        if (acc != null) animator.addLivingAnimation(motion, acc);
    }

    @Override
    public void updateMotion(boolean considerInaction) {
        Actor a = this.getOriginal();
        int s = a.stance();
        boolean moving = a.walkAnimation.speed() > 0.05F;
        if (s > 0 && !moving && a.getHealth() > 0 && !a.isAggressive()) {
            LivingMotions m = switch (s) {
                case 1 -> LivingMotions.SIT;
                case 2 -> LivingMotions.SLEEP;
                case 3 -> LivingMotions.KNEEL;
                case 4 -> LivingMotions.ADMIRE;
                case 5 -> LivingMotions.CREATIVE_IDLE;
                case 6 -> LivingMotions.CELEBRATE;
                case 7 -> LivingMotions.SPELLCAST;
                case 8 -> LivingMotions.BLOCK;
                case 9 -> LivingMotions.SNEAK;
                default -> LivingMotions.IDLE;
            };
            this.currentLivingMotion = m;
            this.currentCompositeMotion = m;
            return;
        }
        super.commonAggressiveMobUpdateMotion(considerInaction);
    }

    @Override
    public OpenMatrix4f getModelMatrix(float partialTicks) {
        Actor a = this.getOriginal();
        float scale = a.isBaby() ? 0.5F : a.scale();
        float yRotO = this.isLogicalClient() ? a.yBodyRotO : a.getYRot();
        float yRot = this.isLogicalClient() ? a.yBodyRot : a.getYRot();
        return MathUtils.getModelMatrixIntegral(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, yRotO, yRot, partialTicks, scale, scale, scale);
    }
}
