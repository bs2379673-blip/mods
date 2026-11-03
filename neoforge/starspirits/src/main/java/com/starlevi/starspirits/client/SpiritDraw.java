package com.starlevi.starspirits.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Dibuja un espiritu (en el mundo, en la maceta o saliendo del punto de extraccion). */
public final class SpiritDraw {
    /** Escala base: el modelo mide ~15 pixeles y queda como un bichito de media cuadra. */
    public static final float BASE = 0.95f;

    private SpiritDraw() {
    }

    /**
     * Con el origen en los pies: gira "yaw" grados, escala y dibuja. Devuelve la altura de la cabeza
     * en bloques (para poner cosas encima).
     */
    public static float draw(String id, PoseStack ps, MultiBufferSource buffers, int light, SpiritRig.Pose pose,
                             float yaw, float scale) {
        SpiritRig rig = ClientDefs.rig(id);
        com.starlevi.starspirits.SpiritDef def = ClientDefs.def(id);
        pose.specials = def == null ? java.util.List.of() : def.specials;
        pose.wings = def == null ? java.util.List.of() : def.wings;
        rig.animate(pose);
        float s = BASE * scale * ClientDefs.scale(id);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180 - (yaw + ClientDefs.turn(id))));
        ps.scale(-s, -s, s);
        ps.translate(0, -1.501f, 0);
        ResourceLocation tex = ClientDefs.texture(id);
        // parpadeo: cada tanto (a veces doble) cierra los ojos; dormido los tiene cerrados
        ResourceLocation closed = ClientDefs.blink(id);
        if (closed != null) {
            float cyc = (pose.age + pose.seed * 29) % 95;
            boolean blink = pose.sleeping || cyc < 3.2f || (pose.seed % 3 == 0 && cyc > 8 && cyc < 11);
            if (blink) {
                tex = closed;
            }
        }
        if (tex != null) {
            rig.render(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        }
        ResourceLocation glow = ClientDefs.glow(id);
        if (glow != null) {
            rig.render(ps, buffers.getBuffer(RenderType.eyes(glow)), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        }
        ps.popPose();
        return rig.topY / 16f * s;
    }
}
