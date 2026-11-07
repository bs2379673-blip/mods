package com.starlevi.starscenes.client;

import java.util.HashMap;
import java.util.Map;

/** Los globitos de texto que hay ahora arriba de los personajes (por id de entidad). */
public final class Bubbles {
    private record Bubble(String text, long until) {
    }

    private static final Map<Integer, Bubble> NOW = new HashMap<>();

    private Bubbles() {
    }

    public static void show(int entity, String text, int ticks) {
        NOW.put(entity, new Bubble(text, System.currentTimeMillis() + ticks * 50L));
    }

    public static String get(int entity) {
        Bubble b = NOW.get(entity);
        if (b == null) {
            return null;
        }
        if (System.currentTimeMillis() > b.until) {
            NOW.remove(entity);
            return null;
        }
        return b.text;
    }

    public static void clear() {
        NOW.clear();
    }
}
