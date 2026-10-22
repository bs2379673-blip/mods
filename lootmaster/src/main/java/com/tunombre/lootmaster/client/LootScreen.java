package com.tunombre.lootmaster.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tunombre.lootmaster.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Panel de Loot Master: pestanas a la izquierda y ajustes a la derecha (mismo estilo que el editor de NPC). */
public final class LootScreen extends Screen {
    private static LootScreen active;
    private static String lastTab = "Resumen";

    private static final int C_BG = 0xF00C0C0F, C_SIDE = 0xFF121216, C_BORDER = 0xFF6E1420, C_ACCENT = 0xFFC8283A, C_TEXT = 0xFFE8E8EC,
            C_DIM = 0xFF8C8C98, C_OK = 0xFF55D68A, C_FIELD = 0xFF1C1C22, C_BAR = 0xFF2B0E14, C_ON = 0xFF2EBB55, C_OFF = 0xFF55555F, C_WARN = 0xFFE0A030;
    private static final int GAP = 6, SIDE_W = 108;
    private static final List<String> TABS = List.of("Resumen", "Varita", "Zonas", "Perfiles", "Global", "Relleno inicial", "Evento", "Tipos", "Ayuda");

    private JsonObject st;
    private String sig = "";
    private String tab;
    private final List<Cell> cells = new ArrayList<>();
    private int scroll, contentH;
    private int W, H, x0, y0, cx, cw, ctop, cbot;
    private int cy, slot, rowH;

    private final Map<String, Long> pending = new LinkedHashMap<>();
    private final Map<String, JsonObject> pendingMsg = new LinkedHashMap<>();
    private final Map<String, Long> armed = new LinkedHashMap<>();
    private String status = "";
    private int statusColor = C_OK;
    private long statusAt;
    private String fillDim = "todas";
    private String evPick = "";
    private String zoneName = "";

    private static final class Cell {
        final AbstractWidget w;
        final int relY;

        Cell(AbstractWidget w, int relY) {
            this.w = w;
            this.relY = relY;
        }
    }

    private interface Make { List<AbstractWidget> make(int x, int w); }

    private LootScreen(JsonObject st) {
        super(Component.literal("Loot Master"));
        this.st = st;
        this.tab = TABS.contains(lastTab) ? lastTab : TABS.get(0);
        this.sig = signature(st);
    }

    public static void onState(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (active == null) {
                if (Minecraft.getInstance().screen instanceof LootScreen) return;
                if (o.has("zones")) WandRender.setZones(o.getAsJsonArray("zones"));
                Minecraft.getInstance().setScreen(new LootScreen(o));
                return;
            }
            active.st = o;
            if (o.has("zones")) WandRender.setZones(o.getAsJsonArray("zones"));
            String s = signature(o);
            if (!s.equals(active.sig)) { active.sig = s; active.buildTab(); }
        } catch (RuntimeException ignored) { }
    }

    public static void wantTab(String t) {
        if (!TABS.contains(t)) return;
        lastTab = t;
        if (active != null) { active.tab = t; active.scroll = 0; active.buildTab(); }
    }

    public static void onSel(String json) {
        if (active == null || active.st == null) return;
        try { active.st.add("sel", JsonParser.parseString(json).getAsJsonObject()); } catch (RuntimeException ignored) { }
    }

    public static void onJob(String json) {
        if (active == null || active.st == null) return;
        try { active.st.add("job", JsonParser.parseString(json).getAsJsonObject()); } catch (RuntimeException ignored) { }
    }

    public static void notice(String msg) {
        if (active != null) active.setStatus(msg, C_OK);
    }

    private void setStatus(String s, int color) {
        status = s;
        statusColor = color;
        statusAt = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ datos ----
    private static String signature(JsonObject o) {
        StringBuilder sb = new StringBuilder();
        for (JsonElement e : o.getAsJsonArray("profiles")) {
            JsonObject p = e.getAsJsonObject();
            sb.append(p.get("id").getAsString()).append(',');
        }
        sb.append('|');
        for (JsonElement e : o.getAsJsonArray("dims")) sb.append(e.getAsJsonObject().get("id").getAsString()).append(e.getAsJsonObject().get("done").getAsString().isEmpty() ? "0" : "1").append(',');
        sb.append('|');
        for (JsonElement e : o.getAsJsonArray("types")) sb.append(e.getAsJsonObject().get("id").getAsString()).append(',');
        sb.append('|');
        if (o.has("zones")) for (JsonElement e : o.getAsJsonArray("zones")) sb.append(e.getAsJsonObject().get("name").getAsString()).append(',');
        sb.append('|').append(o.has("wandUndo") && o.get("wandUndo").getAsInt() > 0 ? 'U' : 'N').append(str(o, "evProfile").isEmpty() ? 0 : 1).append(o.get("evChanged").getAsInt() > 0 ? 1 : 0).append(o.get("origCount").getAsInt() > 0 ? 1 : 0);
        JsonObject j = o.getAsJsonObject("job");
        sb.append(j != null && j.has("running") && j.get("running").getAsBoolean() ? 'R' : 'S');
        return sb.toString();
    }

    private static String str(JsonObject o, String k) { return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; }

    private static boolean bool(JsonObject o, String k) { return o != null && o.has(k) && o.get(k).getAsBoolean(); }

    private static double num(JsonObject o, String k) { try { return o.has(k) ? o.get(k).getAsDouble() : 0; } catch (RuntimeException e) { return 0; } }

    private JsonObject set() { return st.getAsJsonObject("set"); }

    private JsonObject job() { return st.getAsJsonObject("job"); }

    private boolean running() { return bool(job(), "running"); }

    private void send(JsonObject o) { Net.CH.sendToServer(new Net.ToServer("op", o.toString())); }

    private void op(String op) {
        JsonObject o = new JsonObject();
        o.addProperty("op", op);
        send(o);
    }

    private void opWith(String op, String k, String v) {
        JsonObject o = new JsonObject();
        o.addProperty("op", op);
        o.addProperty(k, v);
        send(o);
    }

    private void setSetting(String k, String v) {
        set().addProperty(k, v);
        if (v.equals("true") || v.equals("false")) set().addProperty(k, Boolean.parseBoolean(v));
        JsonObject o = new JsonObject();
        o.addProperty("op", "set");
        o.addProperty("k", k);
        o.addProperty("v", v);
        send(o);
    }

    private void queue(String key, JsonObject o) {
        pending.put(key, System.currentTimeMillis());
        pendingMsg.put(key, o);
    }

    private void flush() {
        for (String k : new ArrayList<>(pending.keySet())) send(pendingMsg.get(k));
        pending.clear();
        pendingMsg.clear();
    }

    private void profileOp(JsonObject p, String k, String field, String v) {
        JsonObject o = new JsonObject();
        o.addProperty("op", "profile");
        o.addProperty("id", p.get("id").getAsString());
        o.addProperty("k", k);
        o.addProperty("v", v);
        if (field != null) {
            if (v.equals("true") || v.equals("false")) p.addProperty(field, Boolean.parseBoolean(v)); else { try { p.addProperty(field, Double.parseDouble(v)); } catch (NumberFormatException ignored) { } }
        }
        if (k.equals("min")) queue("min:" + p.get("id").getAsString(), o); else send(o);
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        for (var it = pending.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (now - e.getValue() > 400) {
                send(pendingMsg.remove(e.getKey()));
                it.remove();
            }
        }
    }

    private boolean confirm(String key) {
        long now = System.currentTimeMillis();
        Long t = armed.get(key);
        if (t != null && now - t < 3000) { armed.remove(key); return true; }
        armed.put(key, now);
        return false;
    }

    private boolean isArmed(String key) {
        Long t = armed.get(key);
        return t != null && System.currentTimeMillis() - t < 3000;
    }

    // ------------------------------------------------------------------ widgets propios ----
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
        private final Supplier<String> title;

        Line(int x, int y, int w, Supplier<String> title) {
            super(x, y, w, 12);
            this.title = title;
            this.active = false;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            String ti = title.get();
            int tw = font().width(ti);
            g.drawString(font(), ti, getX(), getY() + 2, C_ACCENT);
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


    /** Texto con saltos de linea automaticos, de altura fija. */
    private static final class Wrap extends Base {
        private final Supplier<String> text;
        private final int color, lines;

        Wrap(int x, int y, int w, int lines, Supplier<String> text, int color) {
            super(x, y, w, lines * 10);
            this.lines = lines;
            this.text = text;
            this.color = color;
            this.active = false;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int i = 0;
            for (FormattedCharSequence seq : font().split(Component.literal(text.get()), width)) {
                if (i >= lines) break;
                g.drawString(font(), seq, getX(), getY() + i * 10, color);
                i++;
            }
        }
    }

    /** Barra de progreso de la tarea en curso. */
    private static final class Bar extends Base {
        private final Supplier<Double> frac;
        private final Supplier<String> label;

        Bar(int x, int y, int w, Supplier<Double> frac, Supplier<String> label) {
            super(x, y, w, 16);
            this.frac = frac;
            this.label = label;
            this.active = false;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            frame(g, getX(), getY(), width, height, 0xFF2C2C34, C_FIELD);
            double f = Math.max(0, Math.min(1, frac.get()));
            g.fill(getX() + 1, getY() + 1, getX() + 1 + (int) ((width - 2) * f), getY() + height - 1, mix(C_ACCENT, 0xFF101014, 0.35F));
            g.drawCenteredString(font(), font().plainSubstrByWidth(label.get(), width - 8), getX() + width / 2, getY() + 4, C_TEXT);
        }
    }

    // ------------------------------------------------------------------ construccion ----
    @Override
    protected void init() {
        flush();
        active = this;
        op("open");
        W = Math.min(width - 12, 520);
        H = Math.min(height - 12, 340);
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
        cx = x0 + SIDE_W + 10;
        cw = W - SIDE_W - 10 - 14;
        ctop = y0 + 30;
        cbot = y0 + H - 6;
        int th = Math.max(14, Math.min(22, (H - 36) / TABS.size()));
        int ty = y0 + 28;
        for (String t : TABS) {
            addRenderableWidget(new Tab(x0 + 4, ty, SIDE_W - 6, th - 1, t));
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

    private void buildTab() {
        for (Cell c : cells) removeWidget(c.w);
        cells.clear();
        cy = 0;
        slot = 0;
        rowH = 0;
        switch (tab) {
            case "Resumen" -> tabSummary();
            case "Varita" -> tabWand();
            case "Zonas" -> tabZones();
            case "Perfiles" -> tabProfiles();
            case "Global" -> tabGlobal();
            case "Relleno inicial" -> tabFill();
            case "Evento" -> tabEvent();
            case "Tipos" -> tabTypes();
            default -> tabHelp();
        }
        endRow();
        contentH = cy;
        int view = cbot - ctop;
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

    private void header(Supplier<String> t) {
        endRow();
        if (!cells.isEmpty()) cy += 4;
        place(false, 12, (x, w) -> List.of(new Line(x, 0, w, t)));
    }

    private void header(String t) { header(() -> t); }

    private void note(String t) {
        endRow();
        int lines = font().split(Component.literal(t), cw).size();
        place(false, lines * 10, (x, w) -> List.of(new Wrap(x, 0, w, lines, () -> t, C_DIM)));
    }

    private void dyn(int lines, int color, Supplier<String> s) {
        endRow();
        place(false, lines * 10, (x, w) -> List.of(new Wrap(x, 0, w, lines, s, color)));
    }

    private void textNum(String label, Supplier<String> get, Consumer<String> setter, boolean half) {
        place(half, 32, (x, w) -> {
            Lbl l = new Lbl(x, 0, () -> label, C_DIM);
            EditBox eb = new EditBox(font(), x, 11, w, 18, Component.empty());
            eb.setMaxLength(9);
            eb.setFilter(s -> s.matches("\\d*\\.?\\d*"));
            eb.setValue(get.get());
            eb.moveCursorToStart();
            eb.setResponder(setter);
            return List.of(l, eb);
        });
    }

    private void tog(String label, Supplier<Boolean> get, Consumer<Boolean> setter, boolean half) {
        place(half, 20, (x, w) -> List.of(new Tog(x, 0, w, label, get, setter)));
    }

    private void cycMap(String label, List<String> labels, List<String> values, String cur, Consumer<String> setter, boolean half) {
        place(half, 20, (x, w) -> List.of(new Cyc(x, 0, w, label, labels, labels.get(Math.max(0, values.indexOf(cur))), v -> setter.accept(values.get(labels.indexOf(v))))));
    }

    private void btn(String label, int color, boolean half, Runnable r) {
        place(half, 20, (x, w) -> List.of(new Btn(x, 0, w, label, color, r)));
    }

    /** boton que pide pulsar dos veces */
    private void btnSure(String key, String label, int color, boolean half, Runnable r) {
        place(half, 20, (x, w) -> {
            Btn b = new Btn(x, 0, w, label, color, () -> { if (confirm(key)) r.run(); });
            b.dyn = () -> isArmed(key) ? "¿SEGURO? PULSA DE NUEVO" : label;
            return List.of(b);
        });
    }

    private List<String> profileIds() {
        List<String> l = new ArrayList<>();
        for (JsonElement e : st.getAsJsonArray("profiles")) l.add(e.getAsJsonObject().get("id").getAsString());
        return l;
    }

    private JsonArray dims() { return st.getAsJsonArray("dims"); }

    private int totalMarked() {
        int n = 0;
        for (JsonElement e : dims()) n += e.getAsJsonObject().get("count").getAsInt();
        return n;
    }

    // ------------------------------------------------------------------ pestanas ----
    private void tabSummary() {
        header("ESTADO");
        dyn(1, C_TEXT, () -> "Contenedores con etiqueta de loot: " + totalMarked() + " · perfiles: " + st.getAsJsonArray("profiles").size());
        for (JsonElement e : dims()) {
            JsonObject d = e.getAsJsonObject();
            dyn(1, C_DIM, () -> "  " + str(d, "id") + ": " + str(d, "count"));
        }
        dyn(1, C_TEXT, () -> str(st, "evProfile").isEmpty() ? "Evento activo: ninguno" : "Evento activo: «" + str(st, "evProfile") + "» (" + st.get("evChanged").getAsInt() + " contenedores cambiados)");
        header("TAREA");
        place(false, 16, (x, w) -> List.of(new Bar(x, 0, w, () -> {
            double t = num(job(), "total");
            return t <= 0 ? (running() ? 0.0 : 1.0) : num(job(), "done") / t;
        }, () -> {
            if (!job().has("label")) return "Sin tareas";
            return (running() ? "En curso: " : "Última: ") + str(job(), "label") + (num(job(), "total") > 0 ? " · " + (int) num(job(), "done") + "/" + (int) num(job(), "total") + " chunks" : "");
        })));
        dyn(3, C_DIM, () -> str(job(), "sum"));
        if (running()) btn("Detener la tarea", C_WARN, false, () -> op("stop"));
        header("REINICIO");
        note("Vacía y vuelve a llenar YA todos los contenedores con etiqueta (menos los perfiles protegidos). Normalmente no hace falta: el respawn por tiempo de cada perfil ya los rellena solo cuando un jugador los vacía.");
        if (!running()) btnSure("resetall", "REINICIAR TODOS LOS LOOTS AHORA", C_ACCENT, false, () -> opWith("reset", "profile", ""));
    }

    private static String pt(JsonObject s, String k) {
        if (s == null || !s.has(k)) return "sin marcar";
        JsonArray a = s.getAsJsonArray(k);
        return a.get(0).getAsInt() + "  " + a.get(1).getAsInt() + "  " + a.get(2).getAsInt();
    }

    private void tabWand() {
        header("VARITA DE RELLENO");
        note("Consigue la varita con /give @s lootmaster:rellenador (o en la pestaña de herramientas). Clic izquierdo en un bloque = punto 1, clic derecho = punto 2. Clic en el aire (o agachado + clic) abre este panel.");
        dyn(1, C_TEXT, () -> "Punto 1: " + pt(st.getAsJsonObject("sel"), "a"));
        dyn(1, C_TEXT, () -> "Punto 2: " + pt(st.getAsJsonObject("sel"), "b"));
        dyn(1, C_DIM, () -> {
            JsonObject s = st.getAsJsonObject("sel");
            if (s == null || !s.has("a") || !s.has("b")) return "Zona: marca los dos puntos";
            JsonArray a = s.getAsJsonArray("a"), b = s.getAsJsonArray("b");
            int dx = Math.abs(a.get(0).getAsInt() - b.get(0).getAsInt()) + 1, dz = Math.abs(a.get(2).getAsInt() - b.get(2).getAsInt()) + 1, dy = Math.abs(a.get(1).getAsInt() - b.get(1).getAsInt()) + 1;
            return "Zona: " + dx + " × " + dz + (bool(set(), "wIgnoreY") ? " (toda la altura)" : " × " + dy + " de alto");
        });
        header("QUÉ PONER");
        List<String> ids = new ArrayList<>(profileIds());
        List<String> labels = new ArrayList<>(ids);
        ids.add(0, "__quitar__");
        labels.add(0, "(quitar etiqueta)");
        String cur = str(set(), "wProfile");
        cycMap("Perfil de loot", labels, ids, ids.contains(cur) ? cur : ids.get(Math.min(1, ids.size() - 1)), v -> setSetting("wProfile", v), false);
        tog("Toda la altura (ignorar Y)", () -> bool(set(), "wIgnoreY"), v -> setSetting("wIgnoreY", String.valueOf(v)), false);
        tog("Cambiar también los que ya tienen etiqueta", () -> bool(set(), "wReplace"), v -> setSetting("wReplace", String.valueOf(v)), false);
        tog("…incluso los perfiles protegidos", () -> bool(set(), "wReplaceProt"), v -> setSetting("wReplaceProt", String.valueOf(v)), false);
        tog("Incluir los que tienen objetos (BORRA)", () -> bool(set(), "wNonEmpty"), v -> setSetting("wNonEmpty", String.valueOf(v)), false);
        header("ACCIONES");
        if (running()) {
            dyn(1, C_WARN, () -> "Hay una tarea en curso: " + str(job(), "label"));
            btn("Detener la tarea", C_WARN, false, () -> op("stop"));
        } else {
            btn("CONTAR (no cambia nada)", C_BORDER, false, () -> { JsonObject o = new JsonObject(); o.addProperty("op", "wand"); o.addProperty("dry", true); send(o); });
            btnSure("wapply", "APLICAR A LA ZONA", C_ACCENT, false, () -> { JsonObject o = new JsonObject(); o.addProperty("op", "wand"); o.addProperty("dry", false); send(o); });
            if (st.has("wandUndo") && st.get("wandUndo").getAsInt() > 0) btnSure("wundo", "DESHACER EL ÚLTIMO RELLENO", C_OK, false, () -> op("wundo"));
        }
        dyn(4, C_DIM, () -> str(job(), "sum"));
    }

    private void tabZones() {
        header("ZONAS DE AVENTURA");
        note("Dentro de una zona los jugadores pasan a modo aventura (no rompen ni colocan bloques). Al salir de la zona vuelven a supervivencia (también al morir o desconectarse).  Si reaparecen o se reconectan dentro de una zona, vuelven a aventura al instante. Solo se cambia a quien está en supervivencia (creativo y espectador no se tocan).");
        dyn(1, C_TEXT, () -> "Jugadores en aventura ahora: " + st.get("advCount").getAsInt());
        tog("No afectar a operadores", () -> bool(set(), "zOps"), v -> setSetting("zOps", String.valueOf(v)), false);
        header("NUEVA ZONA");
        note("1) Marca dos puntos con la varita. 2) Pon un nombre. 3) Crear. Si «Toda la altura» (pestaña Varita) está activado, la zona cubre todas las alturas.");
        place(false, 32, (x, w) -> {
            Lbl l = new Lbl(x, 0, () -> "Nombre de la zona", C_DIM);
            EditBox eb = new EditBox(font(), x, 11, w, 18, Component.empty());
            eb.setMaxLength(40);
            eb.setValue(zoneName);
            eb.setResponder(s -> zoneName = s);
            return List.of(l, eb);
        });
        btn("CREAR ZONA CON LA SELECCIÓN DE LA VARITA", C_ACCENT, false, () -> opWith("zone_add", "name", zoneName));
        header("ZONAS");
        JsonArray zs = st.getAsJsonArray("zones");
        if (zs == null || zs.size() == 0) note("Aún no hay zonas.");
        else for (JsonElement e : zs) {
            JsonObject z = e.getAsJsonObject();
            String n = str(z, "name");
            dyn(1, C_TEXT, () -> n + " · " + str(z, "dim") + " · " + (z.get("x2").getAsInt() - z.get("x1").getAsInt() + 1) + "×" + (z.get("z2").getAsInt() - z.get("z1").getAsInt() + 1) + (bool(z, "ignoreY") ? " (toda la altura)" : ""));
            tog("Activa", () -> bool(z, "enabled"), v -> { z.addProperty("enabled", v); JsonObject o = new JsonObject(); o.addProperty("op", "zone_on"); o.addProperty("name", n); o.addProperty("v", v); send(o); }, true);
            btnSure("zdel:" + n, "Borrar", C_BORDER, true, () -> opWith("zone_del", "name", n));
        }
    }

    private void tabProfiles() {
        header("PERFILES DE LOOT");
        note("Cada perfil tiene sus propios ajustes. «Protegido» lo excluye de los eventos, del reinicio total y de los ajustes globales (hospitales, etc.).");
        for (JsonElement e : st.getAsJsonArray("profiles")) {
            JsonObject p = e.getAsJsonObject();
            header(() -> str(p, "name") + (str(p, "name").equals(str(p, "id")) ? "" : " [" + str(p, "id") + "]") + " · " + str(p, "count") + " contenedores");
            tog("Independiente por jugador", () -> bool(p, "per"), v -> profileOp(p, "per", "per", String.valueOf(v)), true);
            tog("Respawn activo", () -> bool(p, "rEn"), v -> profileOp(p, "resp", "rEn", String.valueOf(v)), true);
            textNum("Minutos para volver a llenarse", () -> trim(num(p, "rMin")), s -> { if (!s.isEmpty() && !s.equals(".")) profileOp(p, "min", "rMin", s); }, true);
            tog("Solo si está vacío", () -> bool(p, "rOnly"), v -> profileOp(p, "only", "rOnly", String.valueOf(v)), true);
            tog("Vaciar antes de rellenar", () -> bool(p, "rClear"), v -> profileOp(p, "clear", "rClear", String.valueOf(v)), true);
            tog("Protegido", () -> bool(p, "prot"), v -> profileOp(p, "prot", "prot", String.valueOf(v)), true);
            btnSure("reset:" + str(p, "id"), "Reiniciar sus contenedores ahora", C_BORDER, false, () -> opWith("reset", "profile", str(p, "id")));
        }
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "");
    }

    private void tabGlobal() {
        header("AJUSTES MASIVOS");
        note("Se aplican a TODOS los perfiles que no estén protegidos. Lo que dejes en «sin cambio» no se toca. Antes de cambiar, el mod guarda cómo estaba cada perfil para poder restaurarlo.");
        List<String> l3 = List.of("(sin cambio)", "Sí", "No");
        cycMap("Independiente por jugador", l3, List.of("", "si", "no"), str(set(), "gPer"), v -> setSetting("gPer", v), false);
        cycMap("Respawn", List.of("(sin cambio)", "Activado", "Desactivado"), List.of("", "on", "off"), str(set(), "gResp"), v -> setSetting("gResp", v), false);
        textNum("Minutos entre rellenos (0 = no cambiar)", () -> str(set(), "gMin"), s -> { if (!s.isEmpty() && !s.equals(".")) setSetting("gMin", s); }, false);
        cycMap("Solo si está vacío", l3, List.of("", "si", "no"), str(set(), "gOnly"), v -> setSetting("gOnly", v), true);
        cycMap("Vaciar antes de rellenar", l3, List.of("", "si", "no"), str(set(), "gClear"), v -> setSetting("gClear", v), true);
        btnSure("gapply", "APLICAR A TODOS LOS PERFILES", C_ACCENT, false, () -> op("gapply"));
        if (st.get("origCount").getAsInt() > 0) {
            dyn(1, C_WARN, () -> "Hay " + st.get("origCount").getAsInt() + " perfiles con ajustes guardados para restaurar.");
            btnSure("grestore", "RESTAURAR COMO ESTABAN", C_BORDER, false, () -> op("grestore"));
        }
        note("Ejemplo «reset cada 10 minutos»: Respawn = Activado, Minutos = 10, Solo si está vacío = No. Los contenedores se rellenan cuando alguien los abre pasado ese tiempo (también los que están en zonas descargadas).");
    }

    private void tabFill() {
        header("RELLENO INICIAL (UNA SOLA VEZ)");
        note("Pone un perfil a todos los contenedores VACÍOS que aún no tienen etiqueta. Se hace una vez por dimensión y luego se bloquea, para no tocar las casas de los jugadores. Hazlo antes de abrir el servidor.");
        List<String> ids = profileIds();
        if (!ids.isEmpty()) {
            String cur = str(set(), "fillProfile");
            cycMap("Perfil a poner", ids, ids, ids.contains(cur) ? cur : ids.get(0), v -> setSetting("fillProfile", v), false);
        }
        List<String> dl = new ArrayList<>(List.of("todas"));
        for (JsonElement e : dims()) dl.add(e.getAsJsonObject().get("id").getAsString());
        cycMap("Dimensión", dl, dl, fillDim, v -> fillDim = v, false);
        tog("Aplicar semillas de estructuras", () -> bool(set(), "fillSeeds"), v -> setSetting("fillSeeds", String.valueOf(v)), true);
        tog("Incluir con objetos (BORRA)", () -> bool(set(), "fillNonEmpty"), v -> setSetting("fillNonEmpty", String.valueOf(v)), true);
        textNum("Chunks por tick (más = rápido y con lag)", () -> str(set(), "perTick"), s -> { if (!s.isEmpty() && !s.equals(".")) setSetting("perTick", s); }, false);
        boolean anyPending = false;
        for (JsonElement e : dims()) {
            JsonObject d = e.getAsJsonObject();
            boolean done = !str(d, "done").isEmpty();
            if (!done) anyPending = true;
            dyn(1, done ? C_OK : C_DIM, () -> "  " + str(d, "id") + ": " + (str(d, "done").isEmpty() ? "pendiente" : "hecho el " + str(d, "done")));
        }
        header("ACCIONES");
        if (!running()) {
            btn("ESCANEAR (solo cuenta, no cambia nada)", C_BORDER, false, () -> opWith("scan", "dim", fillDim));
            if (anyPending) btnSure("fill", "RELLENAR AHORA (solo se puede una vez)", C_ACCENT, false, () -> opWith("fill", "dim", fillDim));
        } else {
            dyn(1, C_WARN, () -> "Hay una tarea en curso, mira la pestaña Resumen.");
            btn("Detener la tarea", C_WARN, false, () -> op("stop"));
        }
        dyn(4, C_DIM, () -> str(job(), "sum"));
        for (JsonElement e : dims()) {
            JsonObject d = e.getAsJsonObject();
            if (!str(d, "done").isEmpty()) btnSure("unlock:" + str(d, "id"), "Desbloquear relleno de " + str(d, "id"), C_BORDER, false, () -> opWith("unlock", "dim", str(d, "id")));
        }
    }

    private void tabEvent() {
        header("EVENTO DE LOOT");
        note("Cambia el perfil de TODOS los contenedores con etiqueta (menos los protegidos) y se puede revertir: cada uno vuelve a su perfil anterior.");
        List<String> ids = profileIds();
        if (evPick.isEmpty() && !ids.isEmpty()) evPick = ids.contains("civil") ? "civil" : ids.get(0);
        if (!ids.isEmpty()) cycMap("Perfil del evento", ids, ids, evPick, v -> evPick = v, false);
        tog("Vaciar el contenido al cambiar", () -> bool(set(), "evClear"), v -> setSetting("evClear", String.valueOf(v)), false);
        dyn(1, C_TEXT, () -> str(st, "evProfile").isEmpty() ? "Evento activo: ninguno" : "Evento activo: «" + str(st, "evProfile") + "» · " + st.get("evChanged").getAsInt() + " contenedores cambiados");
        if (!running()) {
            if (!ids.isEmpty()) btnSure("event", "APLICAR EL EVENTO A TODOS", C_ACCENT, false, () -> opWith("event", "profile", evPick));
            if (st.get("evChanged").getAsInt() > 0) btnSure("revert", "REVERTIR AL LOOT ANTERIOR", C_OK, false, () -> op("revert"));
        } else {
            dyn(1, C_WARN, () -> "Hay una tarea en curso, mira la pestaña Resumen.");
            btn("Detener la tarea", C_WARN, false, () -> op("stop"));
        }
        dyn(4, C_DIM, () -> str(job(), "sum"));
    }

    private void tabTypes() {
        header("TIPOS DE CONTENEDOR");
        note("Qué bloques entran en el relleno inicial. Por defecto entran todos los contenedores de vanilla y de otros mods, menos bloques técnicos (hopper, dispenser, hornos, etc.). Los tipos aparecen aquí después de un escaneo.");
        JsonArray ty = st.getAsJsonArray("types");
        if (ty.size() == 0) note("Aún no se ha visto ningún tipo. Ve a «Relleno inicial» y pulsa ESCANEAR.");
        for (JsonElement e : ty) {
            JsonObject t = e.getAsJsonObject();
            String id = str(t, "id");
            String cur = bool(t, "auto") ? "auto" : (bool(t, "on") ? "true" : "false");
            place(false, 20, (x, w) -> List.of(new Cyc(x, 0, w, id, List.of("Automático", "Activado", "Desactivado"), cur.equals("auto") ? "Automático" : cur.equals("true") ? "Activado" : "Desactivado", v -> {
                String val = v.equals("Automático") ? "auto" : v.equals("Activado") ? "true" : "false";
                JsonObject o = new JsonObject();
                o.addProperty("op", "type");
                o.addProperty("id", id);
                o.addProperty("v", val);
                send(o);
            })));
        }
    }

    private void tabHelp() {
        header("CÓMO SE USA");
        note("1) Antes de abrir el server: pestaña «Relleno inicial» → ESCANEAR para ver cuántos contenedores hay, y luego RELLENAR. Solo se puede una vez por dimensión.");
        note("2) Marca como «Protegidos» (pestaña Perfiles) los perfiles bien configurados, como el hospital. No los tocan eventos ni ajustes globales.");
        note("3) Para un día especial: pestaña «Evento» → elige perfil → APLICAR. Para volver: REVERTIR.");
        note("4) El respawn y el loot por jugador son de cada perfil de Survivors Arsenal; aquí puedes cambiarlos en uno (Perfiles) o en todos (Global).");
        note("Comandos: /lootmaster · /lootmaster estado · /lootmaster evento <perfil> · /lootmaster revertir · /lootmaster reset [perfil] · /lootmaster parar");
        note("Los cofres dobles no son compatibles con las etiquetas de Survivors Arsenal y se omiten (el escaneo los cuenta aparte).");
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
        g.drawString(font(), "LOOT MASTER", x0 + 8, y0 + 7, C_ACCENT);
        long age = System.currentTimeMillis() - statusAt;
        if (!status.isEmpty() && age < 6000) {
            int alpha = age > 4500 ? (int) (255 * (6000 - age) / 1500.0) : 255;
            int col = (Math.max(8, alpha) << 24) | (statusColor & 0xFFFFFF);
            String s = font().plainSubstrByWidth(status, W - 130);
            g.drawString(font(), s, x0 + W - 8 - font().width(s), y0 + 7, col);
        } else if (running()) {
            String s = font().plainSubstrByWidth("⏳ " + str(job(), "label"), W - 130);
            g.drawString(font(), s, x0 + W - 8 - font().width(s), y0 + 7, C_WARN);
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
        op("close");
        if (active == this) active = null;
        super.removed();
    }
}
