package com.tunombre.taaef;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * Lectura del estado de Tacz Animation Additions por reflexion (sin depender de su codigo al compilar): si TAA no
 * esta, o en otra version cambia algun metodo, esa parte simplemente no se anima.
 */
final class Taa {
    private Taa() { }

    private static final String P = "com.locknar.taczammocheck.";
    private static boolean tried, present;
    private static Method kick, ammo, prone, hrEntity, hrLocal, run, gunInfo, kind, wallInfo, wallActive, wallLocal;

    static boolean present() {
        if (!tried) {
            tried = true;
            present = ModList.get().isLoaded("locknar_doorkick");
            if (present) {
                kick = find(P + "tactical.client.KickAnimationClient", "isThirdPersonKickActive", int.class);
                ammo = find(P + "client.AmmoCheckThirdPersonClient", "active", AbstractClientPlayer.class);
                prone = find(P + "prone.ProneClient", "active", AbstractClientPlayer.class);
                hrEntity = find(P + "tactical.client.TaczHighReadyClient", "isHighReadyActiveForEntity", int.class);
                hrLocal = find(P + "tactical.client.TaczHighReadyClient", "isLocalHighReadyActive");
                run = find(P + "tactical.client.GunRunAnimationClient", "isOverridingLowerBody", int.class);
                gunInfo = find(P + "tactical.TaczGunCompat", "getGunInfo", ItemStack.class);
                kind = find(P + "tactical.TaczGunCompat$GunInfo", "kind");
                wallLocal = find(P + "tactical.client.TaczWallObstructionClient", "isObstructed");
                wallInfo = find(P + "tactical.client.TaczWallObstructionThirdPersonClient", "getMainHandGunInfo", Player.class);
                try {
                    wallActive = find(P + "tactical.client.TaczWallObstructionThirdPersonClient", "isWallOrKickRaiseActive",
                            AbstractClientPlayer.class, Class.forName(P + "tactical.TaczGunCompat$GunInfo"));
                } catch (Throwable ignored) { }
                TaaEF.LOG.info("TAA encontrado: patada={} municion={} tierra={} highready={} correr={} pared={}",
                        kick != null, ammo != null, prone != null, hrEntity != null, run != null, wallActive != null);
            }
        }
        return present;
    }

    private static Method find(String cls, String name, Class<?>... args) {
        try {
            Method m = Class.forName(cls).getDeclaredMethod(name, args);
            m.setAccessible(true);
            return m;
        } catch (Throwable t) {
            TaaEF.LOG.warn("TAA: no se encontro {}.{} ({})", cls, name, t.toString());
            return null;
        }
    }

    private static boolean bool(Method m, Object target, Object... args) {
        if (m == null) return false;
        try {
            return m.invoke(target, args) instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean kick(AbstractClientPlayer p) { return bool(kick, null, p.getId()); }

    static boolean ammoCheck(AbstractClientPlayer p) { return bool(ammo, null, p); }

    static boolean prone(AbstractClientPlayer p) { return bool(prone, null, p); }

    static boolean highReady(AbstractClientPlayer p, boolean local) {
        return (local && bool(hrLocal, null)) || bool(hrEntity, null, p.getId());
    }

    static boolean gunRun(AbstractClientPlayer p) { return bool(run, null, p.getId()); }

    static boolean wall(AbstractClientPlayer p, boolean local) {
        if (local && bool(wallLocal, null)) return true;
        if (wallInfo == null || wallActive == null) return false;
        try {
            Object info = wallInfo.invoke(null, p);
            return info != null && bool(wallActive, null, p, info);
        } catch (Throwable t) {
            return false;
        }
    }

    /** null = no es un arma de TACZ; si no, "PISTOL", "SMG", "RIFLE", "SHOTGUN" o "SNIPER" */
    static String gunKind(ItemStack s) {
        if (s.isEmpty() || gunInfo == null) return null;
        try {
            Object info = gunInfo.invoke(null, s);
            if (info == null) return null;
            Object k = kind == null ? null : kind.invoke(info);
            return k == null ? "RIFLE" : k.toString();
        } catch (Throwable t) {
            return null;
        }
    }
}
