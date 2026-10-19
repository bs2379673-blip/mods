package com.tunombre.obscureui.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tunombre.obscureui.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.tunombre.obscureui.client.Widgets.*;

/** Panel de administracion de Obscure. */
public class ObscureScreen extends Screen {
    // secciones
    private static final String[] SECS = {"ANOMALÍAS", "CONCIERTO", "SPAWNS", "INFECTADOS", "CRAFTEOS Y LOOT"};
    private static final String[] SEC_HINT = {"cielos y eventos", "show de Miku", "spawn natural", "zonas de plagas", "recetas, items y loot"};

    // anomalias
    private static final String[] AN_ID = {"rojo", "ambar", "violeta", "azul", "verde"};
    private static final String[] AN_NAME = {"ROJA", "ÁMBAR", "VIOLETA", "AZUL", "VERDE"};
    private static final String[] AN_DANGER = {"EXTREMO", "ALTO", "MEDIO", "MEDIO", "BAJO"};
    private static final int[] AN_RGB = {0xFFD42A3A, 0xFFE59A1F, 0xFF8B4DE0, 0xFF2F7FEA, 0xFF2EBB55};
    private static final String[][] MSG = {
            {"Aviso: anomalía detectada", "detectada"}, {"Aviso: en expansión", "expansion"}, {"Aviso: anomalía activa", "activa"},
            {"Aviso extra (después de activa)", "extra"}, {"Aviso: en retirada", "retirada"}, {"Aviso: neutralizada", "neutralizada"},
            {"Mensaje en el chat (al activarse)", "chat"}};
    private static final String[][] CMD = {
            {"Comandos al iniciar (separa con ;)", "cmdInicio"}, {"Comandos al activarse", "cmdActiva"}, {"Comandos al terminar", "cmdFin"}};

    // estado que se conserva al cerrar y reabrir
    private static int sec, tab, color, scope;   // scope: 0 = global, 1..5 = color
    private static String durText = "60";
    private static boolean fixed;
    private static double strobeVal = 1.0;
    private static JsonObject cfg;
    private static boolean hasAnom = true;
    private static String status = "…";
    private static String mob = "minecraft:zombie", num = "10", zoneName = "zona1", zoneRadius = "40", item = "", recipe = "";

    private int px, py, pw, ph, sw, cx, cy, cw;
    private int tickN;
    private String toast = "";
    private long toastAt;
    private final List<EditBox> boxes = new ArrayList<>();

    public ObscureScreen() { super(Component.literal("Obscure UI")); }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 580);
        ph = Math.min(height - 16, 330);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        sw = 112;
        cx = px + sw + 12;
        cy = py + 34;
        cw = pw - sw - 24;
        build();
        if (sec == 0) Net.CH.sendToServer(new Net.Req("anom.get", ""));
    }

    private void send(String cmd) {
        Net.CH.sendToServer(new Net.Cmd(cmd));
        toast = "/" + cmd;
        toastAt = System.currentTimeMillis();
    }

    private Btn btn(int x, int y, int w, int h, String label, Runnable r) { return addRenderableWidget(new Btn(x, y, w, h, label, r)); }

    private EditBox box(int x, int y, int w, String value, int max, java.util.function.Consumer<String> onChange) {
        EditBox e = new EditBox(font, x, y, w, 16, Component.empty());
        e.setMaxLength(max);
        e.setBordered(false);
        e.setValue(value);
        e.setResponder(onChange);
        boxes.add(e);
        return addRenderableWidget(e);
    }

    private void label(String t, int x, int y) { labels.add(new Object[]{t, x, y}); }

    private final List<Object[]> labels = new ArrayList<>();

    private void rebuild() {
        clearWidgets();
        boxes.clear();
        labels.clear();
        build();
    }

    private void build() {
        // barra lateral
        int y = py + 44;
        for (int i = 0; i < SECS.length; i++) {
            final int idx = i;
            Btn b = btn(px + 8, y, sw - 16, 24, SECS[i], () -> { sec = idx; rebuild(); if (idx == 0) Net.CH.sendToServer(new Net.Req("anom.get", "")); }).flat();
            b.selected = sec == i;
            y += 28;
        }
        switch (sec) {
            case 0 -> buildAnomalias();
            case 1 -> buildConcierto();
            case 2 -> buildSpawns();
            case 3 -> buildInfectados();
            default -> buildCrafteos();
        }
    }

    // ------------------------------------------------------------------ ANOMALIAS
    private String scopeId() { return scope == 0 ? "global" : AN_ID[scope - 1]; }

    private String cfgGet(String key) {
        if (cfg == null) return "";
        JsonObject o = scope == 0 ? cfg.getAsJsonObject("global") : (cfg.getAsJsonObject("tipos") == null ? null : cfg.getAsJsonObject("tipos").getAsJsonObject(scopeId()));
        if (o == null || !o.has(key)) return "";
        return o.get(key).getAsString();
    }

    private void cfgSet(String key, String v) {
        if (cfg == null) return;
        JsonObject o;
        if (scope == 0) {
            o = cfg.getAsJsonObject("global");
            if (o == null) { o = new JsonObject(); cfg.add("global", o); }
        } else {
            JsonObject t = cfg.getAsJsonObject("tipos");
            if (t == null) { t = new JsonObject(); cfg.add("tipos", t); }
            o = t.getAsJsonObject(scopeId());
            if (o == null) { o = new JsonObject(); t.add(scopeId(), o); }
        }
        o.addProperty(key, v);
    }

    private void buildAnomalias() {
        // pestañas
        btn(cx, cy, 90, 18, "LANZAR", () -> { tab = 0; if (scope == 0) scope = color + 1; rebuild(); }).selected = tab == 0;
        btn(cx + 94, cy, 90, 18, "MENSAJES", () -> { tab = 1; rebuild(); }).selected = tab == 1;
        int yy = cy + 24;
        // colores
        int chipW = (cw - (tab == 1 ? 60 : 0)) / 5 - 3;
        int x = cx;
        if (tab == 1) {
            Btn g = btn(x, yy, 54, 18, "GLOBAL", () -> { scope = 0; rebuild(); });
            g.selected = scope == 0;
            x += 58;
            chipW = (cw - 58) / 5 - 3;
        }
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            Btn b = btn(x, yy, chipW, 18, AN_NAME[i], () -> { color = idx; if (tab == 1) scope = idx + 1; rebuild(); }).color(AN_RGB[i]);
            b.selected = tab == 1 ? scope == i + 1 : color == i;
            x += chipW + 3;
        }
        yy += 28;
        if (tab == 0) {
            label("Duración (segundos, mínimo 20)", cx, yy);
            EditBox d = box(cx + 4, yy + 12, 54, durText, 5, v -> durText = v);
            d.setEditable(!fixed);
            Btn f = btn(cx + 64, yy + 10, 120, 18, fixed ? "FIJA (hasta apagar): SÍ" : "FIJA (hasta apagar): NO", () -> { fixed = !fixed; rebuild(); });
            f.selected = fixed;
            yy += 36;
            Sld s = addRenderableWidget(new Sld(cx, yy, Math.min(cw, 260), 18, "Destellos", strobeVal, 1.0, v -> Math.round(v * 100) + "%", v -> {
                strobeVal = v;
                send("anomalia destellos " + String.format(Locale.ROOT, "%.2f", v));
            }));
            s.color = AN_RGB[color];
            yy += 30;
            label("Peligro: " + AN_DANGER[color] + "   ·   " + (fixed ? "queda prendida hasta que la apagues" : "dura " + durText + " s y vuelve el cielo normal"), cx, yy);
            yy += 16;
            int bw = Math.min(180, (cw - 8) / 2);
            btn(cx, yy, bw, 26, "LANZAR ANOMALÍA " + AN_NAME[color], () -> send("anomalia " + AN_ID[color] + " " + (fixed ? "fijo" : (durText.isBlank() ? "60" : durText)))).color(AN_RGB[color]);
            btn(cx + bw + 8, yy, bw, 26, "APAGAR", () -> send("anomalia fin")).color(0xFF6B6B6B);
            if (!hasAnom) label("El mod anomalias no está instalado en el servidor", cx, yy + 34);
        } else {
            // mensajes: dos columnas
            boolean glob = scope == 0;
            int colW = (cw - 10) / 2;
            int rows = 5;
            List<String[]> fields = new ArrayList<>();
            for (String[] m : MSG) fields.add(m);
            if (!glob) for (String[] m : CMD) fields.add(m);
            for (int i = 0; i < fields.size(); i++) {
                int col = i / rows, row = i % rows;
                int fx = cx + col * (colW + 10), fy = yy + row * 28;
                final String key = fields.get(i)[1];
                label(fields.get(i)[0], fx, fy);
                box(fx + 4, fy + 11, colW - 8, cfgGet(key), 240, v -> cfgSet(key, v));
            }
            int by = yy + rows * 28 + 2;
            btn(cx, by, 110, 20, "GUARDAR", this::saveCfg).color(0xFF2EBB55);
            btn(cx + 116, by, 110, 20, "RECARGAR", () -> Net.CH.sendToServer(new Net.Req("anom.get", ""))).color(0xFF6B6B6B);
            label("Formato: Título|Subtítulo · {COLOR} y {PELIGRO} se reemplazan · en un color, vacío = usa el global", cx, by + 24);
        }
    }

    private void saveCfg() {
        if (cfg == null) return;
        Net.CH.sendToServer(new Net.Req("anom.set", cfg.toString()));
        toast = "Guardando…";
        toastAt = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ CONCIERTO
    private static final String[][] SONGS = {
            {"Intro", "intro"}, {"Pre-Mesmerizer", "premesmerizer"}, {"Mesmerizer", "mesmerizer"}, {"Minna Miku Miku", "minnamikumiku"},
            {"Konton Boogie", "kontonboogie"}, {"Looping the Rooms", "loopingtherooms"}, {"Magical Cure", "magicalcure"}, {"Melt", "melt"},
            {"Static", "static"}, {"Anamanaguchi", "anamanaguchi"}, {"Intermedio", "intermedio"}, {"Pre-Triple Baka", "pretriplebaka"},
            {"Triple Baka", "triplebaka"}, {"Despedida", "despedida"}};

    private void buildConcierto() {
        label("Canciones (toca para iniciar)", cx, cy);
        int cols = Math.max(2, Math.min(4, cw / 110));
        int bw = (cw - (cols - 1) * 4) / cols;
        int yy = cy + 12;
        for (int i = 0; i < SONGS.length; i++) {
            final String id = SONGS[i][1];
            btn(cx + (i % cols) * (bw + 4), yy + (i / cols) * 22, bw, 19, SONGS[i][0], () -> send("concierto iniciar " + id)).color(0xFFE0457B);
        }
        yy += ((SONGS.length + cols - 1) / cols) * 22 + 6;
        int third = (cw - 8) / 3;
        btn(cx, yy, third, 20, "DETENER", () -> send("concierto detener")).color(0xFF6B6B6B);
        btn(cx + third + 4, yy, third, 20, "FINAL (prueba)", () -> send("concierto final")).color(0xFFE0457B);
        btn(cx + 2 * (third + 4), yy, third, 20, "ESCENARIO AQUÍ", () -> send("concierto escenario aqui")).color(0xFF2F7FEA);
        yy += 26;
        int sl = Math.min((cw - 8) / 2, 200);
        addRenderableWidget(new Sld(cx, yy, sl, 18, "Intensidad", 0.5, 2.0, v -> String.format(Locale.ROOT, "%.2f", v), v -> send("concierto intensidad " + String.format(Locale.ROOT, "%.2f", v)))).color = 0xFFE0457B;
        addRenderableWidget(new Sld(cx + sl + 8, yy, sl, 18, "Estrobo", 1.0, 1.0, v -> Math.round(v * 100) + "%", v -> send("concierto estrobo " + String.format(Locale.ROOT, "%.2f", v)))).color = 0xFFE0457B;
        yy += 24;
        label("Hologramas de Miku", cx, yy);
        btn(cx + 100, yy - 3, 50, 16, "ON", () -> send("concierto hologramas on")).color(0xFF2EBB55);
        btn(cx + 154, yy - 3, 50, 16, "OFF", () -> send("concierto hologramas off")).color(0xFF6B6B6B);
        yy += 16;
        label("Efecto en los jugadores", cx, yy);
        int jx = cx + 100;
        for (String m : new String[]{"volar", "brillo", "confeti", "nada"}) {
            btn(jx, yy - 3, 50, 16, m.toUpperCase(Locale.ROOT), () -> send("concierto jugadores " + m)).color(0xFF8B4DE0);
            jx += 54;
        }
        yy += 16;
        btn(cx, yy, 100, 16, "VER MARCAS", () -> send("concierto vermarcas")).color(0xFF2F7FEA);
        btn(cx + 104, yy, 110, 16, "QUITAR MARCAS", () -> send("concierto quitarmarcas")).color(0xFF6B6B6B);
    }

    // ------------------------------------------------------------------ SPAWNS
    private void buildSpawns() {
        int yy = cy;
        label("Mob (ej. minecraft:zombie)", cx, yy);
        box(cx + 4, yy + 11, Math.min(cw - 8, 240), mob, 80, v -> mob = v);
        label("Valor", cx + Math.min(cw, 252), yy);
        box(cx + Math.min(cw, 252) + 4, yy + 11, 50, num, 6, v -> num = v);
        yy += 34;
        int bw = (cw - 8) / 3;
        btn(cx, yy, bw, 20, "ACTIVAR SPAWN", () -> send("spawns activar " + mob)).color(0xFF2EBB55);
        btn(cx + bw + 4, yy, bw, 20, "DESACTIVAR", () -> send("spawns desactivar " + mob)).color(0xFFD42A3A);
        btn(cx + 2 * (bw + 4), yy, bw, 20, "INFO", () -> send("spawns info " + mob)).color(0xFF2F7FEA);
        yy += 24;
        btn(cx, yy, bw, 20, "MÁXIMO = valor", () -> send("spawns max " + mob + " " + num)).color(0xFFE59A1F);
        btn(cx + bw + 4, yy, bw, 20, "PROBABILIDAD % = valor", () -> send("spawns prob " + mob + " " + num)).color(0xFFE59A1F);
        btn(cx + 2 * (bw + 4), yy, bw, 20, "RESETEAR REGLA", () -> send("spawns reset " + mob)).color(0xFF6B6B6B);
        yy += 34;
        label("General", cx, yy);
        yy += 12;
        btn(cx, yy, bw, 20, "ABRIR PANEL COMPLETO", () -> { onClose(); send("spawns gui"); }).color(0xFF8B4DE0);
        btn(cx + bw + 4, yy, bw, 20, "LISTA DE REGLAS", () -> send("spawns lista")).color(0xFF2F7FEA);
        btn(cx + 2 * (bw + 4), yy, bw, 20, "RECARGAR", () -> send("spawns recargar")).color(0xFF6B6B6B);
        yy += 24;
        btn(cx, yy, (cw - 4) / 2, 20, "MONSTRUOS BLOQUEADOS POR DEFECTO", () -> send("spawns defecto animales")).color(0xFFD42A3A);
        btn(cx + (cw - 4) / 2 + 4, yy, (cw - 4) / 2, 20, "TODO LIBRE POR DEFECTO", () -> send("spawns defecto libre")).color(0xFF2EBB55);
    }

    // ------------------------------------------------------------------ INFECTADOS
    private void buildInfectados() {
        int yy = cy;
        label("Nombre de la zona", cx, yy);
        box(cx + 4, yy + 11, 110, zoneName, 24, v -> zoneName = v);
        label("Radio", cx + 124, yy);
        box(cx + 128, yy + 11, 50, zoneRadius, 4, v -> zoneRadius = v);
        label("Máx.", cx + 188, yy);
        box(cx + 192, yy + 11, 40, num, 4, v -> num = v);
        yy += 34;
        int bw = (cw - 8) / 3;
        btn(cx, yy, bw, 20, "CREAR ZONA AQUÍ", () -> send("infectados zona crear " + zoneName + " " + zoneRadius + " " + num)).color(0xFF2EBB55);
        btn(cx + bw + 4, yy, bw, 20, "BORRAR ZONA", () -> send("infectados zona borrar " + zoneName)).color(0xFFD42A3A);
        btn(cx + 2 * (bw + 4), yy, bw, 20, "VER ZONAS", () -> send("infectados zona lista")).color(0xFF2F7FEA);
        yy += 24;
        btn(cx, yy, bw, 20, "MÁXIMO = Máx.", () -> send("infectados zona max " + zoneName + " " + num)).color(0xFFE59A1F);
        btn(cx + bw + 4, yy, bw, 20, "ACTIVA: SÍ", () -> send("infectados zona activa " + zoneName + " true")).color(0xFF2EBB55);
        btn(cx + 2 * (bw + 4), yy, bw, 20, "ACTIVA: NO", () -> send("infectados zona activa " + zoneName + " false")).color(0xFF6B6B6B);
        yy += 24;
        btn(cx, yy, bw, 20, "MOTOSIERRA: SÍ", () -> send("infectados zona motosierra " + zoneName + " true")).color(0xFFE59A1F);
        btn(cx + bw + 4, yy, bw, 20, "MOTOSIERRA: NO", () -> send("infectados zona motosierra " + zoneName + " false")).color(0xFF6B6B6B);
        yy += 32;
        label("La zona se crea donde estás parado. El radio va de 4 a 512 y el máximo de 1 a 100.", cx, yy);
    }

    // ------------------------------------------------------------------ CRAFTEOS Y LOOT
    private void buildCrafteos() {
        int yy = cy;
        label("Ítem (ej. minecraft:diamond, modid:* = mod entero, mano = lo que llevas)", cx, yy);
        box(cx + 4, yy + 11, Math.min(cw - 8, 300), item, 100, v -> item = v);
        yy += 34;
        int bw = (cw - 8) / 3;
        btn(cx, yy, bw, 20, "PROHIBIR ÍTEM", () -> send("crafteos prohibir " + (item.isBlank() ? "mano" : item))).color(0xFFD42A3A);
        btn(cx + bw + 4, yy, bw, 20, "PERMITIR ÍTEM", () -> send("crafteos permitir " + item)).color(0xFF2EBB55);
        btn(cx + 2 * (bw + 4), yy, bw, 20, "VER PROHIBIDOS", () -> send("crafteos prohibidos")).color(0xFF2F7FEA);
        yy += 34;
        label("Receta (clave que muestra el panel de crafteos)", cx, yy);
        box(cx + 4, yy + 11, Math.min(cw - 8, 300), recipe, 100, v -> recipe = v);
        yy += 34;
        btn(cx, yy, bw, 20, "DESACTIVAR RECETA", () -> send("crafteos desactivar " + recipe)).color(0xFFD42A3A);
        btn(cx + bw + 4, yy, bw, 20, "ACTIVAR RECETA", () -> send("crafteos activar " + recipe)).color(0xFF2EBB55);
        btn(cx + 2 * (bw + 4), yy, bw, 20, "VER DESACTIVADAS", () -> send("crafteos lista")).color(0xFF2F7FEA);
        yy += 28;
        btn(cx, yy, bw, 20, "PANEL DE CRAFTEOS", () -> { onClose(); send("crafteos gui"); }).color(0xFF8B4DE0);
        btn(cx + bw + 4, yy, bw, 20, "PANEL DE MODELOS", () -> { onClose(); send("modelos gui"); }).color(0xFF8B4DE0);
        btn(cx + 2 * (bw + 4), yy, bw, 20, "RECARGAR", () -> send("crafteos recargar")).color(0xFF6B6B6B);
        yy += 28;
        label("Un ítem prohibido no sale en cofres ni drops, no se craftea y se borra de los inventarios.", cx, yy);
    }

    // ------------------------------------------------------------------ datos del servidor
    void onData(String key, String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            switch (key) {
                case "anom.get" -> {
                    hasAnom = !o.has("has") || o.get("has").getAsBoolean();
                    if (hasAnom && o.has("cfg")) cfg = o.getAsJsonObject("cfg");
                    if (hasAnom && o.has("status")) setStatus(o.getAsJsonObject("status"));
                    if (sec == 0) rebuild();
                }
                case "anom.status" -> {
                    hasAnom = !o.has("has") || o.get("has").getAsBoolean();
                    if (hasAnom) setStatus(o);
                }
                case "anom.saved" -> {
                    JsonElement e = o.get("error");
                    toast = e == null || e.getAsString().isEmpty() ? "Guardado" : "Error: " + e.getAsString();
                    toastAt = System.currentTimeMillis();
                }
                default -> { }
            }
        } catch (Exception ignored) { }
    }

    private void setStatus(JsonObject s) {
        String a = s.has("activa") ? s.get("activa").getAsString() : "";
        if (a.isEmpty()) { status = "Sin anomalía activa"; return; }
        float left = s.has("resta") ? s.get("resta").getAsFloat() : -1F;
        status = "ACTIVA: " + a.toUpperCase(Locale.ROOT) + (left < 0 ? "  (fija)" : String.format(Locale.ROOT, "  (quedan %.0f s)", left));
    }

    @Override
    public void tick() {
        super.tick();
        if (sec == 0 && ++tickN % 20 == 0) Net.CH.sendToServer(new Net.Req("anom.status", ""));
    }

    // ------------------------------------------------------------------ dibujo
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(px, py, px + pw, py + ph, PANEL);
        g.fill(px, py, px + sw, py + ph, SIDE);
        g.fill(px + sw, py, px + sw + 1, py + ph, LINE);
        g.fill(px, py, px + pw, py + 2, ACCENT);
        g.drawString(font, "O B S C U R E", px + 10, py + 9, 0xFFE02A40, true);
        g.drawString(font, "panel de administración", px + 10, py + 21, MUTED, false);
        g.drawString(font, SECS[sec], cx, py + 10, TEXT, true);
        g.drawString(font, SEC_HINT[sec], cx + font.width(SECS[sec]) + 8, py + 10, MUTED, false);
        g.fill(cx, py + 24, px + pw - 12, py + 25, LINE);
        if (sec == 0) {
            int c = status.startsWith("ACTIVA") ? AN_RGB[Math.max(0, Math.min(4, indexOfActive()))] : MUTED;
            g.drawString(font, status, px + pw - 12 - font.width(status), py + 10, c, true);
        }
        // fondo de los campos
        for (EditBox b : boxes) {
            g.fill(b.getX() - 4, b.getY() - 2, b.getX() + b.getWidth() + 2, b.getY() + b.getHeight() - 1, FIELD);
            g.fill(b.getX() - 4, b.getY() + b.getHeight() - 2, b.getX() + b.getWidth() + 2, b.getY() + b.getHeight() - 1, b.isFocused() ? ACCENT : LINE);
        }
        for (Object[] l : labels) g.drawString(font, (String) l[0], (int) l[1], (int) l[2], MUTED, false);
        super.render(g, mx, my, pt);
        // pie
        long age = System.currentTimeMillis() - toastAt;
        if (!toast.isEmpty() && age < 4000) g.drawString(font, "› " + toast, cx, py + ph - 12, mix(0xFF2EBB55, PANEL, Math.min(1F, age / 4000F)), false);
        else g.drawString(font, "Tecla O o /obscure · solo operadores", cx, py + ph - 12, 0xFF5E4A4D, false);
    }

    private int indexOfActive() {
        for (int i = 0; i < AN_ID.length; i++) if (status.toLowerCase(Locale.ROOT).contains(AN_ID[i])) return i;
        return 0;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == ObscureClient.OPEN.getKey().getValue() && getFocused() == null && !isTyping()) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    private boolean isTyping() {
        for (EditBox b : boxes) if (b.isFocused()) return true;
        return false;
    }
}
