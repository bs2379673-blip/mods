package com.tunombre.obscurearcade;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Animations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Estilos de pelea (config/obscurearcade/estilos/&lt;id&gt;.json): que animacion se usa para cada carril/acierto/fallo,
 * cuanto mueve la barra cada acierto, combo del golpe final, etc. Todo editable sin tocar codigo; se anaden estilos
 * nuevos copiando pelea.json y poniendo el nombre en la consola de duelo. Cualquier animacion de Epic Fight (BIPED_*, FIST_*)
 * o de otro mod ("espacio:ruta") sirve.
 */
public final class Styles {
    private static final Random RND = new Random();
    private static final Map<String, Style> CACHE = new HashMap<>();
    private static final Map<String, AnimationManager.AnimationAccessor<? extends StaticAnimation>> MINE = new HashMap<>();

    public static final class Style {
        public String id = "pelea";
        public double gainPerfect = 0.018, gainGreat = 0.012, gainGood = 0.006, missLoss = 0.03;
        public boolean ko = true;
        public int finisherCombo = 20;
        public double finisherBonus = 0.03;
        public int throttleMs = 220;
        public double lunge = 0.25, knock = 0.30, ringShift = 1.6, resetDist = 1.6;
        @SuppressWarnings("unchecked")
        public final List<String>[] attacks = new List[]{new ArrayList<String>(), new ArrayList<String>(), new ArrayList<String>(), new ArrayList<String>()};
        public final List<String> attackGood = new ArrayList<>(), finisher = new ArrayList<>(), hitLight = new ArrayList<>(), hitHeavy = new ArrayList<>(),
                dodge = new ArrayList<>(), counter = new ArrayList<>();
        public String stance = "stance", win = "win", lose = "BIPED_KNOCKDOWN", taunt = "taunt";

        public double gain(int judgement) { return judgement >= 300 ? gainPerfect : judgement >= 200 ? gainGreat : gainGood; }

        public String pick(List<String> l) { return l.isEmpty() ? null : l.get(RND.nextInt(l.size())); }

        public String attackFor(int lane, int judgement, boolean fin) {
            if (fin && !finisher.isEmpty()) return pick(finisher);
            if (judgement < 200 && !attackGood.isEmpty()) return pick(attackGood);
            List<String> l = attacks[Math.floorMod(lane, 4)];
            return l.isEmpty() ? pick(counter) : pick(l);
        }
    }

    private Styles() { }

    public static Path dir() { return FMLPaths.CONFIGDIR.get().resolve("obscurearcade").resolve("estilos"); }

    public static void reload() { CACHE.clear(); }

    public static List<String> ids() {
        List<String> l = new ArrayList<>();
        try (var s = Files.isDirectory(dir()) ? Files.list(dir()) : java.util.stream.Stream.<Path>empty()) {
            s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json")).forEach(n -> l.add(n.substring(0, n.length() - 5)));
        } catch (IOException ignored) { }
        java.util.Collections.sort(l);
        return l;
    }

    /** deja el estilo por defecto en la carpeta de config si no existe */
    static void installDefaults() {
        try {
            Path dst = dir().resolve("pelea.json");
            if (Files.exists(dst)) return;
            Files.createDirectories(dst.getParent());
            try (var in = Styles.class.getResourceAsStream("/defaults/estilos/pelea.json")) {
                if (in != null) Files.write(dst, in.readAllBytes());
            }
        } catch (IOException ignored) { }
    }

    public static Style get(String id) {
        String key = id == null || id.isBlank() ? "pelea" : id.toLowerCase(Locale.ROOT);
        Style s = CACHE.get(key);
        if (s != null) return s;
        s = load(key);
        if (s == null && !key.equals("pelea")) s = get("pelea");
        if (s == null) s = defaults();
        CACHE.put(key, s);
        return s;
    }

    private static Style defaults() {
        Style s = new Style();
        s.attacks[0].addAll(List.of("FIST_AUTO1", "FIST_AUTO2"));
        s.attacks[1].addAll(List.of("FIST_AUTO2", "FIST_AUTO3"));
        s.attacks[2].addAll(List.of("FIST_AUTO3", "FIST_DASH"));
        s.attacks[3].addAll(List.of("FIST_AUTO3", "FIST_AUTO1"));
        s.attackGood.add("FIST_AUTO1");
        s.finisher.add("FIST_DASH");
        s.hitLight.add("BIPED_HIT_SHORT");
        s.hitHeavy.add("BIPED_HIT_LONG");
        s.dodge.add("BIPED_STEP_BACKWARD");
        s.counter.addAll(List.of("FIST_AUTO1", "FIST_AUTO2", "FIST_AUTO3"));
        return s;
    }

    private static Style load(String id) {
        Path f = dir().resolve(id + ".json");
        try {
            if (!Files.isRegularFile(f)) return null;
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            Style s = defaults();
            s.id = id;
            if (o.has("ganancia")) {
                JsonObject g = o.getAsJsonObject("ganancia");
                s.gainPerfect = d(g, "perfect", s.gainPerfect);
                s.gainGreat = d(g, "great", s.gainGreat);
                s.gainGood = d(g, "good", s.gainGood);
            }
            s.missLoss = d(o, "perdida_fallo", s.missLoss);
            s.ko = !o.has("ko") || o.get("ko").getAsBoolean();
            s.finisherCombo = Math.max(2, (int) d(o, "combo_final", s.finisherCombo));
            s.finisherBonus = d(o, "bonus_final", s.finisherBonus);
            s.throttleMs = (int) d(o, "espera_ms", s.throttleMs);
            s.lunge = d(o, "avance", s.lunge);
            s.knock = d(o, "retroceso", s.knock);
            s.ringShift = d(o, "desplazamiento_ring", s.ringShift);
            s.resetDist = d(o, "distancia_reanclar", s.resetDist);
            if (o.has("ataques")) {
                JsonObject a = o.getAsJsonObject("ataques");
                for (int i = 0; i < 4; i++) if (a.has(String.valueOf(i))) fill(s.attacks[i], a.get(String.valueOf(i)));
            }
            fillIf(o, "ataque_bueno", s.attackGood);
            fillIf(o, "ataque_final", s.finisher);
            fillIf(o, "golpe_leve", s.hitLight);
            fillIf(o, "golpe_fuerte", s.hitHeavy);
            fillIf(o, "esquiva", s.dodge);
            fillIf(o, "contragolpe", s.counter);
            if (o.has("postura")) s.stance = o.get("postura").getAsString();
            if (o.has("victoria")) s.win = o.get("victoria").getAsString();
            if (o.has("derrota")) s.lose = o.get("derrota").getAsString();
            if (o.has("burla")) s.taunt = o.get("burla").getAsString();
            return s;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static double d(JsonObject o, String k, double def) {
        try { return o.has(k) ? o.get(k).getAsDouble() : def; } catch (RuntimeException e) { return def; }
    }

    private static void fillIf(JsonObject o, String k, List<String> into) { if (o.has(k)) fill(into, o.get(k)); }

    private static void fill(List<String> into, JsonElement e) {
        into.clear();
        if (e.isJsonArray()) for (JsonElement x : (JsonArray) e) into.add(x.getAsString());
        else into.add(e.getAsString());
    }

    // ------------------------------------------------------------------ busqueda de animaciones ----
    @SuppressWarnings("unchecked")
    public static AnimationManager.AnimationAccessor<? extends StaticAnimation> find(String name) {
        if (name == null || name.isBlank()) return null;
        String k = name.trim();
        var mine = MINE.get(k.toLowerCase(Locale.ROOT));
        if (mine != null) return mine;
        try {
            Object o = Animations.class.getField(k.toUpperCase(Locale.ROOT)).get(null);
            if (o instanceof AnimationManager.AnimationAccessor<?> acc) return (AnimationManager.AnimationAccessor<? extends StaticAnimation>) acc;
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        if (k.contains(":")) {
            try { return AnimationManager.byKey(k); } catch (RuntimeException ignored) { }
        }
        return null;
    }

    static void registerMine(String name, AnimationManager.AnimationAccessor<? extends StaticAnimation> acc) { MINE.put(name, acc); }
}
