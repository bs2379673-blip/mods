package com.starlevi.starbody.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.starlevi.starbody.StarBody;
import com.starlevi.starbody.Traits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Los rasgos de cada skin que conoce este cliente, sus tiras de pelo y la skin "sin el pelo" (los
 * pixeles que pasan a colgar se sacan de la capa de afuera; en la de adentro quedan mas oscuros,
 * como la raiz del pelo).
 */
public final class ClientTraits {
    private static final Map<String, Traits> BY_SKIN = new HashMap<>();
    private static final Map<String, HairMesh> MESH = new HashMap<>();
    private static final Map<String, Identifier> HIDDEN = new HashMap<>();
    private static final Map<String, DynamicTexture> HIDDEN_TEX = new HashMap<>();
    private static int serial;

    /** Lo que se esta editando ahora (se ve en vivo, todavia sin guardar). */
    static String draftSkin;
    static Traits draft;

    private ClientTraits() {
    }

    public static Traits get(String skin) {
        if (skin.equals(draftSkin) && draft != null) {
            return draft;
        }
        return BY_SKIN.get(skin);
    }

    public static void put(String skin, Traits t) {
        if (t == null || t.empty()) {
            BY_SKIN.remove(skin);
        } else {
            BY_SKIN.put(skin, t);
        }
        changed(skin);
    }

    /** Algo cambio en esa skin: se rearman las tiras y la textura. */
    public static void changed(String skin) {
        MESH.remove(skin);
        HIDDEN.remove(skin);
        DynamicTexture old = HIDDEN_TEX.remove(skin);
        if (old != null) {
            Minecraft.getInstance().execute(old::close);
        }
    }

    public static HairMesh mesh(String skin) {
        Traits t = get(skin);
        if (t == null || t.empty()) {
            return null;
        }
        return MESH.computeIfAbsent(skin, k -> new HairMesh(t));
    }

    /** La skin sin el pelo que cuelga (o null si no hace falta o no se pudo leer). */
    public static Identifier hidden(Identifier skinId) {
        String skin = skinId.toString();
        Identifier id = HIDDEN.get(skin);
        if (id != null || HIDDEN.containsKey(skin)) {
            return id;
        }
        Traits t = get(skin);
        NativeImage img = t == null || t.empty() ? null : read(skinId);
        if (img == null) {
            HIDDEN.put(skin, null);
            return null;
        }
        for (SkinLayout.Face f : SkinLayout.FACES) {
            for (int u = f.u0(); u < f.u0() + f.width(); u++) {
                for (int v = f.v0(); v < f.v0() + f.height(); v++) {
                    if (!t.hair(u, v)) {
                        continue;
                    }
                    if (f.outer()) {
                        img.setPixel(u, v, 0);
                    } else {
                        int c = img.getPixel(u, v);
                        int a = c >>> 24, r = (c >> 16 & 255) * 6 / 10, g = (c >> 8 & 255) * 6 / 10, b = (c & 255) * 6 / 10;
                        img.setPixel(u, v, a << 24 | r << 16 | g << 8 | b);
                    }
                }
            }
        }
        id = Identifier.fromNamespaceAndPath(StarBody.MOD_ID, "sin_pelo/" + (serial++));
        DynamicTexture tex = new DynamicTexture(id::toString, img);
        Minecraft.getInstance().getTextureManager().register(id, tex);
        HIDDEN.put(skin, id);
        HIDDEN_TEX.put(skin, tex);
        return id;
    }

    /** Copia los pixeles de una skin (bajada o del juego). */
    static NativeImage read(Identifier skinId) {
        try {
            Minecraft mc = Minecraft.getInstance();
            AbstractTexture t = mc.getTextureManager().getTexture(skinId);
            if (t instanceof DynamicTexture dt && dt.getPixels() != null) {
                NativeImage src = dt.getPixels();
                NativeImage copy = new NativeImage(src.getWidth(), src.getHeight(), false);
                copy.copyFrom(src);
                return copy.getWidth() == 64 && copy.getHeight() == 64 ? copy : null;
            }
            var res = mc.getResourceManager().getResource(skinId);
            if (res.isPresent()) {
                try (InputStream in = res.get().open()) {
                    NativeImage img = NativeImage.read(in);
                    return img.getWidth() == 64 && img.getHeight() == 64 ? img : null;
                }
            }
        } catch (Exception e) {
            StarBody.LOGGER.warn("StarBody: no pude leer la skin {}: {}", skinId, e.toString());
        }
        return null;
    }

    public static void clear() {
        BY_SKIN.clear();
        for (String s : new java.util.ArrayList<>(HIDDEN_TEX.keySet())) {
            changed(s);
        }
        MESH.clear();
        HIDDEN.clear();
    }
}
