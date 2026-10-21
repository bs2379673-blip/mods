package com.tunombre.obscurenpc.client;

import com.mojang.authlib.GameProfile;
import com.tunombre.obscurenpc.Actor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Skin del NPC: nombre de jugador (se descarga una vez) o textura de un resource pack ("espacio:ruta"). */
public final class SkinCache {
    private static final Map<String, ResourceLocation> DONE = new HashMap<>();
    private static final Set<String> LOADING = new HashSet<>();

    private SkinCache() { }

    public static ResourceLocation get(Actor a) {
        String s = a.getEntityData().get(Actor.SKIN);
        ResourceLocation def = DefaultPlayerSkin.getDefaultSkin(a.getUUID());
        if (s == null || s.isBlank()) return def;
        if (s.contains(":")) {
            try { return new ResourceLocation(s); } catch (RuntimeException e) { return def; }
        }
        ResourceLocation got = DONE.get(s);
        if (got != null) return got;
        if (LOADING.add(s)) {
            final String name = s;
            SkullBlockEntity.updateGameprofile(new GameProfile(null, name), profile -> {
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> DONE.put(name, mc.getSkinManager().getInsecureSkinLocation(profile)));
            });
        }
        return def;
    }

    public static void clear() { DONE.clear(); LOADING.clear(); }
}
