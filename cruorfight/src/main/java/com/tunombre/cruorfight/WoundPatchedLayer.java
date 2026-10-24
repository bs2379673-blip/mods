package com.tunombre.cruorfight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.client.model.Mesh;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec3f;
import yesman.epicfight.client.renderer.EpicFightRenderTypes;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.client.renderer.patched.layer.RenderOriginalModelLayer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.lang.reflect.Method;

/**
 * Sustituye a la capa "modelo vanilla pegado a la cadera" que Epic Fight usa por defecto con la WoundLayer de Cruor.
 * 1) Deja que Cruor siga pintando/actualizando sus heridas como siempre (se ejecuta su capa, pero su dibujo se descarta).
 * 2) Dibuja la textura de heridas de Cruor sobre la malla animada de Epic Fight.
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
    private static final MultiBufferSource DISCARD = type -> NULL;

    private final PatchedLivingEntityRenderer renderer;
    private final RenderOriginalModelLayer painter;

    public WoundPatchedLayer(PatchedLivingEntityRenderer renderer) {
        this.renderer = renderer;
        this.painter = new RenderOriginalModelLayer("Root", new Vec3f(0.0F, heightCorrection(renderer), 0.0F), new Vec3f(0.0F, 0.0F, 0.0F));
    }

    private static float heightCorrection(Object renderer) {
        for (Class<?> c = renderer.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod("getDefaultLayerHeightCorrection");
                m.setAccessible(true);
                return (float) m.invoke(renderer);
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable t) {
                break;
            }
        }
        return 1.15F;
    }

    @Override
    public void renderLayer(LivingEntity entity, LivingEntityPatch patch, RenderLayer vanillaLayer, PoseStack poseStack, MultiBufferSource buffer,
                            int light, OpenMatrix4f[] poses, float bob, float yRot, float xRot, float partialTicks) {
        if (vanillaLayer == null) return;
        // 1) la capa original de Cruor: pinta heridas nuevas y actualiza la textura; lo que dibuja se tira
        painter.renderLayer(entity, patch, vanillaLayer, poseStack, DISCARD, light, poses, bob, yRot, xRot, partialTicks);
        // 2) dibujar la textura de heridas sobre la malla de Epic Fight
        if (entity.isInvisible() || !CruorAccess.woundsEnabled()) return;
        CruorAccess.SkinInfo skin = CruorAccess.skin(entity);
        if (skin == null) return;
        Object mesh = renderer.getMeshProvider(patch).get();
        if (!(mesh instanceof SkinnedMesh sm)) return;
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
