package com.annis.vfx.forge;

/** Solo se carga en el cliente. */
final class ClientEntry {
    private ClientEntry() {
    }

    static void init() {
        new com.annis.vfx.client.AnnisVfxClient().onInitializeClient();
    }
}
