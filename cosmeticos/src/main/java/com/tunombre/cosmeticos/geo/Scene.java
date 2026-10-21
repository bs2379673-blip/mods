package com.tunombre.cosmeticos.geo;

import java.util.LinkedHashMap;
import java.util.Map;

/** mallas agrupadas por textura y modo (solido con sombreado / brillo aditivo) */
public final class Scene {
    public final Map<String, Mesh> meshes = new LinkedHashMap<>();

    public Mesh solid(String tex) { return meshes.computeIfAbsent(tex, k -> new Mesh(tex, false)); }

    public Mesh glow(String tex) { return meshes.computeIfAbsent(tex + "#g", k -> new Mesh(tex, true)); }

    public void clear() { meshes.clear(); }
}
