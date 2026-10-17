package com.tunombre.crafteos;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Guarda TODAS las recetas tal como las cargo el juego, las agrupa por resultado y, segun la lista de desactivados,
 * le deja al RecipeManager solo las permitidas. Se puede cambiar en vivo: se vuelve a filtrar y se reenvia a los jugadores.
 */
public final class Recipes {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** un resultado (o una receta suelta) con todas las recetas que lo producen */
    public static final class Group {
        public final String key, mod;
        public int kinds;           // bits: 1 mesa, 2 hornos, 4 herreria, 8 cantero, 16 otros (mods)
        public final List<Recipe<?>> recipes = new ArrayList<>();
        Group(String key, String mod) { this.key = key; this.mod = mod; }
    }

    public static int kindBit(String n) {
        return switch (n) { case "craft" -> K_CRAFT; case "smelt" -> K_SMELT; case "smith" -> K_SMITH; case "stone" -> K_STONE; default -> K_OTHER; };
    }

    static final int K_CRAFT = 1, K_SMELT = 2, K_SMITH = 4, K_STONE = 8, K_OTHER = 16;

    /** todas las recetas originales (sin filtrar) */
    private static volatile List<Recipe<?>> all = List.of();
    private static volatile Map<String, Group> groups = new LinkedHashMap<>();
    /** claves desactivadas: id de resultado (minecraft:diamond_sword), id de receta (tacz:gun/ai_awp) o "modid:*" */
    private static final Set<String> disabled = java.util.Collections.synchronizedSet(new TreeSet<>());

    private Recipes() { }

    static Path file() { return FMLPaths.CONFIGDIR.get().resolve("crafteos").resolve("desactivados.json"); }

    static synchronized void load() {
        disabled.clear();
        Path f = file();
        if (!Files.isRegularFile(f)) return;
        try (Reader r = Files.newBufferedReader(f)) {
            JsonObject o = GSON.fromJson(r, JsonObject.class);
            if (o != null && o.has("desactivados")) for (JsonElement e : o.getAsJsonArray("desactivados")) disabled.add(e.getAsString());
        } catch (Exception e) { Crafteos_LOG("No se pudo leer " + f + ": " + e); }
    }

    static synchronized void save() {
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            JsonObject o = new JsonObject();
            JsonArray a = new JsonArray();
            synchronized (disabled) { for (String s : disabled) a.add(s); }
            o.add("desactivados", a);
            try (Writer w = Files.newBufferedWriter(f)) { GSON.toJson(o, w); }
        } catch (Exception e) { Crafteos_LOG("No se pudo guardar " + f + ": " + e); }
    }

    private static void Crafteos_LOG(String m) { System.err.println("[crafteos] " + m); }

    // ------------------------------------------------------------------ consultas ----
    public static Map<String, Group> groups() { return groups; }
    public static boolean isDisabled(String key) { return disabled.contains(key); }
    public static Set<String> disabledSet() { return disabled; }

    public static boolean set(String key, boolean off) {
        boolean ch = off ? disabled.add(key) : disabled.remove(key);
        if (ch) save();
        return ch;
    }

    /** una receta queda desactivada si lo esta su grupo, su id o todo su mod */
    private static boolean blocked(Group g) {
        return disabled.contains(g.key) || disabled.contains(g.mod + ":*");
    }

    // ------------------------------------------------------------------ construccion ----
    private static int kindOf(Recipe<?> r) {
        ResourceLocation t = ForgeRegistries.RECIPE_TYPES.getKey(r.getType());
        if (t == null) return K_OTHER;
        if (!t.getNamespace().equals("minecraft")) return K_OTHER;
        return switch (t.getPath()) {
            case "crafting" -> K_CRAFT;
            case "smelting", "blasting", "smoking", "campfire_cooking" -> K_SMELT;
            case "smithing" -> K_SMITH;
            case "stonecutting" -> K_STONE;
            default -> K_OTHER;
        };
    }

    private static void index(net.minecraft.core.RegistryAccess ra, List<Recipe<?>> list) {
        Map<String, Group> m = new java.util.TreeMap<>();
        for (Recipe<?> r : list) {
            String key = r.getId().toString(), mod = r.getId().getNamespace();
            int kind = kindOf(r);
            try {
                ItemStack out = r.getResultItem(ra);
                if (out != null && !out.isEmpty()) {
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(out.getItem());
                    if (id != null) {
                        mod = id.getNamespace();
                        // los resultados con NBT en recetas de otros mods (ej: las armas de TACZ son todas el mismo item) se separan por receta
                        key = (out.hasTag() && kind == K_OTHER) ? r.getId().toString() : id.toString();
                    }
                }
            } catch (Throwable ignored) { }
            Group g = m.computeIfAbsent(key, k -> new Group(k, r.getId().getNamespace()));
            g.recipes.add(r);
            g.kinds |= kind;
        }
        // el "mod" de un grupo es el del resultado (mejor que el de la receta); se recalcula con la primera receta
        Map<String, Group> fixed = new LinkedHashMap<>();
        for (Group g : m.values()) {
            String mod = g.mod;
            Recipe<?> first = g.recipes.get(0);
            try {
                ItemStack out = first.getResultItem(ra);
                if (out != null && !out.isEmpty()) { ResourceLocation id = ForgeRegistries.ITEMS.getKey(out.getItem()); if (id != null) mod = id.getNamespace(); }
            } catch (Throwable ignored) { }
            Group n = new Group(g.key, mod);
            n.kinds = g.kinds;
            n.recipes.addAll(g.recipes);
            fixed.put(g.key, n);
        }
        groups = fixed;
    }

    /** vuelve a dejar en el RecipeManager solo las recetas permitidas */
    public static int apply(MinecraftServer s) {
        RecipeManager rm = s.getRecipeManager();
        List<Recipe<?>> keep = new ArrayList<>();
        int removed = 0;
        for (Group g : groups.values()) {
            if (blocked(g)) { removed += g.recipes.size(); continue; }
            keep.addAll(g.recipes);
        }
        rm.replaceRecipes(keep);
        return removed;
    }

    /** reenvia las recetas a todos los jugadores (y refresca su libro de recetas) */
    public static void resync(MinecraftServer s) {
        Collection<Recipe<?>> now = s.getRecipeManager().getRecipes();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundUpdateRecipesPacket(now));
            p.getRecipeBook().sendInitialRecipeBook(p);
        }
    }

    /** aplicar un cambio en vivo */
    public static int change(MinecraftServer s) {
        int removed = apply(s);
        resync(s);
        return removed;
    }

    // ------------------------------------------------------------------ recarga ----
    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent e) {
        // se agrega al final de la lista: para cuando se aplica, el RecipeManager ya cargo las recetas
        e.addListener(new SimplePreparableReloadListener<Object>() {
            @Override protected Object prepare(ResourceManager rm, ProfilerFiller p) { return new Object(); }

            @Override
            protected void apply(Object o, ResourceManager rm, ProfilerFiller p) {
                load();
                RecipeManager mgr = e.getServerResources().getRecipeManager();
                List<Recipe<?>> list = new ArrayList<>(mgr.getRecipes());
                all = list;
                index(e.getRegistryAccess(), list);
                int removed = 0;
                List<Recipe<?>> keep = new ArrayList<>();
                for (Group g : groups.values()) { if (blocked(g)) removed += g.recipes.size(); else keep.addAll(g.recipes); }
                mgr.replaceRecipes(keep);
                if (removed > 0) System.out.println("[crafteos] recetas desactivadas: " + removed);
            }
        });
    }
}
