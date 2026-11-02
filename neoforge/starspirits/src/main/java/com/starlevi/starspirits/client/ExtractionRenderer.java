package com.starlevi.starspirits.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starlevi.starspirits.Rarity;
import com.starlevi.starspirits.block.ExtractionBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * La animacion del punto de extraccion: la semilla de luz flota; al extraer sube girando, se abre un
 * anillo en el piso, sale un haz de luz (blanco, y despues del color de la rareza) y el espiritu
 * brota girando desde la semilla.
 */
public class ExtractionRenderer implements BlockEntityRenderer<ExtractionBlockEntity> {
    private static final float CORE_Y = 1.5f;
    private final SpiritRig.Pose pose = new SpiritRig.Pose();

    public ExtractionRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(ExtractionBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        float now = be.getLevel().getGameTime() + partial;
        float idle = now + be.getBlockPos().asLong() % 100;
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        switch (be.phase) {
            case ExtractionBlockEntity.EXTRACTING -> extracting(be, now, ps, buffers);
            case ExtractionBlockEntity.RECHARGING -> {
                float total = Math.max(1, be.rechargeUntil - be.rechargeStart);
                float f = Mth.clamp((now - be.rechargeStart) / total, 0, 1);
                float size = 0.05f + 0.13f * f;
                ps.pushPose();
                ps.translate(0, CORE_Y - 0.1f + Mth.sin(idle * 0.05f) * 0.03f, 0);
                Glow.orb(ps, buffers.getBuffer(Glow.LIGHT), size, idle * 0.02f, 0.55f, 0.8f, 0.5f, 0.25f + 0.35f * f);
                ps.popPose();
            }
            default -> {
                // semilla de luz que flota y late, con un halo y chispitas que orbitan
                float bob = Mth.sin(idle * 0.08f) * 0.06f;
                float pulse = 0.85f + Mth.sin(idle * 0.15f) * 0.15f;
                ps.pushPose();
                ps.translate(0, CORE_Y + bob, 0);
                Glow.orb(ps, buffers.getBuffer(Glow.LIGHT), 0.2f * pulse, idle * 0.03f, 0.75f, 1f, 0.7f, 0.9f);
                Glow.orb(ps, buffers.getBuffer(Glow.LIGHT), 0.36f * pulse, -idle * 0.02f, 0.5f, 0.95f, 0.6f, 0.25f);
                for (int i = 0; i < 3; i++) {
                    float a = idle * 0.06f + i * Mth.TWO_PI / 3;
                    ps.pushPose();
                    ps.translate(Mth.cos(a) * 0.38f, Mth.sin(idle * 0.1f + i) * 0.08f, Mth.sin(a) * 0.38f);
                    Glow.orb(ps, buffers.getBuffer(Glow.LIGHT), 0.05f, idle * 0.1f, 1f, 0.95f, 0.75f, 0.9f);
                    ps.popPose();
                }
                ps.popPose();
                Glow.ring(ps.last(), buffers.getBuffer(Glow.LIGHT), 1.27f, 0.42f, 0.06f, 0.6f, 1f, 0.6f, 0.35f + 0.15f * pulse);
            }
        }
        ps.popPose();
    }

    private void extracting(ExtractionBlockEntity be, float now, PoseStack ps, MultiBufferSource buffers) {
        float d = Math.max(20, be.duration);
        float t = Mth.clamp(now - be.start, 0, d);
        float f = t / d;
        Rarity r = be.rarityEnum();
        // color: blanco y despues el de la rareza (suspenso)
        float c = Mth.clamp((f - 0.3f) / 0.25f, 0, 1);
        float cr = Mth.lerp(c, 1f, r.r()), cg = Mth.lerp(c, 1f, r.g()), cb = Mth.lerp(c, 1f, r.b());
        // temblor
        float shake = f < 0.75f ? Mth.sin(now * 2.7f) * 0.012f * f : 0;
        // anillos en el piso
        for (int k = 0; k < 2; k++) {
            float rf = (f * 1.6f + k * 0.5f) % 1f;
            Glow.ring(ps.last(), buffers.getBuffer(Glow.LIGHT), 0.03f, 0.5f + rf * 1.6f, 0.12f, cr, cg, cb, (1 - rf) * 0.7f * Math.min(1, f * 4));
        }
        // haz de luz
        if (f > 0.2f) {
            float bf = Mth.clamp((f - 0.2f) / 0.3f, 0, 1);
            float flash = f > 0.72f ? 1 + (1 - Math.abs(f - 0.8f) / 0.08f) * 0.6f : 1;
            float fade = f > 0.9f ? (1 - f) / 0.1f : 1;
            ps.pushPose();
            ps.translate(shake, 1.2f, 0);
            float len = 4 + 18 * bf;
            float rad = (0.18f + 0.05f * Mth.sin(now * 0.6f)) * Math.max(1, flash);
            Glow.beam(ps.last(), buffers.getBuffer(Glow.LIGHT), len, rad, rad * 2.2f, cr, cg, cb, 0.85f * bf * Math.max(0.3f, fade));
            ps.popPose();
        }
        // semilla: sube y gira cada vez mas rapido
        float y = CORE_Y + f * 0.45f;
        float spin = t * t * 0.01f;
        if (f < 0.82f) {
            ps.pushPose();
            ps.translate(shake, y, 0);
            float size = 0.2f + f * 0.25f;
            Glow.orb(ps, buffers.getBuffer(Glow.LIGHT), size, spin, cr, cg, cb, 0.95f);
            Glow.orb(ps, buffers.getBuffer(Glow.LIGHT), size * 2.2f, -spin * 0.7f, cr, cg, cb, 0.3f);
            ps.popPose();
        }
        // el espiritu brota girando
        if (f > 0.7f) {
            float g = Mth.clamp((f - 0.7f) / 0.3f, 0, 1);
            float ease = 1 - (1 - g) * (1 - g) * (1 - g);
            this.pose.walk = 0;
            this.pose.walkAmount = 0;
            this.pose.age = now;
            this.pose.headYaw = 0;
            this.pose.headPitch = -10;
            this.pose.happy = g;
            this.pose.sleeping = false;
            this.pose.seed = (int) be.getBlockPos().asLong();
            ps.pushPose();
            ps.translate(0, y - 0.25f + ease * 0.1f, 0);
            SpiritDraw.draw(be.spirit, ps, buffers, LightTexture.FULL_BRIGHT, this.pose, (1 - ease) * 720, Math.max(0.05f, ease));
            ps.popPose();
            if (g < 0.5f) {
                ps.pushPose();
                ps.translate(0, y, 0);
                Glow.orb(ps, buffers.getBuffer(Glow.LIGHT), 0.6f * (1 - g * 2), spin, cr, cg, cb, 0.9f * (1 - g * 2));
                ps.popPose();
            }
        }
    }

    @Override
    public AABB getRenderBoundingBox(ExtractionBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(2, 0, 2).expandTowards(0, 24, 0);
    }

    @Override
    public boolean shouldRenderOffScreen(ExtractionBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
