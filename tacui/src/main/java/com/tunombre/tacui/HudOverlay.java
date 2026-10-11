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

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Layout lay = Layout.get();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (!lay.hud || p == null || p.isSpectator() || mc.options.hideGui) return;
        Theme t = lay.theme();
        Font f = mc.font;
        Inventory inv = p.getInventory();

        int cw = 188, cx = w / 2 - cw / 2, hotY = h - 30;

        // ---- hotbar
        g.fill(cx, hotY, cx + cw, hotY + 26, t.panel);
        Draw.frame(g, cx, hotY, cw, 26, Theme.alpha(t.accent, 120));
        for (int i = 0; i < 9; i++) {
            int sx = cx + 4 + i * 20 + 1, sy = hotY + 5;
            boolean sel = i == inv.selected;
            Draw.slot(g, t, sx, sy, false, sel);
            ItemStack s = inv.items.get(i);
            if (!s.isEmpty()) { g.renderItem(s, sx, sy); g.renderItemDecorations(f, s, sx, sy); }
            Draw.text(g, f, Integer.toString(i + 1), sx + 1, sy + 1, sel ? t.accent : Theme.alpha(t.text, 110), 0.55f);
        }
        ItemStack off = p.getOffhandItem();
        if (!off.isEmpty()) {
            int ox = cx - 26;
            g.fill(ox - 3, hotY, ox + 21, hotY + 26, t.panel);
            Draw.frame(g, ox - 3, hotY, 24, 26, Theme.alpha(t.accent, 120));
            g.renderItem(off, ox, hotY + 5); g.renderItemDecorations(f, off, ox, hotY + 5);
        }

        // ---- vida
        float hp = p.getHealth(), max = Math.max(1f, p.getMaxHealth());
        int hy = hotY - 11;
        boolean low = hp / max < 0.3f;
        int hcol = low && (System.currentTimeMillis() / 250) % 2 == 0 ? 0xFFFF6A6A : t.accent;
        Draw.bar(g, cx, hy, cw, 8, hp / max, hcol, 0xCC120606);
        float abs = p.getAbsorptionAmount();
        if (abs > 0) Draw.bar(g, cx, hy - 2, cw, 2, Math.min(1f, abs / max), 0xFFE8C040, 0x00000000);
        Draw.frame(g, cx, hy, cw, 8, Theme.alpha(t.accent, 140));
        Draw.textCenter(g, f, Math.round(hp / max * 100f) + "%", w / 2, hy + 1, 0xFFFFFFFF, 0.7f);

        // ---- barras de estado (armadura, hambre, sed, aire)
        String[] names = new String[4]; float[] vals = new float[4]; int[] cols = new int[4]; int n = 0;
        names[n] = "ARM"; vals[n] = Math.min(1f, p.getArmorValue() / 20f); cols[n++] = 0xFF9AA7B5;
        names[n] = "HUN"; vals[n] = p.getFoodData().getFoodLevel() / 20f; cols[n++] = 0xFFE59B3A;
        float th = Mods.thirst(p);
        if (th >= 0) { names[n] = "H2O"; vals[n] = th; cols[n++] = 0xFF4FA8E8; }
        if (n < 4 && p.isEyeInFluid(FluidTags.WATER) || (n < 4 && p.getAirSupply() < p.getMaxAirSupply())) {
            names[n] = "O2"; vals[n] = Math.max(0f, p.getAirSupply() / (float) p.getMaxAirSupply()); cols[n++] = 0xFF8FD6F5;
        }
        int by = hy - 12, gap = 6, bw = (cw - gap * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            int bx = cx + i * (bw + gap);
            Draw.text(g, f, names[i], bx, by - 1, Theme.alpha(t.text, 170), 0.6f);
            Draw.textRight(g, f, Math.round(vals[i] * 100f) + "%", bx + bw, by - 1, t.text, 0.6f);
            Draw.bar(g, bx, by + 6, bw, 2, vals[i], cols[i], 0x99000000);
        }

        // ---- barras verticales arriba a la derecha: resistencia (Epic Fight) y cordura
        int vx = w - 16, vy = 10;
        float st = Mods.stamina(p), sa = Mods.sanity(p);
        if (st >= 0) { Draw.vbar(g, vx, vy, 3, 44, st, 0xFFE8C040, 0x88000000); vx -= 7; }
        if (sa >= 0) { Draw.vbar(g, vx, vy, 3, 44, sa, 0xFFB07CFF, 0x88000000); }

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

        // ---- brujula
        String[] dir = {"S", "SO", "O", "NO", "N", "NE", "E", "SE"};
        float yaw = ((p.getYRot() % 360f) + 360f) % 360f;
        String d = dir[Math.round(yaw / 45f) % 8];
        Draw.textCenter(g, f, d + " " + String.format("%03d", Math.round((yaw + 180f) % 360f)) + "°", w / 2, 6, Theme.alpha(t.text, 190), 0.8f);
        g.fill(w / 2 - 1, 16, w / 2 + 1, 19, t.accent);

        // ---- nombre del objeto en la mano (aparece unos segundos al cambiar)
        ItemStack held = p.getMainHandItem();
        if (!ItemStack.isSameItemSameTags(held, lastHeld)) { lastHeld = held.copy(); heldChanged = System.currentTimeMillis(); }
        long age = System.currentTimeMillis() - heldChanged;
        if (!held.isEmpty() && age < 2200) {
            int al = age < 1500 ? 255 : (int) (255 * (2200 - age) / 700f);
            Draw.textCenter(g, f, held.getHoverName().getString(), w / 2, by - 12, Theme.alpha(t.text, al), 0.9f);
        }
    }
}
