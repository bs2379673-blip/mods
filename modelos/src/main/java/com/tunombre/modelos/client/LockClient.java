package com.tunombre.modelos.client;

import com.tunombre.modelos.Modelos;

import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;

/** solo cliente: lista de jugadores con modelo puesto */
public final class LockClient {
    private LockClient() { }

    public static void set(Collection<UUID> ids) {
        Modelos.clientModels = new HashSet<>(ids);
    }
}
