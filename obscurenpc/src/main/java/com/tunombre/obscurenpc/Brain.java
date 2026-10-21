package com.tunombre.obscurenpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * "IA" sin internet: responde preguntas libres a partir de una base de conocimiento por NPC (config/obscurenpc/conocimiento/*.json)
 * y de su contexto (hora, clima, lo que ya hablaron, banderas, afinidad, personalidad). Busca temas por palabras clave con
 * puntaje, recuerda de que se hablo para contestar "¿y eso?" y avanza por respuestas cada vez mas profundas.
 */
public final class Brain {
    private static final Random RND = new Random();

    public record Reply(String text, String gesture) { }

    private static final class Kb {
        long stamp;
        JsonObject root;
    }

    private static final Map<String, Kb> CACHE = new HashMap<>();

    private Brain() { }

    public static void reload() { CACHE.clear(); }

    public static List<String> fileIds() {
        List<String> l = new ArrayList<>();
        try (var s = Files.isDirectory(dir()) ? Files.list(dir()) : java.util.stream.Stream.<Path>empty()) {
            s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json")).forEach(n -> l.add(n.substring(0, n.length() - 5)));
        } catch (IOException ignored) { }
        java.util.Collections.sort(l);
        return l;
    }

    private static Path dir() { return State.dir().resolve("conocimiento"); }

    private static JsonObject load(String id) {
        Path f = dir().resolve(id + ".json");
        try {
            if (!Files.isRegularFile(f)) return null;
            long st = Files.getLastModifiedTime(f).toMillis();
            Kb k = CACHE.get(id);
            if (k != null && k.stamp == st) return k.root;
            k = new Kb();
            k.stamp = st;
            k.root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            CACHE.put(id, k);
            return k.root;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static List<JsonObject> sources(Actor npc) {
        List<JsonObject> l = new ArrayList<>();
        String cfg = npc.data().conocimiento;
        List<String> ids = new ArrayList<>();
        if (cfg != null && !cfg.isBlank()) for (String s : cfg.split(",")) ids.add(s.trim().toLowerCase(Locale.ROOT));
        else { ids.add(npc.data().key()); ids.add("_mundo"); }
        for (String id : ids) {
            JsonObject o = load(id);
            if (o != null) l.add(o);
        }
        return l;
    }

    // ------------------------------------------------------------------ texto ----
    public static String norm(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.replaceAll("[^a-z0-9ñ ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static final Set<String> STOP = new HashSet<>(Arrays.asList("el", "la", "los", "las", "un", "una", "de", "del", "que", "y", "o", "a", "en", "es",
            "me", "te", "se", "lo", "al", "por", "para", "con", "mi", "tu", "su", "si", "no", "como", "eso", "esto", "hay", "ha", "son", "era", "fue"));

    private static List<String> tokens(String normText) {
        List<String> t = new ArrayList<>();
        for (String w : normText.split(" ")) if (!w.isEmpty()) t.add(w);
        return t;
    }

    private static double score(String text, List<String> toks, JsonObject topic) {
        double sc = 0;
        for (JsonElement e : arr(topic, "claves")) {
            String k = norm(e.getAsString());
            if (k.isEmpty()) continue;
            if (k.indexOf(' ') >= 0) {
                if ((" " + text + " ").contains(" " + k + " ")) sc += 3.0;
                continue;
            }
            for (String w : toks) {
                if (w.equals(k)) { sc += 2.5; break; }
                if (w.length() >= 4 && k.length() >= 4 && (w.startsWith(k) || k.startsWith(w) || stem(w).equals(stem(k)))) { sc += 1.5; break; }
            }
        }
        double prio = topic.has("prioridad") ? topic.get("prioridad").getAsDouble() : 1.0;
        return sc * prio;
    }

    private static String stem(String w) {
        String s = w;
        for (String suf : new String[]{"ando", "iendo", "ciones", "cion", "mente", "ados", "adas", "idos", "idas", "ado", "ada", "ido", "ida", "ar", "er", "ir", "os", "as", "es", "s"}) {
            if (s.length() > suf.length() + 3 && s.endsWith(suf)) { s = s.substring(0, s.length() - suf.length()); break; }
        }
        return s;
    }

    private static JsonArray arr(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray();
    }

    private static String pick(JsonArray a, String avoid) {
        if (a.size() == 0) return null;
        for (int i = 0; i < 4; i++) {
            String s = a.get(RND.nextInt(a.size())).getAsString();
            if (a.size() == 1 || !s.equals(avoid)) return s;
        }
        return a.get(0).getAsString();
    }

    private static String pick(String[] a) { return a[RND.nextInt(a.length)]; }

    // ------------------------------------------------------------------ respuesta ----
    /**
     * @param speaker quien habla (jugador); para charla entre NPCs se pasa null y el id del que habla en {@code fromId}
     */
    public static Reply reply(Actor npc, ServerPlayer speaker, UUID fromId, String message) {
        NpcData d = npc.data();
        UUID who = speaker != null ? speaker.getUUID() : fromId;
        String text = norm(message);
        // quitar el nombre del NPC si le hablan por su nombre
        String nm = norm(d.nombre);
        if (!nm.isEmpty()) text = (" " + text + " ").replace(" " + nm + " ", " ").trim();
        List<String> toks = tokens(text);
        List<JsonObject> srcs = sources(npc);

        // 1) temas de los archivos de conocimiento
        JsonObject best = null;
        double bs = 0;
        for (JsonObject s : srcs) {
            for (JsonElement e : arr(s, "temas")) {
                JsonObject t = e.getAsJsonObject();
                if (t.has("si") && !Fmt.test(npc, speaker, t.get("si"))) continue;
                double sc = score(text, toks, t);
                if (sc > bs) { bs = sc; best = t; }
            }
        }
        String lastTopic = npc.mem(who).getString("ultimo_tema");
        boolean followUp = isFollowUp(toks);
        if (best != null && bs >= 2.0) return answerTopic(npc, speaker, who, best);
        if (followUp && !lastTopic.isEmpty()) {
            for (JsonObject s : srcs) for (JsonElement e : arr(s, "temas")) {
                JsonObject t = e.getAsJsonObject();
                if (t.has("id") && t.get("id").getAsString().equals(lastTopic)) return answerTopic(npc, speaker, who, t);
            }
        }

        // 2) intenciones basicas (con la personalidad del NPC)
        String intent = intent(text, toks);
        if (intent != null) {
            String custom = null;
            for (JsonObject s : srcs) { custom = pick(arr(s, intent), npc.mem(who).getString("ultima")); if (custom != null) break; }
            if (intent.equals("gracias")) npc.addCounter(who, "afinidad", 1);
            if (intent.equals("insultos")) npc.addCounter(who, "afinidad", -2);
            String line = custom != null ? custom : builtin(npc, intent, who);
            npc.mem(who).putString("ultima", line);
            return new Reply(line, null);
        }

        // 3) nada claro: respuestas de "no entiendo" del NPC
        String none = null;
        for (JsonObject s : srcs) { none = pick(arr(s, "no_entiendo"), npc.mem(who).getString("ultima")); if (none != null) break; }
        if (none == null) none = pick(personalityLines(d.personalidad, "no_entiendo"));
        npc.mem(who).putString("ultima", none);
        return new Reply(none, null);
    }

    private static boolean isFollowUp(List<String> t) {
        if (t.size() > 5) return false;
        for (String w : t) if (w.equals("eso") || w.equals("mas") || w.equals("explica") || w.equals("explicame") || w.equals("porque") || w.equals("cuenta")
                || w.equals("sigue") || w.equals("continua") || w.equals("entonces") || w.equals("cual") || w.equals("cuentame") || w.equals("detalles")) return true;
        return false;
    }

    private static Reply answerTopic(Actor npc, ServerPlayer speaker, UUID who, JsonObject t) {
        String id = t.has("id") ? t.get("id").getAsString() : "tema";
        int n = npc.counter(who, "tema_" + id);
        npc.addCounter(who, "tema_" + id, 1);
        npc.mem(who).putString("ultimo_tema", id);
        JsonArray more = arr(t, "mas");
        String line;
        if (n > 0 && more.size() > 0) line = more.get(Math.min(n - 1, more.size() - 1)).getAsString();
        else line = pick(arr(t, "respuestas"), npc.mem(who).getString("ultima"));
        if (line == null) line = "...";
        if (n > more.size() + 1 && more.size() > 0) {
            // ya se agotaron: a veces repite lo ya dicho en lugar de la ultima
            String alt = pick(more, line);
            if (alt != null && RND.nextBoolean()) line = alt;
        }
        npc.mem(who).putString("ultima", line);
        if (t.has("pone")) for (JsonElement e : arr(t, "pone")) if (speaker != null) npc.setFlag(who, e.getAsString());
        if (t.has("gpone")) for (JsonElement e : arr(t, "gpone")) State.set(e.getAsString());
        if (t.has("cmd")) for (JsonElement e : arr(t, "cmd")) Fmt.runCommand(npc, e.getAsString(), speaker);
        if (t.has("afinidad")) npc.addCounter(who, "afinidad", t.get("afinidad").getAsInt());
        return new Reply(line, t.has("gesto") ? t.get("gesto").getAsString() : null);
    }

    private static boolean has(List<String> toks, String text, String... keys) {
        for (String k : keys) {
            if (k.indexOf(' ') >= 0) { if ((" " + text + " ").contains(" " + k + " ")) return true; }
            else if (toks.contains(k)) return true;
        }
        return false;
    }

    private static String intent(String text, List<String> t) {
        if (has(t, text, "hola", "buenas", "buen dia", "buenos dias", "buenas tardes", "buenas noches", "saludos", "ey", "epa", "hey", "que tal", "ola")) return "saludos";
        if (has(t, text, "adios", "chau", "chao", "hasta luego", "nos vemos", "me voy", "bye", "cuidate")) return "despedidas";
        if (has(t, text, "gracias", "te agradezco", "grac", "muy amable")) return "gracias";
        if (has(t, text, "idiota", "estupido", "imbecil", "inutil", "basura", "callate", "cállate", "tonto", "maldito", "hijo de", "puto", "mierda")) return "insultos";
        if (has(t, text, "quien eres", "quien sos", "como te llamas", "tu nombre", "cual es tu nombre", "presentate", "quien sois")) return "identidad";
        if (has(t, text, "como estas", "como andas", "como te sientes", "todo bien", "que tal estas", "como vas")) return "estado";
        if (has(t, text, "que hora", "que hora es", "hora es", "es de dia", "es de noche")) return "hora";
        if (has(t, text, "clima", "llueve", "lluvia", "tormenta", "el tiempo")) return "clima";
        if (has(t, text, "donde estamos", "donde estoy", "que lugar", "donde nos encontramos", "en que lugar")) return "lugar";
        if (has(t, text, "que haces", "a que te dedicas", "que haces aqui", "tu trabajo", "que hacen aqui", "que hacemos")) return "ocupacion";
        if (has(t, text, "ayuda", "ayudame", "necesito ayuda", "auxilio", "socorro", "me puedes ayudar")) return "ayuda";
        if (has(t, text, "adonde voy", "que hago", "que debo hacer", "que puedo hacer", "objetivo", "mision")) return "consejo";
        if (has(t, text, "tienes miedo", "miedo", "asustado", "te asusta")) return "miedo";
        return null;
    }

    // ------------------------------------------------------------------ lineas por defecto segun personalidad ----
    private static String[] personalityLines(String p, String key) {
        String per = p == null ? "neutral" : p.toLowerCase(Locale.ROOT);
        switch (key) {
            case "saludos":
                return switch (per) {
                    case "amable" -> new String[]{"¡Hola, {jugador}! Qué alegría verte.", "¡Buenas, {jugador}! ¿Cómo va todo?", "Hola, hola. Pasa, pasa."};
                    case "seria" -> new String[]{"Hola, {jugador}.", "Buenas. Sea breve, por favor.", "{jugador}. Dime."};
                    case "burlona" -> new String[]{"Mira quién apareció: {jugador}.", "Ah, {jugador}. Justo me estaba aburriendo.", "¿Otra vez tú? Bueno... hola."};
                    case "fria" -> new String[]{"...Hola.", "Qué quieres.", "{jugador}."};
                    default -> new String[]{"Hola, {jugador}.", "Buenas, {jugador}.", "Hola. ¿Necesitas algo?"};
                };
            case "despedidas":
                return switch (per) {
                    case "amable" -> new String[]{"¡Cuídate mucho, {jugador}!", "¡Hasta pronto! Vuelve cuando quieras.", "Que te vaya bien."};
                    case "seria" -> new String[]{"Adiós.", "Que tenga cuidado.", "Hasta luego."};
                    case "burlona" -> new String[]{"Vete, vete, que ya me aburriste. Es broma. Cuídate.", "Chau, {jugador}. No te mueras... todavía.", "Adiós, valiente."};
                    case "fria" -> new String[]{"...", "Vete.", "Adiós."};
                    default -> new String[]{"Hasta luego, {jugador}.", "Cuídate.", "Nos vemos."};
                };
            case "gracias":
                return switch (per) {
                    case "amable" -> new String[]{"¡No hay de qué!", "Para eso estamos.", "Con gusto, {jugador}."};
                    case "seria" -> new String[]{"No tiene importancia.", "Está bien.", "De nada."};
                    case "burlona" -> new String[]{"Lo sé, soy genial.", "Anota que me debes una.", "Sí, sí, de nada."};
                    case "fria" -> new String[]{"Mm.", "No lo hice por ti.", "..."};
                    default -> new String[]{"De nada.", "No hay problema.", "Cuando quieras."};
                };
            case "insultos":
                return switch (per) {
                    case "amable" -> new String[]{"Eso me dolió, {jugador}...", "No hace falta hablar así.", "Tranquilo, respira."};
                    case "seria" -> new String[]{"Cuide sus palabras.", "No voy a tolerar eso.", "Una más y termina la conversación."};
                    case "burlona" -> new String[]{"Uy, qué miedo. Tiemblo.", "Wow, ¿eso es todo lo que tienes?", "Dilo otra vez, pero esta vez con ingenio."};
                    case "fria" -> new String[]{"...", "Eso dice más de ti que de mí.", "Terminamos aquí."};
                    default -> new String[]{"No hace falta ser grosero.", "Eso no fue necesario.", "Vamos a calmarnos."};
                };
            case "no_entiendo":
                return switch (per) {
                    case "amable" -> new String[]{"Mmm, no logro entenderte, {jugador}. ¿Me lo explicas de otra forma?", "Perdona, no sé de qué hablas. ¿Puedes repetirlo?", "Eso no lo sé, pero me encantaría ayudarte."};
                    case "seria" -> new String[]{"No tengo información sobre eso.", "No entiendo la pregunta.", "Sea más específico."};
                    case "burlona" -> new String[]{"¿Eso fue una pregunta? Porque no le encontré sentido.", "No tengo ni idea. Pregúntale a otro, yo soy decorativo.", "Qué pregunta tan... interesante. No sé qué decirte."};
                    case "fria" -> new String[]{"No sé.", "No me interesa.", "Pregunta otra cosa."};
                    default -> new String[]{"No sé qué responderte a eso.", "Eso no lo tengo claro. ¿Qué más quieres saber?", "Mmm... no tengo información sobre eso."};
                };
            default:
                return new String[]{"..."};
        }
    }

    private static String builtin(Actor npc, String intent, UUID who) {
        NpcData d = npc.data();
        String per = d.personalidad;
        String last = npc.mem(who).getString("ultima");
        switch (intent) {
            case "saludos": case "despedidas": case "gracias": case "insultos":
                String[] l = personalityLines(per, intent);
                for (int i = 0; i < 4; i++) { String s = pick(l); if (!s.equals(last) || l.length == 1) return s; }
                return l[0];
            case "identidad":
                return "Me llamo " + d.nombre + (d.faccion.isBlank() ? "." : ", y estoy con " + d.faccion + ".");
            case "estado": {
                float hp = npc.getHealth() / npc.getMaxHealth();
                String base = hp < 0.4F ? "Estoy herido, la verdad." : hp < 0.8F ? "Aguanto, aunque me duele un poco." : "Bien, dentro de lo que cabe.";
                return base + (npc.level().isThundering() ? " Con esta tormenta, nadie está tranquilo." : "");
            }
            case "hora":
                return "Es de {hora}, más o menos. Día {dia}.";
            case "clima":
                return "Hay " + "{clima}" + " por ahora.";
            case "lugar":
                return "Estamos en una zona de {bioma}. No es un buen sitio para confiarse.";
            case "ocupacion":
                return "Estoy aquí, atento a todo. En estos tiempos no se puede hacer mucho más.";
            case "ayuda":
                return "Haré lo que pueda. Dime qué necesitas, {jugador}.";
            case "consejo":
                return "Mantente con vida y no te separes del grupo. Lo demás ya se verá.";
            case "miedo":
                return d.personalidad.equals("burlona") ? "¿Miedo yo? Bueno... un poquito." : "Todos lo tenemos. Lo importante es seguir.";
            default:
                return "...";
        }
    }

    public static String greeting(Actor npc, ServerPlayer p) {
        NpcData d = npc.data();
        UUID who = p.getUUID();
        int visits = npc.counter(who, "visitas");
        for (JsonObject s : sources(npc)) {
            String g = pick(arr(s, "saludos"), npc.mem(who).getString("ultima"));
            if (g != null) return g;
        }
        String[] l = personalityLines(d.personalidad, "saludos");
        String line = pick(l);
        if (visits >= 3 && !d.personalidad.equals("fria")) line = line + " Ya van " + visits + " veces que hablamos.";
        return line;
    }

    /** una frase suelta de este NPC para hablarle a otro NPC, o la que escoja del conocimiento */
    public static Reply replyToNpc(Actor npc, Actor other, String message) {
        return reply(npc, null, other.getUUID(), message);
    }
}
