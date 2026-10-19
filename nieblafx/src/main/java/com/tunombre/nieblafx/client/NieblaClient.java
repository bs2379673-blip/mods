package com.tunombre.nieblafx.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tunombre.nieblafx.Fog;
import com.tunombre.nieblafx.NieblaFX;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.joml.Matrix4f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NieblaClient {
    private static ShaderInstance shader;
    private static boolean failed;

    /** estado objetivo recibido del servidor */
    private static volatile Fog.State target = new Fog.State();

    // valores actuales (se acercan al objetivo con el fundido)
    private static float presence;      // 0..1 niebla global
    private static float cDens = -1F, cMax, cBase, cFall, cNoise, cWind, cSky, cScale = 24F;
    private static float lSky = 1F, lBlock = 0F;   // luz local suavizada en la camara
    private static float cR, cG, cB;
    private static final Map<String, Float> zk = new HashMap<>();   // presencia de cada zona
    private static final Map<String, Fog.Zone> zcur = new HashMap<>();  // ultima definicion conocida (para desvanecer las borradas)
    private static final Map<String, float[]> zcol = new HashMap<>();
    private static long last;

    /** calidad: pasos del raymarching */
    private static int steps = 24;

    public static void init(IEventBus bus) {
        loadQuality();
        bus.addListener((RegisterShadersEvent e) -> {
            try {
                e.registerShader(new ShaderInstance(e.getResourceProvider(), new ResourceLocation(NieblaFX.MODID, "nieblafx_fog"),
                        DefaultVertexFormat.POSITION), s -> shader = s);
            } catch (IOException ex) {
                failed = true;
                NieblaFX.LOGGER.error("No se pudo cargar el shader de niebla", ex);
            }
        });
        MinecraftForge.EVENT_BUS.register(NieblaClient.class);
    }

    public static void receive(String json) {
        try {
            Fog.State s = new Gson().fromJson(json, Fog.State.class);
            if (s != null) {
                if (s.zones == null) s.zones = new ArrayList<>();
                target = s;
            }
        } catch (Exception ex) {
            NieblaFX.LOGGER.error("Estado de niebla invalido", ex);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        target = new Fog.State();
        presence = 0F; cDens = -1F;
        zk.clear(); zcur.clear(); zcol.clear();
    }

    private static float[] rgb(String hex) {
        int c = Fog.parseColor(hex);
        return new float[]{((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F};
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("nieblacalidad")
                .then(Commands.argument("nivel", StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[]{"bajo", "medio", "alto"}, b))
                        .executes(c -> {
                            String n = StringArgumentType.getString(c, "nivel");
                            steps = switch (n) { case "bajo" -> 12; case "alto" -> 40; default -> 24; };
                            saveQuality();
                            c.getSource().sendSuccess(() -> Component.literal("Calidad de niebla: " + n + " (" + steps + " pasos)"), false);
                            return 1;
                        })));
    }

    private static Path qfile() { return FMLPaths.CONFIGDIR.get().resolve("nieblafx-cliente.txt"); }

    private static void loadQuality() {
        try { if (Files.exists(qfile())) steps = Math.max(6, Math.min(64, Integer.parseInt(Files.readString(qfile()).trim()))); } catch (Exception ignored) { }
    }

    private static void saveQuality() {
        try { Files.writeString(qfile(), String.valueOf(steps)); } catch (Exception ignored) { }
    }

    private static float smooth(float a, float b, float x) {
        float t = Math.max(0F, Math.min(1F, (x - a) / (b - a)));
        return t * t * (3F - 2F * t);
    }

    private static float approach(float cur, float tgt, float step) {
        return cur < tgt ? Math.min(tgt, cur + step) : Math.max(tgt, cur - step);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        if (shader == null || failed) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long now = System.nanoTime();
        float dt = last == 0 ? 0.016F : Math.min(0.25F, (now - last) / 1e9F);
        last = now;

        Fog.State t = target;
        float fade = Math.max(0.05F, t.fade);
        float sp = dt / fade;
        presence = approach(presence, t.on ? 1F : 0F, sp);
        float[] col = rgb(t.color);
        if (cDens < 0F) { cMax = t.maxDist; cBase = t.baseY; cFall = t.falloff; cNoise = t.noise; cWind = t.wind; cSky = t.sky; cScale = t.scale; cR = col[0]; cG = col[1]; cB = col[2]; cDens = t.density; }
        float k = 1F - (float) Math.exp(-dt * 3F / fade);
        cDens += (t.density - cDens) * k;
        cMax += (t.maxDist - cMax) * k;
        cBase += (t.baseY - cBase) * k;
        cFall += (t.falloff - cFall) * k;
        cNoise += (t.noise - cNoise) * k;
        cWind += (t.wind - cWind) * k;
        cSky += (t.sky - cSky) * k;
        cScale += (t.scale - cScale) * k;
        cR += (col[0] - cR) * k; cG += (col[1] - cG) * k; cB += (col[2] - cB) * k;

        // zonas: presencia de cada una (las borradas se desvanecen)
        String dim = mc.level.dimension().location().toString();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Fog.Zone z : t.zones) {
            String id = z.id.toLowerCase();
            seen.add(id);
            zcur.put(id, z);
            zcol.put(id, rgb(z.color));
            float tg = (z.on && z.dim.equals(dim)) ? 1F : 0F;
            zk.put(id, approach(zk.getOrDefault(id, 0F), tg, sp));
        }
        for (String id : new ArrayList<>(zk.keySet())) {
            if (seen.contains(id)) continue;
            float v = approach(zk.get(id), 0F, sp);
            if (v <= 0F) { zk.remove(id); zcur.remove(id); zcol.remove(id); } else zk.put(id, v);
        }

        var cam = e.getCamera().getPosition();
        // las 8 zonas mas cercanas con presencia
        List<String> ids = new ArrayList<>();
        for (String id : zk.keySet()) if (zk.get(id) > 0.001F) ids.add(id);
        ids.sort((a, b) -> Double.compare(d2(zcur.get(a), cam), d2(zcur.get(b), cam)));
        int zn = Math.min(8, ids.size());
        float gd = cDens * presence;
        if (gd <= 0.00001F && zn == 0) return;

        float[] za = new float[32], zb = new float[32], zc = new float[24];
        for (int i = 0; i < zn; i++) {
            Fog.Zone z = zcur.get(ids.get(i));
            float[] c = zcol.get(ids.get(i));
            za[i * 4] = (float) (z.x - cam.x); za[i * 4 + 1] = (float) (z.y - cam.y); za[i * 4 + 2] = (float) (z.z - cam.z); za[i * 4 + 3] = Math.max(1F, z.radius);
            zb[i * 4] = Math.max(1F, z.height); zb[i * 4 + 1] = z.density * zk.get(ids.get(i));
            zc[i * 3] = c[0]; zc[i * 3 + 1] = c[1]; zc[i * 3 + 2] = c[2];
        }

        Matrix4f view = new Matrix4f(e.getPoseStack().last().pose());
        view.setTranslation(0F, 0F, 0F);
        Matrix4f invView = view.invert();
        Matrix4f invProj = new Matrix4f(e.getProjectionMatrix()).invert();
        float time = (float) ((System.currentTimeMillis() % 6_000_000L) / 1000.0);

        shader.safeGetUniform("InvProj").set(invProj);
        shader.safeGetUniform("InvView").set(invView);
        shader.safeGetUniform("Time").set(time);
        shader.safeGetUniform("GDens").set(gd);
        shader.safeGetUniform("GBase").set((float) (cBase - cam.y));
        shader.safeGetUniform("GFall").set(cFall);
        shader.safeGetUniform("MaxDist").set(Math.max(8F, cMax));
        shader.safeGetUniform("NoiseAmt").set(cNoise);
        shader.safeGetUniform("Wind").set(cWind);
        shader.safeGetUniform("SkyFog").set(cSky);
        // luz: sol/luna segun la hora, clima y luz local (cuevas, antorchas)
        float ang = mc.level.getSunAngle(1F);
        float sx = -(float) Math.sin(ang), sy = (float) Math.cos(ang);
        float day = smooth(-0.15F, 0.25F, sy);
        float rain = mc.level.getRainLevel(1F) * 0.6F + mc.level.getThunderLevel(1F) * 0.2F;
        net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(cam);
        float ls = mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, bp) / 15F;
        float lb = mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, bp) / 15F;
        float lk = 1F - (float) Math.exp(-dt * 2.5F);
        lSky += (ls - lSky) * lk; lBlock += (lb - lBlock) * lk;
        float dl = 1F - rain;
        float nightAmb = 0.06F;
        float amb = (nightAmb + (0.55F - nightAmb) * day) * dl;
        amb = nightAmb + (amb - nightAmb) * lSky;
        float aR = amb * 0.95F + lBlock * 0.45F, aG = amb * 1.0F + lBlock * 0.30F, aB = amb * 1.08F + lBlock * 0.12F;
        float sun = day * dl * lSky;
        float moon = (1F - day) * 0.10F * lSky * dl;
        float dx = day > 0.02F ? sx : -sx, dy = day > 0.02F ? sy : -sy;
        float inv = (float) (1.0 / Math.sqrt(dx * dx + dy * dy));
        shader.safeGetUniform("SunDir").set(dx * inv, dy * inv, 0.1F);
        shader.safeGetUniform("SunCol").set(sun * 0.95F + moon * 0.6F, sun * 0.88F + moon * 0.7F, sun * 0.72F + moon * 1.0F);
        shader.safeGetUniform("Amb").set(aR, aG, aB);
        shader.safeGetUniform("Scale").set(cScale);
        shader.safeGetUniform("Steps").set((float) steps);
        shader.safeGetUniform("ZN").set((float) zn);
        shader.safeGetUniform("GCol").set(cR, cG, cB);
        shader.safeGetUniform("CamN").set((float) (cam.x % 4096.0), (float) (cam.y % 4096.0), (float) (cam.z % 4096.0));
        shader.safeGetUniform("ZA").set(za);
        shader.safeGetUniform("ZB").set(zb);
        shader.safeGetUniform("ZC").set(zc);
        shader.setSampler("Depth", mc.getMainRenderTarget().getDepthTextureId());

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);
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

    private static double d2(Fog.Zone z, net.minecraft.world.phys.Vec3 c) {
        double dx = z.x - c.x, dy = z.y - c.y, dz = z.z - c.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
