package com.tunombre.obscurenpc.client;

import com.cotii.ui.CotiiUI;
import com.cotii.ui.Theme;
import com.cotii.ui.UiPanel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tunombre.obscurenpc.Net;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Editor de NPCs hecho con CotiiUI: identidad, comportamiento, atributos, equipo, dialogos, IA, sonidos, rutinas y acciones. */
public final class NpcPanel extends UiPanel {
    private static NpcPanel active;
    private static boolean themed;

    private final boolean isSpawner;
    private final JsonObject root;
    private final JsonObject data;
    private final JsonObject cat;
    private String gestoTest = "talk_a";
    private String sayText = "";
    private String lineaNueva = "";
    private final Map<String, Long> pending = new HashMap<>();
    private final Map<String, String> pendingVal = new HashMap<>();

    static {
        SECTION_COLORS.put("SPAWNER", 0xFF2F7FEA);
        SECTION_COLORS.put("IDENTIDAD", 0xFF2EBB55);
        SECTION_COLORS.put("COMPORTAMIENTO", 0xFFE59A1F);
        SECTION_COLORS.put("ATRIBUTOS", 0xFFD42A3A);
        SECTION_COLORS.put("EQUIPO Y ARMAS", 0xFFB45BE0);
        SECTION_COLORS.put("DIÁLOGO", 0xFF2AA6C8);
        SECTION_COLORS.put("IA POR CONTEXTO", 0xFFE0457B);
        SECTION_COLORS.put("SONIDOS", 0xFFFF7A1A);
        SECTION_COLORS.put("RUTINA / OBJETIVOS", 0xFF7FBF3F);
        SECTION_COLORS.put("ACCIONES", 0xFFE5C21F);
        OPEN_BY_DEFAULT.add("IDENTIDAD");
        OPEN_BY_DEFAULT.add("COMPORTAMIENTO");
        OPEN_BY_DEFAULT.add("SPAWNER");
    }

    private NpcPanel(JsonObject root) {
        this.root = root;
        this.isSpawner = "spawner".equals(str(root, "tipo"));
        this.data = root.getAsJsonObject("data");
        this.cat = root.getAsJsonObject("cat");
        if (!themed) {
            themed = true;
            CotiiUI.applyTheme(Theme.RED);
            setStateFile(FMLPaths.CONFIGDIR.get().resolve("obscurenpc_panel.json"));
        }
    }

    public static void open(String json) {
        try {
            NpcPanel p = new NpcPanel(JsonParser.parseString(json).getAsJsonObject());
            active = p;
            CotiiUI.openPanel(Component.literal("Obscure NPC"), p);
        } catch (RuntimeException ignored) { }
    }

    public static void notice(String msg) {
        if (active != null) active.setStatus(msg, COLOR_OK);
    }

    @Override
    protected String title() { return isSpawner ? "SPAWNER de NPC" : "NPC · " + str(data, "nombre"); }

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

    private double gn(String k) { try { return data.has(k) ? data.get(k).getAsDouble() : 0; } catch (RuntimeException e) { return 0; } }

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

    /** cambia un valor del NPC: se muestra ya y se manda al servidor */
    private void setS(String k, String v) {
        if (k.startsWith("@")) { root.addProperty(k.substring(1), v); }
        else if (data.has(k) && data.get(k).isJsonArray()) {
            JsonArray a = new JsonArray();
            for (String s : v.split("\\|")) if (!s.isBlank()) a.add(s.trim());
            data.add(k, a);
            v = String.join("\n", java.util.Arrays.stream(v.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        } else data.addProperty(k, v);
        sendSet(k, v);
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

    private double rn(String k) { return root.has(k) ? root.get(k).getAsDouble() : 0; }

    // ------------------------------------------------------------------ widgets cortos ----
    private int tf(int y, String k, String label) {
        textField("f_" + k, LEFT, y, INNER, label + ": ", () -> gs(k), v -> setS(k, v), TEXT_CHARS);
        return y + ROW;
    }

    private int tg(int y, String k, String label) {
        toggle("t_" + k, LEFT, y, INNER, label, () -> gb(k), v -> setB(k, v));
        return y + ROW;
    }

    private int sl(int y, String k, String label, String unit, double smin, double smax, double min, double max, double step, double reset, boolean integer) {
        return slider(y, LEFT, INNER, "s_" + k, label, unit, smin, smax, min, max, step, () -> gn(k), v -> setN(k, v, integer), reset);
    }

    private void dd(int y, String k, String label, List<String> opts, boolean allowNone) {
        List<String> o = new ArrayList<>();
        if (allowNone) o.add("(ninguno)");
        o.addAll(opts);
        String cur = gs(k);
        dropdown("d_" + k, LEFT, y, INNER, label, o, cur.isEmpty() && allowNone ? "(ninguno)" : cur, v -> setS(k, v.equals("(ninguno)") ? "" : v), null);
    }

    private static List<String> list(JsonObject o, String k) {
        List<String> l = new ArrayList<>();
        if (o != null && o.has(k)) for (JsonElement e : o.getAsJsonArray(k)) l.add(e.getAsString());
        return l;
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

    // ------------------------------------------------------------------ layout ----
    @Override
    protected int layout(int y) {
        if (isSpawner) y = secSpawner(y);
        y = secIdentity(y);
        y = secBehavior(y);
        y = secAttributes(y);
        y = secEquipment(y);
        y = secDialogue(y);
        y = secBrain(y);
        y = secSounds(y);
        y = secRoutine(y);
        y = secActions(y);
        return y;
    }

    private int secSpawner(int y) {
        header(y, "SPAWNER");
        y += 16;
        toggle("sp_on", LEFT, y, INNER, "Activo", () -> root.has("activo") && root.get("activo").getAsBoolean(), v -> setB("@activo", v));
        y += ROW;
        y = slider(y, LEFT, INNER, "sp_int", "Cada (tras morir)", " ticks", 20, 6000, 20, 72000, 20, () -> rn("intervalo"), v -> setN("@intervalo", v, true), 600);
        y = slider(y, LEFT, INNER, "sp_max", "Máximo vivos", "", 1, 20, 1, 64, 1, () -> rn("maximo"), v -> setN("@maximo", v, true), 1);
        y = slider(y, LEFT, INNER, "sp_rad", "Radio de aparición", " bl", 0, 16, 0, 32, 1, () -> rn("radio"), v -> setN("@radio", v, true), 4);
        y = slider(y, LEFT, INNER, "sp_rng", "Activo si hay jugador a", " bl", 16, 128, 8, 256, 4, () -> rn("rango"), v -> setN("@rango", v, true), 48);
        label(LEFT, y, "Plantilla de abajo = el NPC que repite", COLOR_DIM);
        return y + 12;
    }

    private int secIdentity(int y) {
        header(y, "IDENTIDAD");
        y += 16;
        y = tf(y, "nombre", "Nombre");
        y = tf(y, "id", "Clave (diálogos/IA)");
        y = tg(y, "mostrar_nombre", "Mostrar nombre sobre la cabeza");
        y = tf(y, "color_nombre", "Color del nombre (hex)");
        y = tf(y, "skin", "Skin (jugador o ns:ruta)");
        y = tg(y, "brazos_finos", "Brazos finos (Alex)");
        y = sl(y, "tamano", "Tamaño", "x", 0.5, 2.0, 0.2, 4.0, 0.05, 1.0, false);
        String motor = str(root, "motor");
        List<String> ms = List.of("Epic Fight (huesos de EF)", "Normal (huesos de Minecraft)");
        dropdown("d_motor", LEFT, y, INNER, "Motor de animación", ms, motor.equals("ef") ? ms.get(0) : ms.get(1), v -> {
            String m = v.startsWith("Epic") ? "ef" : "normal";
            JsonObject o = new JsonObject();
            o.addProperty("op", "motor");
            o.addProperty("motor", m);
            root.addProperty("motor", m);
            send(o);
        }, null);
        return y + ROW;
    }

    private int secBehavior(int y) {
        header(y, "COMPORTAMIENTO");
        y += 16;
        dd(y, "mirar", "Mira a", List.of("no", "jugadores", "todos"), false);
        y += ROW;
        y = sl(y, "mirar_rango", "Rango de mirada", " bl", 2, 24, 1, 64, 1, 8, false);
        y = tg(y, "quieto", "Quieto (no se mueve de su sitio)");
        y = tg(y, "vagar", "Vaga cerca de su hogar");
        y = sl(y, "vagar_radio", "Radio de vagar", " bl", 2, 20, 1, 64, 1, 6, true);
        List<String> st = list(cat, "posturas");
        dropdown("d_postura", LEFT, y, INNER, "Postura", st, st.isEmpty() ? "normal" : st.get(Math.max(0, Math.min(st.size() - 1, (int) gn("postura")))), v -> {
            data.addProperty("postura", st.indexOf(v));
            sendSet("postura", String.valueOf(st.indexOf(v)));
        }, null);
        y += ROW;
        y = tg(y, "fijar_giro", "Cuerpo mirando a un ángulo fijo");
        y = row1(y, "b_giro", "Fijar giro hacia donde miro", 0xFFE59A1F, () -> act("fijar_giro"));
        y = tg(y, "invulnerable", "Invulnerable");
        y = tg(y, "empujable", "Se deja empujar");
        dd(y, "hostil", "Ataca a", List.of("ninguno", "jugadores", "mobs", "ambos"), false);
        y += ROW;
        y = tg(y, "represalia", "Se defiende si lo atacan");
        y = tf(y, "faccion", "Facción (aliados)");
        y = tg(y, "persistente", "No desaparece nunca");
        return y;
    }

    private int secAttributes(int y) {
        header(y, "ATRIBUTOS");
        y += 16;
        y = sl(y, "vida", "Vida", " ♥", 1, 100, 1, 1024, 1, 20, false);
        y = sl(y, "velocidad", "Velocidad", "", 0.1, 0.6, 0.02, 1.5, 0.01, 0.25, false);
        y = sl(y, "dano", "Daño", "", 0, 20, 0, 200, 0.5, 3, false);
        y = sl(y, "armadura", "Armadura", "", 0, 20, 0, 30, 1, 0, false);
        y = sl(y, "empuje_res", "Resistencia al empuje", "", 0, 1, 0, 1, 0.05, 0, false);
        y = sl(y, "rango_vision", "Rango de visión", " bl", 8, 64, 4, 128, 1, 24, false);
        y = sl(y, "regeneracion", "Regeneración", " ♥/s", 0, 5, 0, 20, 0.1, 0, false);
        return y;
    }

    private int secEquipment(int y) {
        header(y, "EQUIPO Y ARMAS");
        y += 16;
        y = row2(y, "eq1", "Copiar mi mano", 0xFFB45BE0, () -> equip("mano"), "Copiar mano 2", 0xFFB45BE0, () -> equip("mano2"));
        y = row2(y, "eq2", "Copiar mi armadura", 0xFFB45BE0, () -> equip("armadura"), "Vaciar equipo", 0xFFD42A3A, () -> act("vaciar_equipo"));
        y = tg(y, "suelta_equipo", "Suelta el equipo al morir");
        boolean tacz = cat.has("tacz") && cat.get("tacz").getAsBoolean();
        label(LEFT, y, tacz ? "TACZ detectado: dale un arma de TACZ en la mano y dispara" : "TACZ no está instalado (las armas no disparan)", tacz ? COLOR_OK : COLOR_DIM);
        y += 12;
        y = sl(y, "arma_alcance", "Alcance de disparo", " bl", 8, 64, 4, 128, 1, 24, true);
        y = sl(y, "arma_cadencia", "Cadencia (ticks)", "", 2, 40, 1, 200, 1, 6, true);
        y = sl(y, "arma_dispersion", "Puntería (error)", "°", 0, 10, 0, 30, 0.5, 2, false);
        y = tg(y, "arma_infinita", "Munición infinita");
        return y;
    }

    private void equip(String que) {
        JsonObject o = new JsonObject();
        o.addProperty("op", "equipo");
        o.addProperty("que", que);
        send(o);
    }

    private int secDialogue(int y) {
        header(y, "DIÁLOGO");
        y += 16;
        dd(y, "dialogo", "Archivo de diálogo", list(cat, "dialogos"), true);
        y += ROW;
        y = tf(y, "lineas", "Líneas simples (separa con |)");
        y = tg(y, "burbuja", "Burbuja sobre la cabeza");
        y = tg(y, "chat", "Escribir en el chat");
        y = tg(y, "mirar_al_hablar", "Mira a quien le habla");
        y = sl(y, "velocidad_texto", "Velocidad del texto", "%", 50, 200, 20, 400, 5, 100, true);
        y = tf(y, "gestos", "Gestos al hablar (coma)");
        y = tg(y, "saludo_auto", "Saluda al acercarse");
        y = sl(y, "saludo_rango", "Rango de saludo", " bl", 2, 15, 1, 40, 1, 5, true);
        y = tf(y, "saludo", "Frase de saludo");
        y = tg(y, "charla_npc", "Charla con otros NPCs cercanos");
        y = tf(y, "charla_lineas", "Frases para charlar (|)");
        y = row1(y, "b_reload", "Recargar archivos de diálogo/IA/rutinas", 0xFF2AA6C8, () -> act("recargar"));
        return y;
    }

    private int secBrain(int y) {
        header(y, "IA POR CONTEXTO");
        y += 16;
        y = tg(y, "charla_ia", "Responde a lo que le escriben");
        dd(y, "personalidad", "Personalidad", List.of("neutral", "amable", "seria", "burlona", "fria"), false);
        y += ROW;
        y = tf(y, "conocimiento", "Conocimiento (archivos, coma)");
        label(LEFT, y, "Vacío = su clave + _mundo. Habla por su nombre o a <4 bl con '?'", COLOR_DIM);
        y += 12;
        List<String> kb = list(cat, "conocimientos");
        label(LEFT, y, "Archivos: " + (kb.isEmpty() ? "(ninguno aún)" : String.join(", ", kb)), COLOR_DIM);
        y += 12;
        textField("say_text", LEFT, y, INNER, "Decir: ", () -> sayText, v -> sayText = v, TEXT_CHARS);
        y += ROW;
        y = row1(y, "b_say", "Hacer que lo diga", 0xFFE0457B, () -> {
            JsonObject o = new JsonObject();
            o.addProperty("op", "hablar");
            o.addProperty("texto", sayText);
            send(o);
        });
        return y;
    }

    private int secSounds(int y) {
        header(y, "SONIDOS");
        y += 16;
        y = tf(y, "snd_idle", "Ambiente (id)");
        y = tf(y, "snd_hurt", "Herido (id)");
        y = tf(y, "snd_death", "Muerte (id)");
        y = tf(y, "snd_talk", "Al hablar (id)");
        y = sl(y, "snd_vol", "Volumen", "", 0, 2, 0, 4, 0.05, 1, false);
        y = sl(y, "snd_tono", "Tono", "", 0.5, 2, 0.1, 3, 0.05, 1, false);
        y = sl(y, "snd_idle_cada", "Ambiente cada", " ticks", 40, 1200, 0, 6000, 20, 300, true);
        label(LEFT, y, "Ids tipo minecraft:entity.villager.ambient o de un resource pack", COLOR_DIM);
        return y + 12;
    }

    private int secRoutine(int y) {
        header(y, "RUTINA / OBJETIVOS");
        y += 16;
        dd(y, "rutina", "Rutina", list(cat, "rutinas"), true);
        y += ROW;
        y = tg(y, "rutina_auto", "Empieza sola al aparecer");
        y = row2(y, "b_rut", "Iniciar", 0xFF2EBB55, () -> {
            JsonObject o = new JsonObject();
            o.addProperty("op", "rutina");
            o.addProperty("accion", "iniciar");
            send(o);
        }, "Parar", 0xFFD42A3A, () -> {
            JsonObject o = new JsonObject();
            o.addProperty("op", "rutina");
            o.addProperty("accion", "parar");
            send(o);
        });
        label(LEFT, y, "Pasos en config/obscurenpc/rutinas/<id>.json", COLOR_DIM);
        return y + 12;
    }

    private int secActions(int y) {
        header(y, "ACCIONES");
        y += 16;
        List<String> g = new ArrayList<>(list(cat, "gestos"));
        g.addAll(list(cat, "posturas_gesto"));
        g.addAll(List.of("comer", "beber", "cavar", "sentarse", "saltar"));
        dropdown("d_gesto", LEFT, y, INNER, "Probar gesto", g, gestoTest, v -> gestoTest = v, null);
        y += ROW;
        y = row1(y, "b_gesto", "▶ Reproducir gesto", 0xFFE5C21F, () -> {
            JsonObject o = new JsonObject();
            o.addProperty("op", "gesto");
            o.addProperty("id", gestoTest);
            send(o);
        });
        if (!isSpawner) {
            y = row1(y, "b_dlg", "Probar el diálogo conmigo", 0xFF2AA6C8, () -> act("dialogo"));
            y = row2(y, "b_tp1", "Traer NPC a mí", 0xFF2F7FEA, () -> act("tp_aqui"), "Ir al NPC", 0xFF2F7FEA, () -> act("tp_a_el"));
            y = row2(y, "b_dup", "Duplicar", 0xFF2EBB55, () -> act("duplicar"), "BORRAR", 0xFFD42A3A, () -> act("borrar"));
        }
        y = row2(y, "b_tpl", "Copiar plantilla al ítem", 0xFFB45BE0, () -> act("plantilla"), "Pegar plantilla", 0xFFB45BE0, () -> act("plantilla_aplicar"));
        label(LEFT, y, "Los cambios se guardan al instante", COLOR_DIM);
        return y + 12;
    }

    // ------------------------------------------------------------------ envio con retardo de sliders ----
    @Override
    protected void renderContent(GuiGraphics graphics, Font font) {
        if (pending.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (var it = pending.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (now - e.getValue() > 300) {
                sendSet(e.getKey(), pendingVal.getOrDefault(e.getKey(), "0"));
                it.remove();
            }
        }
    }
}
