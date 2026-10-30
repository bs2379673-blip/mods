package com.annis.npc.client;

import com.annis.npc.AnnisNpc;
import com.annis.npc.entity.AnnisNpcEntity;
import com.annis.npc.compat.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * Avatares de Figura en los NPC, por reflexion (no hace falta Figura para compilar).
 * Cada NPC guarda el nombre de un avatar (carpeta con avatar.json o archivo .zip, en cualquier
 * subcarpeta de la carpeta de avatares de Figura); cada cliente lo carga localmente y se lo asigna
 * al NPC con la API de avatares de entidad de Figura.
 */
public final class FiguraBridge {
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("figura");
    private static boolean broken;

    private static final class Pending {
        final Object user;
        final String name;
        int ticks;

        Pending(Object user, String name) {
            this.user = user;
            this.name = name;
        }
    }

    private static final Map<AnnisNpcEntity, String> APPLIED = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<AnnisNpcEntity, Pending> PENDING = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Path, FileSystem> ZIPS = new HashMap<>();

    private static Class<?> managerClass;
    private static Class<?> userClass;
    private static Class<?> loaderClass;
    private static Method loadEntityAvatar;
    private static Method loadAvatar;
    private static Method getMainAvatar;
    private static Method avatarsDirMethod;
    private static Method resetWatchKeys;
    private static Method addWatchKey;
    private static Field lastLoadedPath;
    private static Field watchKeys;
    private static Field nbtField;
    private static Field cemField;

    private FiguraBridge() {
    }

    public static boolean available() {
        return LOADED && !broken;
    }

    private static void tell(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(msg), false);
        }
    }

    /** Carpeta de avatares de Figura (respeta la carpeta configurada en Figura). */
    public static Path avatarsDir() {
        try {
            init();
            if (avatarsDirMethod != null) {
                return (Path) avatarsDirMethod.invoke(null);
            }
        } catch (Throwable ignored) {
        }
        return FabricLoader.getInstance().getGameDir().resolve("figura").resolve("avatars");
    }

    private static String baseName(String s) {
        String n = s.trim();
        if (n.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            n = n.substring(0, n.length() - 4);
        }
        return n;
    }

    /** Todos los avatares que se pueden usar (nombre de carpeta o de .zip). */
    public static List<String> list() {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Path dir = avatarsDir();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> stream = Files.walk(dir, 6)) {
                stream.forEach(p -> {
                    String file = p.getFileName() == null ? "" : p.getFileName().toString();
                    if (Files.isDirectory(p) && Files.isRegularFile(p.resolve("avatar.json"))) {
                        names.add(file);
                    } else if (file.toLowerCase(Locale.ROOT).endsWith(".zip") && Files.isRegularFile(p)) {
                        names.add(baseName(file));
                    }
                });
            } catch (Exception e) {
                AnnisNpc.LOGGER.warn("No se pudo listar los avatares de Figura", e);
            }
        }
        return new ArrayList<>(names);
    }

    /** Busca el avatar por nombre (sin distinguir mayusculas), en carpetas o .zip. */
    private static Path resolve(String name) {
        String want = baseName(name);
        Path dir = avatarsDir();
        if (!Files.isDirectory(dir)) {
            return null;
        }
        List<Path> candidates = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(dir, 6)) {
            stream.forEach(candidates::add);
        } catch (Exception e) {
            return null;
        }
        for (Path p : candidates) {
            if (Files.isDirectory(p) && p.getFileName() != null && p.getFileName().toString().equalsIgnoreCase(want)
                    && Files.isRegularFile(p.resolve("avatar.json"))) {
                return p;
            }
        }
        for (Path p : candidates) {
            String file = p.getFileName() == null ? "" : p.getFileName().toString();
            if (Files.isRegularFile(p) && file.toLowerCase(Locale.ROOT).endsWith(".zip") && baseName(file).equalsIgnoreCase(want)) {
                Path inside = openZip(p);
                if (inside != null) {
                    return inside;
                }
            }
        }
        return null;
    }

    private static Path openZip(Path zip) {
        try {
            FileSystem fs = ZIPS.get(zip);
            if (fs == null || !fs.isOpen()) {
                fs = FileSystems.newFileSystem(zip);
                ZIPS.put(zip, fs);
            }
            Path root = fs.getPath("/");
            if (Files.isRegularFile(root.resolve("avatar.json"))) {
                return root;
            }
            try (Stream<Path> s = Files.walk(root, 4)) {
                return s.filter(p -> Files.isDirectory(p) && Files.isRegularFile(p.resolve("avatar.json"))).findFirst().orElse(null);
            }
        } catch (Exception e) {
            AnnisNpc.LOGGER.warn("No se pudo abrir el avatar {}", zip, e);
            return null;
        }
    }

    private static void init() throws Exception {
        if (managerClass != null) {
            return;
        }
        managerClass = Class.forName("org.figuramc.figura.avatar.AvatarManager");
        userClass = Class.forName("org.figuramc.figura.avatar.UserData");
        loaderClass = Class.forName("org.figuramc.figura.avatar.local.LocalAvatarLoader");
        for (Method m : managerClass.getMethods()) {
            if (m.getName().equals("loadEntityAvatar") && m.getParameterCount() == 2) {
                loadEntityAvatar = m;
            }
        }
        for (Method m : loaderClass.getMethods()) {
            if (m.getName().equals("loadAvatar") && m.getParameterCount() == 2) {
                loadAvatar = m;
            }
        }
        getMainAvatar = userClass.getMethod("getMainAvatar");
        Class<?> avatarClass = Class.forName("org.figuramc.figura.avatar.Avatar");
        nbtField = avatarClass.getField("nbt");
        cemField = managerClass.getDeclaredField("LOADED_CEM");
        cemField.setAccessible(true);
        try {
            avatarsDirMethod = Class.forName("org.figuramc.figura.avatar.local.LocalAvatarFetcher").getMethod("getLocalAvatarDirectory");
        } catch (Throwable ignored) {
            avatarsDirMethod = null;
        }
        // para no romper la recarga automatica del avatar del propio jugador
        try {
            resetWatchKeys = loaderClass.getMethod("resetWatchKeys");
            addWatchKey = loaderClass.getDeclaredMethod("addWatchKey", Path.class, BiConsumer.class);
            addWatchKey.setAccessible(true);
            lastLoadedPath = loaderClass.getDeclaredField("lastLoadedPath");
            lastLoadedPath.setAccessible(true);
            watchKeys = loaderClass.getDeclaredField("KEYS");
            watchKeys.setAccessible(true);
        } catch (Throwable ignored) {
            resetWatchKeys = null;
        }
        if (loadEntityAvatar == null || loadAvatar == null) {
            throw new NoSuchMethodException("API de avatares de Figura no encontrada");
        }
    }

    /** Carga el avatar del NPC sin tocar lo que Figura vigila para el avatar del jugador. */
    @SuppressWarnings("unchecked")
    private static void loadKeepingPlayer(Path path, Object user) throws Exception {
        Path previous = lastLoadedPath != null ? (Path) lastLoadedPath.get(null) : null;
        loadAvatar.invoke(null, path, user);
        if (resetWatchKeys != null) {
            resetWatchKeys.invoke(null);
            if (previous != null) {
                lastLoadedPath.set(null, previous);
                Map<Path, Object> keys = (Map<Path, Object>) watchKeys.get(null);
                BiConsumer<Path, Object> put = keys::put;
                addWatchKey.invoke(null, previous, put);
            }
        }
    }

    /** Se llama cada tick de cliente por cada NPC. */
    @SuppressWarnings("unchecked")
    public static void sync(AnnisNpcEntity npc) {
        if (!available()) {
            return;
        }
        try {
            String want = npc.getFigura();
            String have = APPLIED.getOrDefault(npc, "");
            Pending pending = PENDING.get(npc);

            if (!want.equals(have)) {
                init();
                Map<Object, Object> cem = (Map<Object, Object>) cemField.get(null);
                Object old = cem.remove(npc);
                if (old != null) {
                    old.getClass().getMethod("clean").invoke(old);
                }
                PENDING.remove(npc);
                APPLIED.put(npc, want);
                if (want.isEmpty()) {
                    return;
                }
                Path path = resolve(want);
                if (path == null) {
                    tell("[Annis NPC] No encontre el avatar de Figura \"" + want + "\" en " + avatarsDir()
                            + " (tiene que ser una carpeta con avatar.json o un .zip)");
                    return;
                }
                Object user = userClass.getConstructor(UUID.class).newInstance(npc.getUUID());
                loadKeepingPlayer(path, user);
                PENDING.put(npc, new Pending(user, want));
                return;
            }

            if (pending != null) {
                if (++pending.ticks > 1200) {
                    PENDING.remove(npc);
                    tell("[Annis NPC] El avatar de Figura \"" + pending.name + "\" no termino de cargar (mira si Figura mostro un error)");
                    return;
                }
                Object avatar = getMainAvatar.invoke(pending.user);
                Object nbt = avatar == null ? null : nbtField.get(avatar);
                if (nbt != null) {
                    PENDING.remove(npc);
                    loadEntityAvatar.invoke(null, npc, nbt);
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.displayClientMessage(Component.literal("Avatar de Figura \"" + pending.name + "\" cargado en el NPC"), true);
                    }
                }
            }
        } catch (Throwable t) {
            broken = true;
            AnnisNpc.LOGGER.warn("Integracion con Figura desactivada", t);
            tell("[Annis NPC] La integracion con Figura fallo: " + t);
        }
    }

    /** Fuerza recargar el avatar de un NPC (tras editar sus archivos). */
    public static void reload(AnnisNpcEntity npc) {
        APPLIED.remove(npc);
    }
}
