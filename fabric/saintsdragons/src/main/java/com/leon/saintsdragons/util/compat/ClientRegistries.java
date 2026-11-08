package com.leon.saintsdragons.util.compat;

/** Los registros del lado del cliente (solo si hay un mundo cargado). */
final class ClientRegistries {
    private ClientRegistries() {
    }

    static net.minecraft.core.HolderLookup.Provider get() {
        try {
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft");
            Object inst = mc.getMethod("getInstance").invoke(null);
            Object level = mc.getField("level").get(inst);
            if (level instanceof net.minecraft.world.level.Level l) {
                return l.registryAccess();
            }
        } catch (Throwable ignored) {
            // servidor dedicado
        }
        return net.minecraft.core.RegistryAccess.EMPTY;
    }
}
