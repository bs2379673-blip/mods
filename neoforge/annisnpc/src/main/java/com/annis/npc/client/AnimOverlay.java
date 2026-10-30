package com.annis.npc.client;

import com.annis.npc.entity.AnnisNpcEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.LivingEntity;

/**
 * Animaciones de Annis encima de cualquier modelo de humanoide (jugadores, aldeanos de MCA...):
 * se copia la pose al modelo de NPC, se aplica el emote o la actividad con la misma mezcla
 * suave que en los NPC, y se devuelve. Las animaciones de EMF se pausan mientras tanto.
 */
public final class AnimOverlay {
    private static NpcModel scratch;

    private AnimOverlay() {
    }

    private static NpcModel scratch(boolean canBend) {
        if (scratch == null) {
            ModelPart root = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot();
            scratch = new NpcModel(root, false);
        }
        return scratch;
    }

    /** Llamar al final de setAngles. Devuelve true si cambio la pose. */
    public static boolean afterSetAngles(HumanoidModel<?> model, LivingEntity entity, float animationProgress) {
        if (entity instanceof AnnisNpcEntity) {
            return false;
        }
        NpcAnimState state = NpcAnimState.peek(entity);
        ClientLinks.Anim anim = ClientLinks.anim(entity);
        if (state == null || anim == null) {
            EmfCompat.setPaused(entity, false);
            return false;
        }
        boolean overriding = state.isOverriding();
        EmfCompat.setPaused(entity, overriding);
        if (!overriding) {
            return false;
        }
        NpcModel p = scratch(false);
        if (p.rightForearm != null) {
            for (net.minecraft.client.model.geom.ModelPart lo : new net.minecraft.client.model.geom.ModelPart[]{
                    p.rightForearm, p.leftForearm, p.rightShin, p.leftShin, p.waist}) {
                lo.xRot = 0;
                lo.yRot = 0;
                lo.zRot = 0;
            }
        }
        p.head.copyFrom(model.head);
        p.body.copyFrom(model.body);
        p.rightArm.copyFrom(model.rightArm);
        p.leftArm.copyFrom(model.leftArm);
        p.rightLeg.copyFrom(model.rightLeg);
        p.leftLeg.copyFrom(model.leftLeg);
        float partial = animationProgress - entity.tickCount;
        float shared = partial;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            long elapsed = mc.level.getGameTime() - anim.start();
            if (elapsed >= 0 && elapsed < 20L * 60 * 60 * 24) {
                shared = elapsed + partial;
            }
        }
        state.applier.setTickDelta(partial);
        Activities.setCurrent(entity);
        state.applyOverlay(p, shared);
        Activities.setCurrent(null);
        model.head.copyFrom(p.head);
        model.hat.copyFrom(p.head);
        model.body.copyFrom(p.body);
        model.rightArm.copyFrom(p.rightArm);
        model.leftArm.copyFrom(p.leftArm);
        model.rightLeg.copyFrom(p.rightLeg);
        model.leftLeg.copyFrom(p.leftLeg);
        if (model instanceof PlayerModel<?> pm) {
            pm.jacket.copyFrom(pm.body);
            pm.rightSleeve.copyFrom(pm.rightArm);
            pm.leftSleeve.copyFrom(pm.leftArm);
            pm.rightPants.copyFrom(pm.rightLeg);
            pm.leftPants.copyFrom(pm.leftLeg);
        }
        return true;
    }

    /** Pose de cuerpo entero (tumbado, en brazos...) para entidades que no son NPC Annis. */
    public static Activities.RootPose root(LivingEntity entity, float tickDelta) {
        if (entity instanceof AnnisNpcEntity) {
            return null;
        }
        NpcAnimState state = NpcAnimState.peek(entity);
        ClientLinks.Anim anim = ClientLinks.anim(entity);
        if (state == null || anim == null) {
            return null;
        }
        return state.rootPose(anim.start(), tickDelta);
    }
}
