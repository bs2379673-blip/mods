package com.starlevi.staravatars.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.List;

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
        draw(ps, buffers, light, overlay, r, parts(getParentModel()), player.isCrouching());
    }

    private final Matrix4f[] mats = new Matrix4f[AvatarBuilder.PARTS];
    private final Matrix4f[] full = new Matrix4f[AvatarBuilder.PARTS];
    private final Matrix3f[] fullN = new Matrix3f[AvatarBuilder.PARTS];
    private final Matrix3f rotN = new Matrix3f();

    {
        for (int i = 0; i < this.mats.length; i++) {
            this.mats[i] = new Matrix4f();
            this.full[i] = new Matrix4f();
            this.fullN[i] = new Matrix3f();
        }
    }

    /**
     * Cuanto se dobla la rodilla segun como esta la pierna: al quedar atras (paso), mucho al sentarse
     * (la pantorrilla cuelga) y un poco al agacharse. Positivo = la pantorrilla va para atras.
     */
    static float knee(float legX, boolean crouch) {
        float b = 0.06f + Math.max(0, legX) * 0.45f + Math.max(0, -legX) * 0.12f + Math.max(0, -legX - 0.9f) * 2f;
        if (crouch) {
            b += 0.45f;
        }
        return Math.min(b, 2.2f);
    }

    /** El codo: un poco siempre (como en Roblox), mas al ir el brazo adelante; estirado al apuntar. */
    static float elbow(float armX) {
        float f = Math.max(0, -armX);
        f = f < 0.8f ? f : Math.max(0, 1.6f - f);
        return -(0.12f + 0.5f * f);
    }

    /** Tambien lo usa el menu (vista del personaje sin jugador). */
    void draw(PoseStack ps, MultiBufferSource buffers, int light, int overlay, ClientAvatars.Ready r, ModelPart[] vanilla, boolean crouch) {
        AvatarBuilder.Result m = r.model;
        // cada parte del jugador, desde la articulacion del personaje (en pixeles, posicion absoluta del modelo)
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
        // antebrazos y pantorrillas: siguen al brazo o la pierna y se doblan en el codo o la rodilla
        for (int c = AvatarBuilder.RIGHT_FOREARM; c < AvatarBuilder.PARTS; c++) {
            int parent = c - 4;
            float x = vanilla[parent].xRot;
            float bend = c <= AvatarBuilder.LEFT_FOREARM ? elbow(x) : knee(x, crouch);
            float[] k = m.joints[c];
            this.mats[c].set(this.mats[parent]).translate(k[0], k[1], k[2]).rotateX(bend).translate(-k[0], -k[1], -k[2]);
        }
        PoseStack.Pose pose = ps.last();
        for (int i = 0; i < AvatarBuilder.PARTS; i++) {
            this.full[i].set(pose.pose()).scale(1 / 16f).mul(this.mats[i]);
            this.fullN[i].set(pose.normal()).mul(this.rotN.set(this.mats[i]));
        }
        // una textura a la vez: cada cambio de textura es un dibujo aparte para la placa de video
        for (int mi = 0; mi < m.byMaterial.size(); mi++) {
            List<AvatarBuilder.Batch> list = m.byMaterial.get(mi);
            if (list.isEmpty()) {
                continue;
            }
            VertexConsumer vc = buffers.getBuffer(AvatarRenderTypes.cutout(r.textures[mi], m.materials.get(mi).doubleSided));
            for (AvatarBuilder.Batch b : list) {
                if (b.part >= 0) {
                    emit(vc, this.full[b.part], this.fullN[b.part], b.data, light, overlay);
                } else {
                    seams(vc, b.data, light, overlay);
                }
            }
        }
    }

    /** Uniones de la ropa 3D: cada vertice va con su parte; el triangulo se estira entre las dos. */
    private void seams(VertexConsumer vc, float[] d, int light, int overlay) {
        int st = AvatarBuilder.Batch.SEAM_STRIDE;
        for (int o = 0; o < d.length; o += st) {
            int part = (int) d[o + 8];
            Matrix4f mat = this.full[part];
            Matrix3f nm = this.fullN[part];
            float x = d[o], y = d[o + 1], z = d[o + 2], nx = d[o + 3], ny = d[o + 4], nz = d[o + 5];
            float qx = nm.m00() * nx + nm.m10() * ny + nm.m20() * nz;
            float qy = nm.m01() * nx + nm.m11() * ny + nm.m21() * nz;
            float qz = nm.m02() * nx + nm.m12() * ny + nm.m22() * nz;
            float l = (float) Math.sqrt(qx * qx + qy * qy + qz * qz) + 1e-6f;
            vc.addVertex(mat.m00() * x + mat.m10() * y + mat.m20() * z + mat.m30(), mat.m01() * x + mat.m11() * y + mat.m21() * z + mat.m31(),
                    mat.m02() * x + mat.m12() * y + mat.m22() * z + mat.m32(), -1, d[o + 6], d[o + 7], overlay, light, qx / l, qy / l, qz / l);
        }
    }

    /** Posiciones en pixeles del modelo: mat ya incluye la escala a bloques. */
    private static void emit(VertexConsumer vc, Matrix4f mat, Matrix3f nm, float[] d, int light, int overlay) {
        for (int o = 0; o < d.length; o += 8) {
            float x = d[o], y = d[o + 1], z = d[o + 2];
            float px = mat.m00() * x + mat.m10() * y + mat.m20() * z + mat.m30();
            float py = mat.m01() * x + mat.m11() * y + mat.m21() * z + mat.m31();
            float pz = mat.m02() * x + mat.m12() * y + mat.m22() * z + mat.m32();
            float nx = d[o + 3], ny = d[o + 4], nz = d[o + 5];
            float qx = nm.m00() * nx + nm.m10() * ny + nm.m20() * nz;
            float qy = nm.m01() * nx + nm.m11() * ny + nm.m21() * nz;
            float qz = nm.m02() * nx + nm.m12() * ny + nm.m22() * nz;
            float l = (float) Math.sqrt(qx * qx + qy * qy + qz * qz) + 1e-6f;
            vc.addVertex(px, py, pz, -1, d[o + 6], d[o + 7], overlay, light, qx / l, qy / l, qz / l);
        }
    }
}
