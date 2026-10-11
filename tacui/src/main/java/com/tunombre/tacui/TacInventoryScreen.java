package com.tunombre.tacui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Inventario tactico. Usa el mismo menu del jugador (InventoryMenu), asi que las casillas que anaden otros mods
 * (mochilas, ropa de Survivors Arsenal...) siguen funcionando: solo se recolocan en pantalla mientras esta abierta.
 */
public class TacInventoryScreen extends AbstractContainerScreen<InventoryMenu> {
    private static Field FX, FY;
    private static final String CRAFT = "craft", EQUIP = "equip", HOTBAR = "hotbar", EXTRA = "extra";

    private final Player player;
    private final Map<Slot, int[]> original = new HashMap<>();
    private boolean edit;
    private String dragging; private int dragDX, dragDY;
    private int mouseX, mouseY;

    // rectangulos de los paneles (x, y, w, h) y botones, recalculados en cada fotograma
    private final Map<String, int[]> rects = new HashMap<>();
    private int[] btnSort, btnDrop, btnEdit, btnTheme;

    public TacInventoryScreen(Player p) {
        super(p.inventoryMenu, p.getInventory(), Component.translatable("container.crafting"));
        this.player = p;
    }

    private static void ensureFields() {
        if (FX != null) return;
        FX = ObfuscationReflectionHelper.findField(Slot.class, "f_40220_");
        FY = ObfuscationReflectionHelper.findField(Slot.class, "f_40221_");
    }

    private static void place(Slot s, int x, int y) {
        try { FX.setInt(s, x); FY.setInt(s, y); } catch (Throwable ignored) { }
    }

    @Override
    protected void init() {
        this.imageWidth = this.width;
        this.imageHeight = this.height;
        super.init();
        this.leftPos = 0;
        this.topPos = 0;
        ensureFields();
        original.clear();
        for (Slot s : menu.slots) original.put(s, new int[]{s.x, s.y});
        layout();
    }

    @Override
    public void removed() {
        for (Map.Entry<Slot, int[]> e : original.entrySet()) place(e.getKey(), e.getValue()[0], e.getValue()[1]);
        Layout.get().save();
        super.removed();
    }

    private boolean visibleOriginally(Slot s) {
        int[] o = original.get(s);
        return o != null && o[0] >= -50 && o[0] <= 400 && o[1] >= -50 && o[1] <= 400;
    }

    /** Calcula los paneles y coloca todas las casillas del menu. */
    private void layout() {
        Layout lay = Layout.get();
        int w = this.width, h = this.height;
        rects.clear();

        // ---- panel de equipo (inventario principal, 27 casillas en columnas)
        int rows = Math.max(4, Math.min(9, (h - 110) / 20));
        int cols = (27 + rows - 1) / rows;
        int ew = cols * 20 + 12, eh = 13 + 6 + 24 + rows * 20 + 6 + 16 + 6;
        int[] ep = lay.px(EQUIP, w - ew - 14, Math.max(6, (h - eh) / 2 - 10), w, h);
        ep = clamp(ep, ew, eh, w, h);
        rects.put(EQUIP, new int[]{ep[0], ep[1], ew, eh});
        btnSort = new int[]{ep[0] + ew - 36, ep[1] + 1, 34, 11};
        btnDrop = new int[]{ep[0] + 6, ep[1] + eh - 22, ew - 12, 16};
        for (int i = 9; i < 36; i++) {
            Slot s = menu.slots.get(i);
            int k = i - 9;
            place(s, ep[0] + 7 + (k % cols) * 20, ep[1] + 13 + 6 + 24 + (k / cols) * 20);
        }

        // ---- crafting
        int cw = 104, ch = 70;
        int[] cp = clamp(lay.px(CRAFT, 18, h / 3, w, h), cw, ch, w, h);
        rects.put(CRAFT, new int[]{cp[0], cp[1], cw, ch});
        place(menu.slots.get(1), cp[0] + 12, cp[1] + 22); place(menu.slots.get(2), cp[0] + 32, cp[1] + 22);
        place(menu.slots.get(3), cp[0] + 12, cp[1] + 42); place(menu.slots.get(4), cp[0] + 32, cp[1] + 42);
        place(menu.slots.get(0), cp[0] + 76, cp[1] + 32);

        // ---- hotbar
        int hw = 9 * 20 + 8, hh = 28;
        int[] hp = clamp(lay.px(HOTBAR, (w - hw) / 2, h - hh - 14, w, h), hw, hh, w, h);
        rects.put(HOTBAR, new int[]{hp[0], hp[1], hw, hh});
        for (int i = 0; i < 9; i++) place(menu.slots.get(36 + i), hp[0] + 5 + i * 20, hp[1] + 6);

        // ---- anillo de equipo alrededor del modelo: armadura, mano secundaria y casillas de otros mods
        List<Slot> ring = new ArrayList<>(), extra = new ArrayList<>();
        for (int i = 5; i <= 8; i++) ring.add(menu.slots.get(i));
        if (menu.slots.size() > 45) ring.add(menu.slots.get(45));
        for (int i = 46; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (!visibleOriginally(s)) continue;
            if (ring.size() < 10) ring.add(s); else extra.add(s);
        }
        int mx = w / 2, my = (int) (h * 0.46);
        int rx = Math.max(60, Math.min(w / 5, 170)), ry = Math.max(50, (int) (h * 0.27));
        int nl = (ring.size() + 1) / 2, nr = ring.size() / 2;
        for (int i = 0; i < ring.size(); i++) {
            boolean left = i % 2 == 0;
            int idx = i / 2, cnt = left ? nl : nr;
            double a = left ? Math.toRadians(120 + (cnt <= 1 ? 60 : 120.0 * idx / (cnt - 1)))
                            : Math.toRadians(-60 + (cnt <= 1 ? 60 : 120.0 * idx / (cnt - 1)));
            place(ring.get(i), (int) (mx + Math.cos(a) * rx) - 8, (int) (my + Math.sin(a) * ry) - 8);
        }
        // ---- panel de almacenamiento extra
        if (!extra.isEmpty()) {
            int xc = 4, xr = (extra.size() + xc - 1) / xc;
            int pw = xc * 20 + 12, ph = 13 + 8 + xr * 20 + 6;
            int[] xp = clamp(lay.px(EXTRA, 18, h / 3 + 80, w, h), pw, ph, w, h);
            rects.put(EXTRA, new int[]{xp[0], xp[1], pw, ph});
            for (int i = 0; i < extra.size(); i++) place(extra.get(i), xp[0] + 7 + (i % xc) * 20, xp[1] + 13 + 8 + (i / xc) * 20);
        }
        // ---- botones superiores
        btnEdit = new int[]{14, 24, 30, 11};
        btnTheme = new int[]{48, 24, 44, 11};
    }

    private static int[] clamp(int[] p, int pw, int ph, int sw, int sh) {
        return new int[]{Math.max(0, Math.min(p[0], sw - pw)), Math.max(0, Math.min(p[1], sh - ph))};
    }

    private static boolean in(int[] r, double x, double y) { return r != null && x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3]; }

    // ------------------------------------------------------------------ dibujo

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        this.mouseX = mx; this.mouseY = my;
        layout();   // las casillas de otros mods pueden aparecer o desaparecer
        Theme t = Layout.get().theme();
        int w = this.width, h = this.height;

        g.fill(0, 0, w, h, t.bg);
        Draw.vignette(g, w, h);
        Draw.scanlines(g, w, h);
        Draw.corners(g, 10, 10, w - 20, h - 20, 18, Theme.alpha(t.accent, 200));
        boolean blink = (System.currentTimeMillis() / 600) % 2 == 0;
        g.fill(14, 14, 18, 18, blink ? t.accent : Theme.alpha(t.accent, 70));
        Draw.text(g, this.font, "LIVE //TAC_STATUS_ACTIVE", 22, 13, Theme.alpha(t.text, 220), 0.8f);

        // modelo del jugador y anillo de equipo
        int mcx = w / 2, mcy = (int) (h * 0.46);
        Draw.corners(g, mcx - 52, mcy - (int) (h * 0.23), 104, (int) (h * 0.46), 8, Theme.alpha(t.accent, 160));
        for (Slot s : menu.slots) {
            if (!isRing(s)) continue;
            Draw.dotted(g, s.x + 8, s.y + 8, mcx, mcy, Theme.alpha(t.accent, 90), 5);
        }
        int scale = Math.max(30, h / 6);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, mcx, mcy + scale, scale, (float) (mcx - mx), (float) (mcy - scale - my), player);

        // paneles
        String[] titles = {"CRAFTING", "EQUIPMENT", "HOTBAR", "STORAGE"};
        String[] keys = {CRAFT, EQUIP, HOTBAR, EXTRA};
        for (int i = 0; i < keys.length; i++) {
            int[] r = rects.get(keys[i]);
            if (r == null) continue;
            Draw.panel(g, font, t, r[0], r[1], r[2], r[3], titles[i], edit || keys[i].equals(dragging));
        }
        int[] c = rects.get(CRAFT);
        if (c != null) Draw.textCenter(g, font, "→", c[0] + 62, c[1] + 36, t.accent, 1.2f);
        int[] e = rects.get(EQUIP);
        if (e != null) {
            // casilla de decoracion superior y botones
            Draw.text(g, font, "SLOTS " + 27, e[0] + 7, e[1] + 19, Theme.alpha(t.text, 130), 0.6f);
            button(g, t, btnSort, "SORT", false);
            button(g, t, btnDrop, "DROP", !menu.getCarried().isEmpty());
        }
        button(g, t, btnEdit, "EDIT", edit);
        button(g, t, btnTheme, Layout.get().theme, false);

        // marcos de todas las casillas visibles
        for (Slot s : menu.slots) {
            if (!s.isActive()) continue;
            if (!visibleOriginally(s) && s.index >= 46) continue;
            boolean hov = mx >= s.x - 1 && mx < s.x + 17 && my >= s.y - 1 && my < s.y + 17;
            Draw.slot(g, t, s.x, s.y, hov, false);
        }
        if (edit) {
            Draw.textCenter(g, font, "EDIT MODE  |  ARRASTRA LOS PANELES  |  R REINICIAR  |  ESC GUARDAR", w / 2, 6, t.accent, 0.8f);
        }
    }

    private boolean isRing(Slot s) {
        int i = s.index;
        return (i >= 5 && i <= 8) || (i >= 45 && visibleOriginally(s) && !inExtra(s));
    }

    private boolean inExtra(Slot s) {
        int[] r = rects.get(EXTRA);
        return r != null && s.x >= r[0] && s.x < r[0] + r[2] && s.y >= r[1] && s.y < r[1] + r[3];
    }

    private void button(GuiGraphics g, Theme t, int[] r, String label, boolean active) {
        if (r == null) return;
        boolean hov = in(r, mouseX, mouseY);
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], active ? Theme.alpha(t.accent, 90) : 0xAA000000);
        Draw.frame(g, r[0], r[1], r[2], r[3], hov || active ? t.accent : Theme.alpha(t.dim, 230));
        Draw.textCenter(g, font, label, r[0] + r[2] / 2, r[1] + (r[3] - 6) / 2 + 1, hov || active ? t.text : Theme.alpha(t.text, 170), 0.7f);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) { /* sin etiquetas de vainilla */ }

    // ------------------------------------------------------------------ entrada

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (in(btnEdit, mx, my)) { edit = !edit; if (!edit) Layout.get().save(); return true; }
            if (in(btnTheme, mx, my)) { Layout lay = Layout.get(); lay.theme = lay.theme().next().name(); lay.save(); return true; }
            if (edit) {
                for (String k : new String[]{EQUIP, CRAFT, HOTBAR, EXTRA}) {
                    int[] r = rects.get(k);
                    if (in(r, mx, my)) { dragging = k; dragDX = (int) mx - r[0]; dragDY = (int) my - r[1]; return true; }
                }
                return true;
            }
            if (in(btnSort, mx, my)) { sort(); return true; }
            if (in(btnDrop, mx, my)) { drop(); return true; }
        }
        if (edit) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            Layout.get().setPx(dragging, (int) mx - dragDX, (int) my - dragDY, width, height);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null) { dragging = null; Layout.get().save(); return true; }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (edit) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { edit = false; Layout.get().save(); return true; }
            if (key == GLFW.GLFW_KEY_R) { Layout.get().pos.clear(); Layout.get().save(); return true; }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // ------------------------------------------------------------------ acciones

    private void drop() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null && !menu.getCarried().isEmpty())
            mc.gameMode.handleInventoryMouseClick(menu.containerId, -999, 0, ClickType.PICKUP, player);
    }

    private void click(int slot) {
        Minecraft.getInstance().gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, player);
    }

    private static String key(ItemStack s) {
        return s.isEmpty() ? "~" : BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    }

    /** Ordena el inventario principal (huecos 9..35): junta pilas iguales y ordena por objeto. */
    private void sort() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || !menu.getCarried().isEmpty()) return;
        // 1) juntar pilas
        for (int i = 9; i < 36; i++) {
            ItemStack a = menu.slots.get(i).getItem();
            if (a.isEmpty() || !a.isStackable()) continue;
            for (int j = i + 1; j < 36 && a.getCount() < a.getMaxStackSize(); j++) {
                ItemStack b = menu.slots.get(j).getItem();
                if (b.isEmpty() || !ItemStack.isSameItemSameTags(a, b)) continue;
                click(j); click(i);
                if (!menu.getCarried().isEmpty()) click(j);
                a = menu.slots.get(i).getItem();
            }
        }
        // 2) ordenar (seleccion): el que corresponde en cada hueco se trae con tres clics
        for (int i = 9; i < 36; i++) {
            int best = i;
            for (int j = i + 1; j < 36; j++) {
                ItemStack bj = menu.slots.get(j).getItem(), bb = menu.slots.get(best).getItem();
                int cmp = key(bj).compareTo(key(bb));
                if (cmp < 0 || (cmp == 0 && bj.getCount() > bb.getCount())) best = j;
            }
            if (best != i) { click(best); click(i); click(best); }
        }
    }
}
