package com.tunombre.tacui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Ajustes y posiciones de los paneles (fraccion de la pantalla), guardados en config/tacui_layout.json. */
public final class Layout {
    private static Layout instance;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String theme = "RED";
    public boolean inventory = true;
    public boolean hud = true;
    public boolean containers = true;
    public Map<String, double[]> pos = new HashMap<>();

    public static Layout get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static Path file() { return FMLPaths.CONFIGDIR.get().resolve("tacui_layout.json"); }

    private static Layout load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                Layout l = GSON.fromJson(Files.readString(f), Layout.class);
                if (l != null) { if (l.pos == null) l.pos = new HashMap<>(); return l; }
            }
        } catch (Exception ignored) { }
        return new Layout();
    }

    public void save() {
        try { Files.writeString(file(), GSON.toJson(this)); } catch (Exception ignored) { }
    }

    public Theme theme() { return Theme.of(theme); }

    /** Posicion en pixeles del panel; si no hay una guardada se usa la de por defecto. */
    public int[] px(String key, int defX, int defY, int sw, int sh) {
        double[] p = pos.get(key);
        if (p == null) return new int[]{defX, defY};
        return new int[]{(int) Math.round(p[0] * sw), (int) Math.round(p[1] * sh)};
    }

    public void setPx(String key, int x, int y, int sw, int sh) {
        pos.put(key, new double[]{(double) x / Math.max(1, sw), (double) y / Math.max(1, sh)});
    }
}
