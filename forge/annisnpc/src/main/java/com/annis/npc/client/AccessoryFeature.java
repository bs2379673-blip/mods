package com.annis.npc.client;

import com.annis.npc.entity.AnnisNpcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Dibuja los accesorios pegados a los huesos: siguen exactamente la pose del hueso
 * (emotes, actividades, movimientos grabados o EMF), asi un tambor va con el torso y
 * una baqueta con la mano.
 */
public class AccessoryFeature extends RenderLayer<AnnisNpcEntity, PlayerModel<AnnisNpcEntity>> {
    private final ItemInHandRenderer itemRenderer;

    public AccessoryFeature(RenderLayerParent<AnnisNpcEntity, PlayerModel<AnnisNpcEntity>> context,
                            ItemInHandRenderer itemRenderer) {
        super(context);
        this.itemRenderer = itemRenderer;
    }

    private static ModelPart bone(PlayerModel<?> m, String bone) {
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
    public void render(PoseStack matrices, MultiBufferSource vertexConsumers, int light, AnnisNpcEntity npc,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress,
                       float headYaw, float headPitch) {
        if (npc.isInvisible()) {
            return;
        }
        PlayerModel<AnnisNpcEntity> model = this.getParentModel();
        for (Accessory a : Accessory.parseAll(npc.getAccessories())) {
            ItemStack stack = a.stack();
            if (stack.isEmpty()) {
                continue;
            }
            matrices.pushPose();
            ModelPart part = bone(model, a.bone);
            part.translateAndRotate(matrices);
            float ay = a.y;
            // modelo articulado: lo que va en la mano o el pie sigue al antebrazo / la espinilla
            if (model instanceof NpcModel nm && nm.jointed && !a.bone.equals("head")) {
                ModelPart lower = part.getChild("lower");
                if (ay > lower.y - 0.5f) {
                    lower.translateAndRotate(matrices);
                    ay -= lower.y;
                }
            }
            matrices.translate(a.x / 16.0f, ay / 16.0f, a.z / 16.0f);
            matrices.mulPose(Axis.YP.rotationDegrees(180.0f));
            matrices.scale(a.scale, -a.scale, -a.scale);
            matrices.mulPose(Axis.YP.rotationDegrees(a.ry));
            matrices.mulPose(Axis.XP.rotationDegrees(a.rx));
            matrices.mulPose(Axis.ZP.rotationDegrees(a.rz));
            this.itemRenderer.renderItem(npc, stack, ItemDisplayContext.FIXED, false, matrices, vertexConsumers, light);
            matrices.popPose();
        }
    }
}
