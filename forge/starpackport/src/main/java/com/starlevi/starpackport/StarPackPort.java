package com.starlevi.starpackport;

import com.mojang.logging.LogUtils;
import com.starlevi.starpackport.convert.PackConverter;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * StarPackPort — propiedad de Starlevi.
 * Hace que los paquetes de recursos de 1.20.2 a 1.21.11 funcionen en Forge 1.20.1:
 * los convierte una vez (se guardan en la carpeta starpackport) y el juego usa la copia convertida.
 */
@Mod(StarPackPort.MODID)
public final class StarPackPort {
    public static final String MODID = "starpackport";
    public static final Logger LOG = LogUtils.getLogger();
    private static final Map<Path, Path> DONE = new HashMap<>();

    public StarPackPort() {
        // no hace falta en el servidor
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> IExtensionPoint.DisplayTest.IGNORESERVERONLY, (a, b) -> true));
    }

    /** Solo los packs de la carpeta resourcepacks del juego. */
    public static boolean isResourcePack(Path path) {
        if (!FMLEnvironment.dist.isClient()) {
            return false;
        }
        try {
            Path dir = FMLPaths.GAMEDIR.get().resolve("resourcepacks").toAbsolutePath().normalize();
            Path parent = path.toAbsolutePath().normalize().getParent();
            return parent != null && parent.equals(dir);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Devuelve la carpeta con el pack convertido, o null si no hace falta convertirlo. */
    public static synchronized Path converted(Path src) {
        Path key = src.toAbsolutePath().normalize();
        if (DONE.containsKey(key)) {
            Path p = DONE.get(key);
            if (p == null || Files.isDirectory(p)) {
                return p;
            }
        }
        Path result = null;
        try {
            if (PackConverter.needsConversion(src)) {
                String name = src.getFileName().toString().replaceAll("\\.zip$", "").replaceAll("[^\\p{L}\\p{N}._ +()\\[\\]-]", "_");
                Path out = FMLPaths.GAMEDIR.get().resolve(MODID).resolve("convertidos").resolve(name);
                String stamp = PackConverter.VERSION + "|" + stamp(src);
                Path stampFile = out.resolve("starpackport.stamp");
                if (Files.isRegularFile(stampFile) && Files.readString(stampFile).equals(stamp)) {
                    result = out;
                } else {
                    long t = System.currentTimeMillis();
                    Files.createDirectories(out.getParent());
                    List<String> report = PackConverter.convert(src, out, StarPackPort::vanilla, LOG::warn);
                    Files.writeString(stampFile, stamp);
                    LOG.info("StarPackPort: convertido '{}' en {} ms", src.getFileName(), System.currentTimeMillis() - t);
                    for (String r : report) {
                        LOG.info("StarPackPort:   {}", r);
                    }
                    result = out;
                }
            }
        } catch (Throwable e) {
            LOG.error("StarPackPort: no pude convertir '{}', se usa tal cual", src.getFileName(), e);
            result = null;
        }
        DONE.put(key, result);
        return result;
    }

    private static String stamp(Path src) throws java.io.IOException {
        if (Files.isDirectory(src)) {
            long[] acc = {0, 0};
            try (var s = Files.walk(src)) {
                s.filter(Files::isRegularFile).forEach(p -> {
                    try {
                        acc[0] += Files.size(p);
                        acc[1] = Math.max(acc[1], Files.getLastModifiedTime(p).toMillis());
                    } catch (java.io.IOException ignored) {
                    }
                });
            }
            return acc[0] + "|" + acc[1];
        }
        return Files.size(src) + "|" + Files.getLastModifiedTime(src).toMillis();
    }

    /** Lee un archivo del juego base, p. ej. "assets/minecraft/textures/gui/icons.png". */
    static byte[] vanilla(String path) {
        try {
            String[] p = path.split("/", 3);
            if (p.length == 3 && p[0].equals("assets")) {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.getVanillaPackResources() != null) {
                    IoSupplier<InputStream> s = mc.getVanillaPackResources().getResource(PackType.CLIENT_RESOURCES, new ResourceLocation(p[1], p[2]));
                    if (s != null) {
                        try (InputStream in = s.get()) {
                            return in.readAllBytes();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try (InputStream in = Minecraft.class.getResourceAsStream("/" + path)) {
            return in == null ? null : in.readAllBytes();
        } catch (Throwable e) {
            return null;
        }
    }
}
