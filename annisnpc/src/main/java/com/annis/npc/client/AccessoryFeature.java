package com.annis.npc.client;

import com.annis.npc.entity.AnnisNpcEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;

/**
 * Dibuja los accesorios pegados a los huesos: siguen exactamente la pose del hueso
 * (emotes, actividades, movimientos grabados o EMF), asi un tambor va con el torso y
 * una baqueta con la mano.
 */
public class AccessoryFeature extends FeatureRenderer<AnnisNpcEntity, PlayerEntityModel<AnnisNpcEntity>> {
    private final HeldItemRenderer itemRenderer;

    public AccessoryFeature(FeatureRendererContext<AnnisNpcEntity, PlayerEntityModel<AnnisNpcEntity>> context,
                            HeldItemRenderer itemRenderer) {
        super(context);
        this.itemRenderer = itemRenderer;
    }

    private static ModelPart bone(PlayerEntityModel<?> m, String bone) {
        return switch (bone) {
            case "body" -> m.body;
            case "right_arm" -> m.rightArm;
            case "left_arm" -> m.leftArm;
            case "right_leg" -> m.rightLeg;
            case "left_leg" -> m.leftLeg;
            default -> m.head;
        };
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AnnisNpcEntity npc,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress,
                       float headYaw, float headPitch) {
        if (npc.isInvisible()) {
            return;
        }
        PlayerEntityModel<AnnisNpcEntity> model = this.getContextModel();
        for (Accessory a : Accessory.parseAll(npc.getAccessories())) {
            ItemStack stack = a.stack();
            if (stack.isEmpty()) {
                continue;
            }
            matrices.push();
            ModelPart part = bone(model, a.bone);
            part.rotate(matrices);
            float ay = a.y;
            // modelo articulado: lo que va en la mano o el pie sigue al antebrazo / la espinilla
            if (model instanceof NpcModel nm && nm.jointed && !a.bone.equals("head") && !a.bone.equals("body")) {
                ModelPart lower = part.getChild("lower");
                if (ay > lower.pivotY - 0.5f) {
                    lower.rotate(matrices);
                    ay -= lower.pivotY;
                }
            }
            matrices.translate(a.x / 16.0f, ay / 16.0f, a.z / 16.0f);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));
            matrices.scale(a.scale, -a.scale, -a.scale);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(a.ry));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(a.rx));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(a.rz));
            this.itemRenderer.renderItem(npc, stack, ModelTransformationMode.FIXED, false, matrices, vertexConsumers, light);
            matrices.pop();
        }
    }
}
