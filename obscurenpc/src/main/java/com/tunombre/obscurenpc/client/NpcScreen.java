package com.tunombre.obscurenpc.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tunombre.obscurenpc.Ctx;
import com.tunombre.obscurenpc.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Editor de NPC: pestañas a la izquierda y los ajustes a la derecha (estilo Custom NPCs, mas simple).
 * Los cambios se aplican al instante en el servidor; el contexto de la IA es un cuadro de texto grande.
 */
public final class NpcScreen extends Screen {
    private static NpcScreen active;
    private static String lastTab = "General";

    // paleta oscura con acento rojo (la de Obscure)
    private static final int C_BG = 0xF00C0C0F, C_SIDE = 0xFF121216, C_BORDER = 0xFF6E1420, C_ACCENT = 0xFFC8283A, C_TEXT = 0xFFE8E8EC,
            C_DIM = 0xFF8C8C98, C_OK = 0xFF55D68A, C_FIELD = 0xFF1C1C22, C_BAR = 0xFF2B0E14, C_ON = 0xFF2EBB55, C_OFF = 0xFF55555F;
    private static final int GAP = 6, SIDE_W = 108;

    private final boolean isSpawner;
    private final JsonObject root;
    private final JsonObject data;
    private final JsonObject cat;
    private final List<String> tabs = new ArrayList<>();

    private String tab;
    private final List<Cell> cells = new ArrayList<>();
    private final List<AbstractWidget> tabButtons = new ArrayList<>();
    private int scroll, contentH;
    private int W, H, x0, y0, cx, cw, ctop, cbot;
    private int cy, slot, rowH;

    private final Map<String, Long> pending = new LinkedHashMap<>();
    private final Map<String, String> pendingVal = new HashMap<>();

    // estado local de la pestaña Acciones / IA
    private String gestoTest = "talk_a";
    private String sayText = "";
    private String askText = "";
    private long deleteArmed;
    private String status = "";
    private int statusColor = C_OK;
    private long statusAt;

    // contexto de la IA
    private String ctxKey;
    private String ctxNpc;
    private String ctxMundo;
    private boolean editMundo;
    private boolean dirtyNpc, dirtyMundo;
    private MultiLineEditBox ctxBox;
    private long keyCheckAt;

    private static final class Cell {
        final AbstractWidget w;
        final int relY;
        boolean fill;

        Cell(AbstractWidget w, int relY) {
            this.w = w;
            this.relY = relY;
        }
    }

    private interface Make { List<AbstractWidget> make(int x, int w); }

    private NpcScreen(JsonObject root) {
        super(Component.literal("Obscure NPC"));
        this.root = root;
        this.isSpawner = "spawner".equals(str(root, "tipo"));
        this.data = root.getAsJsonObject("data");
        this.cat = root.getAsJsonObject("cat");
        this.ctxKey = str(root, "ctx_key");
        this.ctxNpc = str(root, "ctx_npc");
        this.ctxMundo = str(root, "ctx_mundo");
        if (isSpawner) tabs.add("Spawner");
        tabs.addAll(List.of("General", "Conducta", "Atributos", "Equipo", "Diálogo", "IA / Contexto", "Sonidos", "Rutina", "Acciones"));
        this.tab = tabs.contains(lastTab) ? lastTab : tabs.get(0);
    }

    public static void open(String json) {
        try {
            NpcScreen s = new NpcScreen(JsonParser.parseString(json).getAsJsonObject());
            Minecraft.getInstance().setScreen(s);
        } catch (RuntimeException ignored) { }
    }

    public static void notice(String msg) {
        if (active != null) active.setStatus(msg, C_OK);
    }

    public static void onCtx(String json) {
        if (active == null) return;
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            active.ctxKey = str(o, "key");
            if (!active.dirtyNpc) {
                active.ctxNpc = str(o, "npc");
                if (active.ctxBox != null && !active.editMundo) active.ctxBox.setValue(active.ctxNpc);
                active.dirtyNpc = false;
            }
        } catch (RuntimeException ignored) { }
    }

    private void setStatus(String s, int color) {
        status = s;
        statusColor = color;
        statusAt = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ datos ----
    private static String str(JsonObject o, String k) { return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; }

    private String gs(String k) {
        if (!data.has(k)) return "";
        JsonElement e = data.get(k);
        if (e.isJsonArray()) {
            List<String> l = new ArrayList<>();
            for (JsonElement x : e.getAsJsonArray()) l.add(x.getAsString());
            return String.join(" | ", l);
        }
        return e.getAsString();
    }

    private boolean gb(String k) { return data.has(k) && data.get(k).getAsBoolean(); }

    private double gn(String k) {
        try { return k.startsWith("@") ? (root.has(k.substring(1)) ? root.get(k.substring(1)).getAsDouble() : 0) : (data.has(k) ? data.get(k).getAsDouble() : 0); } catch (RuntimeException e) { return 0; }
    }

    private static List<String> list(JsonObject o, String k) {
        List<String> l = new ArrayList<>();
        if (o != null && o.has(k)) for (JsonElement e : o.getAsJsonArray(k)) l.add(e.getAsString());
        return l;
    }

    private void send(JsonObject o) {
        if (isSpawner) {
            o.addProperty("x", root.get("x").getAsInt());
            o.addProperty("y", root.get("y").getAsInt());
            o.addProperty("z", root.get("z").getAsInt());
        } else o.addProperty("uuid", str(root, "uuid"));
        Net.CH.sendToServer(new Net.ToServer("edit", o.toString()));
    }

    private void act(String op) {
        JsonObject o = new JsonObject();
        o.addProperty("op", op);
        send(o);
    }

    private void sendSet(String k, String v) {
        JsonObject o = new JsonObject();
        o.addProperty("op", "set");
        o.addProperty("k", k);
        o.addProperty("v", v);
        send(o);
    }

    private void setS(String k, String v) {
        if (k.startsWith("@")) root.addProperty(k.substring(1), v);
        else if (data.has(k) && data.get(k).isJsonArray()) {
            JsonArray a = new JsonArray();
            List<String> parts = new ArrayList<>();
            for (String s : v.split("\\|")) if (!s.isBlank()) { a.add(s.trim()); parts.add(s.trim()); }
            data.add(k, a);
            v = String.join("\n", parts);
        } else data.addProperty(k, v);
        sendSet(k, v);
        if (k.equals("id") || k.equals("nombre")) keyCheckAt = System.currentTimeMillis() + 700;
    }

    private void setB(String k, boolean v) {
        if (k.startsWith("@")) root.addProperty(k.substring(1), v); else data.addProperty(k, v);
        sendSet(k, String.valueOf(v));
    }

    private void setN(String k, double v, boolean integer) {
        String s = integer ? String.valueOf((int) Math.round(v)) : String.format(Locale.ROOT, "%.3f", v);
        if (k.startsWith("@")) root.addProperty(k.substring(1), v); else data.addProperty(k, v);
        pending.put(k, System.currentTimeMillis());
        pendingVal.put(k, s);
    }

    private void flush() {
        for (String k : new ArrayList<>(pending.keySet())) sendSet(k, pendingVal.getOrDefault(k, "0"));
        pending.clear();
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        if (!pending.isEmpty()) {
            for (var it = pending.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                if (now - e.getValue() > 350) {
                    sendSet(e.getKey(), pendingVal.getOrDefault(e.getKey(), "0"));
                    it.remove();
                }
            }
        }
        if (keyCheckAt != 0 && now > keyCheckAt) {
            keyCheckAt = 0;
            act("ctx_get");
        }
    }

    // ------------------------------------------------------------------ widgets propios ----
    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static void frame(GuiGraphics g, int x, int y, int w, int h, int border, int fill) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private static Font font() { return Minecraft.getInstance().font; }

    private abstract static class Base extends AbstractWidget {
        Base(int x, int y, int w, int h) { super(x, y, w, h, Component.empty()); }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) { }
    }

    private static final class Lbl extends Base {
        private final Supplier<String> text;
        private final int color;

        Lbl(int x, int y, Supplier<String> text, int color) {
            super(x, y, 10, 10);
            this.text = text;
            this.color = color;
            this.active = false;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) { g.drawString(font(), text.get(), getX(), getY(), color); }
    }

    private static final class Line extends Base {
        private final String title;

        Line(int x, int y, int w, String title) {
            super(x, y, w, 12);
            this.title = title;
            this.active = false;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int tw = font().width(title);
            g.drawString(font(), title, getX(), getY() + 2, C_ACCENT);
            g.fill(getX() + tw + 6, getY() + 6, getX() + width, getY() + 7, 0xFF3A1A20);
        }
    }

    private static final class Btn extends Base {
        private final String label;
        private final int color;
        private final Runnable run;
        private Supplier<String> dyn;

        Btn(int x, int y, int w, String label, int color, Runnable run) {
            super(x, y, w, 20);
            this.label = label;
            this.color = color;
            this.run = run;
        }

        @Override
        public void onClick(double mx, double my) { run.run(); }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean hov = isMouseOver(mx, my);
            frame(g, getX(), getY(), width, height, color, mix(color, 0xFF101014, hov ? 0.5F : 0.72F));
            String t = dyn != null ? dyn.get() : label;
            g.drawCenteredString(font(), t, getX() + width / 2, getY() + 6, C_TEXT);
        }
    }

    private final class Tab extends Base {
        private final String name;

        Tab(int x, int y, int w, int h, String name) {
            super(x, y, w, h);
            this.name = name;
        }

        @Override
        public void onClick(double mx, double my) { switchTab(name); }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean sel = name.equals(tab);
            boolean hov = isMouseOver(mx, my);
            g.fill(getX(), getY(), getX() + width, getY() + height, sel ? 0xFF2A1218 : hov ? 0xFF1E1E26 : 0xFF17171C);
            if (sel) g.fill(getX(), getY(), getX() + 3, getY() + height, C_ACCENT);
            g.drawString(font(), name, getX() + 10, getY() + (height - 8) / 2, sel ? C_TEXT : C_DIM);
        }
    }

    private static final class Tog extends Base {
        private final String label;
        private final Supplier<Boolean> get;
        private final Consumer<Boolean> set;

        Tog(int x, int y, int w, String label, Supplier<Boolean> get, Consumer<Boolean> set) {
            super(x, y, w, 20);
            this.label = label;
            this.get = get;
            this.set = set;
        }

        @Override
        public void onClick(double mx, double my) { set.accept(!get.get()); }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean on = get.get();
            boolean hov = isMouseOver(mx, my);
            frame(g, getX(), getY(), width, height, hov ? 0xFF4A4A56 : 0xFF2C2C34, C_FIELD);
            g.drawString(font(), font().plainSubstrByWidth(label, width - 40), getX() + 6, getY() + 6, C_TEXT);
            int px = getX() + width - 28, py = getY() + 5;
            g.fill(px, py, px + 22, py + 10, on ? C_ON : C_OFF);
            g.fill(on ? px + 13 : px + 1, py + 1, on ? px + 21 : px + 9, py + 9, 0xFFF2F2F2);
        }
    }

    private static final class Cyc extends Base {
        private final String label;
        private final List<String> opts;
        private int idx;
        private final Consumer<String> set;

        Cyc(int x, int y, int w, String label, List<String> opts, String cur, Consumer<String> set) {
            super(x, y, w, 20);
            this.label = label;
            this.opts = opts;
            this.set = set;
            this.idx = Math.max(0, opts.indexOf(cur));
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            if (!active || !visible || !clicked(mx, my) || opts.isEmpty() || (b != 0 && b != 1)) return false;
            playDownSound(Minecraft.getInstance().getSoundManager());
            idx = (idx + (b == 0 ? 1 : opts.size() - 1)) % opts.size();
            set.accept(opts.get(idx));
            return true;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean hov = isMouseOver(mx, my);
            frame(g, getX(), getY(), width, height, hov ? 0xFF4A4A56 : 0xFF2C2C34, C_FIELD);
            String v = opts.isEmpty() ? "-" : opts.get(idx);
            String l = label + ": ";
            int lw = font().width(l);
            g.drawString(font(), l, getX() + 6, getY() + 6, C_DIM);
            g.drawString(font(), font().plainSubstrByWidth(v, Math.max(10, width - lw - 22)), getX() + 6 + lw, getY() + 6, C_TEXT);
            g.drawString(font(), "‹›", getX() + width - 14, getY() + 6, C_DIM);
        }
    }

    private static final class Sld extends Base {
        private final String label, unit;
        private final double min, max, step, reset;
        private final boolean integer;
        private final Supplier<Double> get;
        private final Consumer<Double> set;
        private boolean dragging;

        Sld(int x, int y, int w, String label, String unit, double min, double max, double step, double reset, boolean integer, Supplier<Double> get, Consumer<Double> set) {
            super(x, y, w, 20);
            this.label = label;
            this.unit = unit;
            this.min = min;
            this.max = max;
            this.step = step;
            this.reset = reset;
            this.integer = integer;
            this.get = get;
            this.set = set;
        }

        private void fromMouse(double mx) {
            double t = Math.max(0, Math.min(1, (mx - getX() - 2) / (width - 4)));
            double v = min + t * (max - min);
            v = Math.round((v - min) / step) * step + min;
            set.accept(Math.max(min, Math.min(max, v)));
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            if (!active || !visible || !clicked(mx, my)) return false;
            if (b == 1) { set.accept(reset); return true; }
            if (b != 0) return false;
            dragging = true;
            fromMouse(mx);
            return true;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
            if (!dragging || b != 0) return false;
            fromMouse(mx);
            return true;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int b) {
            boolean was = dragging;
            dragging = false;
            return was;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean hov = isMouseOver(mx, my);
            frame(g, getX(), getY(), width, height, hov || dragging ? 0xFF4A4A56 : 0xFF2C2C34, C_FIELD);
            double v = get.get();
            double t = Math.max(0, Math.min(1, (v - min) / (max - min)));
            g.fill(getX() + 1, getY() + 1, getX() + 1 + (int) ((width - 2) * t), getY() + height - 1, mix(C_ACCENT, 0xFF101014, 0.5F));
            String val = integer ? String.valueOf((int) Math.round(v)) : String.format(Locale.ROOT, "%.2f", v);
            g.drawCenteredString(font(), label + ": " + val + unit, getX() + width / 2, getY() + 6, C_TEXT);
        }
    }

    // ------------------------------------------------------------------ construccion ----
    @Override
    protected void init() {
        flush();
        active = this;
        W = Math.min(width - 12, 500);
        H = Math.min(height - 12, 330);
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
        cx = x0 + SIDE_W + 10;
        cw = W - SIDE_W - 10 - 14;
        ctop = y0 + 30;
        cbot = y0 + H - 6;
        tabButtons.clear();
        int th = Math.max(14, Math.min(21, (H - 36) / tabs.size()));
        int ty = y0 + 28;
        for (String t : tabs) {
            Tab b = new Tab(x0 + 4, ty, SIDE_W - 6, th - 1, t);
            tabButtons.add(b);
            addRenderableWidget(b);
            ty += th;
        }
        buildTab();
    }

    private void switchTab(String t) {
        if (t.equals(tab)) return;
        tab = t;
        lastTab = t;
        scroll = 0;
        buildTab();
    }

    private void clearCells() {
        for (Cell c : cells) removeWidget(c.w);
        cells.clear();
        ctxBox = null;
    }

    private void buildTab() {
        clearCells();
        cy = 0;
        slot = 0;
        rowH = 0;
        switch (tab) {
            case "Spawner" -> tabSpawner();
            case "General" -> tabGeneral();
            case "Conducta" -> tabBehavior();
            case "Atributos" -> tabAttributes();
            case "Equipo" -> tabEquipment();
            case "Diálogo" -> tabDialogue();
            case "IA / Contexto" -> tabBrain();
            case "Sonidos" -> tabSounds();
            case "Rutina" -> tabRoutine();
            default -> tabActions();
        }
        endRow();
        contentH = cy;
        int view = cbot - ctop;
        for (Cell c : cells) {
            if (c.fill) {
                int h = Math.max(70, view - c.relY - 2);
                c.w.setHeight(h);
                contentH = Math.max(contentH, c.relY + h + 2);
            }
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - view)));
    }

    private void place(boolean half, int h, Make m) {
        int x, w;
        int hw = (cw - GAP) / 2;
        if (half) {
            x = slot == 0 ? cx : cx + hw + GAP;
            w = slot == 0 ? hw : cw - hw - GAP;
        } else {
            if (slot == 1) { cy += rowH + 4; slot = 0; }
            x = cx;
            w = cw;
        }
        for (AbstractWidget wd : m.make(x, w)) {
            cells.add(new Cell(wd, cy + wd.getY()));
            addWidget(wd);
        }
        if (half) {
            if (slot == 0) { slot = 1; rowH = h; }
            else { slot = 0; cy += Math.max(rowH, h) + 4; }
        } else cy += h + 4;
    }

    private void endRow() {
        if (slot == 1) { cy += rowH + 4; slot = 0; }
    }

    private void header(String t) {
        endRow();
        if (!cells.isEmpty()) cy += 4;
        place(false, 12, (x, w) -> List.of(new Line(x, 0, w, t)));
    }

    private void note(String t) {
        endRow();
        for (FormattedCharSequence seq : font().split(Component.literal(t), cw)) {
            StringBuilder sb = new StringBuilder();
            seq.accept((i, style, cp) -> { sb.appendCodePoint(cp); return true; });
            String line = sb.toString();
            place(false, 9, (x, w) -> List.of(new Lbl(x, 0, () -> line, C_DIM)));
            cy -= 2;
        }
        cy += 4;
    }

    private void textRaw(String label, Supplier<String> get, Consumer<String> set, boolean half, boolean number) {
        place(half, 32, (x, w) -> {
            Lbl l = new Lbl(x, 0, () -> label, C_DIM);
            EditBox eb = new EditBox(font(), x, 11, w, 18, Component.empty());
            eb.setMaxLength(number ? 12 : 2000);
            if (number) eb.setFilter(s -> s.matches("-?\\d*\\.?\\d*"));
            eb.setValue(get.get());
            eb.moveCursorToStart();
            eb.setResponder(set);
            return List.of(l, eb);
        });
    }

    private void text(String k, String label, boolean half) { textRaw(label, () -> gs(k), v -> setS(k, v), half, false); }

    private void num(String k, String label, boolean half, boolean integer) {
        textRaw(label, () -> {
            double v = gn(k);
            return integer || v == Math.rint(v) ? String.valueOf((long) Math.rint(v)) : String.format(Locale.ROOT, "%.3f", v).replaceAll("0+$", "");
        }, s -> {
            try { if (!s.isEmpty() && !s.equals("-") && !s.equals(".")) setN(k, Double.parseDouble(s), integer); } catch (NumberFormatException ignored) { }
        }, half, true);
    }

    private void tog(String k, String label, boolean half) {
        place(half, 20, (x, w) -> List.of(new Tog(x, 0, w, label, () -> k.startsWith("@") ? root.has(k.substring(1)) && root.get(k.substring(1)).getAsBoolean() : gb(k), v -> setB(k, v))));
    }

    private void sld(String k, String label, String unit, double min, double max, double step, double reset, boolean integer) {
        place(false, 20, (x, w) -> List.of(new Sld(x, 0, w, label, unit, min, max, step, reset, integer, () -> gn(k), v -> setN(k, v, integer))));
    }

    private void cyc(String k, String label, List<String> opts, boolean allowNone, boolean half) {
        List<String> o = new ArrayList<>();
        if (allowNone) o.add("(ninguno)");
        o.addAll(opts);
        String cur = gs(k);
        place(half, 20, (x, w) -> List.of(new Cyc(x, 0, w, label, o, cur.isEmpty() && allowNone ? "(ninguno)" : cur, v -> setS(k, v.equals("(ninguno)") ? "" : v))));
    }

    private void cycRaw(String label, List<String> opts, String cur, Consumer<String> set, boolean half) {
        place(half, 20, (x, w) -> List.of(new Cyc(x, 0, w, label, opts, cur, set)));
    }

    private void btn(String label, int color, boolean half, Runnable r) { btnAt(label, color, half, 0, r); }

    /** boton alineado con el cuadro de un campo con etiqueta (dy = 11) */
    private void btnAt(String label, int color, boolean half, int dy, Runnable r) {
        place(half, 20 + dy, (x, w) -> List.of(new Btn(x, dy, w, label, color, r)));
    }

    // ------------------------------------------------------------------ pestañas ----
    private void tabSpawner() {
        header("SPAWNER DE NPC");
        tog("@activo", "Activo", false);
        num("@intervalo", "Reaparece tras (ticks, 20 = 1 s)", true, true);
        num("@maximo", "Máximo vivos a la vez", true, true);
        num("@radio", "Radio de aparición (bloques)", true, true);
        num("@rango", "Activo si hay un jugador a (bloques)", true, true);
        note("El NPC que repite es el que configuras en las demás pestañas (la plantilla del spawner).");
    }

    private void tabGeneral() {
        header("IDENTIDAD");
        text("nombre", "Nombre", false);
        text("id", "Clave interna (une al NPC con su contexto, diálogos y rutinas)", false);
        tog("mostrar_nombre", "Mostrar nombre", true);
        tog("persistente", "No desaparece", true);
        text("color_nombre", "Color del nombre (hex, ej. FFFFFF)", false);
        header("ASPECTO");
        text("skin", "Skin: nombre de un jugador, o espacio:ruta de un resource pack", false);
        tog("brazos_finos", "Brazos finos (Alex)", true);
        sld("tamano", "Tamaño", "x", 0.5, 2.0, 0.05, 1.0, false);
        String motor = str(root, "motor");
        List<String> ms = List.of("Epic Fight (huesos EF)", "Normal (huesos MC)");
        cycRaw("Motor de animación", ms, motor.equals("ef") ? ms.get(0) : ms.get(1), v -> {
            String m = v.startsWith("Epic") ? "ef" : "normal";
            JsonObject o = new JsonObject();
            o.addProperty("op", "motor");
            o.addProperty("motor", m);
            root.addProperty("motor", m);
            send(o);
        }, false);
        note("Al cambiar de motor se reabre el editor con el NPC nuevo (se conserva toda la configuración).");
    }

    private void tabBehavior() {
        header("MIRADA Y MOVIMIENTO");
        cyc("mirar", "Mira a", List.of("no", "jugadores", "todos"), false, true);
        cyc("hostil", "Ataca a", List.of("ninguno", "jugadores", "mobs", "ambos"), false, true);
        sld("mirar_rango", "Rango de mirada", " bl", 2, 32, 1, 8, false);
        tog("quieto", "Quieto en su sitio", true);
        tog("vagar", "Vaga cerca de su hogar", true);
        sld("vagar_radio", "Radio de vagar", " bl", 2, 24, 1, 6, true);
        List<String> st = list(cat, "posturas");
        cycRaw("Postura", st, st.isEmpty() ? "normal" : st.get(Math.max(0, Math.min(st.size() - 1, (int) gn("postura")))), v -> {
            data.addProperty("postura", st.indexOf(v));
            sendSet("postura", String.valueOf(st.indexOf(v)));
        }, true);
        btn("Fijar giro hacia donde miro", 0xFFE59A1F, true, () -> act("fijar_giro"));
        tog("fijar_giro", "Cuerpo con ángulo fijo", true);
        header("COMBATE Y DEFENSA");
        tog("invulnerable", "Invulnerable", true);
        tog("empujable", "Se deja empujar", true);
        tog("represalia", "Se defiende si lo atacan", true);
        text("faccion", "Facción (los de la misma no se atacan)", false);
    }

    private void tabAttributes() {
        header("ATRIBUTOS");
        num("vida", "Vida máxima", true, false);
        num("velocidad", "Velocidad", true, false);
        num("dano", "Daño de ataque", true, false);
        num("armadura", "Armadura", true, false);
        num("empuje_res", "Resistencia al empuje (0 a 1)", true, false);
        num("rango_vision", "Rango de visión (bloques)", true, false);
        num("regeneracion", "Regeneración (vida por segundo)", true, false);
        note("Velocidad normal de un humano: 0.25. Vida 20 = 10 corazones.");
    }

    private void tabEquipment() {
        header("EQUIPO");
        btn("Copiar lo que tengo en la mano", 0xFFB45BE0, true, () -> equip("mano"));
        btn("Copiar mi mano izquierda", 0xFFB45BE0, true, () -> equip("mano2"));
        btn("Copiar mi armadura", 0xFFB45BE0, true, () -> equip("armadura"));
        btn("Vaciar equipo", 0xFFD42A3A, true, () -> act("vaciar_equipo"));
        tog("suelta_equipo", "Suelta el equipo al morir", false);
        header("ARMAS (TACZ)");
        boolean tacz = cat.has("tacz") && cat.get("tacz").getAsBoolean();
        note(tacz ? "TACZ detectado: copia un arma de TACZ en la mano y el NPC la usará para disparar." : "TACZ no está instalado: las armas de TACZ no dispararán.");
        sld("arma_alcance", "Alcance de disparo", " bl", 8, 64, 1, 24, true);
        sld("arma_cadencia", "Ticks entre disparos", "", 2, 40, 1, 6, true);
        sld("arma_dispersion", "Error de puntería", "°", 0, 10, 0.5, 2, false);
        tog("arma_infinita", "Munición infinita", false);
    }

    private void equip(String que) {
        JsonObject o = new JsonObject();
        o.addProperty("op", "equipo");
        o.addProperty("que", que);
        send(o);
    }

    private void tabDialogue() {
        header("CÓMO HABLA");
        cyc("dialogo", "Archivo de diálogo", list(cat, "dialogos"), true, false);
        text("lineas", "Frases simples si no hay archivo (separa con |)", false);
        tog("burbuja", "Burbuja sobre la cabeza", true);
        tog("chat", "Escribir en el chat", true);
        tog("mirar_al_hablar", "Mira a quien le habla", true);
        tog("saludo_auto", "Saluda al acercarse", true);
        sld("saludo_rango", "Rango de saludo", " bl", 2, 15, 1, 5, true);
        text("saludo", "Frase de saludo (vacía = la del contexto)", false);
        sld("velocidad_texto", "Velocidad del texto", "%", 50, 250, 5, 100, true);
        text("gestos", "Gestos al hablar (separados por coma)", false);
        header("ENTRE NPCS");
        tog("charla_npc", "Charla con otros NPCs cercanos", false);
        text("charla_lineas", "Frases para charlar (separa con |; vacío = usa su contexto)", false);
        btn("Recargar diálogos, contexto y rutinas", 0xFF2AA6C8, false, () -> act("recargar"));
    }

    private void tabBrain() {
        header("RESPONDE A LO QUE LE ESCRIBES (SIN INTERNET)");
        tog("charla_ia", "Activada", true);
        cyc("personalidad", "Personalidad base", List.of("neutral", "amable", "seria", "burlona", "fria"), false, true);
        cycRaw("Editando", List.of("Este NPC", "Compartido (todos)"), editMundo ? "Compartido (todos)" : "Este NPC", v -> {
            boolean m = v.startsWith("Compartido");
            if (m == editMundo) return;
            editMundo = m;
            if (ctxBox != null) ctxBox.setValue(editMundo ? ctxMundo : ctxNpc);
        }, true);
        btn("Guardar contexto", 0xFF2EBB55, true, () -> { saveCtx(); setStatus("Guardando contexto...", C_OK); });
        btn("Insertar plantilla de ejemplo", 0xFF2AA6C8, true, () -> {
            if (ctxBox == null) return;
            String cur = ctxBox.getValue();
            ctxBox.setValue(cur.isBlank() ? Ctx.TEMPLATE : cur + "\n\n" + Ctx.TEMPLATE);
        });
        btn("Vaciar cuadro", 0xFFD42A3A, true, () -> { if (ctxBox != null) ctxBox.setValue(""); });
        textRaw("Prueba: escríbele algo al NPC", () -> askText, v -> askText = v, true, false);
        btnAt("Preguntar ahora", 0xFFE0457B, true, 11, () -> {
            if (isSpawner) { setStatus("Las pruebas se hacen con un NPC ya puesto", 0xFFE59A1F); return; }
            saveCtx();
            JsonObject o = new JsonObject();
            o.addProperty("op", "preguntar");
            o.addProperty("texto", askText);
            send(o);
        });
        place(false, 11, (x, w) -> List.of(new Lbl(x, 0, () -> {
            int n = (editMundo ? ctxMundo : ctxNpc).length();
            return "contexto/" + (editMundo ? "_mundo" : ctxKey) + ".txt · " + n + " / " + Ctx.MAX_CHARS + " caracteres" + ((editMundo ? dirtyMundo : dirtyNpc) ? "  · sin guardar" : "");
        }, C_DIM)));
        cy -= 2;
        place(false, 80, (x, w) -> {
            MultiLineEditBox box = new MultiLineEditBox(font(), x, 0, w, 80, Component.literal("Pega aquí todo lo que debe saber y cómo habla…"), Component.empty());
            box.setCharacterLimit(Ctx.MAX_CHARS);
            box.setValue(editMundo ? ctxMundo : ctxNpc);
            box.setValueListener(v -> {
                if (editMundo) { ctxMundo = v; dirtyMundo = true; } else { ctxNpc = v; dirtyNpc = true; }
            });
            ctxBox = box;
            return List.of(box);
        });
        cells.get(cells.size() - 1).fill = true;
    }

    private void saveCtx() {
        if (dirtyNpc) { sendCtx("npc", ctxNpc); dirtyNpc = false; }
        if (dirtyMundo) { sendCtx("mundo", ctxMundo); dirtyMundo = false; }
    }

    private void sendCtx(String dest, String text) {
        int chunk = 5000;
        int n = Math.max(1, (text.length() + chunk - 1) / chunk);
        for (int i = 0; i < n; i++) {
            JsonObject o = new JsonObject();
            o.addProperty("op", "ctx_save");
            o.addProperty("dest", dest);
            o.addProperty("i", i);
            o.addProperty("n", n);
            o.addProperty("texto", text.substring(Math.min(text.length(), i * chunk), Math.min(text.length(), (i + 1) * chunk)));
            send(o);
        }
    }

    private void tabSounds() {
        header("SONIDOS (id de sonido, p. ej. minecraft:entity.villager.ambient)");
        text("snd_idle", "Ambiente", false);
        text("snd_hurt", "Al ser herido", false);
        text("snd_death", "Al morir", false);
        text("snd_talk", "Al hablar", false);
        sld("snd_vol", "Volumen", "", 0, 2, 0.05, 1, false);
        sld("snd_tono", "Tono", "", 0.5, 2, 0.05, 1, false);
        sld("snd_idle_cada", "Sonido de ambiente cada", " ticks", 40, 1200, 20, 300, true);
        note("También sirven los sonidos de un resource pack o de otro mod (usa su id).");
    }

    private void tabRoutine() {
        header("OBJETIVOS / RUTINA");
        cyc("rutina", "Rutina", list(cat, "rutinas"), true, false);
        tog("rutina_auto", "Empieza sola al aparecer", false);
        btn("Iniciar rutina", 0xFF2EBB55, true, () -> rutina("iniciar"));
        btn("Detener rutina", 0xFFD42A3A, true, () -> rutina("parar"));
        note("Las rutinas son archivos config/obscurenpc/rutinas/<id>.json: ir a un punto, esperar, hablar, gestos, comandos, atacar… Sirven para actores de escenas.");
    }

    private void rutina(String accion) {
        JsonObject o = new JsonObject();
        o.addProperty("op", "rutina");
        o.addProperty("accion", accion);
        send(o);
    }

    private void tabActions() {
        header("PROBAR");
        List<String> g = new ArrayList<>(list(cat, "gestos"));
        g.addAll(list(cat, "posturas_gesto"));
        g.addAll(List.of("comer", "beber", "cavar", "sentarse", "saltar"));
        cycRaw("Gesto", g, gestoTest, v -> gestoTest = v, true);
        btn("▶ Reproducir", 0xFFE5C21F, true, () -> {
            JsonObject o = new JsonObject();
            o.addProperty("op", "gesto");
            o.addProperty("id", gestoTest);
            send(o);
        });
        textRaw("Hacer que diga…", () -> sayText, v -> sayText = v, true, false);
        btnAt("Decirlo", 0xFFE0457B, true, 11, () -> {
            JsonObject o = new JsonObject();
            o.addProperty("op", "hablar");
            o.addProperty("texto", sayText);
            send(o);
        });
        if (!isSpawner) {
            btn("Probar el diálogo conmigo", 0xFF2AA6C8, true, () -> act("dialogo"));
            btn("Traer NPC a mí", 0xFF2F7FEA, true, () -> act("tp_aqui"));
            btn("Ir hasta el NPC", 0xFF2F7FEA, true, () -> act("tp_a_el"));
            btn("Duplicar", 0xFF2EBB55, true, () -> act("duplicar"));
        }
        header("PLANTILLA (sostén el Editor o el Creador)");
        btn("Copiar al ítem", 0xFFB45BE0, true, () -> act("plantilla"));
        btn("Pegar desde el ítem", 0xFFB45BE0, true, () -> act("plantilla_aplicar"));
        if (!isSpawner) {
            header("PELIGRO");
            Btn[] ref = new Btn[1];
            place(false, 20, (x, w) -> {
                Btn b = new Btn(x, 0, w, "BORRAR NPC", 0xFFD42A3A, () -> {
                    if (System.currentTimeMillis() - deleteArmed < 3000) { act("borrar"); onClose(); }
                    else { deleteArmed = System.currentTimeMillis(); setStatus("Pulsa otra vez para borrar", 0xFFD42A3A); }
                });
                b.dyn = () -> System.currentTimeMillis() - deleteArmed < 3000 ? "¿SEGURO? PULSA DE NUEVO" : "BORRAR NPC";
                ref[0] = b;
                return List.of(b);
            });
        }
    }

    // ------------------------------------------------------------------ pintado e interaccion ----
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        frame(g, x0 - 1, y0 - 1, W + 2, H + 2, C_BORDER, C_BG);
        g.fill(x0, y0, x0 + W, y0 + 22, C_BAR);
        g.fill(x0, y0 + 22, x0 + W, y0 + 23, C_ACCENT);
        g.fill(x0, y0 + 23, x0 + SIDE_W, y0 + H, C_SIDE);
        g.fill(x0 + SIDE_W, y0 + 23, x0 + SIDE_W + 1, y0 + H, 0xFF2A1A1F);
        String name = isSpawner ? "Spawner de NPC" : str(data, "nombre");
        g.drawString(font(), "OBSCURE NPC", x0 + 8, y0 + 7, C_ACCENT);
        g.drawString(font(), "· " + name, x0 + 8 + font().width("OBSCURE NPC") + 6, y0 + 7, C_TEXT);
        long age = System.currentTimeMillis() - statusAt;
        if (!status.isEmpty() && age < 5000) {
            int alpha = age > 3500 ? (int) (255 * (5000 - age) / 1500.0) : 255;
            int col = (Math.max(8, alpha) << 24) | (statusColor & 0xFFFFFF);
            String s = font().plainSubstrByWidth(status, W - 240);
            g.drawString(font(), s, x0 + W - 8 - font().width(s), y0 + 7, col);
        }
        super.render(g, mx, my, pt);

        int view = cbot - ctop;
        g.enableScissor(cx - 2, ctop, cx + cw + 4, cbot);
        for (Cell c : cells) {
            int y = ctop + c.relY - scroll;
            c.w.setY(y);
            int h = Math.max(1, c.w.getHeight());
            c.w.visible = c.relY + h > scroll && c.relY < scroll + view;
            if (c.w.visible) c.w.render(g, mx, my, pt);
        }
        g.disableScissor();
        if (contentH > view) {
            int bh = Math.max(16, view * view / contentH);
            int by = ctop + (int) ((view - bh) * (scroll / (double) (contentH - view)));
            g.fill(cx + cw + 6, ctop, cx + cw + 9, cbot, 0xFF1C1C22);
            g.fill(cx + cw + 6, by, cx + cw + 9, by + bh, C_ACCENT);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (mx >= cx - 2 && (my < ctop || my > cbot)) return false;
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (super.mouseScrolled(mx, my, delta)) return true;
        int view = cbot - ctop;
        if (contentH > view && mx >= cx - 4) {
            scroll = Math.max(0, Math.min(contentH - view, scroll - (int) (delta * 18)));
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void removed() {
        flush();
        saveCtx();
        if (active == this) active = null;
        super.removed();
    }
}
