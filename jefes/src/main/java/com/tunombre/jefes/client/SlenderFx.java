package com.tunombre.jefes.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tunombre.jefes.Jefes;
import com.tunombre.jefes.JefeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;

import java.util.Random;

/**
 * Lo que le pasa a la pantalla de un jugador cerca de Slenderman: estatica, cortes horizontales, viñeta, temblor de camara y, si lo mira
 * de frente, destellos con su cara. Todo se calcula en el cliente a partir de donde esta el jefe y hacia donde mira el jugador.
 */
public final class SlenderFx {
    private static final ResourceLocation[] FLASH = {new ResourceLocation(Jefes.MODID, "textures/gui/slender_flash_0.png"), new ResourceLocation(Jefes.MODID, "textures/gui/slender_flash_1.png"), new ResourceLocation(Jefes.MODID, "textures/gui/slender_flash_2.png"), new ResourceLocation(Jefes.MODID, "textures/gui/slender_flash_3.png")};
    private static final Random R = new Random();
    private static float intensity, target;
    private static int burstLeft, burstTotal, flashLeft, tick, gameCollected, gameTotal;
    private static float burstStrength;
    private static boolean burstFlash, gameActive;

    private SlenderFx() { }

    public static void game(int collected, int total, boolean active) { gameCollected = collected; gameTotal = total; gameActive = active; SlenderMusic.game(collected, total, active); }

    public static void burst(int ticks, float strength, boolean flash) {
        if (strength <= 0.02F) return;
        burstLeft = Math.max(burstLeft, ticks); burstTotal = Math.max(burstTotal, ticks);
        burstStrength = Math.max(burstStrength * (burstLeft > ticks ? 1F : 0F), strength);
        if (flash) { burstFlash = true; flashLeft = 4; }
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(Jefes.SLENDER_STATIC.get(), 1.0F, Mth.clamp(strength, 0.2F, 1F)));
    }

    public static float level() { return Math.min(1F, Math.max(intensity, burstLeft > 0 ? burstStrength * burstLeft / Math.max(1, burstTotal) : 0F)); }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { intensity = 0; target = 0; return; }
        tick++;
        if (burstLeft > 0) { burstLeft--; if (burstLeft == 0) { burstStrength = 0; burstTotal = 0; burstFlash = false; } }
        if (flashLeft > 0) flashLeft--;
        float t = 0F;
        if (!Control.active() && !mc.player.isSpectator()) {
            Vec3 eye = mc.player.getEyePosition();
            Vec3 look = mc.player.getLookAngle();
            for (var en : mc.level.entitiesForRendering()) {
                if (!(en instanceof JefeEntity j) || !j.slender()) continue;
                Vec3 head = j.position().add(0, 3.6, 0);
                double d = head.distanceTo(eye);
                if (d > 48) continue;
                Vec3 to = head.subtract(eye).normalize();
                double dot = to.dot(look);
                float near = (float) Math.pow(Math.max(0, 1 - d / 48.0), 1.2);
                float lookf = 0F;
                if (dot > 0.5) {
                    var hit = mc.level.clip(new ClipContext(eye, head, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
                    boolean los = hit.getType() == HitResult.Type.MISS;
                    if (los) lookf = dot > 0.85 ? 1F : 0.4F;
                }
                float v = near * (0.3F + 0.7F * lookf);
                if (lookf > 0 && d < 14) v = Math.max(v, 0.6F + 0.4F * lookf * (float) (1 - d / 14));
                t = Math.max(t, v);
            }
            if (gameActive && gameTotal > 0) t = Math.max(t, 0.18F * gameCollected / gameTotal);
        }
        target = t;
        intensity += (target - intensity) * (target > intensity ? 0.12F : 0.04F);
        if (intensity < 0.003F) intensity = 0F;
        // flash de la cara: mirandolo muy de cerca o muy fuerte
        if (intensity > 0.62F && R.nextInt(Math.max(8, (int) (60 - (intensity - 0.6F) * 130))) == 0) flashLeft = 2 + R.nextInt(3);
        if (intensity > 0.3F && tick % 120 == 0) mc.getSoundManager().play(SimpleSoundInstance.forUI(Jefes.SLENDER_STATIC.get(), 0.8F + R.nextFloat() * 0.3F, Mth.clamp(intensity * 1.2F, 0.1F, 1F)));
    }

    public static void draw(GuiGraphics g, int w, int h) {
        float lv = level();
        if (lv < 0.02F) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // viñeta oscura que se cierra
        int va = (int) (Mth.clamp(lv, 0, 1) * 150);
        int band = (int) (h * (0.10F + 0.28F * lv));
        g.fillGradient(0, 0, w, band, va << 24, 0);
        g.fillGradient(0, h - band, w, h, 0, va << 24);
        int bw = (int) (w * (0.06F + 0.18F * lv));
        for (int i = 0; i < bw; i += 2) {
            int a = (int) (va * (1F - i / (float) bw) * 0.7F);
            g.fill(i, 0, i + 2, h, a << 24);
            g.fill(w - i - 2, 0, w - i, h, a << 24);
        }
        // lineas de barrido
        int sl = (int) (lv * 26);
        if (sl > 2) for (int y = 0; y < h; y += 4) g.fill(0, y, w, y + 1, sl << 24);
        // cortes horizontales de colores (como una señal que se rompe)
        int cuts = (int) (lv * 9);
        for (int i = 0; i < cuts; i++) {
            int y = R.nextInt(h), hh = 1 + R.nextInt(2 + (int) (lv * 14));
            int a = 30 + (int) (lv * 70);
            int col = switch (R.nextInt(4)) { case 0 -> 0x00FFFF; case 1 -> 0xFF2030; case 2 -> 0xFFFFFF; default -> 0x000000; };
            int x0 = R.nextInt(Math.max(1, w / 3)), x1 = w - R.nextInt(Math.max(1, w / 3));
            g.fill(x0, y, x1, y + hh, (a << 24) | col);
        }
        // ruido
        int dots = (int) (lv * lv * 380);
        for (int i = 0; i < dots; i++) {
            int x = R.nextInt(w), y = R.nextInt(h), s = 1 + R.nextInt(4), c = R.nextInt(200) + 30;
            g.fill(x, y, x + s, y + s, ((20 + (int) (lv * 80)) << 24) | (c << 16) | (c << 8) | c);
        }
        // apagon corto
        if (lv > 0.55F && R.nextInt(Math.max(6, (int) (40 - lv * 30))) == 0) g.fill(0, 0, w, h, 0xB0000000);
        // la cara: fotogramas del susto original, partidos en tiras que se desplazan
        if (flashLeft > 0) {
            float a = burstFlash ? 1F : 0.65F + 0.3F * lv;
            ResourceLocation tex = FLASH[R.nextInt(FLASH.length)];
            g.fill(0, 0, w, h, 0xFF000000);
            g.setColor(1F, 1F, 1F, a);
            int strips = 9;
            for (int j = 0; j < strips; j++) {
                int y0 = h * j / strips, y1 = h * (j + 1) / strips;
                int dx = R.nextInt(5) == 0 ? R.nextInt(41) - 20 : R.nextInt(5) - 2;
                g.blit(tex, dx - 24, y0, w + 48, y1 - y0, 0F, 360F * j / strips, 640, 360 / strips + 1, 640, 360);
            }
            g.setColor(1F, 1F, 1F, 1F);
            int nz = 260;
            for (int i = 0; i < nz; i++) { int x = R.nextInt(w), y = R.nextInt(h), c = R.nextInt(255); g.fill(x, y, x + 2 + R.nextInt(5), y + 1, 0x90000000 | (c << 16) | (c << 8) | c); }
        }
        RenderSystem.disableBlend();
    }

    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        float lv = level();
        if (lv < 0.05F) return;
        float k = lv * lv;
        e.setRoll(e.getRoll() + (R.nextFloat() - 0.5F) * 2.2F * k);
        e.setYaw(e.getYaw() + (R.nextFloat() - 0.5F) * 1.3F * k);
        e.setPitch(e.getPitch() + (R.nextFloat() - 0.5F) * 1.0F * k);
    }

    public static void fov(ViewportEvent.ComputeFov e) {
        float lv = level();
        if (lv < 0.2F) return;
        e.setFOV(e.getFOV() + (R.nextFloat() - 0.4F) * 5F * lv * lv);
    }
}
