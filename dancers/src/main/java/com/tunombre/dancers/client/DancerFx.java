package com.tunombre.dancers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tunombre.dancers.DancerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Efectos de luz de la entrada y la salida: estrella que late, anillos que se cierran, destello,
 * onda de choque, pilar de luz y rayos. Todo con texturas aditivas propias (no depende de otros mods).
 */
final class DancerFx {
    private static final ResourceLocation FLARE = new ResourceLocation("dancers", "textures/fx/flare.png");
    private static final ResourceLocation RING = new ResourceLocation("dancers", "textures/fx/ring.png");
    private static final ResourceLocation BEAM = new ResourceLocation("dancers", "textures/fx/beam.png");
    private static final int FULL = LightTexture.FULL_BRIGHT;

    private DancerFx() {}

    static void render(DancerEntity e, float pt, PoseStack ps, MultiBufferSource buf, EntityRenderDispatcher disp) {
        long now = e.level().getGameTime();
        long v = e.getVanishStart(), a = e.getAppearStart();
        double t, flashT; boolean vanishing = v > 0;
        if (vanishing) { t = now - v + pt; flashT = DancerEntity.VANISH_TICKS * 0.75; }
        else if (a > 0) { t = now - a + pt; flashT = DancerEntity.APPEAR_TICKS * 0.4; }
        else return;
        double dur = 20;
        if (t < 0 || t > flashT + dur) return;
        float s = e.getDancerScale();
        float cy = 0.95f * s;
        double f = t - flashT;   // ticks desde el destello (negativo = anticipacion)
        Minecraft mc = Minecraft.getInstance();
        float camYaw = mc.gameRenderer.getMainCamera().getYRot();

        if (f < 0) {
            double u = t / flashT;
            // anillos de suelo que se cierran hacia la figura
            for (int j = 0; j < 3; j++) {
                double ph = (u * 2.2 + j / 3.0) % 1.0;
                float rad = (float) ((2.3 * (1 - ph) + 0.25) * s);
                ps.pushPose(); ps.translate(0, 0.03 * s, 0); ps.mulPose(Axis.XP.rotationDegrees(-90));
                quad(ps, buf, RING, rad, rad, 0, 0.5f, 1f, 0.95f, (float) (ph * 0.85 * (0.4 + 0.6 * u)));
                ps.popPose();
            }
            // la estrella late y crece
            float pulse = (float) (1 + 0.15 * Math.sin(t * 1.4));
            float r = (float) ((0.12 + 0.75 * u * u) * s) * pulse;
            ps.pushPose(); ps.translate(0, cy, 0); ps.mulPose(disp.cameraOrientation());
            quad(ps, buf, FLARE, r * 1.6f, r * 1.6f, 0, 0, 0.8f, 1f, (float) Math.min(1, 0.45 + 0.7 * u));
            ps.mulPose(Axis.ZP.rotationDegrees((float) (t * 6)));
            quad(ps, buf, FLARE, r, r, 0, 0, 1f, 1f, (float) Math.min(1, 0.4 + 0.6 * u));
            ps.popPose();
            return;
        }

        double p = Math.min(1, f / dur), q = 1 - p;
        float qa = (float) (q * q);
        // destello central
        ps.pushPose(); ps.translate(0, cy, 0); ps.mulPose(disp.cameraOrientation());
        float big = (float) ((2.2 + 3.0 * p) * s);
        quad(ps, buf, FLARE, big, big, 0, 0, 0.9f, 1f, qa);
        ps.mulPose(Axis.ZP.rotationDegrees((float) (20 + f * 5)));
        quad(ps, buf, FLARE, big * 0.7f, big * 0.7f, 0, 0, 1f, 1f, (float) q);
        // rayos que salen hacia afuera
        float rayAlpha = (float) (q * q * 0.9);
        for (int i = 0; i < 12; i++) {
            ps.pushPose();
            ps.mulPose(Axis.ZP.rotationDegrees(i * 30f + (i % 2 == 0 ? 8f : -5f)));
            float len = (float) ((1.2 + 3.2 * Math.sqrt(p)) * s * (0.7 + 0.3 * ((i * 7) % 5) / 4.0));
            quad(ps, buf, BEAM, 0.09f * s * (float) (1 + p), len, 0, len, 0.8f, 1f, rayAlpha);   // desplazado para que nazca en el centro
            ps.popPose();
        }
        // onda de choque de cara a la camara
        float sw = (float) (Math.sqrt(p) * 3.4 * s);
        quad(ps, buf, RING, sw, sw, 0, 0, 0.85f, 1f, (float) q);
        ps.popPose();
        // onda de choque en el suelo
        float gw = (float) (Math.pow(p, 0.6) * 4.2 * s);
        ps.pushPose(); ps.translate(0, 0.03 * s, 0); ps.mulPose(Axis.XP.rotationDegrees(-90));
        quad(ps, buf, RING, gw, gw, 0, 0, 0.9f, 1f, (float) q);
        quad(ps, buf, RING, gw * 0.55f, gw * 0.55f, 0, 0, 0.8f, 1f, (float) (q * 0.7));
        ps.popPose();
        // pilar de luz
        ps.pushPose(); ps.translate(0, 0, 0); ps.mulPose(Axis.YP.rotationDegrees(-camYaw + 180f));
        float pw = (float) ((0.55 + 0.5 * p) * s), ph2 = 3.2f * s;
        quad(ps, buf, BEAM, pw, ph2, 0, ph2, 0.85f, 1f, (float) Math.pow(q, 1.2));
        ps.popPose();
    }

    /**
     * Dibuja un rectangulo aditivo en el plano XY local: medio ancho hw, medio alto hh, centrado en (ox, oy).
     */
    private static void quad(PoseStack ps, MultiBufferSource buf, ResourceLocation tex, float hw, float hh, float ox, float oy,
                             float tr, float tg, float tb, float alpha) {
        if (alpha <= 0.003f) return;
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(tex));
        Matrix4f pm = ps.last().pose(); Matrix3f nm = ps.last().normal();
        int r = (int) (255 * Math.min(1, tr + 0.5)), g = (int) (255 * Math.min(1, tg + 0.5)), b = (int) (255 * Math.min(1, tb + 0.5));
        int al = (int) (255 * Math.min(1f, alpha));
        float[][] c = {{-hw, -hh, 0, 1}, {hw, -hh, 1, 1}, {hw, hh, 1, 0}, {-hw, hh, 0, 0}};
        for (float[] k : c)
            vc.vertex(pm, ox + k[0], oy + k[1], 0f).color(r, g, b, al).uv(k[2], k[3]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL).normal(nm, 0f, 0f, 1f).endVertex();
    }
}
