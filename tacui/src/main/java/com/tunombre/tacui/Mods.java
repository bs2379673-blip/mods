package com.tunombre.tacui;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;

import java.lang.reflect.Method;

/**
 * Lectura de datos de otros mods por reflexion: si un mod no esta o cambia su API, ese dato simplemente no se muestra.
 * Cada valor devuelve -1 cuando no esta disponible.
 */
public final class Mods {
    private Mods() {}

    // ---- Thirst Was Taken (0..20)
    private static Capability<?> thirstCap; private static Method thirstGet; private static boolean thirstTried;

    public static float thirst(Player p) {
        try {
            if (!thirstTried) {
                thirstTried = true;
                thirstCap = (Capability<?>) Class.forName("dev.ghen.thirst.foundation.common.capability.ModCapabilities").getField("PLAYER_THIRST").get(null);
                thirstGet = Class.forName("dev.ghen.thirst.foundation.common.capability.IThirst").getMethod("getThirst");
            }
            if (thirstCap == null) return -1;
            Object o = p.getCapability(thirstCap).resolve().orElse(null);
            return o == null ? -1 : ((Number) thirstGet.invoke(o)).floatValue() / 20f;
        } catch (Throwable t) { thirstCap = null; return -1; }
    }

    // ---- Sanity: Descent Into Madness (0..1)
    private static Capability<?> sanityCap; private static Method sanityGet; private static boolean sanityTried;

    public static float sanity(Player p) {
        try {
            if (!sanityTried) {
                sanityTried = true;
                sanityCap = (Capability<?>) Class.forName("croissantnova.sanitydim.capability.SanityProvider").getField("CAP").get(null);
                sanityGet = Class.forName("croissantnova.sanitydim.capability.ISanity").getMethod("getSanity");
            }
            if (sanityCap == null) return -1;
            Object o = p.getCapability(sanityCap).resolve().orElse(null);
            return o == null ? -1 : Math.max(0f, Math.min(1f, ((Number) sanityGet.invoke(o)).floatValue()));
        } catch (Throwable t) { sanityCap = null; return -1; }
    }

    // ---- Epic Fight (stamina 0..1)
    private static Method efGetPatch, efStamina, efMax; private static Class<?> efPlayerPatch; private static boolean efTried, efDead;

    public static float stamina(Player p) {
        if (efDead) return -1;
        try {
            if (!efTried) {
                efTried = true;
                efPlayerPatch = Class.forName("yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch");
                efGetPatch = Class.forName("yesman.epicfight.world.capabilities.EpicFightCapabilities").getMethod("getEntityPatch", net.minecraft.world.entity.Entity.class, Class.class);
                efStamina = efPlayerPatch.getMethod("getStamina");
                efMax = efPlayerPatch.getMethod("getMaxStamina");
            }
            Object patch = efGetPatch.invoke(null, p, efPlayerPatch);
            if (patch == null) return -1;
            float max = ((Number) efMax.invoke(patch)).floatValue();
            return max <= 0 ? -1 : ((Number) efStamina.invoke(patch)).floatValue() / max;
        } catch (Throwable t) { efDead = true; return -1; }
    }

    // ---- TACZ: arma en la mano
    public static final class Gun { public int mag, reserve; public String mode = ""; }

    private static Method gGet, gAmmo, gBarrel, gMode, aGet, aIs; private static boolean gTried, gDead;
    private static Gun gunCache; private static int gunCacheAge = 99;

    public static Gun gun(Player p) {
        if (gDead) return null;
        try {
            if (!gTried) {
                gTried = true;
                Class<?> igun = Class.forName("com.tacz.guns.api.item.IGun");
                gGet = igun.getMethod("getIGunOrNull", ItemStack.class);
                gAmmo = igun.getMethod("getCurrentAmmoCount", ItemStack.class);
                gBarrel = igun.getMethod("hasBulletInBarrel", ItemStack.class);
                gMode = igun.getMethod("getFireMode", ItemStack.class);
                Class<?> iammo = Class.forName("com.tacz.guns.api.item.IAmmo");
                aGet = iammo.getMethod("getIAmmoOrNull", ItemStack.class);
                aIs = iammo.getMethod("isAmmoOfGun", ItemStack.class, ItemStack.class);
            }
            ItemStack s = p.getMainHandItem();
            if (s.isEmpty()) return null;
            Object g = gGet.invoke(null, s);
            if (g == null) return null;
            if (gunCache != null && ++gunCacheAge < 4) {   // la reserva se recuenta cada pocos fotogramas
                gunCache.mag = ((Number) gAmmo.invoke(g, s)).intValue() + (((Boolean) gBarrel.invoke(g, s)) ? 1 : 0);
                return gunCache;
            }
            gunCacheAge = 0;
            Gun r = new Gun();
            r.mag = ((Number) gAmmo.invoke(g, s)).intValue() + (((Boolean) gBarrel.invoke(g, s)) ? 1 : 0);
            Object m = gMode.invoke(g, s);
            r.mode = m == null ? "" : m.toString();
            int res = 0;
            for (ItemStack a : p.getInventory().items) {
                if (a.isEmpty()) continue;
                Object ia = aGet.invoke(null, a);
                if (ia != null && (Boolean) aIs.invoke(ia, a, s)) res += a.getCount();
            }
            r.reserve = res;
            gunCache = r;
            return r;
        } catch (ClassNotFoundException e) { gDead = true; return null;
        } catch (Throwable t) { return null; }
    }
}
