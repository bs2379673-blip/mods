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
        if (!m.skinned.isEmpty()) {
            skin(ps, buffers, light, overlay, r, vanilla);
        }
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

    private final Matrix4f[] mats = {new Matrix4f(), new Matrix4f(), new Matrix4f(), new Matrix4f(), new Matrix4f(), new Matrix4f()};

    /**
     * Ropa 3D: cada vertice es el promedio (con sus pesos) de donde lo llevaria cada parte, como hace
     * Roblox con la ropa en capas. Asi la tela se estira en las articulaciones en vez de partirse.
     */
    private void skin(PoseStack ps, MultiBufferSource buffers, int light, int overlay, ClientAvatars.Ready r, ModelPart[] vanilla) {
        AvatarBuilder.Result m = r.model;
        for (int i = 0; i < 6; i++) {
            ModelPart p = vanilla[i];
            PartPose d = p.getInitialPose();
            float[] j = m.joints[i];
            Matrix4f mt = this.mats[i].identity().translate(j[0] + p.x - d.x, j[1] + p.y - d.y, j[2] + p.z - d.z);
            if (p.xRot != 0 || p.yRot != 0 || p.zRot != 0) {
                mt.rotate(this.q.rotationZYX(p.zRot, p.yRot, p.xRot));
            }
            if (p.xScale != 1 || p.yScale != 1 || p.zScale != 1) {
                mt.scale(p.xScale, p.yScale, p.zScale);
            }
            mt.translate(-j[0], -j[1], -j[2]);
        }
        PoseStack.Pose pose = ps.last();
        Matrix4f pm = pose.pose();
        Matrix3f nm = pose.normal();
        float s = 1 / 16f;
        int st = AvatarBuilder.Batch.SKIN_STRIDE;
        for (AvatarBuilder.Batch b : m.skinned) {
            AvatarBuilder.Material mat = m.materials.get(b.material);
            VertexConsumer vc = buffers.getBuffer(mat.doubleSided ? RenderType.entityCutoutNoCull(r.textures[b.material])
                    : RenderType.entityCutout(r.textures[b.material]));
            float[] dd = b.data;
            for (int t = 0; t < dd.length; t += 3 * st) {
                for (int k = 0; k < 4; k++) {
                    int o = t + Math.min(k, 2) * st;
                    float x = dd[o], y = dd[o + 1], z = dd[o + 2], nx0 = dd[o + 3], ny0 = dd[o + 4], nz0 = dd[o + 5];
                    float px = 0, py = 0, pz = 0, nx = 0, ny = 0, nz = 0;
                    for (int i = 0; i < 6; i++) {
                        float w = dd[o + 8 + i];
                        if (w == 0) {
                            continue;
                        }
                        Matrix4f a = this.mats[i];
                        px += w * (a.m00() * x + a.m10() * y + a.m20() * z + a.m30());
                        py += w * (a.m01() * x + a.m11() * y + a.m21() * z + a.m31());
                        pz += w * (a.m02() * x + a.m12() * y + a.m22() * z + a.m32());
                        nx += w * (a.m00() * nx0 + a.m10() * ny0 + a.m20() * nz0);
                        ny += w * (a.m01() * nx0 + a.m11() * ny0 + a.m21() * nz0);
                        nz += w * (a.m02() * nx0 + a.m12() * ny0 + a.m22() * nz0);
                    }
                    px *= s;
                    py *= s;
                    pz *= s;
                    float qx = pm.m00() * px + pm.m10() * py + pm.m20() * pz + pm.m30();
                    float qy = pm.m01() * px + pm.m11() * py + pm.m21() * pz + pm.m31();
                    float qz = pm.m02() * px + pm.m12() * py + pm.m22() * pz + pm.m32();
                    float rx = nm.m00() * nx + nm.m10() * ny + nm.m20() * nz;
                    float ry = nm.m01() * nx + nm.m11() * ny + nm.m21() * nz;
                    float rz = nm.m02() * nx + nm.m12() * ny + nm.m22() * nz;
                    float l = (float) Math.sqrt(rx * rx + ry * ry + rz * rz) + 1e-6f;
                    vc.addVertex(qx, qy, qz, -1, dd[o + 6], dd[o + 7], overlay, light, rx / l, ry / l, rz / l);
                }
            }
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
