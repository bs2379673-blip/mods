package com.tunombre.obscurenpc;

import com.google.gson.JsonArray;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contexto en texto libre de un NPC (config/obscurenpc/contexto/&lt;clave&gt;.txt) mas el compartido (_mundo.txt).
 * Se pega todo el lore/personalidad como texto normal; aqui se separa en datos que la "IA" sin internet usa para responder:
 * frases sueltas (hechos), pares P:/R:, saludos/despedidas propios, muletillas y la personalidad.
 */
public final class Ctx {
    private static final Random RND = new Random();
    public static final int MAX_CHARS = 60000;

    public static final class Fact {
        final String text;
        final String section;
        final Set<String> stems;
        final Set<String> secStems;
        final JsonArray cond;
        final int para;
        int index;

        Fact(String text, String section, Set<String> stems, Set<String> secStems, JsonArray cond, int para) {
            this.text = text;
            this.section = section;
            this.stems = stems;
            this.secStems = secStems;
            this.cond = cond;
            this.para = para;
        }
    }

    static final class Qa {
        final Set<String> stems;
        final List<String> answers = new ArrayList<>();
        final JsonArray cond;

        Qa(Set<String> stems, JsonArray cond) {
            this.stems = stems;
            this.cond = cond;
        }
    }

    public static final class Data {
        final List<Fact> facts = new ArrayList<>();
        final List<Fact> profile = new ArrayList<>();
        final List<Qa> qa = new ArrayList<>();
        final Map<String, List<String>> lines = new HashMap<>();
        final StringBuilder style = new StringBuilder();
        final Map<String, Integer> df = new HashMap<>();
        String personality;
        int sentences;
        int pairs;

        boolean isEmpty() { return facts.isEmpty() && qa.isEmpty() && lines.isEmpty() && profile.isEmpty(); }
    }

    private static final class Cached {
        long stamp = -1;
        Data data;
    }

    private static final Map<String, Cached> CACHE = new HashMap<>();

    private Ctx() { }

    // ------------------------------------------------------------------ archivos ----
    public static Path dir() { return State.dir().resolve("contexto"); }

    private static Path file(String key, boolean shared) { return dir().resolve((shared ? "_mundo" : safe(key)) + ".txt"); }

    public static String safe(String key) {
        String k = key == null ? "" : key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return k.isEmpty() ? "npc" : k;
    }

    public static String read(String key, boolean shared) {
        try {
            Path f = file(key, shared);
            return Files.isRegularFile(f) ? Files.readString(f, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }

    /** @return resumen del analisis */
    public static String write(String key, boolean shared, String text) {
        if (text.length() > MAX_CHARS) text = text.substring(0, MAX_CHARS);
        try {
            Path f = file(key, shared);
            Files.createDirectories(f.getParent());
            if (text.isBlank()) Files.deleteIfExists(f);
            else Files.writeString(f, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "No se pudo guardar el contexto";
        }
        CACHE.clear();
        Data d = merge(parse(text), null);
        return text.isBlank() ? "Contexto vaciado" : "Contexto guardado: " + d.sentences + " datos, " + d.pairs + " pares P/R"
                + (d.personality != null ? ", personalidad " + d.personality : "");
    }

    public static void reload() { CACHE.clear(); }

    public static Data get(String key) {
        Path a = file(key, false), b = file(key, true);
        long stamp = 0;
        try {
            if (Files.isRegularFile(a)) stamp += Files.getLastModifiedTime(a).toMillis();
            if (Files.isRegularFile(b)) stamp += Files.getLastModifiedTime(b).toMillis() * 3;
        } catch (IOException ignored) { }
        if (stamp == 0) return null;
        Cached c = CACHE.computeIfAbsent(key, k -> new Cached());
        if (c.stamp != stamp) {
            c.stamp = stamp;
            Data own = parse(read(key, false));
            Data shared = parse(read(key, true));
            c.data = merge(own, shared);
        }
        return c.data != null && !c.data.isEmpty() ? c.data : null;
    }

    // ------------------------------------------------------------------ analisis ----
    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
            "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "que", "y", "o", "a", "en", "es", "me", "te", "se", "lo", "al", "por", "para", "con",
            "mi", "tu", "su", "si", "no", "como", "eso", "esto", "hay", "ha", "son", "era", "fue", "quien", "quienes", "cual", "cuales", "cuando", "donde", "porque",
            "puedes", "puede", "sabes", "dime", "cuentame", "contame", "podrias", "algo", "sobre", "acerca", "hablame", "hablar", "decir", "dices", "tienes", "tiene",
            "hacer", "haces", "eres", "soy", "estas", "esta", "estoy", "yo", "vos", "usted", "ellos", "nos", "les", "le", "mas", "muy", "pero", "tambien", "ya", "ser",
            "estar", "tener", "haber", "sabe", "saber", "nada", "todo", "todos", "toda", "hola", "oye", "pues", "entonces", "bueno", "sus", "mis", "tus", "este", "esta",
            "estos", "estas", "ese", "esa", "esos", "esas", "aqui", "alli", "ahi", "cosa", "cosas", "tipo", "ahora", "solo", "cada", "otro", "otra", "mucho", "poco",
            "dicen", "dijo", "puedo", "pueden", "debe", "deben", "tan", "tanto", "hasta", "desde", "entre", "sin", "tras", "hacia", "ni", "e", "u", "fueron", "ser",
            "sido", "siendo", "tiene", "tienen", "tenia", "habia", "hubo", "van", "va", "voy", "vas"));

    private static final Set<String> RULE_START = new HashSet<>(Arrays.asList(
            "nunca", "jamas", "evita", "evitar", "debes", "deberas", "responde", "responder", "habla", "hablas", "actua", "comportate", "siempre", "recuerda",
            "contesta", "contestar", "intenta", "trata", "manten", "mantente", "usa", "utiliza", "dirigete"));
    private static final Set<String> RULE_NO = new HashSet<>(Arrays.asList("reveles", "digas", "menciones", "hables", "respondas", "cuentes", "contestes", "uses", "olvides", "rompas"));

    private static final Pattern DIRECTIVE = Pattern.compile("^([\\p{L} ]{1,26}?)\\s*:\\s*(.+)$");
    private static final Pattern SENT = Pattern.compile("(?<=[.!?…])\\s+");

    static String norm(String s) { return Brain.norm(s); }

    static String stem(String w) {
        String s = w;
        for (String suf : new String[]{"ando", "iendo", "ciones", "cion", "mente", "ados", "adas", "idos", "idas", "ado", "ada", "ido", "ida", "ar", "er", "ir", "os", "as", "es", "s"}) {
            if (s.length() > suf.length() + 3 && s.endsWith(suf)) { s = s.substring(0, s.length() - suf.length()); break; }
        }
        return s;
    }

    static Set<String> stems(String normText) {
        Set<String> out = new LinkedHashSet<>();
        for (String w : normText.split(" ")) {
            if (w.length() < 3 || STOP.contains(w)) continue;
            out.add(stem(w));
        }
        return out;
    }

    private static String label(String raw) { return norm(raw); }

    private static boolean isRule(String line) {
        String[] w = norm(line).split(" ");
        if (w.length == 0) return false;
        if (RULE_START.contains(w[0])) return true;
        return w[0].equals("no") && w.length > 1 && RULE_NO.contains(w[1]);
    }

    private static String clean(String line) {
        String s = line.trim();
        s = s.replaceFirst("^(?:[-*•>]+|\\d+[.)])\\s+", "");
        return s.trim();
    }

    private static JsonArray conds(String spec) {
        JsonArray a = new JsonArray();
        if (spec == null) return a;
        for (String p : spec.split("[,;]")) if (!p.isBlank()) a.add(p.trim());
        return a;
    }

    static Data parse(String text) {
        Data d = new Data();
        if (text == null || text.isBlank()) return d;
        String section = "";
        JsonArray secCond = new JsonArray();
        int para = 0;
        String pendingQ = null;
        Qa lastQa = null;
        StringBuilder styleRaw = d.style;
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) { para++; lastQa = null; continue; }
            if (line.startsWith("//")) continue;
            // encabezado: "# Titulo", "[Titulo]" o "Titulo:" solo
            String head = null;
            if (line.startsWith("#")) head = line.replaceFirst("^#+\\s*", "");
            else if (line.matches("^\\[[^\\]]+\\](\\s*\\|.*)?$")) head = line.replaceFirst("^\\[", "").replaceFirst("\\]", "");
            else if (line.endsWith(":") && line.length() <= 60 && !line.contains(". ")) head = line.substring(0, line.length() - 1);
            if (head != null) {
                String cond = null;
                int bar = head.indexOf('|');
                if (bar >= 0) { cond = head.substring(bar + 1).replaceFirst("(?i)^\\s*si\\s*:?", ""); head = head.substring(0, bar); }
                Matcher pm = Pattern.compile("\\((?i:si)\\s*:?\\s*([^)]*)\\)").matcher(head);
                if (pm.find()) { cond = pm.group(1); head = head.replace(pm.group(0), ""); }
                section = head.trim();
                secCond = conds(cond);
                para++;
                lastQa = null;
                continue;
            }
            Matcher m = DIRECTIVE.matcher(clean(line));
            if (m.matches()) {
                String lab = label(m.group(1));
                String val = m.group(2).trim();
                if (handleDirective(d, lab, val, styleRaw)) {
                    if (lab.equals("p") || lab.equals("pregunta") || lab.equals("pregunta frecuente")) { pendingQ = val; lastQa = null; }
                    else if (lab.equals("r") || lab.equals("respuesta")) {
                        if (pendingQ != null) {
                            Set<String> st = stems(norm(pendingQ));
                            if (!st.isEmpty()) {
                                lastQa = new Qa(st, secCond);
                                lastQa.answers.add(val);
                                d.qa.add(lastQa);
                                d.pairs++;
                            }
                        }
                    }
                    continue;
                }
            }
            String s = clean(line);
            if (lastQa != null && !isRule(s)) { lastQa.answers.set(lastQa.answers.size() - 1, lastQa.answers.get(lastQa.answers.size() - 1) + " " + s); continue; }
            if (isRule(s)) { styleRaw.append(' ').append(s); continue; }
            for (String sent : SENT.split(s)) {
                String t = sent.trim();
                if (t.length() < 10) continue;
                boolean[] second = new boolean[1];
                t = firstPerson(t, second);
                Set<String> st = stems(norm(t));
                String secNorm = norm(section);
                Fact f = new Fact(t, section, st, stems(secNorm), secCond, para);
                f.index = d.facts.size();
                d.facts.add(f);
                d.sentences++;
                if (second[0] || secNorm.contains("identidad") || secNorm.contains("quien soy") || secNorm.contains("sobre ti") || secNorm.contains("perfil")
                        || secNorm.equals("yo") || secNorm.contains("personaje")) d.profile.add(f);
            }
        }
        return d;
    }

    /** true si la linea era una directiva reconocida (y ya se proceso) */
    private static boolean handleDirective(Data d, String lab, String val, StringBuilder style) {
        switch (lab) {
            case "personalidad", "tono", "caracter", "estilo", "forma de hablar", "como hablas", "como hablar", "actitud", "temperamento" -> { style.append(' ').append(val); return true; }
            case "saludo", "saludos" -> { addLines(d, "saludos", val); return true; }
            case "despedida", "despedidas" -> { addLines(d, "despedidas", val); return true; }
            case "gracias", "agradecimiento" -> { addLines(d, "gracias", val); return true; }
            case "insulto", "insultos", "si te insultan" -> { addLines(d, "insultos", val); return true; }
            case "no entiendo", "desconocido", "no se", "no sabe", "si no sabes" -> { addLines(d, "no_entiendo", val); return true; }
            case "obedece", "acepta seguir", "sigue", "al seguir" -> { addLines(d, "o_seguir", val); return true; }
            case "espera", "se queda", "al quedarse" -> { addLines(d, "o_quieto", val); return true; }
            case "protege", "al proteger" -> { addLines(d, "o_proteger", val); return true; }
            case "guardia", "zona", "al defender" -> { addLines(d, "o_zona", val); return true; }
            case "libre", "al soltarlo" -> { addLines(d, "o_libre", val); return true; }
            case "rechaza", "no obedece", "rechazo" -> { addLines(d, "o_rechazo", val); addLines(d, "o_condicion", val); return true; }
            case "chiste", "chistes" -> { addLines(d, "chistes", val); return true; }
            case "muletilla", "muletillas", "frase tipica", "frases tipicas", "coletilla" -> { addLines(d, "muletillas", val); return true; }
            case "p", "pregunta", "pregunta frecuente", "r", "respuesta" -> { return true; }
            case "nombre" -> { return true; }
            default -> { return false; }
        }
    }

    private static void addLines(Data d, String key, String val) {
        List<String> l = d.lines.computeIfAbsent(key, k -> new ArrayList<>());
        for (String s : val.split("\\|")) if (!s.isBlank()) l.add(s.trim());
    }

    private static final String[][] SECOND = {
            {"tu nombre es ", "Mi nombre es "}, {"te llamas ", "Me llamo "}, {"tu eres ", "Soy "}, {"tú eres ", "Soy "}, {"eres ", "Soy "}, {"tienes ", "Tengo "},
            {"estás ", "Estoy "}, {"estas ", "Estoy "}, {"sabes ", "Sé "}, {"puedes ", "Puedo "}, {"quieres ", "Quiero "}, {"trabajas ", "Trabajo "}, {"vives ", "Vivo "},
            {"odias ", "Odio "}, {"amas ", "Amo "}, {"buscas ", "Busco "}, {"investigas ", "Investigo "}, {"estudias ", "Estudio "}, {"sueñas ", "Sueño "},
            {"temes ", "Temo "}, {"crees ", "Creo "}, {"conoces ", "Conozco "}, {"intentas ", "Intento "}, {"necesitas ", "Necesito "}, {"tu ", "Mi "}, {"tus ", "Mis "}
    };

    /** "Eres una cientifica" -> "Soy una cientifica" (el texto del contexto suele estar escrito en segunda persona) */
    static String firstPerson(String s, boolean[] converted) {
        String low = s.toLowerCase(Locale.ROOT);
        for (String[] r : SECOND) {
            if (low.startsWith(r[0])) {
                String rest = s.substring(r[0].length());
                rest = rest.replaceAll("(?i)\\btus\\b", "mis").replaceAll("(?i)\\btu\\b", "mi").replaceAll("(?i)\\bte\\b", "me").replaceAll("(?i)\\bti\\b", "mí");
                converted[0] = true;
                return r[1] + rest;
            }
        }
        return s;
    }

    private static Data merge(Data own, Data shared) {
        Data d = new Data();
        for (Data x : new Data[]{own, shared}) {
            if (x == null) continue;
            for (Fact f : x.facts) { f.index = d.facts.size(); d.facts.add(f); }
            d.profile.addAll(x.profile);
            d.qa.addAll(x.qa);
            for (var e : x.lines.entrySet()) d.lines.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).addAll(e.getValue());
            if (x.style.length() > 0) d.style.append(' ').append(x.style);
            d.sentences += x.sentences;
            d.pairs += x.pairs;
        }
        for (Fact f : d.facts) for (String s : f.stems) d.df.merge(s, 1, Integer::sum);
        d.personality = detectPersonality(norm(d.style.toString()));
        if (own != null && own.style.length() > 0) {
            String p = detectPersonality(norm(own.style.toString()));
            if (p != null) d.personality = p;
        }
        return d;
    }

    private static String detectPersonality(String s) {
        if (s.isBlank()) return null;
        String[][] keys = {
                {"burlona", "burl", "sarcas", "ironic", "bromist", "chistos", "gracios", "travies"},
                {"fria", "frio", "fria", "distante", "cortante", "hostil", "borde", "arrogante", "seco ", "seca "},
                {"seria", "serio", "seria", "formal", "militar", "estricto", "disciplin", "directo", "directa", "profesional"},
                {"amable", "amable", "calid", "simpatic", "alegre", "cordial", "dulce", "entusiast", "energic", "carismatic", "curiosa", "curioso", "extrovert"}
        };
        int bestPos = Integer.MAX_VALUE;
        String best = null;
        for (String[] k : keys) {
            for (int i = 1; i < k.length; i++) {
                int p = s.indexOf(k[i]);
                if (p >= 0 && p < bestPos) { bestPos = p; best = k[0]; }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ uso ----
    public static String personality(Data c, Actor npc) { return c != null && c.personality != null ? c.personality : npc.data().personalidad; }

    public static String line(Data c, String key, String avoid) {
        if (c == null) return null;
        List<String> l = c.lines.get(key);
        if (l == null || l.isEmpty()) return null;
        for (int i = 0; i < 4; i++) {
            String s = l.get(RND.nextInt(l.size()));
            if (l.size() == 1 || !s.equals(avoid)) return s;
        }
        return l.get(0);
    }

    private static double idf(Data c, String stem) {
        int df = c.df.getOrDefault(stem, 0);
        if (df == 0) return 0;
        return 0.5 + Math.log(1.0 + (double) c.facts.size() / df);
    }

    private static boolean match(String a, String b) {
        if (a.equals(b)) return true;
        return a.length() >= 5 && b.length() >= 5 && (a.startsWith(b) || b.startsWith(a));
    }

    private static double weight(Set<String> q, Set<String> against) {
        double sc = 0;
        for (String x : q) {
            if (against.contains(x)) { sc += 1; continue; }
            for (String y : against) if (match(x, y)) { sc += 0.6; break; }
        }
        return sc;
    }

    /** @return una respuesta sacada del contexto, o null si no hay nada que se parezca a la pregunta */
    public static Brain.Reply answer(Data c, Actor npc, ServerPlayer speaker, UUID who, String text, boolean followUp, String intent) {
        if (c == null) return null;
        Set<String> q = stems(text);
        String avoid = npc.mem(who).getString("ultima");

        // 1) preguntas/respuestas escritas a mano
        Qa bestQa = null;
        double bq = 0;
        for (Qa a : c.qa) {
            if (a.cond.size() > 0 && !Fmt.test(npc, speaker, a.cond)) continue;
            double w = weight(q, a.stems);
            double cover = w / Math.max(1, a.stems.size());
            if (w >= 1 && cover >= 0.6 && cover + w * 0.05 > bq) { bq = cover + w * 0.05; bestQa = a; }
        }
        if (bestQa != null) {
            String ans = bestQa.answers.get(RND.nextInt(bestQa.answers.size()));
            return done(c, npc, who, ans, -1);
        }

        // 2) quien eres / que haces: frases en primera persona
        if ((intent != null && (intent.equals("identidad") || intent.equals("ocupacion"))) && !c.profile.isEmpty()) {
            List<Fact> ok = new ArrayList<>();
            for (Fact f : c.profile) if (f.cond.size() == 0 || Fmt.test(npc, speaker, f.cond)) ok.add(f);
            if (!ok.isEmpty()) {
                int n = npc.counter(who, "perfil_n");
                npc.addCounter(who, "perfil_n", 1);
                Fact f = ok.get(n % ok.size());
                return say(c, npc, who, join(c, f, 1));
            }
        }

        // 3) "¿y eso?", "cuentame mas": sigue por donde iba
        int last = npc.mem(who).contains("ctx_last") ? npc.mem(who).getInt("ctx_last") : -1;
        if (followUp && q.isEmpty() && last >= 0 && last < c.facts.size()) {
            Fact lf = c.facts.get(last);
            for (int i = last + 1; i < Math.min(c.facts.size(), last + 4); i++) {
                Fact n = c.facts.get(i);
                if ((n.para == lf.para || n.section.equals(lf.section)) && (n.cond.size() == 0 || Fmt.test(npc, speaker, n.cond))) return say(c, npc, who, join(c, n, 1));
            }
            return new Brain.Reply(pickNoMore(c, npc, who), null);
        }

        // 4) hecho mas parecido a la pregunta
        if (q.isEmpty()) return null;
        Fact best = null;
        double bs = 0;
        for (Fact f : c.facts) {
            if (f.cond.size() > 0 && !Fmt.test(npc, speaker, f.cond)) continue;
            double sc = 0;
            for (String x : q) {
                if (f.stems.contains(x)) sc += idf(c, x);
                else {
                    for (String y : f.stems) if (match(x, y)) { sc += 0.6 * Math.min(idf(c, x), idf(c, y)); break; }
                }
            }
            double sec = weight(q, f.secStems);
            if (sec > 0) sc += 1.5 * Math.min(2, sec);
            // un poco de preferencia por frases cortas y claras
            sc = sc / (1.0 + Math.max(0, f.stems.size() - 10) * 0.03);
            if (sc > bs + 1e-9) { bs = sc; best = f; }
        }
        if (best == null || bs < 1.8) return null;
        // si repite la misma pregunta, avanza al siguiente dato del mismo tema
        if (npc.mem(who).contains("ctx_last") && npc.mem(who).getInt("ctx_last") == best.index) {
            for (int i = best.index + 1; i < Math.min(c.facts.size(), best.index + 4); i++) {
                Fact n = c.facts.get(i);
                if ((n.para == best.para || n.section.equals(best.section)) && (n.cond.size() == 0 || Fmt.test(npc, speaker, n.cond))) { best = n; break; }
            }
        }
        return say(c, npc, who, join(c, best, 1));
    }

    private record Joined(String text, int last) { }

    private static Joined join(Data c, Fact f, int extra) {
        StringBuilder sb = new StringBuilder(f.text);
        int i = f.index;
        int last = i;
        if (sb.length() < 90) {
            for (int k = 1; k <= extra && i + k < c.facts.size(); k++) {
                Fact n = c.facts.get(i + k);
                if (n.para != f.para || n.cond.size() > 0) break;
                sb.append(' ').append(n.text);
                last = i + k;
            }
        }
        return new Joined(sb.toString(), last);
    }

    private static Brain.Reply say(Data c, Actor npc, UUID who, Joined j) { return done(c, npc, who, j.text(), j.last()); }

    private static String pickNoMore(Data c, Actor npc, UUID who) {
        String custom = line(c, "no_entiendo", null);
        return custom != null ? custom : "No tengo mucho más que decir sobre eso.";
    }

    private static Brain.Reply done(Data c, Actor npc, UUID who, String text, int factIndex) {
        if (factIndex >= 0) npc.mem(who).putInt("ctx_last", factIndex);
        List<String> mul = c.lines.get("muletillas");
        if (mul != null && !mul.isEmpty() && RND.nextInt(100) < 25) {
            String m = mul.get(RND.nextInt(mul.size()));
            text = RND.nextBoolean() ? m + " " + text : text + " " + m;
        }
        npc.mem(who).putString("ultima", text);
        return new Brain.Reply(text, null);
    }

    /** texto de plantilla que el editor puede insertar */
    public static final String TEMPLATE = """
            # Quién soy
            Eres Hange, una científica obsesionada con los infectados, pero del lado de la humanidad.
            Te llamas Hange y trabajas investigando la Mortifilia.
            Tienes una energía desbordante y te emocionas con todo lo raro.

            Personalidad: curiosa, enérgica, algo excéntrica, habla rápido y con entusiasmo.
            Muletillas: ¡Fascinante! | Anota eso, anota eso.
            Saludo: ¡Un sujeto nuevo! Digo... un superviviente. | ¡Justo a tiempo, {jugador}!
            Despedida: Vuelve vivo, me debes datos. | Cuídate, {jugador}.
            No entiendo: No tengo datos sobre eso... todavía. | Eso se me escapa. Y odio que algo se me escape.

            # La Mortifilia
            La Mortifilia es una flor con propiedades regenerativas absurdas.
            Cura casi cualquier herida, pero cobra su precio.
            Los infectados son lo que pasa cuando la cepa gana.

            # La isla
            Estamos en una isla usada como zona de pruebas por el gobierno.
            Hay instalaciones abandonadas, no es buena idea entrar sin saber lo que haces.

            # Secreto de los jugadores (si afinidad>=4)
            Todos ustedes están infectados, aunque no lo saben todavía.
            Por eso regeneran al morir y por eso los trajeron aquí.

            P: ¿Qué es lo que más te gusta?
            R: Estudiar a los infectados de cerca. Con cuidado, claro. Más o menos.
            """;
}
