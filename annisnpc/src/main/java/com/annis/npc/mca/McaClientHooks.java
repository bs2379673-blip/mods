package com.annis.npc.mca;

import com.annis.npc.client.ClientLinks;
import com.annis.npc.client.EmfCompat;
import com.annis.npc.client.NpcAnimState;
import com.annis.npc.client.NpcModel;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/** Cliente: nuestras animaciones encima del modelo de los aldeanos de MCA, y su escala (ninos). */
public final class McaClientHooks {
    private static NpcModel proxy;

    private McaClientHooks() {
    }

    private static NpcModel proxy() {
        if (proxy == null) {
            ModelPart root = TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE, false), 64, 64).createModel();
            proxy = new NpcModel(root, false);
        }
        return proxy;
    }

    private static void copy(BipedEntityModel<?> from, NpcModel to) {
        to.head.copyTransform(from.head);
        to.body.copyTransform(from.body);
        to.rightArm.copyTransform(from.rightArm);
        to.leftArm.copyTransform(from.leftArm);
        to.rightLeg.copyTransform(from.rightLeg);
        to.leftLeg.copyTransform(from.leftLeg);
    }

    private static void copyBack(NpcModel from, BipedEntityModel<?> to) {
        to.head.copyTransform(from.head);
        to.hat.copyTransform(from.head);
        to.body.copyTransform(from.body);
        to.rightArm.copyTransform(from.rightArm);
        to.leftArm.copyTransform(from.leftArm);
        to.rightLeg.copyTransform(from.rightLeg);
        to.leftLeg.copyTransform(from.leftLeg);
    }

    /** Llamado al final de setAngles del modelo de MCA. */
    public static void afterSetAngles(BipedEntityModel<?> model, LivingEntity entity, float animationProgress) {
        NpcAnimState state = NpcAnimState.peek(entity);
        ClientLinks.Anim anim = ClientLinks.anim(entity);
        if (state == null || anim == null) {
            EmfCompat.setPaused(entity, false);
            return;
        }
        boolean overriding = state.isOverriding();
        EmfCompat.setPaused(entity, overriding);
        if (!overriding) {
            return;
        }
        NpcModel p = proxy();
        copy(model, p);
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
        state.applyOverlay(p, shared);
        copyBack(p, model);
        if (model instanceof VillagerEntityModelMCA<?> mca) {
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
