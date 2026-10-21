package com.tunombre.obscurenpc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Banderas globales del servidor (las comparten todos los NPCs, dialogos y rutinas). Se guardan en config/obscurenpc/estado.json. */
public final class State {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> FLAGS = new TreeSet<>();
    private static boolean loaded;

    private State() { }

    public static Path dir() { return FMLPaths.CONFIGDIR.get().resolve("obscurenpc"); }

    private static Path file() { return dir().resolve("estado.json"); }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.isRegularFile(file())) {
                String[] a = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), String[].class);
                if (a != null) for (String s : a) FLAGS.add(s.toLowerCase(Locale.ROOT));
            }
        } catch (IOException | RuntimeException ignored) { }
    }

    private static void save() {
        try {
            Files.createDirectories(dir());
            Files.writeString(file(), GSON.toJson(FLAGS.toArray(new String[0])), StandardCharsets.UTF_8);
        } catch (IOException ignored) { }
    }

    public static boolean has(String f) { load(); return FLAGS.contains(f.toLowerCase(Locale.ROOT)); }

    public static void set(String f) { load(); if (FLAGS.add(f.toLowerCase(Locale.ROOT))) save(); }

    public static void clear(String f) { load(); if (FLAGS.remove(f.toLowerCase(Locale.ROOT))) save(); }

    public static Set<String> all() { load(); return FLAGS; }
}
