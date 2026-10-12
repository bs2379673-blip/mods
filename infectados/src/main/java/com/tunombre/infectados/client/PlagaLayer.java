package com.tunombre.infectados.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tunombre.infectados.mob.Ganado;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.utils.math.MathUtils;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/** Dibuja la plaga pegada al hueso de la cabeza (indice 9) de los aldeanos sin cabeza. */
@SuppressWarnings({"rawtypes"})
public class PlagaLayer extends PatchedLayer {
    private PlagaModel model;

    @Override
    protected void renderLayer(LivingEntityPatch patch, LivingEntity e, RenderLayer layer, PoseStack ps, MultiBufferSource buf, int light, OpenMatrix4f[] poses, float a, float b, float c, float d) {
        if (!(e instanceof Ganado g) || !g.headless() || poses.length <= 9) return;
        if (model == null) model = new PlagaModel(Minecraft.getInstance().getEntityModels().bakeLayer(ClientSetup.PLAGA_LAYER));
        float pt = Minecraft.getInstance().getFrameTime();
        float t = e.tickCount + pt;
        model.pose(t, t - g.plagaAttackStart());
        ps.pushPose();
        MathUtils.mulStack(ps, poses[9]);
        model.render(ps, buf.getBuffer(RenderType.entityCutoutNoCull(InfVanillaRenderer.tex("plaga"))), light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }
}
