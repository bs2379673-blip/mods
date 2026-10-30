package com.annis.npc.forge;

/** Solo se carga en el cliente. */
final class ClientEntry {
    private ClientEntry() {
    }

    static void init() {
        new com.annis.npc.client.AnnisNpcClient().onInitializeClient();
    }
}
