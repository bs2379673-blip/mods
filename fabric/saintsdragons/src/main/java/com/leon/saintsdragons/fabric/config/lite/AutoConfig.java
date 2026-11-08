package com.leon.saintsdragons.fabric.config.lite;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/** Reemplazo minimo de Cloth AutoConfig: registrar y obtener configuraciones (sin pantalla). */
public final class AutoConfig {
    private static final Map<Class<?>, ConfigHolder<?>> HOLDERS = new HashMap<>();

    private AutoConfig() {
    }

    public static synchronized <T extends ConfigData> ConfigHolder<T> register(Class<T> type, BiFunction<Config, Class<T>, ?> serializer) {
        @SuppressWarnings("unchecked")
        ConfigHolder<T> h = (ConfigHolder<T>) HOLDERS.computeIfAbsent(type, t -> new ConfigHolder<>(type));
        return h;
    }

    @SuppressWarnings("unchecked")
    public static synchronized <T extends ConfigData> ConfigHolder<T> getConfigHolder(Class<T> type) {
        return (ConfigHolder<T>) HOLDERS.computeIfAbsent(type, t -> new ConfigHolder<>(type));
    }
}
