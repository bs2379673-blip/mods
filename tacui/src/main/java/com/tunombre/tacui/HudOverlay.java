package com.tunombre.tacui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** HUD tactico: estados, vida, hotbar, municion, resistencia y brujula. Sustituye al HUD de vainilla. */
public final class HudOverlay {
    private HudOverlay() {}

    private static ItemStack lastHeld = ItemStack.EMPTY;
    private static long heldChanged;
    private static float dispHp = -1, ghostHp = -1;
    private static long lastT;

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

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Layout lay = Layout.get();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (!lay.hud || p == null || p.isSpectator() || mc.options.hideGui || mc.screen instanceof TacInventoryScreen) return;
        Theme t = lay.theme();
        Font f = mc.font;
        Inventory inv = p.getInventory();

        int cw = 188, cx = w / 2 - cw / 2, hotY = h - 30;

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
        edgeGlow(g, w, h, 0xC01818, Math.max(low ? 0.25f + 0.35f * pulse : 0f, p.hurtTime > 0 ? p.hurtTime / (float) Math.max(1, p.hurtDuration) * 0.9f : 0f));

        // ---- hotbar
        Draw.panel(g, f, t, cx, hotY, cw, 26, "", false);
        for (int i = 0; i < 9; i++) {
            int sx = cx + 4 + i * 20 + 1, sy = hotY + 5;
            boolean sel = i == inv.selected;
            Draw.slot(g, t, sx, sy - (sel ? 2 : 0), false, sel);
            ItemStack s = inv.items.get(i);
            if (!s.isEmpty()) {
                g.renderItem(s, sx, sy - (sel ? 2 : 0)); g.renderItemDecorations(f, s, sx, sy - (sel ? 2 : 0));
                if (s.getRarity() != net.minecraft.world.item.Rarity.COMMON && s.getRarity().color.getColor() != null)
                    g.fill(sx, sy + 16 - (sel ? 2 : 0), sx + 16, sy + 17 - (sel ? 2 : 0), 0xFF000000 | s.getRarity().color.getColor());
            }
            Draw.text(g, f, Integer.toString(i + 1), sx + 1, sy + 1 - (sel ? 2 : 0), sel ? t.accent : Theme.alpha(t.text, 110), 0.55f);
        }
        ItemStack off = p.getOffhandItem();
        if (!off.isEmpty()) {
            int ox = cx - 28;
            Draw.panel(g, f, t, ox - 3, hotY, 24, 26, "", false);
            g.renderItem(off, ox, hotY + 5); g.renderItemDecorations(f, off, ox, hotY + 5);
        }
        // experiencia: linea fina bajo la hotbar y nivel a la derecha
        Draw.bar(g, cx, h - 3, cw, 1, p.experienceProgress, 0xFF7CFF5A, 0x66000000);
        if (p.experienceLevel > 0) Draw.text(g, f, "LV " + p.experienceLevel, cx + cw + 6, hotY + 10, 0xFF9BFF7A, 0.7f);

        // ---- vida: barra con segmentos, estela de dano y cifras
        int hy = hotY - 12;
        int hcol = low && pulse > 0.5f ? 0xFFFF6A6A : t.accent;
        g.fill(cx, hy, cx + cw, hy + 8, 0xCC120606);
        int gw = (int) (cw * ghostHp);
        if (ghostHp > dispHp) g.fill(cx + (int) (cw * dispHp), hy, cx + gw, hy + 8, 0xCCFFD0C8);
        g.fill(cx, hy, cx + (int) (cw * dispHp), hy + 8, hcol);
        g.fill(cx, hy, cx + (int) (cw * dispHp), hy + 2, 0x30FFFFFF);
        for (int i = 1; i < 10; i++) g.fill(cx + cw * i / 10, hy, cx + cw * i / 10 + 1, hy + 8, 0x66000000);
        Draw.frame(g, cx, hy, cw, 8, Theme.alpha(t.accent, 160));
        float abs = p.getAbsorptionAmount();
        if (abs > 0) Draw.bar(g, cx, hy - 2, cw, 2, Math.min(1f, abs / max), 0xFFE8C040, 0x00000000);
        Draw.text(g, f, "HP", cx + 3, hy + 1, 0xFFFFFFFF, 0.65f);
        Draw.textCenter(g, f, Math.round(hp / max * 100f) + "%", w / 2, hy + 1, 0xFFFFFFFF, 0.7f);
        Draw.textRight(g, f, Math.round(hp) + "/" + Math.round(max), cx + cw - 3, hy + 1, 0xFFFFFFFF, 0.65f);
        // postura
        String stance = p.isSprinting() ? "SPRINT" : p.isCrouching() ? "AGACHADO" : p.isSwimming() ? "NADANDO" : p.isFallFlying() ? "PLANEO" : "";
        if (!stance.isEmpty()) Draw.textCenter(g, f, stance, w / 2, 29, Theme.alpha(t.accent, 220), 0.6f);

        // ---- barras de estado (armadura, hambre, sed, aire)
        String[] names = new String[4]; float[] vals = new float[4]; int[] cols = new int[4]; int n = 0;
        names[n] = "ARM"; vals[n] = Math.min(1f, p.getArmorValue() / 20f); cols[n++] = 0xFF9AA7B5;
        names[n] = "HUN"; vals[n] = p.getFoodData().getFoodLevel() / 20f; cols[n++] = 0xFFE59B3A;
        float th = Mods.thirst(p);
        if (th >= 0) { names[n] = "H2O"; vals[n] = th; cols[n++] = 0xFF4FA8E8; }
        if (n < 4 && p.isEyeInFluid(FluidTags.WATER) || (n < 4 && p.getAirSupply() < p.getMaxAirSupply())) {
            names[n] = "O2"; vals[n] = Math.max(0f, p.getAirSupply() / (float) p.getMaxAirSupply()); cols[n++] = 0xFF8FD6F5;
        }
        int by = hy - 13, gap = 6, bw = (cw - gap * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            int bx = cx + i * (bw + gap);
            boolean warn = vals[i] < 0.25f && !names[i].equals("ARM");
            int col = warn && pulse > 0.5f ? 0xFFFF6A6A : cols[i];
            Draw.text(g, f, names[i], bx, by - 1, warn ? col : Theme.alpha(t.text, 170), 0.6f);
            Draw.textRight(g, f, Math.round(vals[i] * 100f) + "%", bx + bw, by - 1, t.text, 0.6f);
            g.fill(bx, by + 6, bx + bw, by + 9, 0x99000000);
            g.fill(bx, by + 6, bx + (int) (bw * vals[i]), by + 9, col);
            g.fill(bx, by + 6, bx + (int) (bw * vals[i]), by + 7, 0x40FFFFFF);
            for (int s = 1; s < 5; s++) g.fill(bx + bw * s / 5, by + 6, bx + bw * s / 5 + 1, by + 9, 0x88000000);
        }

        // ---- barras verticales arriba a la derecha: resistencia (Epic Fight) y cordura
        int vx = w - 16, vy = 10;
        float st = Mods.stamina(p), sa = Mods.sanity(p);
        if (st >= 0) {
            Draw.vbar(g, vx, vy, 3, 44, st, st < 0.2f && pulse > 0.5f ? 0xFFFF6A6A : 0xFFE8C040, 0x88000000);
            Draw.textCenter(g, f, "STA", vx + 1, vy + 46, Theme.alpha(t.text, 150), 0.45f); vx -= 9;
        }
        if (sa >= 0) {
            Draw.vbar(g, vx, vy, 3, 44, sa, sa < 0.25f && pulse > 0.5f ? 0xFFFF6A6A : 0xFFB07CFF, 0x88000000);
            Draw.textCenter(g, f, "SAN", vx + 1, vy + 46, Theme.alpha(t.text, 150), 0.45f);
        }

        // ---- municion (TACZ)
        Mods.Gun gun = Mods.gun(p);
        if (gun != null) {
            int ax = w - 18, ay = h - 40;
            g.fill(ax - 96, ay - 4, ax + 4, ay + 26, Theme.alpha(t.panel, 200));
            Draw.frame(g, ax - 96, ay - 4, 100, 30, Theme.alpha(t.accent, 120));
            g.renderItem(p.getMainHandItem(), ax - 92, ay + 2);
            String cur = String.format("%03d", gun.mag);
            Draw.textRight(g, f, cur, ax - 30, ay, gun.mag == 0 ? 0xFFFF5050 : 0xFFFFFFFF, 2.0f);
            Draw.textRight(g, f, "/" + String.format("%03d", gun.reserve), ax - 2, ay + 8, Theme.alpha(t.text, 190), 0.9f);
            if (!gun.mode.isEmpty()) Draw.textRight(g, f, gun.mode, ax - 2, ay + 18, t.accent, 0.6f);
        }

        // ---- brujula: cinta con marcas que se desplaza
        float heading = ((p.getYRot() + 180f) % 360f + 360f) % 360f;
        String[] dir = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};
        int cwid = 200, cx0 = w / 2 - cwid / 2;
        g.fill(cx0, 3, cx0 + cwid, 15, Theme.alpha(t.panel, 120));
        g.fill(cx0, 15, cx0 + cwid, 16, Theme.alpha(t.accent, 80));
        for (int b2 = 0; b2 < 360; b2 += 5) {
            float d2 = ((b2 - heading + 540f) % 360f) - 180f;
            if (Math.abs(d2) > 62f) continue;
            int x = cx0 + (int) ((d2 + 62f) / 124f * cwid);
            int al = (int) (255 * (1f - Math.abs(d2) / 62f));
            if (b2 % 45 == 0) Draw.textCenter(g, f, dir[b2 / 45], x, 5, Theme.alpha(b2 == 0 ? t.accent : t.text, al), 0.8f);
            else g.fill(x, b2 % 15 == 0 ? 9 : 11, x + 1, 14, Theme.alpha(t.text, al / 2));
        }
        g.fill(w / 2 - 1, 15, w / 2 + 1, 19, t.accent);
        Draw.textCenter(g, f, String.format("%03d°", Math.round(heading) % 360), w / 2, 20, Theme.alpha(t.text, 190), 0.65f);

        // ---- nombre del objeto en la mano (aparece unos segundos al cambiar)
        ItemStack held = p.getMainHandItem();
        if (!ItemStack.isSameItemSameTags(held, lastHeld)) { lastHeld = held.copy(); heldChanged = System.currentTimeMillis(); }
        long age = System.currentTimeMillis() - heldChanged;
        if (!held.isEmpty() && age < 2200) {
            int al = age < 1500 ? 255 : (int) (255 * (2200 - age) / 700f);
            Draw.textCenter(g, f, held.getHoverName().getString() + (held.getCount() > 1 ? "  x" + held.getCount() : ""), w / 2, by - 12, Theme.alpha(t.text, al), 0.9f);
        }
    }
}
