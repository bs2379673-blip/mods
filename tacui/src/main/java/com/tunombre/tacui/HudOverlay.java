package com.tunombre.tacui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * HUD minimo, "tus ojos": solo la hotbar con el nivel, y encima unas lineas finas de vida / hambre / sed que se
 * apagan cuando no pasa nada y se encienden cuando cambian o estan bajas. Todo lo demas (brujula, hora, dia,
 * coordenadas, efectos) vive en el inventario, que hace de tableta.
 *
 * Rendimiento: cada g.fill() suelto es una llamada de dibujo; aqui todas las formas van juntas en un solo lote
 * (drawManaged) y despues, aparte, los objetos y el texto. Son ~40 rectangulos por fotograma en vez de cientos.
 */
public final class HudOverlay {
    private HudOverlay() {}

    /** Cada linea de vital recuerda su ultimo valor y cuando cambio, para encenderse un rato. */
    private static final class Vital {
        float last = -2, shown; long changedAt; float glow;
        void update(float v, long now, float dt, boolean alert) {
            if (last < -1) { last = v; shown = v; }
            if (Math.abs(v - last) > 0.004f) { changedAt = now; last = v; }
            shown += (v - shown) * Math.min(1f, dt * 10f);
            float target = alert || now - changedAt < 3500 ? 1f : 0.28f;
            glow += (target - glow) * Math.min(1f, dt * (target > glow ? 8f : 1.5f));
        }
    }

    private static final Vital HP = new Vital(), STA = new Vital(), FOOD = new Vital(), H2O = new Vital(), AIR = new Vital();
    private static float ghostHp = -1;
    private static long lastT;

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Layout lay = Layout.get();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (!lay.hud || p == null || p.isSpectator() || mc.options.hideGui || mc.screen instanceof TacInventoryScreen) return;
        Theme t = lay.theme();
        Font f = mc.font;

        long now = System.currentTimeMillis();
        float dt = lastT == 0 ? 0.016f : Math.min(0.1f, (now - lastT) / 1000f);
        lastT = now;

        float max = Math.max(1f, p.getMaxHealth()), hpF = Math.min(1f, p.getHealth() / max);
        boolean survival = !p.isCreative();
        HP.update(hpF, now, dt, hpF < 0.6f);
        if (ghostHp < 0 || hpF > ghostHp) ghostHp = hpF; else ghostHp += (hpF - ghostHp) * Math.min(1f, dt * 1.4f);
        float st = Mods.stamina(p);
        STA.update(Math.max(0, st), now, dt, st >= 0 && st < 0.999f);
        float food = p.getFoodData().getFoodLevel() / 20f;
        FOOD.update(food, now, dt, food < 0.5f);
        float th = Mods.thirst(p);
        H2O.update(Math.max(0, th), now, dt, th >= 0 && th < 0.5f);
        boolean underwater = p.isEyeInFluid(FluidTags.WATER) || p.getAirSupply() < p.getMaxAirSupply();
        float air = Math.max(0f, p.getAirSupply() / (float) p.getMaxAirSupply());
        AIR.update(air, now, dt, true);

        boolean low = hpF < 0.3f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 160.0);
        float glow = Math.max(low ? 0.25f + 0.35f * pulse : 0f, p.hurtTime > 0 ? p.hurtTime / (float) Math.max(1, p.hurtDuration) * 0.9f : 0f);

        Inventory inv = p.getInventory();
        int cx = w / 2 - 90, y = h - 21;                 // fila de la hotbar (casillas de 16 px cada 20)
        int vy = y - 7;                                  // lineas de vitales, justo encima
        Mods.Gun gun = Mods.gun(p);

        // ---------------- 1) todas las formas en un solo lote
        g.drawManaged(() -> {
            if (glow > 0.01f) edgeGlow(g, w, h, 0xC01818, glow);
            // hotbar
            g.fill(cx - 3, y - 3, cx + 183, h, 0x40000000);
            for (int i = 0; i < 9; i++) {
                boolean sel = i == inv.selected;
                int sx = cx + i * 20 + 2, sy = y - (sel ? 2 : 0);
                g.fill(sx - 1, sy - 1, sx + 17, sy + 17, sel ? Theme.alpha(t.accent, 45) : 0x88000000);
                g.fill(sx - 1, sy + 17, sx + 17, sy + 18, sel ? t.accent : Theme.alpha(t.dim, 200));
                if (sel) Draw.corners(g, sx - 3, sy - 3, 22, 22, 4, t.accent);
            }
            if (!p.getOffhandItem().isEmpty()) {
                int ox = cx - 22;
                g.fill(ox - 1, y - 1, ox + 17, y + 17, 0x88000000);
                g.fill(ox - 1, y + 17, ox + 17, y + 18, Theme.alpha(t.dim, 200));
            }
            // experiencia: la linea de abajo
            g.fill(cx, h - 2, cx + 180, h - 1, 0x66000000);
            g.fill(cx, h - 2, cx + Math.round(180 * p.experienceProgress), h - 1, 0xFF7CFF5A);

            if (survival) {
                // vida (mitad izquierda) con estela de dano; resistencia de Epic Fight justo encima
                int hw = 86;
                line(g, cx + 2, vy, hw, 3, HP.shown, low && pulse > 0.5f ? 0xFFFF6A6A : t.accent, HP.glow);
                int gw = Math.round(hw * ghostHp), fw = Math.round(hw * HP.shown);
                if (gw > fw) g.fill(cx + 2 + fw, vy, cx + 2 + gw, vy + 3, Theme.alpha(0xFFD0C8, (int) (200 * HP.glow)));
                float abs = p.getAbsorptionAmount();
                if (abs > 0) g.fill(cx + 2, vy - 2, cx + 2 + Math.round(hw * Math.min(1f, abs / max)), vy - 1, 0xFFE8C040);
                if (st >= 0 && STA.glow > 0.3f) line(g, cx + 2, vy - 3, hw, 1, STA.shown, 0xFFE8C040, STA.glow);
                // hambre y sed (mitad derecha); el oxigeno solo bajo el agua
                int rx = cx + 94, rw = 86;
                if (th >= 0) {
                    int half = (rw - 4) / 2;
                    line(g, rx, vy, half, 3, FOOD.shown, warn(food, pulse, 0xFFE59B3A), FOOD.glow);
                    line(g, rx + half + 4, vy, half, 3, H2O.shown, warn(th, pulse, 0xFF4FA8E8), H2O.glow);
                } else line(g, rx, vy, rw, 3, FOOD.shown, warn(food, pulse, 0xFFE59B3A), FOOD.glow);
                if (underwater) line(g, rx, vy - 3, rw, 1, AIR.shown, 0xFF8FD6F5, 1f);
            }
        });

        // ---------------- 2) objetos (cada uno se dibuja aparte, como en vainilla)
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.items.get(i);
            if (s.isEmpty()) continue;
            int sx = cx + i * 20 + 2, sy = y - (i == inv.selected ? 2 : 0);
            g.renderItem(s, sx, sy);
            g.renderItemDecorations(f, s, sx, sy);
            Integer rc = s.getRarity().color.getColor();
            if (s.getRarity() != Rarity.COMMON && rc != null) g.fill(sx, sy + 16, sx + 16, sy + 17, 0xFF000000 | rc);
        }
        ItemStack off = p.getOffhandItem();
        if (!off.isEmpty()) { g.renderItem(off, cx - 22, y); g.renderItemDecorations(f, off, cx - 22, y); }

        // ---------------- 3) texto (poco: nivel, municion y las cifras de los vitales cuando estan encendidos)
        if (p.experienceLevel > 0) Draw.text(g, f, "LV " + p.experienceLevel, cx + 186, y + 6, 0xFF9BFF7A, 0.65f);
        if (gun != null) {
            boolean empty = gun.mag == 0;
            String a = gun.mag + " / " + gun.reserve;
            int col = empty ? ((now / 300) % 2 == 0 ? 0xFFFF5050 : 0x00000000) : 0xFFFFFFFF;
            if (col != 0) Draw.text(g, f, empty ? "RECARGAR" : a, cx + 186, y - 4, col, 0.75f);
            if (!gun.mode.isEmpty()) Draw.text(g, f, gun.mode, cx + 186, y - 10, Theme.alpha(t.accent, 200), 0.5f);
        }
        if (survival) {
            label(g, f, "HP " + (int) Math.ceil(p.getHealth()), cx + 2, vy - 6, t.text, HP.glow);
            label(g, f, "HUN", cx + 94, vy - 6, t.text, FOOD.glow);
            if (th >= 0) label(g, f, "H2O", cx + 94 + 41 + 4, vy - 6, t.text, H2O.glow);
        }
    }

    private static int warn(float v, float pulse, int col) { return v < 0.25f && pulse > 0.5f ? 0xFFFF6A6A : col; }

    /** Linea de vital: fondo oscuro + relleno, con la opacidad del brillo actual. */
    private static void line(GuiGraphics g, int x, int y, int w, int h, float frac, int col, float glow) {
        int a = (int) (255 * Math.max(0.15f, Math.min(1f, glow)));
        g.fill(x, y, x + w, y + h, Theme.alpha(0x000000, a * 120 / 255));
        int fw = Math.round(w * Math.max(0f, Math.min(1f, frac)));
        if (fw > 0) g.fill(x, y, x + fw, y + h, Theme.alpha(col, a));
    }

    private static void label(GuiGraphics g, Font f, String s, int x, int y, int col, float glow) {
        if (glow < 0.45f) return;   // apagado: sin texto (y sin coste)
        Draw.text(g, f, s, x, y, Theme.alpha(col, (int) (220 * glow)), 0.5f);
    }

    /** Resplandor rojo en los bordes (poca vida / golpe): 8 franjas por lado, todo en el mismo lote. */
    private static void edgeGlow(GuiGraphics g, int w, int h, int rgb, float strength) {
        int n = 8, step = Math.max(2, Math.min(w, h) / 60);
        for (int i = 0; i < n; i++) {
            float k = 1f - i / (float) n;
            int a = (int) (strength * 130 * k * k);
            if (a <= 0) continue;
            int c = (a << 24) | (rgb & 0xFFFFFF), o = i * step;
            g.fill(0, o, w, o + step, c); g.fill(0, h - o - step, w, h - o, c);
            g.fill(o, 0, o + step, h, c); g.fill(w - o - step, 0, w - o, h, c);
        }
    }
}
