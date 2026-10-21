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
    /** matriz para pasar de las matrices de "piel" a la del hueso en su sistema local (inversa del reposo) por esqueleto y hueso */
    private final Map<String, OpenMatrix4f> bind = new HashMap<>();

    @Override
    protected void renderLayer(LivingEntityPatch patch, LivingEntity e, RenderLayer layer, PoseStack ps, MultiBufferSource buf, int light, OpenMatrix4f[] poses, float a, float b, float c, float d) {
        if (!(e instanceof AbstractClientPlayer p)) return;
        long mask = CosClient.mask(p.getUUID());
        if (mask == 0L || p.isInvisible() || p.isSpectator()) return;
        Armature arm = patch.getArmature();
        float pt = net.minecraft.client.Minecraft.getInstance().getFrameTime();
        CosRender.draw(p, mask, pt, light, buf, ps, (stack, bone) -> {
            String name = switch (bone) { case HEAD -> "Head"; case CHEST -> "Chest"; case PELVIS -> "Torso"; };
            Joint j = arm.hasJoint(name) ? arm.searchJointByName(name) : null;
            if (j == null || j.getId() >= poses.length) return false;
            OpenMatrix4f inv = bind.computeIfAbsent(System.identityHashCode(arm) + name, k -> OpenMatrix4f.invert(j.getToOrigin(), null));
            OpenMatrix4f world = OpenMatrix4f.mul(poses[j.getId()], inv, null);
            stack.pushPose();
            MathUtils.mulStack(stack, world);
            if (bone == Cosm.Bone.CHEST) stack.translate(0F, 0.4F, 0F);
            else if (bone == Cosm.Bone.PELVIS) stack.translate(0F, -0.064F, 0F);
            return true;
        });
    }
}
