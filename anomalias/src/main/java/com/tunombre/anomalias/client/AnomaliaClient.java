package com.tunombre.anomalias.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.renderer.GameRenderer;
import com.tunombre.anomalias.Anomalias;
import com.tunombre.anomalias.Net;
import com.tunombre.anomalias.Tipo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Dibujo y sonido de la anomalia en el cliente. */
public final class AnomaliaClient {
    private AnomaliaClient() { }

    private static ShaderInstance sky;
    private static boolean failed;

    private static boolean on;
    private static Tipo tipo = Tipo.ROJO;
    private static float dur;           // 0 = fija
    private static long startNs, lastNs;
    private static float strobe = 1F;
    private static boolean stopping;
    private static long stopNs;
    private static float stopFrom = 1F;
    private static float curFade = 1F, curCov, curLight, curFlash;

    private static final Random R = new Random();
    private static final float[] bolts = new float[12];
    private static final float[] bAge = {-1F, -1F, -1F};
    private static float nextBolt = 2F;
    private record Thunder(float at, float vol) { }
    private static final List<Thunder> thunders = new ArrayList<>();
    private static final boolean[] done = new boolean[8];
    private static float nextAmbient;

    public static void init(IEventBus bus) {
        bus.addListener((RegisterShadersEvent e) -> {
            try {
                e.registerShader(new ShaderInstance(e.getResourceProvider(), new ResourceLocation(Anomalias.MODID, "anomalias_sky"),
                        DefaultVertexFormat.POSITION), s -> sky = s);
            } catch (IOException ex) {
                failed = true;
                Anomalias.LOGGER.error("No se pudo cargar el shader de anomalias", ex);
            }
        });
        MinecraftForge.EVENT_BUS.register(AnomaliaClient.class);
    }

    public static void receive(Net.Msg m) {
        switch (m.op()) {
            case Net.START -> {
                tipo = Tipo.values()[Math.max(0, Math.min(Tipo.values().length - 1, m.tipo()))];
                dur = m.duration();
                strobe = m.a();
                long now = System.nanoTime();
                startNs = now - (long) (m.elapsed() * 1e9F);
                lastNs = now;
                on = true; stopping = false;
                java.util.Arrays.fill(done, false);
                java.util.Arrays.fill(bAge, -1F);
                thunders.clear();
                nextBolt = 2F; nextAmbient = 0F;
                float el = m.elapsed();
                float k = dur > 0 ? dur / 60F : 1F;
                // los hitos que ya pasaron (jugador que entra tarde) no se vuelven a sonar
                if (el > 0.3F) done[0] = true;
                if (el > 0.8F * k) done[1] = true;
                if (el > 7F * k) done[2] = true;
            }
            case Net.STOP -> {
                if (on && !stopping) {
                    stopping = true; stopNs = System.nanoTime(); stopFrom = curFade;
                    play("fin", 1F, 1F);
                }
            }
            case Net.SETTINGS -> strobe = m.a();
            default -> { }
        }
    }

    private static void play(String id, float pitch, float vol) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvent.createVariableRangeEvent(new ResourceLocation(Anomalias.MODID, id)), pitch, vol)));
    }

    private static float sm(float a, float b, float x) {
        float t = Math.max(0F, Math.min(1F, (x - a) / (b - a)));
        return t * t * (3F - 2F * t);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) { on = false; stopping = false; }

    @SubscribeEvent
    public static void onFog(ViewportEvent.ComputeFogColor e) {
        if (!on || failed) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) return;
        float a = Math.min(1F, curCov * 0.85F) * curFade;
        if (a <= 0F) return;
        float[] c = tipo.c0, d = tipo.c1;
        e.setRed(e.getRed() + (c[0] * 0.5F + d[0] * 0.25F - e.getRed()) * a);
        e.setGreen(e.getGreen() + (c[1] * 0.5F + d[1] * 0.25F - e.getGreen()) * a);
        e.setBlue(e.getBlue() + (c[2] * 0.5F + d[2] * 0.25F - e.getBlue()) * a);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (failed || !on || sky == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) return;
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) { drawLight(e); return; }
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;

        long now = System.nanoTime();
        float dt = Math.min(0.1F, (now - lastNs) / 1e9F);
        lastNs = now;
        float t = (now - startNs) / 1e9F;
        float k = dur > 0 ? dur / 60F : 1F;
        float tl = dur > 0 ? Math.min(t, dur) : t;   // tiempo en la escala de 60 s
        float u = tl / k;

        float fade = 1F;
        if (stopping) {
            float s = (now - stopNs) / 1e9F;
            fade = stopFrom * (1F - sm(0F, 4F, s));
            if (s > 4.2F) { on = false; stopping = false; return; }
        } else if (dur > 0) {
            fade = 1F - sm(dur - 6F * k, dur - 0.2F, t);
            if (t > dur) { on = false; return; }
        }
        curFade = fade;

        float appear = sm(0F, 5F, u);
        float open = sm(5F, 16F, u);
        float spread = sm(14F, 42F, u);
        float flowerFade = 1F - sm(42F, 50F, u);
        if (dur == 0F && !stopping && u > 50F) { spread = 1F; flowerFade = 0F; }
        curCov = spread;
        curLight = (0.18F * appear + 0.82F * spread) * fade;

        // sonidos del guion
        if (!done[0] && u > 0.2F) { done[0] = true; play("alarma", 1F, 1F); }
        if (!done[1] && u > 0.6F) { done[1] = true; play("mortifilia", 1F, 1F); }
        if (!done[2] && u > 7F) { done[2] = true; play("mortifilia", 0.6F, 0.9F); }
        if (!done[3] && u > 14F) { done[3] = true; play("glitch", 1F, 0.8F); }
        if (!done[4] && u > 40F) { done[4] = true; play("glitch", 0.8F, 0.9F); }
        nextAmbient -= dt;
        if (nextAmbient <= 0F && !stopping && u > 1F) { nextAmbient = 174F; play("ambiente", 1F, 0.8F); }

        // relampagos
        float stormK = tipo.storm * (0.25F + 0.75F * Math.max(appear * 0.4F, spread));
        nextBolt -= dt;
        if (nextBolt <= 0F && strobe > 0.01F && !stopping) {
            int slot = -1;
            for (int i = 0; i < 3; i++) if (bAge[i] < 0F) { slot = i; break; }
            if (slot >= 0) {
                bolts[slot * 4] = R.nextFloat() * 6.2832F;
                bolts[slot * 4 + 1] = 0.25F + R.nextFloat() * 1.0F;
                bolts[slot * 4 + 3] = R.nextFloat() * 100F;
                bAge[slot] = 0F;
                thunders.add(new Thunder(0.35F + R.nextFloat() * 1.8F, 0.5F + 0.5F * R.nextFloat()));
            }
            nextBolt = (1.2F + R.nextFloat() * 4.5F) / Math.max(0.15F, stormK * strobe);
        }
        float flash = 0F;
        for (int i = 0; i < 3; i++) {
            if (bAge[i] >= 0F) {
                bAge[i] += dt;
                if (bAge[i] > 0.9F) bAge[i] = -1F;
                else flash = Math.max(flash, (1F - bAge[i] / 0.35F) * 0.5F * strobe);
            }
            bolts[i * 4 + 2] = bAge[i];
        }
        flash = Math.max(0F, flash);
        curFlash = flash * fade;
        for (int i = thunders.size() - 1; i >= 0; i--) {
            Thunder th = thunders.get(i);
            Thunder n = new Thunder(th.at() - dt, th.vol());
            if (n.at() <= 0F) { thunders.remove(i); play("trueno", 0.8F + R.nextFloat() * 0.4F, n.vol() * Math.min(1F, fade + 0.2F)); }
            else thunders.set(i, n);
        }

        Matrix4f view = new Matrix4f(e.getPoseStack().last().pose());
        view.setTranslation(0F, 0F, 0F);
        Matrix4f invView = view.invert();
        Matrix4f invProj = new Matrix4f(e.getProjectionMatrix()).invert();
        sky.safeGetUniform("InvProj").set(invProj);
        sky.safeGetUniform("InvView").set(invView);
        sky.safeGetUniform("Time").set(t);
        sky.safeGetUniform("Appear").set(appear);
        sky.safeGetUniform("Open").set(open);
        sky.safeGetUniform("Spread").set(spread);
        sky.safeGetUniform("FlowerFade").set(flowerFade);
        sky.safeGetUniform("Fade").set(fade);
        sky.safeGetUniform("StormK").set(stormK);
        sky.safeGetUniform("Flash").set(flash);
        sky.safeGetUniform("Strobe").set(strobe);
        sky.safeGetUniform("Col0").set(tipo.c0[0], tipo.c0[1], tipo.c0[2]);
        sky.safeGetUniform("Col1").set(tipo.c1[0], tipo.c1[1], tipo.c1[2]);
        sky.safeGetUniform("Col2").set(tipo.c2[0], tipo.c2[1], tipo.c2[2]);
        var b = sky.safeGetUniform("Bolts");
        if (b != null) b.set(bolts);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> sky);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bb.vertex(-1.0, -1.0, 0.0).endVertex();
        bb.vertex(1.0, -1.0, 0.0).endVertex();
        bb.vertex(1.0, 1.0, 0.0).endVertex();
        bb.vertex(-1.0, 1.0, 0.0).endVertex();
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    /** Ilumina el mundo con el color de la anomalia: oscurece y tine (multiplicando) solo lo que no es cielo, y los rayos encienden el paisaje. */
    private static void drawLight(RenderLevelStageEvent e) {
        float k = curLight;
        if (k <= 0.003F && curFlash <= 0.003F) return;
        float dark = 0.30F + 0.70F * (1F - 0.55F * tipo.storm * k);
        // color de multiplicacion: mezcla de blanco y el color medio de la anomalia, mas oscuro cuanto mas cubierto
        float pulse = 1F + 0.05F * (float) Math.sin(System.nanoTime() / 1e9 * 1.3);
        float[] m = tipo.c1;
        float mr = 1F - k * (1F - (0.25F + 0.75F * m[0]) * 0.80F * pulse);
        float mg = 1F - k * (1F - (0.25F + 0.75F * m[1]) * 0.80F * pulse);
        float mb = 1F - k * (1F - (0.25F + 0.75F * m[2]) * 0.80F * pulse);
        float dm = 1F - k * (1F - dark) * 0.9F;
        mr *= dm; mg *= dm; mb *= dm;

        PoseStack mvs = RenderSystem.getModelViewStack();
        mvs.pushPose();
        mvs.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(516);   // GL_GREATER: solo donde ya hay terreno, nunca sobre el cielo
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        if (k > 0.003F) {
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ZERO, GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
            quad(bb, mr, mg, mb, 1F);
        }
        if (curFlash > 0.003F) {
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
            quad(bb, tipo.c2[0], tipo.c2[1], tipo.c2[2], Math.min(0.35F, curFlash * 0.45F));
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(515);
        RenderSystem.enableCull();
        RenderSystem.setProjectionMatrix(e.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        mvs.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    private static void quad(BufferBuilder bb, float r, float g, float b, float a) {
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bb.vertex(-1.0, -1.0, 0.99999).color(r, g, b, a).endVertex();
        bb.vertex(1.0, -1.0, 0.99999).color(r, g, b, a).endVertex();
        bb.vertex(1.0, 1.0, 0.99999).color(r, g, b, a).endVertex();
        bb.vertex(-1.0, 1.0, 0.99999).color(r, g, b, a).endVertex();
        BufferUploader.drawWithShader(bb.end());
    }
}
