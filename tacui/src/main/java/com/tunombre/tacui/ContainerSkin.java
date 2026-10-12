package com.tunombre.tacui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Viste los contenedores de almacenamiento (cofres, barriles, shulkers, tolvas, mochilas...) con el estilo tactico.
 * Solo toca pantallas "de almacenamiento" (lista blanca) para no tapar las flechas/barras de hornos, yunques, etc.
 */
@Mod.EventBusSubscriber(modid = TacUI.ID, value = Dist.CLIENT)
public final class ContainerSkin {
    private ContainerSkin() {}

    private static final Map<Screen, Component> TITLES = new WeakHashMap<>();
    private static final Map<Screen, Long> OPENED = new WeakHashMap<>();
    private static Field titleField, invTitleField;
    private static boolean fieldsDone;

    static boolean skinnable(AbstractContainerScreen<?> s) {
        if (s instanceof TacInventoryScreen) return false;
        AbstractContainerMenu m = s.getMenu();
        if (m instanceof ChestMenu || m instanceof ShulkerBoxMenu || m instanceof HopperMenu || m instanceof DispenserMenu) return true;
        String n = m.getClass().getName().toLowerCase();
        return n.contains("backpack") || n.contains("crate") || n.contains("locker");
    }

    private static void findFields() {
        if (fieldsDone) return;
        fieldsDone = true;
        try {   // por tipo, asi no depende de los nombres ofuscados: Screen.title y AbstractContainerScreen.playerInventoryTitle
            for (Field f : Screen.class.getDeclaredFields())
                if (f.getType() == Component.class && !Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); titleField = f; break; }
            for (Field f : AbstractContainerScreen.class.getDeclaredFields())
                if (f.getType() == Component.class && !Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); invTitleField = f; break; }
        } catch (Throwable t) { TacUI.LOGGER.warn("ContainerSkin: no se pudieron localizar los titulos: {}", t.toString()); }
    }

    @SubscribeEvent
    public static void onBackground(ContainerScreenEvent.Render.Background e) {
        if (!Layout.get().containers) return;
        AbstractContainerScreen<?> s = e.getContainerScreen();
        if (!skinnable(s)) return;
        GuiGraphics g = e.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        Theme t = Layout.get().theme();
        int sw = s.width, sh = s.height;
        int L = s.getGuiLeft(), T = s.getGuiTop(), W = s.getXSize(), H = s.getYSize();

        // titulos de vainilla fuera (se pintaban en gris oscuro encima de nuestro panel)
        findFields();
        Component title = TITLES.computeIfAbsent(s, Screen::getTitle);
        try {
            if (titleField != null) titleField.set(s, Component.empty());
            if (invTitleField != null) invTitleField.set(s, Component.empty());
        } catch (Throwable ignored) { }

        g.fill(0, 0, sw, sh, Theme.alpha(t.bg, 0x98));
        Draw.vignette(g, sw, sh);
        Draw.scanlines(g, sw, sh);
        Draw.corners(g, 10, 10, sw - 20, sh - 20, 18, Theme.alpha(t.accent, 200));

        int th = Draw.panel(g, mc.font, t, L - 3, T - 3, W + 6, H + 6, "", false);
        // barra de titulo propia por encima de la zona de casillas
        g.fill(L - 2, T - 2, L + W + 2, T + 11, Theme.alpha(t.dim, 200));
        g.fill(L - 2, T + 11, L + W + 2, T + 12, Theme.alpha(t.accent, 160));
        Draw.text(g, mc.font, title.getString().toUpperCase(), L + 5, T + 1, t.text, 0.8f);
        g.fill(L + W - 6, T + 3, L + W - 3, T + 6, t.accent);

        // etiqueta del inventario del jugador
        int minY = Integer.MAX_VALUE, minX = 0;
        for (Slot sl : s.getMenu().slots) {
            if (sl.container instanceof Inventory && sl.y < minY) { minY = sl.y; minX = sl.x; }
        }
        if (minY != Integer.MAX_VALUE) Draw.text(g, mc.font, "INVENTORY", L + minX, T + minY - 9, Theme.alpha(t.text, 170), 0.7f);

        if (s.getMenu().getClass().getName().toLowerCase().contains("backpack")) {
            // mochila: costura por dentro y cremallera bajo el titulo, que se abre al entrar
            Draw.stitch(g, L, T + 13, W, H - 15, Theme.alpha(t.accent, 110));
            long since = System.currentTimeMillis() - OPENED.computeIfAbsent(s, k -> System.currentTimeMillis());
            float k = Math.min(1f, since / 650f);
            Draw.zipper(g, t, L + 6, T + 14, W - 16, 1f - (1f - k) * (1f - k) * (1f - k));
        }
        for (Slot sl : s.getMenu().slots) {
            if (!sl.isActive()) continue;
            int x = L + sl.x, y = T + sl.y;
            boolean hov = e.getMouseX() >= x - 1 && e.getMouseX() < x + 17 && e.getMouseY() >= y - 1 && e.getMouseY() < y + 17;
            Draw.slot(g, t, x, y, hov, false);
        }
    }
}
