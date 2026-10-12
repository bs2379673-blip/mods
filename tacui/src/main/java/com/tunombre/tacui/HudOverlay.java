package com.tunombre.tacui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD tactico "visor": vitales abajo a la izquierda (cifra grande, electrocardiograma, barras inclinadas),
 * hotbar minimal en el centro, arma / objeto abajo a la derecha, brujula en cinta, efectos y reloj arriba.
 */
public final class HudOverlay {
    private HudOverlay() {}

    private static float dispHp = -1, ghostHp = -1;
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
        float hp = p.getHealth(), max = Math.max(1f, p.getMaxHealth());
        float frac = Math.min(1f, hp / max);
        if (dispHp < 0) { dispHp = frac; ghostHp = frac; }
        dispHp += (frac - dispHp) * Math.min(1f, dt * 12f);
        if (frac > ghostHp) ghostHp = frac; else ghostHp += (frac - ghostHp) * Math.min(1f, dt * 1.4f);
        boolean low = frac < 0.3f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 160.0);
        edgeGlow(g, w, h, 0xC01818, Math.max(low ? 0.25f + 0.35f * pulse : 0f,
                p.hurtTime > 0 ? p.hurtTime / (float) Math.max(1, p.hurtDuration) * 0.9f : 0f));

        vitals(g, f, t, p, w, h, now, hp, max, low, pulse);
        hotbar(g, f, t, p, w, h);
        weapon(g, f, t, p, w, h, now);
        compass(g, f, t, p, w);
        effects(g, f, t, p, now);
        clock(g, f, t, p, w);
    }

    // ------------------------------------------------------------------ vitales (abajo a la izquierda)

    private static void vitals(GuiGraphics g, Font f, Theme t, LocalPlayer p, int w, int h, long now, float hp, float max, boolean low, float pulse) {
        int x0 = 14, base = h - 12;
        Draw.fadeRect(g, x0 - 8, base - 50, 200, 56, 130, 1);
        g.fill(x0 - 6, base - 46, x0 - 5, base + 2, Theme.alpha(t.accent, 200));
        g.fill(x0 - 8, base - 46, x0 - 5, base - 44, t.accent);
        Draw.text(g, f, "VITALS //", x0, base - 46, Theme.alpha(t.accent, 230), 0.5f);

        // cifra grande de vida
        int hcol = low && pulse > 0.5f ? 0xFFFF6A6A : 0xFFFFFFFF;
        String hs = Integer.toString((int) Math.ceil(hp));
        Draw.text(g, f, hs, x0, base - 38, hcol, 2.2f);
        int hw = (int) (f.width(hs) * 2.2f);
        Draw.text(g, f, "/" + Math.round(max), x0 + hw + 2, base - 37, Theme.alpha(t.text, 160), 0.6f);
        Draw.text(g, f, "HP", x0 + hw + 2, base - 29, t.accent, 0.6f);
        // electrocardiograma: mas rapido con poca vida
        ekg(g, x0, base - 7, 40, low ? 0xFFFF5050 : t.accent, low ? 2.4f : 1f, now);

        // barra de vida inclinada con estela de dano
        int x1 = x0 + 50, bw = 118;
        Draw.slant(g, x1, base - 37, bw, 8, 4, 0xCC140808);
        int gw = Math.round(bw * ghostHp), fw = Math.round(bw * dispHp);
        if (gw > fw) Draw.slant(g, x1 + fw, base - 37, gw - fw, 8, 4, 0xCCFFD0C8);
        Draw.slant(g, x1, base - 37, fw, 8, 4, low && pulse > 0.5f ? 0xFFFF6A6A : t.accent);
        Draw.slant(g, x1, base - 37, fw, 2, 1, 0x40FFFFFF);
        for (int s = 1; s < 10; s++) Draw.slant(g, x1 + bw * s / 10, base - 37, 1, 8, 4, 0x99000000);
        float abs = p.getAbsorptionAmount();
        if (abs > 0) Draw.slant(g, x1, base - 40, Math.round(bw * Math.min(1f, abs / max)), 2, 1, 0xFFE8C040);
        Draw.text(g, f, Math.round(hp / max * 100f) + "%", x1 + bw + 6, base - 36, Theme.alpha(t.text, 200), 0.6f);

        // resistencia de Epic Fight
        float st = Mods.stamina(p);
        if (st >= 0) {
            Draw.slantBar(g, x1, base - 26, bw, 2, 1, st, st < 0.2f && pulse > 0.5f ? 0xFFFF6A6A : 0xFFE8C040, 0x88000000, 1);
            Draw.text(g, f, "STA", x1 + bw + 6, base - 27, Theme.alpha(t.text, 150), 0.5f);
        }

        // armadura, hambre, sed, aire
        List<Object[]> bars = new ArrayList<>();
        bars.add(new Object[]{"ARM", Math.min(1f, p.getArmorValue() / 20f), 0xFF9AA7B5, Integer.toString(p.getArmorValue())});
        float food = p.getFoodData().getFoodLevel() / 20f;
        bars.add(new Object[]{"HUN", food, 0xFFE59B3A, Math.round(food * 100) + "%"});
        float th = Mods.thirst(p);
        if (th >= 0) bars.add(new Object[]{"H2O", th, 0xFF4FA8E8, Math.round(th * 100) + "%"});
        if (p.isEyeInFluid(FluidTags.WATER) || p.getAirSupply() < p.getMaxAirSupply()) {
            float air = Math.max(0f, p.getAirSupply() / (float) p.getMaxAirSupply());
            bars.add(new Object[]{"O2", air, 0xFF8FD6F5, Math.round(air * 100) + "%"});
        }
        int n = bars.size(), gap = 4, mw = (bw - gap * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            Object[] b = bars.get(i);
            float v = (Float) b[1];
            boolean warn = v < 0.25f && !"ARM".equals(b[0]);
            int col = warn && pulse > 0.5f ? 0xFFFF6A6A : (Integer) b[2];
            int bx = x1 + i * (mw + gap);
            Draw.text(g, f, (String) b[0], bx + 2, base - 19, warn ? col : Theme.alpha(t.text, 150), 0.5f);
            Draw.textRight(g, f, (String) b[3], bx + mw, base - 19, Theme.alpha(t.text, 230), 0.5f);
            Draw.slantBar(g, bx, base - 13, mw, 4, 2, v, col, 0x99000000, 4);
        }
    }

    /** Electrocardiograma que se desplaza hacia la izquierda; lo mas nuevo, a la derecha y mas brillante. */
    private static void ekg(GuiGraphics g, int x, int y, int w, int col, float speed, long now) {
        int period = 26;
        double ph = (now / 1000.0 * speed * 26.0) % period;
        int prev = 0;
        for (int i = 0; i < w; i++) {
            double u = (i + ph) % period;
            int dy = u < 12 ? 0 : u < 13 ? -1 : u < 14 ? 1 : u < 15 ? -6 : u < 16 ? 3 : u < 18 ? -1 : 0;
            int a = (int) (40 + 215 * (i / (float) w));
            int c = Theme.alpha(col, a);
            int y1 = Math.min(prev, dy), y2 = Math.max(prev, dy);
            g.fill(x + i, y + y1, x + i + 1, y + y2 + 1, c);
            prev = dy;
        }
    }

    // ------------------------------------------------------------------ hotbar (centro)

    private static void hotbar(GuiGraphics g, Font f, Theme t, LocalPlayer p, int w, int h) {
        Inventory inv = p.getInventory();
        int cx = w / 2 - 90, y = h - 26;
        g.fill(cx - 6, y - 4, cx + 186, h, 0x55000000);
        Draw.fadeRect(g, cx - 30, y - 4, 24, h - y + 4, 0x55, -1);
        Draw.fadeRect(g, cx + 186, y - 4, 24, h - y + 4, 0x55, 1);
        for (int i = 0; i < 9; i++) {
            boolean sel = i == inv.selected;
            int sx = cx + i * 20 + 2, sy = y - (sel ? 3 : 0);
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, sel ? Theme.alpha(t.accent, 45) : 0xAA000000);
            g.fill(sx - 1, sy + 17, sx + 17, sy + 18, sel ? t.accent : Theme.alpha(t.dim, 220));
            if (sel) Draw.corners(g, sx - 3, sy - 3, 22, 22, 4, t.accent);
            ItemStack s = inv.items.get(i);
            if (!s.isEmpty()) {
                g.renderItem(s, sx, sy);
                g.renderItemDecorations(f, s, sx, sy);
                Integer rc = s.getRarity().color.getColor();
                if (s.getRarity() != Rarity.COMMON && rc != null) g.fill(sx, sy + 16, sx + 16, sy + 17, 0xFF000000 | rc);
            }
            Draw.text(g, f, Integer.toString(i + 1), sx, sy - 1, sel ? t.accent : Theme.alpha(t.text, 90), 0.45f);
        }
        ItemStack off = p.getOffhandItem();
        if (!off.isEmpty()) {
            int ox = cx - 22;
            g.fill(ox - 1, y - 1, ox + 17, y + 17, 0xAA000000);
            g.fill(ox - 1, y + 17, ox + 17, y + 18, Theme.alpha(t.dim, 220));
            g.renderItem(off, ox, y);
            g.renderItemDecorations(f, off, ox, y);
        }
        g.fill(cx, h - 4, cx + 180, h - 3, 0x66000000);
        g.fill(cx, h - 4, cx + Math.round(180 * p.experienceProgress), h - 3, 0xFF7CFF5A);
        if (p.experienceLevel > 0) Draw.text(g, f, "LV " + p.experienceLevel, cx + 186, y + 5, 0xFF9BFF7A, 0.65f);
    }

    // ------------------------------------------------------------------ arma / objeto (abajo a la derecha)

    private static void weapon(GuiGraphics g, Font f, Theme t, LocalPlayer p, int w, int h, long now) {
        ItemStack held = p.getMainHandItem();
        if (held.isEmpty()) return;
        int rx = w - 14, base = h - 12;
        Draw.fadeRect(g, rx - 190, base - 50, 198, 56, 130, -1);
        g.fill(rx + 5, base - 46, rx + 6, base + 2, Theme.alpha(t.accent, 200));
        g.fill(rx + 5, base - 46, rx + 8, base - 44, t.accent);
        Mods.Gun gun = Mods.gun(p);
        String name = held.getHoverName().getString();
        if (name.length() > 26) name = name.substring(0, 26) + "…";
        if (gun != null) {
            Draw.textRight(g, f, "WEAPON //", rx, base - 46, Theme.alpha(t.accent, 230), 0.5f);
            String cur = String.format("%03d", gun.mag);
            boolean empty = gun.mag == 0;
            Draw.textRight(g, f, cur, rx, base - 38, empty ? 0xFFFF5050 : 0xFFFFFFFF, 2.2f);
            int nw = (int) (f.width(cur) * 2.2f);
            Draw.textRight(g, f, "/" + String.format("%03d", gun.reserve), rx - nw - 3, base - 26, Theme.alpha(t.text, 180), 0.7f);
            g.renderItem(held, rx - nw - 56, base - 39);
            if (!gun.mode.isEmpty()) {
                int mw = (int) (f.width(gun.mode) * 0.55f) + 6, mx = rx - nw - 3 - mw;
                g.fill(mx, base - 37, mx + mw, base - 30, 0xAA000000);
                Draw.frame(g, mx, base - 37, mw, 7, t.accent);
                Draw.text(g, f, gun.mode, mx + 3, base - 36, t.accent, 0.55f);
            }
            if (empty) {
                if ((now / 300) % 2 == 0) Draw.textRight(g, f, "RECARGAR", rx, base - 14, 0xFFFF5050, 0.7f);
            } else {
                int pips = Math.min(gun.mag, 40);
                for (int i = 0; i < pips; i++) g.fill(rx - i * 3 - 2, base - 13, rx - i * 3, base - 8, Theme.alpha(t.accent, 230));
            }
            Draw.textRight(g, f, name.toUpperCase(), rx, base - 4, Theme.alpha(t.text, 150), 0.5f);
        } else {
            Draw.textRight(g, f, "ITEM //", rx, base - 46, Theme.alpha(t.accent, 230), 0.5f);
            g.renderItem(held, rx - 16, base - 38);
            Draw.textRight(g, f, name, rx - 20, base - 34, 0xFFFFFFFF, 0.75f);
            if (held.isDamageableItem()) {
                float d = 1f - held.getDamageValue() / (float) Math.max(1, held.getMaxDamage());
                int col = d > 0.5f ? 0xFF6CE07A : d > 0.2f ? 0xFFE8C040 : 0xFFFF5050;
                Draw.slantBar(g, rx - 90, base - 18, 90, 3, 2, d, col, 0x99000000, 5);
                Draw.textRight(g, f, "DUR " + Math.round(d * 100) + "%", rx, base - 25, Theme.alpha(t.text, 200), 0.55f);
            } else if (held.getCount() > 1) {
                Draw.textRight(g, f, "x" + held.getCount(), rx, base - 22, 0xFFFFFFFF, 1.3f);
            }
        }
    }

    // ------------------------------------------------------------------ brujula (arriba en el centro)

    private static void compass(GuiGraphics g, Font f, Theme t, LocalPlayer p, int w) {
        float heading = ((p.getYRot() + 180f) % 360f + 360f) % 360f;
        String[] dir = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};
        int cwid = 220, cx0 = w / 2 - cwid / 2;
        for (int i = 0; i < cwid; i += 4) {
            int a = (int) (90 * (1f - Math.abs(i - cwid / 2f) / (cwid / 2f)));
            g.fill(cx0 + i, 15, cx0 + i + 3, 16, Theme.alpha(t.accent, a));
        }
        for (int b = 0; b < 360; b += 5) {
            float d = ((b - heading + 540f) % 360f) - 180f;
            if (Math.abs(d) > 62f) continue;
            int x = cx0 + (int) ((d + 62f) / 124f * cwid);
            int al = (int) (255 * (1f - Math.abs(d) / 62f));
            if (b % 45 == 0) Draw.textCenter(g, f, dir[b / 45], x, 5, Theme.alpha(b == 0 ? t.accent : t.text, al), 0.8f);
            else g.fill(x, b % 15 == 0 ? 9 : 11, x + 1, 14, Theme.alpha(t.text, al / 2));
        }
        for (int r = 0; r < 3; r++) g.fill(w / 2 - 2 + r, 15 + r, w / 2 + 3 - r, 16 + r, t.accent);
        g.fill(w / 2 - 14, 20, w / 2 + 14, 28, 0x88000000);
        Draw.frame(g, w / 2 - 14, 20, 28, 8, Theme.alpha(t.accent, 150));
        Draw.textCenter(g, f, String.format("%03d", Math.round(heading) % 360), w / 2, 21, 0xFFFFFFFF, 0.65f);
        String stance = p.isSprinting() ? "SPRINT" : p.isCrouching() ? "AGACHADO" : p.isSwimming() ? "NADANDO" : p.isFallFlying() ? "PLANEO" : "";
        if (!stance.isEmpty()) Draw.textCenter(g, f, stance, w / 2, 31, Theme.alpha(t.accent, 220), 0.55f);
    }

    // ------------------------------------------------------------------ efectos (arriba a la izquierda)

    private static void effects(GuiGraphics g, Font f, Theme t, LocalPlayer p, long now) {
        List<MobEffectInstance> list = new ArrayList<>(p.getActiveEffects());
        if (list.isEmpty()) return;
        var tex = Minecraft.getInstance().getMobEffectTextures();
        int x = 8, y = 8;
        for (int i = 0; i < list.size() && i < 12; i++) {
            MobEffectInstance e = list.get(i);
            if (!e.showIcon()) continue;
            boolean good = e.getEffect().isBeneficial();
            boolean ending = !e.isInfiniteDuration() && e.getDuration() < 200 && (now / 250) % 2 == 0;
            g.fill(x - 1, y - 1, x + 13, y + 13, 0xAA000000);
            Draw.frame(g, x - 1, y - 1, 14, 14, good ? Theme.alpha(t.accent, 180) : 0xFFC03030);
            if (!ending) g.blit(x, y, 0, 12, 12, tex.get(e.getEffect()));
            String d = MobEffectUtil.formatDuration(e, 1.0f).getString();
            Draw.textCenter(g, f, d, x + 6, y + 15, Theme.alpha(t.text, 200), 0.45f);
            if (e.getAmplifier() > 0) Draw.text(g, f, Integer.toString(e.getAmplifier() + 1), x + 9, y + 8, 0xFFFFFFFF, 0.45f);
            x += 17;
        }
    }

    // ------------------------------------------------------------------ dia y hora (arriba a la derecha)

    private static void clock(GuiGraphics g, Font f, Theme t, LocalPlayer p, int w) {
        long dayTime = p.level().getDayTime();
        long day = dayTime / 24000L + 1;
        int tod = (int) ((dayTime + 6000L) % 24000L);
        int hh = tod / 1000, mm = (tod % 1000) * 60 / 1000;
        boolean night = (dayTime % 24000L) >= 13000L && (dayTime % 24000L) < 23000L;
        Draw.fadeRect(g, w - 80, 4, 80, 22, 110, -1);
        Draw.textRight(g, f, "DIA " + day, w - 8, 7, Theme.alpha(t.text, 220), 0.7f);
        Draw.textRight(g, f, String.format("%02d:%02d", hh, mm), w - 8, 15, night ? 0xFFFF6A6A : Theme.alpha(t.text, 170), 0.6f);
        if (night) Draw.textRight(g, f, "NOCHE", w - 34, 15, 0xFFFF6A6A, 0.5f);
    }

    /** Resplandor rojo en los bordes de la pantalla (poca vida / golpe recibido). */
    private static void edgeGlow(GuiGraphics g, int w, int h, int rgb, float strength) {
        if (strength <= 0.01f) return;
        int n = Math.max(10, Math.min(w, h) / 10);
        for (int i = 0; i < n; i++) {
            float k = 1f - i / (float) n;
            int a = (int) (strength * 150 * k * k);
            if (a <= 0) continue;
            int c = (a << 24) | (rgb & 0xFFFFFF);
            g.fill(0, i, w, i + 1, c); g.fill(0, h - 1 - i, w, h - i, c);
            g.fill(i, 0, i + 1, h, c); g.fill(w - 1 - i, 0, w - i, h, c);
        }
    }
}
