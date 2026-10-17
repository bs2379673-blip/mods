package com.tunombre.crafteos.client;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** lista de items prohibidos que el servidor le manda al cliente: se esconden del inventario creativo */
public final class ClientBans {
    private static volatile Set<String> banned = Set.of();

    private ClientBans() { }

    public static void set(Set<String> s) {
        Set<String> n = ConcurrentHashMap.newKeySet();
        n.addAll(s);
        banned = n;
        hideInTabs();
    }

    private static boolean is(ItemStack st) {
        if (st.isEmpty() || banned.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
        return id != null && (banned.contains(id.toString()) || banned.contains(id.getNamespace() + ":*"));
    }

    static void hideInTabs() {
        if (banned.isEmpty()) return;
        try {
            for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                tab.getDisplayItems().removeIf(ClientBans::is);
                tab.getSearchTabDisplayItems().removeIf(ClientBans::is);
            }
        } catch (Throwable ignored) { }
    }

    @SubscribeEvent
    public static void onOpening(ScreenEvent.Opening e) {
        if (e.getNewScreen() instanceof CreativeModeInventoryScreen) hideInTabs();
    }
}
