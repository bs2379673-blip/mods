package com.tunombre.obscureui.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Atajos (uno o varios comandos con nombre, separados por ;) y el historial de comandos del panel. Se guardan en config/obscureui_atajos.json. */
final class Shortcuts {
    record Item(String name, String commands) { }

    static final List<Item> ITEMS = new ArrayList<>();
    static final List<String> HISTORY = new ArrayList<>();
    private static boolean loaded;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private Shortcuts() { }

    private static Path file() { return FMLPaths.CONFIGDIR.get().resolve("obscureui_atajos.json"); }

    static synchronized void load() {
        if (loaded) return;
        loaded = true;
        ITEMS.clear();
        HISTORY.clear();
        try {
            if (Files.isRegularFile(file())) {
                JsonObject o = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("atajos")) for (var e : o.getAsJsonArray("atajos")) {
                    JsonObject a = e.getAsJsonObject();
                    ITEMS.add(new Item(a.get("n").getAsString(), a.get("c").getAsString()));
                }
                if (o.has("historial")) for (var e : o.getAsJsonArray("historial")) HISTORY.add(e.getAsString());
                return;
            }
        } catch (IOException | RuntimeException ignored) { }
        // primera vez: unos atajos de ejemplo
        ITEMS.add(new Item("Calma total", "visionfx modo off; anomalia fin; concierto detener"));
        ITEMS.add(new Item("Noche de terror", "visionfx plantilla terror; visionfx modo pesadilla"));
        ITEMS.add(new Item("Sin filtros", "visionfx off; visionfx modo off"));
        ITEMS.add(new Item("Curar a todos", "effect give @a minecraft:instant_health 1 10 true; effect give @a minecraft:saturation 1 10 true"));
        save();
    }

    static synchronized void save() {
        JsonObject o = new JsonObject();
        JsonArray a = new JsonArray();
        for (Item i : ITEMS) {
            JsonObject j = new JsonObject();
            j.addProperty("n", i.name());
            j.addProperty("c", i.commands());
            a.add(j);
        }
        o.add("atajos", a);
        JsonArray h = new JsonArray();
        HISTORY.forEach(h::add);
        o.add("historial", h);
        try {
            Files.writeString(file(), GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (IOException ignored) { }
    }

    static void remember(String cmd) {
        HISTORY.remove(cmd);
        HISTORY.add(0, cmd);
        while (HISTORY.size() > 15) HISTORY.remove(HISTORY.size() - 1);
        save();
    }

    static void add(String name, String commands) {
        ITEMS.removeIf(i -> i.name().equalsIgnoreCase(name));
        ITEMS.add(new Item(name, commands));
        save();
    }

    static void remove(String name) {
        ITEMS.removeIf(i -> i.name().equals(name));
        save();
    }
}
