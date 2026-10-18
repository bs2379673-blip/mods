package com.tunombre.concierto.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tunombre.concierto.Concierto;
import com.tunombre.concierto.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** lado cliente: analiza la cancion, sigue el reloj que mando el servidor y dibuja la escena en el cielo con un shader */
public final class ConciertoClient {
    /** orden del modo automatico (indices del shader): cambia cada 16 s, igual para todos porque depende solo del tiempo de la cancion */
    private static final int[] ORDER = {4, 0, 2, 7, 3, 5, 1, 6};
    private static final float SEGMENT = 16F;

    private static final Map<String, Track> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();

    private static ShaderInstance sky;
    private static boolean failed;

    private static boolean active, stopping;
    private static String song = "";
    private static long startNs, lastNs;
    private static int sceneSel = -1, lastScene = -1;
    private static float intensity = 1F, strobe = 0.5F;

    private static float fade;
    private static final float[] spec = new float[Track.BANDS];
    private static float level, bass, mid, high, beat, flash;

    private ConciertoClient() { }

    public static void init(IEventBus bus) {
        bus.addListener((RegisterShadersEvent e) -> {
            try {
                e.registerShader(new ShaderInstance(e.getResourceProvider(), new ResourceLocation(Concierto.MODID, "concierto_sky"),
                        DefaultVertexFormat.POSITION), s -> sky = s);
            } catch (IOException ex) {
                failed = true;
            }
        });
        MinecraftForge.EVENT_BUS.register(ConciertoClient.class);
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> { if (mc.player != null) mc.player.displayClientMessage(Component.literal(s), false); });
    }

    public static void receive(Net.Msg m) {
        switch (m.op()) {
            case Net.PLAY -> {
                song = m.song();
                sceneSel = m.scene();
                lastScene = -1;
                startNs = System.nanoTime() - (long) (m.offset() * 1e9F);
                lastNs = System.nanoTime();
                active = true;
                stopping = false;
                fade = 0F;
                java.util.Arrays.fill(spec, 0F);
                level = bass = mid = high = beat = flash = 0F;
                load(song);
            }
            case Net.STOP -> { if (active) stopping = true; }
            case Net.SCENE -> sceneSel = m.scene();
            case Net.PARAMS -> { intensity = m.a(); strobe = m.b(); }
            default -> { }
        }
    }

    private static void load(String name) {
        if (CACHE.containsKey(name) || !LOADING.add(name)) return;
        Thread t = new Thread(() -> {
            try {
                byte[] ogg = Analyzer.find(name);
                if (ogg == null) {
                    msg("[Concierto] No encuentro '" + name + ".ogg' (ponelo en config/concierto/). Uso un ritmo de prueba.");
                    CACHE.put(name, Track.synthetic(600F));
                    return;
                }
                Track tr = Analyzer.analyze(ogg);
                if (tr == null) {
                    msg("[Concierto] No pude leer '" + name + ".ogg'. Uso un ritmo de prueba.");
                    CACHE.put(name, Track.synthetic(600F));
                    return;
                }
                CACHE.put(name, tr);
            } catch (Throwable ex) {
                msg("[Concierto] Error analizando '" + name + "': " + ex);
                CACHE.put(name, Track.synthetic(600F));
            } finally {
                LOADING.remove(name);
            }
        }, "concierto-analisis");
        t.setDaemon(true);
        t.start();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) { active = false; stopping = false; }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || !active || sky == null || failed) return;
        long now = System.nanoTime();
        float dt = Math.min(0.1F, (now - lastNs) / 1e9F);
        lastNs = now;
        float t = (now - startNs) / 1e9F;
        Track tr = CACHE.get(song);

        if (stopping) {
            fade -= dt;
            if (fade <= 0F) { active = false; stopping = false; return; }
        } else {
            fade = Math.min(1F, fade + dt / 1.5F);
        }
        if (tr == null) return; // todavia analizando
        if (!tr.synthetic && t > tr.duration() + 0.5F) stopping = true;

        if (t >= 0F) {
            float d60 = dt * 60F;
            float dec = (float) Math.pow(0.86, d60);
            for (int i = 0; i < Track.BANDS; i++) spec[i] = Math.max(tr.at(t, i), spec[i] * dec);
            level += (tr.at(t, Track.LEVEL) - level) * Math.min(1F, 0.25F * d60);
            bass = Math.max(tr.at(t, Track.BASS), bass * dec);
            mid = Math.max(tr.at(t, Track.MID), mid * dec);
            high = Math.max(tr.at(t, Track.HIGH), high * dec);
            float b = tr.beatAt(t);
            beat = Math.max(beat * (float) Math.pow(0.88, d60), b);
            if (b > 0.85F) flash = Math.max(flash, strobe * b);
            flash *= (float) Math.pow(0.8, d60);
        }

        int sc = sceneSel >= 0 ? sceneSel : ORDER[((int) (Math.max(0F, t) / SEGMENT)) % ORDER.length];
        if (lastScene != -1 && sc != lastScene) flash = Math.max(flash, strobe * 0.8F);
        lastScene = sc;

        Matrix4f view = new Matrix4f(e.getPoseStack().last().pose());
        view.setTranslation(0F, 0F, 0F);
        Matrix4f invView = view.invert();
        Matrix4f invProj = new Matrix4f(e.getProjectionMatrix()).invert();

        sky.safeGetUniform("InvProj").set(invProj);
        sky.safeGetUniform("InvView").set(invView);
        sky.safeGetUniform("Time").set(Math.max(0F, t));
        sky.safeGetUniform("Scene").set((float) sc);
        sky.safeGetUniform("Level").set(level);
        sky.safeGetUniform("Bass").set(bass);
        sky.safeGetUniform("Mid").set(mid);
        sky.safeGetUniform("High").set(high);
        sky.safeGetUniform("Beat").set(beat);
        sky.safeGetUniform("Flash").set(flash);
        sky.safeGetUniform("Fade").set(fade * intensity);
        sky.safeGetUniform("Hue").set(0F);
        sky.safeGetUniform("Spec").set(spec);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShader(() -> sky);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bb.vertex(-1.0, -1.0, 0.0).endVertex();
        bb.vertex(1.0, -1.0, 0.0).endVertex();
        bb.vertex(1.0, 1.0, 0.0).endVertex();
        bb.vertex(-1.0, 1.0, 0.0).endVertex();
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }
}
