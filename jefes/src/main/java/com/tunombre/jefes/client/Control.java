package com.tunombre.jefes.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.tunombre.jefes.Boss;
import com.tunombre.jefes.JefeEntity;
import com.tunombre.jefes.Net;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** El administrador controla a un jefe: camara en tercera persona, teclas de acciones y lo que aprieta viaja al servidor. */
public final class Control {
    private static int entity = -1;
    private static final KeyMapping ROAR = key("roar", GLFW.GLFW_KEY_R);
    private static final KeyMapping GROWL = key("growl", GLFW.GLFW_KEY_G);
    private static final KeyMapping VOICE = key("voice", GLFW.GLFW_KEY_V);
    private static final KeyMapping SMASH = key("smash", GLFW.GLFW_KEY_F);
    private static final KeyMapping SNIFF = key("sniff", GLFW.GLFW_KEY_N);
    private static final KeyMapping LEAP = key("leap", GLFW.GLFW_KEY_X);
    private static final KeyMapping FREE = key("free", GLFW.GLFW_KEY_Z);
    private static final KeyMapping KILL = key("kill", GLFW.GLFW_KEY_K);
    private static final KeyMapping EXIT = key("exit", GLFW.GLFW_KEY_H);

    private Control() { }

    /** las teclas de los jefes solo existen mientras se controla uno: no chocan con las de ningun otro mod */
    private enum Ctx implements IKeyConflictContext {
        CTRL;

        @Override public boolean isActive() { return Control.active(); }

        @Override public boolean conflicts(IKeyConflictContext other) { return other == this; }
    }

    private static KeyMapping key(String n, int code) { return new KeyMapping("key.jefes." + n, Ctx.CTRL, InputConstants.Type.KEYSYM, code, "key.categories.jefes"); }

    private static java.util.Set<KeyMapping> own() { return java.util.Set.of(ROAR, GROWL, VOICE, SMASH, SNIFF, LEAP, FREE, KILL, EXIT); }

    /** mientras se controla un jefe (o se esta agarrado) ninguna otra tecla de ningun mod hace nada, salvo las imprescindibles */
    public static void suppressOthers(boolean victim) {
        Minecraft mc = Minecraft.getInstance();
        var o = mc.options;
        java.util.Set<KeyMapping> keep = new java.util.HashSet<>(java.util.List.of(o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keyChat, o.keyCommand, o.keyScreenshot, o.keyFullscreen));
        if (!victim) { keep.addAll(own()); keep.addAll(java.util.List.of(o.keySprint, o.keyShift, o.keyAttack, o.keyUse, o.keyPlayerList)); }
        for (KeyMapping k : o.keyMappings) {
            if (keep.contains(k)) continue;
            k.setDown(false);
            while (k.consumeClick()) { }
        }
    }

    public static void register(RegisterKeyMappingsEvent e) {
        for (KeyMapping k : new KeyMapping[]{ROAR, GROWL, VOICE, SMASH, SNIFF, LEAP, FREE, KILL, EXIT}) e.register(k);
    }

    public static boolean active() { return entity >= 0; }

    public static void set(int id) {
        entity = id;
        if (id < 0) CamHolder.release();
    }

    static JefeEntity boss() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getEntity(entity) instanceof JefeEntity j ? j : null;
    }

    public static void onAttackUse(boolean attack) { act(attack ? 1 : 5); }

    private static void act(int id) { Net.CH.sendToServer(new Net.Action(id)); }

    public static void tick() {
        if (entity < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { entity = -1; return; }
        JefeEntity j = boss();
        if (j == null) return;       // todavia no llego la entidad
        mc.player.setPos(j.getX(), j.getY(), j.getZ());
        mc.player.setDeltaMovement(Vec3.ZERO);
        var o = mc.options;
        float fwd = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
        float str = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
        Net.CH.sendToServer(new Net.Input(fwd, str, o.keyJump.isDown(), o.keySprint.isDown(), o.keyShift.isDown(), mc.player.getYRot(), mc.player.getXRot()));
        while (ROAR.consumeClick()) act(4);
        while (GROWL.consumeClick()) act(8);
        while (VOICE.consumeClick()) act(9);
        while (SMASH.consumeClick()) act(3);
        while (SNIFF.consumeClick()) act(6);
        while (LEAP.consumeClick()) act(10);
        while (FREE.consumeClick()) act(11);
        while (KILL.consumeClick()) act(12);
        while (EXIT.consumeClick()) act(7);
    }

    /** camara detras del jefe, mirando para donde mira el administrador */
    public static void camera(float pt) {
        Minecraft mc = Minecraft.getInstance();
        JefeEntity j = boss();
        if (j == null || mc.player == null) return;
        Boss b = j.getBoss();
        double dist = b == null ? 9 : b.spec.cameraDist;
        double eye = b == null ? 4 : b.spec.eye * 0.92;
        Vec3 target = j.getPosition(pt).add(0, eye, 0);
        float yaw = mc.player.getViewYRot(pt), pitch = Mth.clamp(mc.player.getViewXRot(pt), -75F, 75F);
        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        Vec3 want = target.subtract(look.scale(dist));
        var hit = mc.level.clip(new ClipContext(target, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, j));
        Vec3 pos = hit.getType() == HitResult.Type.MISS ? want : hit.getLocation().add(look.scale(0.35));
        CamHolder.place(pos, look);
    }

    public static void hud(GuiGraphics g, int w, int h) {
        if (entity < 0) return;
        Minecraft mc = Minecraft.getInstance();
        JefeEntity j = boss();
        var f = mc.font;
        int y = 6;
        String[] lines = {
                "§c§l" + (j == null ? "..." : j.displayName().toUpperCase()) + (j != null && j.getBoss() != null && j.getBoss().spec.blind ? " §7(ciego: solo reacciona a sonidos)" : ""),
                "§fWASD §7mover  §fShift §7agachado  §fCtrl §7correr  §fEspacio §7saltar",
                "§fClic izq §7zarpazo  §fClic der §7agarrar  §fF §7aplastar  §fX §7salto largo",
                "§fR §7rugido  §fG §7gruñido  §fV §7voz  §fN §7olfatear",
                "§fZ §7soltar/lanzar  §fK §7devorar ya  §fH §7dejar de controlar"};
        for (String s : lines) { g.drawString(f, s, 6, y, 0xFFFFFF, true); y += 11; }
        if (j != null && j.getAct() == com.tunombre.jefes.core.Act.HOLD) {
            String s = "§e§lTENES A UNA VICTIMA  §7(Z: lanzarla · K: devorarla)";
            g.drawString(f, s, w / 2 - f.width(s) / 2, h - 60, 0xFFFFFF, true);
        }
    }
}
