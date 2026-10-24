package com.tunombre.cruorfight;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeConfigSpec;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Acceso por reflexion a los datos internos de Cruor (sus clases son privadas del paquete). */
final class CruorAccess {
    record SkinInfo(ResourceLocation location, int copies) { }

    private static boolean tried, ok;
    private static Object bodies;
    private static Method body;
    private static Field bodySkin, skinLocation, skinCopies, skinCount;
    private static ForgeConfigSpec.ConfigValue<?> woundsCfg;

    private CruorAccess() { }

    private static void init() {
        if (tried) return;
        tried = true;
        try {
            Class<?> cb = Class.forName("com.raiiiden.cruor.client.ClientBlood");
            Field f = cb.getDeclaredField("BODIES");
            f.setAccessible(true);
            bodies = f.get(null);
            body = bodies.getClass().getDeclaredMethod("body", int.class);
            body.setAccessible(true);
            Class<?> bc = Class.forName("com.raiiiden.cruor.client.body.BodyBlood$Body");
            bodySkin = bc.getDeclaredField("skin");
            bodySkin.setAccessible(true);
            Class<?> sc = Class.forName("com.raiiiden.cruor.client.body.WoundSkin");
            skinLocation = sc.getDeclaredField("location");
            skinCopies = sc.getDeclaredField("copies");
            skinCount = sc.getDeclaredField("count");
            skinLocation.setAccessible(true);
            skinCopies.setAccessible(true);
            skinCount.setAccessible(true);
            try {
                Field w = Class.forName("com.raiiiden.cruor.CruorClientConfig").getDeclaredField("WOUNDS");
                w.setAccessible(true);
                woundsCfg = (ForgeConfigSpec.ConfigValue<?>) w.get(null);
            } catch (Throwable ignored) { }
            ok = true;
        } catch (Throwable t) {
            CruorFight.LOGGER.error("CruorFight: no pude enganchar Cruor (version distinta?)", t);
        }
    }

    static boolean woundsEnabled() {
        init();
        if (woundsCfg == null) return true;
        try { return Boolean.TRUE.equals(woundsCfg.get()); } catch (Throwable t) { return true; }
    }

    /** textura de heridas del cuerpo de esta entidad, o null si no hay nada que dibujar */
    static SkinInfo skin(LivingEntity e) {
        init();
        if (!ok) return null;
        try {
            Object b = body.invoke(bodies, e.getId());
            if (b == null) return null;
            Object s = bodySkin.get(b);
            if (s == null || skinCount.getInt(s) <= 0) return null;
            return new SkinInfo((ResourceLocation) skinLocation.get(s), Math.max(1, skinCopies.getInt(s)));
        } catch (Throwable t) {
            ok = false;
            CruorFight.LOGGER.error("CruorFight: fallo leyendo datos de Cruor", t);
            return null;
        }
    }
}
