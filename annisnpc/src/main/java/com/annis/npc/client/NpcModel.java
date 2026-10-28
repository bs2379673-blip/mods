package com.annis.npc.client;

import com.annis.npc.entity.AnnisNpcEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

/**
 * Modelo de jugador para los NPC. Aplica a mano los emotes de playerAnimator
 * (la libreria solo lo hace para jugadores) y, si no hay emote, la capa procedural suave.
 */
public class NpcModel extends PlayerEntityModel<AnnisNpcEntity> {
    public NpcModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setAngles(AnnisNpcEntity npc, float limbAngle, float limbDistance,
                          float animationProgress, float headYaw, float headPitch) {
        super.setAngles(npc, limbAngle, limbDistance, animationProgress, headYaw, headPitch);

        NpcAnimState state = NpcAnimState.of(npc);
        boolean overriding = state.isOverriding();
        EmfCompat.setPaused(npc, overriding);
        // si EMF anima el modelo (caminar/reposo), su animacion manda cuando no hay emote ni actividad
        boolean emfDrives = !overriding && EmfCompat.emfAnimates(this);
        if (state.applier.isActive()) {
            state.applier.updatePart("head", this.head);
            state.applier.updatePart("leftArm", this.leftArm);
            state.applier.updatePart("rightArm", this.rightArm);
            state.applier.updatePart("leftLeg", this.leftLeg);
            state.applier.updatePart("rightLeg", this.rightLeg);
            state.applier.updatePart("torso", this.body);
        } else if (!npc.isSneaking() && !npc.hasVehicle()) {
            if (!emfDrives) {
                state.applyProcedural(this, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
            }
            // reloj comun: todos los NPC con la misma actividad se mueven al mismo compas
            float partial = animationProgress - npc.age;
            float shared = partial;
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.world != null) {
                long elapsed = mc.world.getTime() - npc.getEmoteStart();
                if (elapsed >= 0 && elapsed < 20L * 60 * 60 * 24) {
                    shared = elapsed + partial;
                }
            }
            state.applyActivity(this, shared);
        }

        // la capa exterior de la skin copia la pose de cada parte
        this.hat.copyTransform(this.head);
        this.jacket.copyTransform(this.body);
        this.leftSleeve.copyTransform(this.leftArm);
        this.rightSleeve.copyTransform(this.rightArm);
        this.leftPants.copyTransform(this.leftLeg);
        this.rightPants.copyTransform(this.rightLeg);
    }
}
