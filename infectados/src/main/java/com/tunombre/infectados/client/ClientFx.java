package com.tunombre.infectados.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tunombre.infectados.Infectados;
import com.tunombre.infectados.mob.Motosierra;
import com.tunombre.infectados.mob.Smoker;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;

/** Lengua del Smoker (cinta rosa desde la boca hasta la victima) y el motor de la motosierra sonando en bucle. */
public final class ClientFx {
    private static final Set<Integer> SAWS = new HashSet<>();

    private ClientFx() { }

    static void renderStage(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Camera cam = e.getCamera();
        Vec3 cp = cam.getPosition();
        float pt = e.getPartialTick();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        PoseStack ps = e.getPoseStack();
        boolean any = false;
        for (Entity ent : mc.level.entitiesForRendering()) {
            if (!(ent instanceof Smoker s) || s.tongueTarget() < 0) continue;
            Entity v = mc.level.getEntity(s.tongueTarget());
            if (v == null) continue;
            any = true;
            Vec3 a = new Vec3(s.xOld, s.yOld, s.zOld).lerp(s.position(), pt).add(0, 1.62, 0)
                    .add(Vec3.directionFromRotation(0, s.getViewYRot(pt)).scale(0.3));
            Vec3 b = new Vec3(v.xOld, v.yOld, v.zOld).lerp(v.position(), pt).add(0, v.getBbHeight() * 0.62, 0);
            tongue(ps, buf.getBuffer(RenderType.entityCutoutNoCull(InfVanillaRenderer.tex("lengua"))), a.subtract(cp), b.subtract(cp), cp.subtract(a), LevelRenderer.getLightColor(mc.level, BlockPos.containing(a)), s.tickCount + pt);
        }
        if (any) buf.endBatch(RenderType.entityCutoutNoCull(InfVanillaRenderer.tex("lengua")));
    }

    /** cinta de varios tramos que mira a la camara, con una pequena curva que tiembla */
    private static void tongue(PoseStack ps, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 toCam, int light, float t) {
        Matrix4f m = ps.last().pose();
        Vec3 d = b.subtract(a);
        int n = Math.max(6, (int) (d.length() * 2));
        Vec3 side = d.cross(toCam).normalize().scale(0.06);
        if (side.lengthSqr() < 1e-6) side = new Vec3(0.06, 0, 0);
        Vec3 prev = a;
        for (int i = 1; i <= n; i++) {
            double k = i / (double) n;
            double sag = Math.sin(k * Math.PI) * (0.25 + 0.05 * Math.sin(t * 0.8 + k * 6));
            Vec3 p = a.add(d.scale(k)).add(0, -sag, 0);
            float v0 = (float) ((i - 1) / (double) n * 4), v1 = (float) (k * 4);
            v(vc, m, prev.subtract(side), 0, v0, light);
            v(vc, m, prev.add(side), 1, v0, light);
            v(vc, m, p.add(side), 1, v1, light);
            v(vc, m, p.subtract(side), 0, v1, light);
            prev = p;
        }
    }

    private static void v(VertexConsumer vc, Matrix4f m, Vec3 p, float u, float v, int light) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 1, 0).endVertex();
    }

    static void clientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { SAWS.clear(); return; }
        for (Entity ent : mc.level.entitiesForRendering()) {
            if (ent instanceof Motosierra m && m.isAlive() && m.distanceToSqr(mc.player) < 40 * 40 && SAWS.add(m.getId())) {
                mc.getSoundManager().play(new Motor(m));
            }
        }
    }

    /** el motor suena mientras el de la motosierra este vivo y cerca; acelera cuando ataca */
    static class Motor extends AbstractTickableSoundInstance {
        private final Motosierra m;

        Motor(Motosierra m) {
            super(Infectados.CHAINSAW_LOOP.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
            this.m = m;
            this.looping = true;
            this.delay = 0;
            this.volume = 1.2F;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.x = m.getX();
            this.y = m.getY();
            this.z = m.getZ();
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (m.isRemoved() || !m.isAlive() || mc.player == null || m.distanceToSqr(mc.player) > 44 * 44) {
                SAWS.remove(m.getId());
                stop();
                return;
            }
            x = m.getX();
            y = m.getY() + 1;
            z = m.getZ();
            double sp = m.getDeltaMovement().horizontalDistance();
            pitch = 0.85F + (float) Math.min(0.5, sp * 2.5) + (m.getTarget() != null ? 0.15F : 0F);
        }
    }
}
