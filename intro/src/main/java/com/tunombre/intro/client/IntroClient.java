package com.tunombre.intro.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Lado cliente: fundidos, barras de cine, subtitulos, camara libre por puntos, temblor y bloqueo de controles. */
public final class IntroClient {
    private static final Random RND = new Random();

    // fundido (0 = claro, 1 = negro)
    private static float fadeFrom, fadeTo, fadeCur;
    private static int fadeT, fadeDur;
    // barras
    private static float barsFrom, barsTo, barsCur;
    private static int barsT, barsDur;
    // subtitulo
    private static String subWho = "", subText = "";
    private static int subColor = 0xFFE0E0E0, subLeft, subTotal;
    // temblor
    private static float shakeForce;
    private static int shakeLeft, shakeTotal;
    // controles
    private static boolean locked;
    // camara
    private record Pt(double x, double y, double z, float yaw, float pitch, int ticks) { }
    private static List<Pt> path;
    private static int seg, segT;
    private static Entity camEntity;
    private static Entity prevCamera;

    private IntroClient() { }

    public static void init(IEventBus bus) { MinecraftForge.EVENT_BUS.register(IntroClient.class); }

    private static double d(JsonObject o, String k, double def) { return o.has(k) ? o.get(k).getAsDouble() : def; }

    private static boolean b(JsonObject o, String k, boolean def) { return o.has(k) ? o.get(k).getAsBoolean() : def; }

    public static void handle(String json) {
        JsonObject o;
        try { o = JsonParser.parseString(json).getAsJsonObject(); } catch (RuntimeException e) { return; }
        String op = o.get("op").getAsString();
        switch (op) {
            case "fundido" -> {
                fadeFrom = fadeCur;
                fadeTo = o.has("a") && o.get("a").getAsString().equals("claro") ? 0F : 1F;
                fadeDur = Math.max(1, (int) d(o, "ticks", 20));
                if (!b(o, "suave", true)) fadeDur = 1;
                fadeT = 0;
            }
            case "barras" -> {
                barsFrom = barsCur;
                barsTo = b(o, "on", true) ? 1F : 0F;
                barsDur = Math.max(1, (int) d(o, "ticks", 20));
                barsT = 0;
            }
            case "subtitulo" -> {
                subWho = o.has("quien") ? o.get("quien").getAsString() : "";
                subText = o.has("texto") ? o.get("texto").getAsString() : "";
                subTotal = subLeft = Math.max(10, (int) d(o, "ticks", 60));
                subColor = o.has("color") ? 0xFF000000 | Integer.parseInt(o.get("color").getAsString().replace("#", ""), 16) : 0xFFE0E0E0;
            }
            case "temblor" -> {
                shakeForce = (float) d(o, "fuerza", 1);
                shakeTotal = shakeLeft = Math.max(0, (int) d(o, "ticks", 40));
            }
            case "bloquear" -> locked = b(o, "on", true);
            case "camara" -> startCamera(o);
            case "camara_fin" -> stopCamera();
            case "reset" -> reset();
            default -> { }
        }
    }

    private static void startCamera(JsonObject o) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !o.has("puntos")) return;
        List<Pt> l = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("puntos")) {
            JsonObject p = e.getAsJsonObject();
            l.add(new Pt(d(p, "x", 0), d(p, "y", 0), d(p, "z", 0), (float) d(p, "yaw", 0), (float) d(p, "pitch", 0), (int) d(p, "ticks", 0)));
        }
        if (l.isEmpty()) return;
        if (camEntity == null || camEntity.level() != mc.level) {
            camEntity = EntityType.MARKER.create(mc.level);
            if (camEntity == null) return;
        }
        if (prevCamera == null) prevCamera = mc.getCameraEntity();
        path = l;
        seg = 0;
        segT = 0;
        Pt a = l.get(0);
        place(a.x, a.y, a.z, a.yaw, a.pitch, true);
        mc.setCameraEntity(camEntity);
    }

    private static void place(double x, double y, double z, float yaw, float pitch, boolean snap) {
        if (snap) { camEntity.xo = camEntity.xOld = x; camEntity.yo = camEntity.yOld = y; camEntity.zo = camEntity.zOld = z; camEntity.yRotO = yaw; camEntity.xRotO = pitch; }
        else { camEntity.xo = camEntity.xOld = camEntity.getX(); camEntity.yo = camEntity.yOld = camEntity.getY(); camEntity.zo = camEntity.zOld = camEntity.getZ(); camEntity.yRotO = camEntity.getYRot(); camEntity.xRotO = camEntity.getXRot(); }
        camEntity.setPos(x, y, z);
        camEntity.setYRot(yaw);
        camEntity.setXRot(pitch);
    }

    private static void stopCamera() {
        Minecraft mc = Minecraft.getInstance();
        path = null;
        if (prevCamera != null || mc.getCameraEntity() == camEntity) mc.setCameraEntity(mc.player);
        prevCamera = null;
    }

    private static void reset() {
        stopCamera();
        fadeCur = fadeFrom = fadeTo = 0;
        fadeT = fadeDur = 0;
        barsCur = barsFrom = barsTo = 0;
        barsT = barsDur = 0;
        subLeft = 0;
        shakeLeft = 0;
        locked = false;
    }

    private static float smooth(float t) { return t * t * (3 - 2 * t); }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (fadeT < fadeDur) { fadeT++; fadeCur = fadeFrom + (fadeTo - fadeFrom) * Math.min(1F, fadeT / (float) fadeDur); }
        if (barsT < barsDur) { barsT++; barsCur = barsFrom + (barsTo - barsFrom) * smooth(Math.min(1F, barsT / (float) barsDur)); }
        if (subLeft > 0) subLeft--;
        if (shakeLeft > 0) shakeLeft--;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (path != null && camEntity != null) {
            if (seg + 1 >= path.size()) {
                Pt last = path.get(path.size() - 1);
                place(last.x, last.y, last.z, last.yaw, last.pitch, false);
            } else {
                Pt a = path.get(seg), nx = path.get(seg + 1);
                segT++;
                float t = nx.ticks <= 0 ? 1F : Math.min(1F, segT / (float) nx.ticks);
                float k = smooth(t);
                float dy = nx.yaw - a.yaw;
                while (dy > 180) dy -= 360;
                while (dy < -180) dy += 360;
                place(a.x + (nx.x - a.x) * k, a.y + (nx.y - a.y) * k, a.z + (nx.z - a.z) * k,
                        a.yaw + dy * k, a.pitch + (nx.pitch - a.pitch) * k, false);
                if (t >= 1F) { seg++; segT = 0; }
            }
            if (mc.getCameraEntity() != camEntity) mc.setCameraEntity(camEntity);
        } else if (camEntity != null && mc.getCameraEntity() == camEntity) {
            mc.setCameraEntity(mc.player);
        }
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        if (shakeLeft <= 0) return;
        float k = shakeForce * (shakeLeft / (float) Math.max(1, shakeTotal));
        e.setYaw(e.getYaw() + (RND.nextFloat() - 0.5F) * 2.2F * k);
        e.setPitch(e.getPitch() + (RND.nextFloat() - 0.5F) * 2.2F * k);
        e.setRoll(e.getRoll() + (RND.nextFloat() - 0.5F) * 3F * k);
    }

    /** primero de todos: Epic Fight : Dancing cancela el emote al ver una tecla de movimiento, asi que tiene que verlas ya en cero */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMove(MovementInputUpdateEvent e) {
        if (!locked && path == null) return;
        var in = e.getInput();
        in.leftImpulse = 0;
        in.forwardImpulse = 0;
        in.up = in.down = in.left = in.right = in.jumping = in.shiftKeyDown = false;
    }

    /** bloqueado: no abre inventario ni el menu de emotes (el chat y la pausa si) */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreen(ScreenEvent.Opening e) {
        if (!locked) return;
        var sc = e.getNewScreen();
        if (sc == null || sc instanceof net.minecraft.client.gui.screens.ChatScreen || sc instanceof net.minecraft.client.gui.screens.PauseScreen
                || sc instanceof net.minecraft.client.gui.screens.DeathScreen) return;
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered e) {
        if (locked || path != null) { e.setCanceled(true); e.setSwingHand(false); }
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
        if (path == null && barsCur < 0.01F) return;
        var id = e.getOverlay();
        if (id == VanillaGuiOverlay.CHAT_PANEL.type()) return;
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        GuiGraphics g = e.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        float pt = e.getPartialTick();
        float bars = barsDur > 0 ? barsFrom + (barsTo - barsFrom) * smooth(Math.min(1F, (barsT + pt) / barsDur)) : barsCur;
        if (bars > 0.005F) {
            int bh = (int) (h * 0.12F * bars);
            g.fill(0, 0, w, bh, 0xFF000000);
            g.fill(0, h - bh, w, h, 0xFF000000);
        }
        if (subLeft > 0 && !subText.isEmpty()) {
            float a = Math.min(1F, Math.min(subLeft, subTotal - subLeft + 6) / 8F);
            int al = Math.max(4, (int) (255 * a)) << 24;
            int y = h - (int) (h * 0.12F * Math.max(bars, 0.5F)) - 34;
            var font = mc.font;
            int tw = font.width(subText);
            int bw = Math.max(tw, font.width(subWho)) + 24;
            g.fill(w / 2 - bw / 2, y - 14, w / 2 + bw / 2, y + 16, ((int) (150 * a) << 24));
            if (!subWho.isEmpty()) g.drawCenteredString(font, subWho.toUpperCase(), w / 2, y - 10, (subColor & 0xFFFFFF) | al);
            g.drawCenteredString(font, subText, w / 2, y + 2, 0xFFFFFF | al);
        }
        float f = fadeDur > 0 ? fadeFrom + (fadeTo - fadeFrom) * Math.min(1F, (fadeT + pt) / fadeDur) : fadeCur;
        if (f > 0.004F) g.fill(0, 0, w, h, ((int) (255 * Math.min(1F, f)) << 24));
    }
}
