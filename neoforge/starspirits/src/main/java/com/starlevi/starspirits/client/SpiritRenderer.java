package com.starlevi.starspirits.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starlevi.starspirits.SpiritEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Dibuja los espiritus que te siguen; el activo lleva una estrellita del color de su rareza. */
public class SpiritRenderer extends EntityRenderer<SpiritEntity> {
    private final SpiritRig.Pose pose = new SpiritRig.Pose();

    public SpiritRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.25f;
    }

    @Override
    public void render(SpiritEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        float headYaw = Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot);
        SpiritRig.Pose p = this.pose;
        p.walk = e.walkAnimation.position(partial);
        p.walkAmount = Math.min(1f, e.walkAnimation.speed(partial));
        p.age = e.tickCount + partial;
        p.headYaw = Mth.wrapDegrees(headYaw - bodyYaw);
        p.headPitch = Mth.lerp(partial, e.xRotO, e.getXRot());
        p.happy = e.happyProgress(partial);
        p.sleeping = false;
        p.seed = e.getId();
        String type = e.spiritType();
        float top = SpiritDraw.draw(type, ps, buffers, light, p, bodyYaw, 1f);
        if (e.isActiveSpirit()) {
            int c = ClientDefs.tint(type);
            float age = e.tickCount + partial;
            ps.pushPose();
            ps.translate(0, top + 0.22f + Mth.sin(age * 0.12f) * 0.04f, 0);
            ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
            ps.mulPose(Axis.ZP.rotation(age * 0.05f));
            Glow.star(ps.last(), buffers.getBuffer(Glow.LIGHT), 0.13f + Mth.sin(age * 0.2f) * 0.015f,
                    (c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, 0.95f);
            ps.popPose();
        }
        super.render(e, yaw, partial, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SpiritEntity e) {
        return ClientDefs.texture(e.spiritType());
    }
}
