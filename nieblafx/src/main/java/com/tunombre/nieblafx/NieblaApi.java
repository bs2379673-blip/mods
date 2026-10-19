package com.tunombre.nieblafx;

/** API simple para Obscure UI / otros mods (por reflexion). */
public final class NieblaApi {
    private NieblaApi() { }
    public static String stateJson() { return Fog.json(); }
    public static String applyJson(String json) { return Fog.apply(json); }
    /** cambia un parametro global y lo sincroniza; devuelve error o null */
    public static String set(String key, String value) {
        String r = Fog.set(key, value);
        if (r == null) { Fog.save(); Net.sendAll(Fog.json()); }
        return r;
    }
}
