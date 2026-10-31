package com.annis.compat.client;

import com.annis.compat.AnnisCompat;
import dev.dyslexic.cinematic.client.ClientMenus;
import dev.dyslexic.cinematic.client.ClientPayloads;
import dev.dyslexic.cinematic.client.KneeLayer;
import dev.dyslexic.cinematic.client.ShelfBooks;
import dev.dyslexic.cinematic.station.StationKind;

import java.util.Locale;

/**
 * Lo unico que se lee de Cinematic Interactions: en que estacion esta cada jugador y hace cuanto
 * hizo la ultima accion. Solo se carga si Cinematic esta instalado; si cambia algo en una version
 * nueva, se apaga solo en vez de romper el juego.
 */
final class CinematicBridge {
    private static boolean broken;

    private CinematicBridge() {
    }

    /** "table", "anvil", "campfire_sentado"... o null si no esta en ninguna. */
    static String station(int entityId) {
        if (broken) {
            return null;
        }
        try {
            StationKind kind = ClientPayloads.atStation(entityId);
            if (kind == null) {
                return null;
            }
            String k = kind.name().toLowerCase(Locale.ROOT);
            if (kind == StationKind.CAMPFIRE && KneeLayer.sitting(entityId)) {
                return k + "_sentado";
            }
            if (kind == StationKind.CHISELED_BOOKSHELF && ShelfBooks.reading(entityId)) {
                return k + "_leyendo";
            }
            return k;
        } catch (Throwable t) {
            broken = true;
            AnnisCompat.LOGGER.warn("Annis Compat: esta version de Cinematic Interactions no es compatible ({}); se desactiva", t.toString());
            return null;
        }
    }

    /** Segundos desde la ultima accion en la estacion (martillazo, girar la piedra...). */
    static double sinceAction(int entityId) {
        if (broken) {
            return 1e9;
        }
        try {
            return ClientMenus.sinceMove(entityId);
        } catch (Throwable t) {
            return 1e9;
        }
    }
}
