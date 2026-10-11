package com.tunombre.dancers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tunombre.dancers.DancerEntity;
import com.tunombre.dancers.rig.Rig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class DancerRenderer extends EntityRenderer<DancerEntity> {
    private static final ResourceLocation FALLBACK = new ResourceLocation("minecraft", "textures/entity/steve.png");

    private static final class Holder { Rig.Model model; Rig.SpringState st; long last; float[] pos, nrm; }

    public DancerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.4f;
    }

    @Override
    public ResourceLocation getTextureLocation(DancerEntity e) { return FALLBACK; }

    @Override
    public void render(DancerEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float scale = e.getDancerScale();
        this.shadowRadius = 0.4f * scale;
        super.render(e, yaw, pt, ps, buf, light);
        Rig.Skeleton sk = DancerAssets.skeleton();
        Rig.Model m = DancerAssets.model(e.getModelName());
        if (sk == null || m == null) return;

        Rig.Anim anim = null; double time = 0;
        String id = e.getAnim();
        if (!id.isEmpty()) {
            anim = DancerAssets.anim(id);
            if (anim != null) {
                time = ((e.level().getGameTime() - e.getStart()) + pt) / 20.0;
                if (time < 0) time = 0;
                if (e.isLooping() && anim.duration > 0) time %= anim.duration;
                else if (time > anim.duration) anim = null;
            }
        }
        Holder h = e.clientSprings instanceof Holder hh ? hh : null;
        long now = System.nanoTime();
        if (h == null || h.model != m) { h = new Holder(); h.model = m; h.st = new Rig.SpringState(m); h.last = now; e.clientSprings = h; }
        double dt = Math.min(0.1, (now - h.last) / 1e9); h.last = now;

        int nb = m.boneCount();
        double[] R = new double[nb * 9], T = new double[nb * 3];
        Rig.pose(m, sk, anim, time, e.getTravel(), h.st, dt, R, T);

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot)));
        ps.scale(scale, scale, scale);
        PoseStack.Pose pose = ps.last();
        Matrix4f pm = pose.pose(); Matrix3f nm = pose.normal();
        ResourceLocation[] tex = (ResourceLocation[]) m.userData;
        for (int t = 0; t < tex.length; t++) {
            VertexConsumer vc = null;
            for (Rig.Model.Part p : m.parts) {
                if (p.tex != t) continue;
                if (vc == null) vc = buf.getBuffer(RenderType.entityCutoutNoCull(tex[t]));
                int b = p.bone, r = b * 9, o = b * 3;
                float[] v = p.v;
                for (int i = 0; i < v.length; i += 8) {
                    double x = v[i], y = v[i + 1], z = v[i + 2];
                    double px = R[r] * x + R[r + 1] * y + R[r + 2] * z + T[o];
                    double py = R[r + 3] * x + R[r + 4] * y + R[r + 5] * z + T[o + 1];
                    double pz = R[r + 6] * x + R[r + 7] * y + R[r + 8] * z + T[o + 2];
                    double nx0 = v[i + 5], ny0 = v[i + 6], nz0 = v[i + 7];
                    double nx = R[r] * nx0 + R[r + 1] * ny0 + R[r + 2] * nz0;
                    double ny = R[r + 3] * nx0 + R[r + 4] * ny0 + R[r + 5] * nz0;
                    double nz = R[r + 6] * nx0 + R[r + 7] * ny0 + R[r + 8] * nz0;
                    // espacio de Epic Fight (X derecha, Y frente, Z arriba) -> Minecraft (X izquierda, Y arriba, Z frente)
                    vc.vertex(pm, (float) -px, (float) pz, (float) py).color(255, 255, 255, 255).uv(v[i + 3], v[i + 4])
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nm, (float) -nx, (float) nz, (float) ny).endVertex();
                }
            }
        }
        if (m.skin != null) {
            Rig.Skin k = m.skin;
            int n = k.count();
            if (h.pos == null || h.pos.length != n * 3) { h.pos = new float[n * 3]; h.nrm = new float[n * 3]; }
            Rig.skin(k, R, T, h.pos, h.nrm);
            float[] pp = h.pos, nn = h.nrm;
            for (int g = 0; g < k.groupTex.length; g++) {
                VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(tex[k.groupTex[g]]));
                int end = (k.groupStart[g] + k.groupCount[g]) * 3;
                for (int ti = k.groupStart[g] * 3; ti < end; ti += 3) {
                    for (int c = 0; c < 4; c++) {
                        int vi = k.tris[ti + (c < 3 ? c : 2)];
                        // Epic Fight (X derecha, Y frente, Z arriba) -> Minecraft (X izquierda, Y arriba, Z frente)
                        vc.vertex(pm, -pp[vi * 3], pp[vi * 3 + 2], pp[vi * 3 + 1]).color(255, 255, 255, 255).uv(k.v[vi * 8 + 3], k.v[vi * 8 + 4])
                                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nm, -nn[vi * 3], nn[vi * 3 + 2], nn[vi * 3 + 1]).endVertex();
                    }
                }
            }
        }
        ps.popPose();
    }
}
