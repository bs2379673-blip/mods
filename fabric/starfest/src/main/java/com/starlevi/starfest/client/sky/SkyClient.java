package com.starlevi.starfest.client.sky;

import com.starlevi.starfest.StarFest;
import com.starlevi.starfest.client.Beat;
import com.starlevi.starfest.client.Show;
import com.starlevi.starfest.net.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Cliente: cielos activos que manda el servidor. Se ve el de la zona en la que estas (con fundido al
 * entrar y salir) y reacciona a la musica que suena ahi.
 */
public final class SkyClient {
    private static final class Active {
        final Net.Sky msg;
        final SkyDef def;
        final Vec3 center;
        final Map<String, String> files = new HashMap<>();

        Active(Net.Sky msg, SkyDef def) {
            this.msg = msg;
            this.def = def;
            this.center = new Vec3(msg.x() + 0.5, msg.y(), msg.z() + 0.5);
            for (Net.SkyImage i : msg.images()) {
                this.files.put(i.file(), i.hash());
            }
        }
    }

    private static final Map<String, Active> ACTIVE = new HashMap<>();
    private static Active shown;
    private static float weight;
    private static long lastNanos;

    private SkyClient() {
    }

    public static void sky(Net.Sky p) {
        SkyDef def;
        try {
            def = new SkyDef(p.name(), p.json());
        } catch (Exception e) {
            StarFest.LOGGER.warn("StarFest: el cielo '{}' tiene un error: {}", p.name(), e.toString());
            return;
        }
        Active a = new Active(p, def);
        ACTIVE.put(p.zone(), a);
        if (shown != null && shown.msg.zone().equals(p.zone())) {
            shown = a; // cambio del mismo cielo (o se edito el archivo): sin fundido
        }
        for (Net.SkyImage i : p.images()) {
            if (SkyImages.byHash(i.hash()) == null) {
                Show.request(i.hash(), i.bytes(), data -> Minecraft.getInstance().execute(() -> SkyImages.load(i.hash(), i.file(), data)));
            }
        }
        StarFest.LOGGER.info("StarFest: cielo '{}' ({} capas, {} imagenes)", p.name(), def.layers.size(), p.images().size());
    }

    public static void stop(Net.SkyStop p) {
        ACTIVE.remove(p.zone());
    }

    public static void clear() {
        ACTIVE.clear();
        shown = null;
        weight = 0;
    }

    /** Antes de las entidades: el cielo va como una cupula lejana (el terreno, que esta mas cerca, queda adelante). */
    public static void render(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ACTIVE.isEmpty() && shown == null) {
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        Active best = null;
        double bestD = Double.MAX_VALUE;
        for (Active a : ACTIVE.values()) {
            double d = a.center.distanceToSqr(cam);
            double r = a.msg.radius();
            if (d <= r * r && d < bestD) {
                best = a;
                bestD = d;
            }
        }
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0 : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;
        if (best != null && (shown == null || shown == best)) {
            shown = best;
            weight = Math.min(1, weight + dt / 1.5f);
        } else {
            weight = Math.max(0, weight - dt / 1.5f);
            if (weight <= 0) {
                shown = best;
            }
        }
        if (shown == null || weight <= 0) {
            return;
        }
        Active a = shown;
        Beat b = Show.beatAt(BlockPos.containing(cam));
        float w = weight * weight * (3 - 2 * weight);
        // mas lejos que todo el terreno que se dibuja (y adentro de lo que alcanza la camara)
        float radius = Math.max(100, Math.min(1000, mc.options.getEffectiveRenderDistance() * 16 + 48));
        com.mojang.blaze3d.vertex.PoseStack ps = ctx.matrices();
        ps.pushPose();
        SkyRenderer.render(ctx.commandQueue(), ps, radius, a.def, b, w, file -> {
            String h = a.files.get(file);
            return h == null ? null : SkyImages.byHash(h);
        });
        ps.popPose();
    }
}
