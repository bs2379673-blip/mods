package com.annis.npc.mca;

import com.annis.npc.client.ClientLinks;
import com.annis.npc.client.EmfCompat;
import com.annis.npc.client.NpcAnimState;
import com.annis.npc.client.NpcModel;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Cliente: nuestras animaciones encima del modelo de los aldeanos de MCA, y su escala (ninos). */
public final class McaClientHooks {
    private static NpcModel proxy;

    private McaClientHooks() {
    }

    private static NpcModel proxy() {
        if (proxy == null) {
            ModelPart root = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot();
            proxy = new NpcModel(root, false);
        }
        return proxy;
    }

    private static void copy(HumanoidModel<?> from, NpcModel to) {
        to.head.copyFrom(from.head);
        to.body.copyFrom(from.body);
        to.rightArm.copyFrom(from.rightArm);
        to.leftArm.copyFrom(from.leftArm);
        to.rightLeg.copyFrom(from.rightLeg);
        to.leftLeg.copyFrom(from.leftLeg);
    }

    private static void copyBack(NpcModel from, HumanoidModel<?> to) {
        to.head.copyFrom(from.head);
        to.hat.copyFrom(from.head);
        to.body.copyFrom(from.body);
        to.rightArm.copyFrom(from.rightArm);
        to.leftArm.copyFrom(from.leftArm);
        to.rightLeg.copyFrom(from.rightLeg);
        to.leftLeg.copyFrom(from.leftLeg);
    }

    /** Llamado al final de setAngles del modelo de MCA. */
    public static void afterSetAngles(HumanoidModel<?> model, LivingEntity entity, float animationProgress) {
        if (com.annis.npc.client.AnimOverlay.afterSetAngles(model, entity, animationProgress)
                && model instanceof VillagerEntityModelMCA<?> mca) {
            mca.syncWearParts();
        }
    }

    /** Escala de MCA (ninos, adolescentes, altura). */
    public static float[] scale(Entity e) {
        if (e instanceof VillagerLike<?> v) {
            return new float[]{v.getRawHorizontalScaleFactor(), v.getRawVerticalScaleFactor()};
        }
        return null;
    }
}
