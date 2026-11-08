package com.leon.saintsdragons.util.compat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayDeque;
import java.util.Deque;

/** Port 1.21.11: que se esta registrando ahora (para darle el id a las Properties de items y bloques). */
public final class RegistryContext {
    private static final ThreadLocal<Deque<ResourceKey<?>>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private RegistryContext() {
    }

    public static void push(ResourceKey<?> key) {
        STACK.get().push(key);
    }

    public static void pop() {
        STACK.get().pop();
    }

    @SuppressWarnings("unchecked")
    public static <T> ResourceKey<T> current(ResourceKey<? extends net.minecraft.core.Registry<T>> registry) {
        ResourceKey<?> k = STACK.get().peek();
        return k != null && k.isFor(registry) ? (ResourceKey<T>) k : null;
    }

    public static ResourceKey<net.minecraft.world.item.Item> item() {
        return current(Registries.ITEM);
    }

    public static ResourceKey<net.minecraft.world.level.block.Block> block() {
        return current(Registries.BLOCK);
    }
}
