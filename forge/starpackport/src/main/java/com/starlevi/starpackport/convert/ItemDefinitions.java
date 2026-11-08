package com.starlevi.starpackport.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Desde 1.21.4 cada item tiene una "definicion" en assets/&lt;ns&gt;/items/&lt;id&gt;.json que elige el modelo
 * segun el contexto (mano, inventario...), si se esta usando, etc. 1.20.1 solo lee models/item/&lt;id&gt;.json
 * con "overrides". Esto traduce una cosa a la otra:
 * <ul>
 *   <li>"select" por display_context -> cargador forge:separate_transforms (un modelo por perspectiva)</li>
 *   <li>condiciones y rangos (arco tensado, brujula, custom_model_data...) -> overrides con predicados</li>
 *   <li>"composite" -> cargador forge:composite</li>
 * </ul>
 */
final class ItemDefinitions {
    static final String[] CTX = {"none", "thirdperson_lefthand", "thirdperson_righthand", "firstperson_lefthand",
            "firstperson_righthand", "head", "gui", "ground", "fixed"};
    private static final int GUI = 6;
    private static final Pattern DEF = Pattern.compile("^assets/([^/]+)/items/(.+)\\.json$");
    private static final Map<String, Float> TRIMS = new LinkedHashMap<>();

    static {
        String[] m = {"quartz", "iron", "netherite", "redstone", "copper", "gold", "emerald", "diamond", "lapis", "amethyst"};
        for (int i = 0; i < m.length; i++) {
            TRIMS.put("minecraft:" + m[i], (i + 1) / 10f);
        }
    }

    private final PackConverter c;
    /** modelos de item que vamos a reescribir ("ns:item/id") */
    private final Set<String> wrapped = new HashSet<>();
    private final Map<String, String> relocated = new HashMap<>();
    private final Map<String, String> genByJson = new HashMap<>();
    private int genCount;

    ItemDefinitions(PackConverter c) {
        this.c = c;
    }

    // ------------------------------------------------------------------ hojas (modelos finales)

    /** Un modelo final: una referencia, varios combinados (composite) o nada. */
    private record Leaf(List<String> refs) {
        static final Leaf EMPTY = new Leaf(List.of());

        static Leaf of(String ref) {
            return new Leaf(List.of(ref));
        }
    }

    // ------------------------------------------------------------------ predicados

    /** Traduccion de una propiedad de 1.21 a un predicado de 1.20.1 (o null si no hay). */
    private record Pred(String name, float factor) {
    }

    private static String prop(JsonObject n) {
        String p = n.has("property") ? n.get("property").getAsString() : "";
        return p.startsWith("minecraft:") ? p.substring(10) : p;
    }

    private static String type(JsonObject n) {
        String t = n.has("type") ? n.get("type").getAsString() : "";
        return t.startsWith("minecraft:") ? t.substring(10) : t;
    }

    private static Pred boolPred(String property, String item) {
        return switch (property) {
            case "using_item" -> switch (item) {
                case "minecraft:bow", "minecraft:crossbow" -> new Pred("pulling", 1);
                case "minecraft:shield" -> new Pred("blocking", 1);
                case "minecraft:trident" -> new Pred("throwing", 1);
                case "minecraft:goat_horn" -> new Pred("tooting", 1);
                default -> null;
            };
            case "broken" -> new Pred("broken", 1);
            case "damaged" -> new Pred("damaged", 1);
            case "fishing_rod/cast" -> new Pred("cast", 1);
            default -> null;
        };
    }

    private static Pred rangePred(JsonObject n, String item) {
        float scale = n.has("scale") ? n.get("scale").getAsFloat() : 1f;
        return switch (prop(n)) {
            case "use_duration" -> item.equals("minecraft:bow") ? new Pred("pull", 20 * scale) : null;
            case "crossbow/pull" -> new Pred("pull", scale);
            case "compass" -> new Pred("angle", scale);
            case "time" -> new Pred("time", scale);
            case "damage" -> new Pred("damage", scale);
            case "custom_model_data" -> (n.has("index") && n.get("index").getAsInt() != 0) ? null : new Pred("custom_model_data", scale);
            case "cooldown" -> new Pred("cooldown", scale);
            default -> null;
        };
    }

    /** Junta los valores de corte de cada predicado (en unidades de 1.20.1). */
    private void collect(JsonElement el, String item, Map<String, TreeSet<Float>> out) {
        if (el == null || !el.isJsonObject()) {
            return;
        }
        JsonObject n = el.getAsJsonObject();
        switch (type(n)) {
            case "condition" -> {
                Pred p = boolPred(prop(n), item);
                if (p != null) {
                    out.computeIfAbsent(p.name, k -> new TreeSet<>()).add(1f);
                }
                collect(n.get("on_true"), item, out);
                collect(n.get("on_false"), item, out);
            }
            case "range_dispatch" -> {
                Pred p = rangePred(n, item);
                if (n.has("entries")) {
                    for (JsonElement e : n.getAsJsonArray("entries")) {
                        JsonObject o = e.getAsJsonObject();
                        if (p != null && o.has("threshold")) {
                            float t = o.get("threshold").getAsFloat() / p.factor;
                            if (t > 0 && Float.isFinite(t)) {
                                out.computeIfAbsent(p.name, k -> new TreeSet<>()).add(round(t));
                            }
                        }
                        collect(o.get("model"), item, out);
                    }
                }
                collect(n.get("fallback"), item, out);
            }
            case "select" -> {
                String pr = prop(n);
                if (n.has("cases")) {
                    for (JsonElement e : n.getAsJsonArray("cases")) {
                        JsonObject o = e.getAsJsonObject();
                        if (pr.equals("trim_material")) {
                            for (String w : whens(o)) {
                                Float v = TRIMS.get(w.contains(":") ? w : "minecraft:" + w);
                                if (v != null) {
                                    out.computeIfAbsent("trim_type", k -> new TreeSet<>()).add(v);
                                }
                            }
                        }
                        collect(o.get("model"), item, out);
                    }
                }
                if (pr.equals("charge_type")) {
                    out.computeIfAbsent("charged", k -> new TreeSet<>()).add(1f);
                    out.computeIfAbsent("firework", k -> new TreeSet<>()).add(1f);
                } else if (pr.equals("main_hand")) {
                    out.computeIfAbsent("lefthanded", k -> new TreeSet<>()).add(1f);
                }
                collect(n.get("fallback"), item, out);
            }
            case "composite" -> {
                if (n.has("models")) {
                    for (JsonElement e : n.getAsJsonArray("models")) {
                        collect(e, item, out);
                    }
                }
            }
            default -> {
            }
        }
    }

    private static float round(float v) {
        return Math.round(v * 10000f) / 10000f;
    }

    private static List<String> whens(JsonObject o) {
        List<String> out = new ArrayList<>();
        JsonElement w = o.get("when");
        if (w == null) {
            return out;
        }
        if (w.isJsonArray()) {
            for (JsonElement e : w.getAsJsonArray()) {
                if (e.isJsonPrimitive()) {
                    out.add(e.getAsString());
                }
            }
        } else if (w.isJsonPrimitive()) {
            out.add(w.getAsString());
        }
        return out;
    }

    // ------------------------------------------------------------------ evaluacion

    /** Que modelo muestra la definicion en la perspectiva ctx con estos valores de predicados. */
    private Leaf eval(JsonElement el, String item, String ctx, Map<String, Float> a) {
        if (el == null || !el.isJsonObject()) {
            return Leaf.EMPTY;
        }
        JsonObject n = el.getAsJsonObject();
        switch (type(n)) {
            case "model":
                return n.has("model") ? Leaf.of(norm(n.get("model").getAsString())) : Leaf.EMPTY;
            case "composite": {
                List<String> refs = new ArrayList<>();
                if (n.has("models")) {
                    for (JsonElement e : n.getAsJsonArray("models")) {
                        refs.addAll(eval(e, item, ctx, a).refs);
                    }
                }
                return new Leaf(refs);
            }
            case "condition": {
                Pred p = boolPred(prop(n), item);
                boolean on = p != null && a.getOrDefault(p.name, 0f) >= 1f;
                return eval(n.get(on ? "on_true" : "on_false"), item, ctx, a);
            }
            case "range_dispatch": {
                Pred p = rangePred(n, item);
                float v = p == null ? 0 : a.getOrDefault(p.name, 0f) * p.factor;
                JsonElement best = null;
                float bt = -Float.MAX_VALUE;
                if (n.has("entries")) {
                    for (JsonElement e : n.getAsJsonArray("entries")) {
                        JsonObject o = e.getAsJsonObject();
                        float t = o.has("threshold") ? o.get("threshold").getAsFloat() : 0;
                        if (t <= v + 1e-4f && t > bt) {
                            bt = t;
                            best = o.get("model");
                        }
                    }
                }
                return eval(best != null ? best : n.get("fallback"), item, ctx, a);
            }
            case "select": {
                String pr = prop(n);
                String value = switch (pr) {
                    case "display_context" -> ctx;
                    case "charge_type" -> a.getOrDefault("charged", 0f) < 1 ? "none" : (a.getOrDefault("firework", 0f) >= 1 ? "rocket" : "arrow");
                    case "main_hand" -> a.getOrDefault("lefthanded", 0f) >= 1 ? "left" : "right";
                    case "trim_material" -> {
                        Float t = a.get("trim_type");
                        String found = null;
                        if (t != null) {
                            for (Map.Entry<String, Float> e : TRIMS.entrySet()) {
                                if (Math.abs(e.getValue() - t) < 1e-4) {
                                    found = e.getKey();
                                }
                            }
                        }
                        yield found;
                    }
                    default -> null;
                };
                if (value != null && n.has("cases")) {
                    for (JsonElement e : n.getAsJsonArray("cases")) {
                        JsonObject o = e.getAsJsonObject();
                        for (String w : whens(o)) {
                            String ww = pr.equals("trim_material") && !w.contains(":") ? "minecraft:" + w : w;
                            if (ww.equals(value)) {
                                return eval(o.get("model"), item, ctx, a);
                            }
                        }
                    }
                }
                return eval(n.get("fallback"), item, ctx, a);
            }
            case "special": {
                // cofres, escudos, cabezas...: en 1.20.1 los dibuja el juego con su modelo de siempre
                String id = item.substring(item.indexOf(':') + 1);
                return Leaf.of(item.substring(0, item.indexOf(':')) + ":item/" + id);
            }
            default:
                return Leaf.EMPTY;
        }
    }

    static String norm(String ref) {
        return ref.contains(":") ? ref : "minecraft:" + ref;
    }

    // ------------------------------------------------------------------ armado

    private record Def(String id, String ns, String path, JsonElement root) {
    }

    int apply() {
        List<Def> defs = new ArrayList<>();
        for (String k : c.keys()) {
            Matcher m = DEF.matcher(k);
            if (!m.matches()) {
                continue;
            }
            JsonObject o = c.json(k);
            if (o == null || !o.has("model")) {
                continue;
            }
            String ns = m.group(1);
            String path = m.group(2);
            String modelFile = "assets/" + ns + "/models/item/" + path + ".json";
            if (c.fromOverlay.contains(modelFile) && !c.fromOverlay.contains(k)) {
                continue; // el pack ya trae el modelo viejo para esta version
            }
            defs.add(new Def(ns + ":" + path, ns, path, o.get("model")));
        }
        // 1) cuales hay que reescribir
        Map<String, Map<String, TreeSet<Float>>> preds = new HashMap<>();
        List<Def> todo = new ArrayList<>();
        for (Def d : defs) {
            if (PackConverter.GSON.toJson(d.root).contains("special")
                    && PackConverter.GSON.toJson(d.root).matches("(?s).*\"type\"\\s*:\\s*\"(minecraft:)?special\".*")) {
                continue; // cofres, escudos, cabezas: se quedan como en 1.20.1
            }
            Map<String, TreeSet<Float>> p = new TreeMap<>();
            collect(d.root, d.id, p);
            preds.put(d.id, p);
            String self = d.ns + ":item/" + d.path;
            boolean trivial = p.isEmpty();
            if (trivial) {
                Leaf first = eval(d.root, d.id, CTX[0], Map.of());
                for (String ctx : CTX) {
                    Leaf l = eval(d.root, d.id, ctx, Map.of());
                    if (!l.equals(first)) {
                        trivial = false;
                        break;
                    }
                }
                if (trivial && first.refs.size() == 1 && first.refs.get(0).equals(self)) {
                    continue; // igual que en 1.20.1
                }
            }
            todo.add(d);
            wrapped.add(self);
        }
        // 2) los modelos que se llaman igual que el que vamos a escribir se mudan a starpackport:orig/...
        for (String self : new ArrayList<>(wrapped)) {
            String ns = self.substring(0, self.indexOf(':'));
            String p = self.substring(self.indexOf(':') + 1);
            String file = "assets/" + ns + "/models/" + p + ".json";
            byte[] src = c.files.get(file);
            if (src == null) {
                src = c.vanilla.get(file);
            }
            if (src == null) {
                continue; // de otro mod y el pack no lo trae: se resuelve abajo
            }
            String target = "starpackport:orig/" + ns + "/" + p.substring(5);
            relocated.put(self, target);
            c.files.put("assets/starpackport/models/orig/" + ns + "/" + p.substring(5) + ".json", src);
        }
        // 3) los "parent" de todos los modelos apuntan a la copia mudada
        for (String k : c.keys()) {
            if (!k.endsWith(".json") || !k.matches("^assets/[^/]+/models/.*")) {
                continue;
            }
            JsonObject o = c.json(k);
            if (o == null || !o.has("parent") || !o.get("parent").isJsonPrimitive()) {
                continue;
            }
            String par = norm(o.get("parent").getAsString());
            if (relocated.containsKey(par)) {
                o.addProperty("parent", relocated.get(par));
                c.putJson(k, o);
            }
        }
        // 4) escribir los modelos nuevos
        int n = 0;
        for (Def d : todo) {
            try {
                JsonObject model = build(d, preds.get(d.id));
                if (model != null) {
                    c.putJson("assets/" + d.ns + "/models/item/" + d.path + ".json", model);
                    n++;
                }
            } catch (RuntimeException e) {
                c.log.accept("StarPackPort: no pude convertir el item " + d.id + ": " + e);
            }
        }
        return n;
    }

    private String ref(String r, String self) {
        String x = relocated.get(r);
        if (x != null) {
            return x;
        }
        if (wrapped.contains(r) && !r.equals(self)) {
            return r; // otro item ya convertido: usa su modelo nuevo
        }
        return r;
    }

    private JsonObject build(Def d, Map<String, TreeSet<Float>> preds) {
        String self = d.ns + ":item/" + d.path;
        List<String> names = new ArrayList<>(preds.keySet());
        // limitar combinaciones
        List<String> used = new ArrayList<>();
        long combos = 1;
        for (String p : names) {
            long next = combos * (preds.get(p).size() + 1);
            if (next > 96) {
                c.log.accept("StarPackPort: " + d.id + " tiene demasiadas variantes, se ignora '" + p + "'");
                continue;
            }
            combos = next;
            used.add(p);
        }
        List<Map<String, Float>> assigns = new ArrayList<>();
        assigns.add(new TreeMap<>());
        for (String p : used) {
            List<Map<String, Float>> nx = new ArrayList<>();
            for (Map<String, Float> a : assigns) {
                Map<String, Float> z = new TreeMap<>(a);
                z.put(p, 0f);
                nx.add(z);
                for (Float v : preds.get(p)) {
                    Map<String, Float> b = new TreeMap<>(a);
                    b.put(p, v);
                    nx.add(b);
                }
            }
            assigns = nx;
        }
        // orden lexicografico (con los nombres ordenados): el ultimo override que coincide es el correcto
        assigns.sort((x, y) -> {
            for (String p : used) {
                int cmp = Float.compare(x.getOrDefault(p, 0f), y.getOrDefault(p, 0f));
                if (cmp != 0) {
                    return cmp;
                }
            }
            return 0;
        });
        JsonObject top = null;
        JsonArray overrides = new JsonArray();
        for (Map<String, Float> a : assigns) {
            boolean zero = a.values().stream().allMatch(v -> v == 0f);
            Object m = perspectives(d, a, self);
            if (m == null) {
                return null;
            }
            if (zero) {
                if (m instanceof JsonObject jo) {
                    top = jo.deepCopy();
                } else {
                    top = new JsonObject();
                    top.addProperty("parent", (String) m);
                }
            } else {
                JsonObject ov = new JsonObject();
                JsonObject pr = new JsonObject();
                for (Map.Entry<String, Float> e : a.entrySet()) {
                    if (e.getValue() != 0f) {
                        pr.addProperty(e.getKey(), e.getValue());
                    }
                }
                ov.add("predicate", pr);
                ov.addProperty("model", m instanceof JsonObject jo ? gen(jo) : (String) m);
                overrides.add(ov);
            }
        }
        if (top == null) {
            return null;
        }
        if (!overrides.isEmpty()) {
            top.add("overrides", overrides);
        }
        return top;
    }

    /** Modelo para estos predicados: una referencia (String) o un JSON de separate_transforms. */
    private Object perspectives(Def d, Map<String, Float> a, String self) {
        String[] refs = new String[CTX.length];
        for (int i = 0; i < CTX.length; i++) {
            Leaf l = eval(d.root, d.id, CTX[i], a);
            String r = leafRef(l, self);
            if (r == null) {
                return null;
            }
            refs[i] = r;
        }
        boolean same = true;
        for (String r : refs) {
            same &= r.equals(refs[0]);
        }
        if (same) {
            return refs[0];
        }
        // base = inventario (asi la luz del icono plano queda bien); el resto por perspectiva
        JsonObject o = new JsonObject();
        o.addProperty("loader", "forge:separate_transforms");
        o.add("base", parent(refs[GUI]));
        JsonObject per = new JsonObject();
        for (int i = 0; i < CTX.length; i++) {
            if (i != GUI && !refs[i].equals(refs[GUI])) {
                per.add(CTX[i], parent(refs[i]));
            }
        }
        o.add("perspectives", per);
        return o;
    }

    private static JsonObject parent(String r) {
        JsonObject o = new JsonObject();
        o.addProperty("parent", r);
        return o;
    }

    /** Referencia a un modelo para una hoja; crea los combinados y el vacio si hace falta. */
    private String leafRef(Leaf l, String self) {
        if (l.refs.isEmpty()) {
            JsonObject empty = new JsonObject();
            JsonObject tex = new JsonObject();
            tex.addProperty("particle", "minecraft:item/barrier");
            empty.add("textures", tex);
            empty.add("elements", new JsonArray());
            return gen(empty);
        }
        List<String> rs = new ArrayList<>();
        for (String r : l.refs) {
            String x = ref(r, self);
            if (x.equals(self)) {
                return null; // apunta a si mismo y no tenemos el original: no se puede convertir
            }
            rs.add(x);
        }
        if (rs.size() == 1) {
            return rs.get(0);
        }
        JsonObject o = new JsonObject();
        o.addProperty("parent", rs.get(0));
        o.addProperty("loader", "forge:composite");
        JsonObject ch = new JsonObject();
        for (int i = 0; i < rs.size(); i++) {
            ch.add("part" + i, parent(rs.get(i)));
        }
        o.add("children", ch);
        return gen(o);
    }

    /** Guarda un modelo generado en starpackport:gen/N (sin repetir) y devuelve su nombre. */
    private String gen(JsonObject o) {
        String key = PackConverter.GSON.toJson(o);
        String have = genByJson.get(key);
        if (have != null) {
            return have;
        }
        String name = "starpackport:gen/m" + (genCount++);
        c.files.put("assets/starpackport/models/gen/m" + (genCount - 1) + ".json", key.getBytes(StandardCharsets.UTF_8));
        genByJson.put(key, name);
        return name;
    }
}
