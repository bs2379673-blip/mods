package com.annis.npc.client;

import com.annis.npc.entity.AnnisNpcEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;

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
            ModelPart root = TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE, false), 64, 64).createModel();
            scratch = new NpcModel(root, false);
        }
        return scratch;
    }

    /** Llamar al final de setAngles. Devuelve true si cambio la pose. */
    public static boolean afterSetAngles(BipedEntityModel<?> model, LivingEntity entity, float animationProgress) {
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
            for (net.minecraft.client.model.ModelPart lo : new net.minecraft.client.model.ModelPart[]{
                    p.rightForearm, p.leftForearm, p.rightShin, p.leftShin, p.waist}) {
                lo.pitch = 0;
                lo.yaw = 0;
                lo.roll = 0;
            }
        }
        p.head.copyTransform(model.head);
        p.body.copyTransform(model.body);
        p.rightArm.copyTransform(model.rightArm);
        p.leftArm.copyTransform(model.leftArm);
        p.rightLeg.copyTransform(model.rightLeg);
        p.leftLeg.copyTransform(model.leftLeg);
        float partial = animationProgress - entity.age;
        float shared = partial;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null) {
            long elapsed = mc.world.getTime() - anim.start();
            if (elapsed >= 0 && elapsed < 20L * 60 * 60 * 24) {
                shared = elapsed + partial;
            }
        }
        state.applier.setTickDelta(partial);
        Activities.setCurrent(entity);
        state.applyOverlay(p, shared);
        Activities.setCurrent(null);
        model.head.copyTransform(p.head);
        model.hat.copyTransform(p.head);
        model.body.copyTransform(p.body);
        model.rightArm.copyTransform(p.rightArm);
        model.leftArm.copyTransform(p.leftArm);
        model.rightLeg.copyTransform(p.rightLeg);
        model.leftLeg.copyTransform(p.leftLeg);
        if (model instanceof PlayerEntityModel<?> pm) {
            pm.jacket.copyTransform(pm.body);
            pm.rightSleeve.copyTransform(pm.rightArm);
            pm.leftSleeve.copyTransform(pm.leftArm);
            pm.rightPants.copyTransform(pm.rightLeg);
            pm.leftPants.copyTransform(pm.leftLeg);
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
