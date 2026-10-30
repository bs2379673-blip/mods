package com.annis.interact.forge;

/** Solo se carga en el cliente. */
final class ClientEntry {
    private ClientEntry() {
    }

    static void init() {
        new com.annis.interact.client.AnnisInteractClient().onInitializeClient();
    }
}
