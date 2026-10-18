package com.tunombre.dancers.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.tunombre.dancers.Dancers;
import com.tunombre.dancers.rig.Rig;
import com.tunombre.dancers.rig.Rig3;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/** Carga (y guarda en cache) esqueleto, modelos y animaciones desde los recursos. Se vacia al recargar recursos. */
public final class DancerAssets {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Object LOADING = new Object(), FAILED = new Object();
    private static Rig.Skeleton skeleton;
    private static boolean skeletonFailed;
    private static final Map<String, Rig.Model> MODELS = new HashMap<>();
    private static final Set<String> MODEL_FAILED = new HashSet<>();
    private static final Map<String, Object> ANIMS = new ConcurrentHashMap<>();
    private static final Map<String, Rig3.Model3> MODELS3 = new HashMap<>();
    private static final Set<String> NOT3 = new HashSet<>();
    private static final Map<String, Object> ANIMS3 = new ConcurrentHashMap<>();

    private DancerAssets() {}

    public static void clear() {
        skeleton = null; skeletonFailed = false; MODELS.clear(); MODEL_FAILED.clear(); ANIMS.clear(); MODELS3.clear(); NOT3.clear(); ANIMS3.clear();
    }

    private static JsonObject readJson(ResourceManager rm, ResourceLocation rl) throws Exception {
        Optional<net.minecraft.server.packs.resources.Resource> r = rm.getResource(rl);
        if (r.isEmpty()) throw new java.io.FileNotFoundException(rl.toString());
        try (InputStream in = r.get().open(); InputStreamReader rd = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(rd).getAsJsonObject();
        }
    }

    public static Rig.Skeleton skeleton() {
        if (skeleton == null && !skeletonFailed) {
            try { skeleton = Rig.Skeleton.load(readJson(Minecraft.getInstance().getResourceManager(), new ResourceLocation(Dancers.MODID, "skeleton/ef_biped.json"))); }
            catch (Throwable t) { skeletonFailed = true; LOG.error("Dancers: no se pudo leer el esqueleto", t); }
        }
        return skeleton;
    }

    public static Rig.Model model(String name) {
        Rig.Model m = MODELS.get(name);
        if (m != null || MODEL_FAILED.contains(name)) return m;
        Rig.Skeleton sk = skeleton();
        if (sk == null) return null;
        try {
            m = Rig.Model.load(readJson(Minecraft.getInstance().getResourceManager(), new ResourceLocation(Dancers.MODID, "dancer_models/" + name + ".json")), sk);
            ResourceLocation[] tex = new ResourceLocation[m.textures.length];
            for (int i = 0; i < tex.length; i++) tex[i] = m.textures[i].contains(":") ? new ResourceLocation(m.textures[i]) : new ResourceLocation(Dancers.MODID, "textures/dancers/" + m.textures[i]);
            m.userData = tex;
            MODELS.put(name, m);
        } catch (Throwable t) {
            MODEL_FAILED.add(name); LOG.error("Dancers: no se pudo leer el modelo '{}'", name, t);
        }
        return m;
    }

    /** Devuelve la animacion si ya esta cargada; si no, la empieza a cargar en segundo plano y devuelve null. */
    public static Rig.Anim anim(String id) {
        Object o = ANIMS.get(id);
        if (o instanceof Rig.Anim a) return a;
        if (o != null) return null;
        Rig.Skeleton sk = skeleton();
        if (sk == null || ANIMS.putIfAbsent(id, LOADING) != null) return null;
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        CompletableFuture.runAsync(() -> {
            try {
                ResourceLocation base = new ResourceLocation(id);
                ResourceLocation rl = new ResourceLocation(base.getNamespace(), "animmodels/animations/" + base.getPath() + ".json");
                Optional<net.minecraft.server.packs.resources.Resource> r = rm.getResource(rl);
                if (r.isEmpty()) throw new java.io.FileNotFoundException(rl.toString());
                try (InputStream in = r.get().open()) {
                    ANIMS.put(id, Rig.Anim.load(in, sk));
                }
            } catch (Throwable t) {
                ANIMS.put(id, FAILED); LOG.error("Dancers: no se pudo leer la animacion '{}'", id, t);
            }
        }, Util.backgroundExecutor());
        return null;
    }

    /** Modelo formato 3 (huesos reales del rig); null si el modelo no es de ese formato o no se pudo leer. */
    public static Rig3.Model3 model3(String name) {
        Rig3.Model3 m = MODELS3.get(name);
        if (m != null || NOT3.contains(name)) return m;
        try {
            JsonObject o = readJson(Minecraft.getInstance().getResourceManager(), new ResourceLocation(Dancers.MODID, "dancer_models/" + name + ".json"));
            if (!o.has("format") || o.get("format").getAsInt() != 3) { NOT3.add(name); return null; }
            m = Rig3.Model3.load(o);
            ResourceLocation[] tex = new ResourceLocation[m.textures.length];
            for (int i = 0; i < tex.length; i++) tex[i] = m.textures[i].contains(":") ? new ResourceLocation(m.textures[i]) : new ResourceLocation(Dancers.MODID, "textures/dancers/" + m.textures[i]);
            m.userData = tex;
            MODELS3.put(name, m);
        } catch (Throwable t) {
            NOT3.add(name); LOG.error("Dancers: no se pudo leer el modelo (formato 3) '{}'", name, t);
        }
        return m;
    }

    /** Animacion bakeada (.dnc) de un modelo formato 3. El nombre es lo ultimo de la ruta del id (p. ej. mikuevent:biped/miku/melt -> melt). */
    public static Rig3.Anim3 anim3(String model, String id) {
        String base = new ResourceLocation(id).getPath();
        base = base.substring(base.lastIndexOf('/') + 1);
        String key = model + "|" + base;
        Object o = ANIMS3.get(key);
        if (o instanceof Rig3.Anim3 a) return a;
        if (o != null || ANIMS3.putIfAbsent(key, LOADING) != null) return null;
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        final String fb = base;
        CompletableFuture.runAsync(() -> {
            try {
                ResourceLocation rl = new ResourceLocation(Dancers.MODID, "dancer_anims/" + model + "/" + fb + ".dnc");
                Optional<net.minecraft.server.packs.resources.Resource> r = rm.getResource(rl);
                if (r.isEmpty()) throw new java.io.FileNotFoundException(rl.toString());
                try (InputStream in = r.get().open()) { ANIMS3.put(key, Rig3.Anim3.load(in)); }
            } catch (Throwable t) {
                ANIMS3.put(key, FAILED); LOG.error("Dancers: no se pudo leer la animacion '{}' del modelo '{}'", fb, model, t);
            }
        }, Util.backgroundExecutor());
        return null;
    }
}
