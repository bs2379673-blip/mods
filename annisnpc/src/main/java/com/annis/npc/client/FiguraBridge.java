package com.annis.npc.client;

import com.annis.npc.AnnisNpc;
import com.annis.npc.entity.AnnisNpcEntity;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.stream.Stream;

/**
 * Avatares de Figura en los NPC, por reflexion (no hace falta Figura para compilar).
 * Cada NPC guarda el nombre de una carpeta de figura/avatars; cada cliente la carga localmente
 * y se la asigna al NPC con la API de avatares de entidad de Figura.
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

    private static Class<?> managerClass;
    private static Class<?> userClass;
    private static Class<?> loaderClass;
    private static Method loadEntityAvatar;
    private static Method loadAvatar;
    private static Method getMainAvatar;
    private static Field nbtField;
    private static Field cemField;

    private FiguraBridge() {
    }

    public static boolean available() {
        return LOADED && !broken;
    }

    public static Path avatarsDir() {
        return FabricLoader.getInstance().getGameDir().resolve("figura").resolve("avatars");
    }

    public static List<String> list() {
        List<String> names = new ArrayList<>();
        Path dir = avatarsDir();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().forEach(names::add);
            } catch (Exception e) {
                AnnisNpc.LOGGER.warn("No se pudo listar los avatares de Figura", e);
            }
        }
        return names;
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
        if (loadEntityAvatar == null || loadAvatar == null) {
            throw new NoSuchMethodException("API de avatares de Figura no encontrada");
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
                // quitar el avatar anterior de este NPC
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
                Path path = avatarsDir().resolve(want);
                if (!Files.isDirectory(path)) {
                    AnnisNpc.LOGGER.warn("No existe el avatar de Figura {}", path);
                    return;
                }
                Object user = userClass.getConstructor(UUID.class).newInstance(npc.getUuid());
                loadAvatar.invoke(null, path, user);
                PENDING.put(npc, new Pending(user, want));
                return;
            }

            if (pending != null) {
                if (++pending.ticks > 1200) {
                    PENDING.remove(npc);
                    AnnisNpc.LOGGER.warn("El avatar de Figura {} tardo demasiado en cargar", pending.name);
                    return;
                }
                Object avatar = getMainAvatar.invoke(pending.user);
                Object nbt = avatar == null ? null : nbtField.get(avatar);
                if (nbt != null) {
                    PENDING.remove(npc);
                    loadEntityAvatar.invoke(null, npc, nbt);
                }
            }
        } catch (Throwable t) {
            broken = true;
            AnnisNpc.LOGGER.warn("Integracion con Figura desactivada: {}", t.toString());
        }
    }
}
