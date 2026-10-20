package com.tunombre.obscureui.client;

import com.cotii.ui.UiPanel;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tunombre.obscureui.Net;
import net.minecraftforge.fml.loading.FMLPaths;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Panel de administracion de Obscure hecho con CotiiUI (panel lateral con secciones plegables). El estado vive en variables estaticas, el panel se redibuja entero cada cuadro. */
public final class ObscurePanel extends UiPanel {
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
    private static final String[][] SONGS = {
            {"Intro", "intro"}, {"Pre-Mesmerizer", "premesmerizer"}, {"Mesmerizer", "mesmerizer"}, {"Minna Miku Miku", "minnamikumiku"},
            {"Konton Boogie", "kontonboogie"}, {"Looping the Rooms", "loopingtherooms"}, {"Magical Cure", "magicalcure"}, {"Melt", "melt"},
            {"Static", "static"}, {"Anamanaguchi", "anamanaguchi"}, {"Intermedio", "intermedio"}, {"Pre-Triple Baka", "pretriplebaka"},
            {"Triple Baka", "triplebaka"}, {"Despedida", "despedida"}};

    private static boolean themed;

    // estado (se conserva al cerrar y reabrir)
    private static int color;
    private static int scope;   // 0 = global, 1..5 = color
    private static String durText = "60";
    private static boolean fixed;
    private static double strobeVal = 1.0, concInt = 1.0, concStrobe = 1.0;
    private static JsonObject cfg;
    private static boolean hasAnom = true;
    private static String anomStatus = "…";
    private static String song = SONGS[0][0];
    private static String mob = "minecraft:zombie", num = "10", zoneName = "zona1", zoneRadius = "40", item = "", recipe = "";
    // comandos pendientes de sliders (se mandan cuando se deja de arrastrar)
    private static long strobeAt, concIntAt, concStrobeAt;
    private int tickN;

    static {
        SECTION_COLORS.put("ANOMALÍAS", 0xFFD42A3A);
        SECTION_COLORS.put("MENSAJES", 0xFFE59A1F);
        SECTION_COLORS.put("CONCIERTO", 0xFFE0457B);
        SECTION_COLORS.put("SPAWNS", 0xFF2F7FEA);
        SECTION_COLORS.put("INFECTADOS", 0xFF2EBB55);
        SECTION_COLORS.put("CRAFTEOS", 0xFF8B4DE0);
        OPEN_BY_DEFAULT.add("ANOMALÍAS");
    }

    public ObscurePanel() {
        if (!themed) {
            themed = true;
            com.cotii.ui.CotiiUI.applyTheme(com.cotii.ui.Theme.RED);
            setStateFile(FMLPaths.CONFIGDIR.get().resolve("obscureui_panel.json"));
        }
        Net.CH.sendToServer(new Net.Req("anom.get", ""));
    }

    @Override
    protected String title() { return "OBSCURE · administración"; }

    private void send(String cmd) {
        Net.CH.sendToServer(new Net.Cmd(cmd));
        setStatus("/" + cmd, COLOR_OK);
    }

    private static String f2(double v) { return String.format(Locale.ROOT, "%.2f", v); }

    // ---- util de filas ----
    private int row2(int y, String k, String a, int ca, Runnable ra, String b, int cb, Runnable rb) {
        button(k + "_a", LEFT, y, HALF, a, false, ra).color(ca);
        button(k + "_b", RIGHT, y, HALF, b, false, rb).color(cb);
        return y + ROW;
    }

    private int row1(int y, String k, String a, int ca, Runnable ra) {
        button(k, LEFT, y, INNER, a, false, ra).color(ca);
        return y + ROW;
    }

    private int field(int y, String k, String label, java.util.function.Supplier<String> g, java.util.function.Consumer<String> s, java.util.function.IntPredicate filter) {
        textField(k, LEFT, y, INNER, label + ": ", g, s, filter);
        return y + ROW;
    }

    private static final int OK = 0xFF2EBB55, BAD = 0xFFD42A3A, GREY = 0xFF6B6B6B, BLUE = 0xFF2F7FEA, AMBER = 0xFFE59A1F, PURP = 0xFF8B4DE0, PINK = 0xFFE0457B;

    // ---- anomalias: configuracion ----
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

    @Override
    protected int layout(int y) {
        // ------------------------------------------------ ANOMALÍAS
        header(y, "ANOMALÍAS (cielos y eventos)");
        y += 16;
        label(LEFT, y, anomStatus, anomStatus.startsWith("ACTIVA") ? AN_RGB[Math.max(0, Math.min(4, indexOfActive()))] : COLOR_DIM);
        y += 12;
        dropdown("an_color", LEFT, y, INNER, "Color", List.of(AN_NAME), AN_NAME[color], v -> { for (int i = 0; i < AN_NAME.length; i++) if (AN_NAME[i].equals(v)) color = i; }, null);
        y += ROW;
        textField("an_dur", LEFT, y, HALF + 20, "Segundos: ", () -> durText, v -> durText = v, NUMBER_CHARS);
        toggle("an_fixed", LEFT + HALF + 23, y, INNER - HALF - 23, "Fija", () -> fixed, v -> fixed = v);
        y += ROW;
        y = slider(y, LEFT, INNER, "an_strobe", "Destellos", "%", 0, 100, 0, 100, 1, () -> strobeVal * 100, v -> { strobeVal = v / 100.0; strobeAt = System.currentTimeMillis(); }, 100);
        label(LEFT, y, "Peligro: " + AN_DANGER[color] + (fixed ? " · queda prendida" : " · dura " + durText + " s"), COLOR_DIM);
        y += 12;
        y = row2(y, "an_go", "LANZAR " + AN_NAME[color], AN_RGB[color], () -> send("anomalia " + AN_ID[color] + " " + (fixed ? "fijo" : (durText.isBlank() ? "60" : durText))), "APAGAR", GREY, () -> send("anomalia fin"));
        if (!hasAnom) { label(LEFT, y, "El mod anomalias no está en el servidor", COLOR_ERROR); y += 12; }
        y += 4;

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
        label(LEFT, y, "En un color, vacío = usa el global", COLOR_DIM);
        y += 14;

        // ------------------------------------------------ CONCIERTO
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
        y += 4;

        // ------------------------------------------------ SPAWNS
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
        y += 4;

        // ------------------------------------------------ INFECTADOS
        header(y, "INFECTADOS (zonas de plagas)");
        y += 16;
        y = field(y, "i_name", "Zona", () -> zoneName, v -> zoneName = v, ID_CHARS);
        y = field(y, "i_rad", "Radio", () -> zoneRadius, v -> zoneRadius = v, NUMBER_CHARS);
        y = field(y, "i_max", "Máx.", () -> num, v -> num = v, NUMBER_CHARS);
        y = row2(y, "i_a", "CREAR AQUÍ", OK, () -> send("infectados zona crear " + zoneName + " " + zoneRadius + " " + num), "BORRAR", BAD, () -> send("infectados zona borrar " + zoneName));
        y = row2(y, "i_b", "VER ZONAS", BLUE, () -> send("infectados zona lista"), "MÁXIMO = Máx.", AMBER, () -> send("infectados zona max " + zoneName + " " + num));
        y = row2(y, "i_c", "ACTIVA: SÍ", OK, () -> send("infectados zona activa " + zoneName + " true"), "ACTIVA: NO", GREY, () -> send("infectados zona activa " + zoneName + " false"));
        y = row2(y, "i_d", "MOTOSIERRA: SÍ", AMBER, () -> send("infectados zona motosierra " + zoneName + " true"), "MOTOSIERRA: NO", GREY, () -> send("infectados zona motosierra " + zoneName + " false"));
        label(LEFT, y, "La zona se crea donde estás parado (radio 4-512).", COLOR_DIM);
        y += 14;

        // ------------------------------------------------ CRAFTEOS
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
        y += 14;
        return y;
    }

    private int indexOfActive() {
        for (int i = 0; i < AN_ID.length; i++) if (anomStatus.toLowerCase(Locale.ROOT).contains(AN_ID[i])) return i;
        return 0;
    }

    @Override
    protected void renderContent(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font) {
        long now = System.currentTimeMillis();
        if (strobeAt != 0 && now - strobeAt > 350) { strobeAt = 0; send("anomalia destellos " + f2(strobeVal)); }
        if (concIntAt != 0 && now - concIntAt > 350) { concIntAt = 0; send("concierto intensidad " + f2(concInt)); }
        if (concStrobeAt != 0 && now - concStrobeAt > 350) { concStrobeAt = 0; send("concierto estrobo " + f2(concStrobe)); }
        if (++tickN % 90 == 0) Net.CH.sendToServer(new Net.Req("anom.status", ""));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == ObscureClient.OPEN.getKey().getValue() && focusedKey == null) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- datos del servidor ----
    public void onData(String key, String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            switch (key) {
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
                    if (e == null || e.getAsString().isEmpty()) setStatus("Guardado", COLOR_OK);
                    else setStatus("Error: " + e.getAsString(), COLOR_ERROR);
                }
                default -> { }
            }
        } catch (Exception ignored) { }
    }

    private static void setAnomStatus(JsonObject s) {
        String a = s.has("activa") ? s.get("activa").getAsString() : "";
        if (a.isEmpty()) { anomStatus = "Sin anomalía activa"; return; }
        float left = s.has("resta") ? s.get("resta").getAsFloat() : -1F;
        anomStatus = "ACTIVA: " + a.toUpperCase(Locale.ROOT) + (left < 0 ? " (fija)" : String.format(Locale.ROOT, " (quedan %.0f s)", left));
    }
}
