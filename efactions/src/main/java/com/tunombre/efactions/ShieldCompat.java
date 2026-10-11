package com.tunombre.efactions;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import yesman.epicfight.world.capabilities.item.WeaponCapabilityPresets;
import yesman.epicfight.world.capabilities.provider.ItemCapabilityProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Los escudos de Survivors Arsenal (SAShieldItem) no extienden de ShieldItem, asi que Epic Fight no les da
 * capacidad de escudo (sin animacion de bloqueo en 1a ni 3a persona). Aqui se la damos con el mismo preset
 * que usa Epic Fight para el escudo vanilla, justo antes de que Epic Fight arme el proveedor del item.
 * Va en ambos lados (cliente y servidor).
 */
public class ShieldCompat {
    private final Map<Class<?>, Boolean> cache = new ConcurrentHashMap<>();

    private boolean isSaShield(Item item) {
        return cache.computeIfAbsent(item.getClass(), cls -> {
            for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass())
                if (c.getName().endsWith(".SAShieldItem")) return true;
            return false;
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttach(AttachCapabilitiesEvent<ItemStack> e) {
        ItemStack stack = e.getObject();
        if (stack == null || stack.isEmpty()) return;
        Item item = stack.getItem();
        if (!isSaShield(item)) return;
        try {
            if (ItemCapabilityProvider.get(item) == null)
                ItemCapabilityProvider.put(item, WeaponCapabilityPresets.SHIELD.apply(item).build());
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("efactions").warn("efactions: no se pudo dar capacidad de escudo a {}", item, t);
        }
    }
}
