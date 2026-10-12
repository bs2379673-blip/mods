package com.tunombre.tacui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Inventario tactico. Usa el mismo menu del jugador (InventoryMenu), asi que las casillas que anaden otros mods
 * (mochilas, ropa de Survivors Arsenal...) siguen funcionando: solo se recolocan en pantalla mientras esta abierta.
 */
public class TacInventoryScreen extends AbstractContainerScreen<AbstractContainerMenu> {
    public enum Mode { INVENTORY, CURIOS, COSMETIC }

    private static Field FX, FY;
    private static final String CRAFT = "craft", EQUIP = "equip", HOTBAR = "hotbar", EXTRA = "extra", ITEMS = "items", CURIOS = "curios", BACKPACK = "backpack", CLOTHES = "clothes";

    private final Player player;
    private final Map<Slot, int[]> original = new HashMap<>();
    private boolean edit;
    private String dragging; private int dragDX, dragDY;
    private int mouseX, mouseY;

    // rectangulos de los paneles (x, y, w, h) y botones, recalculados en cada fotograma
    private final Map<String, int[]> rects = new HashMap<>();
    private int[] btnSort, btnDrop, btnEdit, btnTheme;
    private final Mode mode;
    private final boolean creative;

    /** Casilla de solo lectura de otro menu (Curios / cosmetica): al pulsarla se cambia a su pestana. */
    private static final class Ghost {
        final Mode tab; final String label; final ItemStack stack; int x, y;
        Ghost(Mode tab, String label, ItemStack stack) { this.tab = tab; this.label = label; this.stack = stack; }
    }
    private final List<Ghost> ghosts = new ArrayList<>();
    private final List<int[]> ringPts = new ArrayList<>();
    /** Mini boton en la esquina de una casilla (ocultar armadura cosmetica / no dibujar un curio), como en los mods originales. */
    private static final class Mini {
        final int x, y; final boolean on; final String tip; final Runnable act;
        Mini(int x, int y, boolean on, String tip, Runnable act) { this.x = x; this.y = y; this.on = on; this.tip = tip; this.act = act; }
        boolean in(double mx, double my) { return mx >= x && mx < x + 7 && my >= y && my < y + 7; }
    }
    private final List<Mini> minis = new ArrayList<>();

    // ---- modelo giratorio
    private float rotYaw, rotPitch, zoom = 1f;
    private boolean rotating;
    private long lastModelClick;
    private int[] modelRect, btnReset;
    private final long openedAt = System.currentTimeMillis();
    private final List<Object[]> tabBtns = new ArrayList<>();    // {texto, int[] rect, Mode}

    private int workW() {
        if (!EmiCompat.loaded()) return this.width;
        return this.width - Math.min(9 * 18 + 12, (int) (this.width * 0.38));   // el resto lo usa el indice de EMI
    }

    private static int xOf(Object o) { return o instanceof Slot s ? s.x : ((Ghost) o).x; }
    private static int yOf(Object o) { return o instanceof Slot s ? s.y : ((Ghost) o).y; }
    private static void put(Object o, int x, int y) {
        if (o instanceof Slot s) place(s, x, y); else { Ghost g = (Ghost) o; g.x = x; g.y = y; }
    }

    // ---- modo creativo
    private EditBox search;
    private String query = "";
    private final List<CreativeModeTab> cats = new ArrayList<>();
    private List<ItemStack> allItems = new ArrayList<>();
    private List<ItemStack> shown = new ArrayList<>();
    private final Map<ItemStack, String> keyCache = new IdentityHashMap<>();
    private int cat = -1, scroll, gridRows = 6;
    private int gridX, gridY;
    private ItemStack held = ItemStack.EMPTY;
    private long clearArmed;
    private int[] btnPrev, btnNext, btnDel, btnClear;

    // ---- Curios
    private final List<Object[]> curioLabels = new ArrayList<>();   // {x, y, texto}
    private int[] btnCosm, btnPgPrev, btnPgNext;
    private final List<Object[]> ringLabels = new ArrayList<>();   // {x, y, texto} (centrado)
    private final List<Object[]> saLabels = new ArrayList<>();     // etiquetas del panel de ropa
    private int pgX, pgY;

    public TacInventoryScreen(Player p) { this(p, p.inventoryMenu, Mode.INVENTORY); }

    public TacInventoryScreen(Player p, AbstractContainerMenu menu, Mode mode) {
        super(menu, p.getInventory(), Component.translatable("container.crafting"));
        this.player = p;
        this.mode = mode;
        this.creative = p.isCreative();
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
        this.imageWidth = workW();   // EMI y compania usan este ancho para saber donde queda sitio libre
        this.imageHeight = this.height;
        super.init();
        this.leftPos = 0;
        this.topPos = 0;
        ensureFields();
        recordOriginals();
        if (creative) initCreative();
        layout();
    }

    private void recordOriginals() {
        for (Slot s : menu.slots) original.putIfAbsent(s, new int[]{s.x, s.y});
    }

    @Override
    public void removed() {
        for (Map.Entry<Slot, int[]> e : original.entrySet()) place(e.getKey(), e.getValue()[0], e.getValue()[1]);
        Layout.get().save();
        super.removed();
    }

    private boolean visibleOriginally(Slot s) {
        if (mode != Mode.INVENTORY) return s.isActive();
        int[] o = original.get(s);
        return o != null && o[0] >= -50 && o[0] <= 400 && o[1] >= -50 && o[1] <= 400;
    }

    private void offscreen(Slot s) { place(s, -200, -200); }

    /** Calcula los paneles y coloca todas las casillas del menu. */
    private void layout() {
        Layout lay = Layout.get();
        int w = this.width, h = this.height;
        rects.clear();
        curioLabels.clear(); ringLabels.clear(); saLabels.clear(); ringPts.clear(); ghosts.clear(); minis.clear(); tabBtns.clear();
        int ww = workW();
        recordOriginals();
        btnCosm = btnPgPrev = btnPgNext = null;

        // ---- panel de equipo (inventario principal, 27 casillas en columnas)
        int rows = Math.max(4, Math.min(9, (h - 110) / 20));
        int cols = (27 + rows - 1) / rows;
        int ew = Math.max(cols * 20 + 12, 98), eh = 13 + 6 + 24 + rows * 20 + 6 + 16 + 6;
        int[] ep = lay.px(EQUIP, ww - ew - 14, Math.max(6, (h - eh) / 2 - 10), w, h);
        ep = clamp(ep, ew, eh, w, h);
        rects.put(EQUIP, new int[]{ep[0], ep[1], ew, eh});
        btnSort = new int[]{ep[0] + ew - 36, ep[1] + 1, 34, 11};
        btnDrop = new int[]{ep[0] + 6, ep[1] + eh - 22, ew - 12, 16};
        for (int i = 9; i < 36; i++) {
            Slot s = menu.slots.get(i);
            int k = i - 9;
            place(s, ep[0] + (ew - ((cols - 1) * 20 + 16)) / 2 + (k % cols) * 20, ep[1] + 13 + 6 + 24 + (k / cols) * 20);
        }

        // ---- crafting (no hace falta en creativo)
        int cw = 104, ch = 70;
        if (creative) {
            for (int i = 0; i <= 4; i++) offscreen(menu.slots.get(i));
        } else {
            int defY = h - ch - 14 - (EmiCompat.loaded() ? 26 : 0);
            int[] cp = clamp(lay.px(CRAFT, 18, defY, w, h), cw, ch, w, h);
            rects.put(CRAFT, new int[]{cp[0], cp[1], cw, ch});
            place(menu.slots.get(1), cp[0] + 12, cp[1] + 22); place(menu.slots.get(2), cp[0] + 32, cp[1] + 22);
            place(menu.slots.get(3), cp[0] + 12, cp[1] + 42); place(menu.slots.get(4), cp[0] + 32, cp[1] + 42);
            place(menu.slots.get(0), cp[0] + 76, cp[1] + 32);
        }

        // ---- hotbar
        int hw = 9 * 20 + 8, hh = 28;
        int[] hp = clamp(lay.px(HOTBAR, (ww - hw) / 2, h - hh - 14, w, h), hw, hh, w, h);
        rects.put(HOTBAR, new int[]{hp[0], hp[1], hw, hh});
        for (int i = 0; i < 9; i++) place(menu.slots.get(36 + i), hp[0] + 5 + i * 20, hp[1] + 6);

        // ---- anillo de equipo alrededor del modelo: armadura (+ cosmetica), mano secundaria, Curios y casillas de otros mods
        List<Object> left = new ArrayList<>(), right = new ArrayList<>(), curios = new ArrayList<>(), cosItems = new ArrayList<>();
        List<Slot> extra = new ArrayList<>(), bpSlots = new ArrayList<>(), cosSlots = new ArrayList<>();
        java.util.Map<String, List<Slot>> clothes = new java.util.LinkedHashMap<>();
        for (int i = 5; i <= 8; i++) left.add(menu.slots.get(i));
        if (menu.slots.size() > 45) right.add(menu.slots.get(45));
        for (int i = 46; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (SaCompat.isBackpack(s)) { if (s.isActive()) bpSlots.add(s); else offscreen(s); continue; }
            String area = SaCompat.clothingArea(s);
            if (area != null) { if (s.isActive()) clothes.computeIfAbsent(area, k -> new ArrayList<>()).add(s); else offscreen(s); continue; }
            if (CosCompat.isCosSlot(s)) { cosSlots.add(s); continue; }
            if (mode == Mode.CURIOS) { if (s.isActive()) curios.add(s); else offscreen(s); continue; }
            if (!s.isActive() || !visibleOriginally(s)) continue;
            if (right.size() < 6) right.add(s); else extra.add(s);
        }
        // lo que vive en otros menus se ve igualmente (solo lectura); al pulsarlo se abre su pestana
        if (mode != Mode.CURIOS && CuriosCompat.loaded())
            for (Object[] c : CuriosCompat.live(player)) { Ghost gh = new Ghost(Mode.CURIOS, (String) c[0], (ItemStack) c[1]); ghosts.add(gh); curios.add(gh); }
        if (mode == Mode.COSMETIC) cosItems.addAll(cosSlots);
        else if (CosCompat.loaded()) for (int k = 0; k < 4; k++) { Ghost gh = new Ghost(Mode.COSMETIC, "COSMETIC", CosCompat.stack(player, k)); ghosts.add(gh); cosItems.add(gh); }
        int mx = ww / 2, my = (int) (h * 0.46);
        int rx = Math.max(60, Math.min(ww / 5, 170)), ry = Math.max(50, (int) (h * 0.27));
        int cap = ry >= 70 ? 8 : 6;   // las primeras casillas de Curios, en orden, en el arco derecho; el resto, en su panel
        while (right.size() < cap && !curios.isEmpty()) {
            Object o = curios.remove(0);
            right.add(o);
            String id = o instanceof Slot s ? CuriosCompat.identifier(s) : ((Ghost) o).label;
            ringLabels.add(new Object[]{o, id == null ? "" : id.toUpperCase().replace('_', ' ')});
        }
        for (int i = 0; i < left.size(); i++) {
            double a = Math.toRadians(left.size() <= 1 ? 180 : 235 - 110.0 * i / (left.size() - 1));
            put(left.get(i), (int) (mx + Math.cos(a) * rx) - 8, (int) (my + Math.sin(a) * ry) - 8);
        }
        for (int i = 0; i < right.size(); i++) {
            double a = Math.toRadians(right.size() <= 1 ? 0 : -55 + 110.0 * i / (right.size() - 1));
            put(right.get(i), (int) (mx + Math.cos(a) * rx) - 8, (int) (my + Math.sin(a) * ry) - 8);
        }
        String[] armorNames = {"HEAD", "CHEST", "LEGS", "FEET"};
        for (int i = 0; i < left.size() && i < 4; i++) ringLabels.add(new Object[]{left.get(i), armorNames[i]});
        if (!right.isEmpty() && right.get(0) instanceof Slot s45 && s45.index == 45) ringLabels.add(new Object[]{s45, "OFFHAND"});
        modelRect = new int[]{mx - 52, my - (int) (h * 0.23), 104, (int) (h * 0.46)};
        btnReset = new int[]{mx + 52 - 9, my + (int) (h * 0.23) - 9, 9, 9};
        for (Object o : left) ringPts.add(new int[]{xOf(o) + 8, yOf(o) + 8});
        for (Object o : right) ringPts.add(new int[]{xOf(o) + 8, yOf(o) + 8});
        // armadura cosmetica: una casilla al lado de cada pieza de armadura, con su boton de ocultar
        for (int k = 0; k < cosItems.size() && k < left.size(); k++) {
            int cx = xOf(left.get(k)) - 24, cy = yOf(left.get(k));
            put(cosItems.get(k), cx, cy);
            final int kk = k;
            minis.add(new Mini(cx + 11, cy - 2, CosCompat.skin(player, kk), "OCULTAR / MOSTRAR ESTA PIEZA",
                    () -> CosCompat.toggleSkin(player, kk)));
        }
        for (int k = 0; k < cosItems.size(); k++) if (k >= left.size() && cosItems.get(k) instanceof Slot s) offscreen(s);
        // ---- panel de almacenamiento extra
        if (!extra.isEmpty()) {
            int xc = 4, xr = (extra.size() + xc - 1) / xc;
            int pw = xc * 20 + 12, ph = 13 + 8 + xr * 20 + 6;
            int[] xp = clamp(lay.px(EXTRA, 18, h / 3 + 80, w, h), pw, ph, w, h);
            rects.put(EXTRA, new int[]{xp[0], xp[1], pw, ph});
            for (int i = 0; i < extra.size(); i++) place(extra.get(i), xp[0] + 7 + (i % xc) * 20, xp[1] + 13 + 8 + (i / xc) * 20);
        }
        int sy = 38, bpBottom = 38;
        if (!bpSlots.isEmpty()) {
            int bcols = 6, rowsB = (bpSlots.size() + bcols - 1) / bcols;
            int pw = bcols * 20 + 12, ph = 13 + 6 + rowsB * 20 + 6;
            int[] bp = clamp(lay.px(BACKPACK, 18, sy, w, h), pw, ph, w, h);
            rects.put(BACKPACK, new int[]{bp[0], bp[1], pw, ph});
            for (int i = 0; i < bpSlots.size(); i++) place(bpSlots.get(i), bp[0] + 7 + (i % bcols) * 20, bp[1] + 19 + (i / bcols) * 20);
            bpBottom = bp[1] + ph;
        }
        if (!clothes.isEmpty()) {
            int ccols = 6, pw = ccols * 20 + 12, ph = 17;
            for (List<Slot> g : clothes.values()) ph += 9 + ((g.size() + ccols - 1) / ccols) * 20 + 3;
            ph += 4;
            int[] cp2 = clamp(lay.px(CLOTHES, 18, bpBottom + 6, w, h), pw, ph, w, h);
            rects.put(CLOTHES, new int[]{cp2[0], cp2[1], pw, ph});
            int y = cp2[1] + 17;
            for (java.util.Map.Entry<String, List<Slot>> en : clothes.entrySet()) {
                saLabels.add(new Object[]{cp2[0] + 7, y, en.getKey()});
                y += 9;
                List<Slot> g = en.getValue();
                for (int i = 0; i < g.size(); i++) place(g.get(i), cp2[0] + 7 + (i % ccols) * 20, y + (i / ccols) * 20 + 1);
                y += ((g.size() + ccols - 1) / ccols) * 20 + 3;
            }
        }
        layoutCurios(curios, lay, w, h);
        // boton de "no dibujar en el personaje" de cada curio (el mismo que trae Curios)
        if (mode == Mode.CURIOS)
            for (Slot s : menu.slots) {
                if (s.index < 46 || s.x < 0 || !s.isActive() || !CuriosCompat.canToggleRender(s)) continue;
                final Slot fs = s;
                minis.add(new Mini(s.x + 11, s.y - 2, !CuriosCompat.renderStatus(s), "OCULTAR / MOSTRAR EN EL PERSONAJE",
                        () -> CuriosCompat.toggleRender(fs)));
            }
        if (creative) layoutCreative(lay, w, h);

        // ---- botones superiores: EDIT, THEME y pestañas de menu (Curios / Cosmetic / Inventory)
        List<Object[]> tabs = new ArrayList<>();
        if (CuriosCompat.loaded()) tabs.add(new Object[]{"CURIOS", Mode.CURIOS, 40});
        if (CosCompat.loaded()) tabs.add(new Object[]{"COSMETIC", Mode.COSMETIC, 50});
        if (!tabs.isEmpty()) tabs.add(new Object[]{"INVENTORY", Mode.INVENTORY, 52});
        int total = 30 + 4 + 44;
        for (Object[] tb : tabs) total += 4 + (Integer) tb[2];
        int bx = (ww - total) / 2, by = 22;
        btnEdit = new int[]{bx, by, 30, 11}; bx += 34;
        btnTheme = new int[]{bx, by, 44, 11}; bx += 44;
        for (Object[] tb : tabs) {
            bx += 4;
            tabBtns.add(new Object[]{tb[0], new int[]{bx, by, (Integer) tb[2], 11}, tb[1]});
            bx += (Integer) tb[2];
        }
    }

    private void layoutCurios(List<Object> curios, Layout lay, int w, int h) {
        int cols = 5, pw = cols * 20 + 12;
        // grupos consecutivos del mismo tipo de ranura
        List<String> ids = new ArrayList<>();
        List<List<Object>> groups = new ArrayList<>();
        for (Object o : curios) {
            String id = o instanceof Slot s ? CuriosCompat.identifier(s) : ((Ghost) o).label;
            if (id == null) id = "other";
            if (ids.isEmpty() || !ids.get(ids.size() - 1).equals(id)) { ids.add(id); groups.add(new ArrayList<>()); }
            groups.get(groups.size() - 1).add(o);
        }
        boolean footer = CuriosCompat.totalPages(menu) > 1 || CuriosCompat.hasCosmetics(menu);
        if (curios.isEmpty() && !footer) return;
        int ph = 13 + 4;
        for (List<Object> g : groups) ph += 9 + ((g.size() + cols - 1) / cols) * 20 + 3;
        if (groups.isEmpty()) ph += 14;
        ph += 4 + (footer ? 15 : 0);
        int[] cp = clamp(lay.px(CURIOS, 18, 40, w, h), pw, ph, w, h);
        rects.put(CURIOS, new int[]{cp[0], cp[1], pw, ph});
        int y = cp[1] + 17;
        for (int gi = 0; gi < groups.size(); gi++) {
            String name = ids.get(gi).toUpperCase().replace('_', ' ');
            if (name.length() > 22) name = name.substring(0, 22);
            curioLabels.add(new Object[]{cp[0] + 7, y, name});
            y += 9;
            List<Object> g = groups.get(gi);
            for (int i = 0; i < g.size(); i++) put(g.get(i), cp[0] + 7 + (i % cols) * 20, y + (i / cols) * 20 + 1);
            y += ((g.size() + cols - 1) / cols) * 20 + 3;
        }
        if (footer) {
            int fy = cp[1] + ph - 15;
            if (CuriosCompat.hasCosmetics(menu)) btnCosm = new int[]{cp[0] + 6, fy, 52, 11};
            if (CuriosCompat.totalPages(menu) > 1) {
                btnPgPrev = new int[]{cp[0] + pw - 54, fy, 12, 11};
                btnPgNext = new int[]{cp[0] + pw - 18, fy, 12, 11};
                pgX = cp[0] + pw - 30; pgY = fy + 3;
            }
        }
    }

    // ------------------------------------------------------------------ creativo

    private void initCreative() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.player != null)
                CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(),
                        mc.player.canUseGameMasterBlocks() && mc.options.operatorItemsTab().get(), mc.player.level().registryAccess());
        } catch (Throwable t) { TacUI.LOGGER.warn("Creativo: no se pudo reconstruir las pestanas: {}", t.toString()); }
        cats.clear();
        try {
            for (CreativeModeTab tab : CreativeModeTabs.tabs())
                if (tab.getType() == CreativeModeTab.Type.CATEGORY && !tab.getDisplayItems().isEmpty()) cats.add(tab);
            allItems = new ArrayList<>(CreativeModeTabs.searchTab().getSearchTabDisplayItems());
        } catch (Throwable t) { TacUI.LOGGER.warn("Creativo: no se pudo leer la lista de objetos: {}", t.toString()); }
        if (cat >= cats.size()) cat = -1;
        search = new EditBox(this.font, 0, 0, 100, 10, Component.literal("buscar"));
        search.setBordered(false);
        search.setMaxLength(40);
        search.setHint(Component.literal("BUSCAR..."));
        search.setValue(query);
        search.setResponder(v -> { query = v; scroll = 0; refilter(); });
        addRenderableWidget(search);
        refilter();
    }

    private String keyOf(ItemStack s) {
        return keyCache.computeIfAbsent(s, k -> (k.getHoverName().getString() + " " + BuiltInRegistries.ITEM.getKey(k.getItem())).toLowerCase());
    }

    private void refilter() {
        List<ItemStack> src = cat < 0 || cat >= cats.size() ? allItems : new ArrayList<>(cats.get(cat).getDisplayItems());
        String q = query.trim().toLowerCase();
        if (q.isEmpty()) { shown = src; }
        else {
            List<ItemStack> out = new ArrayList<>();
            for (ItemStack s : src) if (keyOf(s).contains(q)) out.add(s);
            shown = out;
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private int maxScroll() { return Math.max(0, (shown.size() + 8) / 9 - gridRows); }

    private String catName() {
        if (cat < 0 || cat >= cats.size()) return "ALL";
        String n = cats.get(cat).getDisplayName().getString().toUpperCase();
        return n.length() > 20 ? n.substring(0, 20) : n;
    }

    private void layoutCreative(Layout lay, int w, int h) {
        gridRows = Math.max(3, Math.min(9, (h - 130) / 20));
        int pw = 9 * 20 + 12, ph = 13 + 4 + 14 + 2 + 13 + 4 + gridRows * 20 + 4 + 14 + 4;
        int[] ip = clamp(lay.px(ITEMS, 18, 38, w, h), pw, ph, w, h);
        rects.put(ITEMS, new int[]{ip[0], ip[1], pw, ph});
        if (search != null) {
            search.setX(ip[0] + 10); search.setY(ip[1] + 13 + 4 + 2); search.setWidth(pw - 20);
        }
        int ty = ip[1] + 13 + 4 + 14 + 2;
        btnPrev = new int[]{ip[0] + 6, ty, 14, 11};
        btnNext = new int[]{ip[0] + pw - 20, ty, 14, 11};
        gridX = ip[0] + 6; gridY = ty + 13 + 4;
        int fy = gridY + gridRows * 20 + 4;
        btnDel = new int[]{ip[0] + 6, fy, 70, 12};
        btnClear = new int[]{ip[0] + pw - 76, fy, 70, 12};
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private int cellAt(double mx, double my) {
        if (!creative || rects.get(ITEMS) == null) return -1;
        double rx = mx - gridX, ry = my - gridY;
        if (rx < 0 || ry < 0 || rx >= 180 || ry >= gridRows * 20) return -1;
        int idx = (scroll + (int) (ry / 20)) * 9 + (int) (rx / 20);
        return idx < shown.size() ? idx : -1;
    }

    private void drawCreative(GuiGraphics g, Theme t, int mx, int my) {
        int[] r = rects.get(ITEMS);
        if (r == null) return;
        // buscador
        g.fill(r[0] + 6, r[1] + 17, r[0] + r[2] - 6, r[1] + 31, 0xAA000000);
        Draw.frame(g, r[0] + 6, r[1] + 17, r[2] - 12, 14, search != null && search.isFocused() ? t.accent : Theme.alpha(t.dim, 230));
        // categoria
        button(g, t, btnPrev, "<", false);
        button(g, t, btnNext, ">", false);
        Draw.textCenter(g, font, catName() + "  " + shown.size(), r[0] + r[2] / 2, btnPrev[1] + 3, t.text, 0.75f);
        // rejilla
        int hov = cellAt(mx, my);
        for (int row = 0; row < gridRows; row++) {
            for (int col = 0; col < 9; col++) {
                int idx = (scroll + row) * 9 + col;
                int x = gridX + col * 20 + 2, y = gridY + row * 20 + 2;
                Draw.slot(g, t, x, y, idx == hov, false);
                if (idx < shown.size()) g.renderItem(shown.get(idx), x, y);
            }
        }
        // barra de desplazamiento
        int sx = r[0] + r[2] - 6, sh = gridRows * 20;
        g.fill(sx, gridY, sx + 3, gridY + sh, 0x88000000);
        int ms = maxScroll();
        int th = ms == 0 ? sh : Math.max(10, sh * gridRows / (gridRows + ms));
        int ty = ms == 0 ? gridY : gridY + (sh - th) * scroll / ms;
        g.fill(sx, ty, sx + 3, ty + th, t.accent);
        // pie
        boolean armed = System.currentTimeMillis() - clearArmed < 3000;
        button(g, t, btnDel, "DELETE", !held.isEmpty());
        button(g, t, btnClear, armed ? "SEGURO?" : "CLEAR INV", armed);
    }

    private void creativeTooltip(GuiGraphics g, int mx, int my) {
        if (!creative) return;
        int c = cellAt(mx, my);
        if (c >= 0 && held.isEmpty() && menu.getCarried().isEmpty()) g.renderTooltip(font, shown.get(c), mx, my);
        if (!held.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            g.renderItem(held, mx - 8, my - 8);
            g.renderItemDecorations(font, held, mx - 8, my - 8);
            g.pose().popPose();
        }
    }

    private Slot slotAt(double mx, double my) {
        for (Slot s : menu.slots) {
            if (!s.isActive()) continue;
            if (mx >= s.x - 1 && mx < s.x + 17 && my >= s.y - 1 && my < s.y + 17) return s;
        }
        return null;
    }

    /** Clic en modo creativo. Devuelve true si lo ha gestionado. */
    private boolean creativeClick(double mx, double my, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null) return false;
        boolean inSearch = rects.get(ITEMS) != null && mx >= search.getX() - 4 && mx < search.getX() + search.getWidth() + 4 && my >= search.getY() - 3 && my < search.getY() + 12;
        if (search != null && !inSearch && getFocused() == search) setFocused(null);
        if (inSearch) { setFocused(search); search.mouseClicked(mx, my, button); return true; }
        if (button == 0) {
            if (in(btnPrev, mx, my)) { cat = cat <= -1 ? cats.size() - 1 : cat - 1; scroll = 0; refilter(); return true; }
            if (in(btnNext, mx, my)) { cat = cat >= cats.size() - 1 ? -1 : cat + 1; scroll = 0; refilter(); return true; }
            if (in(btnDel, mx, my)) { held = ItemStack.EMPTY; return true; }
            if (in(btnClear, mx, my)) {
                if (System.currentTimeMillis() - clearArmed < 3000) {
                    for (int i = 1; i <= 45 && i < menu.slots.size(); i++) {
                        Slot s = menu.slots.get(i);
                        if (s.hasItem()) { mc.gameMode.handleCreativeModeItemAdd(ItemStack.EMPTY, i); s.set(ItemStack.EMPTY); }
                    }
                    clearArmed = 0;
                } else clearArmed = System.currentTimeMillis();
                return true;
            }
        }
        int c = cellAt(mx, my);
        if (c >= 0) {
            if (!menu.getCarried().isEmpty()) return true;
            ItemStack st = shown.get(c).copy();
            int max = st.getMaxStackSize();
            int n = (hasShiftDown() || button == 1) ? max : 1;
            if (!held.isEmpty() && ItemStack.isSameItemSameTags(held, st)) held.setCount(Math.min(max, held.getCount() + n));
            else { st.setCount(n); held = st; }
            return true;
        }
        if (!held.isEmpty()) {
            Slot s = slotAt(mx, my);
            if (s != null) {
                if (s.index >= 1 && s.index <= 45) {
                    ItemStack prev = s.getItem().copy();
                    ItemStack put = held.copy();
                    if (button == 1) put.setCount(1);
                    mc.gameMode.handleCreativeModeItemAdd(put, s.index);
                    s.set(put.copy());
                    if (button == 1 && held.getCount() > 1) held.shrink(1); else held = prev;
                }
                return true;
            }
            for (int[] r : rects.values()) if (in(r, mx, my)) return true;
            if (in(btnEdit, mx, my) || in(btnTheme, mx, my)) return true;
            for (Object[] tb : tabBtns) if (in((int[]) tb[1], mx, my)) return true;
            mc.gameMode.handleCreativeModeItemDrop(held);
            held = ItemStack.EMPTY;
            return true;
        }
        return false;
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
        int w = this.width, h = this.height, ww = workW();

        g.fill(0, 0, w, h, t.bg);
        Draw.vignette(g, w, h);
        Draw.scanlines(g, w, h);
        Draw.corners(g, 10, 10, w - 20, h - 20, 18, Theme.alpha(t.accent, 200));
        boolean blink = (System.currentTimeMillis() / 600) % 2 == 0;
        g.fill(ww / 2 - 62, 9, ww / 2 - 58, 13, blink ? t.accent : Theme.alpha(t.accent, 70));
        Draw.text(g, this.font, "LIVE //TAC_STATUS_ACTIVE", ww / 2 - 54, 8, Theme.alpha(t.text, 220), 0.8f);

        // modelo del jugador y anillo de equipo
        int mcx = ww / 2, mcy = (int) (h * 0.46);
        Draw.corners(g, mcx - 52, mcy - (int) (h * 0.23), 104, (int) (h * 0.46), 8, Theme.alpha(t.accent, 160));
        for (int[] p : ringPts) Draw.dotted(g, p[0], p[1], mcx, mcy, Theme.alpha(t.accent, 90), 5);
        int scale = (int) (Math.max(30, h / 6) * zoom);
        g.enableScissor(mcx - 52, mcy - (int) (h * 0.23), mcx + 52, mcy + (int) (h * 0.23));
        renderModel(g, mcx, mcy + Math.max(30, h / 6), scale);
        g.disableScissor();
        // barrido de escaneo sobre el modelo
        int fh = (int) (h * 0.46), fy0 = mcy - (int) (h * 0.23);
        int sweep = fy0 + (int) ((System.currentTimeMillis() % 2600) / 2600.0 * fh);
        g.fill(mcx - 50, sweep, mcx + 50, sweep + 1, Theme.alpha(t.accent, 70));
        g.fill(mcx - 50, sweep - 3, mcx + 50, sweep, Theme.alpha(t.accent, 18));
        Draw.textCenter(g, font, "OPERATOR // " + player.getName().getString().toUpperCase(), mcx, fy0 - 9, Theme.alpha(t.text, 210), 0.7f);
        boolean hovModel = in(modelRect, mx, my) && slotAt(mx, my) == null;
        if (hovModel || rotating)
            Draw.textCenter(g, font, "ARRASTRA: GIRAR  ·  RUEDA: ZOOM  ·  DOBLE CLIC: CENTRAR", mcx, fy0 + fh - 8, Theme.alpha(t.text, 140), 0.5f);
        if (btnReset != null) {
            boolean hr = in(btnReset, mx, my);
            g.fill(btnReset[0], btnReset[1], btnReset[0] + 9, btnReset[1] + 9, hr ? Theme.alpha(t.accent, 120) : 0x99000000);
            Draw.frame(g, btnReset[0], btnReset[1], 9, 9, hr ? t.accent : Theme.alpha(t.dim, 230));
            Draw.textCenter(g, font, "R", btnReset[0] + 5, btnReset[1] + 2, t.text, 0.6f);
        }
        drawVitals(g, t, mcx, fy0 + fh + 3, mx, my);

        // paneles
        String[] titles = {"CRAFTING", "EQUIPMENT", "", "STORAGE", "CREATIVE ITEMS", "CURIOS", "BACKPACK", "CLOTHING"};
        String[] keys = {CRAFT, EQUIP, HOTBAR, EXTRA, ITEMS, CURIOS, BACKPACK, CLOTHES};
        for (int i = 0; i < keys.length; i++) {
            int[] r = rects.get(keys[i]);
            if (r == null) continue;
            String ti = titles[i].isEmpty() ? "" : String.format("%02d // %s", i + 1, titles[i]);
            Draw.panel(g, font, t, r[0], r[1], r[2], r[3], ti, edit || keys[i].equals(dragging));
        }
        int[] c = rects.get(CRAFT);
        if (c != null) Draw.textCenter(g, font, "→", c[0] + 62, c[1] + 36, t.accent, 1.2f);
        int[] e = rects.get(EQUIP);
        if (e != null) {
            // casilla de decoracion superior y botones
            Draw.text(g, font, "SLOTS 27", e[0] + 7, e[1] + 19, Theme.alpha(t.text, 130), 0.6f);
            button(g, t, btnSort, "SORT", false);
            button(g, t, btnDrop, "DROP", !menu.getCarried().isEmpty());
        }
        for (Object[] l : saLabels) Draw.text(g, font, (String) l[2], (Integer) l[0], (Integer) l[1], Theme.alpha(t.text, 150), 0.6f);
        for (Object[] l : ringLabels)
            Draw.textCenter(g, font, (String) l[1], xOf(l[0]) + 8, yOf(l[0]) + 19, Theme.alpha(t.text, 130), 0.5f);
        if (creative) drawCreative(g, t, mx, my);
        for (Object[] l : curioLabels) Draw.text(g, font, (String) l[2], (Integer) l[0], (Integer) l[1], Theme.alpha(t.text, 150), 0.6f);
        if (btnCosm != null) button(g, t, btnCosm, "CURIO-COS", CuriosCompat.viewingCosmetics(menu));
        if (btnPgPrev != null) {
            button(g, t, btnPgPrev, "<", false);
            button(g, t, btnPgNext, ">", false);
            Draw.textCenter(g, font, (CuriosCompat.currentPage(menu) + 1) + "/" + CuriosCompat.totalPages(menu), pgX, pgY, t.text, 0.7f);
        }
        button(g, t, btnEdit, "EDIT", edit);
        button(g, t, btnTheme, Layout.get().theme, false);
        for (Object[] tb : tabBtns) button(g, t, (int[]) tb[1], (String) tb[0], tb[2] == mode);
        // casillas de solo lectura de otros menus
        for (Ghost gh : ghosts) {
            boolean hov = mx >= gh.x - 1 && mx < gh.x + 17 && my >= gh.y - 1 && my < gh.y + 17;
            Draw.slot(g, t, gh.x, gh.y, hov, false);
            if (!gh.stack.isEmpty()) {
                g.renderItem(gh.stack, gh.x, gh.y);
                g.renderItemDecorations(font, gh.stack, gh.x, gh.y);
            }
            g.fill(gh.x, gh.y, gh.x + 16, gh.y + 16, 0x44000000);
        }

        // marcos de todas las casillas visibles
        for (Slot s : menu.slots) {
            if (!s.isActive()) continue;
            if (mode == Mode.INVENTORY && s.index >= 46 && !visibleOriginally(s) && SaCompat.clothingArea(s) == null && !SaCompat.isBackpack(s)) continue;
            if (s.x < 0) continue;
            boolean hov = mx >= s.x - 1 && mx < s.x + 17 && my >= s.y - 1 && my < s.y + 17;
            Draw.slot(g, t, s.x, s.y, hov, mode == Mode.CURIOS && CuriosCompat.cosmetic(s));
        }
        if (edit) {
            Draw.textCenter(g, font, "EDIT MODE  |  ARRASTRA LOS PANELES  |  R REINICIAR  |  ESC GUARDAR", ww / 2, 46, t.accent, 0.8f);
        }
    }

    private void button(GuiGraphics g, Theme t, int[] r, String label, boolean active) {
        if (r == null) return;
        boolean hov = in(r, mouseX, mouseY);
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], active ? Theme.alpha(t.accent, 90) : 0xAA000000);
        Draw.frame(g, r[0], r[1], r[2], r[3], hov || active ? t.accent : Theme.alpha(t.dim, 230));
        Draw.textCenter(g, font, label, r[0] + r[2] / 2, r[1] + (r[3] - 6) / 2 + 1, hov || active ? t.text : Theme.alpha(t.text, 170), 0.7f);
    }

    /** Clic en zona vacia = fuera de la ventana: con un objeto en el cursor, lo tira (como en vainilla). */
    @Override
    protected boolean hasClickedOutside(double mx, double my, int left, int top, int button) {
        for (int[] r : rects.values()) if (in(r, mx, my)) return false;
        for (int[] b : new int[][]{btnEdit, btnTheme, btnSort, btnDrop, btnCosm, btnPgPrev, btnPgNext}) if (in(b, mx, my)) return false;
        for (Object[] tb : tabBtns) if (in((int[]) tb[1], mx, my)) return false;
        for (Mini m : minis) if (m.in(mx, my)) return false;
        if (in(btnReset, mx, my)) return false;
        for (Ghost gh : ghosts) if (mx >= gh.x - 1 && mx < gh.x + 17 && my >= gh.y - 1 && my < gh.y + 17) return false;
        return slotAt(mx, my) == null;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        // por encima de los objetos: franja de rareza y mini botones de las esquinas
        Theme t = Layout.get().theme();
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        for (Slot s : menu.slots) {
            if (!s.isActive() || s.x < 0 || !s.hasItem()) continue;
            Integer c = s.getItem().getRarity().color.getColor();
            if (c != null && s.getItem().getRarity() != net.minecraft.world.item.Rarity.COMMON)
                g.fill(s.x, s.y + 16, s.x + 16, s.y + 17, 0xFF000000 | c);
        }
        for (Mini m : minis) {
            boolean hov = m.in(mx, my);
            g.fill(m.x, m.y, m.x + 7, m.y + 7, m.on ? t.accent : 0xDD000000);
            Draw.frame(g, m.x, m.y, 7, 7, hov ? t.text : m.on ? t.accent : Theme.alpha(t.dim, 255));
            if (!m.on) g.fill(m.x + 3, m.y + 3, m.x + 4, m.y + 4, Theme.alpha(t.accent, 200));
        }
        g.pose().popPose();
    }

    /** Dibuja al jugador con la rotacion / zoom elegidos (arrastrando sobre el modelo). */
    private void renderModel(GuiGraphics g, int x, int y, int scale) {
        org.joml.Quaternionf pose = new org.joml.Quaternionf().rotateZ((float) Math.PI);
        org.joml.Quaternionf cam = new org.joml.Quaternionf().rotateX(rotPitch * ((float) Math.PI / 180f));
        pose.mul(cam);
        float yb = player.yBodyRot, yr = player.getYRot(), xr = player.getXRot(), yho = player.yHeadRotO, yh = player.yHeadRot;
        player.yBodyRot = 180f + rotYaw;
        player.setYRot(180f + rotYaw);
        player.setXRot(0f);
        player.yHeadRot = player.getYRot();
        player.yHeadRotO = player.getYRot();
        try {
            InventoryScreen.renderEntityInInventory(g, x, y, scale, pose, cam, player);
        } finally {
            player.yBodyRot = yb; player.setYRot(yr); player.setXRot(xr); player.yHeadRotO = yho; player.yHeadRot = yh;
        }
    }

    /** Vida, armadura, dureza y nivel bajo el modelo, y los efectos activos con su tiempo. */
    private void drawVitals(GuiGraphics g, Theme t, int cx, int y, int mx, int my) {
        int hp = (int) Math.ceil(player.getHealth()), max = (int) Math.ceil(player.getMaxHealth());
        int tough = (int) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
        String s = "HP " + hp + "/" + max + "   ARM " + player.getArmorValue() + "   TGH " + tough + "   LV " + player.experienceLevel;
        Draw.textCenter(g, font, s, cx, y, Theme.alpha(t.text, 200), 0.7f);
        int bw = 100;
        Draw.bar(g, cx - bw / 2, y + 7, bw, 2, max <= 0 ? 0 : Math.min(1f, player.getHealth() / player.getMaxHealth()), t.accent, 0x66000000);
        var effects = new ArrayList<>(player.getActiveEffects());
        if (effects.isEmpty()) return;
        int n = Math.min(10, effects.size()), ex = cx - (n * 15 - 3) / 2, ey = y + 12;
        var tex = Minecraft.getInstance().getMobEffectTextures();
        for (int i = 0; i < n; i++) {
            var inst = effects.get(i);
            int x = ex + i * 15;
            g.fill(x - 1, ey - 1, x + 13, ey + 13, 0xAA000000);
            Draw.frame(g, x - 1, ey - 1, 14, 14, inst.getEffect().isBeneficial() ? Theme.alpha(t.accent, 160) : 0xFFAA3030);
            g.blit(x, ey, 0, 12, 12, tex.get(inst.getEffect()));
            if (mx >= x - 1 && mx < x + 13 && my >= ey - 1 && my < ey + 13) hoverEffect = inst;
        }
    }
    private net.minecraft.world.effect.MobEffectInstance hoverEffect;

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        for (Ghost gh : ghosts) {
            if (mx >= gh.x - 1 && mx < gh.x + 17 && my >= gh.y - 1 && my < gh.y + 17) {
                if (!gh.stack.isEmpty()) g.renderTooltip(font, gh.stack, mx, my);
                else g.renderTooltip(font, Component.literal(gh.label.toUpperCase().replace('_', ' ') + "  (CLIC: ABRIR)"), mx, my);
            }
        }
        for (Mini m : minis) if (m.in(mx, my)) g.renderTooltip(font, Component.literal(m.tip), mx, my);
        if (hoverEffect != null) {
            g.renderComponentTooltip(font, List.of(hoverEffect.getEffect().getDisplayName(),
                    net.minecraft.world.effect.MobEffectUtil.formatDuration(hoverEffect, 1.0f)), mx, my);
            hoverEffect = null;
        }
        creativeTooltip(g, mx, my);
        // entrada: fundido y linea de escaneo durante 350 ms
        float k = Math.min(1f, (System.currentTimeMillis() - openedAt) / 350f);
        if (k < 1f) {
            Theme t = Layout.get().theme();
            float e = 1f - (1f - k) * (1f - k);
            g.pose().pushPose();
            g.pose().translate(0, 0, 500);
            g.fill(0, 0, width, height, Theme.alpha(0x000000, (int) ((1f - e) * 230)));
            int ly = (int) (e * height);
            g.fill(0, ly, width, ly + 1, t.accent);
            g.fill(0, ly - 6, width, ly, Theme.alpha(t.accent, 40));
            g.pose().popPose();
        }
    }

    // ------------------------------------------------------------------ entrada

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (in(btnEdit, mx, my)) { edit = !edit; if (!edit) Layout.get().save(); return true; }
            if (in(btnTheme, mx, my)) { Layout lay = Layout.get(); lay.theme = lay.theme().next().name(); lay.save(); return true; }
            if (!edit) {
                for (Object[] tb : tabBtns) if (in((int[]) tb[1], mx, my)) { switchTo((Mode) tb[2]); return true; }
                for (Mini m : minis) if (m.in(mx, my)) { m.act.run(); return true; }
                if (in(btnReset, mx, my)) { rotYaw = 0; rotPitch = 0; zoom = 1f; return true; }
                for (Ghost gh : ghosts)
                    if (mx >= gh.x - 1 && mx < gh.x + 17 && my >= gh.y - 1 && my < gh.y + 17) { switchTo(gh.tab); return true; }
            }
            if (edit) {
                for (String k : new String[]{EQUIP, CRAFT, HOTBAR, EXTRA, ITEMS, CURIOS, BACKPACK, CLOTHES}) {
                    int[] r = rects.get(k);
                    if (in(r, mx, my)) { dragging = k; dragDX = (int) mx - r[0]; dragDY = (int) my - r[1]; return true; }
                }
                return true;
            }
            if (in(btnCosm, mx, my)) { CuriosCompat.toggleCosmetics(menu); return true; }
            if (in(btnPgPrev, mx, my)) { CuriosCompat.page(menu, false); return true; }
            if (in(btnPgNext, mx, my)) { CuriosCompat.page(menu, true); return true; }
            if (in(btnSort, mx, my)) { sort(); return true; }
            if (in(btnDrop, mx, my)) { drop(); return true; }
        }
        if (edit) return true;
        // girar el modelo: arrastrar sobre el (sin nada en el cursor)
        if ((button == 0 || button == 1) && in(modelRect, mx, my) && slotAt(mx, my) == null
                && menu.getCarried().isEmpty() && held.isEmpty()) {
            long now = System.currentTimeMillis();
            if (button == 0 && now - lastModelClick < 300) { rotYaw = 0; rotPitch = 0; zoom = 1f; }
            lastModelClick = now;
            rotating = true;
            return true;
        }
        if (creative && creativeClick(mx, my, button)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (in(modelRect, mx, my)) {
            zoom = Math.max(0.6f, Math.min(1.9f, zoom + (float) delta * 0.1f));
            return true;
        }
        if (creative && in(rects.get(ITEMS), mx, my)) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta)));
            return true;
        }
        if (mode == Mode.CURIOS && CuriosCompat.totalPages(menu) > 1 && in(rects.get(CURIOS), mx, my)) {
            CuriosCompat.page(menu, delta < 0);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            Layout.get().setPx(dragging, (int) mx - dragDX, (int) my - dragDY, width, height);
            return true;
        }
        if (rotating) {
            rotYaw = (rotYaw + (float) dx * 1.6f) % 360f;
            rotPitch = Math.max(-35f, Math.min(35f, rotPitch - (float) dy * 0.9f));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null) { dragging = null; Layout.get().save(); return true; }
        if (rotating) { rotating = false; return true; }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (edit) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { edit = false; Layout.get().save(); return true; }
            if (key == GLFW.GLFW_KEY_R) { Layout.get().pos.clear(); Layout.get().save(); return true; }
            return true;
        }
        if (search != null && search.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { setFocused(null); return true; }
            search.keyPressed(key, scan, mods);
            return true;   // escribir no debe cerrar la pantalla (tecla E) ni cambiar la hotbar
        }
        return super.keyPressed(key, scan, mods);
    }

    // ------------------------------------------------------------------ acciones

    /** Cambia de menu (Curios / armadura cosmetica / inventario): la pantalla se vuelve a abrir con el menu nuevo. */
    private void switchTo(Mode target) {
        if (target == mode) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        try {
            if (target == Mode.CURIOS) CuriosCompat.openCurios();
            else if (target == Mode.COSMETIC) CosCompat.openCos();
            else {
                mc.player.closeContainer();
                mc.setScreen(new TacInventoryScreen(mc.player, mc.player.inventoryMenu, Mode.INVENTORY));
            }
        } catch (Throwable t) { TacUI.LOGGER.warn("No se pudo cambiar de menu: {}", t.toString()); }
    }

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
