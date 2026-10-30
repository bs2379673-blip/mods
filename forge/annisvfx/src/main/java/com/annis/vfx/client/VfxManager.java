package com.annis.vfx.client;

import com.annis.vfx.AnnisVfx;
import com.annis.vfx.client.fx.Flowers;
import com.annis.vfx.client.fx.Fx;
import com.annis.vfx.client.fx.GroundGlow;
import com.annis.vfx.client.fx.LeafFall;
import com.annis.vfx.client.fx.LightPillar;
import com.annis.vfx.client.fx.Motes;
import com.annis.vfx.net.VfxPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.annis.vfx.compat.WorldRenderContext;
import net.minecraft.client.Camera;
import com.annis.vfx.compat.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Escenas activas en este cliente: las arma al llegar el paquete y las dibuja cada fotograma. */
public final class VfxManager {
    /** Una escena: varios efectos con el mismo reloj, mas el tinte de pantalla. */
    static final class Scene {
        final long startTick;
        final Vec3 center;
        final List<Fx> fx = new ArrayList<>();
        /** Tinte de pantalla verde: {empieza, llega al maximo, empieza a bajar, termina} en segundos; null = sin tinte. */
        float[] tint;
        float tintMax;

        Scene(long startTick, Vec3 center) {
            this.startTick = startTick;
            this.center = center;
        }
    }

    private static final List<Scene> SCENES = new ArrayList<>();

    /** Tiempos de la escena del arbol (segundos). */
    public static final float LAND = 6.0f;
    public static final float REVEAL = 10.2f;

    private VfxManager() {
    }

    public static void clear() {
        SCENES.clear();
        Reveal.clear();
    }

    public static void play(VfxPayload p) {
        Vec3 c = new Vec3(p.x(), p.y(), p.z());
        Scene s = new Scene(p.start(), c);
        int seed = p.seed();
        switch (p.effect()) {
            case "parar" -> {
                clear();
                return;
            }
            case "arbol" -> {
                // 1) cae la hoja (y otras alrededor)  2) al tocar el piso se enciende en verde
                // 3) suben lucecitas y brotan flores  4) baja una columna de luz  5) aparece el personaje
                s.fx.add(new LeafFall(c.x, c.y, c.z, seed, 18, 5.0f, true, LAND));
                s.fx.add(new GroundGlow(c.x, c.y, c.z, seed, LAND, 7.0f, 12.0f));
                s.fx.add(new Motes(c.x, c.y, c.z, seed, LAND + 0.3f, 12.0f, 5.0f, 70));
                s.fx.add(new Flowers(c.x, c.y, c.z, seed, LAND + 1.0f, 3.8f, 18, 45.0f));
                s.fx.add(new LightPillar(c.x, c.y, c.z, LAND + 3.0f, LAND + 9.0f));
                s.tint = new float[]{LAND, LAND + 1.5f, LAND + 6.5f, LAND + 9.5f};
                s.tintMax = 0.28f;
                if (p.entity() >= 0) {
                    Reveal.add(p.entity(), p.start(), REVEAL);
                }
            }
            case "hojas" -> s.fx.add(new LeafFall(c.x, c.y, c.z, seed, Math.max(1, Math.round(p.a())), 4.5f, false, 6.0f));
            case "brillo" -> {
                s.fx.add(new GroundGlow(c.x, c.y, c.z, seed, 0, p.a(), 10.0f));
                s.fx.add(new Motes(c.x, c.y, c.z, seed, 0.3f, 10.0f, p.a() * 0.7f, Math.round(p.a() * 10)));
            }
            case "flores" -> s.fx.add(new Flowers(c.x, c.y, c.z, seed, 0, p.a(), Math.max(4, Math.round(p.a() * 5)), 45.0f));
            default -> {
                AnnisVfx.LOGGER.warn("Efecto desconocido: {}", p.effect());
                return;
            }
        }
        SCENES.add(s);
    }

    private static float sceneTime(Scene s, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? -1 : (mc.level.getGameTime() - s.startTick + tickDelta) / 20.0f;
    }

    public static void render(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (SCENES.isEmpty() || mc.level == null) {
            return;
        }
        VfxTextures.ensure();
        Camera camera = ctx.camera();
        float tickDelta = ctx.tickCounter().getGameTimeDeltaPartialTick(true);
        MultiBufferSource.BufferSource imm = mc.renderBuffers().bufferSource();
        Draw d = new Draw(new PoseStack(), imm, camera.getPosition(), camera.rotation());
        Iterator<Scene> it = SCENES.iterator();
        while (it.hasNext()) {
            Scene s = it.next();
            float t = sceneTime(s, tickDelta);
            if (t < 0) {
                continue;
            }
            boolean alive = false;
            for (Fx f : s.fx) {
                if (!f.done(t)) {
                    alive = true;
                    f.render(d, mc.level, t);
                }
            }
            if (!alive) {
                it.remove();
            }
        }
        imm.endBatch();
    }

    /** Tinte suave de pantalla: vineta verde clarita, solo si estas cerca de la escena. */
    public static void hud(GuiGraphics ctx, DeltaTracker counter) {
        Minecraft mc = Minecraft.getInstance();
        if (SCENES.isEmpty() || mc.player == null) {
            return;
        }
        float tickDelta = counter.getGameTimeDeltaPartialTick(true);
        float best = 0;
        for (Scene s : SCENES) {
            if (s.tint == null || mc.player.distanceToSqr(s.center) > 48 * 48) {
                continue;
            }
            float t = sceneTime(s, tickDelta);
            float[] k = s.tint;
            best = Math.max(best, s.tintMax * Draw.env(t, k[0], k[1], k[2], k[3]));
        }
        if (best <= 0.003f) {
            return;
        }
        VfxTextures.ensure();
        int w = ctx.guiWidth(), h = ctx.guiHeight();
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(0.55f, 1.0f, 0.70f, best);
        ctx.blit(VfxTextures.VIGNETTE, 0, 0, 0, 0, w, h, w, h);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }
}
