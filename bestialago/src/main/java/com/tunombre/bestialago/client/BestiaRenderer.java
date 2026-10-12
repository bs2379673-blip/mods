package com.tunombre.bestialago.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tunombre.bestialago.Bestia;
import com.tunombre.bestialago.BestiaAnim;
import com.tunombre.bestialago.BestiaLago;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Dibuja la Bestia con la misma transformacion que usa BestiaAnim para calcular la boca. Ojos que brillan en la oscuridad. */
public class BestiaRenderer extends EntityRenderer<Bestia> {
    public static final ResourceLocation TEX = new ResourceLocation(BestiaLago.MODID, "textures/entity/bestia.png");
    public static final ResourceLocation EYES = new ResourceLocation(BestiaLago.MODID, "textures/entity/bestia_eyes.png");
    private final BestiaModel model;

    public BestiaRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new BestiaModel(ctx.bakeLayer(ClientSetup.LAYER));
        this.shadowRadius = 0F;
    }

    @Override
    public void render(Bestia e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        ps.pushPose();
        float y = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float p = Mth.lerp(pt, e.xRotO, e.getXRot());
        ps.translate(0, BestiaAnim.CENTER_H, 0);
        ps.mulPose(Axis.YP.rotationDegrees(180F - y));
        ps.mulPose(Axis.XP.rotationDegrees(-p));
        ps.scale(-BestiaAnim.SCALE, -BestiaAnim.SCALE, BestiaAnim.SCALE);
        model.setupAnim(e, 0, 0, e.tickCount + pt, 0, 0);
        model.renderToBuffer(ps, buf.getBuffer(RenderType.entityCutoutNoCull(TEX)), light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        model.renderToBuffer(ps, buf.getBuffer(RenderType.eyes(EYES)), 0xF000F0, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(Bestia e) { return TEX; }
}
