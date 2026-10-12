package com.tunombre.tacui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Puente con Curios por reflexion: si Curios no esta (o cambia algo) simplemente no hace nada. */
public final class CuriosCompat {
    private CuriosCompat() {}

    private static final String BASE = "top.theillusivec4.curios.";
    private static final String MENU = BASE + "common.inventory.container.CuriosContainer";

    public static boolean loaded() {
        try { return ModList.get().isLoaded("curios"); } catch (Throwable t) { return false; }
    }

    public static boolean isMenu(AbstractContainerMenu m) {
        return m != null && m.getClass().getName().startsWith(MENU);
    }

    private static void send(String pktClass, Class<?>[] types, Object... args) {
        try {
            Class<?> pc = Class.forName(BASE + "common.network.client." + pktClass);
            Constructor<?> c = pc.getConstructor(types);
            Object pkt = c.newInstance(args);
            Object ch = Class.forName(BASE + "common.network.NetworkHandler").getField("INSTANCE").get(null);
            ((SimpleChannel) ch).send(PacketDistributor.SERVER.noArg(), pkt);
        } catch (Throwable t) {
            TacUI.LOGGER.warn("Curios: no se pudo enviar {}: {}", pktClass, t.toString());
        }
    }

    /** Abre la pantalla de Curios (igual que su tecla). */
    public static void openCurios() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        send("CPacketOpenCurios", new Class<?>[]{ItemStack.class}, mc.player.containerMenu.getCarried().copy());
    }

    /** Vuelve al inventario normal desde Curios. */
    public static void openVanilla() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        send("CPacketOpenVanilla", new Class<?>[]{ItemStack.class}, mc.player.containerMenu.getCarried().copy());
        if (mc.player.isCreative())
            mc.setScreen(new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), mc.options.operatorItemsTab().get()));
        else
            mc.setScreen(new InventoryScreen(mc.player));
    }

    public static void page(AbstractContainerMenu m, boolean next) {
        send("CPacketPage", new Class<?>[]{int.class, boolean.class}, m.containerId, next);
    }

    public static void toggleCosmetics(AbstractContainerMenu m) {
        send("CPacketToggleCosmetics", new Class<?>[]{int.class}, m.containerId);
    }

    /** Contenido actual de las ranuras de Curios sin necesidad de su menu: lista de {identificador, ItemStack}. */
    public static java.util.List<Object[]> live(Player player) {
        java.util.List<Object[]> out = new java.util.ArrayList<>();
        try {
            Object r = Class.forName(BASE + "api.CuriosApi").getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class).invoke(null, player);
            Object handler = null;
            if (r instanceof java.util.Optional<?> o) handler = o.orElse(null);
            else if (r instanceof net.minecraftforge.common.util.LazyOptional<?> lo) handler = lo.orElse(null);
            if (handler == null) return out;
            Class<?> hc = Class.forName(BASE + "api.type.capability.ICuriosItemHandler");
            Class<?> sc = Class.forName(BASE + "api.type.inventory.ICurioStacksHandler");
            Class<?> dc = Class.forName(BASE + "api.type.inventory.IDynamicStackHandler");
            Object map = hc.getMethod("getCurios").invoke(handler);
            for (java.util.Map.Entry<?, ?> en : ((java.util.Map<?, ?>) map).entrySet()) {
                Object sh = en.getValue();
                try { if (!(Boolean) sc.getMethod("isVisible").invoke(sh)) continue; } catch (Throwable ignored) { }
                Object stacks = sc.getMethod("getStacks").invoke(sh);
                int n = (Integer) dc.getMethod("getSlots").invoke(stacks);
                for (int i = 0; i < n; i++) {
                    Object st = dc.getMethod("getStackInSlot", int.class).invoke(stacks, i);
                    out.add(new Object[]{String.valueOf(en.getKey()), st instanceof ItemStack s ? s : ItemStack.EMPTY});
                }
            }
        } catch (Throwable t) { /* sin datos: no se muestran */ }
        return out;
    }

    // ---- lectura de datos del menu / casillas

    private static Object field(Object o, String name) {
        try { Field f = o.getClass().getField(name); return f.get(o); } catch (Throwable t) { return null; }
    }

    public static int currentPage(AbstractContainerMenu m) { return field(m, "currentPage") instanceof Integer i ? i : 0; }
    public static int totalPages(AbstractContainerMenu m) { return field(m, "totalPages") instanceof Integer i ? i : 1; }
    public static boolean hasCosmetics(AbstractContainerMenu m) { return field(m, "hasCosmetics") instanceof Boolean b && b; }
    public static boolean viewingCosmetics(AbstractContainerMenu m) { return field(m, "isViewingCosmetics") instanceof Boolean b && b; }

    private static Object call(Slot s, String name) {
        try {
            Method me = s.getClass().getMethod(name);
            if (!me.getDeclaringClass().getName().startsWith(BASE)) return null;
            return me.invoke(s);
        } catch (Throwable t) { return null; }
    }

    /** Identificador del tipo de ranura (ring, necklace...) o null si no es una ranura de Curios. */
    public static String identifier(Slot s) { return call(s, "getIdentifier") instanceof String id ? id : null; }
    public static boolean canToggleRender(Slot s) { return call(s, "canToggleRender") instanceof Boolean b && b; }
    public static boolean renderStatus(Slot s) { return !(call(s, "getRenderStatus") instanceof Boolean b) || b; }

    /** Igual que el boton de Curios: alterna si ese curio se dibuja en el personaje. */
    public static void toggleRender(Slot s) {
        String id = identifier(s);
        if (id != null) send("CPacketToggleRender", new Class<?>[]{String.class, int.class}, id, s.getSlotIndex());
    }

    public static boolean cosmetic(Slot s) { return call(s, "isCosmetic") instanceof Boolean b && b; }
}
