package com.tunombre.tacui;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.UUID;

/** Puente con Cosmetic Armor Reworked por reflexion. Indice de mod: 3 = casco ... 0 = botas (como el inventario de vainilla). */
public final class CosCompat {
    private CosCompat() {}

    private static final String MENU = "lain.mods.cos.impl.inventory.ContainerCosArmor";
    private static Boolean loaded;

    public static boolean loaded() {
        if (loaded == null) {
            try { Class.forName("lain.mods.cos.api.CosArmorAPI"); loaded = true; } catch (Throwable t) { loaded = false; }
        }
        return loaded;
    }

    public static boolean isMenu(AbstractContainerMenu m) { return m != null && m.getClass().getName().startsWith(MENU); }

    /** Casillas de armadura cosmetica del menu (son las clases anonimas ContainerCosArmor$3). */
    public static boolean isCosSlot(Slot s) { return s.getClass().getName().equals(MENU + "$3"); }

    private static Object inv(Player p) {
        try {
            return Class.forName("lain.mods.cos.api.CosArmorAPI").getMethod("getCAStacksClient", UUID.class).invoke(null, p.getUUID());
        } catch (Throwable t) { return null; }
    }

    /** k = 0 casco, 1 peto, 2 pantalones, 3 botas. */
    public static ItemStack stack(Player p, int k) {
        try {
            Object inv = inv(p);
            if (inv == null) return ItemStack.EMPTY;
            Object st = inv.getClass().getMethod("getStackInSlot", int.class).invoke(inv, 3 - k);
            return st instanceof ItemStack s ? s : ItemStack.EMPTY;
        } catch (Throwable t) { return ItemStack.EMPTY; }
    }

    public static boolean skin(Player p, int k) {
        try {
            Object inv = inv(p);
            return inv != null && (Boolean) inv.getClass().getMethod("isSkinArmor", int.class).invoke(inv, 3 - k);
        } catch (Throwable t) { return false; }
    }

    private static void send(Object pkt) throws Throwable {
        Object net = Class.forName("lain.mods.cos.impl.ModObjects").getField("network").get(null);
        Class<?> np = Class.forName("lain.mods.cos.impl.network.NetworkManager$NetworkPacket");
        Method m = net.getClass().getMethod("sendToServer", np);
        m.invoke(net, pkt);
    }

    /** Abre el menu de armadura cosmetica (el mismo paquete que su boton). */
    public static void openCos() {
        try {
            send(Class.forName("lain.mods.cos.impl.network.packet.PacketOpenCosArmorInventory").getConstructor().newInstance());
        } catch (Throwable t) { TacUI.LOGGER.warn("Cosmetic Armor: no se pudo abrir el menu: {}", t.toString()); }
    }

    /** Boton de ocultar: alterna "armadura como piel" de esa pieza (igual que el boton del propio mod). */
    public static void toggleSkin(Player p, int k) {
        try {
            Object inv = inv(p);
            if (inv == null) return;
            int idx = 3 - k;
            boolean now = !(Boolean) inv.getClass().getMethod("isSkinArmor", int.class).invoke(inv, idx);
            inv.getClass().getMethod("setSkinArmor", int.class, boolean.class).invoke(inv, idx, now);
            send(Class.forName("lain.mods.cos.impl.network.packet.PacketSetSkinArmor").getConstructor(int.class, boolean.class).newInstance(idx, now));
        } catch (Throwable t) { TacUI.LOGGER.warn("Cosmetic Armor: no se pudo alternar la pieza: {}", t.toString()); }
    }
}
