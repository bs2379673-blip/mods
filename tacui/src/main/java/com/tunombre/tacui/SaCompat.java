package com.tunombre.tacui;

import net.minecraft.world.inventory.Slot;

/** Casillas de Survivors Arsenal (mochila y ropa con bolsillos) dentro del menu del inventario, por reflexion. */
public final class SaCompat {
    private SaCompat() {}

    private static final String BP = "com.ogaba.sa_survival.menu.InventoryBackpackSlot";
    private static final String CL = "com.ogaba.sa_survival.menu.InventoryClothingSlot";

    public static boolean isBackpack(Slot s) { return s.getClass().getName().equals(BP); }

    /** PANTS / CHEST... o null si no es una casilla de ropa de SA. */
    public static String clothingArea(Slot s) {
        if (!s.getClass().getName().equals(CL)) return null;
        try {
            Object a = s.getClass().getMethod("getStorageArea").invoke(s);
            return a == null ? "CLOTHING" : a.toString();
        } catch (Throwable t) { return "CLOTHING"; }
    }
}
