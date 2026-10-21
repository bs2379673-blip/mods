package com.tunombre.obscureui.client;

import com.cotii.ui.Draw;
import com.cotii.ui.UiPanel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tunombre.obscureui.Net;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.fml.loading.FMLPaths;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Panel de administracion de Obscure con CotiiUI. Se redibuja entero cada cuadro; el estado vive en variables estaticas.
 * Lo que sabe del servidor (que mods hay, catalogos, jugadores, rendimiento, valores del filtro de color) llega por red:
 * "cat" cada pocos segundos y "live" cada ~1,5 s.
 */
public final class ObscurePanel extends UiPanel {
    private static final String[] AN_ID = {"rojo", "ambar", "violeta", "azul", "verde"};
    private static final String[] AN_NAME = {"ROJA", "ÁMBAR", "VIOLETA", "AZUL", "VERDE"};
    private static final String[] AN_SHORT = {"ROJA", "ÁMBAR", "VIOL.", "AZUL", "VERDE"};
    private static final String[] AN_DANGER = {"EXTREMO", "ALTO", "MEDIO", "MEDIO", "BAJO"};
    private static final int[] AN_RGB = {0xFFD42A3A, 0xFFE59A1F, 0xFF8B4DE0, 0xFF2F7FEA, 0xFF2EBB55};
    private static final String[][] MSG = {
            {"Aviso: anomalía detectada", "detectada"}, {"Aviso: en expansión", "expansion"}, {"Aviso: anomalía activa", "activa"},
            {"Aviso extra (después de activa)", "extra"}, {"Aviso: en retirada", "retirada"}, {"Aviso: neutralizada", "neutralizada"},
            {"Mensaje en el chat (al activarse)", "chat"}};
    private static final String[][] CMD = {
            {"Comandos al iniciar (separa con ;)", "cmdInicio"}, {"Comandos al activarse", "cmdActiva"}, {"Comandos al terminar", "cmdFin"}};
    private static final String[][] SONGS = {
            {"Intro", "intro"}, {"Pre-Mesmerizer", "premesmerizer"}, {"Mesmerizer", "mesmerizer"}, {"Minna Miku Miku", "minnamikumiku"},
            {"Konton Boogie", "kontonboogie"}, {"Looping the Rooms", "loopingtherooms"}, {"Magical Cure", "magicalcure"}, {"Melt", "melt"},
            {"Static", "static"}, {"Anamanaguchi", "anamanaguchi"}, {"Intermedio", "intermedio"}, {"Pre-Triple Baka", "pretriplebaka"},
            {"Triple Baka", "triplebaka"}, {"Despedida", "despedida"}};
    // ajustes del filtro de color: comando, etiqueta, unidad, minimo, maximo, paso, valor al reiniciar
    private static final Object[][] VFX = {
            {"fuerza", "Fuerza", "", 0.0, 1.0, 0.01, 1.0, "strength"},
            {"saturacion", "Saturación", "", 0.0, 2.5, 0.05, 1.0, "saturation"},
            {"gamma", "Gamma (sombras)", "", 0.3, 2.5, 0.05, 1.0, "gamma"},
            {"tonos", "Tonos divididos", "", 0.0, 1.0, 0.01, 0.0, "toneAmt"},
            {"contraste", "Contraste", "", 0.0, 3.0, 0.05, 0.5, "contrast"},
            {"brillo", "Brillo", "", 0.0, 2.0, 0.05, 0.95, "brightness"},
            {"desaturar", "Desaturar", "", 0.0, 1.0, 0.01, 0.2, "desaturate"},
            {"vineta", "Viñeta", "", 0.0, 2.0, 0.05, 0.0, "vignette"},
            {"cromatica", "Cromática", "", 0.0, 0.2, 0.005, 0.0, "chromatic"}};
    private static final String[] GM = {"survival", "creative", "adventure", "spectator"};
    private static final String[] GM_SHORT = {"SURV.", "CREA.", "AVEN.", "ESPEC."};

    private static final int OK = 0xFF2EBB55, BAD = 0xFFD42A3A, GREY = 0xFF6B6B6B, BLUE = 0xFF2F7FEA, AMBER = 0xFFE59A1F, PURP = 0xFF8B4DE0, PINK = 0xFFE0457B, TEAL = 0xFF2AA6C8;

    private static boolean themed;

    // ---- datos del servidor ----
    private static JsonObject cat = new JsonObject();
    private static JsonObject live = new JsonObject();
    private static final List<Double> MS_HIST = new ArrayList<>();
    private static long catAt, liveAt, catSoon;

    // ---- estado del panel (se conserva al cerrar y reabrir) ----
    private static String target = "@a";                // "@a" = todos, o el nombre de un jugador
    private static int color;
    private static int scope;                           // mensajes: 0 = global, 1..5 = color
    private static String durText = "60";
    private static boolean fixed;
    private static double strobeVal = 1.0, concInt = 1.0, concStrobe = 1.0;
    private static JsonObject cfg;
    private static boolean hasAnom = true;
    private static String anomName = "";
    private static float anomLeft = -1F, anomTotal = 1F;
    private static String savedMsg = "";
    private static String song = SONGS[0][0];
    private static String mob = "minecraft:zombie", num = "10", zoneName = "zona1", zoneRadius = "40", item = "", recipe = "";
    private static String tpl = "", modo = "", cosId = "", qteName = "", console = "", shortName = "";
    // slider pendiente de enviar (se manda cuando se deja de mover)
    private static final Map<String, Long> PENDING = new HashMap<>();
    private static final Map<String, Double> LOCAL = new HashMap<>();
    private static long strobeAt, concIntAt, concStrobeAt;

    static {
        SECTION_COLORS.put("EN VIVO", 0xFF2EBB55);
        SECTION_COLORS.put("ANOMALÍAS", 0xFFD42A3A);
        SECTION_COLORS.put("MENSAJES", 0xFFE59A1F);
        SECTION_COLORS.put("VISIÓN", 0xFF2AA6C8);
        SECTION_COLORS.put("COSMÉTICOS", 0xFFB45BE0);
        SECTION_COLORS.put("QUICK TIMES", 0xFFFF7A1A);
        SECTION_COLORS.put("CONCIERTO", 0xFFE0457B);
        SECTION_COLORS.put("SPAWNS", 0xFF2F7FEA);
        SECTION_COLORS.put("INFECTADOS", 0xFF7FBF3F);
        SECTION_COLORS.put("CRAFTEOS", 0xFF8B4DE0);
        SECTION_COLORS.put("ATAJOS", 0xFFE5C21F);
        OPEN_BY_DEFAULT.add("EN VIVO");
        OPEN_BY_DEFAULT.add("ANOMALÍAS");
    }

    public ObscurePanel() {
        if (!themed) {
            themed = true;
            com.cotii.ui.CotiiUI.applyTheme(com.cotii.ui.Theme.RED);
            setStateFile(FMLPaths.CONFIGDIR.get().resolve("obscureui_panel.json"));
        }
        Shortcuts.load();
        Net.CH.sendToServer(new Net.Req("anom.get", ""));
        Net.CH.sendToServer(new Net.Req("cat", ""));
        Net.CH.sendToServer(new Net.Req("live", ""));
        catAt = liveAt = System.currentTimeMillis();
    }

    @Override
    protected String title() { return "OBSCURE · administración"; }

    // ------------------------------------------------------------------ comandos ----
    private void send(String cmd) {
        Net.CH.sendToServer(new Net.Cmd(cmd));
        setStatus("/" + cmd, COLOR_OK);
    }

    /** uno o varios comandos separados por ; */
    private void sendAll(String text) {
        int n = 0;
        for (String c : text.split(";")) {
            String t = c.trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("/")) t = t.substring(1);
            Net.CH.sendToServer(new Net.Cmd(t));
            n++;
        }
        if (n > 0) setStatus(n == 1 ? "/" + text.trim().replaceFirst("^/", "") : n + " comandos enviados", COLOR_OK);
        catSoon = System.currentTimeMillis() + 700;
    }

    private static String f2(double v) { return String.format(Locale.ROOT, "%.2f", v); }

    private static String f3(double v) { return String.format(Locale.ROOT, "%.3f", v); }

    // ------------------------------------------------------------------ lectura de datos ----
    private static boolean mod(String id) {
        JsonObject m = cat.has("mods") ? cat.getAsJsonObject("mods") : null;
        return m != null && m.has(id) && m.get(id).getAsBoolean();
    }

    private static JsonArray arr(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static String str(JsonObject o, String key, String def) {
        return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }

    private static double dbl(JsonObject o, String key, double def) {
        try { return o != null && o.has(key) ? o.get(key).getAsDouble() : def; } catch (RuntimeException e) { return def; }
    }

    private static List<JsonObject> players() {
        List<JsonObject> l = new ArrayList<>();
        for (JsonElement e : arr(live, "players")) l.add(e.getAsJsonObject());
        return l;
    }

    private static JsonObject playerData(String name) {
        for (JsonObject p : players()) if (str(p, "n", "").equals(name)) return p;
        return null;
    }

    private static boolean all() { return target.equals("@a"); }

    // ------------------------------------------------------------------ util de filas ----
    private int rowN(int y, String k, String[] texts, int[] colors, Runnable[] acts, int selected) {
        int n = texts.length;
        int w = (INNER - (n - 1) * 3) / n;
        for (int i = 0; i < n; i++) {
            int x = LEFT + i * (w + 3);
            int ww = i == n - 1 ? LEFT + INNER - x : w;
            button(k + "_" + i, x, y, ww, texts[i], i == selected, acts[i]).color(colors[i]);
        }
        return y + ROW;
    }

    private int row2(int y, String k, String a, int ca, Runnable ra, String b, int cb, Runnable rb) {
        button(k + "_a", LEFT, y, HALF, a, false, ra).color(ca);
        button(k + "_b", RIGHT, y, HALF, b, false, rb).color(cb);
        return y + ROW;
    }

    private int row1(int y, String k, String a, int ca, Runnable ra) {
        button(k, LEFT, y, INNER, a, false, ra).color(ca);
        return y + ROW;
    }

    private int field(int y, String k, String label, Supplier<String> g, Consumer<String> s, java.util.function.IntPredicate filter) {
        textField(k, LEFT, y, INNER, label + ": ", g, s, filter);
        return y + ROW;
    }

    private static String cut(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "…"; }

    // ------------------------------------------------------------------ anomalias: configuracion ----
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

    // ------------------------------------------------------------------ layout ----
    @Override
    protected int layout(int y) {
        y = secLive(y);
        y = secAnom(y);
        y = secMsgs(y);
        if (mod("visionfx")) y = secVision(y);
        if (mod("cosmeticos")) y = secCosmetics(y);
        if (mod("quicktime_events")) y = secQte(y);
        y = secConcert(y);
        y = secSpawns(y);
        if (mod("infectados")) y = secInfected(y);
        y = secCrafts(y);
        y = secShortcuts(y);
        return y;
    }

    // ---------------- EN VIVO ----------------
    private int secLive(int y) {
        header(y, "EN VIVO (servidor y jugadores)");
        y += 16;
        List<JsonObject> ps = players();
        double ms = dbl(live, "ms", 0);
        double tps = Math.min(20.0, 1000.0 / Math.max(50.0, ms));
        label(LEFT, y, ps.size() + " conectado" + (ps.size() == 1 ? "" : "s") + " · " + String.format(Locale.ROOT, "%.1f", ms) + " ms/tick · " + String.format(Locale.ROOT, "%.1f", tps) + " TPS", ms > 50 ? COLOR_ERROR : ms > 40 ? AMBER : COLOR_OK);
        y += 11;
        // historial del tiempo por tick (barras): verde hasta 40 ms, ambar hasta 50, rojo mas alla
        final List<Double> hist = MS_HIST;
        widgets.add(new Canvas("live_graph", LEFT, y, INNER, 22, (g, font, c, hov) -> {
            g.fill(c.x, c.y, c.x + c.w, c.y + c.h, 0xFF000000);
            g.fill(c.x + 1, c.y + 1, c.x + c.w - 1, c.y + c.h - 1, COLOR_FIELD);
            int n = hist.size();
            int bw = Math.max(1, (c.w - 2) / 60);
            for (int i = 0; i < n; i++) {
                double v = hist.get(i);
                int h = (int) Math.min(c.h - 2, Math.round(v / 60.0 * (c.h - 2)));
                int col = v > 50 ? 0xFFD42A3A : v > 40 ? 0xFFE59A1F : 0xFF2EBB55;
                int x1 = c.x + c.w - 1 - (n - i) * bw;
                if (x1 < c.x + 1) continue;
                g.fill(x1, c.y + c.h - 1 - h, x1 + bw, c.y + c.h - 1, col);
            }
            int lim = c.y + c.h - 1 - (int) Math.round(50.0 / 60.0 * (c.h - 2));
            g.fill(c.x + 1, lim, c.x + c.w - 1, lim + 1, 0x60FFFFFF);
        }));
        y += 26;

        // jugadores: TODOS + uno por boton (de a dos por fila); el elegido es el objetivo de las acciones de abajo
        button("pl_all", LEFT, y, HALF, "TODOS (@a)", all(), () -> target = "@a").color(OK);
        int col = 1;
        for (JsonObject p : ps) {
            String n = str(p, "n", "?");
            int x = col == 0 ? LEFT : RIGHT;
            Widget w = button("pl_" + n, x, y, HALF, cut(n, 14), n.equals(target), () -> target = n);
            w.tip(n + " · " + str(p, "d", "?") + " " + (int) dbl(p, "x", 0) + " " + (int) dbl(p, "y", 0) + " " + (int) dbl(p, "z", 0)
                    + " · " + (int) dbl(p, "hp", 0) + " vida · " + (int) dbl(p, "ping", 0) + " ms · " + str(p, "gm", ""));
            if (col == 1) { col = 0; y += ROW; } else col = 1;
        }
        if (col == 0) y += ROW;
        if (!all() && playerData(target) == null && !ps.isEmpty()) target = "@a";
        if (!all()) {
            JsonObject p = playerData(target);
            if (p != null) {
                label(LEFT, y, str(p, "d", "?") + " " + (int) dbl(p, "x", 0) + " " + (int) dbl(p, "y", 0) + " " + (int) dbl(p, "z", 0) + " · ♥" + (int) dbl(p, "hp", 0) + " · " + (int) dbl(p, "ping", 0) + " ms", COLOR_DIM);
                y += 11;
            }
        }
        String t = target;
        y = rowN(y, "pl_a", new String[]{"IR A ÉL", "TRAERLO", "CURAR", "LIMPIAR"}, new int[]{BLUE, BLUE, OK, GREY},
                new Runnable[]{() -> send("tp @s " + t), () -> send("tp " + t + " @s"),
                        () -> sendAll("effect give " + t + " minecraft:instant_health 1 10 true; effect give " + t + " minecraft:saturation 1 10 true"),
                        () -> send("effect clear " + t)}, -1);
        y = rowN(y, "pl_gm", GM_SHORT, new int[]{TEAL, TEAL, TEAL, TEAL},
                new Runnable[]{() -> send("gamemode survival " + t), () -> send("gamemode creative " + t), () -> send("gamemode adventure " + t), () -> send("gamemode spectator " + t)},
                !all() && playerData(target) != null ? java.util.Arrays.asList(GM).indexOf(str(playerData(target), "gm", "")) : -1);
        label(LEFT, y, "Objetivo de VISIÓN, COSMÉTICOS y QUICK TIMES: " + (all() ? "todos" : target), COLOR_DIM);
        y += 14;
        return y;
    }

    // ---------------- ANOMALIAS ----------------
    private int secAnom(int y) {
        header(y, "ANOMALÍAS (cielos y eventos)");
        y += 16;
        boolean active = !anomName.isEmpty();
        int ai = Math.max(0, indexOfAnomaly());
        label(LEFT, y, active ? "ACTIVA: " + anomName.toUpperCase(Locale.ROOT) + (anomLeft < 0 ? " (fija)" : String.format(Locale.ROOT, " · quedan %.0f s", anomLeft)) : "Sin anomalía activa", active ? AN_RGB[ai] : COLOR_DIM);
        y += 11;
        if (active) {
            final int rgb = AN_RGB[ai];
            widgets.add(new Canvas("an_bar", LEFT, y, INNER, 7, (g, font, c, hov) -> {
                g.fill(c.x, c.y, c.x + c.w, c.y + c.h, 0xFF000000);
                float f = anomLeft < 0 ? 1F : Math.max(0F, Math.min(1F, anomLeft / Math.max(1F, anomTotal)));
                g.fill(c.x + 1, c.y + 1, c.x + 1 + Math.round((c.w - 2) * f), c.y + c.h - 1, rgb);
            }));
            y += 10;
        }
        Runnable[] pick = new Runnable[5];
        for (int i = 0; i < 5; i++) { final int k = i; pick[i] = () -> color = k; }
        y = rowN(y, "an_c", AN_SHORT, AN_RGB, pick, color);
        textField("an_dur", LEFT, y, HALF + 20, "Segundos: ", () -> durText, v -> durText = v, NUMBER_CHARS);
        toggle("an_fixed", LEFT + HALF + 23, y, INNER - HALF - 23, "Fija", () -> fixed, v -> fixed = v);
        y += ROW;
        y = slider(y, LEFT, INNER, "an_strobe", "Destellos", "%", 0, 100, 0, 100, 1, () -> strobeVal * 100, v -> { strobeVal = v / 100.0; strobeAt = System.currentTimeMillis(); }, 100);
        label(LEFT, y, "Peligro: " + AN_DANGER[color] + (fixed ? " · queda prendida" : " · dura " + durText + " s"), COLOR_DIM);
        y += 12;
        y = row2(y, "an_go", "LANZAR " + AN_NAME[color], AN_RGB[color], () -> send("anomalia " + AN_ID[color] + " " + (fixed ? "fijo" : (durText.isBlank() ? "60" : durText))), "APAGAR", GREY, () -> send("anomalia fin"));
        if (!hasAnom) { label(LEFT, y, "El mod anomalias no está en el servidor", COLOR_ERROR); y += 12; }
        return y + 4;
    }

    private int indexOfAnomaly() {
        for (int i = 0; i < AN_ID.length; i++) if (anomName.toLowerCase(Locale.ROOT).contains(AN_ID[i])) return i;
        return 0;
    }

    // ---------------- MENSAJES ----------------
    private int secMsgs(int y) {
        header(y, "MENSAJES de la anomalía");
        y += 16;
        List<String> scopes = new ArrayList<>();
        scopes.add("GLOBAL");
        for (String n : AN_NAME) scopes.add(n);
        dropdown("msg_scope", LEFT, y, INNER, "Para", scopes, scopes.get(scope), v -> scope = Math.max(0, scopes.indexOf(v)), null);
        y += ROW;
        List<String[]> fields = new ArrayList<>(List.of(MSG));
        if (scope != 0) fields.addAll(List.of(CMD));
        for (String[] m : fields) {
            final String key = m[1];
            label(LEFT, y, m[0], COLOR_DIM);
            y += 10;
            textField("msg_" + key + scope, LEFT, y, INNER, "", () -> cfgGet(key), v -> cfgSet(key, v), TEXT_CHARS);
            y += ROW;
        }
        y = row2(y, "msg_sv", "GUARDAR", OK, () -> { if (cfg != null) { Net.CH.sendToServer(new Net.Req("anom.set", cfg.toString())); setStatus("Guardando…", COLOR_TEXT); } },
                "RECARGAR", GREY, () -> Net.CH.sendToServer(new Net.Req("anom.get", "")));
        label(LEFT, y, "Título|Subtítulo · {COLOR} y {PELIGRO} se reemplazan", COLOR_DIM);
        y += 10;
        label(LEFT, y, savedMsg.isEmpty() ? "En un color, vacío = usa el global" : savedMsg, savedMsg.startsWith("Error") ? COLOR_ERROR : savedMsg.isEmpty() ? COLOR_DIM : COLOR_OK);
        return y + 14;
    }

    // ---------------- VISION ----------------
    private int secVision(int y) {
        header(y, "VISIÓN (filtros de pantalla)");
        y += 16;
        JsonObject v = cat.has("vfx") ? cat.getAsJsonObject("vfx") : new JsonObject();
        JsonObject now = live.has("vfx") ? live.getAsJsonObject("vfx") : null;
        boolean on = now == null || !now.has("enabled") || now.get("enabled").getAsBoolean();
        y = rowN(y, "vx_on", new String[]{"FILTRO " + (on ? "ON" : "OFF"), "REINICIAR", "PS1 ON", "PS1 OFF"}, new int[]{on ? OK : GREY, GREY, PURP, GREY},
                new Runnable[]{() -> send("visionfx " + (on ? "off" : "on")), () -> send("visionfx reset"), () -> send("visionfx ps1 on"), () -> send("visionfx ps1 off")}, -1);

        // plantillas de color (globales)
        List<String> tpls = ids(v, "plantillas");
        if (tpl.isEmpty() && !tpls.isEmpty()) tpl = tpls.get(0);
        dropdown("vx_tpl", LEFT, y, INNER - 44, "Plantilla", tpls, tpl, s -> tpl = s, null);
        String tp = tpl;
        button("vx_tpl_go", LEFT + INNER - 41, y, 41, "APLICAR", false, () -> send("visionfx plantilla " + tp)).color(TEAL);
        y += ROW;
        label(LEFT, y, cut(descOf(v, "plantillas", tpl), 44), COLOR_DIM);
        y += 11;

        // modos de evento (por jugador o para todos)
        List<String> modes = ids(v, "modos");
        if (modo.isEmpty() && !modes.isEmpty()) modo = modes.get(0);
        dropdown("vx_mode", LEFT, y, INNER - 44, "Modo", modes, modo, s -> modo = s, null);
        String md = modo, tg = target;
        button("vx_mode_go", LEFT + INNER - 41, y, 41, "APLICAR", false, () -> send("visionfx modo " + md + (all() ? "" : " " + tg))).color(TEAL);
        y += ROW;
        label(LEFT, y, cut(descOf(v, "modos", modo), 44), COLOR_DIM);
        y += 11;
        y = row2(y, "vx_mode_off", "QUITAR MODO (" + (all() ? "todos" : cut(target, 8)) + ")", GREY, () -> send("visionfx modo off" + (all() ? "" : " " + tg)),
                "FILTRO " + (all() ? "TODOS" : cut(target, 8)) + ": ON", OK, () -> send("visionfx player " + tg + " on"));
        y = rowN(y, "vx_pl", new String[]{"OFF", "POR DEFECTO"}, new int[]{GREY, BLUE},
                new Runnable[]{() -> send("visionfx player " + tg + " off"), () -> send("visionfx player " + tg + " default")}, -1);

        // ajustes finos del filtro global (valores reales del servidor; se mandan al soltar)
        label(LEFT, y, "Ajustes del filtro global", COLOR_DIM);
        y += 10;
        for (Object[] s : VFX) {
            final String cmd = (String) s[0];
            final String field = (String) s[7];
            final double def = (Double) s[6];
            double shown = LOCAL.containsKey(cmd) ? LOCAL.get(cmd) : dbl(now, field, def);
            final double sv = shown;
            y = slider(y, LEFT, INNER, "vx_" + cmd, (String) s[1], (String) s[2], (Double) s[3], (Double) s[4], (Double) s[3], (Double) s[4], (Double) s[5],
                    () -> LOCAL.containsKey(cmd) ? LOCAL.get(cmd) : sv,
                    val -> { LOCAL.put(cmd, val); PENDING.put(cmd, System.currentTimeMillis()); }, def);
        }
        return y + 4;
    }

    private static List<String> ids(JsonObject o, String key) {
        List<String> l = new ArrayList<>();
        for (JsonElement e : arr(o, key)) l.add(e.getAsJsonObject().get("id").getAsString());
        return l;
    }

    private static String descOf(JsonObject o, String key, String id) {
        for (JsonElement e : arr(o, key)) {
            JsonObject d = e.getAsJsonObject();
            if (d.get("id").getAsString().equals(id)) return d.has("d") ? d.get("d").getAsString() : "";
        }
        return "";
    }

    // ---------------- COSMETICOS ----------------
    private int secCosmetics(int y) {
        header(y, "COSMÉTICOS (efectos infinitos)");
        y += 16;
        Map<String, String> names = new LinkedHashMap<>();
        for (JsonElement e : arr(cat, "cos")) {
            JsonObject d = e.getAsJsonObject();
            names.put(d.get("nombre").getAsString(), d.get("id").getAsString());
        }
        List<String> opts = new ArrayList<>(names.keySet());
        String shown = "";
        for (Map.Entry<String, String> e : names.entrySet()) if (e.getValue().equals(cosId)) shown = e.getKey();
        if (shown.isEmpty() && !opts.isEmpty()) { shown = opts.get(0); cosId = names.get(shown); }
        dropdown("cs_pick", LEFT, y, INNER, "", opts, shown, s -> cosId = names.getOrDefault(s, cosId), null);
        y += ROW;
        String tg = target, id = cosId;
        y = rowN(y, "cs_a", new String[]{"DAR", "QUITAR", "LIMPIAR"}, new int[]{OK, BAD, GREY},
                new Runnable[]{() -> send("cosmetico dar " + tg + " " + id), () -> send("cosmetico quitar " + tg + " " + id), () -> send("cosmetico limpiar " + tg)}, -1);
        label(LEFT, y, "Para: " + (all() ? "todos los jugadores" : target), COLOR_DIM);
        return y + 14;
    }

    // ---------------- QUICK TIMES ----------------
    private int secQte(int y) {
        header(y, "QUICK TIMES (probar y editar)");
        y += 16;
        boolean on = cat.has("qteOn") && cat.get("qteOn").getAsBoolean();
        List<String> saved = new ArrayList<>();
        for (JsonElement e : arr(cat, "qte")) saved.add(e.getAsString());
        if (!on) {
            label(LEFT, y, "El servidor de QuickTime Events está inactivo", COLOR_ERROR);
            y += 11;
        }
        if ((qteName.isEmpty() || !saved.contains(qteName)) && !saved.isEmpty()) qteName = saved.get(0);
        dropdown("qt_pick", LEFT, y, INNER, "", saved, qteName.isEmpty() ? "(ninguno)" : qteName, s -> qteName = s, i -> saved.get(i).startsWith("ob_"));
        y += ROW;
        String tg = target, nm = qteName;
        y = row2(y, "qt_go", "PROBAR EN " + (all() ? "TODOS" : cut(target, 8).toUpperCase(Locale.ROOT)), OK, () -> { if (!nm.isEmpty()) send("quicktime set saved " + tg + " " + nm); },
                "DETENER", BAD, () -> send("quicktime remove " + tg));
        y = row2(y, "qt_ed", "ABRIR EDITOR", PURP, () -> { close(); send("quicktime editor" + (nm.isEmpty() ? "" : " " + nm)); },
                "RECARGAR", GREY, () -> { send("quicktime reload"); catSoon = System.currentTimeMillis() + 500; });
        label(LEFT, y, "Los ob_* los generan infectados y bestialago (naranja).", COLOR_DIM);
        y += 10;
        label(LEFT, y, "Cada fase se prueba por separado; la cadena la lanza el mob.", COLOR_DIM);
        return y + 14;
    }

    // ---------------- CONCIERTO ----------------
    private int secConcert(int y) {
        header(y, "CONCIERTO (show de Miku)");
        y += 16;
        List<String> names = new ArrayList<>();
        for (String[] s : SONGS) names.add(s[0]);
        dropdown("c_song", LEFT, y, HALF + 30, "", names, song, v -> song = v, null);
        button("c_start", LEFT + HALF + 33, y, INNER - HALF - 33, "INICIAR", false, () -> {
            for (String[] s : SONGS) if (s[0].equals(song)) send("concierto iniciar " + s[1]);
        }).color(PINK);
        y += ROW;
        y = row2(y, "c_a", "DETENER", GREY, () -> send("concierto detener"), "FINAL (prueba)", PINK, () -> send("concierto final"));
        y = row1(y, "c_stage", "ESCENARIO AQUÍ", BLUE, () -> send("concierto escenario aqui"));
        y = slider(y, LEFT, INNER, "c_int", "Intensidad", "x", 0.5, 2.0, 0.1, 5, 0.05, () -> concInt, v -> { concInt = v; concIntAt = System.currentTimeMillis(); }, 1.0);
        y = slider(y, LEFT, INNER, "c_str", "Estrobo", "%", 0, 100, 0, 100, 1, () -> concStrobe * 100, v -> { concStrobe = v / 100.0; concStrobeAt = System.currentTimeMillis(); }, 100);
        y = row2(y, "c_holo", "HOLOGRAMAS ON", OK, () -> send("concierto hologramas on"), "HOLOGRAMAS OFF", GREY, () -> send("concierto hologramas off"));
        label(LEFT, y, "Efecto en los jugadores", COLOR_DIM);
        y += 10;
        y = row2(y, "c_p1", "VOLAR", PURP, () -> send("concierto jugadores volar"), "BRILLO", PURP, () -> send("concierto jugadores brillo"));
        y = row2(y, "c_p2", "CONFETI", PURP, () -> send("concierto jugadores confeti"), "NADA", GREY, () -> send("concierto jugadores nada"));
        y = row2(y, "c_m", "VER MARCAS", BLUE, () -> send("concierto vermarcas"), "QUITAR MARCAS", GREY, () -> send("concierto quitarmarcas"));
        return y + 4;
    }

    // ---------------- SPAWNS ----------------
    private int secSpawns(int y) {
        header(y, "SPAWNS (spawn natural)");
        y += 16;
        y = field(y, "s_mob", "Mob", () -> mob, v -> mob = v, TOKEN_CHARS);
        y = field(y, "s_num", "Valor", () -> num, v -> num = v, NUMBER_CHARS);
        y = row2(y, "s_a", "ACTIVAR", OK, () -> send("spawns activar " + mob), "DESACTIVAR", BAD, () -> send("spawns desactivar " + mob));
        y = row2(y, "s_b", "MÁXIMO = valor", AMBER, () -> send("spawns max " + mob + " " + num), "PROB. % = valor", AMBER, () -> send("spawns prob " + mob + " " + num));
        y = row2(y, "s_c", "INFO", BLUE, () -> send("spawns info " + mob), "RESETEAR REGLA", GREY, () -> send("spawns reset " + mob));
        y = row2(y, "s_d", "PANEL COMPLETO", PURP, () -> { close(); send("spawns gui"); }, "LISTA DE REGLAS", BLUE, () -> send("spawns lista"));
        y = row1(y, "s_e", "RECARGAR", GREY, () -> send("spawns recargar"));
        y = row2(y, "s_f", "MONSTRUOS BLOQUEADOS", BAD, () -> send("spawns defecto animales"), "TODO LIBRE", OK, () -> send("spawns defecto libre"));
        return y + 4;
    }

    // ---------------- INFECTADOS ----------------
    private int secInfected(int y) {
        header(y, "INFECTADOS (zonas de plagas)");
        y += 16;
        List<JsonObject> zones = new ArrayList<>();
        for (JsonElement e : arr(cat, "zonas")) zones.add(e.getAsJsonObject());
        final List<JsonObject> zs = zones;
        // mapa: zonas (circulos) y jugadores (puntos) vistos desde arriba; un clic en una zona la elige
        final Canvas map = new Canvas("inf_map", LEFT, y, INNER, 96, (g, font, c, hov) -> paintMap(g, font, c, zs));
        map.onPress = (mx, my) -> {
            double[] b = bounds(zs);
            for (JsonObject z : zs) {
                double px = c2x(map, b, dbl(z, "x", 0)), pz = c2z(map, b, dbl(z, "z", 0)), r = dbl(z, "r", 0) * b[4];
                if (Math.hypot(mx - px, my - pz) <= Math.max(5, r)) { selectZone(z); break; }
            }
        };
        widgets.add(map);
        y += 100;
        List<String> names = new ArrayList<>();
        for (JsonObject z : zones) names.add(str(z, "n", "?"));
        if (!names.isEmpty()) {
            dropdown("i_pick", LEFT, y, INNER, "Zona", names, names.contains(zoneName) ? zoneName : "(elegir)", n -> { for (JsonObject z : zs) if (str(z, "n", "").equals(n)) selectZone(z); }, i -> !zs.get(i).get("on").getAsBoolean());
            y += ROW;
        }
        JsonObject cur = null;
        for (JsonObject z : zones) if (str(z, "n", "").equals(zoneName)) cur = z;
        if (cur != null) {
            label(LEFT, y, cut(str(cur, "d", "?") + " " + (int) dbl(cur, "x", 0) + " " + (int) dbl(cur, "z", 0) + " · r" + (int) dbl(cur, "r", 0) + " · máx " + (int) dbl(cur, "max", 0)
                    + (cur.get("boss").getAsBoolean() ? " · +motosierra" : "") + (cur.get("on").getAsBoolean() ? "" : " · DESACTIVADA"), 48), COLOR_DIM);
            y += 11;
        }
        y = field(y, "i_name", "Zona", () -> zoneName, v -> zoneName = v, ID_CHARS);
        y = field(y, "i_rad", "Radio", () -> zoneRadius, v -> zoneRadius = v, NUMBER_CHARS);
        y = field(y, "i_max", "Máx.", () -> num, v -> num = v, NUMBER_CHARS);
        y = row2(y, "i_a", "CREAR AQUÍ", OK, () -> { send("infectados zona crear " + zoneName + " " + zoneRadius + " " + num); catSoon = System.currentTimeMillis() + 600; },
                "BORRAR", BAD, () -> { send("infectados zona borrar " + zoneName); catSoon = System.currentTimeMillis() + 600; });
        y = row2(y, "i_b", "VER ZONAS", BLUE, () -> send("infectados zona lista"), "MÁXIMO = Máx.", AMBER, () -> { send("infectados zona max " + zoneName + " " + num); catSoon = System.currentTimeMillis() + 600; });
        y = row2(y, "i_c", "ACTIVA: SÍ", OK, () -> { send("infectados zona activa " + zoneName + " true"); catSoon = System.currentTimeMillis() + 600; },
                "ACTIVA: NO", GREY, () -> { send("infectados zona activa " + zoneName + " false"); catSoon = System.currentTimeMillis() + 600; });
        y = row2(y, "i_d", "MOTOSIERRA: SÍ", AMBER, () -> { send("infectados zona motosierra " + zoneName + " true"); catSoon = System.currentTimeMillis() + 600; },
                "MOTOSIERRA: NO", GREY, () -> { send("infectados zona motosierra " + zoneName + " false"); catSoon = System.currentTimeMillis() + 600; });
        label(LEFT, y, "La zona se crea donde estás parado (radio 4-512).", COLOR_DIM);
        return y + 14;
    }

    private static void selectZone(JsonObject z) {
        zoneName = str(z, "n", zoneName);
        zoneRadius = String.valueOf((int) dbl(z, "r", 40));
        num = String.valueOf((int) dbl(z, "max", 10));
    }

    /** [minX, minZ, maxX, maxZ, escala] del mapa para que quepa todo */
    private static double[] bounds(List<JsonObject> zones) {
        double minX = 1e9, minZ = 1e9, maxX = -1e9, maxZ = -1e9;
        for (JsonObject z : zones) {
            double r = dbl(z, "r", 0);
            minX = Math.min(minX, dbl(z, "x", 0) - r); maxX = Math.max(maxX, dbl(z, "x", 0) + r);
            minZ = Math.min(minZ, dbl(z, "z", 0) - r); maxZ = Math.max(maxZ, dbl(z, "z", 0) + r);
        }
        for (JsonObject p : players()) {
            minX = Math.min(minX, dbl(p, "x", 0)); maxX = Math.max(maxX, dbl(p, "x", 0));
            minZ = Math.min(minZ, dbl(p, "z", 0)); maxZ = Math.max(maxZ, dbl(p, "z", 0));
        }
        if (minX > maxX) { minX = -50; maxX = 50; minZ = -50; maxZ = 50; }
        double pad = Math.max(16, (maxX - minX) * 0.06);
        minX -= pad; maxX += pad; minZ -= pad; maxZ += pad;
        return new double[]{minX, minZ, maxX, maxZ, 0};
    }

    private static double scaleOf(Canvas c, double[] b) { return Math.min((c.w - 6) / (b[2] - b[0]), (c.h - 6) / (b[3] - b[1])); }

    private static double c2x(Canvas c, double[] b, double wx) { b[4] = scaleOf(c, b); return c.x + 3 + (wx - b[0]) * b[4]; }

    private static double c2z(Canvas c, double[] b, double wz) { b[4] = scaleOf(c, b); return c.y + 3 + (wz - b[1]) * b[4]; }

    private void paintMap(GuiGraphics g, Font font, Canvas c, List<JsonObject> zones) {
        g.fill(c.x, c.y, c.x + c.w, c.y + c.h, 0xFF000000);
        g.fill(c.x + 1, c.y + 1, c.x + c.w - 1, c.y + c.h - 1, COLOR_FIELD);
        double[] b = bounds(zones);
        b[4] = scaleOf(c, b);
        g.enableScissor(c.x + 1, c.y + 1, c.x + c.w - 1, c.y + c.h - 1);
        Draw.begin(g, false);
        for (JsonObject z : zones) {
            boolean sel = str(z, "n", "").equals(zoneName);
            boolean on = z.get("on").getAsBoolean();
            float px = (float) c2x(c, b, dbl(z, "x", 0)), pz = (float) c2z(c, b, dbl(z, "z", 0)), r = (float) Math.max(3, dbl(z, "r", 0) * b[4]);
            int col = !on ? 0xFF707070 : z.get("boss").getAsBoolean() ? 0xFFE59A1F : 0xFF7FBF3F;
            Draw.disc(px, pz, r, (col & 0xFFFFFF) | 0x30000000, (col & 0xFFFFFF) | 0x50000000);
            Draw.ring(px, pz, r, sel ? 1.8f : 1.0f, sel ? 0xFFFFFFFF : col);
        }
        for (JsonObject p : players()) {
            float px = (float) c2x(c, b, dbl(p, "x", 0)), pz = (float) c2z(c, b, dbl(p, "z", 0));
            boolean me = str(p, "n", "").equals(target);
            Draw.disc(px, pz, me ? 3.2f : 2.4f, me ? 0xFFFFFFFF : 0xFFFFE08A, me ? 0xFFD42A3A : 0xFFE59A1F);
        }
        Draw.end();
        for (JsonObject z : zones) {
            String n = str(z, "n", "?");
            int px = (int) c2x(c, b, dbl(z, "x", 0));
            int pz = (int) c2z(c, b, dbl(z, "z", 0));
            g.drawString(font, n, px - font.width(n) / 2, pz - 4, str(z, "n", "").equals(zoneName) ? 0xFFFFFFFF : 0xFFC8C8C8, true);
        }
        g.disableScissor();
        if (zones.isEmpty()) g.drawString(font, "Sin zonas todavía", c.x + 6, c.y + c.h / 2 - 4, COLOR_DIM, false);
    }

    // ---------------- CRAFTEOS ----------------
    private int secCrafts(int y) {
        header(y, "CRAFTEOS y loot");
        y += 16;
        y = field(y, "k_item", "Ítem", () -> item, v -> item = v, TOKEN_CHARS);
        y = row2(y, "k_a", "PROHIBIR", BAD, () -> send("crafteos prohibir " + (item.isBlank() ? "mano" : item)), "PERMITIR", OK, () -> send("crafteos permitir " + item));
        y = row1(y, "k_b", "VER PROHIBIDOS", BLUE, () -> send("crafteos prohibidos"));
        y = field(y, "k_rec", "Receta", () -> recipe, v -> recipe = v, TOKEN_CHARS);
        y = row2(y, "k_c", "DESACTIVAR", BAD, () -> send("crafteos desactivar " + recipe), "ACTIVAR", OK, () -> send("crafteos activar " + recipe));
        y = row2(y, "k_d", "VER DESACTIVADAS", BLUE, () -> send("crafteos lista"), "RECARGAR", GREY, () -> send("crafteos recargar"));
        y = row2(y, "k_e", "PANEL CRAFTEOS", PURP, () -> { close(); send("crafteos gui"); }, "PANEL MODELOS", PURP, () -> { close(); send("modelos gui"); });
        label(LEFT, y, "Ítem: minecraft:diamond, modid:* o «mano».", COLOR_DIM);
        y += 10;
        label(LEFT, y, "Un ítem prohibido no se craftea ni sale de drops.", COLOR_DIM);
        return y + 14;
    }

    // ---------------- ATAJOS Y CONSOLA ----------------
    private int secShortcuts(int y) {
        header(y, "ATAJOS (consola y combos)");
        y += 16;
        textField("sh_cmd", LEFT, y, INNER, "/", () -> console, v -> console = v, TEXT_CHARS);
        y += ROW;
        List<String> hist = new ArrayList<>(Shortcuts.HISTORY);
        if (!hist.isEmpty()) {
            dropdown("sh_hist", LEFT, y, INNER - 44, "Historial", hist, "elegir", s -> console = s, null);
        } else {
            label(LEFT, y + 2, "Sin historial todavía", COLOR_DIM);
        }
        button("sh_send", LEFT + INNER - 41, y, 41, "ENVIAR", false, () -> {
            if (console.isBlank()) return;
            sendAll(console);
            Shortcuts.remember(console.trim());
        }).color(OK);
        y += ROW;
        textField("sh_name", LEFT, y, INNER - 44, "Nombre: ", () -> shortName, v -> shortName = v, TEXT_CHARS);
        button("sh_save", LEFT + INNER - 41, y, 41, "GUARDAR", false, () -> {
            if (!shortName.isBlank() && !console.isBlank()) { Shortcuts.add(shortName.trim(), console.trim()); setStatus("Atajo guardado: " + shortName.trim(), COLOR_OK); }
            else setStatus("Escribí un nombre y el comando de arriba", COLOR_ERROR);
        }).color(AMBER);
        y += ROW;
        label(LEFT, y, "Varios comandos: sepáralos con ; · clic = ejecutar · clic derecho = borrar", COLOR_DIM);
        y += 11;
        for (Shortcuts.Item it : new ArrayList<>(Shortcuts.ITEMS)) {
            Button b = button("sh_" + it.name(), LEFT, y, INNER, it.name(), false, () -> { sendAll(it.commands()); Shortcuts.remember(it.commands()); });
            b.color(0xFFE5C21F);
            b.onRight = () -> { Shortcuts.remove(it.name()); setStatus("Atajo borrado: " + it.name(), COLOR_DIM); };
            b.tip(it.commands());
            y += ROW;
        }
        return y + 4;
    }

    // ------------------------------------------------------------------ tiempo real ----
    @Override
    protected void renderContent(GuiGraphics graphics, Font font) {
        long now = System.currentTimeMillis();
        if (strobeAt != 0 && now - strobeAt > 350) { strobeAt = 0; send("anomalia destellos " + f2(strobeVal)); }
        if (concIntAt != 0 && now - concIntAt > 350) { concIntAt = 0; send("concierto intensidad " + f2(concInt)); }
        if (concStrobeAt != 0 && now - concStrobeAt > 350) { concStrobeAt = 0; send("concierto estrobo " + f2(concStrobe)); }
        // ajustes del filtro de color: se envian un momento despues de dejar de mover el slider
        if (!PENDING.isEmpty()) {
            for (var it = PENDING.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                if (now - e.getValue() > 350) {
                    Double v = LOCAL.get(e.getKey());
                    if (v != null) send("visionfx set " + e.getKey() + " " + f3(v));
                    it.remove();
                }
            }
        }
        if (now - liveAt > 1500) { liveAt = now; Net.CH.sendToServer(new Net.Req("live", "")); }
        if (now - catAt > 8000 || (catSoon != 0 && now > catSoon)) { catAt = now; catSoon = 0; Net.CH.sendToServer(new Net.Req("cat", "")); }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == ObscureClient.OPEN.getKey().getValue() && focusedKey == null) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------ datos del servidor ----
    public void onData(String key, String json) { receive(key, json); }

    static void receive(String key, String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            switch (key) {
                case "cat" -> cat = o;
                case "live" -> {
                    live = o;
                    MS_HIST.add(dbl(o, "ms", 0));
                    while (MS_HIST.size() > 60) MS_HIST.remove(0);
                    if (o.has("anom") && o.get("anom").isJsonObject()) { hasAnom = !o.getAsJsonObject("anom").has("has") || o.getAsJsonObject("anom").get("has").getAsBoolean(); if (hasAnom) setAnomStatus(o.getAsJsonObject("anom")); }
                    // los sliders vuelven a mostrar lo que dice el servidor cuando no estan pendientes
                    LOCAL.keySet().removeIf(k -> !PENDING.containsKey(k));
                }
                case "anom.get" -> {
                    hasAnom = !o.has("has") || o.get("has").getAsBoolean();
                    if (hasAnom && o.has("cfg")) cfg = o.getAsJsonObject("cfg");
                    if (hasAnom && o.has("status")) setAnomStatus(o.getAsJsonObject("status"));
                }
                case "anom.status" -> {
                    hasAnom = !o.has("has") || o.get("has").getAsBoolean();
                    if (hasAnom) setAnomStatus(o);
                }
                case "anom.saved" -> {
                    JsonElement e = o.get("error");
                    savedMsg = e == null || e.getAsString().isEmpty() ? "Guardado" : "Error: " + e.getAsString();
                }
                default -> { }
            }
        } catch (Exception ignored) { }
    }

    private static void setAnomStatus(JsonObject s) {
        String a = s.has("activa") ? s.get("activa").getAsString() : "";
        float left = s.has("resta") ? s.get("resta").getAsFloat() : -1F;
        if (a.isEmpty()) { anomName = ""; anomLeft = -1F; anomTotal = 1F; return; }
        if (!a.equals(anomName) || left > anomTotal) anomTotal = Math.max(1F, left);
        anomName = a;
        anomLeft = left;
    }
}
