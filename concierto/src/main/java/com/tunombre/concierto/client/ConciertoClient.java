package com.tunombre.concierto.client;

import com.google.gson.JsonObject;
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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** lado cliente: sigue el reloj que mando el servidor, aplica el guion de la cancion y dibuja el cielo y el escenario con shaders */
public final class ConciertoClient {
    /** sin guion: las escenas clasicas rotan cada 16 s, igual para todos porque depende solo del tiempo de la cancion */
    private static final int[] ORDER = {4, 0, 2, 7, 3, 5, 1, 6};
    private static final float SEGMENT = 16F;

    /** guiones sin musica propia: usan un ritmo de relleno */
    private static final Set<String> SYNTH = Set.of("prueba", "intermedio", "premesmerizer", "pretriplebaka", "despedida");
    private static final Map<String, Track> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();

    private static ShaderInstance sky, fx, fxAdd;
    private static boolean failed;

    private static boolean active, stopping;
    private static String song = "", key = "";
    private static long startNs, lastNs;
    private static int sceneSel = -1;
    private static float intensity = 1F, strobe = 0.5F;
    private static Script script;
    private static final Stage stage = new Stage();
    private static final Sprites sprites = new Sprites();

    private static float fade;
    private static final float[] spec = new float[Track.BANDS];
    private static float level, bass, mid, high, kickEnv, snareEnv, flash;
    private static float lastAccent = -9F, swapPrev, lastSwap = -9F;
    private static float nova = -1F, shock = -1F, darkUntil = -1F, songT;
    private static float floorA, eqA, beamsA, ringA;
    private static final float[][] pal = {{1F, 0.42F, 0.6F}, {1F, 0.6F, 0.42F}, {1F, 0.82F, 0.88F}};
    private static int curScene = 8;
    private static String floorMode = "off", beamMode = "off";
    private static boolean ringOn;
    private static float eqGain;

    private ConciertoClient() { }

    public static void init(IEventBus bus) {
        bus.addListener((RegisterShadersEvent e) -> {
            try {
                e.registerShader(new ShaderInstance(e.getResourceProvider(), new ResourceLocation(Concierto.MODID, "concierto_sky"),
                        DefaultVertexFormat.POSITION), s -> sky = s);
                e.registerShader(new ShaderInstance(e.getResourceProvider(), new ResourceLocation(Concierto.MODID, "concierto_fx"),
                        DefaultVertexFormat.POSITION_COLOR_TEX), s -> fx = s);
                e.registerShader(new ShaderInstance(e.getResourceProvider(), new ResourceLocation(Concierto.MODID, "concierto_fxadd"),
                        DefaultVertexFormat.POSITION_COLOR_TEX), s -> fxAdd = s);
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
                key = song.replace("_", "").toLowerCase();
                sceneSel = m.scene();
                startNs = System.nanoTime() - (long) (m.offset() * 1e9F);
                lastNs = System.nanoTime();
                active = true;
                stopping = false;
                fade = 0F;
                java.util.Arrays.fill(spec, 0F);
                level = bass = mid = high = kickEnv = snareEnv = flash = 0F;
                nova = shock = darkUntil = -1F;
                lastAccent = -9F;
                floorA = eqA = beamsA = ringA = 0F;
                sprites.clear();
                stage.pyros.clear();
                stage.shells.clear();
                ModelFx.clear();
                script = Script.load(key);
                if (script != null) {
                    for (int i = 0; i < 3; i++) System.arraycopy(script.look.col[i], 0, pal[i], 0, 3);
                }
                load(key);
            }
            case Net.STOP -> { if (active) stopping = true; }
            case Net.SCENE -> sceneSel = m.scene();
            case Net.PARAMS -> { intensity = m.a(); strobe = m.b(); }
            case Net.STAGE -> {
                stage.set = m.b() > 0F;
                stage.x = m.x(); stage.y = m.y(); stage.z = m.z();
                stage.yaw = m.a();
                if (m.b() > 0F) stage.scale = m.b();
                float[] a = m.arr();
                for (int i = 0; i < a.length && i < stage.p.length; i++) stage.p[i] = a[i];
            }
            case Net.LIGHT -> stage.columns.add(new Stage.Column(new Vec3(m.x(), m.y(), m.z()), m.scene(), Math.max(0.2F, m.a()), Math.max(1F, m.b())));
            case Net.FX -> fireFx(m.song(), null);
            case Net.STAR -> stage.stars.add(new Stage.Star(new Vec3(m.x(), m.y(), m.z()), m.scene(), Math.max(1F, m.a()), m.b() >= 0F ? 1F : -1F));
            default -> { }
        }
    }

    private static void fireFx(String name, JsonObject cue) {
        switch (name) {
            case "flash", "destello" -> flash = Math.max(flash, strobe);
            case "nova" -> nova = 0F;
            case "shock", "onda" -> shock = 0F;
            case "blackout", "apagon" -> {
                float dur = cue != null && cue.has("dur") ? cue.get("dur").getAsFloat() : 0.6F;
                darkUntil = songT + dur;
            }
            case "column", "columna" -> {
                float[] c = cue != null && cue.has("col") ? Script.hex(cue.get("col").getAsString()) : new float[]{0.2F, 0.9F, 1F};
                float size = cue != null && cue.has("size") ? cue.get("size").getAsFloat() : stage.scale * 0.5F;
                float secs = cue != null && cue.has("dur") ? cue.get("dur").getAsFloat() : 4F;
                Vec3 at = new Vec3(stage.x, stage.y, stage.z);
                int rgb = ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255);
                stage.columns.add(new Stage.Column(at, rgb, size, secs));
            }
            case "sprite" -> { /* sin stickers ni GIFs */ }
            case "pyro", "pirotecnia" -> {
                float dur = cue != null && cue.has("dur") ? cue.get("dur").getAsFloat() : 3F;
                float[] c = cue != null && cue.has("col") ? Script.hex(cue.get("col").getAsString()) : new float[]{pal[0][0], pal[0][1], pal[0][2]};
                stage.pyros.add(new Stage.Pyro(dur, c, (int) (songT * 100F) + stage.pyros.size()));
            }
            case "rayo" -> { /* sin rayo de Miku */ }
            case "fuegos" -> {
                float dur = cue != null && cue.has("dur") ? cue.get("dur").getAsFloat() : 8F;
                int n = cue != null && cue.has("n") ? cue.get("n").getAsInt() : Math.max(4, Math.round(dur * 1.6F));
                float[][] cols = {{pal[0][0], pal[0][1], pal[0][2]}, {pal[1][0], pal[1][1], pal[1][2]}, {pal[2][0], pal[2][1], pal[2][2]}, {1F, 0.82F, 0.3F}, {1F, 1F, 1F}};
                stage.spawnShells(dur, n, cols, (int) (songT * 100F) + stage.shells.size() * 7 + 11);
            }
            case "star", "estrella" -> {
                float[] c = cue != null && cue.has("col") ? Script.hex(cue.get("col").getAsString()) : new float[]{0.2F, 0.9F, 1F};
                float dur = cue != null && cue.has("dur") ? cue.get("dur").getAsFloat() : 3.2F;
                float side = cue != null && cue.has("side") ? cue.get("side").getAsFloat() : 1F;
                int rgb = ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255);
                stage.stars.add(new Stage.Star(new Vec3(stage.x, stage.y, stage.z), rgb, dur, side >= 0F ? 1F : -1F));
            }
            default -> { }
        }
    }

    private static void load(String k) {
        if (CACHE.containsKey(k) || !LOADING.add(k)) return;
        Thread t = new Thread(() -> {
            try {
                Track tr = null;
                if (SYNTH.contains(k)) {
                    tr = Track.synthetic(600F);
                } else {
                    var res = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation(Concierto.MODID, "tracks/" + k + ".bin"));
                    if (res.isPresent()) try (InputStream in = res.get().open()) { tr = Track.fromBytes(in.readAllBytes()); }
                    if (tr == null) {
                        byte[] ogg = Analyzer.find(song);
                        if (ogg != null) tr = Analyzer.analyze(ogg);
                    }
                    if (tr == null) {
                        msg("[Concierto] No tengo el analisis de '" + song + "' ni encuentro su .ogg (config/concierto/). Uso un ritmo de prueba.");
                        tr = Track.synthetic(600F);
                    }
                }
                CACHE.put(k, tr);
            } catch (Throwable ex) {
                msg("[Concierto] Error preparando '" + song + "': " + ex);
                CACHE.put(k, Track.synthetic(600F));
            } finally {
                LOADING.remove(k);
            }
        }, "concierto-analisis");
        t.setDaemon(true);
        t.start();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) { active = false; stopping = false; stage.columns.clear(); stage.stars.clear(); stage.pyros.clear(); stage.shells.clear(); ModelFx.clear(); sprites.clear(); }

    private static float towards(float cur, float target, float step) { return cur < target ? Math.min(target, cur + step) : Math.max(target, cur - step); }

    private static float pow(float b, float e) { return (float) Math.pow(b, e); }

    /** avanza todo el estado un cuadro; false = no hay nada que dibujar */
    private static boolean update(float dt, float t) {
        Track tr = CACHE.get(key);
        if (stopping) {
            fade -= dt;
            if (fade <= 0F) { active = false; stopping = false; floorA = eqA = beamsA = ringA = 0F; sprites.clear(); return false; }
        } else {
            fade = Math.min(1F, fade + dt / 1.5F);
        }
        if (tr == null) return false;
        if (!tr.synthetic && t > tr.duration() + 0.5F) stopping = true;
        songT = t;
        float d60 = dt * 60F;
        if (t >= 0F) {
            float dec = pow(0.86F, d60);
            for (int i = 0; i < Track.BANDS; i++) spec[i] = Math.max(tr.at(t, i), spec[i] * dec);
            level += (tr.at(t, Track.LEVEL) - level) * Math.min(1F, 0.25F * d60);
            bass = Math.max(tr.at(t, Track.BASS), bass * dec);
            mid = Math.max(tr.at(t, Track.MID), mid * dec);
            high = Math.max(tr.at(t, Track.HIGH), high * dec);
            kickEnv = Math.max(tr.hitAt(t, Track.KICK), kickEnv * pow(0.80F, d60));
            snareEnv = Math.max(tr.hitAt(t, Track.SNARE), snareEnv * pow(0.78F, d60));
        }
        flash *= pow(0.8F, d60);

        // guion
        Script.Look look;
        if (script != null) {
            List<JsonObject> fxs = script.advance(Math.max(0F, t));
            look = script.look;
            for (JsonObject c : fxs) fireFx(c.get("fx").getAsString(), c);
            curScene = sceneSel >= 0 ? sceneSel : look.scene;
            floorMode = look.floor;
            beamMode = look.beams;
            ringOn = look.ring;
            eqGain = look.eq;
        } else {
            look = null;
            curScene = sceneSel >= 0 ? sceneSel : ORDER[((int) (Math.max(0F, t) / SEGMENT)) % ORDER.length];
            floorMode = "off"; beamMode = "off"; ringOn = false; eqGain = 0F;
        }
        float rKick = look != null ? look.kick : 0.6F, rSnare = look != null ? look.snare : 0.3F, rAcc = look != null ? look.accent : 0.8F;
        flash = Math.max(flash, snareEnv * rSnare * strobe * 0.6F);
        float acc = tr.hitAt(t, Track.ACCENT);
        if (acc > 0.6F && t - lastAccent > 0.4F && t >= 0F) {
            lastAccent = t;
            if (rAcc > 0F) {
                nova = 0F;
                shock = 0F;
                flash = Math.max(flash, strobe * rAcc * 0.7F);
            }
        }
        if (look != null && !look.swap.equals("off")) {
            float env = look.swap.equals("kick") ? kickEnv : snareEnv;
            if (env > 0.75F && swapPrev <= 0.75F && t - lastSwap > 0.25F) {
                float[] c0 = look.col[0];
                look.col[0] = look.col[1];
                look.col[1] = look.col[2];
                look.col[2] = c0;
                lastSwap = t;
            }
            swapPrev = env;
        }
        if (look != null) {
            float rate = look.swap.equals("off") ? 1.5F : 9F;
            for (int i = 0; i < 3; i++) for (int k = 0; k < 3; k++) pal[i][k] += (look.col[i][k] - pal[i][k]) * Math.min(1F, dt * rate);
        }
        if (nova >= 0F) { nova += dt / 1.6F; if (nova > 1F) nova = -1F; }
        if (shock >= 0F) { shock += dt / 2.4F; if (shock > 1F) shock = -1F; }
        if (darkUntil >= 0F && t >= darkUntil) { darkUntil = -1F; flash = Math.max(flash, strobe); }

        floorA = towards(floorA, floorMode.equals("off") ? 0F : 1F, dt * 0.8F);
        eqA = towards(eqA, eqGain > 0F ? 1F : 0F, dt * 0.8F);
        beamsA = towards(beamsA, beamMode.equals("off") ? 0F : 1F, dt * 0.8F);
        ringA = towards(ringA, ringOn ? 1F : 0F, dt * 1.2F);

        tickEffects(dt);
        return true;
    }

    /** columnas, estrellas y chispas envejecen (tambien cuando no suena ninguna cancion) */
    private static void tickEffects(float dt) {
        // columnas de luz: envejecen y, al tocar el piso, destellan
        for (var it = stage.columns.iterator(); it.hasNext(); ) {
            Stage.Column c = it.next();
            c.age += dt;
            if (!c.hit && c.age >= Stage.FALL_TIME) { c.hit = true; flash = Math.max(flash, strobe); }
            if (c.age > c.secs + 1.2F) it.remove();
        }
        for (var it = stage.stars.iterator(); it.hasNext(); ) {
            Stage.Star st = it.next();
            st.age += dt;
            if (!st.hit && st.age >= st.dur) {
                st.hit = true;
                int rgb = ((int) (st.rgb[0] * 255) << 16) | ((int) (st.rgb[1] * 255) << 8) | (int) (st.rgb[2] * 255);
                stage.columns.add(new Stage.Column(st.target, rgb, Math.max(0.8F, stage.scale * 0.45F), 3.6F).instant());
                shock = 0F;
                nova = 0F;
                flash = Math.max(flash, 1F);
                it.remove();
            }
        }
        for (var it = stage.shells.iterator(); it.hasNext(); ) {
            Stage.Shell sh = it.next();
            sh.age += dt;
            if (!sh.burst && sh.age >= Stage.RISE) { sh.burst = true; flash = Math.max(flash, 0.3F); }
            if (sh.age > Stage.RISE + sh.life() + 0.2F) it.remove();
        }
        for (var it = stage.pyros.iterator(); it.hasNext(); ) {
            Stage.Pyro py = it.next();
            py.age += dt;
            if (py.age > py.dur + 2.6F) it.remove();
        }
    }

    private static boolean standalone() { return !stage.shells.isEmpty() || !stage.stars.isEmpty() || !stage.columns.isEmpty() || !stage.pyros.isEmpty(); }

    private static float darkMul() { return darkUntil >= 0F ? 0F : 1F; }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (failed) return;
        if (!active && !standalone()) return;
        RenderLevelStageEvent.Stage st = e.getStage();
        if (st == RenderLevelStageEvent.Stage.AFTER_SKY) {
            long now = System.nanoTime();
            float dt = Math.min(0.1F, (now - lastNs) / 1e9F);
            lastNs = now;
            if (!active) { tickEffects(dt); return; }
            float t = (now - startNs) / 1e9F;
            if (!update(dt, t) || sky == null) return;
            drawSky(e);
        } else if (st == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            if (fx == null || fxAdd == null) return;
            if (active && CACHE.get(key) == null) return;
            drawStage(e);
        }
    }

    private static void drawSky(RenderLevelStageEvent e) {
        Matrix4f view = new Matrix4f(e.getPoseStack().last().pose());
        view.setTranslation(0F, 0F, 0F);
        Matrix4f invView = view.invert();
        Matrix4f invProj = new Matrix4f(e.getProjectionMatrix()).invert();
        // hacia donde esta el escenario visto desde el publico (el publico mira hacia -F... el escenario queda del lado de -F)
        StageFx.draw(fxAdd, cam.x, cam.y, cam.z, stage, Math.max(0F, songT), level, kickEnv, pal, alpha * Math.max(floorA, beamsA));
        float sdx = stage.set ? (float) -stage.fx() : 0F, sdz = stage.set ? (float) -stage.fz() : -1F;

        sky.safeGetUniform("InvProj").set(invProj);
        sky.safeGetUniform("InvView").set(invView);
        sky.safeGetUniform("Time").set(Math.max(0F, songT));
        sky.safeGetUniform("Scene").set((float) curScene);
        sky.safeGetUniform("Level").set(level);
        sky.safeGetUniform("Bass").set(bass);
        sky.safeGetUniform("Mid").set(mid);
        sky.safeGetUniform("High").set(high);
        float k = script != null ? script.look.kick : 0.6F;
        sky.safeGetUniform("Beat").set(kickEnv * k);
        sky.safeGetUniform("Kick").set(kickEnv * k);
        sky.safeGetUniform("Flash").set(flash);
        sky.safeGetUniform("Fade").set(fade * intensity * darkMul());
        sky.safeGetUniform("Hue").set(0F);
        sky.safeGetUniform("Nova").set(nova);
        sky.safeGetUniform("StageDir").set(sdx, 0F, sdz);
        sky.safeGetUniform("P0").set(pal[0][0], pal[0][1], pal[0][2]);
        sky.safeGetUniform("P1").set(pal[1][0], pal[1][1], pal[1][2]);
        sky.safeGetUniform("P2").set(pal[2][0], pal[2][1], pal[2][2]);
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

    private static void drawStage(RenderLevelStageEvent e) {
        boolean any = (active && stage.set && (floorA > 0.003F || eqA > 0.003F || beamsA > 0.003F || ringA > 0.003F)) || !stage.columns.isEmpty()
                || !stage.stars.isEmpty() || !stage.shells.isEmpty() || !stage.pyros.isEmpty() || !sprites.isEmpty();
        if (!any) return;
        Vec3 cam = e.getCamera().getPosition();
        float alpha = active ? fade * Math.min(1F, intensity) * darkMul() : 1F;
        // en un apagon solo se ve la estrella y la luz del impacto
        boolean dark = alpha <= 0.003F;
        if (dark && stage.stars.isEmpty() && stage.columns.isEmpty()) return;

        // drawWithShader usa la matriz de RenderSystem y no la del shader: hay que ponerle la rotacion de la camara ahi
        com.mojang.blaze3d.vertex.PoseStack mvs = RenderSystem.getModelViewStack();
        mvs.pushPose();
        Matrix4f rot = new Matrix4f(e.getPoseStack().last().pose());
        rot.setTranslation(0F, 0F, 0F);
        mvs.mulPoseMatrix(rot);
        RenderSystem.applyModelViewMatrix();
        fx.safeGetUniform("Time").set(Math.max(0F, songT));
        fx.safeGetUniform("Level").set(level);
        fx.safeGetUniform("Bass").set(bass);
        fx.safeGetUniform("Mid").set(mid);
        fx.safeGetUniform("High").set(high);
        float k = script != null ? script.look.kick : 0.6F;
        fx.safeGetUniform("Beat").set(kickEnv * k);
        fx.safeGetUniform("Flash").set(flash);
        fx.safeGetUniform("FloorMode").set(floorMode.equals("ripple") ? 2F : floorMode.equals("disco") ? 3F : 1F);
        fx.safeGetUniform("PanelGain").set(Math.max(0.2F, eqGain));
        fx.safeGetUniform("Shock").set(shock);
        fx.safeGetUniform("Prog").set(1F);
        fx.safeGetUniform("Scale").set(stage.scale);
        fx.safeGetUniform("P0").set(pal[0][0], pal[0][1], pal[0][2]);
        fx.safeGetUniform("P1").set(pal[1][0], pal[1][1], pal[1][2]);
        fx.safeGetUniform("P2").set(pal[2][0], pal[2][1], pal[2][2]);
        fx.safeGetUniform("Spec").set(spec);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.setShader(() -> fx);
        if (!dark) stage.draw(fx, cam.x, cam.y, cam.z, ringOn, floorA * alpha, eqA * alpha, ringA * alpha);
        // la mezcla la define el json de cada shader: fxadd es aditivo (rayos y columnas)
        RenderSystem.setShader(() -> fxAdd);
        if (!dark) stage.drawBeams(fxAdd, cam.x, cam.y, cam.z, Math.max(0F, songT), beamMode, beamsA * alpha, kickEnv, bass, level, pal);
        stage.drawColumns(fxAdd, cam.x, cam.y, cam.z);
        if (!dark) stage.drawPyros(fxAdd, cam.x, cam.y, cam.z);
        stage.drawStars(fxAdd, cam.x, cam.y, cam.z);
        stage.drawShells(fxAdd, cam.x, cam.y, cam.z);
        ModelFx.draw(fxAdd, cam.x, cam.y, cam.z, e.getPartialTick(), stage, Math.max(0F, songT), level, kickEnv, !dark);
        float sdx = stage.set ? (float) -stage.fx() : 0F, sdz = stage.set ? (float) -stage.fz() : -1F;
        if (!dark) sprites.draw(fx, fxAdd, sdx, sdz, Math.max(0F, songT), kickEnv, alpha);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        mvs.popPose();
        RenderSystem.applyModelViewMatrix();
    }
}
