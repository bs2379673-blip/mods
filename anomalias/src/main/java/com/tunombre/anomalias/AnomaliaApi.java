package com.tunombre.anomalias;

/** API sencilla para Obscure UI (se llama por reflexion desde el servidor). */
public final class AnomaliaApi {
    private AnomaliaApi() { }

    public static String configJson() { return Cfg.json(); }

    public static String applyConfig(String json) { return Cfg.apply(json); }

    public static String statusJson() { return Server.statusJson(); }
}
