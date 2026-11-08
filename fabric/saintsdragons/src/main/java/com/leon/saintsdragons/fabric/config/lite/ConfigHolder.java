package com.leon.saintsdragons.fabric.config.lite;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Guarda y lee una clase de configuracion como JSON en config/&lt;nombre&gt;.json. */
public final class ConfigHolder<T extends ConfigData> {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Class<T> type;
    private final Path file;
    private T config;

    ConfigHolder(Class<T> type) {
        this.type = type;
        Config c = type.getAnnotation(Config.class);
        this.file = FabricLoader.getInstance().getConfigDir().resolve((c == null ? type.getSimpleName() : c.name()) + ".json");
        load();
    }

    public T getConfig() {
        return this.config;
    }

    public void setConfig(T config) {
        this.config = config;
    }

    public void load() {
        try {
            if (Files.isRegularFile(this.file)) {
                try (Reader r = Files.newBufferedReader(this.file)) {
                    T t = GSON.fromJson(r, this.type);
                    if (t != null) {
                        this.config = t;
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
            // se usa lo de fabrica
        }
        try {
            this.config = this.type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        save();
    }

    public void save() {
        try {
            Files.createDirectories(this.file.getParent());
            try (Writer w = Files.newBufferedWriter(this.file)) {
                GSON.toJson(this.config, w);
            }
        } catch (Exception ignored) {
            // no se pudo guardar
        }
    }
}
