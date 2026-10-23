package com.tunombre.cosmeticos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tunombre.cosmeticos.geo.Cosm;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.MathUtils;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.util.HashMap;
import java.util.Map;

/**
 * capa para cuando Epic Fight dibuja al jugador: los cosmeticos se pegan a los huesos reales del esqueleto
 * (Head, Chest, Torso), asi siguen cualquier animacion de combate, esquiva, escalada...
 */
@SuppressWarnings({"rawtypes"})
public class CosPatchedLayer extends PatchedLayer {
    @Override
    protected void renderLayer(LivingEntityPatch patch, LivingEntity e, RenderLayer layer, PoseStack ps, MultiBufferSource buf, int light, OpenMatrix4f[] poses, float a, float b, float c, float d) {
        if (!(e instanceof AbstractClientPlayer p)) return;
        long mask = CosClient.mask(p.getUUID());
        if (mask == 0L || p.isInvisible() || p.isSpectator()) return;
        Armature arm = patch.getArmature();
        float pt = net.minecraft.client.Minecraft.getInstance().getFrameTime();
        CosRender.draw(p, mask, pt, light, buf, ps, (stack, bone) -> {
            if (bone == Cosm.Bone.ROOT) {
                // sin hueso: la pila ya está en los pies del jugador y gira con el cuerpo; no sigue las inclinaciones de las animaciones
                stack.pushPose();
                stack.translate(0F, 0.75F, 0F);
                stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180F));
                return true;
            }
            String name = switch (bone) { case HEAD -> "Head"; case CHEST -> "Chest"; case PELVIS -> "Torso"; case HAND_R -> arm.hasJoint("Tool_R") ? "Tool_R" : "Hand_R"; default -> "Root"; };
            Joint j = arm.hasJoint(name) ? arm.searchJointByName(name) : null;
            if (j == null || j.getId() >= poses.length) return false;
            // poses[] = transformacion absoluta de la articulacion (incluye su altura de reposo y la animacion): es lo mismo que
            // usa Epic Fight para el casco, la capa o los elitros, asi que NO hay que sumarle la posicion del hueso otra vez
            stack.pushPose();
            MathUtils.mulStack(stack, poses[j.getId()]);
            // desplazamientos en ejes del hueso (y arriba): del eje de la articulacion al origen que espera cada cosmetico
            switch (bone) {
                case HEAD -> stack.translate(0F, -0.02F, 0F);       // el casco de Epic Fight baja 0.02
                case CHEST -> stack.translate(0F, 0.4F, 0F);        // del centro del pecho (1.11) al cuello (1.51)
                case PELVIS -> stack.translate(0F, -0.064F, 0F);    // de la articulacion Torso (0.81) a la cadera (0.75)
                default -> { }
            }
            // del espacio de los cosmeticos (x izquierda, y arriba, z delante) al del esqueleto (z invertido y x al reves)
            stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180F));
            return true;
        });
    }
}
