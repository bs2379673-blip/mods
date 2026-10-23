package com.tunombre.jefes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tunombre.jefes.Boss;
import com.tunombre.jefes.JefeEntity;
import com.tunombre.jefes.core.Emit;
import com.tunombre.jefes.core.Mat4;
import com.tunombre.jefes.core.Pose;
import com.tunombre.jefes.core.Rig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.WeakHashMap;

/** Dibuja el jefe cubo por cubo con la animacion de {@link Rig} (la misma formula que usa el servidor para la mano). */
public class JefeRenderer extends EntityRenderer<JefeEntity> {
    private static final ResourceLocation NONE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private final Map<JefeEntity, Rig.State> states = new WeakHashMap<>();

    public JefeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 1.4F;
    }

    @Override
    public void render(JefeEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        Boss b = e.getBoss();
        if (b == null) return;
        if ("slender".equals(b.spec.kind)) { SlenderRender.render(e, pt, ps, buf, light); return; }
        Pose pose = e.rig(b.model);
        Rig.State st = states.computeIfAbsent(e, k -> new Rig.State());
        Rig.In in = new Rig.In();
        in.timeMs = (e.tickCount + pt) * 50.0;
        in.moveSpeed = (float) Math.hypot(e.getX() - e.xo, e.getZ() - e.zo);
        in.sprint = e.isSprinting2();
        in.crouch = e.isCrouching2();
        in.onGround = e.onGround();
        float body = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        in.headYaw = Mth.wrapDegrees(Mth.rotLerp(pt, e.yHeadRotO, e.yHeadRot) - body);
        in.act = e.getAct();
        in.actT = e.actTime(pt);
        in.struggle = e.struggle();
        in.turn = Mth.wrapDegrees(e.getYRot() - e.yRotO);
        Rig.pose(b.model, b.spec, pose, st, in);
        Mat4[] w = pose.world(Rig.root(b.model, b.spec.scale, body));
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(ClientBosses.texture(b)));
        PoseStack.Pose last = ps.last();
        Matrix4f pm = last.pose();
        Matrix3f nm = last.normal();
        Emit.quads(b.model, w, (x, y, z, u, v, nx, ny, nz) ->
                vc.vertex(pm, x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nm, nx, ny, nz).endVertex());
    }

    @Override
    public ResourceLocation getTextureLocation(JefeEntity e) {
        Boss b = e.getBoss();
        return b == null ? NONE : ClientBosses.texture(b);
    }
}
