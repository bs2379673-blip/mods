package com.tunombre.infectados.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ViewportEvent;

/** sacudida de camara mientras un infectado te tiene agarrado (el quick time lo dibuja el mod QuickTime Events) */
public final class HoldFx {
    private static volatile boolean active;
    private static volatile int kind;
    private static float k;

    private HoldFx() { }

    public static void set(boolean on, int kd) {
        active = on;
        kind = kd;
    }

    public static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        k += ((active ? 1F : 0F) - k) * 0.15F;
        if (k < 0.02F) return;
        Minecraft mc = Minecraft.getInstance();
        double t = e.getPartialTick() + (mc.player != null ? mc.player.tickCount : 0);
        float amp = (kind == 1 ? 1.6F : 1F) * k;
        e.setRoll(e.getRoll() + (float) Math.sin(t * 1.9) * 3F * amp);
        e.setPitch(e.getPitch() + (float) Math.sin(t * 2.7 + 1) * 1.2F * amp);
        e.setYaw(e.getYaw() + (float) Math.sin(t * 2.3 + 2) * 1.2F * amp);
    }
}
