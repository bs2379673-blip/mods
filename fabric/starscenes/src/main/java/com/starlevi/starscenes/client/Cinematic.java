package com.starlevi.starscenes.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starlevi.starscenes.api.SceneActor;
import com.starlevi.starscenes.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * La parte del cliente de las escenas: la camara de cine (recorridos, paneos, orbitas, seguir a un NPC,
 * temblor), las barras negras, los fundidos, los titulos, los subtitulos de abajo y la musica. Todo sale
 * del json de la escena que manda el servidor; el tiempo corre aca.
 */
public final class Cinematic {
    public record Pose(Vec3 pos, float yaw, float pitch) {
    }

    private static final class Cam {
        double t, dur;
        Vec3 from, to, look, lookTo, offset;
        float[] turn, turnTo;
        String lookNpc, follow;
        boolean smooth = true;
        // orbita
        Vec3 center;
        double radius, height, angFrom, angTo;
        boolean orbit;
        // lo que habia al empezar este tramo (para los que no dicen desde donde)
        Vec3 startPos;
        float startYaw, startPitch;
        boolean started;
    }

    private record Timed(double t, double dur, JsonObject o) {
    }

    private static boolean active;
    private static String id = "";
    private static long startNs;
    private static Vec3 origin = Vec3.ZERO;
    private static final List<Cam> CAMS = new ArrayList<>();
    private static final List<Timed> OTHER = new ArrayList<>();
    private static boolean[] fired = new boolean[0];
    private static boolean bars, skippable;
    private static CameraType cameraBefore;
    private static Pose last;
    private static float firstYaw, firstPitch;
    private static Vec3 firstPos;
    // efectos en curso
    private static int fadeColor = 0x000000;
    private static float fadeFrom, fadeTo;
    private static double fadeAt, fadeDur;
    private static String title = "", subtitle = "", caption = "";
    private static double titleAt, titleDur, captionAt, captionDur;
    private static double shakeAt, shakeDur, shakeAmount;
    private static SoundInstance music;
    private static double skipHeld;
    private static long lastFrame;

    private Cinematic() {
    }

    public static boolean active() {
        return active;
    }

    private static double now() {
        return (System.nanoTime() - startNs) / 1e9;
    }

    private static double d(JsonObject o, String k, double def) {
        try {
            return o.has(k) ? o.get(k).getAsDouble() : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static String s(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : null;
    }

    private static Vec3 v(JsonElement e, boolean rel) {
        if (!(e instanceof JsonArray a) || a.size() < 3) {
            return null;
        }
        Vec3 p = new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
        return rel ? p.add(origin) : p;
    }

    private static float[] f2(JsonElement e) {
        if (!(e instanceof JsonArray a) || a.size() < 2) {
            return null;
        }
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat()};
    }

    public static void start(Net.SceneStart p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        stop();
        JsonObject json;
        try {
            json = JsonParser.parseString(p.json()).getAsJsonObject();
        } catch (Exception e) {
            return;
        }
        id = p.id();
        origin = new Vec3(p.ox(), p.oy(), p.oz());
        startNs = System.nanoTime();
        bars = !json.has("barras") || json.get("barras").getAsBoolean();
        skippable = !json.has("saltear") || json.get("saltear").getAsBoolean();
        firstPos = mc.gameRenderer.getMainCamera().position();
        firstYaw = mc.player.getYRot();
        firstPitch = mc.player.getXRot();
        last = new Pose(firstPos, firstYaw, firstPitch);
        CAMS.clear();
        OTHER.clear();
        if (json.get("pasos") instanceof JsonArray steps) {
            for (JsonElement e : steps) {
                if (!e.isJsonObject()) {
                    continue;
                }
                JsonObject o = e.getAsJsonObject();
                double t = d(o, "en", d(o, "t", 0));
                if (o.get("camara") instanceof JsonObject c) {
                    CAMS.add(cam(t, c));
                }
                OTHER.add(new Timed(t, d(o, "duracion", 0), o));
            }
        }
        CAMS.sort((a, b) -> Double.compare(a.t, b.t));
        OTHER.sort((a, b) -> Double.compare(a.t(), b.t()));
        fired = new boolean[OTHER.size()];
        fadeFrom = fadeTo = 0;
        fadeDur = 0;
        title = subtitle = caption = "";
        shakeDur = 0;
        skipHeld = 0;
        lastFrame = System.nanoTime();
        cameraBefore = mc.options.getCameraType();
        // asi Minecraft dibuja al jugador y no la mano ni la mira
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (mc.screen instanceof TalkScreen) {
            mc.setScreen(null);
        }
        active = true;
    }

    private static Cam cam(double t, JsonObject c) {
        boolean rel = true; // las del cliente vienen relativas al origen (que es 0,0,0 si la escena no es relativa)
        Cam k = new Cam();
        k.t = t;
        k.dur = d(c, "duracion", 0);
        k.from = v(c.get("desde"), rel);
        k.to = v(c.get("hasta"), rel);
        k.look = v(c.get("mirar"), rel);
        k.lookTo = v(c.get("mirar_hasta"), rel);
        k.turn = f2(c.get("girar"));
        k.turnTo = f2(c.get("girar_hasta"));
        k.lookNpc = s(c, "mirar_npc");
        k.follow = s(c, "seguir");
        k.offset = v(c.get("distancia"), false);
        k.smooth = !c.has("suave") || c.get("suave").getAsBoolean();
        if (c.get("orbitar") instanceof JsonObject o) {
            k.orbit = true;
            k.center = v(o.get("centro"), rel);
            k.radius = d(o, "radio", 5);
            k.height = d(o, "altura", 2);
            k.angFrom = d(o, "desde_angulo", 0);
            k.angTo = d(o, "hasta_angulo", k.angFrom + 90);
            if (k.center == null && o.has("npc")) {
                k.lookNpc = o.get("npc").getAsString();
            }
        }
        return k;
    }

    public static void stop() {
        if (!active) {
            return;
        }
        active = false;
        Minecraft mc = Minecraft.getInstance();
        if (cameraBefore != null) {
            mc.options.setCameraType(cameraBefore);
        }
        if (music != null) {
            mc.getSoundManager().stop(music);
            music = null;
        }
    }

    private static Entity npc(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (name == null || mc.level == null || mc.player == null) {
            return null;
        }
        if (name.equals("@jugador")) {
            return mc.player;
        }
        String low = name.toLowerCase(Locale.ROOT);
        Entity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof SceneActor && e.getName().getString().toLowerCase(Locale.ROOT).equals(low)) {
                double dd = e.distanceToSqr(mc.player);
                if (dd < bd) {
                    bd = dd;
                    best = e;
                }
            }
        }
        return best;
    }

    private static float ease(double k, boolean smooth) {
        float x = (float) Mth.clamp(k, 0, 1);
        return smooth ? x * x * (3 - 2 * x) : x;
    }

    /** Donde va la camara este cuadro (o null si no hay escena). */
    public static Pose camera(float partial) {
        if (!active) {
            return null;
        }
        double t = now();
        Cam c = null;
        for (Cam k : CAMS) {
            if (k.t <= t) {
                c = k;
            }
        }
        Pose p;
        if (c == null) {
            p = new Pose(firstPos, firstYaw, firstPitch);
        } else {
            if (!c.started) {
                c.started = true;
                c.startPos = last.pos();
                c.startYaw = last.yaw();
                c.startPitch = last.pitch();
            }
            float k = c.dur <= 0 ? 1 : ease((t - c.t) / c.dur, c.smooth);
            Vec3 pos;
            Entity follow = npc(c.follow);
            Entity target = npc(c.lookNpc);
            if (c.orbit) {
                Vec3 center = c.center != null ? c.center : target != null ? target.getPosition(partial) : c.startPos;
                double ang = Math.toRadians(c.angFrom + (c.angTo - c.angFrom) * k);
                pos = center.add(Math.sin(ang) * c.radius, c.height, Math.cos(ang) * c.radius);
                if (c.look == null && target == null) {
                    c.look = center.add(0, 1.4, 0);
                }
            } else if (follow != null) {
                Vec3 off = c.offset != null ? c.offset : new Vec3(0, 1.8, -4);
                float yr = (float) Math.toRadians(follow.getYRot());
                // la distancia es en el espacio del que se sigue (z = adelante, x = a su derecha)
                Vec3 rot = new Vec3(-Math.sin(yr) * off.z - Math.cos(yr) * off.x, off.y, Math.cos(yr) * off.z - Math.sin(yr) * off.x);
                pos = follow.getPosition(partial).add(rot);
                if (target == null && c.look == null) {
                    target = follow;
                }
            } else {
                Vec3 from = c.from != null ? c.from : c.startPos;
                pos = c.to != null ? from.lerp(c.to, k) : from;
            }
            float yaw, pitch;
            Vec3 look = null;
            if (target != null) {
                look = target.getEyePosition(partial);
            } else if (c.look != null) {
                look = c.lookTo != null ? c.look.lerp(c.lookTo, k) : c.look;
            }
            if (look != null) {
                Vec3 d = look.subtract(pos);
                yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
                pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
            } else if (c.turn != null) {
                yaw = c.turnTo != null ? Mth.rotLerp(k, c.turn[0], c.turnTo[0]) : c.turn[0];
                pitch = c.turnTo != null ? Mth.lerp(k, c.turn[1], c.turnTo[1]) : c.turn[1];
            } else {
                yaw = c.startYaw;
                pitch = c.startPitch;
            }
            p = new Pose(pos, yaw, pitch);
        }
        last = p;
        if (t - shakeAt < shakeDur) {
            double fall = 1 - (t - shakeAt) / shakeDur;
            float a = (float) (shakeAmount * fall);
            p = new Pose(p.pos(), p.yaw() + (float) Math.sin(t * 61) * a, p.pitch() + (float) Math.cos(t * 47) * a);
        }
        return p;
    }

    /** Cada cuadro: dispara los pasos del cliente (titulos, fundidos, musica...) y el saltear. */
    private static void frame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            stop();
            return;
        }
        double t = now();
        for (int i = 0; i < OTHER.size(); i++) {
            Timed x = OTHER.get(i);
            if (fired[i] || x.t() > t) {
                continue;
            }
            fired[i] = true;
            JsonObject o = x.o();
            double dur = x.dur();
            if (o.has("titulo") || o.has("subtitulo")) {
                title = o.has("titulo") ? o.get("titulo").getAsString().replace('&', '§') : "";
                subtitle = o.has("subtitulo") ? o.get("subtitulo").getAsString().replace('&', '§') : "";
                titleAt = x.t();
                titleDur = dur > 0 ? dur : 3;
            }
            if (o.has("texto")) {
                caption = o.get("texto").getAsString().replace('&', '§');
                captionAt = x.t();
                captionDur = dur > 0 ? dur : Math.max(2.5, caption.length() / 14.0);
            }
            if (o.has("fundido")) {
                String f = o.get("fundido").getAsString().toLowerCase(Locale.ROOT);
                float cur = fadeAlpha(t);
                fadeFrom = cur;
                fadeTo = f.equals("entrada") || f.equals("in") || f.equals("aclarar") ? 0 : 1;
                fadeAt = x.t();
                fadeDur = dur > 0 ? dur : 1;
                String col = s(o, "color");
                if (col != null) {
                    try {
                        fadeColor = Integer.parseInt(col.replace("#", ""), 16) & 0xFFFFFF;
                    } catch (Exception ignored) {
                    }
                } else if (fadeTo == 1) {
                    fadeColor = f.equals("blanco") ? 0xFFFFFF : 0x000000;
                }
            }
            if (o.has("barras")) {
                bars = o.get("barras").getAsBoolean();
            }
            if (o.has("temblor")) {
                shakeAmount = o.get("temblor").getAsDouble();
                shakeAt = x.t();
                shakeDur = dur > 0 ? dur : 0.8;
            }
            if (o.has("musica")) {
                if (music != null) {
                    mc.getSoundManager().stop(music);
                }
                mc.getMusicManager().stopPlaying();
                Identifier sid = Identifier.tryParse(o.get("musica").getAsString());
                if (sid != null) {
                    boolean loop = !o.has("repetir") || o.get("repetir").getAsBoolean();
                    music = new SimpleSoundInstance(sid, SoundSource.MUSIC, (float) d(o, "volumen", 1), (float) d(o, "tono", 1), RandomSource.create(), loop, 0,
                            SoundInstance.Attenuation.NONE, 0, 0, 0, true);
                    mc.getSoundManager().play(music);
                }
            }
            if (o.has("parar_musica") && music != null) {
                mc.getSoundManager().stop(music);
                music = null;
            }
            if (o.has("sonido")) {
                Identifier sid = Identifier.tryParse(o.get("sonido").getAsString());
                if (sid != null) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(sid), (float) d(o, "tono", 1), (float) d(o, "volumen", 1)));
                }
            }
        }
        long ns = System.nanoTime();
        double dt = (ns - lastFrame) / 1e9;
        lastFrame = ns;
        if (skippable && mc.screen == null && mc.options.keyShift.isDown()) {
            skipHeld += dt;
            if (skipHeld > 0.8) {
                skipHeld = -999;
                ClientPlayNetworking.send(new Net.SceneSkip());
            }
        } else if (skipHeld > 0) {
            skipHeld = 0;
        }
        // que no se vuelva a primera persona con F5 a mitad de escena
        if (mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    private static float fadeAlpha(double t) {
        if (fadeDur <= 0) {
            return fadeTo;
        }
        double k = Mth.clamp((t - fadeAt) / fadeDur, 0, 1);
        return (float) (fadeFrom + (fadeTo - fadeFrom) * k);
    }

    private static int alpha(float a, int rgb) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static float inOut(double t, double at, double dur) {
        double k = t - at;
        if (k < 0 || k > dur) {
            return 0;
        }
        return (float) Math.min(1, Math.min(k / 0.5, (dur - k) / 0.5));
    }

    private static float barsK;

    private static void hud(GuiGraphics g) {
        if (!active) {
            barsK = 0;
            return;
        }
        frame();
        if (!active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        double t = now();
        barsK += ((bars ? 1 : 0) - barsK) * 0.08f;
        int bh = (int) (Math.max(h * 0.12, 26) * barsK);
        if (bh > 0) {
            g.fill(0, 0, w, bh, 0xFF000000);
            g.fill(0, h - bh, w, h, 0xFF000000);
        }
        float ca = inOut(t, captionAt, captionDur);
        if (ca > 0.02f && !caption.isEmpty()) {
            int y = bh > 14 ? h - bh / 2 - 4 : h - 30;
            List<net.minecraft.util.FormattedCharSequence> lines = mc.font.split(Component.literal(caption), Math.min(w - 40, 360));
            y -= (lines.size() - 1) * 5;
            for (var line : lines) {
                g.drawCenteredString(mc.font, line, w / 2, y, alpha(ca, 0xFFFFFF));
                y += 10;
            }
        }
        float ta = inOut(t, titleAt, titleDur);
        if (ta > 0.02f) {
            if (!title.isEmpty()) {
                g.pose().pushMatrix();
                g.pose().translate(w / 2f, h / 2f - 18);
                g.pose().scale(2.2f, 2.2f);
                g.drawCenteredString(mc.font, title, 0, -4, alpha(ta, 0xFFFFFF));
                g.pose().popMatrix();
            }
            if (!subtitle.isEmpty()) {
                g.drawCenteredString(mc.font, subtitle, w / 2, h / 2 + 6, alpha(ta, 0xDDD6F0));
            }
        }
        float fa = fadeAlpha(t);
        if (fa > 0.002f) {
            g.fill(0, 0, w, h, alpha(fa, fadeColor));
        }
        if (skippable && t > 1) {
            String hint = skipHeld > 0 ? "Salteando..." : "Mantené Shift para saltear";
            g.drawString(mc.font, hint, w - mc.font.width(hint) - 6, h - 12, 0x80FFFFFF, true);
        }
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(Net.SceneStart.TYPE, (p, ctx) -> start(p));
        ClientPlayNetworking.registerGlobalReceiver(Net.SceneStop.TYPE, (p, ctx) -> stop());
        HudElementRegistry.addLast(Net.id("escena"), (g, delta) -> hud(g));
        // durante la escena no se ve el resto de la pantalla del juego
        for (Identifier el : new Identifier[]{VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR, VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR,
                VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL,
                VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.STATUS_EFFECTS, VanillaHudElements.CROSSHAIR, VanillaHudElements.SCOREBOARD,
                VanillaHudElements.CHAT, VanillaHudElements.BOSS_BAR, VanillaHudElements.OVERLAY_MESSAGE, Net.id("misiones")}) {
            try {
                HudElementRegistry.replaceElement(el, old -> (g, delta) -> {
                    if (!active) {
                        old.render(g, delta);
                    }
                });
            } catch (Exception ignored) {
                // si otro mod lo saco, no importa
            }
        }
    }
}
