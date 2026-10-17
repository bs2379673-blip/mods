package com.tunombre.modelos;

import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Biblioteca de modelos: config/modelos/. Acepta .cpmmodel (y .cpmodel) (se codifica a base64) y .b64 / .txt (ya con el texto base64 que exporta el editor de CPM). */
final class Store {
    private Store() { }

    static Path dir() { return FMLPaths.CONFIGDIR.get().resolve("modelos"); }

    static void ensure() {
        try {
            Files.createDirectories(dir());
            Path readme = dir().resolve("LEEME.txt");
            if (!Files.exists(readme)) Files.writeString(readme, "Pone aca los modelos de Custom Player Models:\n"
                    + "  - archivos .cpmmodel (exportados del editor de CPM), o\n"
                    + "  - archivos .b64 / .txt con el texto base64 del modelo (Export -> Base64 en el editor).\n"
                    + "Despues usa /modelos recargar (o el boton Recargar del menu /modelos gui).\n");
        } catch (Exception ignored) { }
    }

    private static boolean isModel(String n) {
        n = n.toLowerCase(Locale.ROOT);
        return n.endsWith(".cpmmodel") || n.endsWith(".cpmodel") || n.endsWith(".b64") || n.endsWith(".txt") && !n.equals("leeme.txt");
    }

    static String baseName(String file) { int i = file.lastIndexOf('.'); return i < 0 ? file : file.substring(0, i); }

    static List<String> list() {
        ensure();
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir())) {
            s.map(p -> p.getFileName().toString()).filter(Store::isModel).map(Store::baseName).distinct().sorted().forEach(out::add);
        } catch (Exception ignored) { }
        return out;
    }

    /** el base64 que espera /cpm setskin, o null si no existe */
    static String read(String name) {
        if (name.contains("/") || name.contains("\\") || name.contains("..")) return null;
        try {
            for (String ext : new String[]{".cpmmodel", ".cpmodel", ".b64", ".txt"}) {
                Path f = dir().resolve(name + ext);
                if (!Files.isRegularFile(f)) continue;
                if (ext.equals(".cpmmodel") || ext.equals(".cpmodel")) return Base64.getEncoder().encodeToString(Files.readAllBytes(f));
                return Files.readString(f).replaceAll("\\s+", "");
            }
        } catch (Exception ignored) { }
        return null;
    }
}
