package com.tunombre.jefes;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Los jefes disponibles. SERVER: se cargan del jar (los que trae el mod) y de config/jefes/bosses/&lt;id&gt;/ (boss.json, model.json, tex.png:
 * los que agrega el admin, convertidos con tools/jefes/cpm2jefe.py) y se mandan a cada cliente al entrar. CLIENT: lo que mando el servidor.
 */
public final class Bosses {
    public static final Map<String, Boss> SERVER = new TreeMap<>();
    public static final Map<String, Boss> CLIENT = new ConcurrentHashMap<>();
    private static final String[] BUILTIN = {"quibungo"};

    private Bosses() { }

    public static Boss get(boolean client, String id) { return (client ? CLIENT : SERVER).get(id); }

    public static synchronized void loadServer() {
        SERVER.clear();
        for (String id : BUILTIN) {
            try {
                String base = "/assets/" + Jefes.MODID + "/bosses/" + id;
                Boss b = new Boss(text(base + ".spec.json"), text(base + ".model.json"), bytes(base + ".png"));
                SERVER.put(b.id(), b);
            } catch (Exception e) {
                Jefes.LOG.error("[jefes] no se pudo cargar el jefe incluido {}", id, e);
            }
        }
        Path dir = FMLPaths.CONFIGDIR.get().resolve("jefes").resolve("bosses");
        try {
            Files.createDirectories(dir);
            try (var st = Files.list(dir)) {
                for (Path p : (Iterable<Path>) st::iterator) {
                    if (!Files.isDirectory(p)) continue;
                    try {
                        Boss b = new Boss(Files.readString(p.resolve("boss.json"), StandardCharsets.UTF_8), Files.readString(p.resolve("model.json"), StandardCharsets.UTF_8), Files.readAllBytes(p.resolve("tex.png")));
                        SERVER.put(b.id(), b);
                        Jefes.LOG.info("[jefes] jefe agregado desde config: {}", b.id());
                    } catch (Exception e) {
                        Jefes.LOG.error("[jefes] jefe roto en {} (hacen falta boss.json, model.json y tex.png)", p, e);
                    }
                }
            }
        } catch (IOException e) {
            Jefes.LOG.warn("[jefes] no se pudo leer config/jefes/bosses: {}", e.toString());
        }
        Jefes.LOG.info("[jefes] jefes cargados: {}", SERVER.keySet());
    }

    private static byte[] bytes(String path) throws IOException {
        try (InputStream in = Bosses.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("falta " + path);
            return in.readAllBytes();
        }
    }

    private static String text(String path) throws IOException { return new String(bytes(path), StandardCharsets.UTF_8); }
}
