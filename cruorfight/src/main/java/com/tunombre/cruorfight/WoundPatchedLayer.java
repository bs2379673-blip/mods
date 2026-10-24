package com.tunombre.cruorfight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.client.model.Mesh;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.EpicFightRenderTypes;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;


/**
 * Sustituye a la capa "modelo vanilla pegado a la cadera" que Epic Fight usa por defecto con la WoundLayer de Cruor.
 * Hace lo mismo que la WoundLayer de Cruor, pero con la malla animada de Epic Fight: la pinta (captura de geometria)
 * y la dibuja con la pose real del cuerpo.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class WoundPatchedLayer extends PatchedLayer {
    private static final VertexConsumer NULL = new VertexConsumer() {
        @Override public VertexConsumer vertex(double x, double y, double z) { return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() { }
        @Override public void defaultColor(int r, int g, int b, int a) { }
        @Override public void unsetDefaultColor() { }
    };

    private final PatchedLivingEntityRenderer renderer;

    public WoundPatchedLayer(PatchedLivingEntityRenderer renderer) {
        this.renderer = renderer;
    }

    // ---- geometria de la malla de Epic Fight (en el mismo espacio que la capturaba Cruor con el modelo vanilla)
    private float[] gp = new float[4096 * 3], gu = new float[4096 * 2];
    private int gn;

    private void collect(SkinnedMesh sm, PoseStack ps, LivingEntityPatch patch, OpenMatrix4f[] poses) {
        gn = 0;
        Mesh.DrawingFunction fn = (b, x, y, z, nx, ny, nz, light, r, g, bl, a, u, v, overlay) -> {
            if (gn * 3 + 3 > gp.length) { gp = java.util.Arrays.copyOf(gp, gp.length * 2); gu = java.util.Arrays.copyOf(gu, gu.length * 2); }
            gp[gn * 3] = x; gp[gn * 3 + 1] = y; gp[gn * 3 + 2] = z;
            gu[gn * 2] = u; gu[gn * 2 + 1] = v;
            gn++;
        };
        sm.drawPosed(ps, NULL, fn, 0, 1F, 1F, 1F, 1F, 0, patch.getArmature(), poses);
    }

    private boolean same(int i, int j) {
        return Math.abs(gp[i * 3] - gp[j * 3]) < 1e-4F && Math.abs(gp[i * 3 + 1] - gp[j * 3 + 1]) < 1e-4F && Math.abs(gp[i * 3 + 2] - gp[j * 3 + 2]) < 1e-4F
                && Math.abs(gu[i * 2] - gu[j * 2]) < 1e-6F && Math.abs(gu[i * 2 + 1] - gu[j * 2 + 1]) < 1e-6F;
    }

    private void emit(VertexConsumer c, int i) {
        c.vertex(gp[i * 3], gp[i * 3 + 1], gp[i * 3 + 2], 1F, 1F, 1F, 1F, gu[i * 2], gu[i * 2 + 1], 0, 0, 0F, 1F, 0F);
    }

    /** pasa la malla (triangulos) al capturador de Cruor (cuadrilateros): une pares de triangulos que comparten una diagonal */
    private void feed(VertexConsumer c) {
        int i = 0;
        while (i + 2 < gn) {
            if (i + 5 < gn) {
                int ua = -1, ub = -1, ma = 0, mb = 0;
                for (int k = 0; k < 3; k++) {
                    boolean found = same(i + k, i + 3) || same(i + k, i + 4) || same(i + k, i + 5);
                    if (found) ma++; else ua = k;
                    boolean foundB = same(i + 3 + k, i) || same(i + 3 + k, i + 1) || same(i + 3 + k, i + 2);
                    if (foundB) mb++; else ub = k;
                }
                if (ma == 2 && mb == 2 && ua >= 0 && ub >= 0) {
                    emit(c, i + ua);
                    emit(c, i + (ua + 1) % 3);
                    emit(c, i + 3 + ub);
                    emit(c, i + (ua + 2) % 3);
                    i += 6;
                    continue;
                }
            }
            emit(c, i); emit(c, i + 1); emit(c, i + 2); emit(c, i + 2);
            i += 3;
        }
    }

    @Override
    public void renderLayer(LivingEntity entity, LivingEntityPatch patch, RenderLayer vanillaLayer, PoseStack poseStack, MultiBufferSource buffer,
                            int light, OpenMatrix4f[] poses, float bob, float yRot, float xRot, float partialTicks) {
        if (vanillaLayer == null || !CruorAccess.available() || entity.isInvisible() || !CruorAccess.woundsEnabled()) return;
        Object body = CruorAccess.body(entity);
        if (body == null) return;
        Object mesh = renderer.getMeshProvider(patch).get();
        if (!(mesh instanceof SkinnedMesh sm)) return;
        if (!CruorAccess.pending(body) || CruorAccess.place(body, entity, Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity).getTextureLocation(entity), partialTicks, () -> {
            collect(sm, poseStack, patch, poses);
            Object capture = CruorAccess.captureConsumer();
            feed((VertexConsumer) capture);
        })) {
            CruorAccess.settle(body);
        }
        CruorAccess.SkinInfo skin = CruorAccess.skin(body);
        if (skin == null) return;
        final float copies = skin.copies();
        Mesh.DrawingFunction fn = (b, x, y, z, nx, ny, nz, packedLight, r, g, bl, a, u, v, overlay) ->
                b.vertex(x + nx * 0.004F, y + ny * 0.004F, z + nz * 0.004F, r, g, bl, a, u, v / copies, overlay, packedLight, nx, ny, nz);
        VertexConsumer vc = buffer.getBuffer(EpicFightRenderTypes.getTriangulated(RenderType.entityTranslucent(skin.location())));
        sm.drawPosed(poseStack, vc, fn, light, 1.0F, 1.0F, 1.0F, 1.0F, LivingEntityRenderer.getOverlayCoords(entity, 0.0F), patch.getArmature(), poses);
    }

    @Override
    protected void renderLayer(LivingEntityPatch patch, LivingEntity entity, RenderLayer vanillaLayer, PoseStack poseStack, MultiBufferSource buffer,
                               int light, OpenMatrix4f[] poses, float bob, float yRot, float xRot, float partialTicks) {
        renderLayer(entity, patch, vanillaLayer, poseStack, buffer, light, poses, bob, yRot, xRot, partialTicks);
    }
}
