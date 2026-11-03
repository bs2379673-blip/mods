package com.starlevi.starspirits.server;

import com.starlevi.starspirits.Config;
import com.starlevi.starspirits.Rarity;
import com.starlevi.starspirits.SpiritDef;
import com.starlevi.starspirits.StarSpirits;
import com.starlevi.starspirits.net.Net;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Los espiritus del servidor: config/starspirits/espiritus/&lt;id&gt;.json, con sus imagenes (.png) y
 * modelos de Blockbench (.geo.json) en la misma carpeta. Se revisa la carpeta sola (sin reiniciar) y
 * los jugadores reciben los cambios. La primera vez se escriben los 8 espiritus de ejemplo.
 */
public final class SpiritLibrary {
    public static final String[] BUILTIN = {"champi", "margarita", "brotecito", "campanita", "luminon", "rosita", "loto", "bonsai"};
    private static final long MAX_FILE = 4L * 1024 * 1024;

    public record Asset(String file, String hash, byte[] data) {
    }

    private static final Map<String, SpiritDef> DEFS = new TreeMap<>();
    private static final Map<String, Asset> ASSETS = new HashMap<>();
    private static final Map<String, Asset> BY_HASH = new HashMap<>();
    private static final Map<Path, Long> STAMPS = new HashMap<>();
    private static boolean examples;
    private static int ticks;

    private SpiritLibrary() {
    }

    public static Path folder() {
        return Config.folder().resolve("espiritus");
    }

    public static synchronized SpiritDef get(String id) {
        return id == null ? null : DEFS.get(id);
    }

    public static synchronized List<SpiritDef> all() {
        return new ArrayList<>(DEFS.values());
    }

    public static synchronized byte[] byHash(String hash) {
        Asset a = BY_HASH.get(hash);
        return a == null ? null : a.data();
    }

    /** Cada 5 segundos revisa la carpeta; si algo cambio, manda la lista nueva a todos. */
    public static void tick(MinecraftServer server) {
        if (++ticks % 100 == 0 && scan(false)) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                send(p);
            }
        }
    }

    /** Revisa la carpeta; devuelve true si algo cambio. */
    public static synchronized boolean scan(boolean force) {
        Path dir = folder();
        try {
            Files.createDirectories(dir);
            if (!examples) {
                examples = true;
                writeExamples(dir);
            }
        } catch (IOException e) {
            return false;
        }
        Map<Path, Long> seen = new HashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : (Iterable<Path>) files::iterator) {
                String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
                if (Files.isRegularFile(f) && (n.endsWith(".json") || n.endsWith(".png") || n.endsWith(".obj"))) {
                    seen.put(f, Files.getLastModifiedTime(f).toMillis() ^ Files.size(f) << 20);
                }
            }
        } catch (IOException ignored) {
            // carpeta inaccesible
        }
        if (!force && seen.equals(STAMPS)) {
            return false;
        }
        STAMPS.clear();
        STAMPS.putAll(seen);
        ASSETS.clear();
        BY_HASH.clear();
        DEFS.clear();
        // los que trae el mod adentro (siempre estan y se actualizan con el mod)
        java.util.Set<String> builtin = loadBuiltins();
        // primero los archivos que usan (imagenes y modelos .geo.json)
        for (Path f : seen.keySet()) {
            String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!n.endsWith(".png") && !n.endsWith(".geo.json") && !n.endsWith(".obj")) {
                continue;
            }
            try {
                if (Files.size(f) > MAX_FILE) {
                    StarSpirits.LOGGER.warn("StarSpirits: {} pesa mas de 4 MB y no se usa", n);
                    continue;
                }
                byte[] data = Files.readAllBytes(f);
                Asset a = new Asset(n, sha1(data), data);
                ASSETS.put(n, a);
                BY_HASH.put(a.hash(), a);
            } catch (IOException ignored) {
                // se reintenta en la proxima revision
            }
        }
        for (Path f : seen.keySet()) {
            String file = f.getFileName().toString();
            String n = file.toLowerCase(Locale.ROOT);
            if (!n.endsWith(".json") || n.endsWith(".geo.json")) {
                continue;
            }
            String id = n.substring(0, n.length() - 5).replaceAll("[^a-z0-9_.-]+", "_");
            if (builtin.contains(id)) {
                // copia vieja de uno que ahora viene en el mod: se aparta para que no confunda
                try {
                    Path old = dir.resolve("viejos");
                    Files.createDirectories(old);
                    Files.move(f, old.resolve(file), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    StarSpirits.LOGGER.info("StarSpirits: {} ya viene en el mod; la copia de la config se movio a viejos/", file);
                } catch (IOException ignored) {
                    // queda donde estaba, pero manda la del mod
                }
                continue;
            }
            try {
                SpiritDef d = new SpiritDef(id, Files.readString(f, StandardCharsets.UTF_8));
                for (String need : d.files()) {
                    if (!ASSETS.containsKey(need)) {
                        StarSpirits.LOGGER.warn("StarSpirits: al espiritu {} le falta el archivo {}", id, need);
                    }
                }
                DEFS.put(id, d);
            } catch (Exception ex) {
                StarSpirits.LOGGER.warn("StarSpirits: el espiritu {} tiene un error: {}", file, ex.getMessage());
            }
        }
        StarSpirits.LOGGER.info("StarSpirits: {} espiritus, {} archivos", DEFS.size(), ASSETS.size());
        return true;
    }

    /** Lista de espiritus incluidos en el mod (assets/starspirits/espiritus/incluidos.txt). */
    private static java.util.Set<String> loadBuiltins() {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        String index = resourceText("incluidos.txt");
        if (index == null) {
            return ids;
        }
        for (String line : index.split("\\R")) {
            String id = line.trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty() || id.startsWith("#")) {
                continue;
            }
            String json = resourceText(id + ".json");
            if (json == null) {
                continue;
            }
            try {
                SpiritDef d = new SpiritDef(id, json);
                for (String need : d.files()) {
                    byte[] data = resourceBytes(need);
                    if (data == null) {
                        StarSpirits.LOGGER.warn("StarSpirits: al espiritu incluido {} le falta {}", id, need);
                        continue;
                    }
                    Asset a = new Asset(need, sha1(data), data);
                    ASSETS.put(need, a);
                    BY_HASH.put(a.hash(), a);
                }
                DEFS.put(id, d);
                ids.add(id);
            } catch (Exception ex) {
                StarSpirits.LOGGER.warn("StarSpirits: el espiritu incluido {} tiene un error: {}", id, ex.getMessage());
            }
        }
        return ids;
    }

    private static byte[] resourceBytes(String file) {
        try (InputStream in = SpiritLibrary.class.getResourceAsStream("/assets/starspirits/espiritus/" + file)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static String resourceText(String file) {
        byte[] b = resourceBytes(file);
        return b == null ? null : new String(b, StandardCharsets.UTF_8);
    }

    /** Manda la lista de espiritus (json + archivos que usa cada uno) a un jugador. */
    public static synchronized void send(ServerPlayer p) {
        List<Net.Def> list = new ArrayList<>();
        for (SpiritDef d : DEFS.values()) {
            List<Net.FileRef> refs = new ArrayList<>();
            for (String f : d.files()) {
                Asset a = ASSETS.get(f);
                if (a != null) {
                    refs.add(new Net.FileRef(f, a.hash(), a.data().length));
                }
            }
            list.add(new Net.Def(d.id, d.json, refs));
        }
        PacketDistributor.sendToPlayer(p, new Net.Defs(list));
    }

    // ------------------------------------------------------------------ sorteo

    /** Sortea un espiritu: primero la rareza (probabilidades de la config), despues uno de esa rareza segun el bioma. */
    public static synchronized SpiritDef roll(ServerLevel level, BlockPos pos, RandomSource rnd) {
        Map<Rarity, List<SpiritDef>> by = new LinkedHashMap<>();
        for (SpiritDef d : DEFS.values()) {
            if (d.enabled && d.weight > 0) {
                by.computeIfAbsent(d.rarity, r -> new ArrayList<>()).add(d);
            }
        }
        if (by.isEmpty()) {
            return null;
        }
        int total = 0;
        for (Rarity r : by.keySet()) {
            total += Config.WEIGHTS.getOrDefault(r, r.defaultWeight);
        }
        Rarity pick = by.keySet().iterator().next();
        if (total > 0) {
            int x = rnd.nextInt(total);
            for (Rarity r : by.keySet()) {
                x -= Config.WEIGHTS.getOrDefault(r, r.defaultWeight);
                if (x < 0) {
                    pick = r;
                    break;
                }
            }
        }
        Holder<Biome> biome = level.getBiome(pos);
        List<SpiritDef> pool = by.get(pick);
        int sum = 0;
        int[] w = new int[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            w[i] = pool.get(i).weight * (matches(biome, pool.get(i).biomes) ? 3 : 1);
            sum += w[i];
        }
        int x = rnd.nextInt(Math.max(1, sum));
        for (int i = 0; i < pool.size(); i++) {
            x -= w[i];
            if (x < 0) {
                return pool.get(i);
            }
        }
        return pool.get(0);
    }

    private static boolean matches(Holder<Biome> biome, List<String> list) {
        for (String s : list) {
            try {
                if (s.startsWith("#")) {
                    if (biome.is(TagKey.create(Registries.BIOME, ResourceLocation.parse(s.substring(1))))) {
                        return true;
                    }
                } else if (biome.is(ResourceLocation.parse(s))) {
                    return true;
                }
            } catch (Exception ignored) {
                // nombre mal escrito
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ ejemplos

    private static void writeExamples(Path dir) {
        copy("/assets/starspirits/espiritus/LEEME.txt", dir.resolve("LEEME.txt"));
        copy("/assets/starspirits/espiritus/ejemplo_textura.png", dir.resolve("ejemplo_textura.png"));
    }

    private static void copy(String res, Path to) {
        if (Files.exists(to)) {
            return;
        }
        try (InputStream in = SpiritLibrary.class.getResourceAsStream(res)) {
            if (in != null) {
                Files.write(to, in.readAllBytes());
            }
        } catch (IOException ignored) {
            // sin ejemplo
        }
    }

    static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (Exception e) {
            return Integer.toHexString(java.util.Arrays.hashCode(data));
        }
    }
}
