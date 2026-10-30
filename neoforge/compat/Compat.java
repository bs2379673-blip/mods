package __PKG__;

import net.neoforged.bus.api.IEventBus;

/**
 * Pieza de adaptacion: el codigo de los mods Annis se escribio para Fabric; estas clases imitan las
 * partes de Fabric que usamos, pero por dentro usan Forge 1.20.1.
 */
public final class Compat {
    /** El bus de eventos del mod (lo pone la clase @Mod al arrancar). */
    public static IEventBus modBus;

    private Compat() {
    }
}
