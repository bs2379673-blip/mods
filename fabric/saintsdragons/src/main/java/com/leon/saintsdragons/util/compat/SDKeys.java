package com.leon.saintsdragons.util.compat;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Port 1.21.11: las categorias de teclas ahora son objetos registrados. */
public final class SDKeys {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("saintsdragons", "saintsdragons"));

    private SDKeys() {
    }
}
