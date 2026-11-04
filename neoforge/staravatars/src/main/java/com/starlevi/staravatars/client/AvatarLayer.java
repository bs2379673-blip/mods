package com.starlevi.staravatars.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Dibuja el personaje en lugar del jugador. Cada parte del personaje sigue a la parte del jugador de
 * Minecraft (cabeza, torso, brazos, piernas) con el mismo giro y corrimiento que le haya puesto
 * cualquier animacion (la de Minecraft, Punchy Moves u otro mod), pero desde la articulacion del
 * personaje, que tiene otras proporciones.
 */
public class AvatarLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final Quaternionf q = new Quaternionf();

    public AvatarLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    static ModelPart[] parts(PlayerModel<?> m) {
        return new ModelPart[]{m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbAmount,
                       float partial, float age, float headYaw, float headPitch) {
        ClientAvatars.Ready r = ClientAvatars.of(player.getUUID());
        if (r == null || player.isInvisible()) {
            return;
        }
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0);
        draw(ps, buffers, light, overlay, r, parts(getParentModel()));
    }

    /** Tambien lo usa el menu (vista del personaje sin jugador). */
    void draw(PoseStack ps, MultiBufferSource buffers, int light, int overlay, ClientAvatars.Ready r, ModelPart[] vanilla) {
        AvatarBuilder.Result m = r.model;
        for (int i = 0; i < 6; i++) {
            ModelPart p = vanilla[i];
            PartPose d = p.getInitialPose();
            float[] j = m.joints[i];
            ps.pushPose();
            ps.translate((j[0] + p.x - d.x) / 16f, (j[1] + p.y - d.y) / 16f, (j[2] + p.z - d.z) / 16f);
            if (p.xRot != 0 || p.yRot != 0 || p.zRot != 0) {
                ps.mulPose(this.q.rotationZYX(p.zRot, p.yRot, p.xRot));
            }
            if (p.xScale != 1 || p.yScale != 1 || p.zScale != 1) {
                ps.scale(p.xScale, p.yScale, p.zScale);
            }
            PoseStack.Pose pose = ps.last();
            for (AvatarBuilder.Batch b : m.parts.get(i)) {
                AvatarBuilder.Material mat = m.materials.get(b.material);
                RenderType type = mat.doubleSided ? RenderType.entityCutoutNoCull(r.textures[b.material])
                        : RenderType.entityCutout(r.textures[b.material]);
                emit(buffers.getBuffer(type), pose, b.data, light, overlay);
            }
            ps.popPose();
        }
    }

    private static void emit(VertexConsumer vc, PoseStack.Pose pose, float[] d, int light, int overlay) {
        Matrix4f mat = pose.pose();
        Matrix3f nm = pose.normal();
        float s = 1 / 16f;
        for (int t = 0; t < d.length; t += 24) {
            for (int k = 0; k < 4; k++) {
                int o = t + Math.min(k, 2) * 8;
                float x = d[o] * s, y = d[o + 1] * s, z = d[o + 2] * s;
                float px = mat.m00() * x + mat.m10() * y + mat.m20() * z + mat.m30();
                float py = mat.m01() * x + mat.m11() * y + mat.m21() * z + mat.m31();
                float pz = mat.m02() * x + mat.m12() * y + mat.m22() * z + mat.m32();
                float nx = d[o + 3], ny = d[o + 4], nz = d[o + 5];
                float qx = nm.m00() * nx + nm.m10() * ny + nm.m20() * nz;
                float qy = nm.m01() * nx + nm.m11() * ny + nm.m21() * nz;
                float qz = nm.m02() * nx + nm.m12() * ny + nm.m22() * nz;
                vc.addVertex(px, py, pz, -1, d[o + 6], d[o + 7], overlay, light, qx, qy, qz);
            }
        }
    }
}
